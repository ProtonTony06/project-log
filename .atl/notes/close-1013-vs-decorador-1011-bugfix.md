---
title: "Backpressure: cerrar al DELEGADO (1013) o el decorador reescribe a 1011"
type: bugfix
what: "ConcurrentWebSocketSessionDecorator al superarse sendTime/buffer marca la sesión no fiable y su propio close() reescribe el CloseStatus a SESSION_NOT_RELIABLE (1011); para materializar el 1013 contractual hay que invocar session.close(CloseStatus.SESSION_NOT_RELIABLE) sobre WebSocketSessionDecorator.getDelegate(session) (la sesión envuelta, no el decorador)."
why: "El aserto E7.13 [L] exigía exactamente 1013 en la pareja de test; cerrando por el decorador el code capturado era 1011 (reasignación interna de checkSessionLimits), lo que lo ponía rojo sin ningún bug en la lógica de límites."
where: "backend/src/main/java/com/projectlog/streaming/LogSocketHandler.java (método cerrarPorLentitud); pareja [L] en backend/src/test/java/com/projectlog/streaming/LatidoYBackpressureTest.java"
learned: "Cuando un decorador de Spring WebSocket captura el cierre por límites, el status final es del DECORADOR (1011); para preservar un CloseStatus específico hay que cerrar sobre la sesión delegada. Verificado contra spring-websocket 6.2.19 (ConcurrentWebSocketSessionDecorator#checkSessionLimits y #limitExceeded). Comportamiento estable y determinista en tests con doble barrera (límite de buffer + límite de tiempo)."
---