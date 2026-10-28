package cl.adndigital.mejorvejez.reportes.alertas;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.adndigital.mejorvejez.reportes.comun.FiltroClaveServicio;

/**
 * Cliente REST del microservicio de alertas y notificaciones: entrega el
 * correo del reporte semanal con el PDF adjunto. El tiempo de conexión es
 * de 10 segundos, igual que {@code ClienteCore}; el de lectura es de 60
 * segundos porque Alertas manda un correo por destinatario antes de
 * responder.
 */
@Component
public class ClienteAlertas {

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(10);
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(60);

    private static final String RUTA_ENVIOS_REPORTE = "/api/interno/envios-reporte";

    private final RestClient restClient;

    public ClienteAlertas(@Value("${mejorvejez.alertas-url}") String urlAlertas,
            @Value("${mejorvejez.interno.clave-servicio}") String claveServicio) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT_CONEXION);
        fabrica.setReadTimeout(TIMEOUT_LECTURA);
        this.restClient = RestClient.builder()
                .baseUrl(urlAlertas)
                .requestFactory(fabrica)
                .defaultHeader(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, claveServicio)
                .build();
    }

    /** Entrega el correo del reporte semanal (PDF adjunto) a cada destinatario de la solicitud. */
    public RespuestaEnvioReporte enviarReporte(SolicitudEnvioReporte solicitud) {
        return restClient.post()
                .uri(RUTA_ENVIOS_REPORTE)
                .body(solicitud)
                .retrieve()
                .body(RespuestaEnvioReporte.class);
    }
}
