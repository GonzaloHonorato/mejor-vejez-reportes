package cl.adndigital.mejorvejez.reportes.alertas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.adndigital.mejorvejez.reportes.comun.FiltroClaveServicio;

/**
 * Prueba {@link ClienteAlertas#enviarReporte} contra un servidor simulado:
 * la ruta, el método, la cabecera de la clave de servicio y el cuerpo.
 */
class ClienteAlertasTest {

    private static final String CLAVE = "clave-de-prueba";

    private MockWebServer alertas;
    private ClienteAlertas cliente;

    @BeforeEach
    void iniciar() throws Exception {
        alertas = new MockWebServer();
        alertas.start();
        cliente = new ClienteAlertas(alertas.url("/").toString(), CLAVE);
    }

    @AfterEach
    void cerrar() throws Exception {
        alertas.close();
    }

    @Test
    void enviarReporte_envíaElCuerpoConCabeceraYDevuelveLosResultados() throws Exception {
        alertas.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("{\"resultados\":[{\"idUsuario\":10,\"enviado\":true,\"detalleError\":null}]}").build());

        SolicitudEnvioReporte solicitud = new SolicitudEnvioReporte("REP-SEM-1-20261102", "Asunto",
                "Cuerpo", "reporte-semanal-2026-11-02.pdf", "cG9ydGFkYS1kZS1wcnVlYmE=",
                List.of(new Destinatario(10L, "Ana", "ana@example.com")));

        RespuestaEnvioReporte respuesta = cliente.enviarReporte(solicitud);

        RecordedRequest solicitudRecibida = alertas.takeRequest();
        assertThat(solicitudRecibida.getMethod()).isEqualTo("POST");
        assertThat(solicitudRecibida.getTarget()).isEqualTo("/api/interno/envios-reporte");
        assertThat(solicitudRecibida.getHeaders().get(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO)).isEqualTo(CLAVE);
        assertThat(solicitudRecibida.getBody().utf8()).contains("\"claveEnvio\":\"REP-SEM-1-20261102\"")
                .contains("\"idUsuario\":10");
        assertThat(respuesta.resultados()).hasSize(1);
        assertThat(respuesta.resultados().get(0).enviado()).isTrue();
    }
}
