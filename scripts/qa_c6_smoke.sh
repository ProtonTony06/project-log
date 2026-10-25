#!/usr/bin/env bash
#
# qa_c6_smoke.sh — smokes [S] E6.16–E6.23 de C6 (tasks.md T6.17–T6.24)
#
# Codifica los escenarios del spec:
#   .atl/sdd/C6/spec.md (§2.5: E6.16–E6.23; pre-fase = smoke-of-the-smoke;
#   §2.6: 12 parejas mock↔psql) y .atl/sdd/C6/design.md (§5: helpers
#   PROPIOS réplica del esqueleto del smoke de C5 — NO invoca NUNCA scripts
#   previos porque su down de referencia usa '-v' y destruiría pgdata).
#   Patrones heredados (R-7/R-15/R-6.h/R6.27): recomposición ÚNICA con
#   --build --force-recreate (NUNCA down -v), psql -qAt, conteos RELATIVOS
#   antes/después de cada escenario, needles sufijados por ejecución,
#   smoke-of-the-smoke antes de la canónica.
#
#   R6.20-a: las agujas con '_' NUNCA van crudas en un LIKE de psql — los
#   needles psql usan '=' (igualdad) o position() (R6.20 heredó la deuda de
#   la S-3 de C4: E6.18 la cierra).
#
# Higiene runtime (R4.13 heredado): la KEY revelada del 201 y el JWT van a
# tmpdir 700 (trap de limpieza) y JAMÁS a consola. "NO revokes keys ajenas
# al smoke": este script NO llama a DELETE de ninguna key (la suya ni las
# del despliegue).
#
# Uso:
#   bash scripts/qa_c6_smoke.sh
#
# Salida:
#   Fases de progreso por stderr; al final una línea
#     E6.n: PASS | FAIL | BLOQUEADO (detalle)
#   por ID de escenario (orden de ID, ejecución en orden cronológico §2.6).
# Exit: 0 = todos PASS · 1 = hay algún FAIL · 2 = solo BLOQUEADOs
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE_URL="http://127.0.0.1:8080"
URL_LOGIN="$BASE_URL/api/v1/panel/auth/login"
URL_ME="$BASE_URL/api/v1/panel/me"
URL_PROJECTS="$BASE_URL/api/v1/panel/projects"
URL_INGEST="$BASE_URL/api/v1/ingest"
URL_BATCH="$BASE_URL/api/v1/ingest/batch"
URL_LOGS="$BASE_URL/api/v1/panel/logs"

TS="$(date +%Y%m%d%H%M%S)"
T_STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
A_NAME="smoke-c6-${TS}-a"
AGUJA_TIMEOUT="smoke_c6_${TS}_timeout"          # aguja con '_' literal (E6.18)
CONTRA_SIN_GUION="smoke-c6-${TS}-sin-guion-bajo" # contra-caso (E6.18)
# H9 (test-side, matrización ratificada por orchestrator): la contraguja del
# PLAN (CONTRA_SIN_GUION) NO discrimina escape real (con '_' comodín crudo
# el patrón `smoke?c6?TS?timeout` tampoco la matchearía). Esta SEGUNDA
# contraguja tiene la MISMA LONGITUD que la aguja y el MISMO sufijo
# '-timeout' sin '_': con escape correcto NO matchea (conteo==1 estricto);
# con '_' comodín SÍ matchearía (conteo==2 → FAIL). La contraguja nueva
# hace que el conteo==1 de E6.18 DISCRIMINE escape real de comodín crudo.
CONTRA_TIMEOUT="smoke-c6-${TS}-timeout"
DESC_VALIDA="descripción del smoke c6 ($TS)"

TMPDIR_QA="$(mktemp -d /tmp/qa_c6_smoke.XXXXXX)"
chmod 700 "$TMPDIR_QA" 2>/dev/null || true
# KEEP_TMP=1 (SOLO para depurar una corrida fallida): conserva el tmpdir 700;
# el operador debe borrarlo a mano AL ACABAR (contiene la key del smoke).
trap '[ "${KEEP_TMP:-0}" = "1" ] && echo "[trap] tmpdir CONSERVADO: $TMPDIR_QA (borrar a mano)" >&2 || rm -rf "$TMPDIR_QA"' EXIT
F_CUERPO="$TMPDIR_QA/cuerpo.txt"
F_CAB="$TMPDIR_QA/cabeceras.txt"
F_TOKEN="$TMPDIR_QA/token.txt"    # JWT del login (higiene: no se imprime)
F_ID_A="$TMPDIR_QA/id_a.txt"
F_KEY1="$TMPDIR_QA/key1.txt"      # key del proyecto 'a' — no consola
F_PAYLOAD="$TMPDIR_QA/payload.json"
F_PAYLOAD2="$TMPDIR_QA/payload2.json"

# Compose wrapper (heredado de C4/C5): 'podman compose' con timeout acotado.
COMPOSE_BIN=""
if command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
elif command -v podman-compose >/dev/null 2>&1; then
  COMPOSE_BIN="podman-compose"
elif command -v podman >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
fi
compose_() {
  local sub="$1" estado
  case "$sub" in
    up)   timeout --kill-after=10 620 $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log" ; estado=$? ;;
    down) timeout --kill-after=10 120 $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log" ; estado=$? ;;
    *)    $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log" ; estado=$? ;;
  esac
  if [ "$estado" -eq 124 ] || [ "$estado" -eq 137 ]; then
    pkill -f 'podman-compose up -d --build' 2>/dev/null || true
    pkill -f 'podman wait --condition=healthy' 2>/dev/null || true
  fi
  return "$estado"
}
PODMAN() { podman "$@" 2>>"$TMPDIR_QA/compose.log"; }

declare -a RESULTADOS=()
marca() { RESULTADOS+=("$1: $2${3:+ ($3)}"); }

# salud por podman (NUNCA curl /actuator como proxy de salud)
pila_saludable() {
  local snap estado e="" svc
  snap="$(PODMAN ps --format '{{.Names}} {{.Status}}' 2>/dev/null || true)"
  for svc in backend frontend postgres; do
    estado="$(grep -E "^[^ ]*${svc}[ _]" <<<"$snap" | head -n1)"
    grep -qE 'Up.*\(healthy\)' <<<"$estado" || e="$e $svc=$(echo "$estado" | head -c 60)"
  done
  [ -z "$e" ] && return 0 || { echo "$e" >&2; return 1; }
}

# esperar_healthy SEGUNDOS: sondea GET /me → 401 invalid_token real.
esperar_healthy() {
  local limite=$(( $(date +%s) + ${1:-240} ))
  while [ "$(date +%s)" -lt "$limite" ]; do
    if curl -sS --max-time 5 -o "$TMPDIR_QA/hc_body.txt" "$URL_ME" 2>/dev/null \
       && grep -q '"code"[[:space:]]*:[[:space:]]*"invalid_token"' "$TMPDIR_QA/hc_body.txt" 2>/dev/null; then
      return 0
    fi
    sleep 3
  done
  return 1
}

# psql_q SQL — salida -qAt limpia; SOLO para SQL SIN secretos.
psql_q() {
  compose_ exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 -qAt -c "$1"
}

# req VERBO URL CUERPO [CABECERA...] — fija CODIGO/CUERPO/CABECERAS globales.
# CUERPO con prefijo @ = enviar desde fichero (--data-binary @fichero).
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
  # depuración (SOLO tmpdir 700): sin secretos (keys viajan por header)
  printf '%s %s -> %s: %s\n' "$verbo" "$url" "$CODIGO" "$(head -c 200 <<<"$CUERPO" | tr '\n' ' ')" \
    >> "$TMPDIR_QA/req_debug.log"
}

# curl_ingest KEY — eco del header X-API-Key de las respuestas ingest (C5).
curl_ingest() { printf 'X-API-Key: %s' "$(cat "$1")"; }

# curl_get JWT — eco del header Authorization del GET de panel (E6.16+).
curl_get() { printf 'Authorization: Bearer %s' "$(cat "$1")"; }

# cursor_r_i TOKEN — decodifica el token (r,i); imprime '<r>|<i>' o falla.
# SIN f-string con \" (Python <3.12 prohíbe backslash dentro de la
# expresión del f-string → SyntaxError silencioso convertido en "FAIL").
cursor_r_i() {
  printf '%s' "$1" | python3 -c '
import sys, json, base64
tok = sys.stdin.read().strip()
try:
    d = json.loads(base64.urlsafe_b64decode(tok + "=" * (-len(tok) % 4)))
    print(d["r"] + "|" + str(d["i"]))
except Exception:
    sys.exit(1)'
}

# login USER PASS — POST de login canónico.
login() { req POST "$URL_LOGIN" "{\"username\":\"$1\",\"password\":\"$2\"}"; }

# bearer_hdr — eco del header Authorization bearer del JWT del tmpdir.
bearer_hdr() { curl_get "$F_TOKEN"; }

# campo_json CLAVE — valor tolerante (utf-8/replace) de CLAVE o falla.
campo_json() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
except Exception:
    sys.exit(3)
v = d.get(sys.argv[1]) if isinstance(d, dict) else None
if v is None:
    sys.exit(2)
print("true" if v is True else "false" if v is False else v)' "$1"
}

# claves_json — claves del objeto JSON, ordenadas y por coma.
claves_json() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
    assert isinstance(d, dict)
except Exception:
    sys.exit(3)
print(",".join(sorted(d.keys())))'
}

# claves_sin_field_sin_next — claves del objeto SIN field (E6.21/E6.20 404:
# el error SIN field y la clave next_cursor ausente/NULA se verifican con
# este helper: imprime field o exit 2 si NO existe la clave).
clave_existe() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
    assert isinstance(d, dict)
except Exception:
    sys.exit(3)
sys.exit(0 if sys.argv[1] in d else 2)' "$1"
}

# items_ids — ids de los items de la respuesta, ASC por coma (para
# comparación EXACTA con string_agg de psql, ORDER BY id).
items_ids() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
    ids = [it["id"] for it in d["items"]]
except Exception:
    sys.exit(3)
print(",".join(str(i) for i in sorted(ids)))'
}

# items_count — número de items de la respuesta (o error).
items_count() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
    print(len(d["items"]))
except Exception:
    sys.exit(3)'
}

# items_ids_ORDEN — ids de los items EN EL ORDEN DE ENTREGA (keyset) por
# coma — para la comparación ADYACENTE de E6.17 (twin psql ordena igual).
items_ids_orden() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
    ids = [it["id"] for it in d["items"]]
except Exception:
    sys.exit(3)
print(",".join(str(i) for i in ids))'
}

# item_campo INDICE CLAVE — campo del item INDICE (o falla).
item_campo() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
    print(d["items"][int(sys.argv[1])][sys.argv[2]])
except Exception:
    sys.exit(3)' "$1" "$2"
}

# next_cursor_tok — el next_cursor de la respuesta: imprime el TOKEN, o
# "NULL" si la clave existe EXPLÍCITA con null (R6.11/R6.24: la clave NUNCA
# se omite — este helper distingue null de ausente).
next_cursor_tok() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
    assert isinstance(d, dict) and "next_cursor" in d
except Exception:
    sys.exit(3)
v = d["next_cursor"]
print("NULL" if v is None else v)'
}

# norm_micro S — normaliza un instante a '...SS.ffffff' (fracción rellena
# a 6) para comparar el 'r' del token con el to_char de psql: Instant
# OMITE '.000000' (y usa 3 dígitos si no hay micros) — psql SIEMPRE imprime
# 6; a micros ambos representan lo mismo (R-6.a).
norm_micro() {
  printf '%s' "$1" | python3 -c '
import sys
s = sys.stdin.read().strip().replace("Z", "")
head, sep, frac = s.partition(".")
frac = frac[:6]
print(head + "." + (frac or "").ljust(6, "0"))' \
    2>/dev/null || printf '%s\n' "$1"
}

# capturar_id NAME ARCHIVO — busca el id del proyecto NAME en la lista actual.
capturar_id() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
for p in d:
    if p.get("name") == sys.argv[1]:
        print(p["id"]); sys.exit(0)
sys.exit(1)' "$1" > "$2"
}

# delta_logs BASE — count(*) del proyecto del smoke (conteo RELATIVO, R-6.h).
delta_cuenta() {
  psql_q "SELECT count(*) FROM logs WHERE project_id = $(cat "$F_ID_A")" \
    2>/dev/null || echo err
}

# rec_micro COND_SQL — received_at a micros (UTC) de la fila que cumple COND.
rec_micro() {
  psql_q "SELECT to_char(received_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US') FROM logs WHERE $1" \
    2>/dev/null || echo err
}

# ----------------------------------------------------------------------------
# 0. Precondiciones: anti-veneno + .env + recomposición ÚNICA (--build:
#    la imagen del backend debe CONTENER las clases C6 nuevas)
# ----------------------------------------------------------------------------
unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
      SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
      JWT_SECRET JWT_TTL_HOURS ADMIN_INITIAL_USERNAME ADMIN_INITIAL_PASSWORD \
      INGEST_MAX_REQUEST_BYTES INGEST_MAX_BATCH_SIZE WS_HEARTBEAT_SECONDS \
      FRONTEND_ORIGIN SPRING_PROFILES_ACTIVE LOGGING_LEVEL TZ 2>/dev/null || true

CON_ENV=0; CON_CRED=0; CON_BD=0
if [ -f .env ]; then
  CON_ENV=1
  while IFS= read -r _qa_linea || [ -n "$_qa_linea" ]; do
    case "$_qa_linea" in ''|\#*) continue ;; esac
    eval "$_qa_linea" 2>/dev/null || true
  done < ./.env
  [ -n "${JWT_TTL_HOURS:-}" ] || JWT_TTL_HOURS="8"
  if [ -n "${ADMIN_INITIAL_USERNAME:-}" ] && [ -n "${ADMIN_INITIAL_PASSWORD:-}" ]; then CON_CRED=1; fi
  if [ -n "${POSTGRES_USER:-}" ] && [ -n "${POSTGRES_DB:-}" ]; then CON_BD=1; fi
fi
bloqueo_env="(sin .env F1 del despliegue: credenciales/BD de la pila no disponibles)"

precondiciones() {
  echo "[pre] recomposición ÚNICA: podman compose up -d --build --force-recreate" >&2
  # --build y --force-recreate OBLIGATORIOS (R-7 heredado + C6: sin rebuild
  # los contenedores seguirían en la imagen vieja, SIN los endpoints /logs)
  if ! compose_ up -d --build --force-recreate; then
    echo "[pre] compose up falló (ver $TMPDIR_QA/compose.log)" >&2; return 1
  fi
  if ! esperar_healthy 420; then
    echo "[pre] la pila no llega a healthy (sondeo /me → 401 invalid_token)" >&2; return 1
  fi
  pila_saludable || { echo "[pre] pila no 3×healthy por podman" >&2; return 1; }
  # No-regresión light de C2/C6 (cadena 2 intocable — pareja [S] de E6.15):
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/me_body.txt" -w '%{http_code}' "$URL_ME" 2>/dev/null || echo 000)"
  # (a) sin JWT → 401 invalid_token
  [ "$hc" = "401" ] && grep -q 'invalid_token' "$TMPDIR_QA/me_body.txt" 2>/dev/null \
    || { echo "[pre] sondeo /me: $hc (esperado 401 invalid_token)" >&2; return 1; }
  # (b) con X-API-Key y SIN JWT → TAMBIÉN 401 invalid_token (la API key no
  #     autentica el panel — R6.3)
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/me_body2.txt" -w '%{http_code}' \
        -H 'X-API-Key: plk_basura_de_smoke' "$URL_ME" 2>/dev/null || echo 000)"
  [ "$hc" = "401" ] && grep -q 'invalid_token' "$TMPDIR_QA/me_body2.txt" 2>/dev/null \
    || { echo "[pre] sondeo /me con X-API-Key: $hc (esperado 401 invalid_token)" >&2; return 1; }
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    echo "[pre] login del admin falló (status ${CODIGO:-000})" >&2; return 1
  fi
  printf '%s' "$(campo_json token)" > "$F_TOKEN"
  echo "[pre] healthy + login OK (higiene: JWT solo en $TMPDIR_QA)" >&2
  return 0
}

if [ "$CON_ENV/$CON_CRED/$CON_BD" != "1/1/1" ]; then
  for id in E6.16 E6.17 E6.18 E6.19 E6.20 E6.21 E6.22 E6.23; do
    [ "$CON_ENV/$CON_CRED" = "1/1" ] || marca "$id" BLOQUEADO "$bloqueo_env"
  done
  ejecutar_pre=1
else
  ejecutar_pre=0
fi

# --- PRE-FASE (smoke-of-the-smoke, parte de E6.16): login + proyecto + key ---
pre_fase() {
  if ! req POST "$URL_PROJECTS" "{\"name\":\"$A_NAME\",\"description\":\"$DESC_VALIDA\"}" "$(bearer_hdr)" \
     || [ "$CODIGO" != "201" ]; then echo "[pre-fase] POST project=${CODIGO:-000}" >&2; return 1; fi
  # captura del id SIEMPRE (incondicional): sin cascada de BLOQUEADOs
  req GET "$URL_PROJECTS" "" "$(bearer_hdr)" || true
  capturar_id "$A_NAME" "$F_ID_A" || { echo "[pre-fase] id de $A_NAME no hallado" >&2; return 1; }
  if ! req POST "$URL_PROJECTS/$(cat "$F_ID_A")/api-key/regenerate" "" "$(bearer_hdr)" \
     || [ "$CODIGO" != "201" ]; then echo "[pre-fase] regenerate=${CODIGO:-000}" >&2; return 1; fi
  printf '%s' "$(campo_json key 2>/dev/null || echo '')" > "$F_KEY1"
  [ -s "$F_KEY1" ] || { echo "[pre-fase] key1 vacía" >&2; return 1; }
  echo "[pre-fase] proyecto + key OK (higiene: key solo en $TMPDIR_QA)" >&2
  return 0
}

# --- E6.16 — filtros combinables en pila + página sin filtros -----------------
case_e6_16() {
  echo "[E6.16] prod/ERROR devuelve SOLO las agujas; psql twin idéntico" >&2
  local id_a base r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E6.16 BLOQUEADO "sin id de -a (pre-fase)"; return 0; fi
  base="$(delta_cuenta)"
  # batch de agujas: k1/k2 production+ERROR, k3 production+WARN (distinguidos)
  python3 - "$F_PAYLOAD" "$T_STAMP" "$TS" <<'PYEOF'
import json, sys
out, t, ts = sys.argv[1], sys.argv[2], sys.argv[3]
recs = [
  {"level": "ERROR", "timestamp": t, "message": f"smoke-c6-{ts}-k1",
   "environment": "production", "correlation_id": "e616-k1", "source": "smoke-c6"},
  {"level": "ERROR", "timestamp": t, "message": f"smoke-c6-{ts}-k2",
   "environment": "production", "correlation_id": "e616-k2", "source": "smoke-c6"},
  {"level": "WARN", "timestamp": t, "message": f"smoke-c6-{ts}-k3",
   "environment": "production", "correlation_id": "e616-k3", "source": "smoke-c6"},
]
open(sys.argv[1], "w").write(json.dumps({"logs": recs}, separators=(",", ":")))
PYEOF
  if ! req POST "$URL_BATCH" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY1")"; then
    marca E6.16 FAIL "curl-ingesta"; return 0
  fi
  [ "$CODIGO" = "202" ] || { marca E6.16 FAIL "ingesta=$CODIGO"; return 0; }
  local e=""
  [ "$(delta_cuenta)" = "$((base + 3))" ] || e="$e ingesta-delta"
  # GET combinado: project_id + environment + level (los 3 combinables)
  if ! req GET "$URL_LOGS?project_id=$id_a&environment=production&level=ERROR" "" "$(bearer_hdr)"; then
    marca E6.16 FAIL "curl-lista"; return 0
  fi
  [ "$CODIGO" = "200" ] || e="$e combinado=$CODIGO"
  local rest_ids psql_ids
  rest_ids="$(items_ids)"
  # TWIN psql (igualdad estricta de sets — R6.20-a: el nivel/error va con =)
  psql_ids="$(psql_q "SELECT COALESCE(string_agg(id::text, ',' ORDER BY id), '') FROM logs WHERE project_id = $id_a AND environment = 'production' AND level = 'ERROR'" 2>/dev/null || echo err)"
  [ "$rest_ids" = "$psql_ids" ] || e="$e combinado-ids(rest=$rest_ids psql=$psql_ids)"
  [ -n "$rest_ids" ] || e="$e vacio-inesperado"
  # k3 (WARN) NO está en la página (distinguido por env/level)
  local id_k3
  id_k3="$(psql_q "SELECT id FROM logs WHERE project_id = $id_a AND correlation_id = 'e616-k3'" 2>/dev/null || echo err)"
  grep -qx "$id_k3" <<<"${rest_ids//,/ }" \
    && e="$e k3-siguió-en-combinado(no-distinguido)" || true
  # página SIN filtros: shape EXACTO {items, next_cursor} + 50 primeras
  if ! req GET "$URL_LOGS" "" "$(bearer_hdr)"; then
    marca E6.16 FAIL "curl-lista2"; return 0
  fi
  [ "$CODIGO" = "200" ] || e="$e sin-filtros=$CODIGO"
  [ "$(claves_json)" = "items,next_cursor" ] || e="$e shape-no-exacto($(claves_json 2>/dev/null || echo err))"
  [ "$(items_count)" = "50" ] || e="$e pag1-no-50($(items_count 2>/dev/null || echo err))"
  local tok
  tok="$(next_cursor_tok 2>/dev/null || echo err)"
  [ "$tok" != "NULL" ] && [ "$tok" != "err" ] && [ -n "$tok" ] || e="$e pag1-cursor-no-presente"
  [ -z "$e" ] && marca E6.16 PASS || marca E6.16 FAIL "$e"
  return 0
}

# --- E6.17 — keyset ESTABLE (el Done literal) ---------------------------------
case_e6_17() {
  echo "[E6.17] batch1(5) → pág1 limit=3 corta DENTRO del instante; batch2" >&2
  local id_a maxprev rec last_id base e=""
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E6.17 BLOQUEADO "sin id de -a"; return 0; fi
  maxprev="$(psql_q "SELECT COALESCE(MAX(id),0) FROM logs WHERE project_id = $id_a" 2>/dev/null || echo err)"
  if [ "$maxprev" = "err" ]; then marca E6.17 FAIL "psql-maxprev"; return 0; fi
  base="$(delta_cuenta)" # snapshot relativo (H4/QP-6.17: conteo R-6.h)
  # batch1: 5 logs EN UN SOLO batch (received_at COMÚN — regla 9)
  python3 - "$F_PAYLOAD" "$T_STAMP" <<'PYEOF'
import json, sys
t = sys.argv[2]
recs = [{"level": "INFO", "timestamp": t, "message": "E6.17-a",
         "environment": "production", "correlation_id": "e617a-%d" % i,
         "source": "smoke-c6"} for i in range(5)]
open(sys.argv[1], "w").write(json.dumps({"logs": recs}, separators=(",", ":")))
PYEOF
  if ! req POST "$URL_BATCH" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY1")"; then
    marca E6.17 FAIL "curl-batch1"; return 0
  fi
  [ "$CODIGO" = "202" ] || { marca E6.17 FAIL "batch1=$CODIGO"; return 0; }
  # H4/QP-6.17: delta tras batch1 = las 5 agujas nuevas (sin DELETE/UPDATE)
  [ "$(delta_cuenta)" = "$((base + 5))" ] || e="$e batch1-delta-no-5"
  # página 1 con limit=3: el corte DENTRO del mismo received_at
  local limite=3 rest_ids tok dec resto
  if ! req GET "$URL_LOGS?project_id=$id_a&limit=3" "" "$(bearer_hdr)"; then
    marca E6.17 FAIL "curl-pag1"; return 0
  fi
  rest_ids="$(items_ids)"
  [ "$(items_count)" = "3" ] || e="$e pag1-no-3"
  last_id="$(item_campo 2 id 2>/dev/null || echo err)"
  rec="$(rec_micro "project_id = $id_a AND id = $last_id")"
  [ "$rec" != "err" ] || e="$e rec-err"
  # el ÚLTIMO entregado comparte received_at (a micros) con filas NO entregadas
  resto="$(psql_q "SELECT count(*) FROM logs WHERE project_id = $id_a AND id < $last_id AND received_at = '${rec}Z'::timestamptz" 2>/dev/null || echo err)"
  if [ "$resto" = "err" ] || [ "$resto" = "0" ]; then
    # el guion: re-intentar con el límite mínimo que produzca el corte
    limite=2
    if ! req GET "$URL_LOGS?project_id=$id_a&limit=2" "" "$(bearer_hdr)"; then
      marca E6.17 FAIL "curl-pag1-reintento"; return 0
    fi
    rest_ids="$(items_ids)"
    [ "$(items_count)" = "2" ] || e="$e pag1-no-2"
    last_id="$(item_campo 1 id 2>/dev/null || echo err)"
    rec="$(rec_micro "project_id = $id_a AND id = $last_id")"
    resto="$(psql_q "SELECT count(*) FROM logs WHERE project_id = $id_a AND id < $last_id AND received_at = '${rec}Z'::timestamptz" 2>/dev/null || echo err)"
    [ "$resto" != "err" ] && [ "$resto" != "0" ] || e="$e corte-no-dentro-del-instante(resto=$resto)"
  fi
  # (a) el token decodificado: r == received_at COMPLETO a micros (no truncado)
  tok="$(next_cursor_tok 2>/dev/null || echo err)"
  [ "$tok" != "NULL" ] && [ "$tok" != "err" ] && [ -n "$tok" ] \
    || e="$e pag1-sin-cursor"
  local dec
  dec="$(cursor_r_i "$tok" 2>/dev/null || echo err)"
  [ "$dec" != "err" ] || e="$e cursor-no-decodificable"
  local rec_tok
  rec_tok="$(norm_micro "${dec%%|*}")"
  [ "$rec_tok" = "$(norm_micro "$rec")" ] || e="$e cursor-r-truncado/rec=$rec tok=$rec_tok"
  # (b) batch2 ENTRE páginas (received_at NUEVO más reciente)
  local maxprev2
  maxprev2="$(psql_q "SELECT COALESCE(MAX(id),0) FROM logs WHERE project_id = $id_a" 2>/dev/null || echo err)"
  python3 - "$F_PAYLOAD2" "$T_STAMP" <<'PYEOF'
import json, sys
t = sys.argv[2]
recs = [{"level": "INFO", "timestamp": t, "message": "E6.17-b",
         "environment": "production", "correlation_id": "e617b-%d" % i,
         "source": "smoke-c6"} for i in range(3)]
open(sys.argv[1], "w").write(json.dumps({"logs": recs}, separators=(",", ":")))
PYEOF
  if ! req POST "$URL_BATCH" "@$F_PAYLOAD2" "$(curl_ingest "$F_KEY1")"; then
    marca E6.17 FAIL "curl-batch2"; return 0
  fi
  [ "$CODIGO" = "202" ] || e="$e batch2=$CODIGO"
  # H4/QP-6.17: delta tras batch2 = las 5 del batch1 + las 3 nuevas
  [ "$(delta_cuenta)" = "$((base + 8))" ] || e="$e batch2-delta-no-8"
  # página 2 con el cursor de la 1 →
  if ! req GET "$URL_LOGS?project_id=$id_a&limit=$limite&cursor=$tok" "" "$(bearer_hdr)"; then
    marca E6.17 FAIL "curl-pag2"; return 0
  fi
  [ "$CODIGO" = "200" ] || e="$e pag2=$CODIGO"
  local pag2_ids pag2_orden
  pag2_ids="$(items_ids)"
  pag2_orden="$(items_ids_orden)"
  # NINGÚN id de página 1 reaparece / NINGÚN id del batch2 (más nuevo) sale
  local f
  for f in ${rest_ids//,/ }; do grep -qx "$f" <<<"${pag2_ids//,/ }" && e="$e pag1-id-repetido($f)" || true; done
  n_ids="$(psql_q "SELECT COALESCE(string_agg(id::text, ',' ORDER BY id), '') FROM logs WHERE project_id = $id_a AND id > $maxprev2" 2>/dev/null || echo err)"
  local b
  for b in ${n_ids//,/ }; do grep -qx "$b" <<<"${pag2_ids//,/ }" && e="$e batch2-id-en-pag2($b)" || true; done
  # la PRIMERA fila de página 2 es la ADYACENTE bajo el MISMO received_at:
  # twin psql con la semántica EXACTA del cursor (estamento §1.G; el 'r' del
  # token ya llega con 'Z') — igualdad de ORDEN keyset (no solo set)
  local twin first2
  twin="$(psql_q "SELECT COALESCE(string_agg(id::text, ',' ORDER BY received_at DESC, id DESC), '') FROM (SELECT id, received_at FROM logs WHERE project_id = $id_a AND (received_at < '${dec%%|*}'::timestamptz OR (received_at = '${dec%%|*}'::timestamptz AND id < $last_id)) ORDER BY received_at DESC, id DESC LIMIT $limite) sub" 2>/dev/null || echo err)"
  [ "$pag2_orden" = "$twin" ] || e="$e pag2-no-adyacente(rest=$pag2_orden twin=$twin)"
  first2="$(item_campo 0 id 2>/dev/null || echo err)"
  [ "$(rec_micro "project_id = $id_a AND id = $first2")" = "$rec" ] \
    || e="$e pag2-primera-no-mismo-instante(id=$first2 rec=$rec)"
  [ -z "$e" ] && marca E6.17 PASS || marca E6.17 FAIL "$e"
  return 0
}

# --- E6.18 — aguja '_' de ILIKE (cierre de la S-3 de C4) -----------------------
case_e6_18() {
  echo "[E6.18] text=smoke_c6_<TS>_timeout → SOLO la aguja; contras fuera" >&2
  local id_a base r r2 ids id_contra2
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E6.18 BLOQUEADO "sin id de -a"; return 0; fi
  base="$(delta_cuenta)"
  local e=""
  # aguja (con '_' literal), contra-caso del plan (mismo prefijo, SIN '_')
  # y contraguja H9 (MISMA LONGITUD + sufijo '-timeout', sin '_')
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"$AGUJA_TIMEOUT\",\"environment\":\"production\",\"correlation_id\":\"e618a\",\"source\":\"smoke-c6\"}" "$(curl_ingest "$F_KEY1")" || true
  [ "$CODIGO" = "202" ] || e="$e aguja=$CODIGO"
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"$CONTRA_SIN_GUION\",\"environment\":\"production\",\"correlation_id\":\"e618b\",\"source\":\"smoke-c6\"}" "$(curl_ingest "$F_KEY1")" || true
  [ "$CODIGO" = "202" ] || e="$e contra=$CODIGO"
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"$CONTRA_TIMEOUT\",\"environment\":\"production\",\"correlation_id\":\"e618c\",\"source\":\"smoke-c6\"}" "$(curl_ingest "$F_KEY1")" || true
  [ "$CODIGO" = "202" ] || e="$e contra2=$CODIGO"
  # delta = 3 (aguja + las 2 contras — la 3.ª es la matrización H9 del plan,
  # ratificada por orchestrator: el conteo RELATIVO R-6.h se mantiene sobre
  # agujas del propio smoke, filas heredadas intocadas)
  [ "$(delta_cuenta)" = "$((base + 3))" ] || e="$e delta-no-3"
  # needle con '_' → patronIlike escapa → SOLO la aguja (conteo=1 estricto)
  if ! req GET "$URL_LOGS?project_id=$id_a&text=$AGUJA_TIMEOUT" "" "$(bearer_hdr)"; then
    marca E6.18 FAIL "curl-lista"; return 0
  fi
  [ "$CODIGO" = "200" ] || e="$e lista=$CODIGO"
  [ "$(items_count)" = "1" ] || e="$e conteo-no-1($(items_count 2>/dev/null || echo err))"
  # el ÚNICO item es la aguja (igualdad psql por '=' — NUNCA LIKE crudo,
  # R6.20-a)
  local id_aguja id_item
  id_aguja="$(psql_q "SELECT id FROM logs WHERE project_id = $id_a AND message = '$AGUJA_TIMEOUT'" 2>/dev/null || echo err)"
  id_item="$(item_campo 0 id 2>/dev/null || echo err)"
  [ "$id_item" = "$id_aguja" ] || e="$e item-no-es-la-aguja($id_item-vs-$id_aguja)"
  # el contra-caso del plan existe (fila en BD) PERO no matchea el needle
  r="$(psql_q "SELECT count(*) FROM logs WHERE project_id = $id_a AND message = '$CONTRA_SIN_GUION'" 2>/dev/null || echo err)"
  [ "$r" = "1" ] || e="$e contra-no-existe($r)"
  # (la respuesta ya tiene conteo=1 y su id es el de la aguja: el contra-caso
  # NO matchea por construcción del conteo estricto.)
  # H9 (contraguja DISCRIMINANTE): misma longitud que la aguja y MISMO
  # sufijo '-timeout' sin '_'. Existe en BD por '=' (igual, R6.20-a) y su id
  # JAMÁS aparece en la página (con escape OK: el patrón '\_' no cabe en un
  # texto sin '_'; con '_' comodín crudo SÍ matchearía → el conteo==1 de
  # arriba y este grep lo DETECTARÍAN)
  id_contra2="$(psql_q "SELECT id FROM logs WHERE project_id = $id_a AND message = '$CONTRA_TIMEOUT'" 2>/dev/null || echo err)"
  [ "$id_contra2" != "err" ] && [ -n "$id_contra2" ] || e="$e contra2-psql-err"
  r2="$(psql_q "SELECT count(*) FROM logs WHERE project_id = $id_a AND message = '$CONTRA_TIMEOUT'" 2>/dev/null || echo err)"
  [ "$r2" = "1" ] || e="$e contra2-no-existe($r2)"
  ids="$(items_ids)"
  grep -qx "$id_contra2" <<<"${ids//,/ }" \
    && e="$e contra2-matcheada-por-comodin($id_contra2)" || true
  [ -z "$e" ] && marca E6.18 PASS || marca E6.18 FAIL "$e"
  return 0
}

# --- E6.19 — from/to con offset NO-UTC e inclusive ------------------------------
case_e6_19() {
  echo "[E6.19] from==to con +02:00 equivalente a Z; extremos inclusivos" >&2
  local id_a base r rec rec_off e
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E6.19 BLOQUEADO "sin id de -a"; return 0; fi
  base="$(delta_cuenta)"
  # fila real en pila: la frontera from == received_at de ESTA aguja
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E6.19\",\"environment\":\"production\",\"correlation_id\":\"e619\",\"source\":\"smoke-c6\"}" "$(curl_ingest "$F_KEY1")" || true
  [ "$CODIGO" = "202" ] || { marca E6.19 FAIL "ingesta=$CODIGO"; return 0; }
  [ "$(delta_cuenta)" = "$((base + 1))" ] || { marca E6.19 FAIL "delta-no-1"; return 0; }
  rec="$(rec_micro "project_id = $id_a AND correlation_id = 'e619'")"
  [ "$rec" != "err" ] && [ -n "$rec" ] || { marca E6.19 FAIL "rec-no-capturado"; return 0; }
  # el equivalente +02:00 (misma instantánea, offset NO-UTC)
  rec_off="$(printf '%s' "$rec" | python3 -c '
import sys, datetime
s = sys.stdin.read().strip()
dt = datetime.datetime.fromisoformat(s).replace(
    tzinfo=datetime.timezone.utc)
print(dt.astimezone(
    datetime.timezone(datetime.timedelta(hours=2))).isoformat())')"
  local from_off
  from_off="${rec_off//+/%2B}"   # '+' crudo en query = espacio: encódalo
  e=""
  # (a) rango de punto con offset NO-UTC: INCLUYE la fila (from inclusive)
  if ! req GET "$URL_LOGS?project_id=$id_a&from=$from_off&to=$from_off" "" "$(bearer_hdr)"; then
    marca E6.19 FAIL "curl-offset"; return 0
  fi
  [ "$CODIGO" = "200" ] || e="$e offset=$CODIGO"
  local ids_off
  ids_off="$(items_ids)"
  # 'rec' de to_char es NAIVE (sin offset): en query y psql lleva 'Z';
  # en from/to REST naive → 400 (por eso el 'Z' explícito)
  # (b) el MISMO rango en Z: resultados IDENTICOS
  if ! req GET "$URL_LOGS?project_id=$id_a&from=${rec}Z&to=${rec}Z" "" "$(bearer_hdr)"; then
    marca E6.19 FAIL "curl-z"; return 0
  fi
  local ids_z
  ids_z="$(items_ids)"
  [ "$ids_off" = "$ids_z" ] || e="$e offset-no-equivalente"
  # twin psql: rango [from, to] inclusive por AMBOS extremos
  local ids_psql
  ids_psql="$(psql_q "SELECT COALESCE(string_agg(id::text, ',' ORDER BY id), '') FROM logs WHERE project_id = $id_a AND received_at >= '${rec}Z'::timestamptz AND received_at <= '${rec}Z'::timestamptz" 2>/dev/null || echo err)"
  [ "$ids_off" = "$ids_psql" ] || e="$e twin-distinto(rest=$ids_off psql=$ids_psql)"
  local id_frontera
  id_frontera="$(psql_q "SELECT id FROM logs WHERE project_id = $id_a AND correlation_id = 'e619'" 2>/dev/null || echo err)"
  grep -qE "(^|,)$id_frontera(,|$)" <<<"$ids_off" || e="$e frontera-no-inclusive($id_frontera)"
  [ -z "$e" ] && marca E6.19 PASS || marca E6.19 FAIL "$e"
  return 0
}

# --- E6.20 — detalle en pila (11 campos) + 404 real ------------------------------
case_e6_20() {
  echo "[E6.20] detalle: exception completo + metadata objeto + 404 not_found" >&2
  local id_a r e base
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E6.20 BLOQUEADO "sin id de -a"; return 0; fi
  base="$(delta_cuenta)" # snapshot relativo (H5/QP-6.20: conteo R-6.h)
  e=""
  req POST "$URL_INGEST" "{\"level\":\"ERROR\",\"timestamp\":\"$T_STAMP\",\"message\":\"E6.20\",\"environment\":\"production\",\"exception\":\"java.lang.NullPointerException: boom (E6.20)\",\"metadata\":{\"span\":\"checkout\",\"attempt\":2},\"correlation_id\":\"e620\",\"source\":\"smoke-c6\"}" "$(curl_ingest "$F_KEY1")" || true
  [ "$CODIGO" = "202" ] || e="$e ingesta=$CODIGO"
  # H5/QP-6.20: delta = la UNA aguja del detalle (sin DELETE/UPDATE)
  [ "$(delta_cuenta)" = "$((base + 1))" ] || e="$e delta-no-1"
  local log_id ts_render rec_seg
  log_id="$(psql_q "SELECT id FROM logs WHERE project_id = $id_a AND correlation_id = 'e620' ORDER BY id DESC LIMIT 1" 2>/dev/null || echo err)"
  [ "$log_id" != "err" ] && [ -n "$log_id" ] || { marca E6.20 FAIL "id-no-hallado"; return 0; }
  if ! req GET "$URL_LOGS/$log_id" "" "$(bearer_hdr)"; then
    marca E6.20 FAIL "curl-detalle"; return 0
  fi
  [ "$CODIGO" = "200" ] || e="$e detalle=$CODIGO"
  # las 11 claves EXACTAS (sorted)
  [ "$(claves_json)" = "correlation_id,environment,exception,id,level,message,metadata,project_id,received_at,source,timestamp" ] \
    || e="$e claves-no-11($(claves_json 2>/dev/null || echo err))"
  # exception = texto COMPLETO
  [ "$(campo_json exception 2>/dev/null || echo err)" = "java.lang.NullPointerException: boom (E6.20)" ] \
    || e="$e exception-incompleto"
  # metadata = el OBJETO EXACTO (no el String crudo del jsonb)
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
m = d.get("metadata")
ok = isinstance(m, dict) and m == {"span": "checkout", "attempt": 2}
sys.exit(0 if ok else 1)' || e="$e metadata-no-objeto-exacto"
  # H3/QP-6.3 (R-6.i): las fechas del DETALLE render C2 truncado a SEGUNDOS
  # (yyyy-MM-dd'T'HH:mm:ss'Z'), pinnadas sobre la fila sembrada por ESTE
  # test: `timestamp` == el T_STAMP ingestado y `received_at` == el to_char
  # de psql truncado a segundos de ESA misma fila (un render con
  # fracciones/milis no encajaría en ninguna de las 2 igualdades)
  ts_render="$(campo_json timestamp 2>/dev/null || echo err)"
  [ "$ts_render" = "$T_STAMP" ] || e="$e timestamp-no-es-el-sembrado($ts_render)"
  ts_render="$(campo_json received_at 2>/dev/null || echo err)"
  printf '%s' "$ts_render" | grep -qE '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' \
    || e="$e received_at-no-formato-C2($ts_render)"
  rec_seg="$(psql_q "SELECT to_char(received_at AT TIME ZONE 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM logs WHERE id = $log_id" 2>/dev/null || echo err)"
  [ "$ts_render" = "$rec_seg" ] || e="$e received_at-no-truncado-a-segundos($ts_render-vs-$rec_seg)"
  # (b) 404 id inexistente
  if ! req GET "$URL_LOGS/999999999" "" "$(bearer_hdr)"; then
    marca E6.20 FAIL "curl-404"; return 0
  fi
  [ "$CODIGO" = "404" ] || e="$e 404=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "not_found" ] || e="$e code"
  [ "$(campo_json message 2>/dev/null || echo err)" = "No existe (quizás fue eliminado)" ] \
    || e="$e message"
  clave_existe field 2>/dev/null && e="$e field-inesperado" || true
  [ -z "$e" ] && marca E6.20 PASS || marca E6.20 FAIL "$e"
  return 0
}

# --- E6.21 — los 5 códigos 400 reales en pila (cuerpos EXACTOS R6.22) ------------
case_e6_21() {
  echo "[E6.21] 5 variantes 400 con cuerpos EXACTOS (codes/field/message)" >&2
  local id_a base
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E6.21 BLOQUEADO "sin id de -a"; return 0; fi
  # H6/QP-6.21: snapshot ANTES de los 400 — la fase es 100% GET y el plan lo
  # declara [M]: "los 400 NO insertan ni modifican nada (delta 0)"
  base="$(delta_cuenta)"
  local e=""
  # (a) from > to → filter_from_to_inverted (SIN field)
  req GET "$URL_LOGS?from=2026-01-02T00:00:00Z&to=2026-01-01T00:00:00Z" "" "$(bearer_hdr)" || true
  [ "$CODIGO" = "400" ] || e="$e a=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "filter_from_to_inverted" ] || e="$e a-code"
  [ "$(campo_json message 2>/dev/null || echo err)" = "El rango temporal no es válido: from es posterior a to" ] || e="$e a-msg"
  clave_existe field 2>/dev/null && e="$e a-field-inesperado" || true
  # (b) level=error → filter_invalid_value (level, SIN normalización)
  req GET "$URL_LOGS?level=error" "" "$(bearer_hdr)" || true
  [ "$CODIGO" = "400" ] || e="$e b=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "filter_invalid_value" ] || e="$e b-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "level" ] || e="$e b-field"
  [ "$(campo_json message 2>/dev/null || echo err)" = "Valor de level no válido; use TRACE, DEBUG, INFO, WARN, ERROR o FATAL" ] || e="$e b-msg"
  # (c) limit=0 → filter_limit_invalid (limit)
  req GET "$URL_LOGS?limit=0" "" "$(bearer_hdr)" || true
  [ "$CODIGO" = "400" ] || e="$e c=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "filter_limit_invalid" ] || e="$e c-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "limit" ] || e="$e c-field"
  [ "$(campo_json message 2>/dev/null || echo err)" = "El filtro limit debe ser un número entre 1 y 200" ] || e="$e c-msg"
  # (d) cursor=basura → filter_cursor_invalid (cursor)
  req GET "$URL_LOGS?cursor=basura" "" "$(bearer_hdr)" || true
  [ "$CODIGO" = "400" ] || e="$e d=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "filter_cursor_invalid" ] || e="$e d-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "cursor" ] || e="$e d-field"
  [ "$(campo_json message 2>/dev/null || echo err)" = "El parámetro cursor no es válido" ] || e="$e d-msg"
  # (e) project_id=abc → filter_invalid_value (project_id)
  req GET "$URL_LOGS?project_id=abc" "" "$(bearer_hdr)" || true
  [ "$CODIGO" = "400" ] || e="$e e=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "filter_invalid_value" ] || e="$e e-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "project_id" ] || e="$e e-field"
  [ "$(campo_json message 2>/dev/null || echo err)" = "El filtro project_id debe ser un número" ] || e="$e e-msg"
  # H6/QP-6.21: la BD quedó EXACTAMENTE igual (delta 0 — los 400 jamás
  # insertan/modifican; igualdad relativa R-6.h contra el snapshot)
  [ "$(delta_cuenta)" = "$base" ] || e="$e 400-mutaron-BD($(delta_cuenta)-vs-$base)"
  [ -z "$e" ] && marca E6.21 PASS || marca E6.21 FAIL "$e"
  return 0
}

# --- E6.22 — limit en frontera (200/201/default) --------------------------------
case_e6_22() {
  echo "[E6.22] limit=200 → 200 items; 201 → 400; sin limit → 50" >&2
  local id_a r total e
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E6.22 BLOQUEADO "sin id de -a"; return 0; fi
  total="$(psql_q "SELECT count(*) FROM logs" 2>/dev/null || echo err)"
  [ "$total" != "err" ] && [ "$total" -ge 201 ] 2>/dev/null \
    || { marca E6.22 BLOQUEADO "pila con <$total filas: frontera 200 no comprobable"; return 0; }
  e=""
  # (a) limit=200: items EXACTOS = 200 (global, sin filtros) + cursor presente
  if ! req GET "$URL_LOGS?limit=200" "" "$(bearer_hdr)"; then
    marca E6.22 FAIL "curl-200"; return 0
  fi
  [ "$CODIGO" = "200" ] || e="$e limit200=$CODIGO"
  [ "$(items_count)" = "200" ] || e="$e items-no-200($(items_count 2>/dev/null || echo err))"
  local tok dec
  tok="$(next_cursor_tok 2>/dev/null || echo err)"
  [ "$tok" != "NULL" ] && [ "$tok" != "err" ] && [ -n "$tok" ] || e="$e cursor-no-presente-con-201"
  # el token de frontera es DECODIFICABLE (r,i)
  dec="$(cursor_r_i "$tok" 2>/dev/null || echo err)"
  [ "$dec" != "err" ] || e="$e cursor-no-decodificable"
  # (b) limite=201 → 400 exacto
  req GET "$URL_LOGS?limit=201" "" "$(bearer_hdr)" || true
  [ "$CODIGO" = "400" ] || e="$e limit201=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "filter_limit_invalid" ] || e="$e limit201-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "limit" ] || e="$e limit201-field"
  # H7/QP-6.22: el message VERBATIM de la tabla (regla global §1 del plan:
  # todo [M] pina HTTP + CUERPO code/message/field — aquí faltaba el message)
  [ "$(campo_json message 2>/dev/null || echo err)" = "El filtro limit debe ser un número entre 1 y 200" ] || e="$e limit201-msg"
  # (c) SIN limit → defecto 50 (global)
  if ! req GET "$URL_LOGS" "" "$(bearer_hdr)"; then
    marca E6.22 FAIL "curl-defecto"; return 0
  fi
  [ "$(items_count)" = "50" ] || e="$e defecto-no-50($(items_count 2>/dev/null || echo err))"
  [ -z "$e" ] && marca E6.22 PASS || marca E6.22 FAIL "$e"
  return 0
}

# --- E6.23 — fin de resultados y recorrido completo con cursor -------------------
case_e6_23() {
  echo "[E6.23] walk de TODAS las páginas (limit=2); sin dupes ni saltos" >&2
  local id_a base maxprev e
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E6.23 BLOQUEADO "sin id de -a"; return 0; fi
  maxprev="$(psql_q "SELECT COALESCE(MAX(id),0) FROM logs WHERE project_id = $id_a" 2>/dev/null || echo err)"
  base="$(delta_cuenta)"
  # batch aguja (UN SOLO batch → received_at COMÚN)
  python3 - "$F_PAYLOAD" "$T_STAMP" <<'PYEOF'
import json, sys
t = sys.argv[2]
recs = [{"level": "INFO", "timestamp": t, "message": "E6.23",
         "environment": "production", "correlation_id": "e623-%d" % i,
         "source": "smoke-c6"} for i in range(5)]
open(sys.argv[1], "w").write(json.dumps({"logs": recs}, separators=(",", ":")))
PYEOF
  if ! req POST "$URL_BATCH" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY1")"; then
    marca E6.23 FAIL "curl-batch"; return 0
  fi
  [ "$CODIGO" = "202" ] || { marca E6.23 FAIL "batch=$CODIGO"; return 0; }
  # el batch COMPARTIÓ received_at (psql: 1 distinct)
  local r
  r="$(psql_q "SELECT (count(DISTINCT received_at) = 1) FROM logs WHERE project_id = $id_a AND id > $maxprev" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || { marca E6.23 FAIL "batch-received_at-no-comun($r)"; return 0; }
  # recorrido COMPLETO: encadenar cursor hasta next_cursor null EXPLÍCITO
  local cursor="" tok paginas=0 walked="" n_it
  e=""
  while [ "$paginas" -lt 60 ]; do
    paginas=$((paginas + 1))
    if ! req GET "$URL_LOGS?project_id=$id_a&limit=2${cursor:+&cursor=$cursor}" "" "$(bearer_hdr)"; then
      marca E6.23 FAIL "curl-pag$paginas"; return 0
    fi
    [ "$CODIGO" = "200" ] || { e="$e pag$paginas=$CODIGO"; break; }
    n_it="$(items_count 2>/dev/null || echo err)"
    [ "$n_it" != "err" ] || { e="$e pag$paginas-items-ilegible"; break; }
    # PAGINA intermedia: EXACTO limite=2 (la última CON items puede traer menos)
    walked="$walked$(items_ids),"
    tok="$(next_cursor_tok 2>/dev/null || echo err)"
    if [ "$tok" = "NULL" ]; then break; fi    # fin de resultados: null EXPLÍCITO
    [ "$tok" != "err" ] && [ -n "$tok" ] || { e="$e pag$paginas-cursor-ausente"; break; }
    [ "$n_it" = "2" ] || e="$e pag$paginas-no-2($n_it)"
    cursor="$tok"
  done
  [ "$paginas" -lt 60 ] || e="$e walk-no-termina-60"
  [ -n "$walked" ] || e="$e walked-vacio"
  local walked_n psql_ids dupes
  walked_n="$(printf '%s' "$walked" | tr ',' '\n' | sed '/^$/d' | sort -n | paste -sd,)"
  dupes="$(printf '%s' "$walked" | tr ',' '\n' | sed '/^$/d' | sort -n | uniq -d | tr '\n' ',')"
  [ -z "$dupes" ] || e="$e ids-duplicados($dupes)"
  # cobertura COMPLETA: los ids entregados == LOS DE LA BD del proyecto
  psql_ids="$(psql_q "SELECT COALESCE(string_agg(id::text, ',' ORDER BY id), '') FROM logs WHERE project_id = $id_a" 2>/dev/null || echo err)"
  [ "$walked_n" = "$psql_ids" ] || e="$e cobertura-no-completa(rest=$walked_n psql=$psql_ids)"
  # el batch de la aguja completo está (sub-set: los 5 ids > maxprev)
  local batch_ids b
  batch_ids="$(psql_q "SELECT COALESCE(string_agg(id::text, ',' ORDER BY id), '') FROM logs WHERE project_id = $id_a AND id > $maxprev" 2>/dev/null || echo err)"
  for b in ${batch_ids//,/ }; do grep -qx "$b" <(printf '%s' "$walked" | tr ',' '\n' | sed '/^$/d') || e="$e batch-id-faltante($b)" || true; done
  [ -z "$e" ] && marca E6.23 PASS || marca E6.23 FAIL "$e"
  return 0
}

ejecutar() { # $1 ID $2 función
  if ! "$2" 2>>"$TMPDIR_QA/errores_internos.log"; then
    RESULTADOS+=("$1: FAIL (error interno del script; ver errores_internos)")
  fi
}

if [ "$ejecutar_pre" = "0" ]; then
  precondiciones || {
    for id in E6.16 E6.17 E6.18 E6.19 E6.20 E6.21 E6.22 E6.23; do
      marca "$id" BLOQUEADO "precondiciones fallidas (ver stderr)"
    done
  }
fi

# ORDEN DE EJECUCIÓN (cronológico §2.5): la pre-fase es parte de E6.16; cada
# escenario ingesta SUS agujas (received_at creciente → los rangos de E6.19
# y el keyset de E6.17 son deterministas en ejecución secuencial).
if [ "$ejecutar_pre" = "0" ]; then
  if ! pre_fase 2>>"$TMPDIR_QA/errores_internos.log"; then
    for id in E6.16 E6.17 E6.18 E6.19 E6.20 E6.21 E6.22 E6.23; do
      marca "$id" BLOQUEADO "pre-fase fallida (ver stderr)"
    done
  else
    ejecutar E6.16 case_e6_16
    ejecutar E6.17 case_e6_17
    ejecutar E6.18 case_e6_18
    ejecutar E6.19 case_e6_19
    ejecutar E6.20 case_e6_20
    ejecutar E6.21 case_e6_21
    ejecutar E6.22 case_e6_22
    ejecutar E6.23 case_e6_23
  fi
fi

echo "=== VEREDICTOS QA C6 (por ID del spec) ==="
if [ "${#RESULTADOS[@]}" -gt 0 ]; then
  printf '%s\n' "${RESULTADOS[@]}" | sort -V
fi

state_exit=0
if printf '%s\n' "${RESULTADOS[@]:-}" | grep -q ': FAIL'; then
  state_exit=1
elif printf '%s\n' "${RESULTADOS[@]:-}" | grep -q ': BLOQUEADO'; then
  state_exit=2
fi
exit "$state_exit"