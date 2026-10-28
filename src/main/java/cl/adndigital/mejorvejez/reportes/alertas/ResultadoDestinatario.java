package cl.adndigital.mejorvejez.reportes.alertas;

/**
 * El resultado del envío del reporte semanal a un destinatario, parte de
 * {@link RespuestaEnvioReporte}. {@code detalleError} nunca incluye
 * direcciones de correo.
 */
public record ResultadoDestinatario(long idUsuario, boolean enviado, String detalleError) {
}
