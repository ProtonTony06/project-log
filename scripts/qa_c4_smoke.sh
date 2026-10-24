#!/usr/bin/env bash
#
# qa_c4_smoke.sh — smokes [S] E4.15–E4.29 de C4 (tasks.md T4.9)
#
# Codifica los escenarios del spec:
#   .atl/sdd/C4/spec.md (E4.15–E4.29; R4.6–R4.13; Done 2/3/4 en psql) y
#   .atl/sdd/C4/design.md (§5: helpers PROPIOS réplica del esqueleto del
#   smoke de C3 — NO invoca NUNCA los scripts de C2/C3 porque su down de
#   referencia usa '-v' y destruiría el volumen con datos del smoke).
#   Patrones heredados de C3 (R4.15/R-7): recomposición ÚNICA con
#   --force-recreate, psql -qAt, booleanos t/f (psql) ≠ true/false (JSON),
#   nombres sufijados por ejecución, smoke-of-the-smoke antes de la canónica.
#
# Higiene runtime (R4.13/nota E4.15): la KEY revelada de cada 201 y el JWT
# van a tmpdir 700 (trap de limpieza) y JAMÁS a consola. En psql la key solo
# existe como dato ENTRANTE por stdin de psql (psql_stdin) para computar
# sha256('<key>'::bytea) — comilla simple escapada '' y sin eco a logs.
#
# Uso:
#   bash scripts/qa_c4_smoke.sh
#
# Salida:
#   Fases de progreso por stderr; al final una línea
#     E4.n: PASS | FAIL | BLOQUEADO (detalle)
#   por ID de escenario.
# Exit: 0 = todos PASS · 1 = hay algún FAIL · 2 = solo BLOQUEADOs
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE_URL="http://127.0.0.1:8080"
URL_LOGIN="$BASE_URL/api/v1/panel/auth/login"
URL_ME="$BASE_URL/api/v1/panel/me"
URL_PROJECTS="$BASE_URL/api/v1/panel/projects"
URL_REGEN="$BASE_URL/api/v1/panel/projects"
URL_REVOC="$BASE_URL/api/v1/panel/projects"

TS="$(date +%Y%m%d%H%M%S)"
A_NAME="smoke-c4-${TS}-a"
DESC_VALIDA="descripción del smoke c4 ($TS)"
ID_FANTASMA="999999"

TMPDIR_QA="$(mktemp -d /tmp/qa_c4_smoke.XXXXXX)"
chmod 700 "$TMPDIR_QA" 2>/dev/null || true
trap 'rm -rf "$TMPDIR_QA"' EXIT
F_CUERPO="$TMPDIR_QA/cuerpo.txt"
F_CAB="$TMPDIR_QA/cabeceras.txt"
F_TOKEN="$TMPDIR_QA/token.txt"    # JWT del login (higiene: no se imprime)
F_ID_A="$TMPDIR_QA/id_a.txt"
F_KEY1="$TMPDIR_QA/key1.txt"      # 1.ª key (E4.18) — higiene: nunca consola
F_KEY2="$TMPDIR_QA/key2.txt"      # 2.ª key (E4.20)

# Compose wrapper: 'podman compose' con timeout acotado para up/down (lección
# de C2/C3: podman-compose 'up' espera a healthy y puede colgarse).
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

# salud por podman (NUNCA curl /actuator desde el host como proxy de salud)
pila_saludable() {
  local snap estado e="" svc
  snap="$(PODMAN ps --format '{{.Names}} {{.Status}}' 2>/dev/null || true)"
  for svc in backend frontend postgres; do
    estado="$(grep -E "^[^ ]*${svc}[ _]" <<<"$snap" | head -n1)"
    grep -qE 'Up.*\(healthy\)' <<<"$estado" || e="$e $svc=$(echo "$estado" | head -c 60)"
  done
  [ -z "$e" ] && return 0 || { echo "$e" >&2; return 1; }
}

# esperar_healthy SEGUNDOS: sondea GET /api/v1/panel/me → 401 invalid_token real
# (el frontend responde 200 index si el backend está caído — sondeo auténtico).
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

# psql_stdin — SQL ENTRENTE POR STDIN (la key no aparece en argv/console/log):
# compone sha256('<key-escapada>'::bytea) dentro del SQL y lo manda por stdin
# a psql -qAt (comilla simple escapada '' — R4.13/nota E4.15).
psql_stdin() {
  printf '%s\n' "$1" | compose_ exec -T postgres psql -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -qAt
}

# esc_key ARCHIVO — key del tmpdir con comillas simples escapadas ('→'').
esc_key() {
  sed "s/'/''/g" "$1"
}

# req VERBO URL CUERPO [CABECERA...] — fija CODIGO/CUERPO/CABECERAS globales.
req() {
  local verbo="$1" url="$2" cuerpo="$3"; shift 3
  local args=(-sS --max-time 30 -o "$F_CUERPO" -D "$F_CAB" -w '%{http_code}' -X "$verbo")
  if [ -n "$cuerpo" ]; then
    args+=(-H 'Content-Type: application/json' --data-binary "$cuerpo")
  fi
  local _h
  for _h in "$@"; do args+=(-H "$_h"); done
  local cod="000"
  if cod="$(curl "${args[@]}" "$url" 2>>"$TMPDIR_QA/curl_err.log")"; then
    CODIGO="$cod"; CUERPO="$(cat "$F_CUERPO")"; CABECERAS="$(cat "$F_CAB")"
    return 0
  fi
  CODIGO="000"; CUERPO=""; CABECERAS=""; return 1
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

# campo_json_nul CLAVE — imprime el valor; "null" literal si el JSON null.
campo_json_nul() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
except Exception:
    sys.exit(3)
if not isinstance(d, dict) or sys.argv[1] not in d:
    sys.exit(2)
v = d[sys.argv[1]]
print("null" if v is None else v)' "$1"
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

# lista_contiene CADENA — 0 si la lista JSON del cuerpo contiene un name.
lista_contiene() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
agujas = [a for a in json.loads(sys.argv[1])]
for p in d:
    if p.get("name") in agujas:
        sys.exit(0)
sys.exit(1)' "$(printf '[%s]' "$(python3 -c 'import sys,json; print(json.dumps(sys.argv[1]))' "$1")")"
}

# autorizado — eco del header Authorization bearer de las respuestas.
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

# ----------------------------------------------------------------------------
# 0. Precondiciones (E4.15): anti-veneno + .env + recomposición ÚNICA
# ----------------------------------------------------------------------------
# Anti-veneno de env: UNSET de las variables de compose en el proceso (el .env
# del despliegue es la ÚNICA fuente; nada heredado del shell contaminando).
unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
      SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
      JWT_SECRET JWT_TTL_HOURS ADMIN_INITIAL_USERNAME ADMIN_INITIAL_PASSWORD \
      INGEST_MAX_REQUEST_BYTES INGEST_MAX_BATCH_SIZE WS_HEARTBEAT_SECONDS \
      FRONTEND_ORIGIN SPRING_PROFILES_ACTIVE LOGGING_LEVEL TZ 2>/dev/null || true

CON_ENV=0; CON_CRED=0; CON_BD=0
if [ -f .env ]; then
  CON_ENV=1
  # Carga ROBUSTA línea a línea SIN exportar (herencia C2/C3).
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
  # --force-recreate OBLIGATORIO (E4.15/R-7 heredado): sin recreate los
  # contenedores seguirían en la imagen vieja (sin los endpoints de C4).
  if ! compose_ up -d --build --force-recreate; then
    echo "[pre] compose up falló (ver $TMPDIR_QA/compose.log)" >&2; return 1
  fi
  if ! esperar_healthy 420; then
    echo "[pre] la pila no llega a healthy (sondeo /me → 401 invalid_token)" >&2; return 1
  fi
  pila_saludable || { echo "[pre] pila no 3×healthy por podman" >&2; return 1; }
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    echo "[pre] login del admin falló (status ${CODIGO:-000})" >&2; return 1
  fi
  printf '%s' "$(campo_json token)" > "$F_TOKEN"
  echo "[pre] healthy + login OK (higiene: JWT solo en $TMPDIR_QA)" >&2
  return 0
}

if [ "$CON_ENV/$CON_CRED/$CON_BD" != "1/1/1" ]; then
  for id in E4.15 E4.16 E4.17 E4.18 E4.19 E4.20 E4.21 E4.22 E4.23 E4.24 E4.25 \
            E4.26 E4.27 E4.28 E4.29; do
    [ "$CON_ENV/$CON_CRED" = "1/1" ] || marca "$id" BLOQUEADO "$bloqueo_env"
  done
  ejecutar_pre=1
else
  ejecutar_pre=0
fi

# --- E4.15 — precondición: recomposición única + 3×healthy + sondeo /me 401 ---
case_e4_15() {
  echo "[E4.15] precondición: pila recomponeda y healthy" >&2
  local e=""
  pila_saludable || e="$e pila-no-3x-healthy"
  # sondeo auténtico SIN JWT sobre /me → 401 invalid_token (el backend real)
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/me_body.txt" -w '%{http_code}' "$URL_ME" 2>/dev/null || echo 000)"
  [ "$hc" = "401" ] || e="$e me=$hc(esperado-401)"
  grep -q 'invalid_token' "$TMPDIR_QA/me_body.txt" 2>/dev/null || e="$e me-code"
  [ -z "$e" ] && marca E4.15 PASS || marca E4.15 FAIL "$e"
  return 0
}

# --- E4.16 — login → 200; JWT al tmpdir (jamás consola) ------------------------
case_e4_16() {
  echo "[E4.16] login → 200 {token, username, expires_at}" >&2
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD"; then
    marca E4.16 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO"
  [ "$(claves_json 2>/dev/null || echo err)" = "expires_at,token,username" ] || e="$e claves"
  printf '%s' "$(campo_json token 2>/dev/null || echo '')" > "$F_TOKEN"
  [ -s "$F_TOKEN" ] || e="$e token-vacio"
  [ -z "$e" ] && marca E4.16 PASS || marca E4.16 FAIL "$e"
  return 0
}

# --- E4.17 — POST /projects → 201; captura de id INCONDICIONAL ------------------
case_e4_17() {
  echo "[E4.17] POST /projects 201 con smoke-c4-<TS>-a" >&2
  if ! req POST "$URL_PROJECTS" "{\"name\":\"$A_NAME\",\"description\":\"$DESC_VALIDA\"}" "$(bearer_hdr)"; then
    marca E4.17 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "201" ] || e="$e status=$CODIGO(esperado-201)"
  [ "$(campo_json has_key 2>/dev/null || echo err)" = "false" ] || e="$e has_key"
  # captura del id SIEMPRE (aun con e no vacío: no cascada de BLOQUEADOs)
  if ! req GET "$URL_PROJECTS" "" "$(bearer_hdr)"; then e="$e lista-id"; fi
  capturar_id "$A_NAME" "$F_ID_A" || e="$e id-a-no-hallado-en-lista"
  [ -z "$e" ] && marca E4.17 PASS || marca E4.17 FAIL "$e"
  return 0
}

# POST regenerate → 201 y salto de la key a F_KEY (higiene: sin consola).
regen_y_guardar() { # $1 archivo-destino
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then return 1; fi
  req POST "$URL_REGEN/$id_a/api-key/regenerate" "" "$(bearer_hdr)" || return 1
  [ "$CODIGO" = "201" ] || return 1
  printf '%s' "$(campo_json key 2>/dev/null || echo '')" > "$1"
  [ -s "$1" ] || return 1
  return 0
}

# --- E4.18 — 1.º regenerate → 201; key/prefix/created_at del contrato ----------
case_e4_18() {
  echo "[E4.18] 1.º POST regenerate → 201 (key plk_ de 47)" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.18 BLOQUEADO "sin id de -a (E4.17)"; return 0; fi
  if ! regen_y_guardar "$F_KEY1"; then
    marca E4.18 FAIL "regenerate=${CODIGO:-000}"; return 0
  fi
  local e=""
  grep -Eq '^plk_[A-Za-z0-9_-]{43}$' "$F_KEY1" || e="$e key-sin-literal-47"
  [ "$( campo_json prefix 2>/dev/null || echo err)" = "$(head -c 10 "$F_KEY1")" ] \
    || e="$e prefix!=key-sub0-10"
  # created_at de la 201 casa la regex ISO-Z del cierre S4 (valor psql en E4.19)
  printf '%s' "$( campo_json created_at 2>/dev/null || echo err)" | grep -Eq \
    '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' || e="$e created_at-no-ISO-Z"
  [ "$( campo_json key 2>/dev/null || echo err)" = "$(cat "$F_KEY1")" ] || e="$e key-consistente"
  [ -z "$e" ] && marca E4.18 PASS || marca E4.18 FAIL "$e"
  return 0
}

# --- E4.19 — psql: hash == sha256(key), prefix de 10, SIN key en claro ----------
case_e4_19() {
  echo "[E4.19] psql: hash/prefix de la 1.ª key; sin key en claro" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.19 BLOQUEADO "sin id de -a"; return 0; fi
  local e="" r key_sql
  key_sql="$(esc_key "$F_KEY1")"
  # (a) hash del String COMPLETO pinche en pila — sha256(bytea) de PG17:
  #     SOLO LECTURA (el psql JAMÁS escribe: E4.19 es verificación del Done 4).
  #     SIN ::text: el boolean crudo con -qAt sale 't/f' (el cast daría true/false)
  r="$(psql_stdin "$(printf \
    "SELECT (api_key_hash = sha256('%s'::bytea)) FROM projects WHERE id = %s;" \
    "$key_sql" "$id_a")" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e hash!=sha256(key-datos($r))"
  # (b) prefix == los primeros 10 chars de la key (un ÚNICO boolean, sin cast):
  r="$(psql_stdin "$(printf \
    "SELECT (api_key_prefix = substr('%s',1,10) AND length(api_key_prefix) = 10) FROM projects WHERE id = %s;" \
    "$key_sql" "$id_a")" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e prefix!=10($r)"
  # (c) NINGÚN campo de la fila contiene la key EN CLARO (Done 4) —
  #     un ÚNICO boolean con OR (boolean OR text = error; por eso todo junto):
  r="$(psql_stdin "$(printf \
    "SELECT (COALESCE(name LIKE '%%%s%%', false) OR COALESCE(description LIKE '%%%s%%', false) OR api_key_prefix = '%s') FROM projects WHERE id = %s;" \
    "$key_sql" "$key_sql" "$key_sql" "$id_a")" 2>/dev/null || echo err)"
  [ "$r" = "f" ] || e="$e key-en-claro-en-fila($r)"
  [ -z "$e" ] && marca E4.19 PASS || marca E4.19 FAIL "$e"
  return 0
}

# --- E4.20 — 2.º regenerate → 201 key DISTINTA; hash nuevo ≠ anterior ----------
case_e4_20() {
  echo "[E4.20] 2.º regenerate → 201 key distinta; hash cambia en psql" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.20 BLOQUEADO "sin id de -a"; return 0; fi
  if ! regen_y_guardar "$F_KEY2"; then
    marca E4.20 FAIL "regenerate2=${CODIGO:-000}"; return 0
  fi
  local e="" r k1_sql k2_sql
  if cmp -s "$F_KEY1" "$F_KEY2"; then e="$e key2==key1(no-regen)"; fi
  grep -Eq '^plk_[A-Za-z0-9_-]{43}$' "$F_KEY2" || e="$e key2-sin-literal-47"
  k1_sql="$(esc_key "$F_KEY1")"
  k2_sql="$(esc_key "$F_KEY2")"
  # Done 3: hash == sha256(key_nueva) → t Y hash <> sha256(key_anterior) → t
  # (boolean crudo con -qAt → t/f; el ::text daría true/false — patrón R4.15)
  r="$(psql_stdin "$(printf \
    "SELECT (api_key_hash = sha256('%s'::bytea)) FROM projects WHERE id = %s;" \
    "$k2_sql" "$id_a")" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e hash!=sha256(key_nueva)($r)"
  r="$(psql_stdin "$(printf \
    "SELECT (api_key_hash <> sha256('%s'::bytea)) FROM projects WHERE id = %s;" \
    "$k1_sql" "$id_a")" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e hash==sha256(key_anterior)($r)"
  [ -z "$e" ] && marca E4.20 PASS || marca E4.20 FAIL "$e"
  return 0
}

# --- E4.21 — DELETE …/api-key → 204 SIN cuerpo -----------------------------------
case_e4_21() {
  echo "[E4.21] DELETE api-key → 204 sin cuerpo" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.21 BLOQUEADO "sin id de -a"; return 0; fi
  if ! req DELETE "$URL_REVOC/$id_a/api-key" "" "$(bearer_hdr)"; then
    marca E4.21 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "204" ] || e="$e status=$CODIGO(esperado-204)"
  [ -z "$CUERPO" ] || e="$e cuerpo-no-vacio"
  [ -z "$e" ] && marca E4.21 PASS || marca E4.21 FAIL "$e"
  return 0
}

# --- E4.22 — psql: los TRES a NULL juntos -----------------------------------------
case_e4_22() {
  echo "[E4.22] psql: api_key_hash/prefix/key_created_at → NULL juntos" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.22 BLOQUEADO "sin id de -a"; return 0; fi
  local e="" r
  r="$(psql_q "SELECT (api_key_hash IS NULL AND api_key_prefix IS NULL AND key_created_at IS NULL) FROM projects WHERE id = $id_a" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e tres-not-NULL($r)"
  [ -z "$e" ] && marca E4.22 PASS || marca E4.22 FAIL "$e"
  return 0
}

# --- E4.23 — GET ficha: has_key false / api_key_prefix null (derivados C3) --------
case_e4_23() {
  echo "[E4.23] GET ficha → has_key:false, api_key_prefix:null" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.23 BLOQUEADO "sin id de -a"; return 0; fi
  if ! req GET "$URL_PROJECTS/$id_a" "" "$(bearer_hdr)"; then
    marca E4.23 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO"
  [ "$(campo_json has_key 2>/dev/null || echo err)" = "false" ] || e="$e has_key"
  [ "$(campo_json_nul api_key_prefix 2>/dev/null || echo err)" = "null" ] || e="$e prefix"
  [ "$(campo_json status 2>/dev/null || echo err)" = "ACTIVE" ] || e="$e status-cambio(archivar-es-C3-y-luego-E4.25)"
  [ -z "$e" ] && marca E4.23 PASS || marca E4.23 FAIL "$e"
  return 0
}

# --- E4.24 — DELETE de nuevo (sin key) → 204; tres SIGUEN a NULL -------------------
case_e4_24() {
  echo "[E4.24] DELETE idempotente (sin key) → 204; tres NULL" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.24 BLOQUEADO "sin id de -a"; return 0; fi
  if ! req DELETE "$URL_REVOC/$id_a/api-key" "" "$(bearer_hdr)"; then
    marca E4.24 FAIL "curl"; return 0
  fi
  local e="" r
  [ "$CODIGO" = "204" ] || e="$e status=$CODIGO(decision-2)"
  r="$(psql_q "SELECT (api_key_hash IS NULL AND api_key_prefix IS NULL AND key_created_at IS NULL) FROM projects WHERE id = $id_a" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e tres-not-NULL($r)"
  # nota S1: updated_at AVANZA en el no-op lógico — NO se aserta (informativo)
  [ -z "$e" ] && marca E4.24 PASS || marca E4.24 FAIL "$e"
  return 0
}

# --- E4.25 — ARCHIVADO → regenerate 409 project_archived; 409 NO escribió ----------
case_e4_25() {
  echo "[E4.25] PATCH ARCHIVED → regenerate 409 project_archived" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.25 BLOQUEADO "sin id de -a"; return 0; fi
  if ! req PATCH "$URL_PROJECTS/$id_a" '{"status": "ARCHIVED"}' "$(bearer_hdr)" \
     || [ "$CODIGO" != "200" ]; then
    marca E4.25 FAIL "patch=${CODIGO:-000}"; return 0
  fi
  # QP-26: epoch de updated_at tras el PATCH archivado (referencia para E4.26;
  # la resolución es de segundos — PostgreSQL now() del UPDATE del archivado)
  psql_q "SELECT FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects \
WHERE id = $id_a" 2>/dev/null >"$TMPDIR_QA/upd_e425.txt" || true
  local e="" r
  if ! req POST "$URL_REGEN/$id_a/api-key/regenerate" "" "$(bearer_hdr)"; then
    marca E4.25 FAIL "regenerate-curl"; return 0
  fi
  [ "$CODIGO" = "409" ] || e="$e status=$CODIGO(esperado-409-cierre-S2)"
  [ "$(campo_json code 2>/dev/null || echo err)" = "project_archived" ] || e="$e code"
  [ "$(campo_json message 2>/dev/null || echo err)" = "El proyecto está archivado: desarchívalo antes de generar o regenerar su API key" ] \
    || e="$e message(literal-S3)"
  r="$(psql_q "SELECT (api_key_hash IS NULL AND api_key_prefix IS NULL AND key_created_at IS NULL) FROM projects WHERE id = $id_a" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e 409-escribio-algo($r)"
  # el proyecto QUEDA archivado para E4.26
  req GET "$URL_PROJECTS/$id_a" "" "$(bearer_hdr)" || true
  [ "$(campo_json status 2>/dev/null || echo err)" = "ARCHIVED" ] || e="$e archivo-no-hallado"
  [ -z "$e" ] && marca E4.25 PASS || marca E4.25 FAIL "$e"
  return 0
}

# --- E4.26 — ARCHIVADO + DELETE api-key → 204 (Decisión 3 revisada) ----------------
case_e4_26() {
  echo "[E4.26] DELETE sobre ARCHIVADO → 204 (permitido)" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E4.26 BLOQUEADO "sin id de -a"; return 0; fi
  sleep 2  # updated_at = now() del UPDATE: resolución de segundos (patrón C3)
  if ! req DELETE "$URL_REVOC/$id_a/api-key" "" "$(bearer_hdr)"; then
    marca E4.26 FAIL "curl"; return 0
  fi
  local e="" r upd_ref upd
  [ "$CODIGO" = "204" ] || e="$e status=$CODIGO"
  r="$(psql_q "SELECT (api_key_hash IS NULL AND api_key_prefix IS NULL AND key_created_at IS NULL) FROM projects WHERE id = $id_a" 2>/dev/null || echo err)"
  [ "$r" = "t" ] || e="$e tres-not-NULL($r)"
  # QP-26 [M]: updated_at AVANZÓ (cierre S1: la fila SÍ se reescribe incluso
  # sin key previa) — epoch > el capturado tras el PATCH archivado de E4.25
  upd="$(psql_q "SELECT FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE id = $id_a" 2>/dev/null || echo err)"
  upd_ref="$(cat "$TMPDIR_QA/upd_e425.txt" 2>/dev/null || echo "")"
  if [ -z "$upd_ref" ] || [ "$upd" = "err" ]; then e="$e updated_at-epoch-indisponible";
  elif [ "$upd" -gt "$upd_ref" ] 2>/dev/null; then :;
  else e="$e updated_at-no-avanzo($upd vs $upd_ref)"; fi
  [ -z "$e" ] && marca E4.26 PASS || marca E4.26 FAIL "$e"
  return 0
}

# --- E4.27 — regenerate sobre id inexistente → 404 estándar -------------------------
case_e4_27() {
  echo "[E4.27] regenerate 999999 → 404 not_found" >&2
  if ! req POST "$URL_REGEN/$ID_FANTASMA/api-key/regenerate" "" "$(bearer_hdr)"; then
    marca E4.27 FAIL "curl"; return 0
  fi
  local e="" msg
  [ "$CODIGO" = "404" ] || e="$e status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "not_found" ] || e="$e code"
  msg="$(campo_json message 2>/dev/null || echo err)"
  [ "$msg" = "No existe (quizás fue eliminado)" ] || e="$e cuerpo-estandar"
  [ -z "$e" ] && marca E4.27 PASS || marca E4.27 FAIL "$e"
  return 0
}

# --- E4.28 — 401 sin JWT en AMBAS rutas (parse tolerante ISO-8859-1) -----------------
case_e4_28() {
  echo "[E4.28] 2 rutas sin Authorization → 401 invalid_token" >&2
  local id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo 7)"
  comprobar() {
    local verbo="$1" url="$2"
    req "$verbo" "$url" "" || { echo "curl-fallo-$url" >&2; return 1; }
    [ "$CODIGO" = "401" ] || return 1
    # ISO-8859-1 del entry point: SIN parse JSON estricto — grep del literal
    printf '%s' "$CUERPO" | grep -q 'invalid_token' || return 1
    return 0
  }
  local e=""
  comprobar POST "$URL_REGEN/$id_a/api-key/regenerate" || e="$e POST=$CODIGO"
  comprobar DELETE "$URL_REVOC/$id_a/api-key" || e="$e DELETE=$CODIGO"
  [ -z "$e" ] && marca E4.28 PASS || marca E4.28 FAIL "$e"
  return 0
}

# --- E4.29 — no-regresión C3 ligera: lista 200 + membresía del proyecto ---------------
case_e4_29() {
  echo "[E4.29] GET /projects 200 + membresía del proyecto del smoke" >&2
  if ! req GET "$URL_PROJECTS" "" "$(bearer_hdr)"; then
    marca E4.29 FAIL "curl"; return 0
  fi
  local e=""
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO"
  lista_contiene "$A_NAME" || e="$e sin-membresia-de--a(DELETE-es-C10)"
  [ -z "$e" ] && marca E4.29 PASS || marca E4.29 FAIL "$e"
  return 0
}

ejecutar() { # $1 ID $2 función
  if ! "$2" 2>>"$TMPDIR_QA/errores_internos.log"; then
    RESULTADOS+=("$1: FAIL (error interno del script; ver errores_internos)")
  fi
}

if [ "$ejecutar_pre" = "0" ]; then
  precondiciones || {
    for id in E4.15 E4.16 E4.17 E4.18 E4.19 E4.20 E4.21 E4.22 E4.23 E4.24 E4.25 \
              E4.26 E4.27 E4.28 E4.29; do
      marca "$id" BLOQUEADO "precondiciones fallidas (ver stderr)"
    done
  }
fi

if grep -q 'BLOQUEADO' <(printf '%s\n' "${RESULTADOS[@]:-}") 2>/dev/null; then
  echo "=== VEREDICTOS QA C4 (precondiciones) ==="
  printf '%s\n' "${RESULTADOS[@]}"
  exit 2
fi

# Orden de guion: precondición → login → crear → key1 → psql → key2 → psql
# → revoke×2 → archivado 409 → revoke archivado → 404-fantasma → 401 → lista.
if [ "$ejecutar_pre" = "0" ]; then
  ejecutar E4.15 case_e4_15
  ejecutar E4.16 case_e4_16
  ejecutar E4.17 case_e4_17
  ejecutar E4.18 case_e4_18
  ejecutar E4.19 case_e4_19
  ejecutar E4.20 case_e4_20
  ejecutar E4.21 case_e4_21
  ejecutar E4.22 case_e4_22
  ejecutar E4.23 case_e4_23
  ejecutar E4.24 case_e4_24
  ejecutar E4.25 case_e4_25
  ejecutar E4.26 case_e4_26
  ejecutar E4.27 case_e4_27
  ejecutar E4.28 case_e4_28
  ejecutar E4.29 case_e4_29
fi

echo "=== VEREDICTOS QA C4 (por ID del spec) ==="
if [ "${#RESULTADOS[@]}" -gt 0 ]; then
  printf '%s\n' "${RESULTADOS[@]}"
fi

state_exit=0
if printf '%s\n' "${RESULTADOS[@]:-}" | grep -q ': FAIL'; then
  state_exit=1
elif printf '%s\n' "${RESULTADOS[@]:-}" | grep -q ': BLOQUEADO'; then
  state_exit=2
fi
exit "$state_exit"