package cl.adndigital.mejorvejez.reportes.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cl.adndigital.mejorvejez.reportes.comun.FiltroClaveServicio;

/**
 * Prueba cada ruta de {@link ClienteCore} contra un servidor simulado: la
 * ruta, el método, la cabecera de la clave de servicio y, según el caso,
 * los parámetros de consulta o el cuerpo.
 */
class ClienteCoreTest {

    private static final String CLAVE = "clave-de-prueba";

    private MockWebServer core;
    private ClienteCore cliente;

    @BeforeEach
    void iniciar() throws Exception {
        core = new MockWebServer();
        core.start();
        cliente = new ClienteCore(core.url("/").toString(), CLAVE);
    }

    @AfterEach
    void cerrar() throws Exception {
        core.close();
    }

    @Test
    void grupos_consultaRutaConCabeceraDeClave() throws Exception {
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("[{\"idGrupo\":1,\"nombreGrupo\":\"Familia Pérez\",\"destinatarios\":"
                        + "[{\"idUsuario\":10,\"nombre\":\"Ana\",\"correo\":\"ana@example.com\"}]}]")
                .build());

        List<GrupoReporte> grupos = cliente.grupos();

        RecordedRequest solicitud = core.takeRequest();
        assertThat(solicitud.getMethod()).isEqualTo("GET");
        assertThat(solicitud.getTarget()).isEqualTo("/api/interno/reportes/grupos");
        assertThat(solicitud.getHeaders().get(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO)).isEqualTo(CLAVE);
        assertThat(grupos).hasSize(1);
        assertThat(grupos.get(0).nombreGrupo()).isEqualTo("Familia Pérez");
        assertThat(grupos.get(0).destinatarios().get(0).correo()).isEqualTo("ana@example.com");
    }

    @Test
    void semana_consultaRutaConIdDeGrupoYFechaDeInicio() throws Exception {
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("{\"idGrupo\":7,\"nombreGrupo\":\"Familia Soto\",\"inicio\":\"2026-11-02\","
                        + "\"fin\":\"2026-11-08\",\"adultos\":[],\"tareas\":[],\"relevos\":[],\"alertas\":[]}")
                .build());

        DatosSemana semana = cliente.semana(7, LocalDate.of(2026, 11, 2));

        RecordedRequest solicitud = core.takeRequest();
        assertThat(solicitud.getMethod()).isEqualTo("GET");
        assertThat(solicitud.getTarget()).isEqualTo("/api/interno/reportes/grupos/7/semana?inicio=2026-11-02");
        assertThat(semana.idGrupo()).isEqualTo(7);
        assertThat(semana.inicio()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(semana.fin()).isEqualTo(LocalDate.of(2026, 11, 8));
    }

    @Test
    void reservarEnvio_envíaElCuerpoYDevuelveLaReserva() throws Exception {
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("{\"idEnvio\":3,\"estado\":\"PENDIENTE\",\"intentos\":0,\"pendientes\":[]}").build());

        RespuestaReservaEnvio respuesta = cliente
                .reservarEnvio(new SolicitudReservarEnvio(7, LocalDate.of(2026, 11, 2)));

        RecordedRequest solicitud = core.takeRequest();
        assertThat(solicitud.getMethod()).isEqualTo("POST");
        assertThat(solicitud.getTarget()).isEqualTo("/api/interno/reportes/envios");
        assertThat(solicitud.getHeaders().get(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO)).isEqualTo(CLAVE);
        assertThat(solicitud.getBody().utf8()).contains("\"idGrupo\":7").contains("\"inicioPeriodo\":\"2026-11-02\"");
        assertThat(respuesta.idEnvio()).isEqualTo(3);
        assertThat(respuesta.estado()).isEqualTo("PENDIENTE");
    }

    @Test
    void registrarResultadoEnvio_envíaElCuerpoYDevuelveElEstado() throws Exception {
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("{\"estado\":\"ENVIADO\"}").build());

        RespuestaResultadoEnvio respuesta = cliente.registrarResultadoEnvio(3,
                new SolicitudResultadoEnvio(List.of(new ResultadoDestinatario(10, true, null))));

        RecordedRequest solicitud = core.takeRequest();
        assertThat(solicitud.getMethod()).isEqualTo("PUT");
        assertThat(solicitud.getTarget()).isEqualTo("/api/interno/reportes/envios/3/resultado");
        assertThat(solicitud.getBody().utf8()).contains("\"idUsuario\":10").contains("\"enviado\":true");
        assertThat(respuesta.estado()).isEqualTo("ENVIADO");
    }

    @Test
    void iniciarEjecucion_envíaElCuerpoYDevuelveElId() throws Exception {
        core.enqueue(new MockResponse.Builder().code(201).addHeader("Content-Type", "application/json")
                .body("{\"idEjecucion\":42}").build());

        OffsetDateTime inicio = OffsetDateTime.of(2026, 11, 9, 8, 0, 0, 0, ZoneOffset.of("-03:00"));
        OffsetDateTime fin = inicio.plusHours(1);
        long idEjecucion = cliente.iniciarEjecucion("REPORTE_SEMANAL", inicio, fin);

        RecordedRequest solicitud = core.takeRequest();
        assertThat(solicitud.getMethod()).isEqualTo("POST");
        assertThat(solicitud.getTarget()).isEqualTo("/api/interno/ejecuciones");
        assertThat(solicitud.getHeaders().get(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO)).isEqualTo(CLAVE);
        assertThat(solicitud.getBody().utf8()).contains("\"tipoEjecucion\":\"REPORTE_SEMANAL\"");
        assertThat(idEjecucion).isEqualTo(42);
    }

    @Test
    void finalizarEjecucion_envíaElCuerpoConElResultado() throws Exception {
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("{\"idEjecucion\":42,\"codigoEstado\":\"EXITOSA\",\"creadas\":2,\"errores\":0,"
                        + "\"resumen\":\"ok\"}")
                .build());

        cliente.finalizarEjecucion(42, "EXITOSA", 2, 0, "ok");

        RecordedRequest solicitud = core.takeRequest();
        assertThat(solicitud.getMethod()).isEqualTo("PUT");
        assertThat(solicitud.getTarget()).isEqualTo("/api/interno/ejecuciones/42");
        assertThat(solicitud.getBody().utf8()).contains("\"codigoEstado\":\"EXITOSA\"").contains("\"creadas\":2");
    }
}
