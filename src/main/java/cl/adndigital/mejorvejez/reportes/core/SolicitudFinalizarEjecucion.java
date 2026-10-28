package cl.adndigital.mejorvejez.reportes.core;

/**
 * Cuerpo de {@code PUT /api/interno/ejecuciones/{idEjecucion}}: cierra la
 * corrida con su estado final ({@code EXITOSA} | {@code PARCIAL} | {@code
 * FALLIDA}) y sus totales.
 */
public record SolicitudFinalizarEjecucion(String codigoEstado, int creadas, int errores, String resumen) {
}
