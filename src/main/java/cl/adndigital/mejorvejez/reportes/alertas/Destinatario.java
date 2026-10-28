package cl.adndigital.mejorvejez.reportes.alertas;

/** Un destinatario del correo del reporte semanal, parte de {@link SolicitudEnvioReporte}. */
public record Destinatario(Long idUsuario, String nombre, String correo) {
}
