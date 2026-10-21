package com.projectlog.projects.dto;

/**
 * Cuerpo de entrada de {@code POST /api/v1/panel/projects} (R3.14, CD-3.1):
 * binding Jackson estándar del record — la compilación va con
 * {@code -parameters} (parent de Boot), igual que {@code LoginRequest} (C2).
 *
 * <p>En POST no hay semántica parcial: {@code description} ausente y
 * {@code description: null} convergen en {@code null} (misma columna NULL en
 * BD; R3.14/E3.2).</p>
 */
public record CreateProjectRequest(String name, String description) {
}