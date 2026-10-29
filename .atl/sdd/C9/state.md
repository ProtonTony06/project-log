---
project: Project_Log
change: C9
artifact: state
last_updated: 2026-10-29
status: done
phase: c9-cerrado (Fase A + Fase B + Bloque 5 COMPLETOS 2026-10-29 — T9.37 commit docs(SDD) ejecutado en esta misma acción; archive-report pendiente de /sdd-archive)
gate: APROBADO 2026-10-29 por el usuario — G1 = E8.40 PASS-CON-NOTA simulado (route-interception); G2 = extras recomendados SÍ (8.26/8.27/8.34/8.36/8.39/8.42/8.45/8.47 + 8.43 con key redactada), opt-ins FUERA (8.11/8.31/8.38); G3 = clipboard NO; G4 = Fase B APROBADA dentro de C9. DECISIÓN ADICIONAL sobre hallazgos H-C9-* (2026-10-29): "APÚNTALOS EN MEMORIA" — parches de producto NO en C9; los 4 findings (WS-COLGADO, doble ?, destino perdido, 3 frames) + NGINX-UPSTREAM anotados para C10 (anchados en memoria projects/project-log.md, commit 3b711bc, y en .atl/notes/c9-hallazgo-*.md); exit 1 honesto de la corrida final = estado de cierre de Fase A. Fase B CONTINÚA según runbook — CON MITIGACIÓN de infra de H-C9-NGINX-UPSTREAM incluida en el deploy de la VM (finding de DEPLOY; el fix de producto sigue siendo C10)
artifacts:
  explore: complete (set re-contraste: qa-plan decía 15 MANUAL, qa-report reclasificó QP-8.26 a [S] — quedan 14; 13/14 automatizables 100%; E8.40 → PASS-CON-NOTA con datos simulados o manual; 3 criterios del 06 con técnica — kill backend stop/up SIN down -v)
  proposal: complete — PENDIENTE GATE (status: pending-gate; 4 opciones al usuario: G1 E8.40, G2 extras, G3 clipboard, G4 Fase B)
  spec: complete (727 líneas; Fase A 6 dominios/42 reqs, E9.1–E9.26; Fase B S9-B1..B6 + R9-B1..B7 + BLOQUEADA-ACCESO; G1–G4 ancladas §0; matiz 8.37 FUERA — seguimos el set EXACTO del gate del coordinador, discrepancia notificada al usuario en el reporte final)
  design: complete (627 líneas; arnés 9 secciones con regla dura ESTRUCTURAL — passO() exige pantalla_vista; 9 bloques de corrida con destructivo ÚLTIMO y kill ÚNICO doble-veredicto; 15 decisiones D-C9-n; File Changes: 2 scripts + qa-log commiteado + PNGs A DISCO no commiteados (D-C9-12); Fase B runbook FB-0..FB-8 con FB-0 SSH-check y orden firewall dura GATE #11; 2 decisiones del orquestador ratificadas: client_max_body_size 5m — DEBE quedar sobre el límite 1 MiB del backend para que el 413 JSON sea del backend, no del proxy (D-C9-13) — y PNGs no commiteados)
  tasks: complete (37 tareas T9.1–T9.37 en 5 bloques: arnés, corrida+bucle, cierre Fase A, Fase B FB-0..FB-8, cierre del corte)
  apply-progress: complete (Bloques 1–4; cierre del corte en tasks.md §Bloque 5 y closure)
  qa-plan: complete (integrado por decisión: los parejas/escenarios viven en spec §2-3 + self-check B0 del arnés)
  tests-written: complete (scripts/qa_vis_c9.py — el arnés ES la suite visual; 26 checks con guard pantalla_vista)
  qa-log: complete (corrida 6 RAW verbatim + residuos .bak de corridas 3/4/5)
  qa-report: done (tabla 26 + Fase B E9-B1..B7 + reconciliación cifra «32 [S]» + demo honesta)
  verify-report: done (8/8 checks PASS — 0 CRITICAL, 0 WARNING, 3 SUGGESTION con ficha)
  archive-report: pending (no solicitado — /sdd-archive cuando toque)
base_head: c144f3d
commits:
  - 4e2b559 test(qa) arnés qa_vis_c9 (py 2434 líneas + sh wrapper; B0 verificado en vivo)

fase-b-log:
  fecha: 2026-10-29 13:20-13:55 CET (sesión reanudada tras caída por API 429; re-anclado con verdad observada)
  FB-0: PASS (T9.22) — `ssh root@192.168.1.58` via ProxyJump (root@100.64.3.17, Tailscale SSH) → ACCESO-OK, hostname=projectlog, eth0 UP 192.168.1.58/24, Debian 12.15. La máquina dev NO está en la LAN (wlp0s20f3, 192.168.42.x) — TODO acceso a la VM vía `-J root@100.64.3.17`.
  vm108:
    creado 13:44 CET por esta sesión (re-creación conforme al briefing "crear VM DE NUEVA"): VMID 108, name=projectlog, Debian 12 genericcloud (qcow2 reutilizado de /root/pl-vm107/ con SHA512SUMS verificada OK), 4096 MB / 2 cores / 32G, ide2 cloud-init local-lvm, onboot=1, serial0 socket (+console.log capturado en /root/pl-vm108/ del host PVE), ostype l26, scsihw virtio-scsi-pci.
    ipconfig0: ip=192.168.1.58/24 gw=192.168.1.1 (estática) — IP DECIDIDA por esta sesión: banda estática de huéspedes PVE (.51/.53/.55/.56/.57) bajo .100, fuera del pool DHCP del router (.248 observado = dispositivo DHCP). ARP .58 limpio antes de asignar; no pisa la .57 de la VM 107.
    credenciales console-root de VM108: /root/pl-vm108/credentials.txt + creds-root.txt en el host PVE (chmod 600; S16 — jamás al repo ni a logs).
    NOTA: eth0 de V107 quedaba sin IP por bug de render de PVE 9.0.3 (nameserver con COMA → network-config `['192.168.1.1,1.1.1.1']` malformada rechazada por cloud-init 22.4.2). Para VM108 se fijó `--nameserver 192.168.1.1` (valor único) y el render es limpio → eth0 arriba al 1.er boot.
  vm107-huerfana:
    creada 13:22 CET por la sesión anterior ANTES de morir (no reflejada en state.md de entonces): config COMPLETA (cloud-init, sshkeys usuario, ipconfig0=.57) pero quedo STOPPED a las 13:35:44 (qmshutdown root@pam desde la sesión anterior); boot 1: cloud-init OK (12.6 s seed) pero eth0 SIN IP (bug nameserver arriba); serial en /root/pl-vm107/serial.log. NO TOCADA por esta sesión (su description dice "no borrar sin coordinar"). Pendiente de decisión del usuario en C10: borrar o reparar (el fix es re-render con nameserver único).
  firewall: SIN aplicar aún (cero reglas tocadas) — riesgo GATE #11 limpio. → SUPERADO: FB-4 hecha (ver abajo).
  fb1-fb6 (2026-10-29 15:30-16:10 CET):
    FB-1: rsync OK — /opt/project_log 2.0 MB, ATL-AUSENTE. Excludes usados: .git .atl .env node_modules dist target __pycache__ + scripts/qa_vis_c9.{py,sh} (el arnés QA NO se despliega al destino — decisión del orquestador, anotada). Tras FB-6: los rsync futuros DEBEN añadir `frontend/nginx.conf` al exclude (R-D-C9-9).
    FB-2: .env generado EN la VM (17 vars, chmod 600). **CORRIGENDUM del design**: el runbook FB-2 del design genera POSTGRES_PASSWORD y SPRING_DATASOURCE_PASSWORD como DOS aleatorios independientes → 28P01 del backend (initdb user ≠ auth user). Corregido: UN solo valor para ambos (H-C9-FB2-DATASOURCE). También ampliado con las vars no-secretas que compose interpola y el runbook omitía: WS_HEARTBEAT_SECONDS=30, FRONTEND_ORIGIN=http://localhost:8080, SPRING_PROFILES_ACTIVE=prod, LOGGING_LEVEL=INFO, TZ=UTC (sin FRONTEND_BIND — default compose 0.0.0.0).
    FB-3: docker compose up -d --build VERDE tras el corrigendum: 3 contenedores healthy (binario anotado: **docker compose — Docker Compose version v5.6.1** en Debian 12; docker-ce repo oficial). Panel HTML por 8080; /panel/me 401 sin token (señal SALUD_BACKEND del arnés).
    FB-4: firewall GATE #11 aplicado con nftables (Debian 12): /etc/nftables.conf (table inet pl_vm, chain input, policy drop): lo accept + established,related + **SSH-LAN PRIMERO** (192.168.1.0/24 dport 22) → verificación en vivo (conexión SSH nueva OK + 8080 cerrado 000) → **DESPUÉS 8080** (192.168.1.0/24 dport 8080) → curl desde PVE (LAN) 200 HTML + ssh vivo. nftables.service habilitado (persistencia FB-7). Consola de escape validada ANTES de tocar firewall (login real por ttyS0: root → shell → whoami → exit). NOTA técnica: el 1.er render usó `flush ruleset` (arrastró las cadenas iptables-nft de Docker → el recreate del frontend falló DNAT) — corregido con `flush table inet pl_vm` SCOPED (jamás arrasa a Docker); verificado DNAT + estado final.
    FB-5: smoke verde: login 200 {expires_at, token, username} (orden literal), /me 200 role ADMIN, binario docker compose anotado.
    FB-6: client_max_body_size 5m aplicado en el nginx del DESTINO (/opt/project_log/frontend/nginx.conf línea 6; en el contenedor vivo confirmado via nginx -T) + pareja desde la LAN (PVE): (1) 0.9 MiB válido en 200 logs → **202 {"accepted":200}**; (2) 1.2 MiB schema-válido → **413 JSON del BACKEND** (cuerpo ISO-8859-1 con code payload_too_large — ver H-C9-FB6-CHARSET). Sin 413 del proxy en la ventana.
  hallazgos-fase-b:
    H-C9-FB2-DATASOURCE: corrigendum del design FB-2 (dos aleatorios independientes) — arreglarla en el destino; NO producto.
    H-C9-FB6-CHARSET-413: el 413 del IngestGuardFilter escribe su JSON en ISO-8859-1 (Latin-1; igual patrón que H-C8-B2 de los 401) — cosmético, literal code correcto; para C10.
    H-C9-DNS-FRONTEND-RESTART: tras un restart de docker.service, el contenedor frontend recreado quedó UNATTAACHED (Networks {}) y en restart-loop con emerg "host not found in upstream backend" — el mismo mecanismo de H-C9-NGINX-UPSTREAM (resolución estática de proxy_pass + timing) pero DE DEPLOY; arreglado con force-recreate tras chains saneadas. En arranque FRÍO normal (todo con network) no se reproduce aparente; FB-7 lo confirma.
    H-C9-CLOCK-VM108: el reloj interno de la VM corre como hora LOCAL marcada como UTC (logs del backend 15:44Z siendo 15:44 CET) — cosmético, no afecta al negocio (TZ=UTC en env); anotado.
    FB-7 (2026-10-29 ~16:20-16:35 CET) — FRÍO y REINICIO, con un hallazgo de catch PREVIO:
      * PRUEBA FRÍA 1.ª (fallida y ÚTIL): tras el shutdown de la ventana de FB-6, un boot limpio reveló nftables.service FAILED — `flush table inet pl_vm` sobre tablas vacías (boot fresco) devuelve error y el servicio no carga (firewall ABIERTO al boot). Cazado por FB-7 tal como estaba diseñada.
      * FIX boot-safe: `/etc/nftables.conf` ahora declara `table inet pl_vm {}` ANTES del `flush table inet pl_vm` (el flush sobre tabla declarada vacía no falla) + `nft -c -f` OK + systemctl start → active (enabled). Reglas verificadas: lo, established, SSH-LAN 22, 8080 (todo 192.168.1.0/24).
      * Verificación post-fix (boot actual): SSH nueva conexión OK por proxy + 8080 desde LAN = 200 (HTML panel).
      * PRUEBA FRÍA RE-RUN (limpia): qm shutdown 108 --timeout 120 → stopped → qm start → SSH de vuelta → `systemctl is-active nftables docker` = active/active → `docker compose ps` = 3/3 healthy "Up About a minute" (autónomos) → curl 8080/ desde LAN = 200 con <html> → **FRÍO OK**.
      * REINICIO: ssh reboot → SSH de vuelta → nftables/docker activos → 3/3 healthy Up 21 s (auto-start, restart: unless-stopped + onboot=1) → 8080 200 + <html> → **REINICIO OK**.
      * El firewall (nftables) persistió en AMBOS estados; nunca quedó la VM inaccesible ni fue necesaria la consola de escape post-validación (seguía disponible).
  firewall: GATE #11 en estado CERRADO y verificado (SOLO SSH-LAN+8080, consola validada, nftables boot-safe probado en frío y reinicio).
  demo-vm (2026-10-29 ~16:12 CET, nivel REST/deploy — 3 criterios del 06):
    CRITERIO 1: lote de 3 con needles demo-c9-vm-161211-n01..03 → 202 {"accepted":3} → visibles por REST (con el del criterio 2, mismo prefijo: 4 resultados, los 3 del lote presentes).
    CRITERIO 2: 1 ERROR production con exception+metadata → 202 {"accepted":1} → detalle (id 204): exception+metadata presentes, stack en exception, level=ERROR env=production.
    CRITERIO 3 (nivel REST): kill ÚNICO ventana de backend → REST caído (000) → up → healthy <180 s → re-listado = delta 0, sin duplicados (4/4 ids idénticos).
    LÍMITE HONESTO: las observaciones NIVEL NAVEGADOR (2.ª ventana sin refetch; cronómetro <120 s; banda/banner WS y exactamente 1 refetch post-restauración) se demostraron en FASE A sobre la pila real (E9.16/E9.24 PASS, E9.25 PASS, E9.26 FAIL por H-C9-WS-COLGADO). En la VM, la parte WS-reconexión del criterio 3 fallaría igual por ese hallazgo de producto (C10) — demo honesto: lo observable a nivel deploy/comportamiento REST es verde; lo WS-nivel queda FAIL-documentado, no simulado.
  producto: dif-0 verificado; ningún comando destructivo sobre la VM 108 (solo lectura + instalación pendiente).
closure-bloque-5 (2026-10-29 ~17:30 CET):
  T9.31: smoke C8 re-corrida post-Fase B → **EXIT 0 · 24 marcas · 23 PASS · 0 FAIL · 1 BLOQUEADO (Q23)** = bit a bit el canon C8 (C8/qa-log.txt:511). La siembra local quedó BARRIDA por el down -v del smoke (esperado, canon).
  T9.32: re-siembra NO ejecutada (el usuario no pidió demo post-corte) — estado local anotado como pila recompuesta SIN siembra C8/C9. Re-siembra disponible a pedido.
  T9.33: qa-report.md ESCRITO (frontmatter results/commit 1056453/log; tabla 26 veredictos + hallazgos + manuales §5 sin silencios + Fase B verde + reconciliación «32 [S]» = 33 líneas con [S] hoy en tabla QP-8.x de C8, NO cifra del script smoke).
  T9.34: verify-report.md ESCRITO (8/8 PASS; 0 CRITICAL / 0 WARNING / 3 SUGGESTION: acotado arnés E9.2, VM 107, estado de siembra).
  T9.36: ficha C9 del 07 → `estado: hecho` con resumen Fase A+B + residuo C10 (sin renegociar literales; D-C8-8 citado).
  T9.37: commit docs(SDD) SELECTIVO — versiona .atl/sdd/C9/*.md + qa-log.txt + docs/07; NO commitea evidencia/ PNGs (D-C9-12), SIN scripts/__pycache__, SIN .env, SIN push.
  PENDIENTE-de-decisión-del-USUARIO (C10): borrado/reparación de la VM 107 huérfana; re-siembra local si quiere demo; A6 ratificación visual humana de los 30 shots; push a origin si lo ordena; hallazgos H-C9-* fichados.
notes: >
  C9 = Demo + deploy + firewall (07 líneas 162–167). ALCANCE FASE A
  (ratificado): convertir las 14 parejas MANUAL-Q23 de C8 (demo visual) en
  checks automatizados Playwright headless (python 1.64.1 + chromium
  headless-shell instalados; pila en :8080 arriba) contra la pila REAL —
  repro de referencia /tmp/qa_c9/repro_detalle.py (NO al repo tal cual) +
  patrón del vídeo (el script NO declara PASS sin haber abierto y observado
  la pantalla real). Screenshots de evidencia en .atl/sdd/C9/. Los 3
  criterios demo del 06 TAMBIÉN cubribles (kill del backend es destructivo
  local: stop/start SIN down -v — jamás borrar datos del usuario). FASE B
  (deploy VM Proxmox + firewall GATE #11 + nginx client_max_body_size +
  smoke compose + secretos .env destino): DESCrita en el proposal pero
  PENDIENTE-de-aprobación. GATE del proposal presentado al usuario vía
  sesión principal — NO avanzar a spec/design sin visto bueno.
---