package com.projectlog.auth;

/**
 * Entrada del login (N8, CD-6): record {username, password}. SIN anotaciones
 * de validación (S17): cualquier campo nulo o cuerpo malformado produce el
 * MISMO 401 {@code invalid_credentials} — la semántica de error es siempre
 * idéntica para no apoyar enumeración.
 */
public record LoginRequest(String username, String password) {
}