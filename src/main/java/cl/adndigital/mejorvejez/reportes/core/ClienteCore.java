package cl.adndigital.mejorvejez.reportes.core;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.adndigital.mejorvejez.reportes.comun.FiltroClaveServicio;

/**
 * Cliente REST del servicio core: todas las llamadas a {@code
 * /api/interno/**} que necesita este microservicio, con la cabecera de la
 * clave de servicio y un tiempo máximo de 10 segundos para conectar y para
 * leer la respuesta.
 */
@Component
public class ClienteCore {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String RUTA_GRUPOS = "/api/interno/reportes/grupos";
    private static final String RUTA_SEMANA = "/api/interno/reportes/grupos/{idGrupo}/semana";
    private static final String RUTA_ENVIOS = "/api/interno/reportes/envios";
    private static final String RUTA_RESULTADO_ENVIO = "/api/interno/reportes/envios/{idEnvio}/resultado";
    private static final String RUTA_EJECUCIONES = "/api/interno/ejecuciones";
    private static final String RUTA_EJECUCION = "/api/interno/ejecuciones/{idEjecucion}";

    private final RestClient restClient;

    public ClienteCore(@Value("${mejorvejez.core-url}") String urlCore,
            @Value("${mejorvejez.interno.clave-servicio}") String claveServicio) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT);
        fabrica.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(urlCore)
                .requestFactory(fabrica)
                .defaultHeader(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, claveServicio)
                .build();
    }

    /** Los grupos ACTIVO con sus destinatarios vigentes. */
    public List<GrupoReporte> grupos() {
        return restClient.get()
                .uri(RUTA_GRUPOS)
                .retrieve()
                .body(new ParameterizedTypeReference<List<GrupoReporte>>() {
                });
    }

    /** Los datos de la semana de un grupo, reconstruidos al cierre del período. */
    public DatosSemana semana(long idGrupo, LocalDate inicio) {
        return restClient.get()
                .uri(constructor -> constructor.path(RUTA_SEMANA)
                        .queryParam("inicio", inicio)
                        .build(idGrupo))
                .retrieve()
                .body(DatosSemana.class);
    }

    /** Reserva (o recupera) el envío del reporte semanal de un grupo; idempotente por (idGrupo, inicioPeriodo). */
    public RespuestaReservaEnvio reservarEnvio(SolicitudReservarEnvio solicitud) {
        return restClient.post()
                .uri(RUTA_ENVIOS)
                .body(solicitud)
                .retrieve()
                .body(RespuestaReservaEnvio.class);
    }

    /** Entrega el resultado del envío a cada destinatario de la foto. */
    public RespuestaResultadoEnvio registrarResultadoEnvio(long idEnvio, SolicitudResultadoEnvio solicitud) {
        return restClient.put()
                .uri(RUTA_RESULTADO_ENVIO, idEnvio)
                .body(solicitud)
                .retrieve()
                .body(RespuestaResultadoEnvio.class);
    }

    /** Registra el inicio de una corrida propia y devuelve su id. */
    public long iniciarEjecucion(String tipoEjecucion, OffsetDateTime inicioVentana, OffsetDateTime finVentana) {
        RespuestaIdEjecucion respuesta = restClient.post()
                .uri(RUTA_EJECUCIONES)
                .body(new SolicitudIniciarEjecucion(tipoEjecucion, inicioVentana, finVentana))
                .retrieve()
                .body(RespuestaIdEjecucion.class);
        return respuesta.idEjecucion();
    }

    /** Cierra una corrida ya iniciada con su estado final y sus totales. */
    public void finalizarEjecucion(long idEjecucion, String codigoEstado, int creadas, int errores, String resumen) {
        restClient.put()
                .uri(RUTA_EJECUCION, idEjecucion)
                .body(new SolicitudFinalizarEjecucion(codigoEstado, creadas, errores, resumen))
                .retrieve()
                .toBodilessEntity();
    }
}
