package cl.adndigital.mejorvejez.reportes.core;

import java.util.List;

/**
 * Respuesta de {@code POST /api/interno/reportes/envios}: idempotente por
 * {@code (idGrupo, inicioPeriodo)}. {@code pendientes} trae los
 * destinatarios de la foto que todavía no están {@code ENVIADO}; viene
 * vacía cuando el envío ya quedó {@code ENVIADO} o {@code FALLIDO} (con 3
 * intentos).
 */
public record RespuestaReservaEnvio(long idEnvio, String estado, int intentos, List<Destinatario> pendientes) {
}
