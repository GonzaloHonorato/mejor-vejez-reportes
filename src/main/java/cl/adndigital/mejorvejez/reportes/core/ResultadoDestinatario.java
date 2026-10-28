package cl.adndigital.mejorvejez.reportes.core;

/**
 * El resultado del envío a un destinatario, parte de {@link
 * SolicitudResultadoEnvio}. {@code detalleError} nunca incluye direcciones
 * de correo.
 */
public record ResultadoDestinatario(long idUsuario, boolean enviado, String detalleError) {
}
