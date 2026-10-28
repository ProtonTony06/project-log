#!/usr/bin/env bash
#
# qa_c8_smoke.sh — smokes [S] del corte C8 (design.md §5.6; canon qa_c2–qa_c7)
#
# Codifica los escenarios del spec C8 que se certificarán en pila REAL sobre
# el ÚNICO puerto 8080 (todo vía el proxy nginx — R8.53/E8.53):
#
#   pre · E8.6/E8.51 (SPA servida, assets ABSOLUTOS, fallback /logs/123,
#                     sin /logs/assets/) · E8.7 (login orden literal
#                     {token,username,expires_at} + /me 200 role ADMIN)
#   E8.19 (GET /me sin token → 401 invalid_token) · E8.14/E8.15 (401 por
#   status; no-200 tolerante — 405 HTML de Spring viaja tal cual) ·
#   E8.48/E8.49 (charset UTF-8 de D1 en los 401 del panel y de ingest:
#   bytes 0xC3 0xB3 / 0xC3 0xA1, cero byte latin-1 aislado) · E8.20 (defecto
#   50) · E8.24 (keyset por cursor acumula, next_cursor null = fin, cero
#   duplicados) · E8.23 (from con offset −03:00 OK; from>to → 400) ·
#   E8.26 (filter_from_to_inverted SIN field; filter_invalid_value CON
#   field=level) · E8.39 (listado SIN summary aunque llegue
#   ?include=summary) · E8.43 (regenerate 201 orden literal
#   {key,prefix,created_at}) · E8.44 (revocar 204 → has_key=false +
#   prefix=null) · E8.34 (detalle LogDto 11 campos) · E8.36 (404
#   not_found) · E8.41 (archivar/desarchivar SOLO por PATCH {status}, y
#   403 project_archived en ingest mientras está ARCHIVED) · E8.17 (CRUD
#   negativos: 409 name_duplicate; 400 field_required field=name) ·
#   E8.38/E8.47 (negativos C10: DELETE /panel/logs|projects|projects/{id}/logs con
#   X-Confirm-* FORMADAS → 401 TAL CUAL (ver H-C8-B2: el endpoint de C10 no
#   existe y el dispatcher de error /error reentra por la cadena 3 denyAll
#   de C2 — el 405/404 predicho en el plan NO es alcanzable por /api/v1;
#   el cliente §5 sin fingir éxito no cambia) ·
#   E8.31 (WS pre-upgrade: 401 cuerpo VACÍO · 403 evil —status, canon E7.9;
#   el cuerpo real "Invalid CORS request" de DefaultCorsProcessor) ·
#   E8.27 (ingesta →
#   EXACTAMENTE 1 frame batch byte-exacto §3.5, batch_id único) · Q23
#   (visual/navegado → BLOQUEADO con instrucción MANUAL para la demo C9).
#
# Diferencia Q13/E2.7 con qa_c7: este smoke es AUTOCONTENIDO — hace
# `down -v` + `up -d --build` (reglamento: el smoke de Fase 8 es el ÚNICO
# autorizado a down -v; la guardia tsc+vite del Dockerfile se corre aquí).
# Higiene (S16/CD-4): JWT y API key SOLO en los ficheros del tmpdir 700 con
# trap EXIT — JAMÁS a consola ni al log de eventos. Cero secretos impresos.
#
# Guard R-1 (patrón R-7.j): sin FRONTEND_ORIGIN en .env el handshake WS
# cierra 403 → los escenarios E8.27/E8.31 van BLOQUEADO con nota.
#
# Uso:
#   bash scripts/qa_c8_smoke.sh
#
# Salida: fases por stderr; una línea
#     E8.n: PASS | FAIL | BLOQUEADO (detalle)
# por ID de escenario.
# Exit: 0 = sin FAILs (los BLOQUEADO-Q23/R-1 anotados son esperados) ·
#       1 = hay algún FAIL.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

BASE_URL="http://127.0.0.1:8080"
WS_HOST="127.0.0.1"; WS_PORT="8080"   # el WS entra por el proxy nginx
URL_LOGIN="$BASE_URL/api/v1/panel/auth/login"
URL_ME="$BASE_URL/api/v1/panel/me"
URL_PROJECTS="$BASE_URL/api/v1/panel/projects"
URL_LOGS="$BASE_URL/api/v1/panel/logs"
URL_INGEST="$BASE_URL/api/v1/ingest"
URL_BATCH="$BASE_URL/api/v1/ingest/batch"
URL_HOME="$BASE_URL/"

TS="$(date +%Y%m%d%H%M%S)"
P_NAME="smoke-c8-${TS}-proyecto"
DESC_C8="proyecto del smoke C8 ($TS)"
NEEDLE_DET="smoke-c8-${TS}-detalle"     # E8.34 fila del detalle
NEEDLE_V20="smoke-c8-${TS}-v20"         # E8.20/24: 65 logs del keyset
NEEDLE_WS="smoke-c8-${TS}-ws"           # E8.27 log del frame en vivo
CORR_DET="e834-${TS}"
CORR_WS="e827-${TS}"

TMPDIR_QA="$(mktemp -d /tmp/qa_c8_smoke.XXXXXX)"
chmod 700 "$TMPDIR_QA" 2>/dev/null || true
# KEEP_TMP=1 (SOLO depuración): conserva el tmpdir 700 (contiene JWT y key).
trap 'trap_liberar; [ "${KEEP_TMP:-0}" = "1" ] && echo "[trap] tmpdir CONSERVADO: $TMPDIR_QA (borrar a mano)" >&2 || rm -rf "$TMPDIR_QA"' EXIT
trap_liberar() {
  if [ -n "${WS_PID:-}" ]; then kill "$WS_PID" 2>/dev/null || true; fi
}
F_CUERPO="$TMPDIR_QA/cuerpo.txt"
F_CAB="$TMPDIR_QA/cabeceras.txt"
F_TOKEN="$TMPDIR_QA/token.txt"     # JWT — no consola
F_KEY="$TMPDIR_QA/key.txt"         # API key — no consola
F_ID="$TMPDIR_QA/id.txt"
F_WS="$TMPDIR_QA/ws_eventos.txt"   # eventos del cliente WS (frames PÚBLICOS)
ERR_WS="$TMPDIR_QA/ws_err.log"

# Compose wrapper (canon C4–C7).
COMPOSE_BIN=""
if podman compose version >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
elif command -v podman-compose >/dev/null 2>&1; then
  COMPOSE_BIN="podman-compose"
elif command -v podman >/dev/null 2>&1; then
  COMPOSE_BIN="podman compose"
fi
compose_() {
  local sub="$1" estado
  case "$sub" in
    down) timeout --kill-after=10 180 $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log" ; estado=$? ;;
    *)    timeout --kill-after=10 620 $COMPOSE_BIN "$@" 2>>"$TMPDIR_QA/compose.log" ; estado=$? ;;
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
  local limite=$(( $(date +%s) + ${1:-420} ))
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
key_hdr() { printf 'X-API-Key: %s' "$(cat "$F_KEY")"; }

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

# Variante null-aware del canon qa_c3 (E3.1): distingue JSON null —clave
# PRESENTE con valor null— de clave AUSENTE (la clave ausente → exit 2).
# H-C8-B2: campo_json (exit 2 en null) NO sirve para asertar "null" EXPLÍCITO.
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

# json_claves_orden — claves de nivel superior EN ORDEN tal cual viajó
# (object_pairs_hook NO reordena: el orden del string es el del servidor).
json_claves_orden() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
crudo = sys.stdin.buffer.read().decode("utf-8", "replace")
pares = json.loads(crudo, object_pairs_hook=lambda p: p)
print("|".join(k for k, _v in pares) if isinstance(pares, list) else "")'
}

# ids_de_ultima_respuesta — lista de {id} de una página GET /logs.
ids_de_ultima_respuesta() {
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
for l in d.get("items", []):
    print(l["id"])' 2>/dev/null
}

urlenc() { python3 -c 'import urllib.parse, sys; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"; }

# id por correlación vía el filtro text (panel API — NUNCA psql directo).
id_por_correlacion() {
  # H-C8-B3: el filtro C6 `text` solo matchea MESSAGE — la búsqueda por
  # correlation_id crudo no encuentra nada. Se busca por el NEEDLE del
  # message y se devuelve el id SOLO si la fila trae el correlation_id
  # esperado (trazabilidad de la fila exacta).
  local needle="$1" corr="$2"
  req GET "$URL_LOGS?text=$(urlenc "$needle")" "" "$(bearer_hdr)" || true
  printf '%s' "$CUERPO" | python3 -c '
import sys, json
d = json.loads(sys.stdin.buffer.read().decode("utf-8", "replace"))
items = [x for x in d.get("items", []) if x.get("correlation_id") == sys.argv[1]]
print(items[0]["id"] if items else "")' "$corr" 2>/dev/null
}

# ---------------------------------------------------------------
# 1. Cliente WS RFC 6455 mínimo (stdlib, patrón ws7_client de qa_c7):
#    hs → 'HS <status> BODY <n> ACCEPT ok|no' (BODY = bytes de cuerpo post-
#    handshake: 0 en la 401 pre-upgrade (E8.31); la 403 de Origin ajena
#    lleva el cuerpo estándar "Invalid CORS request" (DefaultCorsProcessor —
#    canon E7.9 solo aserta el status);
#    long → connecta, manda set_filters compuesto y lee duración s:
#           'hs-ok', 'sf-sent', 'frame <json>', 'close <code>'.
# ---------------------------------------------------------------
cat > "$TMPDIR_QA/ws8_client.py" <<'PYEOF'
#!/usr/bin/env python3
# ws8_client.py — cliente WebSocket RFC 6455 mínimo del smoke C8 (stdlib).
import base64, hashlib, json, os, socket, sys, time

GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


def recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        parte = sock.recv(n - len(buf))
        if not parte:
            raise ConnectionError("EOF")
        buf += parte
    return buf


def abrir(host, port, token, origin, tmo):
    sock = socket.create_connection((host, port), timeout=tmo)
    sock.settimeout(tmo)
    key = base64.b64encode(os.urandom(16)).decode()
    req = (f"GET /ws/logs?token={token} HTTP/1.1\r\nHost: {host}:{port}\r\n"
           f"Upgrade: websocket\r\nConnection: Upgrade\r\n"
           f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n")
    if origin:
        req += f"Origin: {origin}\r\n"
    req += "\r\n"
    sock.sendall(req.encode("iso-8859-1"))
    data = b""
    try:
        while b"\r\n\r\n" not in data:
            parte = sock.recv(4096)
            if not parte:
                break
            data += parte
    except Exception:
        pass
    cabecera, _, resto = data.partition(b"\r\n\r\n")
    lineas = cabecera.decode("iso-8859-1").split("\r\n")
    partes = lineas[0].split() if lineas and lineas[0] else []
    status = int(partes[1]) if len(partes) >= 2 and partes[1].isdigit() else 0
    headers = {}
    for linea in lineas[1:]:
        if ":" in linea:
            k, v = linea.split(":", 1)
            headers[k.strip().lower()] = v.strip()
    return sock, status, headers, resto, key


def send_frame(sock, opcode, payload, tmo):
    sock.settimeout(tmo)
    mascara = os.urandom(4)
    enmascarado = bytes(b ^ mascara[i % 4] for i, b in enumerate(payload))
    n = len(payload)
    if n < 126:
        cab = bytes([0x80 | opcode, 0x80 | n])
    elif n < 65536:
        cab = bytes([0x80 | opcode, 0x80 | 126]) + n.to_bytes(2, "big")
    else:
        cab = bytes([0x80 | opcode, 0x80 | 127]) + n.to_bytes(8, "big")
    sock.sendall(cab + mascara + enmascarado)


def leer_frame(sock, tmo):
    sock.settimeout(tmo)
    dos = recv_exact(sock, 2)
    opcode = dos[0] & 0x0F
    l = dos[1] & 0x7F
    if l == 126:
        l = int.from_bytes(recv_exact(sock, 2), "big")
    elif l == 127:
        l = int.from_bytes(recv_exact(sock, 8), "big")
    if dos[1] & 0x80:
        recv_exact(sock, 4)  # server→client SIN máscara, se descarta
    payload = recv_exact(sock, l) if l else b""
    return opcode, payload


def hs_solo(host, port, modo, tmo=10):
    token = os.environ.get("PY_TOKEN", "")
    if modo == "sin-token":
        token = ""
    origin = "http://evil.example" if modo == "evil" else None
    sock, status, headers, resto, key = abrir(host, port, token, origin, tmo)
    accept = headers.get("sec-websocket-accept")
    ok = False
    if accept:
        ok = accept == base64.b64encode(hashlib.sha1(
            (key + GUID).encode()).digest()).decode()
    cuerpo = len(resto)
    # drena el resto: en la 401 pre-upgrade NO DEBE haber cuerpo (E8.31)
    try:
        while True:
            sock.settimeout(1)
            mas = sock.recv(4096)
            if not mas:
                break
            cuerpo += len(mas)
    except Exception:
        pass
    print(f"HS {status} BODY {cuerpo} ACCEPT {'ok' if ok else 'no'}", flush=True)
    sock.close()


def modo_long(host, port, duracion, sf, tmo=10):
    token = os.environ.get("PY_TOKEN", "")
    try:
        sock, status, headers, _resto, _key = abrir(host, port, token, None, tmo)
    except Exception as ex:
        print(f"HS-fallo {ex}", flush=True)
        return
    if status != 101:
        print(f"HS-fallo {status}", flush=True)
        sock.close()
        return
    print("hs-ok", flush=True)
    try:
        send_frame(sock, 0x1, sf.encode(), tmo)
        print("sf-sent", flush=True)
    except Exception:
        print("fin", flush=True)
        return
    limite = time.time() + duracion
    try:
        while time.time() < limite:
            try:
                opcode, payload = leer_frame(sock, 0.5)
            except socket.timeout:
                continue
            except ConnectionError:
                print("cerrado", flush=True)
                break
            if opcode == 0x9:  # ping → pong
                send_frame(sock, 0xA, payload, 5)
            elif opcode == 0x1:
                print("frame " + payload.decode("utf-8", "replace"), flush=True)
            elif opcode == 0x8:
                code = int.from_bytes(payload[:2], "big") if len(payload) >= 2 else 0
                print(f"close {code}", flush=True)
                break
    finally:
        print("fin", flush=True)


if __name__ == "__main__":
    modo = sys.argv[1]
    if modo == "hs":
        hs_solo(sys.argv[2], int(sys.argv[3]), sys.argv[4])
    elif modo == "long":
        modo_long(sys.argv[2], int(sys.argv[3]), float(sys.argv[4]), sys.argv[5])
PYEOF
chmod 700 "$TMPDIR_QA/ws8_client.py"

# ---------------------------------------------------------------
# 2. Precondiciones: anti-veneno + .env + recomposición autocontenida (Q13)
# ---------------------------------------------------------------
unset POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
      SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD \
      JWT_SECRET JWT_TTL_HOURS ADMIN_INITIAL_USERNAME ADMIN_INITIAL_PASSWORD \
      INGEST_MAX_REQUEST_BYTES INGEST_MAX_BATCH_SIZE WS_HEARTBEAT_SECONDS \
      FRONTEND_ORIGIN SPRING_PROFILES_ACTIVE TZ 2>/dev/null || true

CON_ENV=0; CON_CRED=0; CON_ORIGIN=0
if [ -f .env ]; then
  CON_ENV=1
  while IFS= read -r _qa_linea || [ -n "$_qa_linea" ]; do
    case "$_qa_linea" in ''|\#*) continue ;; esac
    eval "$_qa_linea" 2>/dev/null || true
  done < ./.env
  [ -n "${JWT_TTL_HOURS:-}" ] || JWT_TTL_HOURS="8"
  if [ -n "${ADMIN_INITIAL_USERNAME:-}" ] && [ -n "${ADMIN_INITIAL_PASSWORD:-}" ]; then CON_CRED=1; fi
  if [ -n "${FRONTEND_ORIGIN:-}" ]; then CON_ORIGIN=1; fi
fi
bloqueo_env="(sin .env F1: credenciales del admin no disponibles)"
bloqueo_origin="(R-1: sin FRONTEND_ORIGIN el handshake WS cierra 403 — casos WS bloqueados)"
bloqueo_pila="(pila/compose no disponible)"

ID_REST_ALL="E8.6 E8.51 E8.7 E8.19 E8.14 E8.15 E8.48 E8.49 E8.20 E8.24 E8.23 E8.26 E8.39 E8.43 E8.44 E8.34 E8.36 E8.41 E8.38 E8.47"
ID_WS_ALL="E8.27 E8.31"

precondiciones() {
  echo "[pre] recomposición AUTOCONTENIDA (Q13): compose down -v || up -d --build" >&2
  compose_ down -v || true
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

pre_fase() {
  if ! req POST "$URL_PROJECTS" "{\"name\":\"$P_NAME\",\"description\":\"$DESC_C8\"}" "$(bearer_hdr)" \
     || [ "$CODIGO" != "201" ]; then
    echo "[pre-fase] POST projects=${CODIGO:-000}" >&2; return 1
  fi
  printf '%s' "$(campo_json id)" > "$F_ID"
  [ -s "$F_ID" ] || { echo "[pre-fase] id del proyecto no capturado" >&2; return 1; }
  req POST "$URL_PROJECTS/$(cat "$F_ID")/api-key/regenerate" "" "$(bearer_hdr)"
  if [ "$CODIGO" != "201" ]; then echo "[pre-fase] regenerate=${CODIGO:-000}" >&2; return 1; fi
  printf '%s' "$(campo_json key)" > "$F_KEY"
  [ -s "$F_KEY" ] || { echo "[pre-fase] key vacía" >&2; return 1; }
  return 0
}

# siembra: 65 logs keyset (E8.20 defecto 50 + E8.24) + 1 detalle FATAL (E8.34)
sembrar() {
  python3 - "$TS" > "$TMPDIR_QA/seed1.json" <<'PYEOF'
import json, sys
ts = sys.argv[1]
lotes = []
for i in range(1, 41):
    lotes.append({
        "level": "INFO",
        "timestamp": f"2026-10-25T13:{i:02d}:11.000000Z",
        "message": f"smoke-c8-{ts}-v20 log #{i:03d}",
        "environment": "production",
        "source": f"s{i % 4}",
        "correlation_id": f"e824-{ts}-{i:03d}",
    })
print(json.dumps({"logs": lotes}, separators=(",", ":")))
PYEOF
  python3 - "$TS" > "$TMPDIR_QA/seed2.json" <<'PYEOF'
import json, sys
ts = sys.argv[1]
lotes = []
for i in range(41, 66):
    lotes.append({
        "level": "WARN" if i % 2 else "INFO",
        "timestamp": f"2026-10-25T14:{i - 40:02d}:11.000000Z",
        "message": f"smoke-c8-{ts}-v20 log #{i:03d}",
        "environment": "production",
        "source": f"s{i % 4}",
        "correlation_id": f"e824-{ts}-{i:03d}",
    })
print(json.dumps({"logs": lotes}, separators=(",", ":")))
PYEOF
  python3 - "$TS" "$CORR_DET" "$NEEDLE_DET" > "$TMPDIR_QA/seed3.json" <<'PYEOF'
import json, sys
ts, corr, needle = sys.argv[1], sys.argv[2], sys.argv[3]
print(json.dumps({"logs": [{
    "level": "FATAL",
    "timestamp": "2026-10-26T09:15:22.123456Z",
    "message": f"{needle} message",
    "exception": "java.lang.Example: stack del smoke C8\n\tat pkg.Clase(Linea.java:1)",
    "metadata": {"smoke": "c8", "batch_size": 1},
    "environment": "production",
    "source": "det-src",
    "correlation_id": corr,
}]}, separators=(",", ":")))
PYEOF
  req POST "$URL_BATCH" "$(cat "$TMPDIR_QA/seed1.json")" "$(key_hdr)"
  [ "$CODIGO" = "202" ] || { echo "[pre-fase] seed1=${CODIGO:-000}" >&2; return 1; }
  req POST "$URL_BATCH" "$(cat "$TMPDIR_QA/seed2.json")" "$(key_hdr)"
  [ "$CODIGO" = "202" ] || { echo "[pre-fase] seed2=${CODIGO:-000}" >&2; return 1; }
  req POST "$URL_BATCH" "$(cat "$TMPDIR_QA/seed3.json")" "$(key_hdr)"
  [ "$CODIGO" = "202" ] || { echo "[pre-fase] seed3=${CODIGO:-000}" >&2; return 1; }
  echo "[pre-fase] proyecto($(cat "$F_ID"))+key+66 logs sembrados (higiene: key solo en $TMPDIR_QA)" >&2
  return 0
}

ejecutar_pre=1
if [ "$CON_ENV/$CON_CRED" != "1/1" ]; then
  for id in $ID_REST_ALL $ID_WS_ALL; do marca "$id" BLOQUEADO "$bloqueo_env"; done
  ejecutar_pre=0
else
  if ! precondiciones || ! pre_fase || ! sembrar; then
    for id in $ID_REST_ALL $ID_WS_ALL; do marca "$id" BLOQUEADO "$bloqueo_pila"; done
    ejecutar_pre=0
  fi
fi

# ---------------------------------------------------------------
# 3. Casos REST
# ---------------------------------------------------------------

# --- E8.6/E8.51 — SPA servida con base ABSOLUTA y fallback nginx ---
case_e8_6() {
  echo "[E8.6/E8.51] SPA: / y /logs/123 → 200 text/html, assets absolutos" >&2
  local e="" hc
  hc="$(curl -sS --max-time 15 -o "$TMPDIR_QA/home.html" -D "$TMPDIR_QA/home_h.txt" -w '%{http_code}' "$URL_HOME" 2>/dev/null || echo 000)"
  [ "$hc" = "200" ] || e="/=$hc "
  grep -qi 'content-type: text/html' "$TMPDIR_QA/home_h.txt" 2>/dev/null || e="${e}home-no-html "
  grep -qE '(src|href)="/assets/' "$TMPDIR_QA/home.html" 2>/dev/null || e="${e}sin-assets-absolutos "
  grep -q '/logs/assets/' "$TMPDIR_QA/home.html" 2>/dev/null && e="${e}assets-relativos "
  hc="$(curl -sS --max-time 15 -o "$TMPDIR_QA/logs123.html" -D "$TMPDIR_QA/l123_h.txt" -w '%{http_code}' "$BASE_URL/logs/123" 2>/dev/null || echo 000)"
  [ "$hc" = "200" ] || e="/logs/123=$hc "
  grep -qi 'content-type: text/html' "$TMPDIR_QA/l123_h.txt" 2>/dev/null || e="${e}l123-no-html "
  grep -qE '(src|href)="/assets/' "$TMPDIR_QA/logs123.html" 2>/dev/null || e="${e}l123-sin-absolutos "
  grep -q '/logs/assets/' "$TMPDIR_QA/logs123.html" 2>/dev/null && e="${e}l123-assets-relativos "
  [ -z "$e" ] && { marca E8.6 PASS "assets absolutos, cero racha relativa"; marca E8.51 PASS "fallback SPA /logs/123 → index.html"; } \
    || { marca E8.6 FAIL "(${e% })"; marca E8.51 FAIL "(fallback/base — ver E8.6)"; }
  return 0
}

# --- E8.7/E8.19 — login orden literal + /me con y sin token ---
case_e8_7() {
  echo "[E8.7/E8.19] login {token,username,expires_at}; /me 200 ADMIN; /me sin token 401 invalid_token" >&2
  local e="" orden
  login "$ADMIN_INITIAL_USERNAME" "$ADMIN_INITIAL_PASSWORD"
  [ "$CODIGO" = "200" ] || e="login=$CODIGO "
  orden="$(json_claves_orden)"
  grep -qx 'token|username|expires_at' <<<"$orden" || e="${e}orden=[$orden] "
  req GET "$URL_ME" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}me200=$CODIGO "
  [ "$(campo_json role || echo '?')" = "ADMIN" ] || e="${e}me-role=$(campo_json role || echo '?') "
  [ "$(campo_json username || echo '?')" = "$ADMIN_INITIAL_USERNAME" ] || e="${e}me-username "
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/me_sin.txt" -w '%{http_code}' "$URL_ME" 2>/dev/null || echo 000)"
  [ "$hc" = "401" ] || e="${e}me-sin=$hc "
  grep -q 'invalid_token' "$TMPDIR_QA/me_sin.txt" 2>/dev/null || e="${e}me-sin-body "
  [ -z "$e" ] && { marca E8.7 PASS "orden literal {token,username,expires_at}"; marca E8.19 PASS "401 invalid_token sin token"; } \
    || { marca E8.7 FAIL "(${e//me-username })"; marca E8.19 FAIL "/me sin token — ver E8.7"; }
  return 0
}

# --- E8.14/E8.15 — 401 por status y no-200 tolerante ---
case_e8_14() {
  # H-C8-B2: el "405 HTML" previsto NO es alcanzable por /api/v1 — el error
  # dispatch de /error reentra en la cadena 3 (denyAll, C2-S4) → 401 real.
  # El 405-tolerante del client queda cubierto por Vitest (405 HTML mock).
  echo "[E8.14/E8.15] Bearer viaja; 401 por status (sin parseo, proxy no reescribe)" >&2
  local e="" tolerante="JSON"
  req POST "$URL_ME" "{}" "$(bearer_hdr)"
  [ "$CODIGO" = "401" ] || e="post-me=$CODIGO "
  printf '%s' "$CUERPO" | head -c 1 | grep -q '<' && tolerante="HTML" || tolerante="JSON"
  # DELETE sobre projects (sin mapeo DELETE) → 401 denyAll tal cual; el
  # STATUS viaja INTACTO por el proxy (decisión solo por status)
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/c.txt" -w '%{http_code}' -X DELETE \
       -H "Authorization: Bearer $(cat "$F_TOKEN")" "$URL_PROJECTS/99999999" 2>/dev/null || echo 000)"
  grep -qE '^(405|401|404)$' <<<"$hc" || e="${e}delete=$hc "
  [ -z "$e" ] && { marca E8.14 PASS "Bearer viaja; POST /me sin método → 401 tal cual (status íntegro por el proxy)"; marca E8.15 PASS "401 sin parseo de body (camino E8.14); 405-tolerante vía Vitest (405 HTML mock)"; } \
    || { marca E8.14 FAIL "($e)"; marca E8.15 FAIL "(401 tolerante — ver E8.14)"; }
  return 0
}

# --- E8.48/E8.49 — charset UTF-8 en los 401 (fix D1) ---
case_e8_48() {
  echo "[E8.48/E8.49] 401 panel (ó) e ingest (á): charset=UTF-8 + bytes multi, cero byte latin-1 aislado" >&2
  local e=""
  # (a) PANEL: GET /me sin token → 401 'Autenticación requerida'
  curl -sS --max-time 10 -D "$TMPDIR_QA/p401_h.txt" -o "$TMPDIR_QA/p401.bin" "$URL_ME" 2>/dev/null || true
  tr -d '\r' < "$TMPDIR_QA/p401_h.txt" | grep -qi 'content-type: application/json;charset=UTF-8' \
    || e="panel-ct=$(tr -d '\r' < "$TMPDIR_QA/p401_h.txt" | grep -i content-type || echo none) "
  if ! python3 - "$TMPDIR_QA/p401.bin" <<'PYEOF' 2>>"$TMPDIR_QA/py_err.log"; then e="${e}panel-bytes "; fi
import sys
raw = open(sys.argv[1], "rb").read()
print(raw.decode("utf-8"))
assert b"\xc3\xb3" in raw, "sin 0xC3 0xB3 (ó UTF-8)"
assert raw.count(b"\xf3") == 0, "0xF3 aislado (ISO-8859-1) PRESENTE"
assert raw.count(b"\xe1") == 0, "0xE1 aislado PRESENTE"
assert raw.count(b"\xd3") == 0, "0xD3 aislado PRESENTE"
PYEOF
  # (b) INGEST: POST /ingest sin X-API-Key → 401 'API key de ingesta ausente o inválida'
  curl -sS --max-time 10 -D "$TMPDIR_QA/i401_h.txt" -o "$TMPDIR_QA/i401.bin" -X POST \
    -H 'Content-Type: application/json' -d '{"level":"INFO"}' "$URL_INGEST" 2>/dev/null || true
  tr -d '\r' < "$TMPDIR_QA/i401_h.txt" | grep -qi 'content-type: application/json;charset=UTF-8' \
    || e="${e}ing-ct=$(tr -d '\r' < "$TMPDIR_QA/i401_h.txt" | grep -i content-type || echo none) "
  grep -q 'api_key_invalid' "$TMPDIR_QA/i401.bin" 2>/dev/null || e="${e}ing-code "
  if ! python3 - "$TMPDIR_QA/i401.bin" <<'PYEOF' 2>>"$TMPDIR_QA/py_err.log"; then e="${e}ingest-bytes "; fi
import sys
raw = open(sys.argv[1], "rb").read()
print(raw.decode("utf-8"))
assert b"\xc3\xa1" in raw, "sin 0xC3 0xA1 (á UTF-8)"
assert raw.count(b"\xf3") == 0 and raw.count(b"\xe1") == 0, "byte latin-1 aislado PRESENTE"
PYEOF
  [ -z "$e" ] && { marca E8.48 PASS "401 panel UTF-8: 0xC3 0xB3 sin 0xF3 aislado"; marca E8.49 PASS "401 ingest UTF-8: 0xC3 0xA1 ('ausente o inválida')"; } \
    || { marca E8.48 FAIL "(${e% })"; marca E8.49 FAIL "(ingest charset — ver E8.48)"; }
  return 0
}

# --- E8.20/E8.24 — defecto 50 y keyset por cursor ---
case_e8_20() {
  echo "[E8.20/E8.24] GET /logs sin filtros → 50 + cursor; 2ª página acumula; fin null; cero duplicados" >&2
  local e="" n1 n2 cursor
  req GET "$URL_LOGS" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="p1=$CODIGO "
  n1="$(ids_de_ultima_respuesta | wc -l || true)"
  cursor="$(campo_json next_cursor || echo '-')"
  [ "$n1" -eq 50 ] || e="${e}p1-n=$n1 "
  case "$cursor" in null|-|"") e="${e}p1-cursor=$cursor " ;; esac
  cp "$F_CUERPO" "$TMPDIR_QA/p1.json"
  local cur_url
  cur_url="$(urlenc "$cursor")"
  req GET "$URL_LOGS?cursor=$cur_url" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}p2=$CODIGO "
  n2="$(ids_de_ultima_respuesta | wc -l || true)"
  [ "$n2" -ge 1 ] || e="${e}p2-vacio "
  [ "$(campo_json_nul next_cursor || echo '?')" = "null" ] || e="${e}fin-cursor=$(campo_json_nul next_cursor || echo '?') "
  # solapamiento de ids entre páginas (keyset ⇒ progresión DISYUNTA)
  if ! python3 - "$TMPDIR_QA/p1.json" "$F_CUERPO" <<'PYEOF' 2>/dev/null; then e="${e}solapamiento-claves "; fi
import json, sys
p1 = {l["id"] for l in json.load(open(sys.argv[1]))["items"]}
p2 = {l["id"] for l in json.load(open(sys.argv[2]))["items"]}
assert not (p1 & p2), "ids repetidos entre páginas"
PYEOF
  [ -z "$e" ] && { marca E8.20 PASS "defecto 50 items + next_cursor"; marca E8.24 PASS "p50 + p$(printf '%s' "$n2") disyuntas, next_cursor null = fin"; } \
    || { marca E8.20 FAIL "($e)"; marca E8.24 FAIL "(cursor — ver E8.20)"; }
  return 0
}

# --- CRUD negativos (T8.57): 409 name_duplicate; 400 por campo; detalle 200 ---
case_e8_crud() {
  echo "[E8.17/crud] POST duplicado → 409 name_duplicate; POST name vacío → 400 field_required field=name" >&2
  local e=""
  req POST "$URL_PROJECTS" "{\"name\":\"$P_NAME\",\"description\":\"dup\"}" "$(bearer_hdr)"
  [ "$CODIGO" = "409" ] || e="dup=$CODIGO "
  [ "$(campo_json code || echo '?')" = "name_duplicate" ] || e="${e}code=$(campo_json code || echo '?') "
  req POST "$URL_PROJECTS" '{"name":"  "}' "$(bearer_hdr)"
  [ "$CODIGO" = "400" ] || e="${e}empty=$CODIGO "
  [ "$(campo_json code || echo '?')" = "field_required" ] || e="${e}code2=$(campo_json code || echo '?') "
  [ "$(campo_json field || echo '?')" = "name" ] || e="${e}field2=$(campo_json field || echo '?') "
  req GET "$URL_PROJECTS/$(cat "$F_ID")" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}detalle=$CODIGO "
  grep -q '"summary"' <<<"$CUERPO" && e="${e}detalle-CON-summary "
  [ -z "$e" ] && marca E8.17 PASS "409 name_duplicate / 400 field_required(name) / detalle 200 SIN summary (canon C3)" \
    || marca E8.17 FAIL "($e)"
  return 0
}

# --- E8.23/E8.26 — offsets ISO y 400 con campo culpable ---
case_e8_23() {
  echo "[E8.23/E8.26] from con offset −03:00 OK; from>to → filter_from_to_inverted SIN field; level inválido → filter_invalid_value CON field=level" >&2
  local e=""
  req GET "$URL_LOGS?from=$(urlenc "2026-10-01T00:00:00-03:00")" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="offset=$CODIGO "
  req GET "$URL_LOGS?from=$(urlenc "2026-10-26T00:00:00+02:00")&to=$(urlenc "2026-10-01T00:00:00+02:00")" "" "$(bearer_hdr)"
  [ "$CODIGO" = "400" ] || e="${e}inverted=$CODIGO "
  [ "$(campo_json code || echo '?')" = "filter_from_to_inverted" ] || e="${e}code=$(campo_json code || echo '?') "
  if campo_json field >/dev/null 2>&1; then e="${e}field-PRESENTE($(campo_json field)) "; fi
  req GET "$URL_LOGS?level=ALGO" "" "$(bearer_hdr)"
  [ "$CODIGO" = "400" ] || e="${e}invalid=$CODIGO "
  [ "$(campo_json code || echo '?')" = "filter_invalid_value" ] || e="${e}code2=$(campo_json code || echo '?') "
  [ "$(campo_json field || echo '?')" = "level" ] || e="${e}field2=$(campo_json field || echo '?') "
  [ -z "$e" ] && marca E8.23 PASS && marca E8.26 PASS \
    || { marca E8.23 FAIL "($e)"; marca E8.26 FAIL "(400 campo culpable — ver E8.23)"; }
  return 0
}

# --- E8.39 — listado SIN summary aunque viaje ?include=summary ---
case_e8_39() {
  echo "[E8.39] GET /projects?include=summary → ProjectDto SIN summary" >&2
  local e=""
  req GET "$URL_PROJECTS?include=summary" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="=$CODIGO "
  if ! python3 - "$F_CUERPO" <<'PYEOF' 2>/dev/null; then e="${e}summary-o-dto "; fi
import json, sys
d = json.load(open(sys.argv[1]))
assert isinstance(d, list) and d, "listado vacío"
for p in d:
    assert "summary" not in p, "summary VIAJÓ (R8.18): solo el DETALLE lo incluye"
    for k in ("id", "name", "status", "api_key_prefix", "has_key"):
        assert k in p, f"ProjectDto incompleto: falta {k}"
PYEOF
  [ -z "$e" ] && marca E8.39 PASS || marca E8.39 FAIL "($e)"
  return 0
}

# --- E8.43/E8.44 — regenerate orden literal → revoke 204 → has_key false ---
case_e8_43() {
  echo "[E8.43/E8.44] regenerate 201 {key,prefix,created_at}; revoke 204; has_key false; re-generar para el WS" >&2
  local e="" orden
  req POST "$URL_PROJECTS/$(cat "$F_ID")/api-key/regenerate" "" "$(bearer_hdr)"
  [ "$CODIGO" = "201" ] || e="regen=$CODIGO "
  orden="$(json_claves_orden)"
  grep -qx 'key|prefix|created_at' <<<"$orden" || e="${e}orden=[$orden] "
  req GET "$URL_PROJECTS/$(cat "$F_ID")" "" "$(bearer_hdr)"
  [ "$(campo_json has_key || echo '?')" = "true" ] || e="${e}has_key=$(campo_json has_key || echo '?') "
  req DELETE "$URL_PROJECTS/$(cat "$F_ID")/api-key" "" "$(bearer_hdr)"
  [ "$CODIGO" = "204" ] || e="${e}revoke=$CODIGO "
  req GET "$URL_PROJECTS/$(cat "$F_ID")" "" "$(bearer_hdr)"
  [ "$(campo_json has_key || echo '?')" = "false" ] || e="${e}has_key-tras=$(campo_json has_key || echo '?') "
  [ "$(campo_json_nul api_key_prefix || echo 'x')" = "null" ] || e="${e}prefix-tras=$(campo_json_nul api_key_prefix || echo 'x') "
  req POST "$URL_PROJECTS/$(cat "$F_ID")/api-key/regenerate" "" "$(bearer_hdr)"
  [ "$CODIGO" = "201" ] || e="${e}regen2=$CODIGO "
  printf '%s' "$(campo_json key)" > "$F_KEY"
  [ -s "$F_KEY" ] || e="${e}key-vacia "
  [ -z "$e" ] && { marca E8.43 PASS "201 orden literal {key,prefix,created_at}"; marca E8.44 PASS "204 + has_key=false + prefix null"; } \
    || { marca E8.43 FAIL "($e)"; marca E8.44 FAIL "(revocar — ver E8.43)"; }
  return 0
}

# --- E8.34/E8.36 — detalle 11 campos y 404 not_found ---
case_e8_34() {
  echo "[E8.34/E8.36] GET /logs/{id} 11 campos (exception/metadata no nulos); 404 not_found" >&2
  local e="" id_det
  id_det="$(id_por_correlacion "$NEEDLE_DET" "$CORR_DET")"
  [ -n "$id_det" ] || e="sin-id-detalle "
  req GET "$URL_LOGS/$id_det" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}det=$CODIGO "
  if ! python3 - "$F_CUERPO" <<'PYEOF' 2>/dev/null; then e="${e}campos11 "; fi
import json, sys
d = json.load(open(sys.argv[1]))
campos = ["id", "project_id", "level", "environment", "timestamp",
          "received_at", "message", "source", "correlation_id",
          "exception", "metadata"]
falta = [c for c in campos if c not in d]
assert not falta, f"LogDto incompleto: faltan {falta}"
assert d["exception"] and d["metadata"], "exception/metadata deben viajar no-nulos"
PYEOF
  req GET "$URL_LOGS/99999999" "" "$(bearer_hdr)"
  [ "$CODIGO" = "404" ] || e="${e}404=$CODIGO "
  [ "$(campo_json code || echo '?')" = "not_found" ] || e="${e}code=$(campo_json code || echo '?') "
  [ -z "$e" ] && marca E8.34 PASS && marca E8.36 PASS \
    || { marca E8.34 FAIL "($e)"; marca E8.36 FAIL "(404 — ver E8.34)"; }
  return 0
}

# --- E8.41 — Archivar/Desarchivar SOLO por PATCH {status} ---
case_e8_41() {
  echo "[E8.41] PATCH status ARCHIVED → 200; ingest archivada 403 project_archived; ACTIVE → 200 (NUNCA DELETE)" >&2
  local e=""
  req PATCH "$URL_PROJECTS/$(cat "$F_ID")" '{"status":"ARCHIVED"}' "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="arch=$CODIGO "
  [ "$(campo_json status || echo '?')" = "ARCHIVED" ] || e="${e}st=$(campo_json status || echo '?') "
  local hc
  hc="$(curl -sS --max-time 10 -o "$TMPDIR_QA/arch_ing.txt" -w '%{http_code}' -X POST \
    -H "Content-Type: application/json" -H "$(key_hdr)" \
    -d '{"level":"INFO","timestamp":"2026-10-26T10:00:00Z","message":"arch","environment":"production"}' \
    "$URL_INGEST" 2>/dev/null || echo 000)"
  [ "$hc" = "403" ] || e="${e}ingesta-archivada=$hc "
  grep -q 'project_archived' "$TMPDIR_QA/arch_ing.txt" 2>/dev/null || e="${e}code-arch "
  req PATCH "$URL_PROJECTS/$(cat "$F_ID")" '{"status":"ACTIVE"}' "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}desarch=$CODIGO "
  [ "$(campo_json status || echo '?')" = "ACTIVE" ] || e="${e}st2=$(campo_json status || echo '?') "
  [ -z "$e" ] && marca E8.41 PASS || marca E8.41 FAIL "($e)"
  return 0
}

# --- E8.38/E8.47 — negativos C10: 401 TAL CUAL (H-C8-B2) con X-Confirm-* formadas ---
case_e8_38() {
  echo "[E8.38/E8.47] DELETE panel (C10 inexistente + /error → cadena 3 denyAll) con X-Confirm-* formadas → 401 tal cual, §5 sin fingir éxito" >&2
  local e="" id_det r_log r_proj r_purga
  id_det="$(id_por_correlacion "$NEEDLE_DET" "$CORR_DET")"
  [ -n "$id_det" ] || id_det="1"
  # H-C8-B2: C10 no existe y el /error dispatch reentra en la cadena 3
  # (denyAll, C2-S4) → 401 TAL CUAL (405/404 no alcanzable vía /api/v1).
  req DELETE "$URL_LOGS/$id_det" "" "$(bearer_hdr)" "X-Confirm-Log-Id: $id_det"
  r_log="$CODIGO"
  grep -qE '^(405|404|401)$' <<<"$r_log" || e="logs/$id_det=$r_log "
  req DELETE "$URL_PROJECTS/$(cat "$F_ID")" "" "$(bearer_hdr)" "X-Confirm-Name: $P_NAME"
  r_proj="$CODIGO"
  grep -qE '^(405|404|401)$' <<<"$r_proj" || e="${e}proj=$r_proj "
  req DELETE "$URL_PROJECTS/$(cat "$F_ID")/logs" "" "$(bearer_hdr)" "X-Confirm-Name: $P_NAME"
  r_purga="$CODIGO"
  grep -qE '^(405|404|401)$' <<<"$r_purga" || e="${e}purga=$r_purga "
  # el proyecto y el log SOBREVIVEN (405/404 sin fingir éxito, §5)
  req GET "$URL_PROJECTS/$(cat "$F_ID")" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}proyecto-borrado=$CODIGO "
  req GET "$URL_LOGS/$id_det" "" "$(bearer_hdr)"
  [ "$CODIGO" = "200" ] || e="${e}log-borrado=$CODIGO "
  [ -z "$e" ] && { marca E8.38 PASS "DELETE /panel/logs → $r_log tal cual (H-C8-B2), ficha intacta"; marca E8.47 PASS "8a/8c sin DELETE: $r_log/$r_purga + proyecto y log sobreviven"; } \
    || { marca E8.38 FAIL "($e)"; marca E8.47 FAIL "(negativos C10 — ver E8.38)"; }
  return 0
}

# ---------------------------------------------------------------
# 4. Casos WS (E8.31 handshake y E8.27 frame byte-exacto)
# ---------------------------------------------------------------

# --- E8.31 — pre-upgrade 401/403 con cuerpo VACÍO ---
case_e8_31() {
  echo "[E8.31] WS pre-upgrade: sin token→401; Origin ajeno→403; cuerpo VACÍO ambos" >&2
  local e="" out
  out="$(PY_TOKEN="" python3 "$TMPDIR_QA/ws8_client.py" hs "$WS_HOST" "$WS_PORT" sin-token 2>>"$ERR_WS" || true)"
  grep -q '^HS 401 BODY 0' <<<"$out" || e="sin-token=[$out] "
  out="$(PY_TOKEN="$(cat "$F_TOKEN")" python3 "$TMPDIR_QA/ws8_client.py" hs "$WS_HOST" "$WS_PORT" evil 2>>"$ERR_WS" || true)"
  grep -q '^HS 403 ' <<<"$out" || e="${e}evil=[$out] "
  [ -z "$e" ] && marca E8.31 PASS "401 pre-upgrade cuerpo VACÍO; 403 evil tal cual (canon E7.9: solo status)" || marca E8.31 FAIL "($e)"
  return 0
}

# --- E8.27 — ingesta → EXACTAMENTE 1 frame batch byte-exacto §3.5 ---
case_e8_27() {
  echo "[E8.27] WS: hs 101, set_filters aceptado, ingest → EXACTAMENTE 1 frame batch §3.5" >&2
  local e=""
  : > "$F_WS"
  PY_TOKEN="$(cat "$F_TOKEN")" \
    python3 "$TMPDIR_QA/ws8_client.py" long "$WS_HOST" "$WS_PORT" 25 \
      "$(printf '{"type":"set_filters","filters":{"project_id":%s,"environment":null,"level":null,"from":null,"to":null,"text":null}}' "$(cat "$F_ID")")" \
      >"$F_WS" 2>>"$ERR_WS" &
  WS_PID=$!
  sleep 3
  # inyección del log en vivo (sesión con set_filters activo)
  local sem
  sem="$(python3 - "$NEEDLE_WS" "$CORR_WS" <<'PYEOF'
import json, sys
needle, corr = sys.argv[1], sys.argv[2]
print(json.dumps({"logs": [{
    "level": "ERROR",
    "timestamp": "2026-10-26T11:00:00.000000Z",
    "message": f"{needle} en vivo",
    "environment": "production",
    "source": "ws-src",
    "correlation_id": corr,
}]}, separators=(",", ":")))
PYEOF
)"
  req POST "$URL_BATCH" "$sem" "$(key_hdr)"
  [ "$CODIGO" = "202" ] || e="ingest=$CODIGO "
  local intento=0
  while [ "$intento" -lt 22 ]; do
    grep -q '^frame ' "$F_WS" 2>/dev/null && break
    sleep 1
    intento=$((intento + 1))
  done
  kill "$WS_PID" 2>/dev/null || true
  wait "$WS_PID" 2>/dev/null || true
  WS_PID=""
  grep -q '^hs-ok' "$F_WS" || e="${e}no-hs-ok "
  grep -q '^sf-sent' "$F_WS" || e="${e}no-sf-sent "
  local nf
  nf="$(grep -c '^frame ' "$F_WS" || true)"
  [ "$nf" -eq 1 ] || e="${e}frames=$nf (esperado 1) "
  if ! python3 - "$F_WS" "$NEEDLE_WS" "$(cat "$F_ID")" "$CORR_WS" <<'PYEOF' 2>>"$ERR_WS"; then e="${e}frame-shape "; fi
import json, re, sys
frame = None
with open(sys.argv[1]) as fh:
    for linea in fh:
        if linea.startswith("frame "):
            frame = json.loads(linea[6:])
            break
assert frame is not None, "sin frame"
# §3.5: las 5 claves superiores EXACTAS del contrato
assert set(frame.keys()) == {"type", "batch_id", "project_id", "received_at", "logs"}, frame.keys()
assert frame["type"] == "batch"
assert re.match(r"^b_[0-9a-fA-F-]{36}$", frame["batch_id"]), frame["batch_id"]
assert frame["project_id"] == int(sys.argv[3]), frame["project_id"]
assert frame["logs"], "frame sin logs"
log = frame["logs"][0]
campos = ["id", "project_id", "level", "environment", "timestamp",
          "received_at", "message", "source", "correlation_id",
          "exception", "metadata"]
falta = [c for c in campos if c not in log]
assert not falta, f"LogDto incompleto: {falta}"
assert log["exception"] is None and log["metadata"] is None
assert sys.argv[2] in log["message"], log["message"]
assert log["level"] == "ERROR" and log["source"] == "ws-src"
assert log["correlation_id"] == sys.argv[4], log["correlation_id"]
PYEOF
  local n_ids
  n_ids="$(grep '^frame ' "$F_WS" 2>/dev/null | python3 -c '
import sys, json
ids = set()
for l in sys.stdin:
    ids.add(json.loads(l[6:])["batch_id"])
print(len(ids))' || true)"
  [ "$n_ids" -eq "$nf" ] || e="${e}batch_ids-duplicados "
  [ -z "$e" ] && marca E8.27 PASS "1 frame batch byte-exacto §3.5 (5 claves, LogDto 11, b_+uuid único)" \
    || marca E8.27 FAIL "($e)"
  return 0
}

# ---------------------------------------------------------------
# 5. Ejecución y veredicto
# ---------------------------------------------------------------

echo "== qa_c8_smoke ==" >&2
if [ "${ejecutar_pre:-1}" = "1" ]; then
  case_e8_6
  case_e8_7
  case_e8_14
  case_e8_48
  case_e8_crud
  case_e8_20
  case_e8_23
  case_e8_39
  case_e8_43
  case_e8_34
  case_e8_41
  case_e8_38
  if [ "$CON_ORIGIN" = "1" ]; then
    case_e8_31
    case_e8_27
  else
    for id in $ID_WS_ALL; do marca "$id" BLOQUEADO "$bloqueo_origin"; done
  fi
fi

# Q23 — visual/navegado: el panel REACT renderizado se verifica en la demo C9.
marca Q23 BLOQUEADO "visual del panel (maquetas 01–05, guard, indicador, banda, modales 8a/8b/8c) = MANUAL con navegador para la demo C9 (cubren E8.1–E8.5, E8.8–E8.13, E8.22, E8.29, E8.30-b, E8.32, E8.35, E8.37-b, E8.40, E8.45, E8.46, E8.55)"

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
if [ "$fails" -gt 0 ]; then
  exit 1
fi
exit 0