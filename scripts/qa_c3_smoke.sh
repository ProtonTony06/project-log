#!/usr/bin/env bash
#
# qa_c3_smoke.sh — smokes [S] E3.1–E3.24 de C3 (tasks.md 6.1/6.2/6.3)
#
# Codifica los escenarios del spec:
#   .atl/sdd/C3/spec.md (E3.1–E3.26; E3.23/E3.24 = no-regresiones C2) y
#   .atl/sdd/C3/design.md (CD-3.14: helpers PROPIOS réplica del esqueleto del
#   smoke anterior — NO invoca NUNCA el script de C2 porque su Q13 hace
#   'down' con '-v' y destruiría el volumen con datos del smoke).
#   Literales citados: docs/05_BACKEND_SCHEMA.md §3.1–§4; docs/03_APPFLOW.md.
#
# Uso:
#   bash scripts/qa_c3_smoke.sh
#
# Salida:
#   Fases de progreso por stderr; al final una línea
#     E3.n: PASS | FAIL | BLOQUEADO (detalle)
#   por ID de escenario.
# Exit: 0 = todos PASS · 1 = hay algún FAIL · 2 = solo BLOQUEADOs
#
# Avisos operativos (herencia de la disciplina de C2):
#  * Anti-veneno de env: el entorno del proceso se LIMPIA de las variables de
#    compose y el .env se carga línea a línea SIN exportar (si se exportaran,
#    el proceso padre ganaría la interpolación ${VAR} frente al .env y las
#    mutaciones/recargas no llegarían a los contenedores).
#  * Higiene S16: NO imprime secretos ni el JWT (los tokens van al tmpdir 700).
#  * 401 ISO-8859-1: el body del entry point NO se parsea como JSON estricto;
#    se verifican status y literal con decodificación tolerante / grep ASCII.
#  * Recomposición única inicial con 'up -d --build' (RECREA contenedores con
#    la imagen reconstruida; NUNCA 'restart' de un contenedor sin rebuild).
#  * Idempotencia: TS por ejecución (smoke-c3-<TS>-*) — las verificaciones de
#    lista son de MEMBRESÍA (tolera sobras de corridas previas; DELETE es C10).
#  * Resolución de segundos del formatter: las comparaciones de updated_at
#    llevan sleep 2 antes del PATCH comparativo (dos PATCH del mismo segundo
#    empatarían — Riesgo del design).
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE_URL="http://127.0.0.1:8080"
URL_LOGIN="$BASE_URL/api/v1/panel/auth/login"
URL_ME="$BASE_URL/api/v1/panel/me"
URL_PROJECTS="$BASE_URL/api/v1/panel/projects"

TS="$(date +%Y%m%d%H%M%S)"
A_NAME="smoke-c3-${TS}-a"
B_NAME="smoke-c3-${TS}-b"
B_NAME_REN="smoke-c3-${TS}-b-ren"
C_NAME="smoke-c3-${TS}-c"
C_NAME_E20="smoke-c3-${TS}-c-e20"
NAME_201="x201-$(printf 'n%.0s' $(seq 1 201))"        # 201 chars exactos
DESC_2001="$(printf 'd%.0s' $(seq 1 2001))"           # 2001 chars exactos
DESC_VALIDA="descripción válida del smoke c3 ($TS)"

TMPDIR_QA="$(mktemp -d /tmp/qa_c3_smoke.XXXXXX)"
chmod 700 "$TMPDIR_QA" 2>/dev/null || true
trap 'rm -rf "$TMPDIR_QA"' EXIT
F_CUERPO="$TMPDIR_QA/cuerpo.txt"
F_CAB="$TMPDIR_QA/cabeceras.txt"
F_TOKEN="$TMPDIR_QA/token.txt"      # JWT del login (higiene: no se imprime)
F_ID_A="$TMPDIR_QA/id_a.txt"
F_ID_B="$TMPDIR_QA/id_b.txt"
F_ID_C="$TMPDIR_QA/id_c.txt"

# Compose wrapper: 'podman compose' con timeout acotado para up/down (lección
# de C2: podman-compose 'up' espera a healthy y puede colgarse ante crash-loop).
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
# (el proxy responde 200 index si el backend está caído — sondeo auténtico).
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

# psql_q SQL — salida -qAt limpia; exige POSTGRES_USER/POSTGRES_DB del .env.
psql_q() {
  compose_ exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 -qAt -c "$1"
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
print(v)' "$1"
}

# campo_json_nul CLAVE — imprime el valor; "null" literal si el JSON null
# (distingue description: null de "": E3.2/E3.13).
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

# es_array_json — 0 si el cuerpo actual es un array JSON (E3.7: sin {items}).
es_array_json() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
try:
    d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
    sys.exit(0 if isinstance(d, list) else 1)
except Exception:
    sys.exit(2)'
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

# iso_a_epoch "…Z" / epoch_diff — comparaciones de updated_at (resolución de segundos).
iso_a_epoch() { python3 -c '
import datetime, sys
d = datetime.datetime.strptime(sys.argv[1], "%Y-%m-%dT%H:%M:%SZ")
print(int(d.replace(tzinfo=datetime.timezone.utc).timestamp()))' "$1"; }

# cabecera NOMBRE — valor de la cabecera de la última respuesta.
cabecera() {
  printf '%s' "$CABECERAS" | tr -d '\r' | grep -i "^${1}:" | tail -n1 | sed 's/^[^:]*:[[:space:]]*//'
}

# autorizado — eco del header Authorization de las respuestas autenticadas.
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
# 0. Precondiciones (CD-3.14 fase 0): anti-veneno + .env + recomposición única
# ----------------------------------------------------------------------------
# Anti-veneno de env: UNSET de las variables de compose en el proceso (el .env
# del despliegue es la ÚNICA fuente; nada heredado del shell contaminando).
unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
      SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
      JWT_SECRET JWT_TTL_HOURS ADMIN_INITIAL_USERNAME ADMIN_INITIAL_PASSWORD \
      INGEST_MAX_REQUEST_BYTES INGEST_MAX_BATCH_SIZE WS_HEARTBEAT_SECONDS \
      FRONTEND_ORIGIN SPRING_PROFILES_ACTIVE LOGGING_LEVEL TZ 2>/dev/null || true

CON_ENV=0; CON_CRED=0; CON_BD=0; CON_ORIGIN=0
if [ -f .env ]; then
  CON_ENV=1
  # Carga ROBUSTA línea a línea SIN exportar (lección WARNING 2 de C2: un
  # 'source' directo con set -e aborta todo; 'set -a' contaminaría children).
  while IFS= read -r _qa_linea || [ -n "$_qa_linea" ]; do
    case "$_qa_linea" in ''|\#*) continue ;; esac
    eval "$_qa_linea" 2>/dev/null || true
  done < ./.env
  [ -n "${JWT_TTL_HOURS:-}" ] || JWT_TTL_HOURS="8"
  if [ -n "${ADMIN_INITIAL_USERNAME:-}" ] && [ -n "${ADMIN_INITIAL_PASSWORD:-}" ]; then CON_CRED=1; fi
  if [ -n "${FRONTEND_ORIGIN:-}" ]; then CON_ORIGIN=1; fi
  if [ -n "${POSTGRES_USER:-}" ] && [ -n "${POSTGRES_DB:-}" ]; then CON_BD=1; fi
fi
bloqueo_env="(sin .env F1 del despliegue: credenciales/BD de la pila no disponibles)"

precondiciones() {
  echo "[pre] recomposición única: podman compose up -d --build (recrea contenedores)" >&2
  if ! compose_ up -d --build; then
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
  for id in E3.1 E3.2 E3.3 E3.4 E3.5 E3.6 E3.7 E3.8 E3.9 E3.10 E3.11 E3.12 \
            E3.13 E3.14 E3.15 E3.16 E3.17 E3.18 E3.19 E3.20 E3.21 E3.22 E3.23; do
    [ "$CON_ENV/$CON_CRED" = "1/1" ] || marca "$id" BLOQUEADO "$bloqueo_env"
  done
  ejecutar_pre=1
else
  ejecutar_pre=0
fi

# --- E3.1 — POST 201 con name + description -----------------------------------
case_e3_1() {
  echo "[E3.1] POST 201 con name+description" >&2
  if ! req POST "$URL_PROJECTS" "{\"name\":\"$A_NAME\",\"description\":\"$DESC_VALIDA\"}" "$(bearer_hdr)"; then
    marca E3.1 FAIL "curl sin respuesta"; return 0
  fi
  local e="" claves status_ has_key prefix created_ updated_
  [ "$CODIGO" = "201" ] || e="$e status=$CODIGO(esperado-201)"
  claves="$(claves_json 2>/dev/null || echo err)"
  [ "$claves" = "api_key_prefix,created_at,description,has_key,id,name,status,updated_at" ] \
    || e="$e claves=$claves(sin-summary-exacto-33)"
  status_="$(campo_json status 2>/dev/null || echo err)"
  [ "$status_" = "ACTIVE" ] || e="$e status=$status_(default-V1)"
  has_key="$(campo_json has_key 2>/dev/null || echo err)"
  [ "$has_key" = "false" ] || e="$e has_key=$has_key"
  prefix="$(campo_json_nul api_key_prefix 2>/dev/null || echo err)"
  [ "$prefix" = "null" ] || e="$e api_key_prefix=$prefix"
  created_="$(campo_json created_at 2>/dev/null || echo err)"
  updated_="$(campo_json updated_at 2>/dev/null || echo err)"
  printf '%s %s' "$created_" "$updated_" | grep -Eq \
    '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z [0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' \
    || e="$e fechas-sin-formato-ISO-Z"
  local e1="$e"
  if [ -z "$e" ]; then
    if ! req GET "$URL_PROJECTS" "" "$(bearer_hdr)"; then e="$e lista-id"; fi
    capturar_id "$A_NAME" "$F_ID_A" || e="$e id-a-no-hallado-en-lista"
  fi
  printf '%s' "$(campo_json updated_at 2>/dev/null || echo '')" > "$TMPDIR_QA/upd_a_creo.txt"
  [ -z "$e" ] && marca E3.1 PASS || marca E3.1 FAIL "${e1:-$e}"
  return 0
}

# --- E3.2 — POST 201 sin description ------------------------------------------
case_e3_2() {
  echo "[E3.2] POST 201 sin description" >&2
  if ! req POST "$URL_PROJECTS" "{\"name\":\"$B_NAME\"}" "$(bearer_hdr)"; then
    marca E3.2 FAIL "curl sin respuesta"; return 0
  fi
  local e=""
  [ "$CODIGO" = "201" ] || e="$e status=$CODIGO"
  [ "$(campo_json_nul description 2>/dev/null || echo err)" = "null" ] || e="$e description-no-null"
  [ "$(campo_json has_key 2>/dev/null || echo err)" = "false" ] || e="$e has_key"
  [ "$(campo_json_nul api_key_prefix 2>/dev/null || echo err)" = "null" ] || e="$e api_key_prefix"
  if [ -z "$e" ]; then
    req GET "$URL_PROJECTS" "" "$(bearer_hdr)" || true
    capturar_id "$B_NAME" "$F_ID_B" || e="$e id-b-no-hallado"
  fi
  [ -z "$e" ] && marca E3.2 PASS || marca E3.2 FAIL "$e"
  return 0
}

# --- E3.3 — POST 409 name_duplicate (ida-vuelta inmediata) ---------------------
case_e3_3() {
  echo "[E3.3] POST duplicado inmediato → 409 name_duplicate" >&2
  if ! req POST "$URL_PROJECTS" "{\"name\":\"$A_NAME\",\"description\":\"cualquiera\"}" "$(bearer_hdr)"; then
    marca E3.3 FAIL "curl sin respuesta"; return 0
  fi
  local e=""
  [ "$CODIGO" = "409" ] || e="$e status=$CODIGO(esperado-409)"
  [ "$(campo_json code 2>/dev/null || echo err)" = "name_duplicate" ] || e="$e code"
  [ "$(campo_json message 2>/dev/null || echo err)" = "Ya existe un proyecto con ese nombre" ] \
    || e="$e message(AppFlow-flujo-2)"
  [ "$(campo_json field 2>/dev/null || echo err)" = "name" ] || e="$e field"
  [ -z "$e" ] && marca E3.3 PASS || marca E3.3 FAIL "$e"
  return 0
}

# --- E3.4 — POST 400 name ausente o vacío --------------------------------------
case_e3_4() {
  echo "[E3.4] POST {} y {\"name\":\"\"} → 400 field_required" >&2
  local e=""
  req POST "$URL_PROJECTS" '{}' "$(bearer_hdr)" || { marca E3.4 FAIL "curl a"; return 0; }
  [ "$CODIGO" = "400" ] || e="$e a-status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "field_required" ] || e="$e a-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "name" ] || e="$e a-field"
  req POST "$URL_PROJECTS" '{"name":""}' "$(bearer_hdr)" || { marca E3.4 FAIL "curl b"; return 0; }
  [ "$CODIGO" = "400" ] || e="$e b-status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "field_required" ] || e="$e b-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "name" ] || e="$e b-field"
  [ -z "$e" ] && marca E3.4 PASS || marca E3.4 FAIL "$e"
  return 0
}

# --- E3.5 — POST 400 name >200 --------------------------------------------------
case_e3_5() {
  echo "[E3.5] POST name 201 chars → 400 field_too_long" >&2
  req POST "$URL_PROJECTS" "{\"name\":\"$NAME_201\"}" "$(bearer_hdr)" \
    || { marca E3.5 FAIL "curl"; return 0; }
  local e=""
  [ "$CODIGO" = "400" ] || e="$e status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "field_too_long" ] || e="$e code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "name" ] || e="$e field"
  [ -z "$e" ] && marca E3.5 PASS || marca E3.5 FAIL "$e"
  return 0
}

# --- E3.6 — POST 400 description >2000 ------------------------------------------
case_e3_6() {
  echo "[E3.6] POST description 2001 chars → 400 field_too_long" >&2
  local cuerpo
  cuerpo="$(python3 -c 'import json,sys; print(json.dumps({"name":"smoke-c3-'$TS'-largo","description":"d"*2001}))')"
  req POST "$URL_PROJECTS" "$cuerpo" "$(bearer_hdr)" || { marca E3.6 FAIL "curl"; return 0; }
  local e=""
  [ "$CODIGO" = "400" ] || e="$e status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "field_too_long" ] || e="$e code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "description" ] || e="$e field"
  [ -z "$e" ] && marca E3.6 PASS || marca E3.6 FAIL "$e"
  return 0
}

# --- E3.12 — PATCH solo name: resto intacto, updated_at refrescado -------------
case_e3_12() {
  echo "[E3.12] PATCH {\"name\"} sobre -b" >&2
  local id_b u1 upd antes_created respuesta e=""
  id_b="$(cat "$F_ID_B" 2>/dev/null || echo err)"
  if [ "$id_b" = "err" ]; then marca E3.12 BLOQUEADO "sin id de -b (depende de E3.2)"; return 0; fi
  req GET "$URL_PROJECTS/$id_b" "" "$(bearer_hdr)" || { marca E3.12 FAIL "curl GET previo"; return 0; }
  upd="$(campo_json updated_at 2>/dev/null || echo err)"
  antes_created="$(campo_json created_at 2>/dev/null || echo err)"
  [ "$upd" = "err" ] && { marca E3.12 FAIL "GET previo sin updated_at"; return 0; }
  sleep 2  # resolución de segundos del formatter (Riesgo del design)
  if ! req PATCH "$URL_PROJECTS/$id_b" "{\"name\":\"$B_NAME_REN\"}" "$(bearer_hdr)"; then
    marca E3.12 FAIL "curl PATCH"; return 0
  fi
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO"
  [ "$(campo_json name 2>/dev/null || echo err)" = "$B_NAME_REN" ] || e="$e name-cambiado"
  [ "$(campo_json_nul description 2>/dev/null || echo err)" = "null" ] || e="$e description-cambio(solo-name)"
  [ "$(campo_json status 2>/dev/null || echo err)" = "ACTIVE" ] || e="$e status-cambio(solo-name)"
  [ "$(campo_json created_at 2>/dev/null || echo err)" = "$antes_created" ] || e="$e created_at-cambio"
  upd="$(campo_json updated_at 2>/dev/null || echo err)"
  if [ "$upd" = "err" ]; then e="$e updated_at-ausente"
  elif [ "$(iso_a_epoch "$upd")" -gt "$(iso_a_epoch "$(cat "$TMPDIR_QA/upd_b1.txt" 2>/dev/null || echo 1970-01-01T00:00:00Z)")" ] 2>/dev/null; then :;
  else e="$e updated_at-no-refrescado($upd vs $(cat "$TMPDIR_QA/upd_b1.txt" 2>/dev/null || echo '-'))"; fi
  printf '%s' "$upd" > "$TMPDIR_QA/upd_b1.txt"
  [ -z "$e" ] && marca E3.12 PASS || marca E3.12 FAIL "$e"
  return 0
}
# nota: upd_b1 se fija en el GET previo (ver función real abajo)

# --- E3.13 — PATCH description: texto y null limpia ------------------------------
case_e3_13() {
  echo "[E3.13] PATCH description texto + null (limpia)" >&2
  local id_b upd u3 u4 e=""
  id_b="$(cat "$F_ID_B" 2>/dev/null || echo err)"
  if [ "$id_b" = "err" ]; then marca E3.13 BLOQUEADO "sin id de -b"; return 0; fi
  req GET "$URL_PROJECTS/$id_b" "" "$(bearer_hdr)" || { marca E3.13 FAIL "curl GET"; return 0; }
  u3="$(campo_json updated_at 2>/dev/null || echo err)"
  sleep 2
  req PATCH "$URL_PROJECTS/$id_b" '{"description": "texto nuevo del smoke"}' "$(bearer_hdr)" \
    || { marca E3.13 FAIL "curl PATCH texto"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e texto-status=$CODIGO"
  [ "$(campo_json description 2>/dev/null || echo err)" = "texto nuevo del smoke" ] || e="$e texto-description"
  u4="$(campo_json updated_at 2>/dev/null || echo err)"
  { [ "$u4" != "err" ] && [ "$(iso_a_epoch "$u4")" -gt "$(iso_a_epoch "$u3")" ]; } 2>/dev/null \
    || e="$e texto-updated_at-no-refrescado($u4 vs $u3)"
  sleep 2
  req PATCH "$URL_PROJECTS/$id_b" '{"description": null}' "$(bearer_hdr)" \
    || { marca E3.13 FAIL "curl PATCH null"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e null-status=$CODIGO"
  [ "$(campo_json_nul description 2>/dev/null || echo err)" = "null" ] || e="$e null-description-no-limpia"
  [ "$(campo_json name 2>/dev/null || echo err)" = "$B_NAME_REN" ] || e="$e null-name-cambio"
  [ "$(campo_json status 2>/dev/null || echo err)" = "ACTIVE" ] || e="$e null-status-cambio"
  upd="$(campo_json updated_at 2>/dev/null || echo err)"
  { [ "$upd" != "err" ] && [ "$(iso_a_epoch "$upd")" -gt "$(iso_a_epoch "$u4")" ]; } 2>/dev/null \
    || e="$e null-updated_at-no-refrescado($upd vs $u4)"
  printf '%s' "$upd" > "$TMPDIR_QA/upd_b1.txt"
  [ -z "$e" ] && marca E3.13 PASS || marca E3.13 FAIL "$e"
  return 0
}

# --- E3.15 — PATCH {} vacío → 200 idempotente, updated_at INTACTO ----------------
case_e3_15() {
  echo "[E3.15] PATCH {} → 200 idempotente (updated_at intacto)" >&2
  local id_a u1 u2 e=""
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E3.15 BLOQUEADO "sin id de -a"; return 0; fi
  req GET "$URL_PROJECTS/$id_a" "" "$(bearer_hdr)" || { marca E3.15 FAIL "curl GET"; return 0; }
  u1="$(campo_json updated_at 2>/dev/null || echo err)"
  sleep 2  # fiable en ambas direcciones: la igualdad ESTRICTA tras la pausa
  req PATCH "$URL_PROJECTS/$id_a" '{}' "$(bearer_hdr)" \
    || { marca E3.15 FAIL "curl PATCH"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO"
  u2="$(campo_json updated_at 2>/dev/null || echo err)"
  [ "$u2" = "$u1" ] || e="$e updated_at-cambio($u2 vs $u1 — G2 violado)"
  [ "$(campo_json name 2>/dev/null || echo err)" = "$A_NAME" ] || e="$e name-cambio"
  [ -z "$e" ] && marca E3.15 PASS || marca E3.15 FAIL "$e"
  return 0
}

# --- E3.16 — PATCH name/status null → 400 field_required -------------------------
case_e3_16() {
  echo "[E3.16] PATCH {\"name\":null} / {\"status\":null} → 400" >&2
  local id_b e=""
  id_b="$(cat "$F_ID_B" 2>/dev/null || echo err)"
  if [ "$id_b" = "err" ]; then marca E3.16 BLOQUEADO "sin id de -b"; return 0; fi
  req PATCH "$URL_PROJECTS/$id_b" '{"name": null}' "$(bearer_hdr)" \
    || { marca E3.16 FAIL "curl a"; return 0; }
  [ "$CODIGO" = "400" ] || e="$e a-status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "field_required" ] || e="$e a-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "name" ] || e="$e a-field"
  req PATCH "$URL_PROJECTS/$id_b" '{"status": null}' "$(bearer_hdr)" \
    || { marca E3.16 FAIL "curl b"; return 0; }
  [ "$CODIGO" = "400" ] || e="$e b-status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "field_required" ] || e="$e b-code"
  [ "$(campo_json field 2>/dev/null || echo err)" = "status" ] || e="$e b-field"
  [ -z "$e" ] && marca E3.16 PASS || marca E3.16 FAIL "$e"
  return 0
}

# --- E3.17 — PATCH status inválido ×3 → 400 (nunca 500, nunca toca la fila) ------
case_e3_17() {
  echo "[E3.17] PATCH status fuera de enum ×3 → 400 fail-fast" >&2
  local id_b e="" nombre_antes inv
  id_b="$(cat "$F_ID_B" 2>/dev/null || echo err)"
  if [ "$id_b" = "err" ]; then marca E3.17 BLOQUEADO "sin id de -b"; return 0; fi
  req GET "$URL_PROJECTS/$id_b" "" "$(bearer_hdr)" || return 1
  nombre_antes="$(campo_json name 2>/dev/null || echo err)"
  for inv in ACTIVO active ARCHIVE; do
    req PATCH "$URL_PROJECTS/$id_b" "{\"status\": \"$inv\"}" "$(bearer_hdr)" \
      || { marca E3.17 FAIL "curl $inv"; return 0; }
    [ "$CODIGO" = "400" ] || e="$e $inv-status=$CODIGO(nunca-500)"
    [ "$(campo_json code 2>/dev/null || echo err)" = "filter_invalid_value" ] || e="$e $inv-code"
    [ "$(campo_json field 2>/dev/null || echo err)" = "status" ] || e="$e $inv-field"
  done
  req GET "$URL_PROJECTS/$id_b" "" "$(bearer_hdr)" || return 1
  [ "$(campo_json name 2>/dev/null || echo err)" = "$nombre_antes" ] || e="$e fila-tocada"
  [ "$(campo_json status 2>/dev/null || echo err)" = "ACTIVE" ] || e="$e fila-status-cambiado"
  [ -z "$e" ] && marca E3.17 PASS || marca E3.17 FAIL "$e"
  return 0
}

# --- E3.18 — PATCH choque de name → 409; B intacto --------------------------------
case_e3_18() {
  echo "[E3.18] PATCH -a con name de -b → 409 name_duplicate" >&2
  local id_a id_b e=""
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"; id_b="$(cat "$F_ID_B" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ] || [ "$id_b" = "err" ]; then marca E3.18 BLOQUEADO "sin ids -a/-b"; return 0; fi
  req PATCH "$URL_PROJECTS/$id_a" "{\"name\":\"$B_NAME_REN\"}" "$(bearer_hdr)" \
    || { marca E3.18 FAIL "curl"; return 0; }
  [ "$CODIGO" = "409" ] || e="$e status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "name_duplicate" ] || e="$e code"
  req GET "$URL_PROJECTS/$id_b" "" "$(bearer_hdr)" || { marca E3.18 FAIL "curl GET -b"; return 0; }
  [ "$(campo_json name 2>/dev/null || echo err)" = "$B_NAME_REN" ] || e="$e B-tocado"
  [ "$CODIGO" = "200" ] || e="$e B-ficha=$CODIGO"
  [ -z "$e" ] && marca E3.18 PASS || marca E3.18 FAIL "$e"
  return 0
}

# --- E3.19 — PATCH id inexistente → 404 -------------------------------------------
case_e3_19() {
  echo "[E3.19] PATCH 999999 → 404 not_found" >&2
  req PATCH "$URL_PROJECTS/999999" '{"name": "no-importa"}' "$(bearer_hdr)" \
    || { marca E3.19 FAIL "curl"; return 0; }
  local e=""
  [ "$CODIGO" = "404" ] || e="$e status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "not_found" ] || e="$e code"
  [ -z "$e" ] && marca E3.19 PASS || marca E3.19 FAIL "$e"
  return 0
}

# --- E3.14 — PATCH ARCHIVED → dato persistido e intacto; desarchivar → ACTIVE -----
case_e3_14() {
  echo "[E3.14] archivar/desarchivar -a con PATCH {status}" >&2
  local id_a e="" desc_antes created_antes
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E3.14 BLOQUEADO "sin id de -a"; return 0; fi
  req GET "$URL_PROJECTS/$id_a" "" "$(bearer_hdr)" || { marca E3.14 FAIL "curl GET"; return 0; }
  desc_antes="$(campo_json_nul description 2>/dev/null || echo err)"
  created_antes="$(campo_json created_at 2>/dev/null || echo err)"
  req PATCH "$URL_PROJECTS/$id_a" '{"status": "ARCHIVED"}' "$(bearer_hdr)" \
    || { marca E3.14 FAIL "curl patch"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e patch-status=$CODIGO"
  [ "$(campo_json status 2>/dev/null || echo err)" = "ARCHIVED" ] || e="$e patch-status-valor"
  [ "$(campo_json_nul description 2>/dev/null || echo err)" = "$desc_antes" ] || e="$e desc-cambio(regla-3)"
  [ "$(campo_json created_at 2>/dev/null || echo err)" = "$created_antes" ] || e="$e created_at-cambio"

  # re-GET lista + ficha: dato persistido e INTACTO (nada borrado)
  req GET "$URL_PROJECTS/$id_a" "" "$(bearer_hdr)" || { marca E3.14 FAIL "curl ficha"; return 0; }
  [ "$(campo_json status 2>/dev/null || echo err)" = "ARCHIVED" ] || e="$e ficha-status=$CODIGO"
  [ "$(campo_json name 2>/dev/null || echo err)" = "$A_NAME" ] || e="$e ficha-name"
  req GET "$URL_PROJECTS?status=ARCHIVED" "" "$(bearer_hdr)" || { marca E3.14 FAIL "curl filtro ARCH"; return 0; }
  es_array_json || e="$e lista-ARCHIVED-no-array"
  lista_contiene "$A_NAME" || e="$e lista-ARCHIVED-sin--a"

  req GET "$URL_PROJECTS?status=ACTIVE" "" "$(bearer_hdr)" || { marca E3.14 FAIL "curl filtro ACT"; return 0; }
  lista_contiene "$B_NAME_REN" || e="$e lista-ACTIVE-sin--b"
  lista_contiene "$A_NAME" && e="$e lista-ACTIVE-contiene--a" || true

  # desarchivar → ACTIVE (reversible, §4.2)
  req PATCH "$URL_PROJECTS/$id_a" '{"status": "ACTIVE"}' "$(bearer_hdr)" \
    || { marca E3.14 FAIL "curl desarchivar"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e desarchivar-status=$CODIGO"
  [ "$(campo_json status 2>/dev/null || echo err)" = "ACTIVE" ] || e="$e desarchivar-valor"
  req GET "$URL_PROJECTS?status=ACTIVE" "" "$(bearer_hdr)" || { marca E3.14 FAIL "curl filtro2"; return 0; }
  lista_contiene "$A_NAME" || e="$e filtro-ACTIVE-sin--a-tras-desarchivar"
  req GET "$URL_PROJECTS?status=ARCHIVED" "" "$(bearer_hdr)" || { marca E3.14 FAIL "curl filtro3"; return 0; }
  lista_contiene "$A_NAME" && e="$e filtro-ARCHIVED-contiene--a" || true
  [ -z "$e" ] && marca E3.14 PASS || marca E3.14 FAIL "$e"
  return 0
}

# --- E3.20 — GET no altera updated_at; PATCH con campo sí --------------------------
case_e3_20() {
  echo "[E3.20] GET repite updated_at; PATCH con campo lo refresca" >&2
  local e="" id_c u1 u2 upd
  req POST "$URL_PROJECTS" "{\"name\":\"$C_NAME\",\"description\":\"para e3.20\"}" "$(bearer_hdr)" \
    || { marca E3.20 FAIL "curl crear"; return 0; }
  req GET "$URL_PROJECTS" "" "$(bearer_hdr)" || { marca E3.20 FAIL "curl lista"; return 0; }
  capturar_id "$C_NAME" "$F_ID_C" || { marca E3.20 FAIL "id -c no hallado"; return 0; }
  id_c="$(cat "$F_ID_C")"
  req GET "$URL_PROJECTS/$id_c" "" "$(bearer_hdr)" || { marca E3.20 FAIL "curl ficha1"; return 0; }
  u1="$(campo_json updated_at 2>/dev/null || echo err)"
  req GET "$URL_PROJECTS/$id_c" "" "$(bearer_hdr)" || { marca E3.20 FAIL "curl ficha2"; return 0; }
  u2="$(campo_json updated_at 2>/dev/null || echo err)"
  [ "$u1" = "$u2" ] || e="$e GET-modifico-updated_at($u1 vs $u2 — leer nunca modifica)"
  sleep 2
  req PATCH "$URL_PROJECTS/$id_c" "{\"name\":\"$C_NAME_E20\"}" "$(bearer_hdr)" \
    || { marca E3.20 FAIL "curl patch"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e patch-status=$CODIGO"
  upd="$(campo_json updated_at 2>/dev/null || echo err)"
  { [ "$upd" != "err" ] && [ "$(iso_a_epoch "$upd")" -gt "$(iso_a_epoch "$u1")" ]; } 2>/dev/null \
    || e="$e patch-updated_at-no-posterior($upd vs $u1)"
  req GET "$URL_PROJECTS/$id_c" "" "$(bearer_hdr)" || { marca E3.20 FAIL "curl ficha3"; return 0; }
  [ "$(campo_json name 2>/dev/null || echo err)" = "$C_NAME_E20" ] || e="$e name-renombrado"
  [ "$(campo_json updated_at 2>/dev/null || echo err)" = "$upd" ] || e="$e GET-tras-patch-cambia"
  [ -z "$e" ] && marca E3.20 PASS || marca E3.20 FAIL "$e"
  return 0
}

# --- E3.7 — GET lista 200 array plano ----------------------------------------------
case_e3_7() {
  echo "[E3.7] GET lista → array plano" >&2
  req GET "$URL_PROJECTS" "" "$(bearer_hdr)" || { marca E3.7 FAIL "curl"; return 0; }
  local e=""
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO"
  es_array_json || e="$e no-es-array-plano"
  if printf '%s' "$CUERPO" | grep -q '"items"'; then e="$e contiene-items(keyset?!)"; fi
  lista_contiene "$A_NAME" || e="$e sin--a"
  lista_contiene "$B_NAME_REN" || e="$e sin--b"
  lista_contiene "$C_NAME_E20" || e="$e sin--c"
  [ -z "$e" ] && marca E3.7 PASS || marca E3.7 FAIL "$e"
  return 0
}

# --- E3.8 — GET lista con ?status= filtra ------------------------------------------
case_e3_8() {
  echo "[E3.8] GET ?status=ACTIVE / ARCHIVED" >&2
  local e=""
  req GET "$URL_PROJECTS?status=ACTIVE" "" "$(bearer_hdr)" || { marca E3.8 FAIL "curl ACTIVE"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e ACTIVE-status=$CODIGO"
  es_array_json || e="$e ACTIVE-no-array"
  lista_contiene "$A_NAME" || e="$e ACTIVE-sin--a"
  lista_contiene "$B_NAME_REN" || e="$e ACTIVE-sin--b"
  req GET "$URL_PROJECTS?status=ARCHIVED" "" "$(bearer_hdr)" || { marca E3.8 FAIL "curl ARCH"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e ARCH-status=$CODIGO"
  es_array_json || e="$e ARCH-no-array"
  lista_contiene "$A_NAME" && e="$e ARCH-contiene--a(deberia-estar-ACTIVE)" || true
  [ -z "$e" ] && marca E3.8 PASS || marca E3.8 FAIL "$e"
  return 0
}

# --- E3.9 — GET ?status= inválido → 400 --------------------------------------------
case_e3_9() {
  echo "[E3.9] ?status=active / ARCHIVE / '' → 400 filter_invalid_value" >&2
  local e=""
  for inv in "status=active" "status=ARCHIVE" "status="; do
    req GET "$URL_PROJECTS?$inv" "" "$(bearer_hdr)" || { marca E3.9 FAIL "curl $inv"; return 0; }
    [ "$CODIGO" = "400" ] || e="$e $inv-status=$CODIGO(nunca-silencio)"
    [ "$(campo_json code 2>/dev/null || echo err)" = "filter_invalid_value" ] || e="$e $inv-code"
    [ "$(campo_json field 2>/dev/null || echo err)" = "status" ] || e="$e $inv-field"
  done
  [ -z "$e" ] && marca E3.9 PASS || marca E3.9 FAIL "$e"
  return 0
}

# --- E3.10 — GET ficha existente 200 ------------------------------------------------
case_e3_10() {
  echo "[E3.10] GET ficha → 200 claves exactas" >&2
  local id_a e=""
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo err)"
  if [ "$id_a" = "err" ]; then marca E3.10 BLOQUEADO "sin id de -a"; return 0; fi
  req GET "$URL_PROJECTS/$id_a" "" "$(bearer_hdr)" || { marca E3.10 FAIL "curl"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO"
  [ "$(claves_json 2>/dev/null || echo err)" = "api_key_prefix,created_at,description,has_key,id,name,status,updated_at" ] \
    || e="$e claves(sin-summary)"
  [ "$(campo_json name 2>/dev/null || echo err)" = "$A_NAME" ] || e="$e name"
  [ "$(campo_json status 2>/dev/null || echo err)" = "ACTIVE" ] || e="$e status-valor"
  [ -z "$e" ] && marca E3.10 PASS || marca E3.10 FAIL "$e"
  return 0
}

# --- E3.11 — GET ficha inexistente 404 ----------------------------------------------
case_e3_11() {
  echo "[E3.11] GET 999999 → 404 not_found" >&2
  req GET "$URL_PROJECTS/999999" "" "$(bearer_hdr)" || { marca E3.11 FAIL "curl"; return 0; }
  local e=""
  [ "$CODIGO" = "404" ] || e="$e status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "not_found" ] || e="$e code"
  [ -z "$e" ] && marca E3.11 PASS || marca E3.11 FAIL "$e"
  return 0
}

# --- E3.21 — 401 sin JWT en las 4 rutas (sin parse estricto del body) ---------------
case_e3_21() {
  echo "[E3.21] 4 rutas sin Authorization → 401 invalid_token" >&2
  local e="" id_a
  id_a="$(cat "$F_ID_A" 2>/dev/null || echo 7)"
  comprobar() {
    local verbo="$1" url="$2" cuerpo="$3"
    req "$verbo" "$url" "$cuerpo" || { echo "curl-fallo-$url" >&2; return 1; }
    [ "$CODIGO" = "401" ] || return 1
    # ISO-8859-1 del entry point: SIN parse JSON estricto — grep del literal
    printf '%s' "$CUERPO" | grep -q 'invalid_token' || return 1
    return 0
  }
  comprobar GET  "$URL_PROJECTS" ""                     || e="$e GET-lista=$CODIGO"
  comprobar POST "$URL_PROJECTS" '{"name":"x"}'         || e="$e POST=$CODIGO"
  comprobar GET  "$URL_PROJECTS/$id_a" ""               || e="$e GET-ficha=$CODIGO"
  comprobar PATCH "$URL_PROJECTS/$id_a" '{"name":"x"}'  || e="$e PATCH=$CODIGO"
  [ -z "$e" ] && marca E3.21 PASS || marca E3.21 FAIL "$e"
  return 0
}

# --- E3.22 — maquetas servibles con fetch inline ------------------------------------
case_e3_22() {
  echo "[E3.22] /mockups/04 y 05 servibles; /docs/mockups NO servido" >&2
  local e=""
  req GET "$BASE_URL/mockups/04-proyectos.html" "" || { marca E3.22 FAIL "curl 04"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e 04-status=$CODIGO"
  printf '%s' "$CUERPO" | grep -q 'api/v1/panel/auth/login' || e="$e 04-sin-login"
  printf '%s' "$CUERPO" | grep -q 'api/v1/panel/projects' || e="$e 04-sin-fetch-projects"
  printf '%s' "$CUERPO" | grep -qi '<html' || e="$e 04-no-html"
  req GET "$BASE_URL/mockups/05-proyecto-detail.html" "" || { marca E3.22 FAIL "curl 05"; return 0; }
  [ "$CODIGO" = "200" ] || e="$e 05-status=$CODIGO"
  printf '%s' "$CUERPO" | grep -q 'api/v1/panel/projects/' || e="$e 05-sin-fetch-ficha"
  printf '%s' "$CUERPO" | grep -q '?id=' || e="$e 05-sin-construccion-id"
  printf '%s' "$CUERPO" | grep -q 'status' || e="$e 05-sin-patch-status"
  # el runtime (imagen frontend) NUNCA sirve /docs/**: el fallback SPA devuelve
  # el index placeholder, jamás el HTML original del repo
  req GET "$BASE_URL/docs/mockups/04-proyectos.html" "" || { marca E3.22 FAIL "curl docs"; return 0; }
  printf '%s' "$CUERPO" | grep -q 'MAQUETA' && e="$e docs-mockups-SERVIDO(violacion-G5)" || true
  [ -z "$e" ] && marca E3.22 PASS || marca E3.22 FAIL "$e"
  return 0
}

# --- E3.23 — cadenas auth de C2 intactas (subset propio) -----------------------------
case_e3_23() {
  echo "[E3.23] no-regresión auth C2: login/401×3/invalid_token/preflight" >&2
  local e="" cod_a cod_b cod_c
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    e="$e login-codigo-${CODIGO:-000}"
  else
    [ "$(claves_json 2>/dev/null || echo err)" = "expires_at,token,username" ] || e="$e login-claves"
  fi
  login "$ADMIN_INITIAL_USERNAME" "incorrecta" || { marca E3.23 FAIL "curl login-a"; return 0; }
  cod_a="$(campo_json code 2>/dev/null || echo err)"
  [ "$CODIGO" = "401" ] && [ "$cod_a" = "invalid_credentials" ] || e="$e a=$CODIGO/$cod_a"
  login "no-existe-qa" "lo que sea" || { marca E3.23 FAIL "curl login-b"; return 0; }
  cod_b="$(campo_json code 2>/dev/null || echo err)"
  [ "$CODIGO" = "401" ] && [ "$cod_b" = "invalid_credentials" ] || e="$e b=$CODIGO/$cod_b"
  req POST "$URL_LOGIN" '{ "username": ' || { marca E3.23 FAIL "curl login-c"; return 0; }
  cod_c="$(campo_json code 2>/dev/null || echo err)"
  [ "$CODIGO" = "401" ] && [ "$cod_c" = "invalid_credentials" ] || e="$e c=$CODIGO/$cod_c"
  [ "$cod_a" = "$cod_b" ] && [ "$cod_b" = "$cod_c" ] || e="$e codes-divergen"

  req GET "$URL_ME" "" || { marca E3.23 FAIL "curl me"; return 0; }
  [ "$CODIGO" = "401" ] || e="$e me-status=$CODIGO"
  printf '%s' "$CUERPO" | grep -q 'invalid_token' || e="$e me-code"

  # /api/v1/panel/projects AHORA TIENE controller y SIN JWT sigue 401 (S19)
  req GET "$URL_PROJECTS" "" || { marca E3.23 FAIL "curl projects"; return 0; }
  [ "$CODIGO" = "401" ] || e="$e projects-status=$CODIGO(nunca-404-nunca-2xx)"
  printf '%s' "$CUERPO" | grep -q 'invalid_token' || e="$e projects-code"

  if [ "$CON_ORIGIN" = "1" ]; then
    req OPTIONS "$URL_PROJECTS" "" \
      "Origin: $FRONTEND_ORIGIN" \
      "Access-Control-Request-Method: PATCH" \
      "Access-Control-Request-Headers: authorization, content-type" \
      || { marca E3.23 FAIL "curl preflight"; return 0; }
    case "$CODIGO" in 200|204) : ;; *) e="$e preflight=$CODIGO(nunca-exacto-204-solo-20[46])" ;; esac
    [ "$(cabecera "Access-Control-Allow-Origin")" = "$FRONTEND_ORIGIN" ] \
      || e="$e ACAO=$(cabecera "Access-Control-Allow-Origin")(reflejo-exacto)"
    case "$(cabecera "Access-Control-Allow-Methods")" in *PATCH*) : ;; *) e="$e metodos-sin-PATCH" ;; esac
  else
    e="$e preflight-no-verificado(sin FRONTEND_ORIGIN)"
  fi
  [ -z "$e" ] && marca E3.23 PASS || marca E3.23 FAIL "$e"
  return 0
}

# --- E3.24 — diff 0 en intocables + cero migraciones + flyway=1 ------------------------
case_e3_24() {
  echo "[E3.24] intocables diff-0, flyway_schema_history=1, db/migration sin V2+" >&2
  local e="" historial fichero
  historial="$(psql_q 'SELECT version FROM flyway_schema_history ORDER BY version' 2>/dev/null || echo err)"
  [ "$historial" = "1" ] || e="$e flyway=[$historial](solo-1)"
  if psql_q 'SELECT COUNT(*) FROM flyway_schema_history WHERE version <> '"'"'1'"'"'' 2>/dev/null | grep -vq '^0$'; then
    e="$e migraciones-V2+"
  fi
  local intocables=(
    backend/src/main/java/com/projectlog/config/SecurityConfig.java
    backend/src/main/java/com/projectlog/config/CorsConfig.java
    backend/src/main/java/com/projectlog/config/RestAuthenticationEntryPoint.java
    backend/src/main/java/com/projectlog/auth/TokenService.java
    backend/src/main/java/com/projectlog/auth/JwtAuthFilter.java
    backend/src/main/java/com/projectlog/auth/AdminSeeder.java
    backend/src/main/java/com/projectlog/config/StartupSecretsValidator.java
    backend/src/main/resources/META-INF/spring.factories
    backend/src/main/resources/application.yml
    backend/src/test/resources/application-test.yml
    backend/src/main/resources/db/migration/V1__init.sql
    backend/pom.xml
    compose.yaml
    frontend/nginx.conf
    frontend/Dockerfile
    frontend/vite.config.ts
    docs/mockups
    scripts/qa_c2_smoke.sh
    scripts/ws_echo_test.py
  )
  for fichero in "${intocables[@]}"; do
    git diff --exit-code -- "$fichero" >/dev/null 2>&1 \
      || e="$e diff!=0:$fichero"
  done
  local migraciones
  migraciones="$(ls backend/src/main/resources/db/migration/ | wc -l)"
  [ "$migraciones" = "1" ] || e="$e db/migration=$migraciones-ficheros(solo-V1)"
  # pila 3×healthy por podman al cierre
  pila_saludable || e="$e pila-no-3x-healthy"
  [ -z "$e" ] && marca E3.24 PASS || marca E3.24 FAIL "$e"
  return 0
}

ejecutar() { # $1 ID $2 función
  if ! "$2" 2>>"$TMPDIR_QA/errores_internos.log"; then
    RESULTADOS+=("$1: FAIL (error interno del script; ver errores_internos)")
  fi
}

if [ "$ejecutar_pre" = "0" ]; then
  precondiciones || {
    for id in E3.1 E3.2 E3.3 E3.4 E3.5 E3.6 E3.7 E3.8 E3.9 E3.10 E3.11 E3.12 \
              E3.13 E3.14 E3.15 E3.16 E3.17 E3.18 E3.19 E3.20 E3.21 E3.22 E3.23 E3.24; do
      marca "$id" BLOQUEADO "precondiciones fallidas (ver stderr)"
    done
  }
fi

if grep -q 'BLOQUEADO' <(printf '%s\n' "${RESULTADOS[@]:-}") 2>/dev/null; then
  echo "=== VEREDICTOS QA C3 (precondiciones) ==="
  printf '%s\n' "${RESULTADOS[@]}"
  exit 2
fi

# Orden interno CD-3.14: crear → parchear (archivar incluido) → listar.

# fija updated_at inicial de -b para E3.12 (GET previo con control)
fijar_upd_b1() {
  local id_b; id_b="$(cat "$F_ID_B" 2>/dev/null || echo err)"
  [ "$id_b" != "err" ] || return 0
  req GET "$URL_PROJECTS/$id_b" "" "$(bearer_hdr)" || return 0
  printf '%s' "$(campo_json updated_at 2>/dev/null || echo '')" > "$TMPDIR_QA/upd_b1.txt"
}

if [ "$ejecutar_pre" = "0" ]; then
  ejecutar E3.1  case_e3_1
  ejecutar E3.2  case_e3_2
  ejecutar E3.3  case_e3_3
  ejecutar E3.4  case_e3_4
  ejecutar E3.5  case_e3_5
  ejecutar E3.6  case_e3_6
  if [ "$CON_ENV/$CON_CRED/$CON_BD" != "1/1/1" ]; then :; else fijar_upd_b1; fi
  ejecutar E3.12 case_e3_12
  ejecutar E3.13 case_e3_13
  ejecutar E3.15 case_e3_15
  ejecutar E3.16 case_e3_16
  ejecutar E3.17 case_e3_17
  ejecutar E3.18 case_e3_18
  ejecutar E3.19 case_e3_19
  ejecutar E3.14 case_e3_14
  ejecutar E3.7  case_e3_7
  ejecutar E3.8  case_e3_8
  ejecutar E3.9  case_e3_9
  ejecutar E3.10 case_e3_10
  ejecutar E3.11 case_e3_11
  ejecutar E3.20 case_e3_20
  ejecutar E3.21 case_e3_21
  ejecutar E3.22 case_e3_22
  ejecutar E3.23 case_e3_23
  ejecutar E3.24 case_e3_24
fi

echo "=== VEREDICTOS QA C3 (por ID del spec) ==="
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