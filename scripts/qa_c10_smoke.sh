#!/usr/bin/env bash
#
# qa_c10_smoke.sh — smokes [S] del corte C10 (T10.27/FC#37; canon qa_c2–qa_c8)
#
# Ejecución REAL de SQL sobre la pila (los MvcTest no tocan BD — REQ-C10-QA-1):
# los 3 DELETE de doble confirmación y el resumen por proyecto, con siembra
# propia EFÍMERA qa-c10-* y destructividad ACOTADA a esa siembra (R-1):
#
#   C10-S1  resumen con datos reales de la siembra (GET /projects/{id}/summary:
#           4 claves, by_level en el orden del CASE del SQL — FATAL · ERROR ·
#           WARN · DEBUG · INFO, nota c10-orden-bandas —, total = nº real,
#           last_log_at ISO-Z) (REQ-C10-RESUMEN-3/4 + flujo 9)
#   C10-S2  summary AUSENTE sin include · presente con include=summary en la
#           lista · include=otracosa → 400 filter_invalid_value field=include
#           (REQ-C10-RESUMEN-1/2/6)
#   C10-S3  negativos de la doble confirmación, NADA borrado: 8c sin cabecera
#           → 400 confirm_required; 8c nombre desigual → 400
#           confirm_mismatch; 8a nombre desigual → 400 confirm_mismatch; 8b id
#           desigual → 400 confirm_mismatch; proyecto y sus 45 logs intactos
#           (REQ-C10-DEL-2/3 · PURGA-3 · LOGDEL-2)
#   C10-S4  purga con N REAL → 200 {"deleted":45} (retornos de
#           JdbcTemplate.update — gap 6), resumen a CERO
#           ({"by_level":{},"by_environment":{},"total":0,"last_log_at":null},
#           flujo 8c/9) y proyecto+key intactos (REQ-C10-PURGA-1/2)
#   C10-S5  purga N=0 → 200 {"deleted":0} (estado vacío informativo, M4/PURGA-5)
#   C10-S6  la key sobrevive a la PURGA → ingesta con ella → 202 {accepted:N}
#           (literal §3.3 — REQ-C10-PURGA-2)
#   C10-S7  CASCADE verificado en el PAR (uno purgado, otro eliminado): count
#           logs 10 → DELETE /projects/{id} → 204 → count 0 vía
#           /logs?project_id → 404 not_found (REQ-C10-DEL-1/4/5)
#   C10-S8  key muere con la fila: ingesta con la key del proyecto eliminado →
#           401 api_key_invalid (REQ-C10-DEL-5 — columnas de la fila projects)
#   C10-S9  8b REAL con el proyecto vivo: DELETE /logs/{id} con
#           X-Confirm-Log-Id → 204 → GET /logs/{id} → 404; el proyecto sigue
#           vivo y su key acepta ingesta (REQ-C10-LOGDEL-1/4)
#   C10-S10 limpieza del PROPIO smoke (8a sobre el proyecto purgado) → 204 →
#           404: cero residuo qa-c10-<ts> al término
#
# GESTIÓN DE PILA (diferencia con C8): este smoke SÍ gestiona su propia pila
# (`up -d --build` idempotente + espera de healthy por el sondeo /me → 401
# invalid_token) PERO JAMÁS `down -v` — ese privilegio era SOLO del smoke de
# Fase 8 (bootstrapping de la BD). Los volúmenes y los datos del usuario
# quedan intactos: los DELETE del smoke van SOLO contra su siembra qa-c10-*.
# La pila queda ARRIBA al término (el arnés visual C10 la asume viva).
#
# Higiene (S16/CD-4): JWT y API keys SOLO en el tmpdir 700 con trap EXIT —
# JAMÁS a consola ni al log. Cero secretos impresos.
#
# Uso:
#   bash scripts/qa_c10_smoke.sh
#
# Salida: fases por stderr; una línea
#     C10-Sn: PASS | FAIL | BLOQUEADO (detalle)
# por check.
# Exit: 0 = sin FAILs · 1 = hay algún FAIL.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE_URL="http://127.0.0.1:8080"
URL_LOGIN="$BASE_URL/api/v1/panel/auth/login"
URL_ME="$BASE_URL/api/v1/panel/me"
URL_PROJECTS="$BASE_URL/api/v1/panel/projects"
URL_LOGS="$BASE_URL/api/v1/panel/logs"
URL_INGEST="$BASE_URL/api/v1/ingest"
URL_BATCH="$BASE_URL/api/v1/ingest/batch"

TS="$(date +%Y%m%d%H%M%S)"
P_PURGA="qa-c10-${TS}-purga"          # par A: purga 8c (S4) y eliminado 8a al cierre (S10)
P_PAR="qa-c10-${TS}-par"              # par B: eliminado 8a con CASCADE (S7/S8)
N_PURGA_S1="qa-c10-${TS}-pre"         # needle de los 45 logs del proyecto A
N_SOBRE="qa-c10-${TS}-sobrevive"      # needle de la ingesta post-purga (S6/S9)
N_PAR="qa-c10-${TS}-cascade"          # needle de los 10 logs del proyecto B

TMPDIR_QA="$(mktemp -d /tmp/qa_c10_smoke.XXXXXX)"
chmod 700 "$TMPDIR_QA" 2>/dev/null || true
# KEEP_TMP=1 (SOLO depuración): conserva el tmpdir 700 (contiene JWT y keys).
trap 'trap_liberar; [ "${KEEP_TMP:-0}" = "1" ] && echo "[trap] tmpdir CONSERVADO: $TMPDIR_QA (borrar a mano)" >&2 || rm -rf "$TMPDIR_QA"' EXIT
trap_liberar() {
  if [ -n "${UP_PID:-}" ]; then kill "$UP_PID" 2>/dev/null || true; fi
}
F_CUERPO="$TMPDIR_QA/cuerpo.txt"
F_CAB="$TMPDIR_QA/cabeceras.txt"
F_TOKEN="$TMPDIR_QA/token.txt"        # JWT — jamá consola
F_KEY1="$TMPDIR_QA/key1.txt"          # key del proyecto A — jamá consola
F_KEY2="$TMPDIR_QA/key2.txt"          # key del proyecto B — jamá consola

# Compose wrapper (canon C4–C8, SIN down -v en C10).
COMPOSE_BIN=""
if podman compose version >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
elif command -v podman-compose >/dev/null 2>&1; then
  COMPOSE_BIN="podman-compose"
elif command -v podman >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
fi
compose_() {
  local estado
  timeout --kill-after=10 620 $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log"
  estado=$?
  if [ "$estado" -eq 124 ] || [ "$estado" -eq 137 ]; then
    pkill -f 'podman-compose up' 2>/dev/null || true
  fi
  return "$estado"
}
PODMAN() { podman "$@" 2>>"$TMPDIR_QA/compose.log"; }

declare -a RESULTADOS=()
marca() { RESULTADOS+=("$1: $2${3:+ ($3)}"); }

pila_saludable() {
  local snap estado e="" svc
  snap="$(PODMAN ps --format '{{.Names}} {{.Status}}' 2>/dev/null || true)"
  for svc in backend frontend postgres; do
    estado="$(grep -E "^[^ ]*${svc}[ _]" <<<"$snap" | head -n1)"
    grep -qE 'Up.*\(healthy\)' <<<"$estado" || e="$e $svc=$(echo "$estado" | head -c 60)"
  done
  [ -z "$e" ] && return 0 || { echo "$e" >&2; return 1; }
}

esperar_healthy() {
  local limite=$(( $(date +%s) + ${1:-480} ))
  while [ "$(date +%s)" -lt "$limite" ]; do
    if curl -sS --max-time 5 -o "$TMPDIR_QA/hc_body.txt" "$URL_ME" 2>/dev/null \
       && grep -q '"code"[[:space:]]*:[[:space:]]*"invalid_token"' "$TMPDIR_QA/hc_body.txt" 2>/dev/null; then
      return 0
    fi
    sleep 3
  done
  return 1
}

# req VERBO URL CUERPO [CABECERA...] — fija CODIGO/CUERPO/CABECERAS globales.
req() {
  local verbo="$1" url="$2" cuerpo="$3"; shift 3
  local args=(-sS --max-time 30 -o "$F_CUERPO" -D "$F_CAB" -w '%{http_code}' -X "$verbo")
  if [ -n "$cuerpo" ]; then
    args+=(-H 'Content-Type: application/json')
    args+=(--data-binary "$cuerpo")
  fi
  local _h
  for _h in "$@"; do args+=(-H "$_h"); done
  local cod
  cod="$(curl "${args[@]}" "$url" 2>>"$TMPDIR_QA/curl_err.log" || true)"
  CODIGO="${cod:-000}"
  CUERPO="$(cat "$F_CUERPO" 2>/dev/null || true)"
  CABECERAS="$(cat "$F_CAB" 2>/dev/null || true)"
  printf '%s %s -> %s\n' "$verbo" "$url" "$CODIGO" >> "$TMPDIR_QA/req_debug.log"
}

login() { req POST "$URL_LOGIN" "{\"username\":\"$1\",\"password\":\"$2\"}"; }
bearer_hdr() { printf 'Authorization: Bearer %s' "$(cat "$F_TOKEN")"; }
key1_hdr() { printf 'X-API-Key: %s' "$(cat "$F_KEY1")"; }
key2_hdr() { printf 'X-API-Key: %s' "$(cat "$F_KEY2")"; }

# campo_json CLAVE — valor tolerante (exit 2/3 → vacío). NUNCA imprime secretos.
campo_json() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
except Exception:
    sys.exit(3)
if not isinstance(d, dict):
    sys.exit(3)
v = d.get(sys.argv[1])
if v is None:
    sys.exit(2)
print("true" if v is True else "false" if v is False else v)' "$1"
}

# json_claves_orden — claves top-level EN ORDEN tal cual viajó. El
# object_pairs_hook conserva el orden sin reordenar (assert de orden de las
# 4 claves del SummaryDto en S1 y de la clave única de {"deleted":N} en S4);
# los valores ANIDADOS llegan como listas de pares, que C10 NO navega.
json_claves_orden() {
  printf '%s' "$CUERPO" | python3 -c '
import json, sys
pares = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"),
                   object_pairs_hook=lambda p: p)
print("|".join(k for k, _v in pares) if isinstance(pares, list) else "")'
}

# items_log — nº de filas de un GET /logs (página, ≤50)
n_items_log() {
  printf '%s' "$CUERPO" | python3 -c '
import json, sys
d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
print(len(d.get("items", [])))' 2>/dev/null || echo -1
}

# un_id — 1.er id de la página GET /logs actual
un_id() {
  printf '%s' "$CUERPO" | python3 -c '
import json, sys
d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
items = d.get("items", [])
print(items[0]["id"] if items else "")' 2>/dev/null || echo ""
}

urlenc() { python3 -c 'import urllib.parse, sys; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"; }

# ---------------------------------------------------------------
# 1. Precondiciones: anti-veneno + .env + pila para SQL REAL (sin down -v)
# ---------------------------------------------------------------
unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
      SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
      JWT_SECRET JWT_TTL_HOURS ADMIN_INITIAL_USERNAME ADMIN_INITIAL_PASSWORD \
      INGEST_MAX_REQUEST_BYTES INGEST_MAX_BATCH_SIZE WS_HEARTBEAT_SECONDS \
      FRONTEND_ORIGIN SPRING_PROFILES_ACTIVE TZ 2>/dev/null || true

CON_ENV=0; CON_CRED=0
if [ -f .env ]; then
  CON_ENV=1
  while IFS= read -r _qa_linea || [ -n "$_qa_linea" ]; do
    case "$_qa_linea" in ''|\#*) continue ;; esac
    eval "$_qa_linea" 2>/dev/null || true
  done < ./.env
  if [ -n "${ADMIN_INITIAL_USERNAME:-}" ] && [ -n "${ADMIN_INITIAL_PASSWORD:-}" ]; then CON_CRED=1; fi
fi
bloqueo_env="(sin .env F1: credenciales del admin no disponibles)"
bloqueo_pila="(pila/compose no disponible)"

ID_REST_ALL="C10-S1 C10-S2 C10-S3 C10-S4 C10-S5 C10-S6 C10-S7 C10-S8 C10-S9 C10-S10"

precondiciones() {
  echo "[pre] pila C10 para SQL REAL: compose up -d --build (idempotente;" >&2
  echo "[pre] JAMÁS down -v — ese privilegio era SOLO del smoke de Fase 8)     " >&2
  if ! compose_ up -d --build; then
    echo "[pre] compose up --build falló (ver $TMPDIR_QA/compose.log)" >&2; return 1
  fi
  if ! esperar_healthy 480; then
    echo "[pre] la pila no llega a healthy (sondeo /me → 401 invalid_token)" >&2; return 1
  fi
  pila_saludable || { echo "[pre] pila no 3×healthy por podman" >&2; return 1; }
  # smoke-of-the-smoke: GET /me sin JWT → 401 invalid_token
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/me_body.txt" -w '%{http_code}' "$URL_ME" 2>/dev/null || echo 000)"
  [ "$hc" = "401" ] && grep -q 'invalid_token' "$TMPDIR_QA/me_body.txt" 2>/dev/null \
    || { echo "[pre] sondeo /me: $hc (esperado 401 invalid_token)" >&2; return 1; }
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    echo "[pre] login del admin falló (status ${CODIGO:-000})" >&2; return 1
  fi
  printf '%s' "$(campo_json token)" > "$F_TOKEN"
  [ -s "$F_TOKEN" ] || { echo "[pre] token no capturado" >&2; return 1; }
  echo "[pre] healthy + login OK (higiene: JWT solo en $TMPDIR_QA)" >&2
  return 0
}

# ---------------------------------------------------------------
# 2. Siembra propia efímera qa-c10-<ts> — PAR de proyectos (A purga + B cascade)
#    A: 45 logs (INFO 30 · ERROR 5 · WARN 5 · DEBUG 4 · FATAL 1; production 35
#    · staging 10) — resumen con datos reales deterministas para S1/S4.
# ---------------------------------------------------------------
crear_proyecto() {  # crear_proyecto NAME FKEY
  req POST "$URL_PROJECTS" "{\"name\":\"$1\",\"description\":\"siembra del smoke C10 ($TS)\"}" "$(bearer_hdr)"
  [ "$CODIGO" = "201" ] || return 1
  local id
  id="$(campo_json id)"
  [ -n "$id" ] || return 1
  # $2 llega como RUTA ABSOLUTA (F_KEY): el fichero de id se escribe tal
  # cual "$2_id.txt" (el lector espera "$TMPDIR_QA/$(basename "$F_KEY")_id.txt").
  printf '%s' "$id" > "$2_id.txt"
  req POST "$URL_PROJECTS/$id/api-key/regenerate" "" "$(bearer_hdr)"
  [ "$CODIGO" = "201" ] || return 1
  printf '%s' "$(campo_json key)" > "$2"
  [ -s "$2" ] || return 1
  return 0
}

sembrar() {  # sembrar FKEY NEEDLE — genera el JSON de la siembra y lo ingesta
  local fkey="$1" needle="$2" out="$TMPDIR_QA/seed_$(basename "$fkey").json"
  python3 - "$needle" > "$out" <<'PYEOF'
# 45 logs del proyecto A (S1/S4): niveles en el case del SQL del resumen
# (FATAL 1 · ERROR 5 · WARN 5 · DEBUG 4 · INFO 30) y par de entornos
# (production 35 · staging 10 — INFO se reparte 25/5); el par B (cascade)
# se siembra aparte.
import json, sys
from datetime import datetime, timedelta, timezone
needle = sys.argv[1]
ahora = datetime.now(timezone.utc)
plan = [("FATAL", 1, "production"), ("ERROR", 5, "production"),
        ("WARN", 5, "staging"), ("DEBUG", 4, "production"),
        ("INFO", 25, "production"), ("INFO", 5, "staging")]
lotes = []
n = 1
for nivel, cantidad, entorno in plan:
    for _ in range(cantidad):
        ts = ahora - timedelta(minutes=n)
        lotes.append({
            "level": nivel,
            "timestamp": ts.strftime("%Y-%m-%dT%H:%M:%S.000000Z"),
            "message": f"{needle} #{n:03d}",
            "environment": entorno,
            "source": f"qa-c10-src{n % 3}",
        })
        n += 1
print(json.dumps({"logs": lotes}, separators=(",", ":")))
PYEOF
  req POST "$URL_BATCH" "$(cat "$out")" "$(printf 'X-API-Key: %s' "$(cat "$fkey")")"
  [ "$CODIGO" = "202" ] || { echo "[pre-fase] siembra $fkey=${CODIGO:-000}" >&2; return 1; }
  return 0
}

sembrar_par() {
  local out="$TMPDIR_QA/seed_par.json"
  python3 - "$N_PAR" > "$out" <<'PYEOF'
import json, sys
from datetime import datetime, timedelta, timezone
needle = sys.argv[1]
ahora = datetime.now(timezone.utc)
lotes = []
for i in range(1, 11):
    ts = ahora - timedelta(minutes=i)
    lotes.append({
        "level": "INFO",
        "timestamp": ts.strftime("%Y-%m-%dT%H:%M:%S.000000Z"),
        "message": f"{needle} #{i:02d}",
        "environment": "staging",
        "source": "qa-c10-cascade",
    })
print(json.dumps({"logs": lotes}, separators=(",", ":")))
PYEOF
  req POST "$URL_BATCH" "$(cat "$out")" "$(key2_hdr)"
  [ "$CODIGO" = "202" ] || { echo "[pre-fase] siembra par=${CODIGO:-000}" >&2; return 1; }
  return 0
}

ejecutar_pre=1
if [ "$CON_ENV/$CON_CRED" != "1/1" ]; then
  for id in $ID_REST_ALL; do marca "$id" BLOQUEADO "$bloqueo_env"; done
  ejecutar_pre=0
else
  if ! precondiciones \
     || ! crear_proyecto "$P_PURGA" "$F_KEY1" \
     || ! crear_proyecto "$P_PAR" "$F_KEY2" \
     || ! sembrar "$F_KEY1" "$N_PURGA_S1" \
     || ! sembrar_par; then
    for id in $ID_REST_ALL; do marca "$id" BLOQUEADO "$bloqueo_pila"; done
    ejecutar_pre=0
  fi
fi

ID1="$(cat "$TMPDIR_QA/$(basename "$F_KEY1")_id.txt" 2>/dev/null || true)"
ID2="$(cat "$TMPDIR_QA/$(basename "$F_KEY2")_id.txt" 2>/dev/null || true)"

# ---------------------------------------------------------------
# 3. Casos REST
# ---------------------------------------------------------------

# --- C10-S1 — resumen con datos reales de la siembra ---
case_s1() {
  echo "[C10-S1] GET /projects/$ID1/summary: 4 claves, CASE del SQL, total=45" >&2
  local e="" orden
  req GET "$URL_PROJECTS/$ID1/summary" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="summary=$CODIGO "
  orden="$(json_claves_orden)"
  grep -qx 'by_level|by_environment|total|last_log_at' <<<"$orden" || e="${e}claves=[$orden] "
  if ! python3 - "$F_CUERPO" <<'PYEOF' 2>/dev/null; then e="${e}forma-datos "; fi
import json, re, sys
d = json.load(open(sys.argv[1]))
# NOTA c10-orden-bandas: el CASE del backend manda DEBUG=4 ANTES de INFO=5
# (FATAL 1 · ERROR 2 · WARN 3 · DEBUG 4 · INFO 5 · ELSE 6) — literal maqueta-04.
assert list(d["by_level"].keys()) == ["FATAL", "ERROR", "WARN", "DEBUG", "INFO"], d["by_level"]
assert d["by_level"] == {"FATAL": 1, "ERROR": 5, "WARN": 5, "DEBUG": 4, "INFO": 30}, d["by_level"]
assert d["by_environment"] == {"production": 35, "staging": 10}, d["by_environment"]
assert d["total"] == 45, d["total"]
assert re.match(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$", d["last_log_at"]), d["last_log_at"]
PYEOF
  [ -z "$e" ] && marca C10-S1 PASS "resumen real: CASE FATAL·ERROR·WARN·DEBUG·INFO, 45=35+10, last_log_at ISO-Z" \
    || marca C10-S1 FAIL "($e)"
  return 0
}

# --- C10-S2 — summary ausente sin include / presente con include / inválido ---
case_s2() {
  echo "[C10-S2] GET /projects SIN summary; listado con include=summary; include inválido → 400" >&2
  local e=""
  req GET "$URL_PROJECTS/$ID1" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="ficha=$CODIGO "
  grep -q '"summary"' <<<"$CUERPO" && e="${e}ficha-CON-summary "
  req GET "$URL_PROJECTS" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}lista=$CODIGO "
  grep -q '"summary"' <<<"$CUERPO" && e="${e}lista-CON-summary "
  req GET "$URL_PROJECTS?include=summary" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}include=$CODIGO "
  if ! python3 - "$F_CUERPO" "$ID1" <<'PYEOF' 2>/dev/null; then e="${e}include-shape "; fi
import json, sys
d = json.load(open(sys.argv[1]))
assert isinstance(d, list) and d, "listado vacío"
propio = [p for p in d if str(p.get("id")) == sys.argv[2]]
assert propio, "proyecto A ausente del listado"
assert isinstance(propio[0].get("summary"), dict), "summary AUSENTE con include"
assert set(propio[0]["summary"].keys()) == {
    "by_level", "by_environment", "total", "last_log_at"}, "SummaryDto incompleto"
PYEOF
  req GET "$URL_PROJECTS?include=otracosa" "" "$(bearer_hdr)"
  [ "$CODIGO" = "400" ] || e="${e}invalid=$CODIGO "
  [ "$(campo_json code || echo '?')" = "filter_invalid_value" ] || e="${e}code=$(campo_json code || echo '?') "
  [ "$(campo_json field || echo '?')" = "include" ] || e="${e}field=$(campo_json field || echo '?') "
  [ -z "$e" ] && marca C10-S2 PASS "summary ausente/presente por include + 400 filter_invalid_value field=include" \
    || marca C10-S2 FAIL "($e)"
  return 0
}

# --- C10-S3 — negativos de la doble confirmación (nada borrado) ---
case_s3() {
  echo "[C10-S3] 8c sin cabecera/desigual, 8a desigual, 8b id desigual → 400; NADA borrado" >&2
  local e="" id_log
  req DELETE "$URL_PROJECTS/$ID1/logs" "" "$(bearer_hdr)"
  [ "$CODIGO" = "400" ] || e="purga-sin=$CODIGO "
  [ "$(campo_json code || echo '?')" = "confirm_required" ] || e="${e}code1=$(campo_json code || echo '?') "
  req DELETE "$URL_PROJECTS/$ID1/logs" "" "$(bearer_hdr)" "X-Confirm-Name: $P_PURGA-x"
  [ "$CODIGO" = "400" ] || e="${e}purga-mal=$CODIGO "
  [ "$(campo_json code || echo '?')" = "confirm_mismatch" ] || e="${e}code2=$(campo_json code || echo '?') "
  req DELETE "$URL_PROJECTS/$ID1" "" "$(bearer_hdr)" "X-Confirm-Name: $P_PURGA-x"
  [ "$CODIGO" = "400" ] || e="${e}del-mal=$CODIGO "
  [ "$(campo_json code || echo '?')" = "confirm_mismatch" ] || e="${e}code3=$(campo_json code || echo '?') "
  req GET "$URL_LOGS?project_id=$ID1" "" "$(bearer_hdr)"
  id_log="$(un_id)"
  [ -n "$id_log" ] || e="${e}sina-log-1 "
  req DELETE "$URL_LOGS/$id_log" "" "$(bearer_hdr)" "X-Confirm-Log-Id: 99999999"
  [ "$CODIGO" = "400" ] || e="${e}logdel-mal=$CODIGO "
  [ "$(campo_json code || echo '?')" = "confirm_mismatch" ] || e="${e}code4=$(campo_json code || echo '?') "
  req GET "$URL_PROJECTS/$ID1" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}proyecto-vivo=$CODIGO "
  req GET "$URL_LOGS?project_id=$ID1" "" "$(bearer_hdr)"
  [ "$(n_items_log)" -eq 45 ] || e="${e}logs-tras=$(n_items_log) (esperado 45) "
  [ -z "$e" ] && marca C10-S3 PASS "confirm_required/confirm_mismatch×4 con proyecto y 45 logs intactos" \
    || marca C10-S3 FAIL "($e)"
  return 0
}

# --- C10-S4 — purga con N REAL + resumen a cero + key/proyecto intactos ---
case_s4() {
  echo "[C10-S4] DELETE /projects/$ID1/logs → 200 {\"deleted\":45}; resumen a cero; DTO intacto" >&2
  local e="" orden
  req DELETE "$URL_PROJECTS/$ID1/logs" "" "$(bearer_hdr)" "X-Confirm-Name: $P_PURGA"
  [ "$CODIGO" = "200" ] || e="purga=$CODIGO "
  orden="$(json_claves_orden)"
  grep -qx 'deleted' <<<"$orden" || e="${e}claves=[$orden] "
  [ "$(campo_json deleted || echo '?')" = "45" ] || e="${e}N=$(campo_json deleted || echo '?') (esperado 45) "
  req GET "$URL_PROJECTS/$ID1/summary" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}resumen=$CODIGO "
  if ! python3 - "$F_CUERPO" <<'PYEOF' 2>/dev/null; then e="${e}resumen-cero "; fi
import json, sys
d = json.load(open(sys.argv[1]))
assert d["by_level"] == {} and d["by_environment"] == {}, d
assert d["total"] == 0 and d["last_log_at"] is None, d
PYEOF
  req GET "$URL_PROJECTS/$ID1" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}proyecto=$CODIGO "
  [ "$(campo_json has_key || echo '?')" = "true" ] || e="${e}has_key=$(campo_json has_key || echo '?') "
  [ "$(campo_json status || echo '?')" = "ACTIVE" ] || e="${e}status=$(campo_json status || echo '?') "
  [ "$(campo_json name || echo '?')" = "$P_PURGA" ] || e="${e}name "
  [ -z "$e" ] && marca C10-S4 PASS '200 {"deleted":45} · resumen {"total":0,"last_log_at":null} · proyecto+key intactos' \
    || marca C10-S4 FAIL "($e)"
  return 0
}

# --- C10-S5 — purga N=0 → 200 {"deleted":0} (M4) ---
case_s5() {
  echo "[C10-S5] 2.ª purga sobre proyecto ya sin logs → 200 {\"deleted\":0}" >&2
  local e=""
  req DELETE "$URL_PROJECTS/$ID1/logs" "" "$(bearer_hdr)" "X-Confirm-Name: $P_PURGA"
  [ "$CODIGO" = "200" ] || e="purga0=$CODIGO "
  [ "$(campo_json deleted || echo '?')" = "0" ] || e="${e}N=$(campo_json deleted || echo '?') (esperado 0) "
  [ -z "$e" ] && marca C10-S5 PASS 'N=0 → 200 {"deleted":0} (estado vacío informativo, no error)' \
    || marca C10-S5 FAIL "($e)"
  return 0
}

# --- C10-S6 — la key sobrevive a la PURGA ---
case_s6() {
  echo "[C10-S6] ingesta con la key del proyecto PURGADO → 202 (la key sobrevive)" >&2
  local e="" out
  out="$TMPDIR_QA/seed_sobrevive.json"
  python3 - "$N_SOBRE" > "$out" <<'PYEOF'
import json, sys
from datetime import datetime, timedelta, timezone
needle = sys.argv[1]
ahora = datetime.now(timezone.utc)
lotes = []
for i in range(1, 4):
    ts = ahora - timedelta(minutes=i)
    lotes.append({
        "level": "WARN",
        "timestamp": ts.strftime("%Y-%m-%dT%H:%M:%S.000000Z"),
        "message": f"{needle} #{i:02d}",
        "environment": "production",
        "source": "qa-c10-post-purga",
    })
print(json.dumps({"logs": lotes}, separators=(",", ":")))
PYEOF
  req POST "$URL_BATCH" "$(cat "$out")" "$(key1_hdr)"
  [ "$CODIGO" = "202" ] || e="ingesta=$CODIGO "
  [ "$(campo_json accepted || echo '?')" = "3" ] || e="${e}accepted=$(campo_json accepted || echo '?') (esperado 3) "
  req GET "$URL_PROJECTS/$ID1/summary" "" "$(bearer_hdr)"
  [ "$(campo_json total || echo '?')" = "3" ] || e="${e}total=$(campo_json total || echo '?') (esperado 3) "
  [ -z "$e" ] && marca C10-S6 PASS "202 {accepted:3} con la key de la purga («sigue aceptando ingesta», literal §3.3)" \
    || marca C10-S6 FAIL "($e)"
  return 0
}

# --- C10-S7 — CASCADE verificado en el PAR (count → delete → count 0) ---
case_s7() {
  echo "[C10-S7] PAR: logs 10 → DELETE /projects/$ID2 → 204 → count 0 → 404" >&2
  local e=""
  req GET "$URL_LOGS?project_id=$ID2" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="pre=$CODIGO "
  [ "$(n_items_log)" -eq 10 ] || e="${e}pre-n=$(n_items_log) (esperado 10) "
  req DELETE "$URL_PROJECTS/$ID2" "" "$(bearer_hdr)" "X-Confirm-Name: $P_PAR"
  [ "$CODIGO" = "204" ] || e="${e}delete=$CODIGO "
  req GET "$URL_LOGS?project_id=$ID2" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}post=$CODIGO "
  [ "$(n_items_log)" -eq 0 ] || e="${e}post-n=$(n_items_log) (esperado 0 — CASCADE) "
  req GET "$URL_PROJECTS/$ID2" "" "$(bearer_hdr)"
  [ "$CODIGO" = "404" ] || e="${e}ficha=$CODIGO "
  [ "$(campo_json code || echo '?')" = "not_found" ] || e="${e}code=$(campo_json code || echo '?') "
  [ -z "$e" ] && marca C10-S7 PASS "CASCADE del motor: 10 logs → 204 → 0 huérfanos + ficha 404 not_found" \
    || marca C10-S7 FAIL "($e)"
  return 0
}

# --- C10-S8 — la key muere con la fila ---
case_s8() {
  echo "[C10-S8] ingesta con la key del proyecto ELIMINADO → 401 api_key_invalid" >&2
  local e=""
  req POST "$URL_BATCH" '{"logs":[{"level":"INFO","timestamp":"2026-01-01T00:00:00Z","message":"dead-key","environment":"production"}]}' "$(key2_hdr)"
  [ "$CODIGO" = "401" ] || e="ingesta=$CODIGO "
  [ "$(campo_json code || echo '?')" = "api_key_invalid" ] || e="${e}code=$(campo_json code || echo '?') "
  [ -z "$e" ] && marca C10-S8 PASS "401 api_key_invalid — la key vivía en columnas de projects y muere con la fila" \
    || marca C10-S8 FAIL "($e)"
  return 0
}

# --- C10-S9 — 8b REAL con el proyecto vivo ---
case_s9() {
  echo "[C10-S9] DELETE /logs/{id} → 204 → GET 404; el proyecto y su key siguen vivos" >&2
  local e="" id_log
  req GET "$URL_LOGS?text=$(urlenc "$N_SOBRE")" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="busqueda=$CODIGO "
  id_log="$(un_id)"
  [ -n "$id_log" ] || e="sina-id "
  req DELETE "$URL_LOGS/$id_log" "" "$(bearer_hdr)" "X-Confirm-Log-Id: $id_log"
  [ "$CODIGO" = "204" ] || e="${e}delete=$CODIGO "
  req GET "$URL_LOGS/$id_log" "" "$(bearer_hdr)"
  [ "$CODIGO" = "404" ] || e="${e}detalle=$CODIGO "
  req GET "$URL_PROJECTS/$ID1" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}proyecto=$CODIGO "
  req POST "$URL_BATCH" "{\"logs\":[{\"level\":\"INFO\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"message\":\"$N_SOBRE-post-8b\",\"environment\":\"production\"}]}" "$(key1_hdr)"
  [ "$CODIGO" = "202" ] || e="${e}ingesta-post=$CODIGO "
  [ -z "$e" ] && marca C10-S9 PASS "8b: 204 → 404; borrado de UN log NO toca el proyecto ni su key (LOGDEL-4)" \
    || marca C10-S9 FAIL "($e)"
  return 0
}

# --- C10-S10 — limpieza del propio smoke (8a sobre el proyecto purgado) ---
case_s10() {
  echo "[C10-S10] limpieza: DELETE /projects/$ID1 → 204 → 404 (cero residuo qa-c10-<ts>)" >&2
  local e=""
  req DELETE "$URL_PROJECTS/$ID1" "" "$(bearer_hdr)" "X-Confirm-Name: $P_PURGA"
  [ "$CODIGO" = "204" ] || e="delete=$CODIGO "
  req GET "$URL_PROJECTS/$ID1" "" "$(bearer_hdr)"
  [ "$CODIGO" = "404" ] || e="${e}ficha=$CODIGO "
  req GET "$URL_LOGS?text=$(urlenc "$N_SOBRE")" "" "$(bearer_hdr)"
  [ "$(n_items_log)" -eq 0 ] || e="${e}cascade=$(n_items_log) (esperado 0) "
  [ -z "$e" ] && marca C10-S10 PASS "8a final: proyecto purgado eliminado, cero residuo qa-c10-<ts>" \
    || marca C10-S10 FAIL "($e)"
  return 0
}

# ---------------------------------------------------------------
# 4. Ejecución y veredicto
# ---------------------------------------------------------------

echo "== qa_c10_smoke ==" >&2
if [ "${ejecutar_pre:-1}" = "1" ]; then
  case_s1
  case_s2
  case_s3
  case_s4
  case_s5
  case_s6
  case_s7
  case_s8
  case_s9
  case_s10
fi

fails=0; bloqueados=0
echo ""
for r in "${RESULTADOS[@]}"; do
  echo "$r"
  case "$r" in
    *": FAIL"*) fails=$((fails + 1)) ;;
    *": BLOQUEADO"*) bloqueados=$((bloqueados + 1)) ;;
  esac
done
echo ""
echo "resumen: ${#RESULTADOS[@]} marcas · $fails FAIL · $bloqueados BLOQUEADO" >&2
echo "nota: la pila queda ARRIBA al término (el arnés visual C10 la asume viva)" >&2
if [ "$fails" -gt 0 ]; then
  exit 1
fi
exit 0