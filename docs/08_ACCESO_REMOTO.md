---
status: valido
updated: 2026-10-29
fuera-del-paquete: si
---

# Acceso remoto al panel: Project_Log

> v1.0 · 2026-10-29 · **Documento operativo, NO del paquete de definición**
> (los 7 docs de `/docs` están congelados; este fichero nace del corte SDD
> C10 — tarea T10.26, decisión D-C10-14 — y traza a los REQ-C10-DOC-1..4 del
> spec de ese corte). Cita los literales del TRD (§2.3, §2.5, §6.7, §6.8);
> jamás los redefine. No ejecuta nada: describe el estado real de la VM y
> los pasos operativos, marcando explícitamente lo que está SIN implementar
> o PENDIENTE de ratificación del usuario.

## Tabla de contenidos

1. [Objetivo y alcance](#1-objetivo-y-alcance)
2. [Estado de partida (corte C9)](#2-estado-de-partida-corte-c9)
3. [Acceso remoto Tailscale (P1 del PRD)](#3-acceso-remoto-tailscale-p1-del-prd)
4. [CORS-LAN — condicionada (a+), pendiente de ratificación](#4-cors-lan--condicionada-a-pendiente-de-ratificación-del-usuario)
5. [Proxy inverso con HTTPS (P2) — SIN implementar](#5-proxy-inverso-con-https-p2--sin-implementar)
6. [Referencias](#6-referencias)

## 1. Objetivo y alcance

Este doc es la referencia operativa para alcanzar el panel de Project_Log
desde fuera de la LAN del homelab, sin exponer nada a Internet ni romper el
MVP. Cubre dos niveles, con el alcance ya fijado en el TRD §6.8:

| Prioridad | Qué | Estado |
|---|---|---|
| **P1** (PRD) | Acceso remoto por **Tailscale**: instalar Tailscale en la VM y acceder al **mismo puerto del frontend (8080)** por la IP de la tailnet; **cero cambios en la Compose** | **Operativa como ruta de gestión SSH** (FB-0 de C9); el acceso al *panel* por tailnet queda documentado aquí |
| **P2** (PRD) | **Proxy inverso con HTTPS** (dominio futuro, Let's Encrypt o TLS en el propio nginx) | **Documentado SIN implementar** en el MVP (literal §6.8) |

Fuera de alcance de este doc: cambios en `compose.yaml`, en el código del
backend/frontend o en `.env` de la VM — la variante CORS-LAN se describe como
paso de deploy futuro condicionado (sección 4), **no** como hecho consumado.

## 2. Estado de partida (corte C9)

Estado verificado del despliegue al cerrar el corte C9 (fuente:
`.atl/sdd/C9/state.md`, FB-0, y `qa-report.md` C9):

- **VM 108 «projectlog»** en el homelab Proxmox: Debian 12.15, `eth0` UP
  `192.168.1.58/24` (FB-0).
- **Pila Docker Compose: 3/3 healthy** (postgres, backend, frontend) sirviendo
  el panel por **8080**; auto-start verificado en frío y tras reinicio
  (`restart: unless-stopped` + `onboot=1`).
- **Firewall nftables — GATE #11 superado**: `/etc/nftables.conf`
  (`table inet pl_vm`, chain `input`, `policy drop`) con solo
  `lo`, conexiones `established,related`, **SSH desde la LAN**
  (192.168.1.0/24 → dport 22) y **8080 desde la LAN** (192.168.1.0/24 →
  dport 8080). Boot-safe probado en frío y en reinicio (FB-7). Orden dura del
  GATE respetada: SSH-LAN primero, jamás se cortó la sesión de gestión.
- **Tailscale SSH ya operativo como ruta de gestión** (FB-0): la máquina de
  desarrollo NO está en la LAN (wlp0s20f3, 192.168.42.x); todo acceso a la VM
  salta por la tailnet:

  ```
  ssh -J root@100.64.3.17 root@192.168.1.58
  ```

  El salto (`ProxyJump`) usa el nodo de tailnet `100.64.3.17` con Tailscale
  SSH; el destino es la IP LAN de la VM. Cero credenciales nuevas: la cadena
  SSH existente es el camino autorizado de gestión.

**Implicación de firewall** (clave para el resto del doc): el GATE #11 solo
deja entrar 8080 desde `192.168.1.0/24`. Cualquier acceso al panel desde la
tailnet que NO viaje por el túnel SSH-LAN tocaría una regla que hoy no existe.

## 3. Acceso remoto Tailscale (P1 del PRD)

Literal TRD §6.8: «**Acceso remoto Tailscale** (P1 del PRD): instalar
Tailscale en la VM y acceder al mismo puerto del frontend por la IP de la
tailnet; cero cambios en la Compose.»

### 3.1 Instalación en la VM `projectlog`

Ruta de gestión: la cadena ProxyJump de la sección 2. Dentro de la VM
(Debian 12):

```bash
# 1. Añadir el repositorio oficial de Tailscale para Debian 12 (bookworm)
curl -fsSL https://pkgs.tailscale.com/stable/debian/bookworm.noarmored.asc \
  | tee /usr/share/keyrings/tailscale-archive-keyring.asc >/dev/null
curl -fsSL https://pkgs.tailscale.com/stable/debian/bookworm.list \
  | tee /etc/apt/sources.list.d/tailscale.list

# 2. Instalar y levantar
apt-get update && apt-get install -y tailscale
systemctl enable --now tailscaled

# 3. Unir la VM a la tailnet (autenticación por navegador/link en la salida)
tailscale up
```

`tailscale up` imprime un enlace de autenticación; al aceptarlo desde la
cuenta de la tailnet existente, la VM recibe **su propia IP de tailnet**
(gestión: `tailscale ip -4` dentro de la VM, o consola admin de Tailscale).
Esa IP es la que sustituye a `192.168.1.58` fuera de la LAN.

### 3.2 Acceso al panel por el MISMO puerto 8080 — cero cambios en la Compose

Con la VM en la tailnet, el panel se alcanza desde cualquier dispositivo de
la tailnet **con el mismo puerto del frontend, sin tocar `compose.yaml`
(literal §6.8)**:

```
http://<IP-de-tailnet-de-la-VM>:8080
```

Por qué funciona sin cambios: el frontend ya publica `8080:80` en la Compose y
el propio nginx del frontend enruta `/api/v1/**` + `/ws/logs` al backend
(TRD §6.2 y §9); nada depende del origen de la conexión (IP LAN o IP de
tailnet) salvo el CORS, que se trata en la sección 4. PostgreSQL y backend no publican puertos
(TRD §6.7) y siguen sin existir fuera de la red de Compose.

### 3.3 Firewall: la regla 8080-solo-LAN NO basta fuera del túnel

- El tráfico **Tailscale SSH/tunnel observado en FB-0** (la ruta de gestión
  vía `root@100.64.3.17`) opera a nivel de kernel, **fuera** de la chain
  nftables LAN del GATE #11: por eso SSH funciona hoy desde la tailnet sin
  regla LAN.
- Un **navegador** que pida `http://<tailnet-IP>:8080` directamente NO va por
  ese túnel: llega una conexión TCP nueva con origen en el rango de tailnet
  (`100.64.0.0/10` sobre la interfaz `tailscale0`), que la `policy drop` del
  GATE #11 descartaría — la regla 8080 existente solo acepta origen
  `192.168.1.0/24`.
- **Regla 8080 por tailnet SOLO si el usuario la pide**: en ese caso, añadir
  antes de cualquier cambio una regla scoped a la tabla existente (patrón
  boot-safe del C9: tabla pre-declarada antes del `flush` scoped, `nft -c -f`
  antes de aplicar), es decir, un `accept` para `tcp dport 8080` con `ip
  saddr 100.64.0.0/10` (o `iifname "tailscale0"`) en `table inet pl_vm`,
  **jamás** `flush ruleset` global (arrasaría las cadenas iptables-nft de
  Docker — incidente documentado en FB-4 del C9).

Alternativa sin tocar el firewall: encapsular el panel por el túnel SSH
existente (port-forward: `ssh -J root@100.64.3.17 -L 8080:localhost:8080
root@192.168.1.58` y usar `http://localhost:8080` en el navegador local),
con el mismo efecto de acceso y cero reglas nuevas. El CORS de ese origen
es el de hoy (`http://localhost:8080`, ya permitido).

## 4. CORS-LAN — condicionada (a+), pendiente de ratificación del usuario

**Estado VERDADERO HOY (condicionada — NO implementada en la VM):** accediendo
al panel por `http://192.168.1.58:8080` desde la LAN, el HTML del frontend
sirve, pero las llamadas a la API y al WebSocket (`/ws/logs`) responden **403**
por CORS: el `Origin` visto por el backend es `http://192.168.1.58:8080` y el
`.env` de la VM contiene solo `FRONTEND_ORIGIN=http://localhost:8080`
(estado LATENTE, REQ-C10-CORS-5). Lo mismo aplicaría a un origen de tailnet.

**Lo que YA soporta el backend tras el corte C10 (código aplicado, VM
intocable):** `CorsConfig` divide `FRONTEND_ORIGIN` por comas y llena
`setAllowedOrigins` con la lista (G1 activado LATENTE). Prohibiciones vigentes
(G1-C2): nunca `*` como origin, nunca `setAllowedOriginPatterns`, nunca una
env var nueva — la misma `FRONTEND_ORIGIN` con semántica ampliada.

**Paso de deploy futuro EXACTO — PENDIENTE DE RATIFICACIÓN DEL USUARIO (no
ejecutar sin su aprobación):**

```bash
# Solo si el usuario RATIFICA la variante (a+) de G1:
# 1. En el .env de la VM 108, la variable pasa de un origen a la lista:
#    FRONTEND_ORIGIN=http://localhost:8080,http://192.168.1.58:8080
# 2. Recrear solo el backend con la nueva env:
docker compose up -d backend
# 3. Verificar: docker compose ps (backend healthy) y login + /ws/logs
#    desde http://192.168.1.58:8080 sin 403.
```

Este paso **no está hecho** y no forma parte del estado de la VM: el corte C10
deja la VM 108 y su `.env` intocables (dif-0). Si el usuario no ratifica, el
comportamiento sigue siendo el de hoy (403 desde la IP LAN / tailnet y OK
desde `localhost`) y este doc no cambia. Si un día se accede por IP de
tailnet, la lista admitiría añadir también ese origen por el mismo mecanismo,
siempre con ratificación previa.

## 5. Proxy inverso con HTTPS (P2) — SIN implementar

Literal TRD §6.8: «**Proxy inverso con HTTPS** (P2): si hay dominio futuro, se
añade el proxy con Let's Encrypt delante (o se configura TLS en el propio
nginx) — la estructura actual (1 proxy + servicios internos + WS detrás) ya es
la del diseño inverso; nada del MVP se rompe.»

Estado: **documentado, SIN implementar en el MVP** — no hay dominio, ni DNS
público, ni TLS (TRD §6.7), y esta sección no introduce ninguno.

Estructura ya prevista (lo que el futuro proxy heredaría):

- **1 proxy nginx delante** terminando TLS (Let's Encrypt delante, o TLS en el
  propio nginx del frontend) + servicios internos detrás (postgres y backend
  sin puertos publicados, como hoy, TRD §6.7).
- **`/ws/logs` detrás del proxy** con las cabeceras de upgrade y los timeouts
  que el WebSocket exige (de otro modo los sockets morirían); referencia
  exacta ya en producción: `frontend/nginx.conf` —
  `proxy_http_version 1.1` + `Upgrade`/`Connection "upgrade"` +
  `proxy_read_timeout 3600s` + `proxy_send_timeout 3600s`.
- El proxy del futuro frente rutea solo `/api/v1/**` + `/ws/logs` (TRD §9);
  para el resto, SPA fallback como hoy.

Nada del MVP se rompe con esa incorporación (literal §6.8): el corte C10 no
toca `frontend/nginx.conf` (dif-0) ni la Compose.

## 6. Referencias

| Fuente | Qué aporta |
|---|---|
| `docs/02_TRD.md` §2.3 | Modelo de datos: `logs` y la FK `project_id` con `ON DELETE CASCADE` (contexto de integridad de la pila a la que se accede) |
| `docs/02_TRD.md` §2.5 | Borrado previsto (por proyecto y registro a registro con `X-Confirm-*`) — operaciones sensibles behind del panel remoto |
| `docs/02_TRD.md` §6.7 | Seguridad de red del MVP: 1 puerto TCP expuesto, sin dominio/DNS/TLS, decisión GATE #11 (reglas de firewall) |
| `docs/02_TRD.md` §6.8 | Literales P1 (Tailscale, cero cambios en la Compose) y P2 (proxy inverso HTTPS) que este doc desarrolla |
| `docs/README.md` | Índice congelado del paquete de definición (7/7 validados) — este doc es operativo, fuera del paquete, y no altera el índice (D1 de C10) |
| `compose.yaml` | Definición de la pila (3 servicios, `8080:80` en frontend, backend/postgres sin `ports:`) — NO se modifica para el acceso remoto |
| `frontend/nginx.conf` | Referencia exacta de upgrade headers + timeouts 3600 s de `/ws/logs` (sección 5) |
| `.atl/sdd/C9/state.md` (FB-0) | Estado de partida verificado: VM 108 «projectlog», Debian 12.15, eth0 192.168.1.58/24, cadena ProxyJump `root@100.64.3.17` |
| `.atl/sdd/C9/qa-report.md` / GATE #11 | Firewall nftables aplicado y verificado (SSH-LAN + 8080, solo LAN), boot-safe en frío y reinicio |
| `.atl/sdd/C10/spec.md` (REQ-C10-CORS-5, REQ-C10-DOC-1..4) | Condición (a+) latente y trazabilidad de este doc |

## Trazado de requisitos (C10)

| REQ C10 | Sección de este doc |
|---|---|
| REQ-C10-DOC-1 (operativa Tailscale P1 documentada) | §2 (estado FB-0 + ProxyJump), §3 (instalación, IP de tailnet, mismo puerto 8080 con cero cambios en la Compose, firewall GATE #11) |
| REQ-C10-DOC-2 (TLS P2 documentado SIN implementar) | §5 (estructura prevista, upgrade headers + timeouts 3600 s, Let's Encrypt/nginx TLS, SIN implementar) |
| REQ-C10-DOC-3 (verdad condicionada y paso pendiente) | §4 (403 hoy por `192.168.1.58:8080` en `/ws` y API; backend ya soporta lista por coma; paso de deploy futuro EXACTO marcado «pendiente de ratificación») |
| REQ-C10-DOC-4 (README índice intacto, D1) | — (dif-0 garantizado: `docs/README.md` no se edita; el doc entra en `/docs` por la primera rama del done-when) |

---
*Doc operativo creado en el corte SDD C10 (T10.26, D-C10-14). Sin ejecución
en la VM: todo lo que exige cambios en la VM (regla de firewall por tailnet,
CORS (a+), proxy P2) está marcado como condicionado/pendiente/sin implementar.
No contiene valores de `.env`, credenciales, tokens ni claves.*