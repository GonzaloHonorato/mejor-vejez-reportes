package cl.adndigital.mejorvejez.reportes.core;

import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/interno/reportes/envios}: {@code
 * inicioPeriodo} debe ser un lunes (el inicio del período).
 */
public record SolicitudReservarEnvio(long idGrupo, LocalDate inicioPeriodo) {
}
