package cl.adndigital.mejorvejez.reportes.core;

import java.time.OffsetDateTime;

/**
 * Cuerpo de {@code POST /api/interno/ejecuciones}: este microservicio
 * registra el inicio de una corrida propia ({@code tipoEjecucion}, por
 * ejemplo {@code REPORTE_SEMANAL}), con su ventana.
 */
public record SolicitudIniciarEjecucion(String tipoEjecucion, OffsetDateTime inicioVentana,
        OffsetDateTime finVentana) {
}
