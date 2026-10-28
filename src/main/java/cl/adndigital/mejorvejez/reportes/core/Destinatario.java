package cl.adndigital.mejorvejez.reportes.core;

/**
 * Un destinatario del reporte semanal: parte de {@link
 * GrupoReporte#destinatarios} y de {@link RespuestaReservaEnvio#pendientes}.
 * {@code correo} es siempre el de la cuenta.
 */
public record Destinatario(Long idUsuario, String nombre, String correo) {
}
