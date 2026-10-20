/**
 * Feature de streaming (C7): {@code LogSocketHandler} real en /ws/logs con
 * HandshakeInterceptor (JWT 401 pre-upgrade), emisión tras COMMIT, heartbeat.
 *
 * <p>En C1 este paquete solo contiene {@code EchoSocketHandler},
 * infraestructura temporal de prueba del proxy WS (eco), a SUSTITUIR por
 * {@code LogSocketHandler} en C7 — el registro en /ws/logs y el smoke
 * scripts/ws_echo_test.py no cambian.</p>
 */
package com.projectlog.streaming;