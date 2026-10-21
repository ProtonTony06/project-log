#!/usr/bin/env bash
# =============================================================================
# qa_c3_qa_smoke.sh — fase sdd-qa (delegación B: test-writer), cambio C3
# Proyectos: CRUD + archivado.
#
# Script de smokes [S]/[B]/[D] INDEPENDIENTE, escrito SOLO desde
# .atl/sdd/C3/qa-plan.md (§1 casos Q1..Q25, §2 manual [B], §3 RA-1..RA-4,
# §6 comandos) + spec.md + design.md. NO invoca ni replica el flujo de
# scripts/qa_c2_smoke.sh (su Q13 hace `down -v`: PROHIBIDO) ni de
# scripts/qa_c3_smoke.sh. No se ejecutan tests [U]/[M] (Q26–Q29: `mvn test`,
# responsabilidad de la delegación C).
#
# Disciplina heredada (qa-plan §0 y §7):
#  - Recomposición ANTI-VENENO: `env -i` (variables del proceso UNSET) +
#    `podman-compose up -d --build --force-recreate` (--force-recreate
#    OBLIGATORIO, lección R-nuevo-3). PROHIBIDO `down -v` y PROHIBIDO `restart`.
#  - `.env` se lee con lista blanca (NO se exporta a ciegas del shell).
#  - Idempotencia: sufijo por ejecución TS; nombres `smoke-c3-${TS}-*`;
#    aserciones de MEMBRESÍA (no exclusividad) salvo el filtro de estado.
#  - 401 SIN parse JSON estricto (entry point ISO-8859-1): status + código
#    tolerante, nunca JSON parse.
#  - psql: has_key DERIVADO como (api_key_hash IS NOT NULL); nunca una
#    columna `has_key` que no existe (R3.7).
#  - Orden de estados: crear → parchear → listar (CD-3.14): el `-a` queda
#    ARCHIVED antes del bloque de filtros de lista.
#
# Salida: "Qn: PASS|FAIL|BLOQUEADO" por caso + líneas RA-*.
#
# FIX qa-run sdd-qa (re-ejecución #1, 2026-10-21): 6 bugs PROPIOS del script
# (no del sistema; pila healthy comprobada aparte) corregidos por delegación C:
#  1) wait_healthy usaba "$2" (nombre del contenedor DB) como timeout y llamaba
#     `salud "$1" "$2"` → `[ -lt` no-integer y BLOQUEO GLOBAL espurio. Ahora
#     timeout="$3" y `salud "$1" "$2"`.
#  2) TOKEN extraído de "$L" (variable inexistente) → "$OUT/login.json".
#  3) curl_get NO enviaba "Authorization: Bearer" → todo GET de lista/ficha/
#     filtro devolvía 401 espurio. Ahora SIEMPRE autenticado (los 401 sin JWT
#     se miden con curl_get_noauth en Q21/Q24, como exige el plan).
#  4) marca de "RA-n: INFO" sumaba a RC_BLOQ (exit 2 espurio) → tolerado.
#  5) ra2_paso7 comparaba el created_at de -a3 (proyecto -a renombrado) con el
#     epoch de -c → EP_CA.
#  6) backticks en el echo de bloqueo_global ejecutaban `mvn test` → citado.
#  7) credenciales admin leídas como ADMIN_USERNAME/ADMIN_PASSWORD, pero el
#     .env y compose.yaml usan ADMIN_INITIAL_USERNAME/ADMIN_INITIAL_PASSWORD
#     (re-ejecución #2) → env_val devolvía vacío y el login daba 401 espurio.
#     Ahora se leen los nombres de compose con fallback a los del plan.
# exit 0 → sin FAIL y sin BLOQUEADO (Q23 BLOQUEADO sin navegador es tolerado
#          por el plan §7: no invalida el Done del gate G5).
# exit 1 → algún caso FAIL.
# exit 2 → algún BLOQUEADO (pila no sana / dependencias / sin git), salvo
#          el caso especial Q23.
# =============================================================================

# =============================================================================
# FIX sdd-verify (2026-10-21): bug #8 y FLOOR de epoch (eran defectos del
# ARTEFACTO QA, no del corte; los veredictos del corte los tenía la vía manual
# §7 del plan — esta corrección deja el veredicto DEL SCRIPT limpio):
#  8) curl_post hardcodeado a /api/v1/panel/projects y el LOGIN hecho con él →
#     401 garantizado. Ahora: ruta como parámetro opcional (default /projects)
#     + helper curl_post_login para /api/v1/panel/auth/login (Q1/Q3/... POST de
#     proyecto sigue en la ruta por default).
#  9) FLOOR(EXTRACT(EPOCH …))::bigint en TODAS las comparaciones contra psql:
#     EXTRACT devuelve numeric con microsegundos y el cast ::bigint REDONDEA,
#     mientras el formatter del DTO trunca a segundos → igualdad estricta
#     falsa (p. ej. …0.7 → round up). FLOOR hace cohincidir ambos lados.
#  psql booleanos: ya ok (f/t literales).
# 10) caso_q10: el jq fijo usaba "$s" sin --arg s → error de compilación jq y
#     FAIL espurio de la ficha; ahora --arg s "ARCHIVED" (estado del -a en la
#     posición de Q10: ya archivado por Q14-parte1).
# 11) ra2_paso7: los grep usaban regex con '|' alternando (grep básico) →
#     multi-match espurio + warning '\ sobrante'; ahora grep -qF (fijo).
#     Y el literal del booleano: psql concatena bool DENTRO de expresión de
#     texto como f/t (como columna separada imprimiría false/true — lección) →
#     el row del paso 7 matchea '|ACTIVE|f|'.
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TS="$(date +%Y%m%d%H%M%S)"
HOST="${HOST:-8080}"
API="http://localhost:${HOST}"
COMPOSE=("$(command -v podman-compose || echo podman-compose)")
BIN_NAME="$(basename "${COMPOSE[0]}")"

OUT="$(mktemp -d /tmp/qa_c3_qa_smoke.XXXXXX)"
trap 'rm -rf "$OUT"' EXIT
COMPOSE_LOG="$OUT/compose.log"

RC_FAIL=0
RC_BLOQ=0
NA="smoke-c3-${TS}-a";  NA2=""; NA3=""
NB="smoke-c3-${TS}-b"
NC="smoke-c3-${TS}-c"
NUNK="smoke-c3-${TS}-unk"
NFALLO="smoke-c3-${TS}-d"   # nombre válido para el 400 de description >2000
AUTH=""                      # "Authorization: Bearer <JWT>"
TOKEN=""
ID_A=""; ID_B=""; ID_C=""; ID_UNK=""
EP_CA=""; EP_CB=""; EP_CC=""
ORIGIN=""

# -----------------------------------------------------------------------------
# helpers
# -----------------------------------------------------------------------------
marca() { # id resultado [nota] [tolerado]
  printf '%s: %s%s\n' "$1" "$2" "${3:+ ($3)}"
  if [ "${4:-}" != "tolerado" ]; then
    case "$2" in
      PASS|PASSED-MANUAL) : ;;
      FAIL) RC_FAIL=$((RC_FAIL+1)) ;;
      *)    RC_BLOQ=$((RC_BLOQ+1)) ;;
    esac
  fi
}

env_val() { # lee SOLO la clave pedida de .env (lista blanca, sin exportar a ciegas)
  sed -n "s/^${1}=//p" "$ROOT/.env" 2>/dev/null | tail -n1 \
    | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'\$//"
}

ep_iso() { date -u -d "$1" +%s; }   # ISO-8601 Z → epoch

salud() { # ¿contenedores healthy?
  local c st
  for c in "$@"; do
    st="$(podman inspect --format '{{.State.Health.Status}}' "$c" 2>/dev/null || echo missing)"
    [ "$st" = "healthy" ] || return 1
  done
}

wait_healthy() { # ctr1 ctr2 timeout_s
  local i=0
  while [ "$i" -lt "$3" ]; do
    if salud "$1" "$2"; then return 0; fi
    sleep 5; i=$((i+5))
  done
  return 1
}

psql_q() { # consulta → stdout (vía el contenedor de BD de la pila)
  podman exec "$CTR_DB" psql -U "$PGUSER" -d "$PGDB" -tAc "$1"
}

miembro() { # fich_lista_json  nombre   → 0 si el nombre está en el array
  jq -e --arg n "$2" '(( [.[]?.name] ) | index($n)) != null' "$1" >/dev/null 2>&1
}
no_miembro() {
  jq -e --arg n "$2" '(( [.[]?.name] ) | index($n)) == null' "$1" >/dev/null 2>&1
}

claves_exactas() { # fichero → 0 si el objeto tiene EXACTAMENTE las 8 claves §3.3
  jq -e '((keys|sort) == (["api_key_prefix","created_at","description",
    "has_key","id","name","status","updated_at"]|sort))' "$1" >/dev/null 2>&1
}

fecha_z() { # fichero campo → 0 si campo casa ^…T…Z$
  jq -e --arg c "$2" '.[$c]|test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")' "$1" >/dev/null 2>&1
}

codigo_json() { # fichero valor → 0 si .code == valor (para 4xx parseables, NUNCA 401)
  jq -e --arg c "$2" '.code == $c' "$1" >/dev/null 2>&1
}

codigo_tolerante() { # fichero literal → 0 si el literal aparece en el body crudo
  # (tolerante a ISO-8859-1; NO es parse JSON estricto — disciplina R3.19)
  grep -aqc "$2" "$1" 2>/dev/null
}

curl_post() { # fichero  JSON-body  [ruta]  → imprime http_code
  # FIX bug #8 (verify 2026-10-21): la ruta ahora es parámetro con default;
  # el helper estaba hardcodeado a /api/v1/panel/projects y el LOGIN se hacía
  # con él → 401 garantizado (defecto del ARTEFACTO QA, no del sistema).
  curl -s -o "$1" -w '%{http_code}' -X POST "$API${3:-/api/v1/panel/projects}" \
    -H "$AUTH" -H 'Content-Type: application/json' -d "$2"
}
curl_post_login() { # fichero  JSON-body  (sin header Authorization)
  curl -s -o "$1" -w '%{http_code}' -X POST "$API/api/v1/panel/auth/login" \
    -H 'Content-Type: application/json' -d "$2"
}
curl_patch() { # fichero  id  JSON-body  → imprime http_code
  curl -s -o "$1" -w '%{http_code}' -X PATCH "$API/api/v1/panel/projects/$2" \
    -H "$AUTH" -H 'Content-Type: application/json' -d "$3"
}
curl_get() { # fichero  ruta   (SIEMPRE autenticado: lista/ficha/filtros exigen JWT)
  curl -s -o "$1" -w '%{http_code}' -H "$AUTH" "$API$2"
}
curl_get_noauth() { # fichero  método  ruta
  curl -s -o "$1" -w '%{http_code}' -X "$2" "$API$3"
}

nombre_fallo() { echo "    se esperaba $1 (llegó $2)"; return 1; }

# -----------------------------------------------------------------------------
# bloqueo global (pila/dependencias): todo [S]/[D] → BLOQUEADO, exit 2
# -----------------------------------------------------------------------------
bloqueo_global() {
  echo "## BLOQUEO GLOBAL: $1"
  local id
  for id in Q1 Q2 Q3 Q4 Q5 Q6 Q7 Q8 Q9 Q10 Q11 Q12 Q13 Q14 Q15 Q16 Q17 Q18 Q19 \
            Q20 Q21 Q22 Q24 Q25 RA-1 RA-2 RA-3 RA-4; do
    marca "$id" BLOQUEADO "$1"
  done
  marca Q23 BLOQUEADO "$1 (sin navegador)" tolerado
  echo
  echo "## Q26–Q29 ([U]/[M]): NO corre aquí — 'mvn test' (delegación C)."
  exit 2
}

# =============================================================================
# 0. Precondiciones
# =============================================================================
echo "## qa_c3_qa_smoke.sh — C3 (proyectos CRUD+archivado) — corrida TS=$TS"
echo

for dep in podman "$BIN_NAME" curl jq git; do
  command -v "$dep" >/dev/null 2>&1 || bloqueo_global "dependencia faltante: $dep"
done
[ -f "$ROOT/.env" ] || bloqueo_global "no existe .env en $ROOT"

# Recomposición anti-veneno (R-nuevo-3): SIEMPRE --force-recreate, env UNSET.
echo "## Recomposición anti-veneno: env -i + up -d --build --force-recreate"
echo "## (PROHIBIDO: down -v, restart — qa-plan §6.2)"
if ! env -i HOME="$HOME" PATH="$PATH" timeout 900 "${COMPOSE[@]}" up -d --build --force-recreate \
     >>"$COMPOSE_LOG" 2>&1; then
  echo "## WARN: '${BIN_NAME} up' devolvió error; el veredicto lo da la salud (ver $COMPOSE_LOG)"
fi

CTR_BACKEND="$(podman ps --format '{{.Names}}' 2>/dev/null | grep -Ei 'backend|api' | head -n1 || true)"
CTR_DB="$(podman ps --format '{{.Names}}' 2>/dev/null | grep -Ei 'db|postgres' | head -n1 || true)"
[ -n "$CTR_BACKEND" ] || bloqueo_global "no se encontró contenedor backend"
[ -n "$CTR_DB" ] || bloqueo_global "no se encontró contenedor de BD"
echo "## Contenedores: backend=$CTR_BACKEND db=$CTR_DB"

wait_healthy "$CTR_BACKEND" "$CTR_DB" 300 \
  || bloqueo_global "pila no alcanza 'healthy' tras recomposición disciplinada (§7: BLOQUEADO, no FAIL)"

ADMIN_USERNAME="$(env_val ADMIN_INITIAL_USERNAME)"
[ -n "$ADMIN_USERNAME" ] || ADMIN_USERNAME="$(env_val ADMIN_USERNAME)"
ADMIN_PASSWORD="$(env_val ADMIN_INITIAL_PASSWORD)"
[ -n "$ADMIN_PASSWORD" ] || ADMIN_PASSWORD="$(env_val ADMIN_PASSWORD)"
POSTGRES_USER="$(env_val POSTGRES_USER)"
POSTGRES_PASSWORD="$(env_val POSTGRES_PASSWORD)"
POSTGRES_DB="$(env_val POSTGRES_DB)"
FRONTEND_ORIGIN="$(env_val FRONTEND_ORIGIN)"
PGUSER="${POSTGRES_USER:-postgres}"
PGDB="${POSTGRES_DB:-$PGUSER}"
ORIGIN="${FRONTEND_ORIGIN:-http://localhost:${HOST}}"

AUTH="Authorization: Bearer pendiente"
LC="$(curl_post_login "$OUT/login.json" "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" || echo 000)"
[ "$LC" = "200" ] || bloqueo_global "login admin falló ($LC) — seed C2 / credenciales .env"
TOKEN="$(jq -r '.token // empty' "$OUT/login.json" 2>/dev/null || true)"
[ -n "$TOKEN" ] || bloqueo_global "login sin token"
AUTH="Authorization: Bearer $TOKEN"
echo "## JWT del admin obtenido (no se imprime: higiene S16)."
echo

# =============================================================================
# 1.A Creación — POST (Q1–Q6; RA-1 mitad (i)-a en Q3)
# =============================================================================
caso_q1() {
  local f="$OUT/q1.json" code desc_bd
  desc_a="Proyecto del smoke C3"
  code="$(curl_post "$f" "{\"name\":\"$NA\",\"description\":\"$desc_a\"}")"
  [ "$code" = "201" ] || { nombre_fallo 201 "$code"; return 1; }
  claves_exactas "$f" || { echo "    claves del DTO no coinciden §3.3"; return 1; }
  jq -e '.summary == null and (has("summary")|not)' "$f" >/dev/null || { echo "    summary presente"; return 1; }
  jq -e '.status=="ACTIVE" and .api_key_prefix==null and .has_key==false' "$f" >/dev/null || return 1
  jq -e --arg d "$desc_a" '.description==$d' "$f" >/dev/null || { echo "    description no ecoada"; return 1; }
  fecha_z "$f" created_at && fecha_z "$f" updated_at || { echo "    fechas sin formato Z"; return 1; }
  ID_A="$(jq -r '.id' "$f")"; EP_CA="$(ep_iso "$(jq -r '.created_at' "$f")")"
  desc_bd="$(psql_q "SELECT (api_key_hash IS NOT NULL) FROM projects WHERE id=$ID_A")"
  [ "$desc_bd" = "f" ] || { echo "    psql: has_key!=false"; return 1; }
  [ "$(psql_q "SELECT status FROM projects WHERE id=$ID_A")" = "ACTIVE" ] || { echo "    psql: status!=ACTIVE"; return 1; }
  return 0
}
caso_q2() {
  local f="$OUT/q2.json" code dnull
  code="$(curl_post "$f" "{\"name\":\"$NB\"}")"
  [ "$code" = "201" ] || { nombre_fallo 201 "$code"; return 1; }
  claves_exactas "$f" || return 1
  jq -e '.description==null' "$f" >/dev/null || { echo "    description!=null"; return 1; }
  jq -e '.status=="ACTIVE" and .api_key_prefix==null and .has_key==false' "$f" >/dev/null || return 1
  fecha_z "$f" created_at && fecha_z "$f" updated_at || return 1
  ID_B="$(jq -r '.id' "$f")"; EP_CB="$(ep_iso "$(jq -r '.created_at' "$f")")"
  dnull="$(psql_q "SELECT description IS NULL FROM projects WHERE id=$ID_B")"
  [ "$dnull" = "t" ] || { echo "    psql: description no es NULL"; return 1; }
  return 0
}
caso_q3() { # 409 name_duplicate inmediato + RA-1 mitad (i)-a (constraint BD, COUNT==1)
  local f="$OUT/q3.json" code cnt
  code="$(curl_post "$f" "{\"name\":\"$NA\"}")"
  [ "$code" = "409" ] || { nombre_fallo 409 "$code"; return 1; }
  codigo_json "$f" name_duplicate || return 1
  jq -e '.field=="name"' "$f" >/dev/null || return 1
  cnt="$(psql_q "SELECT COUNT(*) FROM projects WHERE name='$NA'")"
  [ "$cnt" = "1" ] || { echo "    psql: COUNT=$cnt (esperado 1: no se insertó segunda fila)"; return 1; }
  return 0
}
caso_q4() {
  local f1="$OUT/q4a.json" f2="$OUT/q4b.json" c1 c2 cnt0 cnt1
  cnt0="$(psql_q "SELECT COUNT(*) FROM projects WHERE name LIKE 'smoke-c3-${TS}-%'")"
  c1="$(curl_post "$f1" "{}")"
  [ "$c1" = "400" ] || { nombre_fallo 400 "$c1"; return 1; }
  codigo_json "$f1" field_required || return 1
  jq -e '.field=="name"' "$f1" >/dev/null || return 1
  c2="$(curl_post "$f2" '{"name":""}')"
  [ "$c2" = "400" ] || { nombre_fallo 400 "$c2"; return 1; }
  codigo_json "$f2" field_required || return 1
  jq -e '.field=="name"' "$f2" >/dev/null || return 1
  cnt1="$(psql_q "SELECT COUNT(*) FROM projects WHERE name LIKE 'smoke-c3-${TS}-%'")"
  [ "$cnt0" = "$cnt1" ] || { echo "    psql: fila nueva ($cnt0→$cnt1)"; return 1; }
  return 0
}
caso_q5() {
  local f="$OUT/q5.json" code l201 cnt0 cnt1
  l201="$(printf 'x%.0s' $(seq 1 201))"
  cnt0="$(psql_q "SELECT COUNT(*) FROM projects WHERE name LIKE 'smoke-c3-${TS}-%'")"
  code="$(curl_post "$f" "{\"name\":\"$l201\"}")"
  [ "$code" = "400" ] || { nombre_fallo 400 "$code"; return 1; }
  codigo_json "$f" field_too_long || return 1
  jq -e '.field=="name"' "$f" >/dev/null || return 1
  cnt1="$(psql_q "SELECT COUNT(*) FROM projects WHERE name LIKE 'smoke-c3-${TS}-%'")"
  [ "$cnt0" = "$cnt1" ] || { echo "    psql: fila nueva ($cnt0→$cnt1)"; return 1; }
  return 0
}
caso_q6() {
  local f="$OUT/q6.json" code d2001 cnt0 cnt1
  d2001="$(printf 'y%.0s' $(seq 1 2001))"
  cnt0="$(psql_q "SELECT COUNT(*) FROM projects WHERE name LIKE 'smoke-c3-${TS}-%'")"
  code="$(curl_post "$f" "{\"name\":\"$NFALLO\",\"description\":\"$d2001\"}")"
  [ "$code" = "400" ] || { nombre_fallo 400 "$code"; return 1; }
  codigo_json "$f" field_too_long || return 1
  jq -e '.field=="description"' "$f" >/dev/null || return 1
  cnt1="$(psql_q "SELECT COUNT(*) FROM projects WHERE name LIKE 'smoke-c3-${TS}-%'")"
  [ "$cnt0" = "$cnt1" ] || { echo "    psql: fila creada ($cnt0→$cnt1)"; return 1; }
  return 0
}

# =============================================================================
# 1.B (parcial) Q7 — lista: array plano + membresía del TS
# =============================================================================
caso_q7() {
  local f="$OUT/q7.json" code
  code="$(curl_get "$f" "/api/v1/panel/projects")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e 'type=="array"' "$f" >/dev/null || { echo "    no es array plano"; return 1; }
  jq -e 'length > 0' "$f" >/dev/null || { echo "    lista vacía con miembros del TS esperados"; return 1; }
  jq -e 'all(.[]; ((keys|sort) == (["api_key_prefix","created_at","description","has_key","id","name","status","updated_at"]|sort)))' "$f" >/dev/null || { echo "    algún elemento sin las 8 claves exactas"; return 1; }
  miembro "$f" "$NA" || { echo "    falta $NA en la lista (membresía)"; return 1; }
  miembro "$f" "$NB" || { echo "    falta $NB en la lista (membresía)"; return 1; }
  return 0
}

# =============================================================================
# 1.C Edición y archivado — PATCH (Q12–Q20; Q14 en dos partes por el orden
#     crear→parchear→listar: la reversión a ACTIVE se cubre en RA-2 paso 4)
# =============================================================================
captura_ficha() { # $1 id  → ficha en $OUT/ficha_$1.json; fija EP_UP_x
  local f="$OUT/ficha.json" code
  code="$(curl_get "$f" "/api/v1/panel/projects/$1")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  cp "$f" "$OUT/prev.json"
  return 0
}

caso_q12() { # PATCH solo name: resto intacto; created_at igual; updated_at estricto posterior
  local f="$OUT/q12.json" code
  captura_ficha "$ID_A" || return 1
  sleep 2
  NA2="smoke-c3-${TS}-a2"
  code="$(curl_patch "$f" "$ID_A" "{\"name\":\"$NA2\"}")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  claves_exactas "$f" || return 1
  jq -e --arg n "$NA2" '.name==$n' "$f" >/dev/null || return 1
  jq -e --arg d "$desc_a" '.description==$d' "$f" >/dev/null || { echo "    description cambió (ausente≠cambio, G2)"; return 1; }
  jq -e '.status=="ACTIVE"' "$f" >/dev/null || { echo "    status cambió"; return 1; }
  jq -e --arg c "$(jq -r '.created_at' "$OUT/prev.json")" '.created_at==$c' "$f" >/dev/null || { echo "    created_at cambió"; return 1; }
  [ "$(ep_iso "$(jq -r '.updated_at' "$f")")" -gt "$(ep_iso "$(jq -r '.updated_at' "$OUT/prev.json")")" ] \
    || { echo "    updated_at no estrictamente posterior (epoch <)"; return 1; }
  [ "$(psql_q "SELECT FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE id=$ID_A")" \
    = "$(ep_iso "$(jq -r '.updated_at' "$f")")" ] || { echo "    psql: updated_at divergente del DTO"; return 1; }
  return 0
}
caso_q13() { # PATCH description texto → cambio; null → limpia (columna NULL)
  local f="$OUT/q13a.json" f2="$OUT/q13b.json" code
  code="$(curl_patch "$f" "$ID_A" '{"description":"texto nuevo"}')"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e '.description=="texto nuevo"' "$f" >/dev/null || return 1
  jq -e --arg n "$NA2" '.name==$n and .status=="ACTIVE"' "$f" >/dev/null || return 1
  captura_ficha "$ID_A" || return 1
  sleep 2
  code="$(curl_patch "$f2" "$ID_A" '{"description":null}')"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e '.description==null' "$f2" >/dev/null || { echo "    description no limpia a null"; return 1; }
  jq -e --arg n "$NA2" '.name==$n and .status=="ACTIVE"' "$f2" >/dev/null || return 1
  [ "$(ep_iso "$(jq -r '.updated_at' "$f2")")" -gt "$(ep_iso "$(jq -r '.updated_at' "$OUT/prev.json")")" ] \
    || { echo "    updated_at no refrescado tras '{"description":null}'"; return 1; }
  [ "$(psql_q "SELECT description IS NULL FROM projects WHERE id=$ID_A")" = "t" ] \
    || { echo "    psql: description no NULL"; return 1; }
  return 0
}
caso_q14_parte1() { # archivar; dato persistido e INTACTO; lista ARCHIVED lo muestra
  local f="$OUT/q14a.json" fl="$OUT/q14list.json" code
  code="$(curl_patch "$f" "$ID_A" '{"status":"ARCHIVED"}')"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e '.status=="ARCHIVED"' "$f" >/dev/null || return 1
  jq -e --arg n "$NA2" '.name==$n' "$f" >/dev/null || { echo "    archivar tocó name (regla 3)"; return 1; }
  jq -e '.api_key_prefix==null and .has_key==false' "$f" >/dev/null || { echo "    archivar tocó la key (regla 11)"; return 1; }
  code="$(curl_get "$f" "/api/v1/panel/projects/$ID_A")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e '.status=="ARCHIVED" and .description==null' "$f" >/dev/null || { echo "    re-GET: dato no intacto"; return 1; }
  code="$(curl_get "$fl" "/api/v1/panel/projects?status=ARCHIVED")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  miembro "$fl" "$NA2" || { echo "    lista ARCHIVED no muestra el archivado"; return 1; }
  return 0
}
caso_q15() { # PATCH {} idempotente: 200, updated_id INTACTO (igualdad ESTRICTA tras sleep 2)
  local f="$OUT/q15.json" code
  captura_ficha "$ID_A" || return 1
  sleep 2
  code="$(curl_patch "$f" "$ID_A" '{}')"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  claves_exactas "$f" || return 1
  jq -e --arg n "$(jq -r '.name' "$OUT/prev.json")" --arg s "$(jq -r '.status' "$OUT/prev.json")" \
    '.name==$n and .status==$s' "$f" >/dev/null || return 1
  [ "$(ep_iso "$(jq -r '.updated_at' "$f")")" = "$(ep_iso "$(jq -r '.updated_at' "$OUT/prev.json")")" ] \
    || { echo "    updated_at cambiado con {} (UPDATE indebido, R3.6/G2)"; return 1; }
  [ "$(psql_q "SELECT FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE id=$ID_A")" \
    = "$(ep_iso "$(jq -r '.updated_at' "$f")")" ] || { echo "    psql: updated_at divergente tras {}"; return 1; }
  return 0
}
caso_q16() { # PATCH name/status null → 400 field_required; fila intocada
  local f1="$OUT/q16a.json" f2="$OUT/q16b.json" code c0 c1
  c0="$(psql_q "SELECT name||'|'||status||'|'||COALESCE(description,'~NULL~')||'|'||FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE id=$ID_A")"
  code="$(curl_patch "$f1" "$ID_A" '{"name":null}')"
  [ "$code" = "400" ] || { nombre_fallo 400 "$code"; return 1; }
  codigo_json "$f1" field_required && jq -e '.field=="name"' "$f1" >/dev/null 2>&1 || { echo "    esperado field_required(name)"; return 1; }
  code="$(curl_patch "$f2" "$ID_A" '{"status":null}')"
  [ "$code" = "400" ] || { nombre_fallo 400 "$code"; return 1; }
  codigo_json "$f2" field_required && jq -e '.field=="status"' "$f2" >/dev/null 2>&1 || { echo "    esperado field_required(status)"; return 1; }
  c1="$(psql_q "SELECT name||'|'||status||'|'||COALESCE(description,'~NULL~')||'|'||FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE id=$ID_A")"
  [ "$c0" = "$c1" ] || { echo "    psql: fila mutada (Q16)"; return 1; }
  return 0
}
caso_q17() { # PATCH status fuera de enum ×3 → 400 filter_invalid_value, NUNCA 500; fila intacta
  local f="$OUT/q17.json" code c0 c1
  c0="$(psql_q "SELECT status||'|'||FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE id=$ID_A")"
  for bad in ACTIVO active ARCHIVE; do
    code="$(curl_patch "$f" "$ID_A" "{\"status\":\"$bad\"}")"
    [ "$code" = "400" ] || { echo "    status=$bad → $code (R-2: 500 prohibido)"; return 1; }
    codigo_json "$f" filter_invalid_value && jq -e '.field=="status"' "$f" >/dev/null 2>&1 || { echo "    esperado filter_invalid_value(status) para $bad"; return 1; }
  done
  c1="$(psql_q "SELECT status||'|'||FLOOR(EXTRACT(EPOCH FROM updated_at))::bigint FROM projects WHERE id=$ID_A")"
  [ "$c0" = "$c1" ] || { echo "    psql: fila mutada (Q17)"; return 1; }
  return 0
}
caso_q18() { # PATCH choque de name → 409; B intacto (RA-1 mitad (i)-b)
  local f="$OUT/q18.json" fb="$OUT/q18b.json" code
  captura_ficha "$ID_A" >/dev/null 2>&1 || return 1
  sleep 2
  code="$(curl_patch "$f" "$ID_A" "{\"name\":\"$NB\"}")"
  [ "$code" = "409" ] || { nombre_fallo 409 "$code"; return 1; }
  codigo_json "$f" name_duplicate && jq -e '.field=="name"' "$f" >/dev/null 2>&1 || return 1
  code="$(curl_get "$fb" "/api/v1/panel/projects/$ID_B")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e --arg n "$NB" '.name==$n and .status=="ACTIVE" and .description==null' "$fb" >/dev/null \
    || { echo "    B no intacto tras 409"; return 1; }
  return 0
}
caso_q19() {
  local f="$OUT/q19.json" code
  code="$(curl_patch "$f" "999999" '{"description":"x"}')"
  [ "$code" = "404" ] || { nombre_fallo 404 "$code"; return 1; }
  codigo_json "$f" not_found || return 1
  return 0
}
caso_q20() { # crear -c; GETs repetidos no alteran updated_at; PATCH con campo sí
  local f="$OUT/q20c.json" code g1 g2 g3 i ep0 epG
  code="$(curl_post "$f" "{\"name\":\"$NC\",\"description\":\"Descripción -c del smoke C3\"}")"
  [ "$code" = "201" ] || { nombre_fallo 201 "$code"; return 1; }
  ID_C="$(jq -r '.id' "$f")"; EP_CC="$(ep_iso "$(jq -r '.created_at' "$f")")"
  ep0="$(ep_iso "$(jq -r '.updated_at' "$f")")"
  # RA-3(a): GETs SIN PATCH devuelven el MISMO updated_at (leer NUNCA modifica)
  for i in 1 2 3; do
    [ "$i" -gt 1 ] && sleep 1
    code="$(curl_get "$OUT/q20g$i.json" "/api/v1/panel/projects/$ID_C")"
    [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
    epG="$(ep_iso "$(jq -r '.updated_at' "$OUT/q20g$i.json")")"
    [ "$epG" = "$ep0" ] || { echo "    GET #$i alteró updated_at (R3.6)"; return 1; }
  done
  sleep 2
  code="$(curl_patch "$OUT/q20p.json" "$ID_C" '{"description":"touch"}')"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  [ "$(ep_iso "$(jq -r '.updated_at' "$OUT/q20p.json")")" -gt "$ep0" ] \
    || { echo "    updated_at no estrictamente posterior tras PATCH (RA-3c)"; return 1; }
  [ "$(ep_iso "$(jq -r '.created_at' "$OUT/q20p.json")")" = "$EP_CC" ] \
    || { echo "    created_at cambió durante Q20"; return 1; }
  return 0
}

# =============================================================================
# 1.B (resto) listar/ficha con el -a ARCHIVED (orden crear→parchear→listar)
# =============================================================================
caso_q8() {
  local fa="$OUT/q8act.json" fv="$OUT/q8arq.json" ft="$OUT/q8tod.json" code
  code="$(curl_get "$fa" "/api/v1/panel/projects?status=ACTIVE")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e 'type=="array"' "$fa" >/dev/null || return 1
  miembro "$fa" "$NB" || { echo "    ?status=ACTIVE no contiene -b (ACTIVE del TS)"; return 1; }
  no_miembro "$fa" "$NA2" || { echo "    ?status=ACTIVE contiene el ARCHIVED -a"; return 1; }
  code="$(curl_get "$fv" "/api/v1/panel/projects?status=ARCHIVED")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e 'type=="array"' "$fv" >/dev/null || return 1
  miembro "$fv" "$NA2" || { echo "    ?status=ARCHIVED no contiene -a (archivado del TS)"; return 1; }
  no_miembro "$fv" "$NB" || { echo "    ?status=ARCHIVED contiene un ACTIVE del TS"; return 1; }
  code="$(curl_get "$ft" "/api/v1/panel/projects")"
  miembro "$ft" "$NA2" && miembro "$ft" "$NB" \
    || { echo "    sin query faltan miembros del TS"; return 1; }
  return 0
}
caso_q9() { # ?status= inválido → 400 filter_invalid_value (×3; incluye vacío CD-3.11)
  local f="$OUT/q9.json" code
  for v in active ARCHIVE ""; do
    code="$(curl_get "$f" "/api/v1/panel/projects?status=$v")"
    [ "$code" = "400" ] || { echo "    ?status='$v' → $code (esperado 400, nunca 200/500)"; return 1; }
    codigo_json "$f" filter_invalid_value && jq -e '.field=="status"' "$f" >/dev/null 2>&1 \
      || { echo "    ?status='$v' sin filter_invalid_value(status)"; return 1; }
  done
  return 0
}
caso_q10() {
  local f="$OUT/q10.json" code
  code="$(curl_get "$f" "/api/v1/panel/projects/$ID_A")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  claves_exactas "$f" || return 1
  jq -e --arg i "$ID_A" --arg n "$NA2" --arg s "ARCHIVED" \
    '.id|tostring==$i' "$f" >/dev/null 2>&1 || { echo "    id no coincide"; return 1; }
  jq -e --arg n "$NA2" --arg s "ARCHIVED" '.name==$n and .status==$s' "$f" >/dev/null || { echo "    ficha en blanco o sin dato"; return 1; }
  return 0
}
caso_q11() {
  local f="$OUT/q11.json" code
  code="$(curl_get "$f" "/api/v1/panel/projects/999999")"
  [ "$code" = "404" ] || { nombre_fallo 404 "$code"; return 1; }
  codigo_json "$f" not_found || { echo "    404 sin cuerpo not_found (nunca vacío, AppFlow §5)"; return 1; }
  return 0
}

# =============================================================================
# RA-2 — matriz adversarial del PATCH (pasos 4,2,5,6,7; 1/3 compartidos con
#        Q15/Q16) y reversión de Q14 (paso 4)
# =============================================================================
ra2_paso4() { # ciclo completo de estado sobre A; incluye Q14 (desarchivar)
  local f="$OUT/ra2s4.json" code i
  # re-archivar (2×) → 200 idempotente del archivado
  for i in 1 2; do
    code="$(curl_patch "$f" "$ID_A" '{"status":"ARCHIVED"}')"
    [ "$code" = "200" ] || { nombre_fallo "200 (re-archivar $i)" "$code"; return 1; }
    jq -e '.status=="ARCHIVED"' "$f" >/dev/null || return 1
  done
  # desarchivar → 200 (reversible §4.2) = reversión Q14
  code="$(curl_patch "$f" "$ID_A" '{"status":"ACTIVE"}')"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e '.status=="ACTIVE"' "$f" >/dev/null || { echo "    desarchivar no volvió a ACTIVE"; return 1; }
  jq -e --arg n "$NA2" '.name==$n' "$f" >/dev/null || { echo "    desarchivar tocó name"; return 1; }
  jq -e '.api_key_prefix==null and .has_key==false' "$f" >/dev/null || { echo "    key tocada (regla 11)"; return 1; }
  # re-archivar (3ª) → 200 (queda ARCHIVED para el resto)
  code="$(curl_patch "$f" "$ID_A" '{"status":"ARCHIVED"}')"
  [ "$code" = "200" ] || { nombre_fallo "200 (3ª re-archivo)" "$code"; return 1; }
  jq -e '.status=="ARCHIVED" and .name=="'"$NA2"'"' "$f" >/dev/null || return 1
  return 0
}
ra2_paso2() { # {"description":null} sobre un proyecto CON description (C) → limpieza real
  local f="$OUT/ra2s2.json" code
  captura_ficha "$ID_C" >/dev/null 2>&1 || return 1
  code="$(curl_patch "$f" "$ID_C" '{"description":null}')"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e '.description==null' "$f" >/dev/null || return 1
  [ "$(psql_q "SELECT description IS NULL FROM projects WHERE id=$ID_C")" = "t" ] \
    || { echo "    psql: description no NULL en -c"; return 1; }
  return 0
}
ra2_paso5() { # campos desconocidos ignorados (CD-3.9 — XFAIL/ancla decisión menor)
  local f="$OUT/ra2s5.json" fu="$OUT/ra2s5u.json" code
  code="$(curl_post "$fu" "{\"name\":\"$NUNK\",\"nmae\":\"x\"}")"
  [ "$code" = "201" ] || { nombre_fallo "201 (POST con campo desconocido)" "$code"; return 1; }
  claves_exactas "$fu" || { echo "    POST unk: claves §3.3"; return 1; }
  ID_UNK="$(jq -r '.id' "$fu")"
  captura_ficha "$ID_A" >/dev/null 2>&1 || return 1
  NA3="smoke-c3-${TS}-a3"
  code="$(curl_patch "$f" "$ID_A" "{\"descripton\":\"hack\",\"status\":\"ACTIVE\",\"name\":\"$NA3\"}")"
  [ "$code" = "200" ] || { nombre_fallo "200 (PATCH con desconocida)" "$code"; return 1; }
  jq -e --arg n "$NA3" '.name==$n and .status=="ACTIVE"' "$f" >/dev/null || { echo "    status/name no aplicados"; return 1; }
  jq -e '.description==null' "$f" >/dev/null || { echo "    desconocida 'descripton' no ignorada"; return 1; }
  code="$(curl_get "$OUT/ra2s5r.json" "/api/v1/panel/projects/$ID_A")"
  [ "$code" = "200" ] || { nombre_fallo 200 "$code"; return 1; }
  jq -e --arg n "$NA3" '.name==$n and .status=="ACTIVE" and .description==null' "$OUT/ra2s5r.json" >/dev/null \
    || { echo "    re-GET confirma datos reales (re-GET confirma: CD-3.9)"; return 1; }
  return 0
}
ra2_paso6() { # cuerpos no-JSON → 400 field_required SIN field (CD-3.9)
  local f="$OUT/ra2s6.json" code
  code="$(curl -s -o "$f" -w '%{http_code}' -X POST "$API/api/v1/panel/projects" \
    -H "$AUTH" -H 'Content-Type: application/json' -d 'not-json{{{')"
  [ "$code" = "400" ] || { nombre_fallo "400 (POST not-json)" "$code"; return 1; }
  codigo_json "$f" field_required || return 1
  jq -e 'has("field")|not' "$f" >/dev/null || { echo "    field presente (debe omitirse, CD-3.9)"; return 1; }
  code="$(curl -s -o "$f" -w '%{http_code}' -X PATCH "$API/api/v1/panel/projects/$ID_A" \
    -H "$AUTH" -H 'Content-Type: application/json' -d 'not-json{{{')"
  [ "$code" = "400" ] || { nombre_fallo "400 (PATCH not-json)" "$code"; return 1; }
  codigo_json "$f" field_required || return 1
  code="$(curl -s -o "$f" -w '%{http_code}' -X PATCH "$API/api/v1/panel/projects/$ID_A" -H "$AUTH")"
  [ "$code" = "400" ] || { nombre_fallo "400 (PATCH sin body)" "$code"; return 1; }
  codigo_json "$f" field_required || return 1
  return 0
}
ra2_paso7() { # conservación de datos: psql del TS (names/status finales + created_at invariante)
  local r
  r="$(psql_q "SELECT name, status, (api_key_hash IS NOT NULL), FLOOR(EXTRACT(EPOCH FROM created_at))::bigint
    FROM projects WHERE name IN ('$NA3','$NB','$NC','$NUNK') ORDER BY id" | tr '\n' ';')"
  echo "$r" | grep -qF "$NA3|ACTIVE|f|$EP_CA;" || { echo "    -a3: estado final inesperado ($r)"; return 1; }
  echo "$r" | grep -qF "$NB|ACTIVE|f|$EP_CB;" || { echo "    -b: estado final inesperado"; return 1; }
  echo "$r" | grep -qF "$NC|ACTIVE|f|$EP_CC;" || { echo "    -c: estado final inesperado"; return 1; }
  echo "$r" | grep -qF "$NUNK|ACTIVE|f|" || { echo "    -unk: estado final inesperado"; return 1; }
  return 0
}

# =============================================================================
# 1.D/E/F — Q21 (401 ×4), Q24 (cadenas C2), Q22 (maquetas), Q23 ([B])
# =============================================================================
caso_q21() {
  local f="$OUT/q21.json" code
  code="$(curl_get_noauth "$f" GET "/api/v1/panel/projects")"
  [ "$code" = "401" ] || { echo "    GET lista → $code (esperado 401)"; return 1; }
  codigo_tolerante "$f" invalid_token || echo "    (nota: body sin 'invalid_token' reconocible — tolerante)"
  code="$(curl -s -o "$f" -w '%{http_code}' -X POST "$API/api/v1/panel/projects" \
    -H 'Content-Type: application/json' -d '{"name":"x"}')"
  [ "$code" = "401" ] || { echo "    POST → $code (esperado 401)"; return 1; }
  code="$(curl_get_noauth "$f" GET "/api/v1/panel/projects/$ID_A")"
  [ "$code" = "401" ] || { echo "    GET /{id} → $code (esperado 401)"; return 1; }
  code="$(curl -s -o "$f" -w '%{http_code}' -X PATCH "$API/api/v1/panel/projects/$ID_A" \
    -H 'Content-Type: application/json' -d '{}')"
  [ "$code" = "401" ] || { echo "    PATCH → $code (esperado 401)"; return 1; }
  return 0
}
caso_q24() {
  local f="$OUT/q24.json" h code
  code="$(curl_post_login "$f" "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")"
  [ "$code" = "200" ] || { nombre_fallo "200 (login)" "$code"; return 1; }
  jq -e '.token|type=="string" and length>0' "$f" >/dev/null || return 1
  jq -e '.username|type=="string"' "$f" >/dev/null || return 1
  jq -e '.expires_at|test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")' "$f" >/dev/null \
    || { echo "    expires_at sin formato Z"; return 1; }
  # 401 invalid_credentials ×3 — status obligatorio, código TOLERANTE (sin parse estricto)
  code="$(curl_post_login "$f" "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"mala-$TS\"}")"
  [ "$code" = "401" ] || { echo "    password mala → $code"; return 1; }
  codigo_tolerante "$f" invalid_credentials || echo "    (nota: body sin 'invalid_credentials' — tolerante)"
  code="$(curl_post_login "$f" "{\"username\":\"usuario-malo-$TS\",\"password\":\"$ADMIN_PASSWORD\"}")"
  [ "$code" = "401" ] || { echo "    usuario malo → $code"; return 1; }
  codigo_tolerante "$f" invalid_credentials || echo "    (nota tolerante)"
  code="$(curl_post_login "$f" 'cuerpo-roto{{{')"
  [ "$code" = "401" ] || { echo "    cuerpo roto → $code"; return 1; }
  codigo_tolerante "$f" invalid_credentials || echo "    (nota tolerante)"
  # /me sin JWT → 401 (tolerante)
  code="$(curl_get_noauth "$f" GET "/api/v1/panel/auth/me")"
  [ "$code" = "401" ] || { echo "    /me → $code (esperado 401)"; return 1; }
  codigo_tolerante "$f" invalid_token || echo "    (nota tolerante: sin parse estricto)"
  # GET /panel/projects sin JWT (con controller real) → 401 (S19)
  code="$(curl_get_noauth "$f" GET "/api/v1/panel/projects")"
  [ "$code" = "401" ] || { echo "    GET /panel/projects sin JWT → $code (esperado 401, S19)"; return 1; }
  # preflight CORS con PATCH (nuevo verbo en ruta nueva) → 20[46], ACAO reflejada
  h="$(curl -s -o /dev/null -D - -w '\n%{http_code}' -X OPTIONS "$API/api/v1/panel/projects" \
       -H "Origin: $ORIGIN" -H 'Access-Control-Request-Method: PATCH')"
  code="$(printf '%s' "$h" | tail -n1)"
  case "$code" in 200|204) ;; *) echo "    preflight → $code (esperado 200 o 204)"; return 1 ;; esac
  printf '%s' "$h" | grep -qi "access-control-allow-origin: *$ORIGIN" || { echo "    ACAO no reflejada exacta"; return 1; }
  printf '%s' "$h" | grep -qi 'access-control-allow-methods:.*PATCH' || { echo "    PATCH ausente en Allow-Methods"; return 1; }
  return 0
}
caso_q22() {
  local f="$OUT/q22a.html" f2="$OUT/q22b.html" f3="$OUT/q22docs.html" meta code
  meta="$(curl -s -o "$f" -w '%{http_code} %{content_type}' "$API/mockups/04-proyectos.html")"
  code="${meta%% *}"; [ "$code" = "200" ] || { echo "    04 copia → $code"; return 1; }
  [[ "${meta#* }" == text/html* ]] || { echo "    04 copia content-type: ${meta#* }"; return 1; }
  grep -qa '/api/v1/panel/auth/login' "$f" || { echo "    04 sin marca /auth/login"; return 1; }
  grep -qa '/api/v1/panel/projects' "$f" || { echo "    04 sin marca /projects"; return 1; }
  meta="$(curl -s -o "$f2" -w '%{http_code} %{content_type}' "$API/mockups/05-proyecto-detail.html?id=$ID_A")"
  code="${meta%% *}"; [ "$code" = "200" ] || { echo "    05 copia → $code"; return 1; }
  [[ "${meta#* }" == text/html* ]] || { echo "    05 copia content-type: ${meta#* }"; return 1; }
  grep -qa '/api/v1/panel/projects' "$f2" || { echo "    05 sin marca /projects"; return 1; }
  grep -qaE '(\?id=|URLSearchParams|searchParams|location\.search)' "$f2" || { echo "    05 sin uso de ?id="; return 1; }
  # /docs/mockups NOT servido: fallback a index.html del SPA o 404, JAMÁS el HTML original
  meta="$(curl -s -o "$f3" -w '%{http_code}' "$API/docs/mockups/04-proyectos.html")"
  code="${meta%% *}"
  if [ "$code" = "200" ]; then
    cmp -s "$f3" "$ROOT/docs/mockups/04-proyectos.html" 2>/dev/null \
      && { echo "    /docs/mockups SÍ sirvió el HTML original (intocable expuesto)"; return 1; }
  fi
  return 0
}

procedimiento_manual_q23() {
  cat <<'EOM'
## Q23 [B] — procedimiento MANUAL (qa-plan §2) si no hay automatización con driver:
##   Preparación: pila healthy tras up -d --build --force-recreate; credenciales
##   dev del .env (ADMIN_USERNAME/ADMIN_PASSWORD); ≥1 proyecto ACTIVE y ≥1
##   ARCHIVED del TS. URL: http://localhost:8080/mockups/04-proyectos.html
##   1. Abrir 04 → aparece el login inline (form), NO un panel vacío con error JS.
##   2. Loguear con credenciales dev → llama POST /api/v1/panel/auth/login (mismo
##      origen, sin CORS en consola) y guarda sessionStorage["mockups_token"]; el
##      panel sustituye al form.
##   3. Lista real → tarjetas con name, description, etiqueta Activo/Archivado,
##      línea "sin key · has_key: false", created_at formato Z — datos REALES de la pila.
##   4. Filtro → toggle Todos/Activos/Archivados cambia lo visible según ?status=
##      (coherente con Q8); estado vacío → «No hay proyectos todavía».
##   5. Archivar desde 04 → clic Archivar en una tarjeta ACTIVE → PATCH REAL
##      {"status":"ARCHIVED"} (verificar: location.reload tras el PATCH; luego psql
##      status='ARCHIVED' y/o re-GET por API); UI muestra «Archivado» y el botón
##      pasa a Desarchivar.
##   6. Abrir 05 desde la tarjeta (enlace <a> con ?id=…) → ficha pinta name, status,
##      description, api_key_prefix/has_key y created_at/updated_at desde GET …/{id}.
##   7. Archivar/Desarchivar desde 05 → clic → PATCH {status} real → verificar en BD
##      y en la ficha recargada.
##   8. Desarchivar (ciclo completo) → status='ACTIVE' en BD y «Activo» en la UI.
##   9. Originales intactos → lo operado es /mockups/… (servido de public/);
##      /docs/mockups/04-proyectos.html NO es lo que se operó (diff 0: Q25).
EOM
}

caso_q23_automatica_minima() {
  # Lo mínimo posible con un binario headless disponible (sin instalar nada):
  # captura de evidencia de carga de la copia servida. NUNCA sustituye los
  # pasos de ratón §2 — un [B] sin evidencia de los pasos es BLOQUEADO, no PASS.
  local url04="$API/mockups/04-proyectos.html" url05="$API/mockups/05-proyecto-detail.html?id=$ID_A"
  local b
  b="$(command -v chromium || command -v chromium-browser || command -v google-chrome || command -v firefox || true)"
  [ -n "$b" ] || return 1
  case "$(basename "$b")" in
    firefox)
      mkdir -p "$OUT/ffprof"
      timeout 90 "$b" --headless --no-remote --profile "$OUT/ffprof" \
        --screenshot "$OUT/q23_04.png" --window-size=1280,900 "$url04" >/dev/null 2>&1 \
        && timeout 90 "$b" --headless --no-remote --profile "$OUT/ffprof" \
        --screenshot "$OUT/q23_05.png" --window-size=1280,900 "$url05" >/dev/null 2>&1 ;;
    *)
      timeout 90 "$b" --headless=new --disable-gpu --no-sandbox --user-data-dir="$OUT/chromeprof" \
        --screenshot="$OUT/q23_04.png" --window-size=1280,900 "$url04" >/dev/null 2>&1 \
        && timeout 90 "$b" --headless=new --disable-gpu --no-sandbox --user-data-dir="$OUT/chromeprof" \
        --screenshot="$OUT/q23_05.png" --window-size=1280,900 "$url05" >/dev/null 2>&1 ;;
  esac
  [ -s "$OUT/q23_04.png" ] || return 1
  return 0
}

# =============================================================================
# 1.F Q25 [D] — intocables diff-0 + zero-migraciones + Flyway
# =============================================================================
INTOCABLES=(
  '*SecurityConfig.java' '*CorsConfig.java' '*RestAuthenticationEntryPoint.java'
  '*JwtAuthFilter.java' '*TokenService.java' '*AdminSeeder.java'
  '*StartupSecretsValidator.java' '*spring.factories'
  'backend/src/main/resources/application.yml'
  'backend/src/main/resources/application*.yml'
  'backend/src/test/resources/application-test.yml'
  'backend/pom.xml' 'pom.xml' 'compose.yaml'
  'frontend/nginx.conf' 'frontend/Dockerfile' 'frontend/vite.config.ts'
  'docs/mockups' 'scripts/qa_c2_smoke.sh' 'scripts/ws_echo_test.py'
  'backend/src/main/resources/db/migration/V1__init.sql'
)
caso_q25() {
  git rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || { echo "    no es repo git: equivalente estado repo indisponible"; return 1; }
  git diff --quiet -- "${INTOCABLES[@]}" 2>/dev/null \
    || { echo "    working-tree diff != 0 en intocables"; return 1; }
  git diff --cached --quiet -- "${INTOCABLES[@]}" 2>/dev/null \
    || { echo "    index diff != 0 en intocables"; return 1; }
  if ls backend/src/main/resources/db/migration/V[2-9]__*.sql >/dev/null 2>&1; then
    echo "    existen migraciones V2+ (prohibidas, S24/E3)"; return 1
  fi
  [ -f backend/src/main/resources/db/migration/V1__init.sql ] || { echo "    falta V1__init.sql"; return 1; }
  [ "$(psql_q "SELECT COUNT(DISTINCT version) FROM flyway_schema_history")" = "1" ] \
    || { echo "    flyway_schema_history con versiones distintas de 1"; return 1; }
  [ "$(psql_q "SELECT DISTINCT version FROM flyway_schema_history")" = "1" ] \
    || { echo "    flyway_schema_history != 1"; return 1; }
  return 0
}

# =============================================================================
# EJECUCIÓN (orden del plan §0: crear → parchear → listar → RA → auth → maquetas)
# =============================================================================
run() { # id función → imprime veredicto
  if "$2"; then marca "$1" PASS; else marca "$1" FAIL; fi
}

echo "### 1.A Creación — POST"
run Q1 caso_q1
run Q2 caso_q2
run Q3 caso_q3
run Q4 caso_q4
run Q5 caso_q5
run Q6 caso_q6
echo
echo "### 1.B Lista inicial (Q7)"
run Q7 caso_q7
echo
echo "### 1.C Edición y archivado (Q12–Q20; Q14 en 2 partes)"
run Q12 caso_q12
run Q13 caso_q13
if caso_q14_parte1; then
  Q14P1_RC=0
  echo "Q14(parte 1): OK — archivar+persistencia+lista ARCHIVED (reversión en RA-2 paso 4)"
else
  Q14P1_RC=1
  echo "Q14(parte 1): FAIL — el veredicto final de Q14 se emite tras RA-2 paso 4"
fi
run Q15 caso_q15
run Q16 caso_q16
run Q17 caso_q17
run Q18 caso_q18
run Q19 caso_q19
run Q20 caso_q20
echo
echo "### 1.B listar/ficha con -a ARCHIVED (Q8–Q11)"
run Q8 caso_q8
run Q9 caso_q9
run Q10 caso_q10
run Q11 caso_q11
echo "### RA re-ataques (integrados en los casos Q; veredicto propio)"
# Q14 parte 2 (reversión) vive en RA-2 paso 4; el veredicto Q14 une ambas partes.
P4_RC=1
if ra2_paso4; then
  P4_RC=0
  marca RA-2-paso4 PASS "ciclo ARCHIVED×2→ACTIVE→ARCHIVED idempotente; key/logs intactos"
else
  marca RA-2-paso4 FAIL
fi
if [ "${Q14P1_RC:-1}" = "0" ] && [ "$P4_RC" = "0" ]; then
  marca Q14 PASS "archivar→desarchivar reversible; dato persistido e intacto (regla 3)"
else
  marca Q14 FAIL
fi
run "RA-2-paso2" ra2_paso2
run "RA-2-paso5" ra2_paso5
run "RA-2-paso6" ra2_paso6
run "RA-2-paso7" ra2_paso7
echo
echo "### Q21 — 401 sin JWT en las 4 rutas (sin parse estricto del 401)"
run Q21 caso_q21
echo
echo "### Q24 — cadenas auth de C2 intactas (RA-4)"
run Q24 caso_q24
echo
echo "### Q22 — maquetas 04/05 servibles (curl)"
run Q22 caso_q22
echo
echo "### 1.E Q23 — verificación de navegador [B]"
if caso_q23_automatica_minima; then
  echo "## Evidencia mínima headless capturada: $OUT/q23_04.png, $OUT/q23_05.png (imágenes borradas con $OUT al salir; copiar antes si se necesitan)."
  procedimiento_manual_q23
  if [ "${QA_C3_Q23_PASSED_MANUAL:-0}" = "1" ]; then
    marca Q23 PASSED-MANUAL "declarado por el operador tras ejecutar §2 (flag QA_C3_Q23_PASSED_MANUAL=1)"
  else
    marca Q23 BLOQUEADO "sin driver de automatización de ratón: evidencia mínima headless OK; NUNCA PASS sin evidencia de los 9 pasos §2 (ejecutar manual o setear QA_C3_Q23_PASSED_MANUAL=1)" tolerado
  fi
else
  procedimiento_manual_q23
  marca Q23 BLOQUEADO "sin navegador" tolerado
fi
echo
echo "### Q25 — diff 0 intocables + zero-migraciones + Flyway"
run Q25 caso_q25
echo

# Veredictos de re-ataques por síntesis (mitades cubiertas por casos ya corridos)
marca RA-1 INFO "mitad (i) cubierta por Q3/Q18 (409 real END-TO-END, psql COUNT==1, B intacto); mitad (ii) [M] DuplicateKeyException→409 vive en ProjectsControllerMvcTest (existente: lo valida la delegación C con mvn test — contingencia RA-1: si el test no existe en la clase, es FAIL de RA-1)" tolerado
marca RA-3 INFO "integrado en Q12/Q15/Q20 (a: GETs invariantes; b: {} intacto tras sleep 2; c: PATCH estrictamente posterior) + created_at invariante" tolerado
marca RA-4 INFO "materializado en Q21+Q24 (login, invalid_credentials ×3, invalid_token /me y /panel/projects, preflight PATCH 20[46])" tolerado

# Cierre: pila healthy
if salud "$CTR_BACKEND" "$CTR_DB"; then
  echo "## Cierre: pila healthy (backend/db)."
else
  echo "## Cierre: pila NO healthy — la corrida queda señalizada (§7)."
  RC_BLOQ=$((RC_BLOQ+1))
fi

echo
echo "## Resumen: FAIL=$RC_FAIL BLOQUEADO=$RC_BLOQ (Q23 BLOQUEADO es tolerado, plan §7)"
[ "$RC_FAIL" -eq 0 ] || exit 1
[ "$RC_BLOQ" -eq 0 ] || exit 2
exit 0