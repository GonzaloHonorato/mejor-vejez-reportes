package cl.adndigital.mejorvejez.reportes.core;

import java.util.List;

/**
 * Cuerpo de {@code PUT /api/interno/reportes/envios/{idEnvio}/resultado}:
 * este microservicio informa al core el resultado del envío a cada
 * destinatario de la foto, luego de que Alertas intenta enviarlo.
 */
public record SolicitudResultadoEnvio(List<ResultadoDestinatario> resultados) {
}
