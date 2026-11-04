---
title: "Nota C10 (deploy): sesión de despliegue del corte C10 a la VM Proxmox + variante CORS-LAN (a+) activada + P1 Tailscale (ronda 2) — corrigendum aplicado en docs/08"
type: operativa-deploy
date: 2026-11-04
what: >
  Sesión de deploy autorizada y pagada por el dueño (2026-11-04): el código
  C10 de origin/main (7e41100) se desplegó en la VM 108 «projectlog»
  siguiendo el runbook FB-1/FB-3 de C9, y la variante CORS-LAN (a+) quedó
  ACTIVADA — no ya latente — con el literal de docs/08 §4. En la misma
  sesión, ronda 2 AUTORIZADA: P1 Tailscale instalado y autenticado
  (nodo projectlog, IP 100.97.243.102), CORS por tailnet verificado en vivo
  y el hallazgo H-D10-TS-FW-NOOP con DECISIÓN RATIFICADA DEL DUEÑO (no
  aplicar la regla de firewall). Alcance EXACTO de
  la autorización: deploy del código + la línea CORS-LAN; nada más
  (sin down -v, sin cambio de password admin, sin tocar firewall/PVE/otros
  CTs, sin commits/push). Todo se verificó en vivo. Este fichero es la
  propuesta de documentación del deploy; NO commiteado (a demanda del
  orquestador).
---

# Deploy C10 en la VM 108 + CORS-LAN (a+) — 2026-11-04

## 1. Estado ANTES (inventario read-only, FB-0)

- VM 108 «projectlog» viva: Debian 12.15, `eth0` 192.168.1.58/24, boot
  2026-10-29; gestión intacta por `ssh -J root@100.64.3.17 root@192.168.1.58`.
- Pila Docker Compose 3/3 healthy, **código C9** (6 días de uptime): el
  `CorsConfig` del VM era pre-C10 (origen único, `setAllowedHeaders` con el
  sufijo comodín `X-Confirm-*`, md5 distinto del repo) — marcador honesto de
  que faltaba todo el corte.
- Firewall nftables GATE #11 aplicado y sin cambios (lo, established, SSH-LAN
  22, 8080 — solo 192.168.1.0/24). `client_max_body_size 5m` presente
  (FB-6 de C9). Binario anotado: `docker compose` v5.6.1. Volumen
  `project_log_pgdata` intacto.

## 2. Deploy del código (FB-1 + FB-3 del runbook C9)

- **Rsync definitivo** (dry-run primero; 0 borrados contra el VM):
  `rsync -a --delete -e "ssh -J root@100.64.3.17"` con excludes:
  - Del design: `.git .atl .env node_modules dist target __pycache__`.
  - **Nuevos de esta sesión (pauta que queda pina para deploys futuros):**
    - `.env*` — el patrón ampliado protege `.env` Y un **`.env.prev-tunnel`**
      preexistente en `/opt/project_log` (backup in situ de la época de
      tunnel de C9, no documentado) que el `--delete` habría ARRASADO
      (el patrón exacto `.env` no lo cubre). H-D10-VM-ENV-BAK.
    - `frontend/nginx.conf` — R-D-C9-9 (preserva el `5m` del destino).
    - Arnés QA NO se despliega (política C9): `scripts/qa_vis_c9.{py,sh}`,
      `qa_vis_c10.{py,sh}`, `qa_c10_smoke.sh`, `ws_echo_test.py`.
- `docker compose up -d --build`: imágenes backend+frontend reconstruidas y
  recreadas; **postgres NUNCA recreado** (`Up 6 days` sin interrupción;
  jamás `down -v`). Confirmado que corre C10 por COMPORTAMIENTO (preflight
  con literales exactos de D-C10-15 en vivo) y por md5 del `CorsConfig`
  del VM IDÉNTICO al del repo (25f0978f5824…).

## 3. Variante CORS-LAN (a+) — ACTIVADA (ya no latente)

- **ANTES, verificado empíricamente contra la pila C9 aún viva:** preflight
  con `Origin: http://192.168.1.58:8080` sobre `/api/v1/panel/*` → **403
  «Invalid CORS request»** (el estado latente que docs/08 §4 describía).
- **Cambio en el `.env` del VM** (backup previo in situ
  `.env.pre-c10-corslan`, chmod 600; sin imprimir secretos):
  `FRONTEND_ORIGIN=http://localhost:8080,http://192.168.1.58:8080`
  (literal exacto de docs/08 §4, CON esquema — `CorsConfig` refleja cada
  origin EXACTO, sin esquema jamás matchearía). 17 claves intactas.
- **DESPUÉS:** preflight x-confirm-name → **200** con ACAO y ACAH
  reflejados; `content-type,x-confirm-log-id` → 200; origin ajena
  (evil.example) → **403**; GET real con Origin LAN → **401 CON ACAO
  reflejado** (CORS pasa; solo falta el JWT).

## 4. Verificación en vivo + smoke manual

- 3/3 healthy; GET / → 200 HTML; /panel/me sin token → 401.
- Curl desde el PVE (perspectiva LAN): GET `http://192.168.1.58:8080/` →
  **200 HTML** + preflight → 200 (firewall intacto).
- Smoke manual vía API (el arnés `qa_c10_smoke.sh` NO se despliega al VM —
  política C9): login (creds del `.env`, en shell efímera, jamás impresas)
  → POST /panel/projects 201 → regenerate key → **ingesta batch 202**
  ({"accepted":2}) → **GET /{id}/summary 200** (by_level/by_environment/
  total — SummaryDto C10 en vivo) → DELETE /panel/logs/{id} sin
  X-Confirm-Log-Id → **400 confirm_required** → con header → **204** →
  purga DELETE /{id}/logs → **200 {"deleted":1}** (flujo 8c) → DELETE
  /{id} con X-Confirm-Name → **204**. Ronda 2: key muerta tras el CASCADE
  → **401** con la misma key.
- **Residuo: CERO.** count=0 por patrón smoke en `projects`; total=1
  (el proyecto del USUARIO, INTOCADO). Las keys y logs del smoke murieron
  con el CASCADE del DELETE del proyecto — limpieza cero-comandos-extra.

## 5. Hallazgos (de guion/estado — NINGUNO de producto)

- **H-D10-VM-ENV-8081** (→ CORRIGENDUM PENDIENTE en docs/08 §2): el `.env`
  del VM tenía `FRONTEND_ORIGIN=http://localhost:8081`, no `:8080` como
  afirma docs/08 §4 (y state.md C9 FB-2 corrigendum). Nunca fue
  load-bearing (misma-origen por proxy no cruza CORS y el reflejo es
  exacto). Se puso el literal ratificado con `:8080`. docs/08 §2 dice hoy
  «el `.env` de la VM contiene solo `http://localhost:8080`» — el valor
  REAL pre-deploy era :8081; el §2 necesita corrigendum (a cargo del
  orquestador, NUNCA tocado por la sesión de deploy).
- **H-D10-VM-ENV-BAK**: dos backups del `.env` quedan in situ en
  `/opt/project_log` (chmod 600, fuera del repo, nada impreso):
  `.env.pre-c10-corslan` (creado esta sesión antes del cambio CORS) y
  `.env.prev-tunnel` (preexistente, protegido del rsync con `.env*`).
- **H-C9-CLOCK-VM108 vigente**: `last_log_at` con offset respecto al
  timestamp enviado en el smoke (reloj local-marcado-UTC del VM —
  cosmético, ya fichado en C9).

## 6. Defectos de guion del smoke (parcheados con re-corrida — pauta fijada)

1. `environment` debe ser `development|staging|production` (usé "test") →
   400 `environment_required` del producto, correcto.
2. `timestamp` obligatorio ISO-8601 CON zona horaria → 400
   `timestamp_without_timezone`, correcto.
3. `LOGID` vacío cuando la ingesta falló → URL `/panel/logs/` sin id →
   401 `invalid_token` ENGAÑOSO (no es la ruta del DELETE de log; con id
   real la ruta responde 400/204 correctamente).
   Nada de esto afecta al producto; el smoke validó el flujo completo en
   la 3.ª corrida.

# Ronda 2 — P1 Tailscale (2026-11-04, orden AUTORIZADA del dueño)

## 7. Instalación y autenticación

- **Tailscale 1.104.5** desde el repo apt oficial de
  `pkgs.tailscale.com/stable/debian` (bookworm); `tailscaled` active y
  habilitado al boot. El paquete instala el keyring como paquete propio
  (tailscale-archive-keyring 1.35.181).
- **Corrigendum de la ruta de keyring de docs/08 §3.1** (aplicado al doc en
  esta misma sesión): `bookworm.noarmored.asc` devuelve **404** hoy
  (sondeo read-only del repo: `bookworm.asc` → 200, `bookworm.gpg` → 200,
  `repo.gpg` → 404). Instalación con **`bookworm.asc`**.
- `tailscale up` (defaults, sin auth key) → el DUEÑO abrió la URL de login
  (https://login.tailscale.com/a/d3105610147a9) y autentificó. Estado
  pre-auth documentado: Logged out / NeedsLogin, `tailscale0` UP sin IP.
- **Nodo autenticado**: **projectlog** · tailnet ProtonTony06@ ·
  IP **100.97.243.102**. Otros nodos visibles (ai-memory, apps, nextcloud,
  openmediavault, tonypc…). El host dev (tonypc) está en la tailnet
  (tailscale0 100.67.69.32).

## 8. Verificación empírica ANTES del firewall (hallazgo decisivo)

| Prueba | Resultado |
|---|---|
| Loopback intern del VM (curl a su propia 100.x) | 200 (documentativo — entra por lo/local, no conclusiva) |
| REAL: PVE (100.64.3.17, nodo tailnet) → VM:8080 | **200** |
| REAL: host dev (100.67.69.32, nodo tailnet) → VM:8080 | **200** |

**El drop esperado NO ocurre** (contradice docs/08 §3.3 de entonces):
el 8080 lo publica Docker con **DNAT en PREROUTING** hacia el contenedor
frontend, así que ese tráfico NUNCA atraviesa el chain `input` de
`table inet pl_vm` (donde vive el policy drop) — va por FORWARD (cadenas
de Docker). El «solo-LAN» de GATE #11 rige el tráfico host-bound, no los
puertos publicados por contenedor; la propiedad se sostuvo de facto por
topología (sin rutas externas) hasta que la tailnet aportó la ruta.

## 9. H-D10-TS-FW-NOOP — DECISIÓN RATIFICADA DEL DUEÑO: NO APLICAR

- La regla propuesta (`iifname "tailscale0" tcp dport 8080 accept`, patrón
  GATE #11) es un **NO-OP funcional** (los paquetes DNATeados no llegan a
  input) y solo ensancharía el surface del host.
- **RATIFICADO por el dueño (2026-11-04): NO aplicar.** nftables queda
  EXACTO como estaba — `nft list table inet pl_vm` idéntico al antes
  (lo / established,related / SSH-LAN 22 / 8080-LAN) y `/etc/nftables.conf`
  persistente intacto. Nada del chain fue tocado en ninguna ronda.

## 10. CORS por tailnet — HECHO y verificado en vivo

- Backup in situ `.env.pre-tail` (chmod 600) y tercer origen:
  `FRONTEND_ORIGIN=http://localhost:8080,http://192.168.1.58:8080,http://100.97.243.102:8080`
  (17 claves intactas, solo esa línea; secretos jamás impresos).
- Recreate **SOLO del backend** (`docker compose up -d backend`;
  postgres `Up 6 days` intocado, frontend intacto, jamás `down -v`);
  healthy <30 s. Pila 3/3 healthy.

## 11. Verificación en vivo final (desde el HOST DEV por tailnet + LAN)

- GET **http://100.97.243.102:8080/** → **200** (panel por tailnet — P1
  del PRD OPERATIVA de facto).
- GET /panel/me con Origin tailnet → **401 + ACAO reflejado**.
- PREFLIGHT (Origin tailnet + x-confirm-name) → **200** con ACAO/ACAH
  reflejados (D-C10-15 en la ruta cross-origin tailnet).
- PREFLIGHT origin ajena → **403 «Invalid CORS request»**.
- Smoke mínimo vía tailnet: login real → create 201 → ingesta **202
  {"accepted":1}** → DELETE **204** → **residuo 0** (proyecto del usuario
  INTOCADO).
- **Regresión LAN** (desde el PVE): GET http://192.168.1.58:8080 → **200**
  y preflight origin LAN → **200** (GATE #11 intacto). Desde el dev
  directo a 192.168.1.58 → 000 (sin ruta: correcto; su camino sigue siendo
  el ProxyJump).

## 12. H-D10-TS-SSH-GESTIÓN (menor)

SSH directo por tailnet al VM NO aplica: Tailscale SSH solo existe en el
PVE (nodo de gestión); habilitarlo en la VM requeriría `tailscale up` con
SSH habilitado — NO tocado sin autorización. La gestión del VM sigue por
`ssh -J root@100.64.3.17 root@192.168.1.58`; el panel por tailnet no la
necesita.

## 13. Corrigendum aplicado en docs/08 (misma sesión, edit SIN commitear)

Tres correcciones puntuales (el dueño commitea): §2 (divergencia real del
.env — H-D10-VM-ENV-8081, hoy lista de 3 origins), §3.1 (keyring
`bookworm.asc`; el `noarmored` citado da 404) y §3.3 (el policy drop NO
descarta el 8080 por tailnet: DNAT en PREROUTING → FORWARD; verificado
empíricamente 200 el 2026-11-04). El doc §4 queda describiendo el paso (a+)
como PENDIENTE — quedó PENDIENTE-EJECUTAR su actualización al estado real
(para la ronda de commits del dueño): el paso está Ejecutado (§8/§10/§11 de
esta nota).