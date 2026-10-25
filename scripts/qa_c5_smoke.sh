#!/usr/bin/env bash
#
# qa_c5_smoke.sh — smokes [S] E5.21–E5.33 de C5 (tasks.md T5.18/T5.20)
#
# Codifica los escenarios del spec:
#   .atl/sdd/C5/spec.md (§2.5: E5.21–E5.33; pre-fase = smoke-of-the-smoke;
#   §2.6 R-12) y .atl/sdd/C5/design.md (§5: helpers PROPIOS réplica del
#   esqueleto del smoke de C4 — NO invoca NUNCA los scripts previos porque
#   su down de referencia usa '-v' y destruiría el volumen con datos).
#   Patrones heredados de C3/C4 (R-7/R-15): recomposición ÚNICA con
#   --force-recreate, psql -qAt, booleanos t/f (psql) ≠ true/false (JSON),
#   conteos RELATIVOS antes/después de cada escenario, nombres sufijados por
#   ejecución, smoke-of-the-smoke antes de la canónica.
#
# Higiene runtime (R4.13 heredado de C4): la KEY revelada de cada 201 y el
# JWT van a tmpdir 700 (trap de limpieza) y JAMÁS a consola. En psql la key
# solo existe como dato ENTRANTE por stdin de psql (psql_stdin).
#
# Uso:
#   bash scripts/qa_c5_smoke.sh
#
# Salida:
#   Fases de progreso por stderr; al final una línea
#     E5.n: PASS | FAIL | BLOQUEADO (detalle)
#   por ID de escenario (orden de ID, ejecución en orden cronológico del
#   guion — ver ORDEN DE EJECUCIÓN).
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

TS="$(date +%Y%m%d%H%M%S)"
T_STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
A_NAME="smoke-c5-${TS}-a"
DESC_VALIDA="descripción del smoke c5 ($TS)"

TMPDIR_QA="$(mktemp -d /tmp/qa_c5_smoke.XXXXXX)"
chmod 700 "$TMPDIR_QA" 2>/dev/null || true
trap 'rm -rf "$TMPDIR_QA"' EXIT
F_CUERPO="$TMPDIR_QA/cuerpo.txt"
F_CAB="$TMPDIR_QA/cabeceras.txt"
F_TOKEN="$TMPDIR_QA/token.txt"    # JWT del login (higiene: no se imprime)
F_ID_A="$TMPDIR_QA/id_a.txt"
F_KEY1="$TMPDIR_QA/key1.txt"      # 1.ª key (muere revocada en E5.24c) — no consola
F_KEY2="$TMPDIR_QA/key2.txt"      # 2.ª key (la del G-2/E5.31) — no consola
F_PAYLOAD="$TMPDIR_QA/payload.json"
F_PAYLOAD2="$TMPDIR_QA/payload2.json"

# Compose wrapper (heredado de C4): 'podman compose' con timeout acotado.
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

# psql_stdin — SQL ENTRENTE POR STDIN (la key no aparece en argv/console/log).
psql_stdin() {
  printf '%s\n' "$1" | compose_ exec -T postgres psql -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -qAt
}

# esc_key ARCHIVO — key del tmpdir con comillas simples escapadas ('→'').
esc_key() {
  sed "s/'/''/g" "$1"
}

# req VERBO URL CUERPO [CABECERA...] — fija CODIGO/CUERPO/CABECERAS globales.
# CUERPO con prefijo @ = enviar desde fichero (--data-binary @fichero).
# Tolerante a curl exit != 0 (p. ej. conexión cerrada temprana por el 413
# de la R-12: curl puede reportar reset DESPUÉS de recibir la respuesta —
# el veredicto se hace sobre el código/cuerpo capturado, no sobre el exit).
req() {
  local verbo="$1" url="$2" cuerpo="$3"; shift 3
  local args=(-sS --max-time 30 -o "$F_CUERPO" -D "$F_CAB" -w '%{http_code}' -X "$verbo")
  if [ -n "$cuerpo" ]; then
    args+=(-H 'Content-Type: application/json')
    # el @ en la llamada marca "carga por fichero": DEBE llegar a curl con el
    # @ (smoke-of-the-smoke: sin él curl enviaba la RUTA como cuerpo y todo
    # lote grande llegaba vacío → batch_empty/environment_required)
    args+=(--data-binary "$cuerpo")
  fi
  local _h
  for _h in "$@"; do args+=(-H "$_h"); done
  local cod
  cod="$(curl "${args[@]}" "$url" 2>>"$TMPDIR_QA/curl_err.log" || true)"
  CODIGO="${cod:-000}"
  CUERPO="$(cat "$F_CUERPO" 2>/dev/null || true)"
  CABECERAS="$(cat "$F_CAB" 2>/dev/null || true)"
  # depuración del smoke-of-the-smoke: cuerpo acotado (sin secretos: las keys
  # viajan por header y jamás por cuerpo) — log solo en tmpdir 700
  printf '%s %s -> %s: %s\n' "$verbo" "$url" "$CODIGO" "$(head -c 200 <<<"$CUERPO" | tr '\n' ' ')" \
    >> "$TMPDIR_QA/req_debug.log"
}

# curl_ingest KEY — eco del header X-API-Key de las respuestas ingest (C5).
curl_ingest() { printf 'X-API-Key: %s' "$(cat "$1")"; }

# ---- Ruta DIRECTA al backend (desviación E2E documentada, T5.18/T5.20) ------
# El proxy nginx del frontend (frontend/** INTOCABLE, R5.19) NO define
# client_max_body_size → default 1 MiB: TODO cuerpo >1048576 muere en nginx
# con 413 HTML ANTES de llegar al backend, y el gate de C5 (413
# payload_too_large del guard) se vuelve inalcanzable por esa puerta. Los 3
# cuerpos que EXCEDE el límite (E5.27 grande 1048577, E5.28 chunked 1048590,
# E5.33 grande) se envían DIRECTO al contenedor backend con un curl efímero
# DENTRO de la red de la pila (mismo contrato del guard: barrera 1 por
# Content-Length y barrera 2 por stream). La ruta por nginx (8080) queda
# para todo lo demás (la puerta REAL del endpoint).
CURL_IMAGEN="docker.io/curlimages/curl:8.11.0"
DIRECTO_NETO=""
DIRECTO_HABILITADO=0

preparar_ruta_directa() {
  local cont neto img_en_store
  cont="$(PODMAN ps --format '{{.Names}}' 2>/dev/null | grep -E 'backend' | head -n1 || true)"
  if [ -z "$cont" ]; then
    echo "[directo] sin contenedor backend → ruta directa DESHABILITADA" >&2
    return 0
  fi
  neto="$(PODMAN inspect -f \
    '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$cont" 2>/dev/null || true)"
  if [ -z "$neto" ]; then
    echo "[directo] sin red de la pila → ruta directa DESHABILITADA" >&2
    return 0
  fi
  # imagen efímera: si no está en el store, pull silencioso; si falla, disabled
  img_en_store="$(PODMAN image exists "$CURL_IMAGEN" && echo si || echo no)"
  if [ "$img_en_store" = "no" ]; then
    PODMAN pull -q "$CURL_IMAGEN" >/dev/null 2>&1 || \
      { echo "[directo] imagen curl no disponible → ruta directa DISABLED" >&2; return 0; }
  fi
  DIRECTO_NETO="$neto"
  DIRECTO_HABILITADO=1
  echo "[directo] ruta directa habilitada (red $neto, imagen $CURL_IMAGEN)" >&2
  return 0
}

# req_directo F_KEY F_BAS RUTA [CL|CHUNKED] — POST directo al contenedor
# backend: fija CODIGO/CUERPO (mismo contrato global que req). F_KEY y F_BAS
# viven en el tmpdir 700 (higiene R4.13: la key entra al contenedor vía
# -H @fichero — ps del anfitrión NO la ve —; el cuerpo va montado, jamás por
# argv). CHUNKED fuerza Transfer-Encoding (barrera 2, sin Content-Length).
req_directo() {
  local f_key="$1" f_bas="$2" ruta="$3" modo="${4:-CL}" cod
  local f_body="/data/${f_bas##*/}"
  local args=(-sS --max-time 30 -o /data/cuerpo.txt -w '%{http_code}'
              -X POST -H 'Content-Type: application/json'
              -H '@/data/hdr_directo.txt')
  if [ "$modo" = "CHUNKED" ]; then
    args+=(-H 'Transfer-Encoding: chunked')
  fi
  printf 'X-API-Key: %s' "$(cat "$f_key" 2>/dev/null || echo '')" \
    > "$TMPDIR_QA/hdr_directo.txt"
  chmod 600 "$TMPDIR_QA/hdr_directo.txt" 2>/dev/null || true
  : > "$F_CUERPO"
  if [ "$DIRECTO_HABILITADO" != "1" ]; then
    CODIGO="000"; CUERPO=""; CABECERAS=""
    echo "[directo] DESHABILITADO (llamado sin ruta habilitada)" >&2
    return 0
  fi
  cod="$(PODMAN run --rm --user 0:0 --network "$DIRECTO_NETO" -v "$TMPDIR_QA:/data:Z" \
    "$CURL_IMAGEN" "${args[@]}" \
    --data-binary "@$f_body" \
    "http://backend:8080$ruta" 2>>"$TMPDIR_QA/curl_err.log" || true)"
  cod="$(printf '%s' "${cod:-}" | tr -cd '0-9' | head -c 3)"
  CODIGO="${cod:-000}"
  CUERPO="$(cat "$F_CUERPO" 2>/dev/null || true)"
  CABECERAS=""
}

# login USER PASS — POST de login canónico.
login() { req POST "$URL_LOGIN" "{\"username\":\"$1\",\"password\":\"$2\"}"; }

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

# bearer_hdr — eco del header Authorization bearer del JWT del tmpdir.
bearer_hdr() { printf 'Authorization: Bearer %s' "$(cat "$F_TOKEN")"; }

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

# delta_logs BASE — count(*) del proyecto del smoke (conteo RELATIVO, R-15).
delta_cuenta() {
  psql_q "SELECT count(*) FROM logs WHERE project_id = $(cat "$F_ID_A")" \
    2>/dev/null || echo err
}

# gen_pl_padding N FICHERO — registro individual con campo DESCONOCIDO
# "_padding" (S5-2: las claves desconocidas JAMÁS se leen ni persisten) para
# llevar el cuerpo EXACTAMENTE a N bytes JSON (padding LEGAL, E5.27).
gen_pl_padding() {
  python3 - "$1" "$2" "$T_STAMP" <<'PYEOF'
import json, sys
n, out, ts = int(sys.argv[1]), sys.argv[2], sys.argv[3]
base = {"level": "INFO", "timestamp": ts, "message": "E5.27",
        "environment": "production", "correlation_id": "e527",
        "source": "smoke-c5"}
s = json.dumps(base, separators=(",", ":"), ensure_ascii=True)
abierto = ',"_padding":"'
cierre = '"}'
k = n - (len(s) - 1) - len(abierto) - len(cierre)
if k < 1:
    sys.exit("padding negativo para n=%d" % n)
data = s[:-1] + abierto + "x" * k + cierre
raw = data.encode("utf-8")
assert len(raw) == n, "cuerpo %d != objetivo %d" % (len(raw), n)
open(out, "wb").write(raw)
PYEOF
}

# ----------------------------------------------------------------------------
# 0. Precondiciones: anti-veneno + .env + recomposición ÚNICA
# ----------------------------------------------------------------------------
# Anti-veneno de env: UNSET de las variables de compose en el proceso (el .env
# del despliegue es la ÚNICA fuente).
unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
      SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
      JWT_SECRET JWT_TTL_HOURS ADMIN_INITIAL_USERNAME ADMIN_INITIAL_PASSWORD \
      INGEST_MAX_REQUEST_BYTES INGEST_MAX_BATCH_SIZE WS_HEARTBEAT_SECONDS \
      FRONTEND_ORIGIN SPRING_PROFILES_ACTIVE LOGGING_LEVEL TZ 2>/dev/null || true

CON_ENV=0; CON_CRED=0; CON_BD=0
if [ -f .env ]; then
  CON_ENV=1
  # Carga ROBUSTA línea a línea SIN exportar (herencia C2/C3/C4).
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
  # --force-recreate OBLIGATORIO (R-7 heredado): sin recreate los contenedores
  # seguirían en la imagen vieja (sin los endpoints de C5).
  if ! compose_ up -d --build --force-recreate; then
    echo "[pre] compose up falló (ver $TMPDIR_QA/compose.log)" >&2; return 1
  fi
  if ! esperar_healthy 420; then
    echo "[pre] la pila no llega a healthy (sondeo /me → 401 invalid_token)" >&2; return 1
  fi
  pila_saludable || { echo "[pre] pila no 3×healthy por podman" >&2; return 1; }
  preparar_ruta_directa
  # No-regresión light de C2 (cadena 2 intacta):
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/me_body.txt" -w '%{http_code}' "$URL_ME" 2>/dev/null || echo 000)"
  [ "$hc" = "401" ] && grep -q 'invalid_token' "$TMPDIR_QA/me_body.txt" 2>/dev/null \
    || { echo "[pre] sondeo /me: $hc (esperado 401 invalid_token)" >&2; return 1; }
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    echo "[pre] login del admin falló (status ${CODIGO:-000})" >&2; return 1
  fi
  printf '%s' "$(campo_json token)" > "$F_TOKEN"
  echo "[pre] healthy + login OK (higiene: JWT solo en $TMPDIR_QA)" >&2
  return 0
}

if [ "$CON_ENV/$CON_CRED/$CON_BD" != "1/1/1" ]; then
  for id in E5.21 E5.22 E5.23 E5.24 E5.25 E5.26 E5.27 E5.28 E5.29 E5.30 \
            E5.31 E5.32 E5.33; do
    [ "$CON_ENV/$CON_CRED" = "1/1" ] || marca "$id" BLOQUEADO "$bloqueo_env"
  done
  ejecutar_pre=1
else
  ejecutar_pre=0
fi

# --- PRE-FASE (smoke-of-the-smoke, parte de E5.21): login + proyecto + key ---
pre_fase() {
  echo "[pre-fase] GET /me sin token (401 invalid_token, no-regresión light)" >&2
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/me_body.txt" -w '%{http_code}' "$URL_ME" 2>/dev/null || echo 000)"
  [ "$hc" = "401" ] && grep -q 'invalid_token' "$TMPDIR_QA/me_body.txt" 2>/dev/null \
    || { echo "[pre-fase] sondeo /me: $hc" >&2; return 1; }
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" \
     || [ "$CODIGO" != "200" ]; then echo "[pre-fase] login" >&2; return 1; fi
  printf '%s' "$(campo_json token 2>/dev/null || echo '')" > "$F_TOKEN"
  [ -s "$F_TOKEN" ] || { echo "[pre-fase] token vacío" >&2; return 1; }
  if ! req POST "$URL_PROJECTS" "{\"name\":\"$A_NAME\",\"description\":\"$DESC_VALIDA\"}" "$(bearer_hdr)" \
     || [ "$CODIGO" != "201" ]; then echo "[pre-fase] POST project=${CODIGO:-000}" >&2; return 1; fi
  # captura del id SIEMPRE (incondicional): sin cascada de BLOQUEADOs
  req GET "$URL_PROJECTS" "" "$(bearer_hdr)" || true
  capturar_id "$A_NAME" "$F_ID_A" || { echo "[pre-fase] id de $A_NAME no hallado" >&2; return 1; }
  if ! req POST "$URL_PROJECTS/$(cat "$F_ID_A")/api-key/regenerate" "" "$(bearer_hdr)" \
     || [ "$CODIGO" != "201" ]; then echo "[pre-fase] regenerate=${CODIGO:-000}" >&2; return 1; fi
  printf '%s' "$(campo_json key 2>/dev/null || echo '')" > "$F_KEY1"
  [ -s "$F_KEY1" ] || { echo "[pre-fase] key1 vacía" >&2; return 1; }
  echo "[pre-fase] proyecto + key1 OK (higiene: key solo en $TMPDIR_QA)" >&2
  return 0
}

# --- E5.21 — happy individual: 202 {"accepted":1} EXACTO + fila en psql -------
case_e5_21() {
  echo "[E5.21] POST /ingest → 202 {\"accepted\":1}; fila con campos exactos" >&2
  [ -s "$F_ID_A" ] || { marca E5.21 BLOQUEADO "sin id de -a (pre-fase)"; return 0; }
  local base
  base="$(delta_cuenta)"
  if ! req POST "$URL_INGEST" "{\"level\":\"ERROR\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.21\",\"environment\":\"production\",\"correlation_id\":\"e521\",\"source\":\"smoke-c5\",\"metadata\":{\"span\":\"checkout\"}}" "$(curl_ingest "$F_KEY1")"; then
    marca E5.21 FAIL "curl"; return 0
  fi
  local e="" cuenta r
  [ "$CODIGO" = "202" ] || e="$e status=$CODIGO"
  [ "$CUERPO" = '{"accepted":1}' ] || e="$e cuerpo-no-exacto($CUERPO)"
  cuenta="$(delta_cuenta)"
  [ "$cuenta" = "$((base + 1))" ] || e="$e delta=$cuenta-vs-$base(no-1)"
  # la fila NUEVA: level/env/correlation_id/source/message/project_id EXACTOS
  r="$(psql_q "SELECT (project_id = $(cat "$F_ID_A") AND level = 'ERROR' AND environment = 'production' AND correlation_id = 'e521' AND source = 'smoke-c5' AND message = 'E5.21') FROM logs WHERE project_id = $(cat "$F_ID_A") AND correlation_id = 'e521' ORDER BY id DESC LIMIT 1" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e fila-no-exacta($r)"
  [ -z "$e" ] && marca E5.21 PASS || marca E5.21 FAIL "$e"
  return 0
}

# --- E5.22 — batch de 200: 202, delta=200, received_at común y ORDEN ----------
case_e5_22() {
  echo "[E5.22] batch de 200 → 202; 1 received_at; ids en orden de VALUES" >&2
  local id_a maxprev r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.22 BLOQUEADO "sin id de -a"; return 0; fi
  maxprev="$(psql_q "SELECT COALESCE(MAX(id),0) FROM logs WHERE project_id = $id_a" 2>/dev/null || echo err)"
  python3 - "$F_PAYLOAD" "$T_STAMP" <<'PYEOF'
import json, sys
niveles = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"]
envs = ["development", "staging", "production"]
recs = []
for i in range(200):
    recs.append({"level": niveles[i % 6], "timestamp": sys.argv[2],
                 "message": "E5.22-%03d" % i, "environment": envs[i % 3],
                 "correlation_id": "e522-%03d" % i, "source": "smoke-c5"})
open(sys.argv[1], "w").write(json.dumps({"logs": recs}, separators=(",", ":")))
PYEOF
  if ! req POST "$URL_BATCH" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY1")"; then
    marca E5.22 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "202" ] || e="$e status=$CODIGO"
  [ "$CUERPO" = '{"accepted":200}' ] || e="$e cuerpo-no-200"
  # delta = 200 con igualdad EXACTA (regla 9)
  r="$(psql_q "SELECT (count(*) = 200) FROM logs WHERE project_id = $id_a AND id > $maxprev AND correlation_id LIKE 'e522-%'" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e delta-no-200($r)"
  # count(DISTINCT received_at) = 1 SOBRE los 200 ids nuevos (regla 9)
  r="$(psql_q "SELECT (count(DISTINCT received_at) = 1) FROM logs WHERE project_id = $id_a AND id > $maxprev AND correlation_id LIKE 'e522-%'" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e received_at-no-1($r)"
  # ids crecen en ORDEN de VALUES: correlación id ASC ↔ correlation_id ASC
  r="$(psql_q "WITH n AS (SELECT correlation_id, ROW_NUMBER() OVER (ORDER BY id) r1, ROW_NUMBER() OVER (ORDER BY correlation_id) r2 FROM logs WHERE project_id = $id_a AND id > $maxprev AND correlation_id LIKE 'e522-%') SELECT (count(*) FILTER (WHERE r1 <> r2) = 0) FROM n" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e orden-no-values($r)"
  [ -z "$e" ] && marca E5.22 PASS || marca E5.22 FAIL "$e"
  return 0
}

# --- E5.23 — all-or-nothing: registro inválido INTERMEDIO; NADA persiste ------
case_e5_23() {
  echo "[E5.23] batch con level XX en índice 2 → 400 index:2; delta=0" >&2
  local id_a base r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.23 BLOQUEADO "sin id de -a"; return 0; fi
  base="$(delta_cuenta)"
  local T_SUF
  T_SUF="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  python3 - "$T_SUF" > "$F_PAYLOAD" <<'PYEOF'
import json, sys
valido = {"level": "INFO", "timestamp": sys.argv[1], "message": "E5.23",
          "environment": "production", "correlation_id": "e523", "source": "s"}
indice2 = {"level": "XX", "timestamp": sys.argv[1], "message": "E5.23",
           "environment": "production", "correlation_id": "e523-2", "source": "s"}
print(json.dumps({"logs": [valido, valido, indice2, valido, valido]},
                 separators=(",", ":")))
PYEOF
  if ! req POST "$URL_BATCH" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY1")"; then
    marca E5.23 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "400" ] || e="$e status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "level_invalid" ] || e="$e code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "level" ] || e="$e field"
  [ "$(campo_json index 2>/dev/null || echo err)" = "2" ] || e="$e index-no-2"
  r="$(delta_cuenta)"
  [ "$r" = "$base" ] || e="$e delta-no-0($r-vs-$base)"
  [ -z "$e" ] && marca E5.23 PASS || marca E5.23 FAIL "$e"
  return 0
}

# --- E5.24 — 401 en 3 variantes con EL MISMO cuerpo; revocar y regenerar ------
case_e5_24() {
  echo "[E5.24] 401 sin-header/basura/revocada — mismo cuerpo; luego key2" >&2
  local id_a cuerpo_ref tres
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.24 BLOQUEADO "sin id de -a"; return 0; fi
  local e=""
  # (a) sin header
  if ! req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.24\",\"environment\":\"production\"}"; then
    marca E5.24 FAIL "curl-a"; return 0
  fi
  [ "$CODIGO" = "401" ] || e="$e a=$CODIGO"
  cuerpo_ref="$CUERPO"
  grep -q 'api_key_invalid' <<<"$cuerpo_ref" || e="$e a-sin-code"
  if grep -q 'invalid_token' <<<"$cuerpo_ref"; then e="$e a-con-literal-panel"; fi
  # (b) header basura
  if ! req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.24\",\"environment\":\"production\"}" \
     "X-API-Key: plk_basura_no_existente"; then
    marca E5.24 FAIL "curl-b"; return 0
  fi
  [ "$CODIGO" = "401" ] || e="$e b=$CODIGO"
  [ "$CUERPO" = "$cuerpo_ref" ] || e="$e cuerpo-b-distinto"
  # (c) revocar la key con la MISMA key viva — DELETE con JWT → 204
  if ! req DELETE "$URL_PROJECTS/$id_a/api-key" "" "$(bearer_hdr)"; then
    marca E5.24 FAIL "curl-c"; return 0
  fi
  [ "$CODIGO" = "204" ] || e="$e revoke=$CODIGO"
  # regla 11: hash/prefix/key_created_at → NULL juntos (boolean crudo t/f)
  tres="$(psql_q "SELECT (api_key_hash IS NULL AND api_key_prefix IS NULL AND key_created_at IS NULL) FROM projects WHERE id = $id_a" 2>/dev/null || echo err)"
  [ "$tres" = "t" ] || e="$e tres-no-NULL($tres)"
  # la key REVOCADA ya no matchea → 401 con el mismo cuerpo
  if ! req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.24\",\"environment\":\"production\"}" "$(curl_ingest "$F_KEY1")"; then
    marca E5.24 FAIL "curl-c-ingest"; return 0
  fi
  [ "$CODIGO" = "401" ] || e="$e c=$CODIGO"
  [ "$CUERPO" = "$cuerpo_ref" ] || e="$e cuerpo-c-distinto"
  # Luego regenerate → 201 con key2 (las 3 variantes ya comprobadas)
  if ! req POST "$URL_PROJECTS/$id_a/api-key/regenerate" "" "$(bearer_hdr)" \
     || [ "$CODIGO" != "201" ]; then
    marca E5.24 FAIL "regenerate2=${CODIGO:-000}"; return 0
  fi
  printf '%s' "$(campo_json key 2>/dev/null || echo '')" > "$F_KEY2"
  [ -s "$F_KEY2" ] || e="$e key2-vacia"
  grep -Eq '^plk_[A-Za-z0-9_-]{43}$' "$F_KEY2" 2>/dev/null || e="$e key2-forma"
  if cmp -s "$F_KEY1" "$F_KEY2"; then e="$e key2==key1"; fi
  [ -z "$e" ] && marca E5.24 PASS || marca E5.24 FAIL "$e"
  return 0
}

# --- E5.25 — 403 project_archived con key VÁLIDA; delta=0 ----------------------
case_e5_25() {
  echo "[E5.25] PATCH ARCHIVED → ingest key2 → 403 literal; delta=0" >&2
  local id_a base r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.25 BLOQUEADO "sin id de -a"; return 0; fi
  if ! req PATCH "$URL_PROJECTS/$id_a" '{"status":"ARCHIVED"}' "$(bearer_hdr)" \
     || [ "$CODIGO" != "200" ]; then
    # PATCH canónico (C3): 200
    marca E5.25 FAIL "patch=${CODIGO:-000}"; return 0
  fi
  base="$(delta_cuenta)"
  if ! req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.25\",\"environment\":\"production\"}" "$(curl_ingest "$F_KEY2")"; then
    marca E5.25 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "403" ] || e="$e status=$CODIGO(esperado-403)"
  [ "$(campo_json code 2>/dev/null || echo err)" = "project_archived" ] || e="$e code"
  [ "$(campo_json message 2>/dev/null || echo err)" = "El proyecto está archivado: no acepta logs" ] \
    || e="$e message(literal-ingesta)"
  r="$(delta_cuenta)"
  [ "$r" = "$base" ] || e="$e delta-no-0($r-vs-$base)"
  [ -z "$e" ] && marca E5.25 PASS || marca E5.25 FAIL "$e"
  return 0
}

# --- E5.31 (G-2, cierre ACTIVO) — desarchivar → la MISMA key2 → 202 ------------
case_e5_31() {
  echo "[E5.31] G-2: PATCH ACTIVE → ingest con la MISMA key2 → 202" >&2
  local id_a base r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.31 BLOQUEADO "sin id de -a"; return 0; fi
  if ! req GET "$URL_PROJECTS/$id_a" "" "$(bearer_hdr)"; then
    marca E5.31 FAIL "curl-ficha"; return 0
  fi
  # sanity del guion: el proyecto QUEDÓ archivado de E5.25
  [ "$(campo_json status 2>/dev/null || echo err)" = "ARCHIVED" ] \
    || { marca E5.31 FAIL "precondición: proyecto no ARCHIVED"; return 0; }
  if ! req PATCH "$URL_PROJECTS/$id_a" '{"status":"ACTIVE"}' "$(bearer_hdr)" \
     || [ "$CODIGO" != "200" ]; then
    marca E5.31 FAIL "patch=${CODIGO:-000}"; return 0
  fi
  base="$(delta_cuenta)"
  if ! req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.31\",\"environment\":\"production\",\"correlation_id\":\"e531\"}" "$(curl_ingest "$F_KEY2")"; then
    marca E5.31 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "202" ] || e="$e status=$CODIGO"
  [ "$CUERPO" = '{"accepted":1}' ] || e="$e cuerpo-no-1"
  r="$(delta_cuenta)"
  [ "$r" = "$((base + 1))" ] || e="$e delta-no-1($r-vs-$base)"
  [ -z "$e" ] && marca E5.31 PASS || marca E5.31 FAIL "$e"
  return 0
}

# --- E5.26 — 400 reales (ACTIVE + key2): env ausente y timestamp naive ---------
case_e5_26() {
  echo "[E5.26] 400 environment_required y timestamp_without_timezone; delta=0" >&2
  local id_a base r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.26 BLOQUEADO "sin id de -a"; return 0; fi
  base="$(delta_cuenta)"
  # (a) environment ausente
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.26\",\"correlation_id\":\"e526a\"}" "$(curl_ingest "$F_KEY2")" || true
  local e=""
  [ "$CODIGO" = "400" ] || e="$e env=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "environment_required" ] || e="$e env-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "environment" ] || e="$e env-field"
  # (b) timestamp naive (sin offset)
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"2026-10-25T13:00:00\",\"message\":\"E5.26\",\"environment\":\"production\",\"correlation_id\":\"e526b\"}" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "400" ] || e="$e ts=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "timestamp_without_timezone" ] || e="$e ts-code"
  r="$(delta_cuenta)"
  [ "$r" = "$base" ] || e="$e delta-no-0($r-vs-$base)"
  [ -z "$e" ] && marca E5.26 PASS || marca E5.26 FAIL "$e"
  return 0
}

# --- E5.27 — 413 frontera EXACTA: 1048576 → 202; 1048577 → 413 ----------------
case_e5_27() {
  echo "[E5.27] 413 frontera: 1048576 (incl.) → 202; 1048577 → 413" >&2
  local id_a base r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.27 BLOQUEADO "sin id de -a"; return 0; fi
  local e=""
  # 1048576 bytes EXACTOS (barrera INCLUSIVA; padding en clave desconocida)
  if ! gen_pl_padding 1048576 "$F_PAYLOAD"; then
    marca E5.27 FAIL "payload-gen"; return 0
  fi
  base="$(delta_cuenta)"
  req POST "$URL_INGEST" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "202" ] || e="$e legal=$CODIGO(esperado-202-inclusivo)"
  r="$(delta_cuenta)"
  # el registro legal se persiste SI la barrera es inclusiva (delta = 1)
  [ "$r" = "$((base + 1))" ] || e="$e delta-legal=$r-vs-$base"
  # 1048577 bytes → 413 payload_too_large (barrera 1, Content-Length)
  if ! gen_pl_padding 1048577 "$F_PAYLOAD2"; then
    marca E5.27 FAIL "payload-gen2"; return 0
  fi
  base="$r"
  # >1048576: nginx (default 1 MiB, frontend INTOCABLE) intercepta con 413
  # HTML ANTES del backend → la barrera 1 del guard se comprueba DIRECTO
  # (desviación E2E documentada; ver cabecera del bloque req_directo)
  if [ "$DIRECTO_HABILITADO" != "1" ]; then
    marca E5.27 BLOQUEADO "ruta directa al backend no disponible (nginx 1MiB)"
    return 0
  fi
  req_directo "$F_KEY2" "$F_PAYLOAD2" "/api/v1/ingest" || true
  [ "$CODIGO" = "413" ] || e="$e grande=$CODIGO"
  grep -q 'payload_too_large' <<<"$CUERPO" || e="$e sin-needle-413"
  if grep -q 'invalid_token' <<<"$CUERPO"; then e="$e needle-panel-en-413"; fi
  r="$(delta_cuenta)"
  [ "$r" = "$base" ] || e="$e delta-413-no-0($r-vs-$base)"
  [ -z "$e" ] && marca E5.27 PASS || marca E5.27 FAIL "$e"
  return 0
}

# --- E5.28 — 413 chunked: SIN Content-Length, stream > límite → 413 -----------
case_e5_28() {
  echo "[E5.28] 413 chunked (2.ª barrera): sin Content-Length → 413" >&2
  local id_a base r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.28 BLOQUEADO "sin id de -a"; return 0; fi
  if ! gen_pl_padding 1048590 "$F_PAYLOAD"; then
    marca E5.28 FAIL "payload-gen"; return 0
  fi
  base="$(delta_cuenta)"
  # chunked DIRECTO (nginx default 1 MiB interceptaría; desviación E2E doc.):
  # sin Content-Length → la barrera 2 (stream contado) es la que responde 413
  if [ "$DIRECTO_HABILITADO" != "1" ]; then
    marca E5.28 BLOQUEADO "ruta directa al backend no disponible (nginx 1MiB)"
    return 0
  fi
  req_directo "$F_KEY2" "$F_PAYLOAD" "/api/v1/ingest" CHUNKED || true
  CODIGO="${CODIGO:-000}"; CUERPO="$(cat "$F_CUERPO" 2>/dev/null || true)"
  local e=""
  [ "$CODIGO" = "413" ] || e="$e status=$CODIGO"
  grep -q 'payload_too_large' <<<"$CUERPO" || e="$e sin-needle-413"
  r="$(delta_cuenta)"
  [ "$r" = "$base" ] || e="$e delta-no-0($r-vs-$base)"
  [ -z "$e" ] && marca E5.28 PASS || marca E5.28 FAIL "$e"
  return 0
}

# --- E5.29 — confianza timestamp: +02:00 se normaliza a UTC; received_at ≥ T1 -
case_e5_29() {
  echo "[E5.29] timestamptz: +02:00 → UTC exacto; received_at ≥ T1" >&2
  local id_a r t1
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.29 BLOQUEADO "sin id de -a"; return 0; fi
  # T1 = instante previo al request (el received_at lo impone el SERVIDOR —
  # el humo NO lo controla; T1 sirve de piso). Resolución de now() es de
  # segundos: se tolera 1 s de troncamiento (FLOOR) de received_at.
  t1="$(date +%s)"
  # 2026-10-25T15:00:00+02:00 == 2026-10-25T13:00:00Z
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"2026-10-25T15:00:00+02:00\",\"message\":\"E5.29\",\"environment\":\"production\",\"correlation_id\":\"e529\"}" "$(curl_ingest "$F_KEY2")" || true
  local e=""
  [ "$CODIGO" = "202" ] || e="$e status=$CODIGO"
  r="$(psql_q "SELECT (timestamp = '2026-10-25T13:00:00Z'::timestamptz) FROM logs WHERE project_id = $id_a AND correlation_id = 'e529' AND message = 'E5.29' ORDER BY id DESC LIMIT 1" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e timestamp-no-utc($r)"
  r="$(psql_q "SELECT (FLOOR(EXTRACT(EPOCH FROM received_at))::bigint >= $t1 - 1) FROM logs WHERE project_id = $id_a AND correlation_id = 'e529' AND message = 'E5.29' ORDER BY id DESC LIMIT 1" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e received_at-antes-de-T1($r)"
  [ -z "$e" ] && marca E5.29 PASS || marca E5.29 FAIL "$e"
  return 0
}

# --- E5.30 — JSONB + fronteras de bytes de metadata y message ------------------
case_e5_30() {
  echo "[E5.30] metadata JSONB + fronteras 65536/65537 y 32768/32769" >&2
  local id_a r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.30 BLOQUEADO "sin id de -a"; return 0; fi
  local e=""
  # (a) metadata {"span":"checkout"} → 202; psql: metadata->>'span' = checkout
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.30\",\"environment\":\"production\",\"correlation_id\":\"e530a\",\"metadata\":{\"span\":\"checkout\"}}" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "202" ] || e="$e a=$CODIGO"
  r="$(psql_q "SELECT (metadata->>'span' = 'checkout') FROM logs WHERE project_id = $id_a AND correlation_id = 'e530a' ORDER BY id DESC LIMIT 1" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e jsonb-no-checkout($r)"
  # (b) metadata array → 400 metadata_not_object
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.30\",\"environment\":\"production\",\"correlation_id\":\"e530b\",\"metadata\":[1,2]}" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "400" ] || e="$e b=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "metadata_not_object" ] || e="$e b-code"
  # (c) frontera metadata: serialized 65536 → 202 | 65537 → 400 (12+len)
  local T_SUTC
  T_SUTC="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  python3 - "$F_PAYLOAD" "$T_SUTC" <<'PYEOF'
import json, sys
meta = {"clave": "m" * 65524}  # {"clave":"…"} = 12 + len(valor)
rec = {"level": "INFO", "timestamp": sys.argv[2], "message": "E5.30",
       "environment": "production", "correlation_id": "e530c",
       "metadata": meta}
open(sys.argv[1], "w").write(json.dumps(rec, separators=(",", ":")))
PYEOF
  req POST "$URL_INGEST" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "202" ] || e="$e c-65536=$CODIGO"
  python3 - "$F_PAYLOAD" "$T_SUTC" <<'PYEOF'
import json, sys
meta = {"clave": "m" * 65525}  # 65537 bytes serializados
rec = {"level": "INFO", "timestamp": sys.argv[2], "message": "E5.30",
       "environment": "production", "correlation_id": "e530c2",
       "metadata": meta}
open(sys.argv[1], "w").write(json.dumps(rec, separators=(",", ":")))
PYEOF
  req POST "$URL_INGEST" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "400" ] || e="$e c-65537=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "field_too_long" ] || e="$e c-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "metadata" ] || e="$e c-field"
  # (d) frontera message: 32768 bytes (multibyte) → 202 | 32769 → 400
  python3 - "$F_PAYLOAD" "$T_SUTC" <<'PYEOF'
import json, sys
rec = {"level": "INFO", "timestamp": sys.argv[2],
       "message": "ñ" * 16384,  # 32768 bytes (bytes ≠ chars)
       "environment": "production", "correlation_id": "e530d"}
open(sys.argv[1], "wb").write(json.dumps(rec, separators=(",", ":"),
                                         ensure_ascii=False).encode("utf-8"))
PYEOF
  req POST "$URL_INGEST" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "202" ] || e="$e d-32768=$CODIGO"
  python3 - "$F_PAYLOAD" "$T_SUTC" <<'PYEOF'
import json, sys
rec = {"level": "INFO", "timestamp": sys.argv[2],
       "message": "x" * 32769,
       "environment": "production", "correlation_id": "e530d2"}
open(sys.argv[1], "w").write(json.dumps(rec, separators=(",", ":")))
PYEOF
  req POST "$URL_INGEST" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "400" ] || e="$e d-32769=$CODIGO"
  [ "$(campo_json field 2>/dev/null || echo err)" = "message" ] || e="$e d-field"
  [ -z "$e" ] && marca E5.30 PASS || marca E5.30 FAIL "$e"
  return 0
}

# --- E5.32 — fronteras de lote: [] y 201 → batch_empty / batch_too_large ------
case_e5_32() {
  echo "[E5.32] batch [] → batch_empty; 201 → batch_too_large; delta=0" >&2
  local id_a base r
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E5.32 BLOQUEADO "sin id de -a"; return 0; fi
  base="$(delta_cuenta)"
  local e=""
  req POST "$URL_BATCH" '{"logs":[]}' "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "400" ] || e="$e vacio=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "batch_empty" ] || e="$e vacio-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "logs" ] || e="$e vacio-field"
  python3 - "$F_PAYLOAD" <<'PYEOF'
import json, sys
open(sys.argv[1], "w").write(json.dumps({"logs": [{} for _ in range(201)]},
                                        separators=(",", ":")))
PYEOF
  req POST "$URL_BATCH" "@$F_PAYLOAD" "$(curl_ingest "$F_KEY2")" || true
  [ "$CODIGO" = "400" ] || e="$e 201=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "batch_too_large" ] || e="$e 201-code"
  [ "$(campo_json message 2>/dev/null || echo err)" = "El campo logs no puede contener más de 200 registros" ] \
    || e="$e 201-mensaje-limite"
  [ "$(campo_json field 2>/dev/null || echo err)" = "logs" ] || e="$e 201-field"
  r="$(delta_cuenta)"
  [ "$r" = "$base" ] || e="$e delta-no-0($r-vs-$base)"
  [ -z "$e" ] && marca E5.32 PASS || marca E5.32 FAIL "$e"
  return 0
}

# --- E5.33 — orden de gates en pila: >1 MiB+key-basura → 413 (no 401) ----------
case_e5_33() {
  echo "[E5.33] orden de gates: >1 MiB + plk_basura → 413; ≤1 MiB → 401" >&2
  if ! gen_pl_padding 1048590 "$F_PAYLOAD"; then
    marca E5.33 FAIL "payload-gen"; return 0
  fi
  local e=""
  # (a) >1 MiB con key INVÁLIDA → 413 (el gate 1 antecede literalmente).
  # DIRECTO: nginx (default 1 MiB) interceptaría con 413 HTML antes del
  # backend — el orden de gates de C5 se comprueba contra el backend real.
  if [ "$DIRECTO_HABILITADO" != "1" ]; then
    marca E5.33 BLOQUEADO "ruta directa al backend no disponible (nginx 1MiB)"
    return 0
  fi
  printf 'plk_junk_nonexistent' > "$TMPDIR_QA/junk_key.txt"
  chmod 600 "$TMPDIR_QA/junk_key.txt" 2>/dev/null || true
  req_directo "$TMPDIR_QA/junk_key.txt" "$F_PAYLOAD" "/api/v1/ingest" || true
  [ "$CODIGO" = "413" ] || e="$e grande=$CODIGO(no-401)"
  grep -q 'payload_too_large' <<<"$CUERPO" || e="$e grande-sin-413"
  # (b) ≤1 MiB con key inválida → 401 (flujo controlado)
  req POST "$URL_INGEST" "{\"level\":\"INFO\",\"timestamp\":\"$T_STAMP\",\"message\":\"E5.33\",\"environment\":\"production\"}" "X-API-Key: plk_basura_no_existente" || true
  [ "$CODIGO" = "401" ] || e="$e chico=$CODIGO"
  grep -q 'api_key_invalid' <<<"$CUERPO" || e="$e chico-sin-401"
  [ -z "$e" ] && marca E5.33 PASS || marca E5.33 FAIL "$e"
  return 0
}

ejecutar() { # $1 ID $2 función
  if ! "$2" 2>>"$TMPDIR_QA/errores_internos.log"; then
    RESULTADOS+=("$1: FAIL (error interno del script; ver errores_internos)")
  fi
}

if [ "$ejecutar_pre" = "0" ]; then
  precondiciones || {
    for id in E5.21 E5.22 E5.23 E5.24 E5.25 E5.26 E5.27 E5.28 E5.29 E5.30 \
              E5.31 E5.32 E5.33; do
      marca "$id" BLOQUEADO "precondiciones fallidas (ver stderr)"
    done
  }
fi

# ORDEN DE EJECUCIÓN (cronológico del guion; los veredictos se imprimen
# ordenados por ID): el G-2 (E5.31) se ejecuta INMEDIATAMENTE después de
# E5.25 — el proyecto desarchivado queda ACTIVE con la MISMA key2 (jamás
# re-generada) para el resto de escenarios (E5.26–E5.30, E5.32).
if [ "$ejecutar_pre" = "0" ]; then
  # pre-fase (smoke-of-the-smoke): sondeo/me + login + proyecto + key1
  if ! pre_fase 2>>"$TMPDIR_QA/errores_internos.log"; then
    for id in E5.21 E5.22 E5.23 E5.24 E5.25 E5.26 E5.27 E5.28 E5.29 E5.30 \
              E5.31 E5.32 E5.33; do
      marca "$id" BLOQUEADO "pre-fase fallida (ver stderr)"
    done
  else
    ejecutar E5.21 case_e5_21
    ejecutar E5.22 case_e5_22
    ejecutar E5.23 case_e5_23
    ejecutar E5.24 case_e5_24
    ejecutar E5.25 case_e5_25
    ejecutar E5.31 case_e5_31   # G-2: aquí el proyecto vuelve a ACTIVE
    ejecutar E5.26 case_e5_26
    ejecutar E5.27 case_e5_27
    ejecutar E5.28 case_e5_28
    ejecutar E5.29 case_e5_29
    ejecutar E5.30 case_e5_30
    ejecutar E5.32 case_e5_32
    ejecutar E5.33 case_e5_33
  fi
fi

echo "=== VEREDICTOS QA C5 (por ID del spec) ==="
# Los logs de depuración VIVEN SOLO en el tmpdir 700 (el trap los borra);
# hallazgo de qa-loop-back 2026-10-25 (higiene 1a): NADIE copia dumps de
# request fuera del tmpdir — req_debug.log contiene los cuerpos de respuesta
# de /regenerar (el eco de la key jamás sale por ahí, y NO se copia fuera).
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