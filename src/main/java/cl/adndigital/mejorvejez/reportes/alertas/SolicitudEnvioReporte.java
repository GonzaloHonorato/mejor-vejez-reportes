package cl.adndigital.mejorvejez.reportes.alertas;

import java.util.List;

/**
 * Cuerpo de {@code POST /api/interno/envios-reporte}: el correo del reporte
 * semanal, con su PDF en base64. {@code claveEnvio} es la clave de
 * idempotencia del envío ({@code REP-SEM-{idGrupo}-{yyyyMMdd}}).
 */
public record SolicitudEnvioReporte(String claveEnvio, String asunto, String cuerpo, String nombreArchivo,
        String pdfBase64, List<Destinatario> destinatarios) {
}
