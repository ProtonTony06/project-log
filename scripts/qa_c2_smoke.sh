#!/usr/bin/env bash
#
# qa_c2_smoke.sh — CASOS [S]/[D] del qa-plan de C2 (delegación sdd-qa B: test-writer)
#
# Codifica los CASOS Q8–Q20 y Q22–Q27 (+ Q21 [D]) de:
#   .atl/sdd/C2/qa-plan.md (§1.2, §1.3, §6, §7),
#   .atl/sdd/C2/spec.md (E2.x) y .atl/sdd/C2/design.md (CD-1–CD-9).
# Escrito SIN ver el código del feature (prohibición de la delegación B):
# rutas, cabeceras, literales y comandos vienen citados del plan §6 y del spec.
#
# Uso:
#   bash scripts/qa_c2_smoke.sh
#
# Salida:
#   Fases de progreso por stderr; al final, una línea
#     Qn: PASS | FAIL | BLOQUEADO (detalle)
#   por caso de veredicto (§7: veredicto por ID; Q12/Q17 por sub-caso).
# Exit:
#   0 = todos PASS · 1 = hay algún FAIL · 2 = hay BLOQUEADO(s) y ningún FAIL
#
# Avisos operativos:
#  * Toda mutación de .env hace backup (.env.qa-backup) previo y lo restaura
#    (plan §6.5 y trap EXIT): el script cierra dejando la pila en F1.
#  * Higiene: este script NO imprime secretos ni el JWT de las respuestas
#    (S16). Los tokens/snapshots van al directorio temporal con permisos 700.
#  * Q21 (E2.14 [D]) es la contraparte [D] de los smokes Q16/Q17: el plan no
#    trae los hashes de referencia de la captura de C1, por lo que el caso
#    queda BLOQUEADO y el script imprime los SHA-256 actuales (captura para el
#    futuro). Si el orquestador provee QA_C1_VALIDATOR_SHA256 y
#    QA_C1_SPRING_FACTORIES_SHA256, el caso compara contra ellas.
#  * Secuencia fijada por el plan §1.3 para minimizar recomposiciones.
#
set -euo pipefail

# ----------------------------------------------------------------------------
# 0. Constantes y estado
# ----------------------------------------------------------------------------
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE_URL="http://127.0.0.1:8080"
URL_LOGIN="$BASE_URL/api/v1/panel/auth/login"
URL_ME="$BASE_URL/api/v1/panel/me"
URL_HEALTH="$BASE_URL/actuator/health"
URL_INFO="$BASE_URL/actuator/info"
URL_ENV="$BASE_URL/actuator/env"

TMPDIR_QA="$(mktemp -d /tmp/qa_c2_smoke.XXXXXX)"
chmod 700 "$TMPDIR_QA" 2>/dev/null || true
trap 'restaurar_env_siempre; rm -rf "$TMPDIR_QA"' EXIT
F_CUERPO="$TMPDIR_QA/cuerpo.txt"
F_CAB="$TMPDIR_QA/cabeceras.txt"
F_TOKEN="$TMPDIR_QA/token.txt"           # JWT del login (higiene: no se imprime)
F_EXPIRES="$TMPDIR_QA/expires_login.txt"
F_FILA_ANTES="$TMPDIR_QA/fila_antes.txt" # id|username|password_hash (Q14/Q15)

# Compose wrapper: 'podman compose' o, si el entorno lo ejecuta con el binario
# independiente (nota §6.2 del plan: "el entorno ejecuta con podman-compose").
COMPOSE_BIN=""
if command -v podman >/dev/null 2>&1 && podman compose version >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
elif command -v podman-compose >/dev/null 2>&1; then
  COMPOSE_BIN="podman-compose"
elif command -v podman >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"   # se intentará y fallará visiblemente
fi
# Corrección test-side (delegación C, qa-run, 2ª corrección de la re-ejecución):
# podman-compose 'up -d' ESPERA a healthy (lanza podman wait --condition=healthy).
# Si el backend hace fail-fast con restart:unless-stopped, entra en crash-loop y
# NUNCA llega a healthy: el 'up' se cuelga INDEFINIDAMENTE (la re-ejecución 1
# quedó 6 h colgada en el 'up' de Q17a con el backend crash-loopeando ~10k
# reinicios). Se acotan up (620 s) y down (120 s) con timeout y, si el timeout
# dispara, se purgan los hijos órfenes del compose (el wrapper de podman muere,
# el podman-compose de python sobrevive como huérfano).
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

# up_fondo_failfast — 'compose up -d --build' en SEGUNDO PLANO: para los casos
# Q16/Q17a/Q17b, que ESPERAN que el backend NO arranque (fail-fast del
# validador C1): un 'compose up' bloqueante esperaría healthy de un contenedor
# crash-loopeando (colgado del hallazgo). El observador es arranque_fallido.
UP_FONDO_PID=""
up_fondo_failfast() { compose_ up -d --build & UP_FONDO_PID=$!; sleep 2; return 0; }
fin_up_fondo() {
  if [ -n "${UP_FONDO_PID:-}" ]; then
    kill "$UP_FONDO_PID" 2>/dev/null || true
    wait "$UP_FONDO_PID" 2>/dev/null || true
    UP_FONDO_PID=""
  fi
  compose_ down || true
}

# Fijador de resultados (salida por líneas al final, §7: veredicto por ID).
declare -a RESULTADOS=()
marca() { RESULTADOS+=("$1: $2${3:+ ($3)}"); }

# ----------------------------------------------------------------------------
# 1. Helpers de entorno (.env F1), pila, BD, HTTP y parsing
# ----------------------------------------------------------------------------
env_backup() { if [ -f .env ]; then cp .env .env.qa-backup; fi; return 0; }
restaurar_env() { if [ -f .env.qa-backup ]; then mv .env.qa-backup .env; fi; return 0; }
restaurar_env_siempre() { if [ -f .env.qa-backup ]; then mv .env.qa-backup .env; fi; }

# mut_env_comentar VAR — comentar/eliminar la variable bajo prueba (Q16/Q17/Q26).
mut_env_comentar() {
  sed -i -E "s/^(export[[:space:]]+)?(${1})=(.*)$/# C2QA-eliminada: \2=\3/" .env
}
# mut_env_valor VAR VALOR — reemplaza o crea la línea de la variable (Q15/Q17b/c).
mut_env_valor() {
  python3 - "$1" "$2" <<'PY'
import re, sys
var, valor = sys.argv[1], sys.argv[2]
pat = re.compile(r"^(export\s+)?" + re.escape(var) + r"=.*$", re.M)
txt = open(".env").read()
linea = var + "=" + valor
if pat.search(txt):
    txt = pat.sub(lambda m: linea, txt)
else:
    txt = txt.rstrip("\n") + "\n" + linea + "\n"
open(".env", "w").write(txt)
PY
}

# esperar_healthy SEGUNDOS → 0 si el backend responde DE VERDAD por el proxy.
# Corrección test-side (delegación C, qa-run): el plan §6.2 propone esperar por
# /actuator/health, PERO el nginx del front (TRD §9, intacto desde C1, confirmado
# en el verify de C1) SOLO enruta /api/v1/** y /ws/logs: /actuator/** cae en el
# fallback del SPA y devuelve 200 index SIEMPRE, con el backend caído también.
# Un "healthy" por status 200 en /actuator sería un falso positivo total
# (la re-ejecución 1 pasó esperar_healthy con la pila abajo). Sondeo real:
# GET /api/v1/panel/me SIN token → 401 JSON {code:"invalid_token"} (solo lo
# emite el backend auténtico; nginx daría 502 si el upstream está caído).
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

# asegurar_healthy SEGUNDOS — esperar healthy y, si no está, levantar la pila.
asegurar_healthy() {
  if esperar_healthy "${1:-60}"; then return 0; fi
  compose_ up -d --build || return 1
  esperar_healthy "${1:-420}"
}

# recomponer SEGUNDOS — down (conserva pgdata) + up -d --build + esperar healthy.
recomponer() {
  compose_ down || true
  compose_ up -d --build || return 1
  esperar_healthy "${1:-420}"
}

# arranque_fallido — 0 si el backend hace fail-fast (o nunca llega a healthy en 120 s);
# 1 si llegó a healthy (cuando el caso exigía que NO arrancara).
# Corrección test-side: el sondeo de healthy usa /me con 401 invalid_token
# (el proxy devuelve 200 index en /actuator aunque el backend esté caído).
arranque_fallido() {
  local i estado
  for i in $(seq 1 40); do
    if curl -sS --max-time 3 -o "$TMPDIR_QA/hc_body.txt" "$URL_ME" 2>/dev/null \
       && grep -q '"code"[[:space:]]*:[[:space:]]*"invalid_token"' "$TMPDIR_QA/hc_body.txt" 2>/dev/null; then
      return 1
    fi
    estado="$(PODMAN ps -a --format '{{.Names}} {{.State}}' 2>/dev/null \
      | awk '$1 ~ /backend/ {print $2; exit}')"
    if [ "$estado" = "exited" ]; then return 0; fi
    sleep 3
  done
  return 0
}

# psql_q SQL — salida -qAt (tuplas limpias); requiere POSTGRES_USER/POSTGRES_DB de F1.
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
  # Corrección test-side (delegación C, qa-run): las cabeceras extra se pasan
  # SIN el flag -H (p. ej. "Authorization: Bearer …"); sin envolverlas, curl
  # las interpreta como URLs -> error (3) "URL rejected", la primera URL del
  # argv es la cabecera (que consume el único -o) y el cuerpo real va a
  # STDOUT, contaminando CODIGO con "000{cuerpo}401". Se envuelve en -H.
  local _haux
  for _haux in "$@"; do args+=(-H "$_haux"); done
  local cod="000"
  if cod="$(curl "${args[@]}" "$url" 2>>"$TMPDIR_QA/curl_err.log")"; then
    CODIGO="$cod"
    CUERPO="$(cat "$F_CUERPO")"
    CABECERAS="$(cat "$F_CAB")"
  else
    CODIGO="000"; CUERPO=""; CABECERAS=""
    return 1
  fi
  return 0
}

# login USER PASS — POST de login canónico (plan §6.4, Q8).
login() {
  req POST "$URL_LOGIN" "{\"username\":\"$1\",\"password\":\"$2\"}"
}

# campo_json CLAVE — imprime el valor de la clave del CUERPO actual o falla.
# Corrección test-side (delegación C, qa-run): el backend emite los cuerpos
# 401 con Content-Type application/json;charset=ISO-8859-1 (el literal
# "Autenticación requerida" NO llega como UTF-8 válido), por lo que json.load
# sobre stdin en UTF-8 lanzaba UnicodeDecodeError y las aserciones de `code`
# de Q9/Q10/Q22 etc. quedaban "err" aunque el cuerpo fuese correcto. Se
# decodifica tolerante (utf-8 + replace).
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

# claves_json — claves del objeto JSON de respuesta, ordenadas y por coma.
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

# jwt_segmento TOKEN N — segmento base64url decodificado (cabeceiro=1, payload=2).
jwt_segmento() {
  python3 - "$1" "$2" <<'PY'
import base64, sys
seg = sys.argv[1].split(".")[int(sys.argv[2]) - 1]
pad = "=" * (-len(seg) % 4)
print(base64.urlsafe_b64decode(seg + pad).decode())
PY
}

# jwt_campo_seg TOKEN N CLAVE — valor de una clave de un segmento decodificado.
jwt_campo_seg() {
  jwt_segmento "$1" "$2" | python3 -c '
import json, sys
d = json.load(sys.stdin)
print(d[sys.argv[1]])' "$3"
}

# falsificar_jwt SECRETO — JWT HS256 firmado con OTRO secreto (Q12c, fixture F5).
falsificar_jwt() {
  python3 - "$1" <<'PY'
import base64, hashlib, hmac, json, sys, time
secreto = sys.argv[1].encode()
b64u = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=").decode()
cab = b64u(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
iat = int(time.time())
carga = b64u(json.dumps({"sub": "admin-qa", "role": "ADMIN",
                         "iat": iat, "exp": iat + 8 * 3600},
                        separators=(",", ":")).encode())
cuerpo = cab + "." + carga
firma = b64u(hmac.new(secreto, cuerpo.encode(), hashlib.sha256).digest())
print(cuerpo + "." + firma)
PY
}

# alterar_ultimo_caracter CADENA — cambia UN carácter al final (Q12a: firma alterada).
alterar_ultimo_caracter() {
  local cadena="$1" ultimo
  ultimo="${cadena: -1}"
  if [ "$ultimo" = "0" ]; then echo "${cadena:0:${#cadena}-1}1"; else echo "${cadena:0:${#cadena}-1}0"; fi
}

# alterar_payload TOKEN — cambia UN carácter del segmento central (Q12b).
alterar_payload() {
  local token="$1" p1 p2 p3
  p1="${token%%.*}"
  p3="${token##*.}"
  p2="${token#*.}"; p2="${p2%.*}"
  p2="$(alterar_ultimo_caracter "$p2")"
  echo "$p1.$p2.$p3"
}

# epoch_a_iso EPOCH / iso_a_epoch "…Z" — conversión de expires_at (ISO-8601 UTC Z).
epoch_a_iso() { python3 -c '
import datetime, sys
print(datetime.datetime.fromtimestamp(int(sys.argv[1]), datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))' "$1"; }
iso_a_epoch() { python3 -c '
import datetime, sys
d = datetime.datetime.strptime(sys.argv[1], "%Y-%m-%dT%H:%M:%SZ")
print(int(d.replace(tzinfo=datetime.timezone.utc).timestamp()))' "$1"; }

# cabecera NOMBRE — valor de la cabecera de respuesta (última aparición, sin CR).
cabecera() {
  printf '%s' "$CABECERAS" | tr -d '\r' | grep -i "^${1}:" | tail -n1 | sed 's/^[^:]*:[[:space:]]*//'
}

# capturar_fila ARCHIVO — id|username|password_hash de la fila de users (Q14/Q15).
capturar_fila() {
  # Corrección test-side (delegación C, qa-run): el COALESCE original con la
  # cadena vacía escrita como `""` es un error de sintaxis en PostgreSQL
  # ("zero-length delimited identifier"), lo que dejaba Q14/Q15 falsamente
  # BLOQUEADOS. Basta un SELECT simple: con -qAt, NULL y 0 filas → salida vacía.
  if ! psql_q 'SELECT id::text FROM users LIMIT 1' > "$TMPDIR_QA/c1.txt"; then
    return 1
  fi
  if ! psql_q 'SELECT username FROM users LIMIT 1' > "$TMPDIR_QA/c2.txt"; then
    return 1
  fi
  if ! psql_q 'SELECT password_hash FROM users LIMIT 1' > "$TMPDIR_QA/c3.txt"; then
    return 1
  fi
  paste -d'|' "$TMPDIR_QA/c1.txt" "$TMPDIR_QA/c2.txt" "$TMPDIR_QA/c3.txt" > "$1"
}

logs_backend_contiene() { compose_ logs backend 2>/dev/null | grep -qiE "$1"; }
logs_backend_sin() { if compose_ logs backend 2>/dev/null | grep -qiE "$1"; then return 1; fi; return 0; }

# comprobar_401_invalid_token TOKEN — GET /me con ese Bearer → 401 invalid_token.
# ERROR_401 global: vacío si cumple; cadena con detalles si no (Q12, Q3-literal S5).
ERROR_401=""
comprobar_401_invalid_token() {
  ERROR_401=""
  if ! req GET "$URL_ME" "" "Authorization: Bearer $1"; then
    ERROR_401="curl-sin-respuesta "; return 0
  fi
  [ "$CODIGO" = "401" ] || ERROR_401="$ERROR_401 status=$CODIGO(nunca-2xx-nunca-404)"
  [ "$(campo_json code 2>/dev/null || echo err)" = "invalid_token" ] \
    || ERROR_401="$ERROR_401 code_distinto-del-literal-S5"
  return 0
}

# ----------------------------------------------------------------------------
# 2. Precondiciones globales (F1)
# ----------------------------------------------------------------------------
CON_ENV=0; CON_CRED=0; CON_BD=0; CON_ORIGIN=0
if [ -f .env ]; then
  CON_ENV=1
  # Corrección test-side (delegación C, qa-run): el .env real puede contener
  # líneas no interpretables por bash puro (p. ej. valor con '<', que bash
  # toma por una redirección, o comentarios en línea), y con 'set -e' un
  # 'source' directo abortaría TODO el script. Se evalúa línea a línea
  # ignorando errores de esa línea. SIN 'set -a': las variables quedan
  # Disponibles para el script pero NO exportadas — si se exportaran, el
  # entorno del proceso (padre de podman-compose) tendría precedencia sobre
  # el .env en la interpolación ${VAR} de compose.yaml y las MUTACIONES de
  # env de Q15/Q16/Q17/Q26 no llegarían al backend (contaminación descubierta
  # en la primera ejecución del smoke).
  while IFS= read -r _qa_linea || [ -n "$_qa_linea" ]; do
    case "$_qa_linea" in ''|\#*) continue ;; esac
    eval "$_qa_linea" 2>/dev/null || true
  done < ./.env
  [ -n "${JWT_TTL_HOURS:-}" ] || JWT_TTL_HOURS="8"
  # Fixture F5 (segundo JWT_SECRET válido >=32 B para Q12c/Q17c; no está en el plan, valor por defecto sustituible por env)
  JWT_SECRET_ALT="${JWT_SECRET_ALT:-qa-c2-alt-secret-0123456789abcdef-0123456789}"
  if [ -n "${ADMIN_INITIAL_USERNAME:-}" ] && [ -n "${ADMIN_INITIAL_PASSWORD:-}" ]; then CON_CRED=1; fi
  if [ -n "${FRONTEND_ORIGIN:-}" ]; then CON_ORIGIN=1; fi
  if [ -n "${POSTGRES_USER:-}" ] && [ -n "${POSTGRES_DB:-}" ]; then CON_BD=1; fi
fi
bloqueo_env="(sin .env F1 del despliegue: no se puede preparar el estado de BD/env)"

# ----------------------------------------------------------------------------
# 3. Casos (una función por ID; registra su veredicto; devuelve siempre 0)
# ----------------------------------------------------------------------------

# --- Q13 (E2.7): BD-A — arranque limpio, seed de 1 fila, PHC, role, login OK ---
case_q13() {
  if [ "$CON_ENV/$CON_BD" != "1/1" ]; then marca Q13 BLOQUEADO "$bloqueo_env"; return 0; fi
  if [ -z "$COMPOSE_BIN" ]; then marca Q13 BLOQUEADO "ni podman ni podman-compose disponibles"; return 0; fi
  echo "[Q13] BD-A: down -v (borrar pgdata) + up -d --build" >&2
  compose_ down -v || true
  # Fallback: borrar volúmenes pgdata remanentes si 'down -v' no llegara a eliminarlos.
  PODMAN volume ls --format '{{.Name}}' 2>/dev/null | grep -i pgdata | while read -r vol; do
    PODMAN volume rm --force "$vol" 2>/dev/null || true
  done || true
  if ! compose_ up -d --build; then
    marca Q13 FAIL "compose up falló (BD-A)"; return 0
  fi
  if ! esperar_healthy 420; then
    marca Q13 FAIL "la pila no llega a healthy en BD-A (señal: arranque/seed rompen la pila)"; return 0
  fi
  local e=""

  # COUNT(*) exacto = 1
  local cuenta
  cuenta="$(psql_q 'SELECT COUNT(*) FROM users' 2>/dev/null || echo err)"
  [ "$cuenta" = "1" ] || e="$e COUNT=$cuenta(esperado-1)"

  # username / role / prefijo PHC / timestamps NOT NULL
  local usuario db_role phc ts
  usuario="$(psql_q 'SELECT username FROM users LIMIT 1' 2>/dev/null || echo err)"
  db_role="$(psql_q 'SELECT role FROM users LIMIT 1' 2>/dev/null || echo err)"
  phc="$(psql_q 'SELECT left(password_hash,31) FROM users LIMIT 1' 2>/dev/null || echo err)"
  # Corrección test-side (delegación C, qa-run): hay EXACTAMENTE 1 fila, así
  # que el COUNT no-null esperado es 1 (el original "= 2" marcaba Q13 fallo
  # falso "timestamps-nulos").
  ts="$(psql_q "SELECT CASE WHEN COUNT(*) = 1 THEN 'ok' ELSE 'mal' END FROM users
                WHERE created_at IS NOT NULL AND updated_at IS NOT NULL" 2>/dev/null || echo err)"
  [ "$usuario" = "$ADMIN_INITIAL_USERNAME" ] || e="$e username($usuario)"
  [ "$db_role" = "ADMIN" ] || e="$e role($db_role)"
  [ "$phc" = '$argon2id$v=19$m=19456,t=2,p=1$' ] || e="$e prefijo-PHC($phc)"
  [ "$ts" = "ok" ] || e="$e timestamps-nulos"

  # E3 — cero migraciones nuevas (S24). Corrección test-side (delegación C):
  # Flyway v11 emite «Schema "public" is up to date. No migration necessary.»
  # (y en BD-A fresco registra la aplicación de V1), por lo que el grep literal
  # 'schema up to date' nunca casa con la línea real. Se acepta la línea de
  # migración aplicada o la de up-to-date, y se cubre que NO se apliquen V2+.
  logs_backend_contiene 'is up to date|successfully applied [0-9]+ migration|migrating schema' \
    || e="$e sin-log-MigracionOUpToDate"
  logs_backend_sin 'V[2-9]__[0-9]' || e="$e migraciones-nuevas-detectadas"
  # CD-8/S3 — no debe aparecer el usuario in-memory generado por Boot
  logs_backend_sin 'Using generated security password' || e="$e usuario-in-memory-en-logs"

  # login posterior con esas credenciales → 200 (reusa el curl de Q8)
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    e="$e login-tras-seed-codigo-${CODIGO:-000}"
  fi

  [ -z "$e" ] && marca Q13 PASS || marca Q13 FAIL "$e"
  return 0
}

# --- Q8 (E2.1): login 200 {token, username, expires_at} + higiene de logs ---
case_q8() {
  if [ "$CON_ENV/$CON_CRED" != "1/1" ]; then marca Q8 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q8] login correcto" >&2
  asegurar_healthy 120 || { marca Q8 BLOQUEADO "pila no healthy (depende de Q13)"; return 0; }
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD"; then
    marca Q8 FAIL "curl falló"; return 0
  fi
  local e=""
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO(esperado-200)"

  local claves
  claves="$(claves_json || echo err)"
  [ "$claves" = "expires_at,token,username" ] || e="$e claves=$claves(esperado-expires_at,token,username)"

  local token expires_at nseg alg
  token="$(campo_json token 2>/dev/null || echo "")"
  expires_at="$(campo_json expires_at 2>/dev/null || echo "")"
  # JWT de 3 segmentos con alg=HS256 (decodificar el header base64url)
  nseg="$(printf '%s' "$token" | awk -F'[.]' '{print NF}')"
  [ "$nseg" = "3" ] || e="$e token=$nseg-segmentos"
  alg="$(jwt_campo_seg "$token" 1 alg 2>/dev/null || echo err)"
  [ "$alg" = "HS256" ] || e="$e alg=$alg"

  # expires_at: ISO-8601 UTC con sufijo Z, sin milisegundos
  printf '%s' "$expires_at" | grep -Eq '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$' \
    || e="$e expires_at-sin-formato-ISO-Z"

  # exp == iat + TTL (claims del payload contra TTL de JWT_TTL_HOURS)
  local exp_epoch iat_epoch ttl_seg
  exp_epoch="$(jwt_campo_seg "$token" 2 exp 2>/dev/null || echo err)"
  iat_epoch="$(jwt_campo_seg "$token" 2 iat 2>/dev/null || echo err)"
  ttl_seg=$(( ${JWT_TTL_HOURS:-8} * 3600 ))
  [ "$((exp_epoch - iat_epoch))" = "$ttl_seg" ] \
    || e="$e exp-iat=$((exp_epoch - iat_epoch))(esperado-$ttl_seg)"
  [ "$(epoch_a_iso "$exp_epoch")" = "$expires_at" ] || e="$e exp-no-representado-en-expires_at"

  # ventana ≈ TTL respecto de ahora (7–9 h si TTL=8): [TTL-1, TTL+1]
  local ahora exp_diff
  ahora="$(date +%s)"; exp_diff=$(( exp_epoch - ahora ))
  { [ "$exp_diff" -le "$(( ttl_seg + 3600 ))" ] && [ "$exp_diff" -ge "$(( ttl_seg - 3600 ))" ]; } \
    || e="$e ventana-TTL-respecto-a-ahora=${exp_diff}s"

  # Higiene (S3/CD-8): sin usuario in-memory; la contraseña no aparece en logs de la pila
  logs_backend_sin 'Using generated security password' || e="$e usuario-in-memory-en-logs"
  if compose_ logs backend 2>/dev/null | grep -Fq -- "$ADMIN_INITIAL_PASSWORD"; then
    e="$e password-en-logs(S16)"
  fi

  if [ -z "$e" ]; then
    printf '%s' "$token" > "$F_TOKEN"
    printf '%s' "$expires_at" > "$F_EXPIRES"
    marca Q8 PASS
  else
    marca Q8 FAIL "$e"
  fi
  return 0
}

# --- Q9 (E2.2 [S]): 401 x3 con literal idéntico (password/usuario/cuerpo) ---
case_q9() {
  if [ "$CON_ENV/$CON_CRED" != "1/1" ]; then marca Q9 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q9] login 401 x3" >&2
  asegurar_healthy 60 || { marca Q9 BLOQUEADO "pila no healthy (depende de Q13)"; return 0; }
  local e=""

  # (a) password incorrecto
  if ! login "$ADMIN_INITIAL_USERNAME" "no-es-la-contrasena"; then
    marca Q9 FAIL "curl(a) sin respuesta"; return 0
  fi
  local cod_a msg_a
  [ "$CODIGO" = "401" ] || e="$e a$status-$CODIGO"
  cod_a="$(campo_json code 2>/dev/null || echo err)"
  msg_a="$(campo_json message 2>/dev/null || echo err)"
  [ "$cod_a" = "invalid_credentials" ] || e="$e a-code=$cod_a"

  # (b) username inexistente
  if ! login "no-existe" "lo-que-sea"; then
    marca Q9 FAIL "curl(b) sin respuesta"; return 0
  fi
  local cod_b msg_b
  [ "$CODIGO" = "401" ] || e="$e b$status-$CODIGO"
  cod_b="$(campo_json code 2>/dev/null || echo err)"
  msg_b="$(campo_json message 2>/dev/null || echo err)"
  [ "$cod_b" = "invalid_credentials" ] || e="$e b-code=$cod_b"

  # (c) cuerpo malformado (JSON roto)
  if ! req POST "$URL_LOGIN" '{ "username": '; then
    marca Q9 FAIL "curl(c) sin respuesta"; return 0
  fi
  [ "$CODIGO" = "401" ] || e="$e c$status-$CODIGO"
  local cod_c msg_c
  cod_c="$(campo_json code 2>/dev/null || echo err)"
  msg_c="$(campo_json message 2>/dev/null || echo err)"
  [ "$cod_c" = "invalid_credentials" ] || e="$e c-code=$cod_c"
  # sin field diferenciador en el último cuerpo (Schema §3.1 no usado aquí)
  if printf '%s' "$CUERPO" | grep -q '"field"'; then e="$e field-en-401(c)"; fi

  # literal idéntico en las 3 ramas (S17, anti user-enumeration)
  [ "$cod_a" = "$cod_b" ] && [ "$cod_b" = "$cod_c" ] || e="$e codes-divergen"
  [ "$msg_a" = "$msg_b" ] && [ "$msg_b" = "$msg_c" ] || e="$e messages-divergen(subtipos?)"

  [ -z "$e" ] && marca Q9 PASS || marca Q9 FAIL "$e"
  return 0
}

# --- Q10 (E2.3/Done 07): /me y rutas futuras del panel → 401 invalid_token ---
case_q10() {
  if [ "$CON_ENV" != "1" ]; then marca Q10 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q10] panel protegido sin JWT" >&2
  asegurar_healthy 60 || { marca Q10 BLOQUEADO "pila no healthy (depende de Q13)"; return 0; }
  local e=""

  # Q10: la petición es SIN header Authorization (E2.3)
  if ! req GET "$URL_ME" ""; then marca Q10 FAIL "curl /me"; return 0; fi
  [ "$CODIGO" = "401" ] || e="$e me-status=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "invalid_token" ] || e="$e me-code"

  if ! req GET "$BASE_URL/api/v1/panel/projects" ""; then marca Q10 FAIL "curl GET projects"; return 0; fi
  [ "$CODIGO" = "401" ] || e="$e projects-GET=$CODIGO(nunca-404-nunca-2xx)"
  [ "$(campo_json code 2>/dev/null || echo err)" = "invalid_token" ] || e="$e projects-GET-code"

  if ! req POST "$BASE_URL/api/v1/panel/projects" '{}'; then marca Q10 FAIL "curl POST projects"; return 0; fi
  [ "$CODIGO" = "401" ] || e="$e projects-POST=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "invalid_token" ] || e="$e projects-POST-code"

  [ -z "$e" ] && marca Q10 PASS || marca Q10 FAIL "$e"
  return 0
}

# --- Q11 (E2.5): GET /me con Bearer → {username, role:"ADMIN", expires_at} ---
case_q11() {
  if [ "$CON_ENV/$CON_CRED" != "1/1" ]; then marca Q11 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q11] /me con JWT del login de Q8" >&2
  asegurar_healthy 60 || { marca Q11 BLOQUEADO "pila no healthy (depende de Q13)"; return 0; }
  if [ ! -s "$F_TOKEN" ]; then marca Q11 BLOQUEADO "sin token de Q8"; return 0; fi
  local token
  token="$(cat "$F_TOKEN")"
  if ! req GET "$URL_ME" "" "Authorization: Bearer $token"; then marca Q11 FAIL "curl"; return 0; fi
  local e=""
  [ "$CODIGO" = "200" ] || e="$e status=$CODIGO"

  local claves
  claves="$(claves_json || echo err)"
  [ "$claves" = "expires_at,role,username" ] || e="$e claves=$claves"

  local usuario db_role expires_at
  usuario="$(campo_json username 2>/dev/null || echo err)"
  db_role="$(campo_json role 2>/dev/null || echo err)"
  expires_at="$(campo_json expires_at 2>/dev/null || echo err)"
  [ "$db_role" = "ADMIN" ] || e="$e role=$db_role(no-literal-ADMIN)"
  [ "$usuario" = "$ADMIN_INITIAL_USERNAME" ] || e="$e username=$usuario(vs-sub-del-token)"

  # expires_at: del token (exp), idéntico formato Z al del login con el mismo token
  local exp_epoch
  exp_epoch="$(jwt_campo_seg "$token" 2 exp 2>/dev/null || echo err)"
  [ "$(epoch_a_iso "$exp_epoch")" = "$expires_at" ] || e="$e expires_at-vs-exp-token-distinto"
  [ "$expires_at" = "$(cat "$F_EXPIRES")" ] || e="$e expires_at-distinto-del-del-login"

  # (S18: sin consulta BBDD observable — la aserción dura es de diseño; el smoke
  #  solo puede verificar que la respuesta sale del token, lo cual ya cubren
  #  las comparaciones de arriba.)
  [ -z "$e" ] && marca Q11 PASS || marca Q11 FAIL "$e"
  return 0
}

# --- Q12 (E2.4 [S]): a/b/c — firma alterada, payload alterado, otro secreto ---
case_q12() {
  if [ "$CON_ENV/$CON_CRED" != "1/1" ]; then
    marca Q12a BLOQUEADO "$bloqueo_env"
    marca Q12b BLOQUEADO "$bloqueo_env"
    marca Q12c BLOQUEADO "$bloqueo_env"; return 0
  fi
  echo "[Q12] tokens manipulados (a firma / b payload / c otro secreto)" >&2
  asegurar_healthy 60 || {
    marca Q12a BLOQUEADO "pila no healthy"
    marca Q12b BLOQUEADO "pila no healthy"
    marca Q12c BLOQUEADO "pila no healthy"; return 0
  }
  if [ ! -s "$F_TOKEN" ]; then
    marca Q12a BLOQUEADO "sin token de Q8"
    marca Q12b BLOQUEADO "sin token de Q8"
    marca Q12c BLOQUEADO "sin token de Q8"; return 0
  fi
  local token
  token="$(cat "$F_TOKEN")"
  comprobar_401_invalid_token "$(alterar_ultimo_caracter "$token")" || true   # F4(a)
  [ -z "$ERROR_401" ] && marca Q12a PASS || marca Q12a FAIL "${ERROR_401}"
  comprobar_401_invalid_token "$(alterar_payload "$token")" || true           # F4(b)
  [ -z "$ERROR_401" ] && marca Q12b PASS || marca Q12b FAIL "${ERROR_401}"
  comprobar_401_invalid_token "$(falsificar_jwt "$JWT_SECRET_ALT")" || true   # F5
  [ -z "$ERROR_401" ] && marca Q12c PASS || marca Q12c FAIL "${ERROR_401}"
  return 0
}

# --- Q14 (E2.8/Done 07): recomposición BD-B — idempotencia fuerte del seed ---
case_q14() {
  if [ "$CON_ENV/$CON_BD" != "1/1" ]; then marca Q14 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q14] recomposición completa (down + up --build, SIN -v)" >&2
  if ! esperar_healthy 15; then
    marca Q14 BLOQUEADO "pila no previa (depende de Q8–Q12)"; return 0
  fi
  capturar_fila "$F_FILA_ANTES" || { marca Q14 BLOQUEADO "no se pudo capturar la fila (psql)"; return 0; }

  if ! recomponer 420; then marca Q14 FAIL "recomposición/healthy falló"; return 0; fi
  local e=""
  local cuenta
  cuenta="$(psql_q 'SELECT COUNT(*) FROM users' 2>/dev/null || echo err)"
  [ "$cuenta" = "1" ] || e="$e COUNT=$cuenta(esperado-1)"

  capturar_fila "$TMPDIR_QA/fila_despues.txt" || { marca Q14 FAIL "psql tras reinicio"; return 0; }
  if ! diff -q "$F_FILA_ANTES" "$TMPDIR_QA/fila_despues.txt" >/dev/null 2>&1; then
    e="$e fila-distinta(id/username/password_hash cambiados)"
  fi

  # idempotencia fuerte: mismo password_hash ⇒ NO se re-seedó (el salt Argon2 cambia en cada encode)
  logs_backend_contiene 'seed omitido' || e="$e sin-log-SeedOmitido"
  # E3 en frío: Flyway "is up to date" (cero migraciones nuevas) — literal
  # real del log «Schema "public" is up to date.» (corrección test-side).
  logs_backend_contiene 'is up to date' || e="$e sin-log-SchemaUpToDate"
  # login tras el reinicio con las credenciales originales
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    e="$e login-original-codigo-${CODIGO:-000}"
  fi

  [ -z "$e" ] && marca Q14 PASS || marca Q14 FAIL "$e"
  return 0
}

# --- Q17 (E2.14/E2.10, Re-ataque B): a/b/c — fail-fast del validador C1 intocable ---
case_q17() {
  if [ "$CON_ENV/$CON_BD" != "1/1" ]; then
    marca Q17a BLOQUEADO "$bloqueo_env"
    marca Q17b BLOQUEADO "$bloqueo_env"
    marca Q17c BLOQUEADO "$bloqueo_env"; return 0
  fi
  echo "[Q17] mutaciones de JWT_SECRET (backup + restauración obligatorias)" >&2
  env_backup

  # --- Q17a: SIN JWT_SECRET ---
  mut_env_comentar "JWT_SECRET"
  compose_ down || true
  up_fondo_failfast
  local e_a=""
  if ! arranque_fallido; then
    e_a="el-backend-ARRANCA-sin-JWT_SECRET(fail-fast-ausente)"
  fi
  if ! compose_ logs backend 2>/dev/null | tail -n 200 | grep -q "JWT_SECRET"; then
    e_a="$e_a log-sin-referencia-a-JWT_SECRET(mensaje-del-validador-no-visible)"
  fi
  fin_up_fondo
  [ -z "$e_a" ] && marca Q17a PASS || marca Q17a FAIL "$e_a"

  # --- Q17b: JWT_SECRET corto (<32 bytes UTF-8, plan: p. ej. 20) ---
  mut_env_comentar "JWT_SECRET"
  mut_env_valor "JWT_SECRET" "qa-secreto-corto-0123"   # 20 bytes < 32
  compose_ down || true
  up_fondo_failfast
  local e_b=""
  if ! arranque_fallido; then
    e_b="el-backend-ARRANCA-con-JWT_SECRET-corto(<32B)"
  fi
  if ! compose_ logs backend 2>/dev/null | tail -n 200 | grep -q "JWT_SECRET"; then
    e_b="$e_b log-sin-referencia-a-JWT_SECRET"
  fi
  fin_up_fondo
  [ -z "$e_b" ] && marca Q17b PASS || marca Q17b FAIL "$e_b"

  # --- Q17c: JWT_SECRET válido >=32 B distinto (F5) → pila green + login OK ---
  mut_env_comentar "JWT_SECRET"
  mut_env_valor "JWT_SECRET" "$JWT_SECRET_ALT"
  compose_ down || true
  local e_c=""
  if ! recomponer 420; then
    e_c="pila-no-healthy-con-secreto-valido->=32B"
  else
    if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
      e_c="$e_c login-codigo-${CODIGO:-000}"
    fi
  fi
  [ -z "$e_c" ] && marca Q17c PASS || marca Q17c FAIL "$e_c"

  # restauración obligatoria ANTES de continuar (plan §6.5)
  restaurar_env
  compose_ down || true
  return 0
}

# --- Q15 (E2.9, Re-ataque A): tabla no vacía + username del env distinto → no acción ---
case_q15() {
  if [ "$CON_ENV/$CON_BD" != "1/1" ]; then marca Q15 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q15] recomposición con ADMIN_INITIAL_* mutados" >&2
  asegurar_healthy 420 || {
    marca Q15 BLOQUEADO "no hubo pila previa healthy para BD-B (depende de Q17/Q14)"; return 0
  }
  capturar_fila "$TMPDIR_QA/fila_q15_ref.txt" || { marca Q15 BLOQUEADO "psql falló (captura previa)"; return 0; }
  local usuario_original="$ADMIN_INITIAL_USERNAME" password_original="$ADMIN_INITIAL_PASSWORD"

  env_backup
  mut_env_valor "ADMIN_INITIAL_USERNAME" "qa-usuario-alt-Q15"
  mut_env_valor "ADMIN_INITIAL_PASSWORD" "qa-contra-alt-Q15"
  if ! recomponer 420; then
    restaurar_env
    marca Q15 FAIL "arranque falló (E2.9 exige recomposición SIN error)"; return 0
  fi
  local e=""
  local cuenta
  cuenta="$(psql_q 'SELECT COUNT(*) FROM users' 2>/dev/null || echo err)"
  [ "$cuenta" = "1" ] || e="$e COUNT=$cuenta(gate-G4-violado)"
  capturar_fila "$TMPDIR_QA/fila_q15_despues.txt" || true
  if ! diff -q "$TMPDIR_QA/fila_q15_ref.txt" "$TMPDIR_QA/fila_q15_despues.txt" >/dev/null 2>&1; then
    e="$e fila-original-alterada(no-idempotente)"
  fi

  # login con credenciales ORIGINALES → 200
  if ! login "$usuario_original" "$password_original" || [ "$CODIGO" != "200" ]; then
    e="$e login-original-codigo-${CODIGO:-000}"
  fi
  # login con el NUEVO username del env mutado (password también nueva) → 401 invalid_credentials
  login "qa-usuario-alt-Q15" "qa-contra-alt-Q15" || true
  [ "$CODIGO" = "401" ] || e="$e login-alt-codigo-$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "invalid_credentials" ] || e="$e login-alt-code-distinto"

  restaurar_env
  [ -z "$e" ] && marca Q15 PASS || marca Q15 FAIL "$e"
  return 0
}

# --- Q16 (E2.10): sin ADMIN_INITIAL_PASSWORD → fail-fast idéntico a C1 (validador) ---
case_q16() {
  if [ "$CON_ENV/$CON_BD" != "1/1" ]; then marca Q16 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q16] mutación: .env sin ADMIN_INITIAL_PASSWORD" >&2
  env_backup
  mut_env_comentar "ADMIN_INITIAL_PASSWORD"
  compose_ down || true
  up_fondo_failfast
  local e=""
  if ! arranque_fallido; then
    e="el-backend-ARRANCA-sin-ADMIN_INITIAL_PASSWORD(E4-roto)"
  fi
  # el mensaje proviene del validador de C1 (S29 intocable), NO del seeder (S23)
  if ! compose_ logs backend 2>/dev/null | tail -n 200 | grep -q "ADMIN_INITIAL_PASSWORD"; then
    e="$e log-sin-referencia-a-ADMIN_INITIAL_PASSWORD"
  fi
  logs_backend_sin 'sembrado|seed omitido' || e="$e mensaje-con-olor-a-seeder"
  fin_up_fondo
  restaurar_env
  [ -z "$e" ] && marca Q16 PASS || marca Q16 FAIL "$e"
  return 0
}

# --- Q18 (E2.11): estabilidad 3× healthy + health/info por el proxy (E1) ---
case_q18() {
  if [ "$CON_ENV" != "1" ]; then marca Q18 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q18] estabilidad de la pila (>=2 healthchecks) + actuator" >&2
  asegurar_healthy 420 || {
    marca Q18 BLOQUEADO "la pila no recupera healthy tras la restauración F1"; return 0
  }
  local e=""

  local snap1 snap2
  snap1="$(PODMAN ps --format '{{.Names}} {{.Status}}' | grep -E 'backend|frontend|postgres' || true)"
  sleep 75   # >= 2 healthchecks de 30 s (plan Q18)
  snap2="$(PODMAN ps --format '{{.Names}} {{.Status}}' | grep -E 'backend|frontend|postgres' || true)"

  local svc
  for svc in backend frontend postgres; do
    grep -E "^[^ ]*${svc} .*Up.*\(healthy\)" <<<"$snap1" >/dev/null || e="$e $svc-no-healthy(t1)"
    grep -E "^[^ ]*${svc} .*Up.*\(healthy\)" <<<"$snap2" >/dev/null || e="$e $svc-no-healthy(t2)"
  done

  # sin bucles de restart (E1/RG-6): RestartCount estable entre instantes
  local rc1="" rc2="" nombre val
  for nombre in $(PODMAN ps --format '{{.Names}}' | grep -E 'backend|frontend|postgres' || true); do
    val="$(PODMAN inspect --format '{{.State.RestartCount}}' "$nombre" 2>/dev/null)" || val="nd"
    rc1="${rc1}${val};"
    val="$(PODMAN inspect --format '{{.State.RestartCount}}' "$nombre" 2>/dev/null)" || val="nd"
    rc2="${rc2}${val};"
  done
  if [[ "$rc1" != *"nd"* ]]; then
    [ "$rc1" = "$rc2" ] || e="$e RestartCount-creciente(E1/RG-6)"
  fi

  if ! req GET "$URL_HEALTH" ""; then e="$e curl-health"; fi
  [ "$CODIGO" = "200" ] || e="$e health=$CODIGO"
  if ! printf '%s' "$CUERPO" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    e="$e health-sin-status-UP"
  fi
  if ! req GET "$URL_INFO" ""; then e="$e curl-info"; fi
  [ "$CODIGO" = "200" ] || e="$e info=$CODIGO"

  [ -z "$e" ] && marca Q18 PASS || marca Q18 FAIL "$e"
  return 0
}

# --- Q19 (E2.12): /actuator/env 404 y jamás contenido de configuración (E10) ---
case_q19() {
  if [ "$CON_ENV" != "1" ]; then marca Q19 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q19] /actuator/env por el proxy" >&2
  asegurar_healthy 60 || { marca Q19 BLOQUEADO "pila no healthy"; return 0; }
  if ! req GET "$URL_ENV" ""; then marca Q19 FAIL "curl env"; return 0; fi
  local e=""
  [ "$CODIGO" = "404" ] || e="$e status=$CODIGO(esperado-404,E10)"
  # el cuerpo NUNCA contiene contenido de configuración (nombres ni valores)
  if printf '%s' "$CUERPO" | grep -qiE 'JWT_SECRET|FRONTEND_ORIGIN|ADMIN_INITIAL|spring\.datasource|password_hash|jwt-ttl'; then
    e="$e cuerpo-con-contenido-de-configuracion"
  fi
  [ -z "$e" ] && marca Q19 PASS || marca Q19 FAIL "$e"
  return 0
}

# --- Q20 (E2.13): WS Echo de C1 completo (idle 75 s) sin romperse (G2) ---
case_q20() {
  echo "[Q20] scripts/ws_echo_test.py (incluye idle 75 s)" >&2
  if [ ! -f scripts/ws_echo_test.py ]; then
    marca Q20 BLOQUEADO "no existe scripts/ws_echo_test.py (artefacto de C1)"; return 0
  fi
  if [ "$CON_ENV" != "1" ]; then marca Q20 BLOQUEADO "$bloqueo_env"; return 0; fi
  asegurar_healthy 60 || { marca Q20 BLOQUEADO "pila no healthy"; return 0; }
  if timeout 200 python3 scripts/ws_echo_test.py >>"$TMPDIR_QA/q20.log" 2>&1; then
    marca Q20 PASS
  else
    marca Q20 FAIL "exit != 0 (log en $TMPDIR_QA/q20.log)"
  fi
  return 0
}

# --- Q22 (E2.15): puerto único 8080, index placeholder, método+ruta exactos, denyAll ---
case_q22() {
  if [ "$CON_ENV" != "1" ]; then marca Q22 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q22] proxy, método+ruta exactos, denyAll" >&2
  asegurar_healthy 60 || { marca Q22 BLOQUEADO "pila no healthy"; return 0; }
  local e=""

  # único puerto host publicado: 8080 (E2)
  local puertos nombre
  puertos="$(
    for nombre in $(PODMAN ps --format '{{.Names}}' 2>/dev/null | grep -E 'backend|frontend|postgres' || true); do
      PODMAN port "$nombre" 2>/dev/null || true
    done | awk '{print $NF}' | awk -F: '{print $NF}' | sort -u | tr '\n' ' '
  )"
  if [ -z "$puertos" ]; then
    e="$e puertos-no-determinados(inspeccion-de-podman-port-fallo)"
  elif [ "$puertos" != "8080 " ]; then
    e="$e puertos-publicados=[$puertos](esperado-[8080],E2)"
  fi

  # index placeholder servido por nginx
  if ! req GET "$BASE_URL/" ""; then e="$e curl-index"; fi
  [ "$CODIGO" = "200" ] || e="$e index=$CODIGO"
  printf '%s' "$CUERPO" | grep -qi '<html' || e="$e index-no-es-html"

  # GET sobre el login (método != POST público) → 401 JSON invalid_token (S3/E8)
  if ! req GET "$URL_LOGIN" ""; then e="$e curl-GET-login"; fi
  [ "$CODIGO" = "401" ] || e="$e GET-login=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "invalid_token" ] || e="$e GET-login-code"
  printf '%s' "$CUERPO" | grep -qi '<html' && e="$e GET-login-devuelve-html(SPA)" || true

  # ruta inexistente bajo /api/v1/panel/ → 401 del filtro (no el index del SPA, E8)
  if ! req GET "$BASE_URL/api/v1/panel/noexiste" ""; then e="$e curl-noexiste"; fi
  [ "$CODIGO" = "401" ] || e="$e noexiste=$CODIGO"
  [ "$(campo_json code 2>/dev/null || echo err)" = "invalid_token" ] || e="$e noexiste-code"

  # ruta FUERA de los matchers (cadena 3, denyAll) → 401 (o 403), nunca 2xx ni index
  if ! req GET "$BASE_URL/api/v1/otra" ""; then e="$e curl-otra"; fi
  { [ "$CODIGO" = "401" ] || [ "$CODIGO" = "403" ]; } || e="$e otra=$CODIGO(nunca-2xx)"
  printf '%s' "$CUERPO" | grep -qi '<html' && e="$e otra-devuelve-html" || true

  [ -z "$e" ] && marca Q22 PASS || marca Q22 FAIL "$e"
  return 0
}

# --- Q23 (E2.16): ingesta 401 declarada (deny-by-default) + sin CORS (S26/TRD §5) ---
case_q23() {
  if [ "$CON_ENV" != "1" ]; then marca Q23 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q23] POST /api/v1/ingest[/batch] con y sin X-API-Key" >&2
  asegurar_healthy 60 || { marca Q23 BLOQUEADO "pila no healthy"; return 0; }
  local e=""

  if ! req POST "$BASE_URL/api/v1/ingest" '{"x":1}'; then e="$e curl-a"; fi
  [ "$CODIGO" = "401" ] || e="$e ingest-sin-header=$CODIGO(nunca-404-nunca-2xx)"
  [ "$(campo_json code 2>/dev/null || echo err)" != "err" ] || e="$e ingest-a-sin-cuerpo-estandar(code)"
  [ "$(campo_json message 2>/dev/null || echo err)" != "err" ] || e="$e ingest-a-sin-message"

  if ! req POST "$BASE_URL/api/v1/ingest" '{"x":1}' "X-API-Key: anything"; then e="$e curl-con-header"; fi
  [ "$CODIGO" = "401" ] || e="$e ingest-con-XAPIKey=$CODIGO"

  if ! req POST "$BASE_URL/api/v1/ingest/batch" '[]'; then e="$e curl-batch"; fi
  [ "$CODIGO" = "401" ] || e="$e ingest-batch=$CODIGO"

  # preflight sobre la ingesta → SIN cabeceras Access-Control-* (S26, TRD §5)
  if ! req OPTIONS "$BASE_URL/api/v1/ingest" "" \
      "Origin: http://evil.example" \
      "Access-Control-Request-Method: GET"; then e="$e curl-preflight"; fi
  if printf '%s' "$CABECERAS" | tr -d '\r' | grep -qi '^access-control-'; then
    e="$e ingesta-con-cabeceras-CORS(S26-violado)"
  fi

  [ -z "$e" ] && marca Q23 PASS || marca Q23 FAIL "$e"
  return 0
}

# --- Q24 (E2.17, Re-ataque C1): CORS con FRONTEND_ORIGIN (preflight 204 + reflejo) ---
case_q24() {
  if [ "$CON_ENV/$CON_ORIGIN" != "1/1" ]; then
    marca Q24 BLOQUEADO "FRONTEND_ORIGIN vacia/ausente en F1 (el caso exige valor configurado)"; return 0
  fi
  echo "[Q24] CORS con FRONTEND_ORIGIN configurada" >&2
  asegurar_healthy 60 || { marca Q24 BLOQUEADO "pila no healthy"; return 0; }
  local e=""

  if ! req OPTIONS "$URL_LOGIN" "" \
      "Origin: $FRONTEND_ORIGIN" \
      "Access-Control-Request-Method: POST" \
      "Access-Control-Request-Headers: authorization, content-type"; then e="$e curl-preflight"; fi
  [ "$CODIGO" = "204" ] || e="$e preflight=$CODIGO(esperado-204-sin-autenticacion)"
  [ "$(cabecera "Access-Control-Allow-Origin")" = "$FRONTEND_ORIGIN" ] \
    || e="$e ACAO=$(cabecera "Access-Control-Allow-Origin")(nunca-*)"
  local metodos cab_headers
  metodos="$(cabecera "Access-Control-Allow-Methods")"
  cab_headers="$(cabecera "Access-Control-Allow-Headers")"
  case "$metodos" in *POST*) : ;; *) e="$e metodos-sin-POST" ;; esac
  case "$metodos" in *OPTIONS*) : ;; *) e="$e metodos-sin-OPTIONS" ;; esac
  case "$metodos" in *GET*) : ;; *) e="$e metodos-sin-GET(S25)" ;; esac
  case "$metodos" in *PATCH*) : ;; *) e="$e metodos-sin-PATCH(S25)" ;; esac
  case "$metodos" in *DELETE*) : ;; *) e="$e metodos-sin-DELETE(S25)" ;; esac
  case "$(printf '%s' "$cab_headers" | tr '[:upper:]' '[:lower:]')" in
    *authorization*) : ;; *) e="$e headers-sin-authorization" ;;
  esac
  case "$(printf '%s' "$cab_headers" | tr '[:upper:]' '[:lower:]')" in
    *content-type*) : ;; *) e="$e headers-sin-content-type" ;;
  esac
  case "$cab_headers" in *X-Confirm-*) : ;; *) e="$e headers-sin-X-Confirm-*" ;; esac
  [ "$(cabecera "Access-Control-Allow-Credentials")" = "true" ] \
    && e="$e Allow-Credentials=true(S25-prohibido)" || true

  # GET /me con token válido y Origin configurado → respuesta con ACAO reflejada exacta
  if [ -s "$F_TOKEN" ]; then
    if ! req GET "$URL_ME" "" "Authorization: Bearer $(cat "$F_TOKEN")" "Origin: $FRONTEND_ORIGIN"; then
      e="$e curl-me-cors"
    fi
    [ "$CODIGO" = "200" ] || e="$e me-cors-status=$CODIGO"
    [ "$(cabecera "Access-Control-Allow-Origin")" = "$FRONTEND_ORIGIN" ] \
      || e="$e me-ACAO=$(cabecera "Access-Control-Allow-Origin")(reflejo-exacto)"
  else
    e="$e sin-token-de-Q8(GET-me-con-Origin-no-verificado)"
  fi

  [ -z "$e" ] && marca Q24 PASS || marca Q24 FAIL "$e"
  return 0
}

# --- Q25 (E2.17, Re-ataque C2): origen NO listado → sin cabeceras CORS de permiso ---
case_q25() {
  if [ "$CON_ENV/$CON_ORIGIN" != "1/1" ]; then
    marca Q25 BLOQUEADO "FRONTEND_ORIGIN vacia/ausente en F1"; return 0
  fi
  echo "[Q25] Origin http://evil.example (no listado)" >&2
  asegurar_healthy 60 || { marca Q25 BLOQUEADO "pila no healthy"; return 0; }
  local e=""

  if ! req OPTIONS "$URL_LOGIN" "" \
      "Origin: http://evil.example" \
      "Access-Control-Request-Method: POST"; then e="$e curl-preflight"; fi
  [ -n "$(cabecera "Access-Control-Allow-Origin")" ] && e="$e preflight-con-ACAO(S25-violado)"
  [ -n "$(cabecera "Access-Control-Allow-Methods")" ] && e="$e preflight-con-Allow-Methods"
  [ -n "$(cabecera "Access-Control-Allow-Headers")" ] && e="$e preflight-con-Allow-Headers"
  printf '%s' "$CABECERAS" | tr -d '\r' | grep -q 'evil.example' \
    && e="$e preflight-refleja-origen-no-listado" || true

  if [ -s "$F_TOKEN" ]; then
    if ! req GET "$URL_ME" "" "Authorization: Bearer $(cat "$F_TOKEN")" "Origin: http://evil.example"; then
      e="$e curl-me"
    fi
    [ -n "$(cabecera "Access-Control-Allow-Origin")" ] && e="$e me-con-ACAO"
    printf '%s' "$CABECERAS" | tr -d '\r' | grep -q 'evil.example' \
      && e="$e me-refleja-origen-no-listado" || true
  fi

  [ -z "$e" ] && marca Q25 PASS || marca Q25 FAIL "$e"
  return 0
}

# --- Q26 (E2.18, Re-ataque C3): sin FRONTEND_ORIGIN → CORS cerrado por defecto ---
case_q26() {
  if [ "$CON_ENV/$CON_BD" != "1/1" ]; then marca Q26 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q26] mutación: .env sin FRONTEND_ORIGIN + recomposición" >&2
  asegurar_healthy 420 || {
    marca Q26 BLOQUEADO "no hubo pila previa healthy (depende de la secuencia)"; return 0
  }
  env_backup
  mut_env_comentar "FRONTEND_ORIGIN"
  if ! recomponer 420; then
    restaurar_env
    marca Q26 FAIL "recomposición SIN FRONTEND_ORIGIN falló (debe recomponer green, S26/CD-7)"; return 0
  fi
  local e=""

  if ! req OPTIONS "$URL_LOGIN" "" \
      "Origin: http://example.org" \
      "Access-Control-Request-Method: POST"; then e="$e curl-preflight"; fi
  if printf '%s' "$CABECERAS" | tr -d '\r' | grep -qi '^access-control-'; then
    e="$e cero-configuraciones-registradas(S26-violado)"
  fi

  # GET /me con Origin (sin token → 401) tampoco debe listar cabeceras CORS
  req GET "$URL_ME" "" "Origin: http://example.org" || e="$e curl-me"
  if printf '%s' "$CABECERAS" | tr -d '\r' | grep -qi '^access-control-'; then
    e="$e me-con-cabeceras-CORS"
  fi

  # API same-origin (sin header Origin) sigue operando: login correcto → 200 (CD-7)
  if ! login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD" || [ "$CODIGO" != "200" ]; then
    e="$e login-same-origin-codigo-${CODIGO:-000}"
  fi

  restaurar_env
  [ -z "$e" ] && marca Q26 PASS || marca Q26 FAIL "$e"
  return 0
}

# --- Q27 (E2.22): esquema de users vs V1__init.sql + sin otras semillas (S24) ---
case_q27() {
  if [ "$CON_ENV/$CON_BD" != "1/1" ]; then marca Q27 BLOQUEADO "$bloqueo_env"; return 0; fi
  echo "[Q27] inspección del esquema/estado de BD" >&2
  asegurar_healthy 60 || { marca Q27 BLOQUEADO "pila no healthy"; return 0; }
  local e=""
  # helper: verificar SQL → valor esperado
  verificar() {
    local obtenido
    obtenido="$(psql_q "$1" 2>/dev/null || echo err)"
    [ "$obtenido" = "$2" ] || { e="$e $3=$obtenido(esperado-$2)"; }
  }

  verificar "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='id'" "bigint" "id-type"
  verificar "SELECT COALESCE(is_identity,'?') FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='id'" "YES" "id-identity"
  verificar "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='username'" "text" "username-type"
  verificar "SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='username'" "NO" "username-nullable"
  verificar "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='password_hash'" "text" "password_hash-type"
  verificar "SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='password_hash'" "NO" "password_hash-nullable"
  verificar "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='role'" "text" "role-type"
  verificar "SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='role'" "NO" "role-nullable"
  verificar "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='created_at'" "timestamp with time zone" "created_at-type"
  verificar "SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='created_at'" "NO" "created_at-nullable"
  verificar "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='updated_at'" "timestamp with time zone" "updated_at-type"
  verificar "SELECT is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name='updated_at'" "NO" "updated_at-nullable"
  # índice UNIQUE users_username_unique (constraint literal de V1__init.sql)
  verificar "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema='public' AND table_name='users' AND constraint_name='users_username_unique'" "1" "constraint-unique"

  # role DEFAULT 'ADMIN' del esquema (S21/E2.22) y valor ADMIN fijado en la fila
  local col
  col="$(psql_q "SELECT COALESCE(column_default,'') FROM information_schema.columns
                 WHERE table_schema='public' AND table_name='users' AND column_name='role'" 2>/dev/null || echo err)"
  printf '%s' "$col" | grep -qi 'ADMIN' || e="$e role-default-no-ADMIN=[$col]"
  verificar "SELECT role FROM users LIMIT 1" "ADMIN" "role-valor-fila"

  # sin otras semillas (S24): projects/logs (o sus equivalentes V1) VACÍAS
  local tabla fila_q
  for tabla in projects logs; do
    fila_q="$(psql_q "SELECT CASE WHEN to_regclass('public.${tabla}') IS NULL THEN 'sin-tabla'
                        WHEN (SELECT COUNT(*) FROM public.${tabla}) = 0 THEN 'vacia' ELSE 'con-filas' END" \
      2>/dev/null || echo err)"
    { [ "$fila_q" = "vacia" ] || [ "$fila_q" = "sin-tabla" ]; } || e="$e $tabla=$fila_q(S24-no-vacia)"
  done

  [ -z "$e" ] && marca Q27 PASS || marca Q27 FAIL "$e"
  return 0
}

# --- Q21 (E2.14 [D]): SHA-256 del validador de C1 vs captura pre-C2 ---
case_q21() {
  echo "[Q21] SHA-256 de StartupSecretsValidator.java + META-INF/spring.factories" >&2
  local f1="backend/src/main/java/com/projectlog/config/StartupSecretsValidator.java"
  local f2="backend/src/main/resources/META-INF/spring.factories"
  if [ ! -f "$f1" ] || [ ! -f "$f2" ]; then
    marca Q21 BLOQUEADO "ficheros del validador ausentes en las rutas del plan §6.5"; return 0
  fi
  local sha1 sha2
  sha1="$(sha256sum "$f1" | awk '{print $1}')"
  sha2="$(sha256sum "$f2" | awk '{print $1}')"
  echo "  captura registrada: ${f1##*/}=$sha1  ${f2##*/}=$sha2" >&2
  if [ -n "${QA_C1_VALIDATOR_SHA256:-}" ] && [ -n "${QA_C1_SPRING_FACTORIES_SHA256:-}" ]; then
    local e=""
    [ "$sha1" = "$QA_C1_VALIDATOR_SHA256" ] || e="$e hash-validador-distinto(S29-roto)"
    [ "$sha2" = "$QA_C1_SPRING_FACTORIES_SHA256" ] || e="$e hash-spring.factories-distinto"
    [ -z "$e" ] && marca Q21 PASS || marca Q21 FAIL "$e"
  else
    marca Q21 BLOQUEADO "sin hash de referencia en el plan (comparar contra la captura de C1)"
  fi
  return 0
}

# ----------------------------------------------------------------------------
# 4. Cierre: pila sana en F1 antes del veredicto (plan §1.3/§7)
# ----------------------------------------------------------------------------
pila_sana_cierre() {
  echo "[cierre] restaurando pila en F1" >&2
  if [ "$CON_ENV/$CON_BD" != "1/1" ]; then return 0; fi
  asegurar_healthy 420 || echo "[cierre] WARNING: la pila no llegó a healthy" >&2
}

# ----------------------------------------------------------------------------
# 5. Secuencia (plan §1.3) y salida
# ----------------------------------------------------------------------------
ejecutar() { # $1 ID  $2 función — la función registra su propio veredicto
  if ! "$2" 2>>"$TMPDIR_QA/errores_internos.log"; then
    RESULTADOS+=("$1: FAIL (error interno del script; ver qa_c2_smoke errores_internos)")
  fi
}

ejecutar Q13 case_q13
ejecutar Q8  case_q8
ejecutar Q9  case_q9
ejecutar Q10 case_q10
ejecutar Q11 case_q11
ejecutar Q12 case_q12
ejecutar Q14 case_q14
ejecutar Q17 case_q17
ejecutar Q15 case_q15
ejecutar Q16 case_q16
pila_sana_cierre
ejecutar Q18 case_q18
ejecutar Q19 case_q19
ejecutar Q20 case_q20
ejecutar Q22 case_q22
ejecutar Q23 case_q23
ejecutar Q24 case_q24
ejecutar Q25 case_q25
ejecutar Q26 case_q26
ejecutar Q27 case_q27
ejecutar Q21 case_q21
pila_sana_cierre   # Q26 restauró el .env pero la pila quedó sin la variable: recomponer en F1 (plan §1.3.3)

echo "=== VEREDICTOS QA C2 (por ID del plan) ==="
if [ "${#RESULTADOS[@]}" -gt 0 ]; then
  printf '%s\n' "${RESULTADOS[@]}"
fi

state_exit=0
if printf '%s\n' "${RESULTADOS[@]}" | grep -q ': FAIL'; then
  state_exit=1
elif printf '%s\n' "${RESULTADOS[@]}" | grep -q ': BLOQUEADO'; then
  state_exit=2
fi
exit "$state_exit"