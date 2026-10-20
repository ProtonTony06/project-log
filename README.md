# Project_Log — logger de homelab (C1 · Fase 0: setup de pila)

Pila Docker Compose de 3 servicios (frontend nginx → backend Spring Boot →
PostgreSQL 17). Este corte C1 levanta la infraestructura sin lógica de negocio.

## Requisitos

- **Docker Engine con plugin Compose v2** (`docker compose version`).
- `openssl`, `python3` (solo para el smoke; la pila no los necesita).

## Arranque

```bash
cp .env.example .env && chmod 600 .env   # .env NUNCA se sube a git
# Editar .env: generar los secretos (ver comentarios del .env.example):
#   JWT_SECRET       → openssl rand -base64 48
#   POSTGRES_PASSWORD→ openssl rand -base64 24
docker compose up -d
```

El **único puerto del host es 8080** (frontend). Comprobación:
`curl http://localhost:8080/` → placeholder "Project_Log — pila OK".

## Smoke del WebSocket (echo)

```bash
python3 scripts/ws_echo_test.py                  # modo completo (handshake + eco + inactividad 75 s)
python3 scripts/ws_echo_test.py --sin-inactividad # iteraciones rápidas
python3 scripts/ws_echo_test.py --solo-handshake  # solo 101 + Accept
```

Se ejecuta SIEMPRE contra la pila levantada (`docker compose up`), nunca en
dev. Un handshake manual reproducible:
`curl -i http://localhost:8080/ws/logs -H "Connection: Upgrade" -H "Upgrade: websocket" -H "Sec-WebSocket-Key: <base64>" -H "Sec-WebSocket-Version: 13"`.

## Proxy API (comportamiento de C1: sin endpoints de negocio)

```bash
curl -H "Accept: application/json" http://localhost:8080/api/v1/nonexistent
```

→ 404 JSON de Spring (el proxy alcanza al backend). El header
`Accept: application/json` es obligatorio: sin él la respuesta puede salir
HTML y no distingue el proxy de la SPA.

## Diagnóstico: fail-fast de secretos

Si la pila no arranca: `docker compose logs backend`. Con un secreto ausente
o en blanco el backend aborta con **un único error** que lista TODOS los
secretos ausentes, antes de abrir el puerto. Con `restart: unless-stopped` el
contenedor entra en bucle de reinicio mientras falte el secreto: es el
comportamiento esperado (no desactivar el restart). Restaurar el `.env` y
relanzar `docker compose up -d`.

## Reinicio de la base de datos (SOLO local)

Procedimiento de Flyway en frío (nunca en la VM):

```bash
docker compose down                       # sin -v por defecto; -v prohibido en la VM
docker volume rm project_log_pgdata       # SOLO local: borra los datos
docker compose up -d
```

Los logs del backend deben mostrar `Successfully applied 1 migration`
(`V1__init.sql` de Flyway).

## Actualización de versiones

Manual: `docker compose build --pull && docker compose up -d`. Sin `latest`:
todos los tags de imagen son fijos.