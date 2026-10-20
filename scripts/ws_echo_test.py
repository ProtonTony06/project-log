#!/usr/bin/env python3
"""
ws_echo_test.py — smoke del WebSocket /ws/logs detrás del nginx (C1, DD-7).

Cliente WebSocket RFC 6455 mínimo, Python stdlib SOLO (socket, base64,
hashlib, os, struct, argparse, sys, time). Se ejecuta SIEMPRE contra la
pila completa levantada con `docker compose up`, nunca en dev.

Pasos (modo completo):
  1. Handshake: GET /ws/logs + cabeceras de upgrade; exige 101 Switching
     Protocols y Sec-WebSocket-Accept == base64(sha1(key + GUID RFC 6455)).
  2. Eco full-duplex: N frames de texto MASCARADOS (obligación RFC del
     cliente); exige eco idéntico byte a byte; cierre limpio 0x8.
  3. Inactividad: sin enviar nada durante --idle-segundos (default 75) y
     luego un frame de comprobación → la conexión sobrevive (con el default
     60 s del nginx moriría; los 3600 s del TRD §6.2.3 viven).

Modos: --solo-handshake (paso 1; reutilizable en C7 con --token), y
--sin-inactividad (salta el paso 3). El --token existe como CLI pero en C1
NUNCA se pasa (en C7 añade ?token=… al path del handshake).
"""

import argparse
import base64
import hashlib
import os
import socket
import struct
import sys
import time

WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"


def fail(mensaje: str) -> None:
    print(f"[FAIL] {mensaje}")
    sys.exit(1)


def ok(mensaje: str) -> None:
    print(f"[OK] {mensaje}")


def recv_exacto(sock: socket.socket, n: int) -> bytes:
    datos = b""
    while len(datos) < n:
        trozo = sock.recv(n - len(datos))
        if not trozo:
            raise ConnectionError("conexión cerrada por el servidor")
        datos += trozo
    return datos


def handshake(host: str, puerto: int, token: str | None) -> socket.socket:
    """Paso 1: handshake RFC 6455 a pelo y validación del Accept."""
    path = "/ws/logs"
    if token:
        path += f"?token={token}"  # en C1 NUNCA se usa; es el punto de C7

    key = base64.b64encode(os.urandom(16)).decode()
    peticion = (
        f"GET {path} HTTP/1.1\r\n"
        f"Host: {host}:{puerto}\r\n"
        "Upgrade: websocket\r\n"
        "Connection: Upgrade\r\n"
        f"Sec-WebSocket-Key: {key}\r\n"
        "Sec-WebSocket-Version: 13\r\n"
        "\r\n"
    )
    sock = socket.create_connection((host, puerto), timeout=15)
    sock.sendall(peticion.encode())

    respuesta = b""
    while b"\r\n\r\n" not in respuesta:
        trozo = sock.recv(4096)
        if not trozo:
            fail("el servidor cerró la conexión durante el handshake")
        respuesta += trozo
    cabeceras, _ = respuesta.split(b"\r\n\r\n", 1) if b"\r\n\r\n" in respuesta else (respuesta, b"")
    lineas = cabeceras.decode("utf-8", "replace").split("\r\n")
    status = lineas[0] if lineas else ""

    accept_esperado = base64.b64encode(
        hashlib.sha1((key + WS_GUID).encode()).digest()
    ).decode()

    def cabecera(nombre: str) -> str | None:
        for linea in lineas[1:]:
            if ":" in linea:
                k, v = linea.split(":", 1)
                if k.strip().lower() == nombre.lower():
                    return v.strip()
        return None

    if "101" not in status:
        fail(f"handshake: status inesperado «{status}» (se esperaba 101 Switching Protocols)")
    accept = cabecera("Sec-WebSocket-Accept")
    if accept != accept_esperado:
        fail(
            f"Sec-WebSocket-Accept incorrecto: «{accept}» ≠ esperado «{accept_esperado}»"
        )
    ok(f"Handshake: {status.strip()} · Sec-WebSocket-Accept válido (negociado por el nginx → backend)")
    return sock


def frame_mascarado(opcode: int, payload: bytes) -> bytes:
    """Frame de CLIENTE: siempre MASCARADO (obligación RFC 6455), FIN=1."""
    mascara = os.urandom(4)
    fin_opcode = 0x80 | opcode
    longitud = len(payload)
    if longitud < 126:
        cab = struct.pack("!BB", fin_opcode, 0x80 | longitud)
    elif longitud < 65536:
        cab = struct.pack("!BBH", fin_opcode, 0x80 | 126, longitud)
    else:
        cab = struct.pack("!BBQ", fin_opcode, 0x80 | 127, longitud)
    enmascarado = bytes(b ^ mascara[i % 4] for i, b in enumerate(payload))
    return cab + mascara + enmascarado


def parse_frame(sock: socket.socket) -> tuple[int, bytes]:
    """Parsea (opcode, payload) de un frame del servidor (sin máscara)."""
    cab = recv_exacto(sock, 2)
    fin, opcode = cab[0] & 0x80, cab[0] & 0x0F
    mascarado = cab[1] & 0x80
    largo = cab[1] & 0x7F
    if largo == 126:
        largo = struct.unpack("!H", recv_exacto(sock, 2))[0]
    elif largo == 127:
        largo = struct.unpack("!Q", recv_exacto(sock, 8))[0]
    if mascarado:
        mascara = recv_exacto(sock, 4)
        enm = recv_exacto(sock, largo)
        payload = bytes(b ^ mascara[i % 4] for i, b in enumerate(enm))
    else:
        payload = recv_exacto(sock, largo)
    if not fin:
        raise ValueError("frame fragmentado: el protocolo del smoke no lo usa")
    return opcode, payload


def enviar_texto(sock: socket.socket, texto: str) -> None:
    sock.sendall(frame_mascarado(0x1, texto.encode()))


def eco(sock: socket.socket, texto: str) -> None:
    """Envía un frame de texto y exige el eco idéntico byte a byte."""
    enviar_texto(sock, texto)
    opcode, payload = parse_frame(sock)
    if opcode != 0x1:
        fail(f"eco: opcode inesperado 0x{opcode:x} (se esperaba texto 0x1)")
    if payload.decode() != texto:
        fail(f"eco: payload distinto «{payload!r:.80}» ≠ «{texto!r:.80}»")


def cierre_limpio(sock: socket.socket) -> None:
    """Paso 2 (final): close frame 0x8 y espera el close de vuelta."""
    sock.sendall(frame_mascarado(0x8, struct.pack("!H", 1000)))
    fin = time.time() + 10
    sock.settimeout(10)
    while time.time() < fin:
        try:
            opcode, _ = parse_frame(sock)
        except ConnectionError:
            ok("Cierre: el servidor cerró tras el close frame (cierre limpio)")
            return
        if opcode == 0x8:
            ok("Cierre limpio: close frame 0x8 de vuelta del servidor")
            return
    fail("cierre: sin close de vuelta en 10 s")


def paso_inactividad(host: str, puerto: int, idle_segundos: int) -> None:
    """Paso 3: conexión abierta sin enviar nada durante idle_segundos y luego eco."""
    sock = handshake(host, puerto, None)
    ok(f"Inactividad: conexión abierta; sin enviar nada durante {idle_segundos} s...")
    for restante in range(idle_segundos, 0, -_marca):
        print(f"       ...{restante} s", flush=True)
        time.sleep(min(_marca, restante))
    try:
        eco(sock, f"inactividad {idle_segundos}s — conexión viva")
        ok(f"Inactividad > {idle_segundos} s: la conexión SIGUE viva (timeout nginx ≠ 60 s default; 3600s en la location /ws/logs)")
    except (ConnectionError, socket.timeout, ValueError, OSError) as exc:
        fail(f"inactividad: la conexión MURIÓ durante los {idle_segundos} s de silencio ({exc})")
    sock.close()


_marca = 5  # granularidad del contador de inactividad (segundos)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Smoke WS del proxy nginx → backend (echo en /ws/logs)"
    )
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--puerto", type=int, default=8080)
    parser.add_argument("--solo-handshake", action="store_true",
                        help="solo valida 101 + Sec-WebSocket-Accept (reutilizable en C7)")
    parser.add_argument("--sin-inactividad", action="store_true",
                        help="salta el paso 3 (inactividad > 75 s)")
    parser.add_argument("--idle-segundos", type=int, default=75,
                        help="segundos de silencio del paso 3 (default 75)")
    parser.add_argument("--payload", default="project_log-echo-你好 áé 123",
                        help="payload del frame de prueba")
    parser.add_argument("--token", default=None,
                        help="(solo C7) JWT en ?token=… del handshake; en C1 no usar")
    args = parser.parse_args()

    sock = handshake(args.host, args.puerto, args.token)
    if args.solo_handshake:
        sock.close()
        ok("Modo --solo-handshake: terminado (infraestructura WS OK)")
        return

    # Paso 2: eco full-duplex, frames mascarados, idénticos byte a byte
    for n in range(1, 4):
        payload = f"{args.payload} · frame {n}"
        eco(sock, payload)
    ok("Eco full-duplex: 3 frames de texto con eco idéntico byte a byte")
    cierre_limpio(sock)

    if args.sin_inactividad:
        ok("Modo --sin-inactividad: terminado")
        return
    paso_inactividad(args.host, args.puerto, args.idle_segundos)
    print("[OK] SMOKE COMPLETO: handshake 101 · eco full-duplex · inactividad > "
          f"{args.idle_segundos} s — pila WS verde")


if __name__ == "__main__":
    main()