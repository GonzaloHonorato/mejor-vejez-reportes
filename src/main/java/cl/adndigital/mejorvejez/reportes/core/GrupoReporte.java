package cl.adndigital.mejorvejez.reportes.core;

import java.util.List;

/**
 * Respuesta de {@code GET /api/interno/reportes/grupos}: un grupo {@code
 * ACTIVO} con sus destinatarios vigentes (integrante {@code ACTIVO} con
 * cuenta {@code ACTIVO}).
 */
public record GrupoReporte(long idGrupo, String nombreGrupo, List<Destinatario> destinatarios) {
}
