package cl.adndigital.mejorvejez.reportes.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.reportes.calculo.Periodo;
import cl.adndigital.mejorvejez.reportes.comun.FiltroClaveServicio;
import cl.adndigital.mejorvejez.reportes.comun.ZonaNegocio;

/**
 * Prueba la cadena interna completa (filtro + ruta) de {@code POST
 * /api/interno/procesos/reporte-semanal}: la clave de servicio, la
 * validación de {@code inicio} y el candado de una sola corrida a la vez,
 * con el core y alertas simulados. Los procesos programados quedan
 * apagados ({@code mejorvejez.procesos.habilitados=false}) para que la
 * única corrida en curso durante la prueba del candado sea la manual.
 */
@SpringBootTest(properties = { "mejorvejez.procesos.habilitados=false",
        "mejorvejez.interno.clave-servicio=clave-correcta" })
@AutoConfigureMockMvc
class ControladorProcesosTest {

    private static final String RUTA = "/api/interno/procesos/reporte-semanal";

    /**
     * El lunes de la semana anterior a la actual, siempre cerrado respecto al reloj real del
     * sistema (nunca un lunes fijo "en el futuro" que dejaría de estar cerrado con el tiempo).
     */
    private static final String INICIO_SEMANA_CERRADA = Periodo
            .semanaAnterior(Clock.system(ZonaNegocio.ZONA)).inicio().toString();

    private static MockWebServer core;
    private static MockWebServer alertas;

    @DynamicPropertySource
    static void registrarServiciosSimulados(DynamicPropertyRegistry registro) throws Exception {
        core = new MockWebServer();
        core.start();
        alertas = new MockWebServer();
        alertas.start();
        registro.add("mejorvejez.core-url", () -> core.url("/").toString());
        registro.add("mejorvejez.alertas-url", () -> alertas.url("/").toString());
    }

    @AfterAll
    static void cerrarServicios() throws Exception {
        core.close();
        alertas.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext contexto;

    @Test
    void rutaProcesoSinClaveDa401() throws Exception {
        mockMvc.perform(post(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Clave de servicio inválida"));
    }

    @Test
    void rutaProcesoConClaveIncorrectaDa401() throws Exception {
        mockMvc.perform(
                post(RUTA).header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-incorrecta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inicioQueNoEsLunesDa400() throws Exception {
        mockMvc.perform(post(RUTA + "?inicio=2026-11-03")
                        .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inicioDeSemanaEnCursoDa400() throws Exception {
        // El lunes de la semana en curso (todavía no termina): nunca se acepta, aunque sea un lunes.
        String inicioEnCurso = Periodo.semanaAnterior(Clock.system(ZonaNegocio.ZONA)).inicio().plusWeeks(1)
                .toString();

        mockMvc.perform(post(RUTA + "?inicio=" + inicioEnCurso)
                        .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La semana todavía no termina."));
    }

    @Test
    void rutaProcesoConClaveCorrectaYSinGruposResponde200() throws Exception {
        core.enqueue(new MockResponse.Builder().code(201).addHeader("Content-Type", "application/json")
                .body("{\"idEjecucion\":1}").build());
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("[]")
                .build());
        core.enqueue(new MockResponse.Builder().code(200).build());

        mockMvc.perform(post(RUTA + "?inicio=" + INICIO_SEMANA_CERRADA)
                        .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gruposProcesados").value(0));

        // Deja la cola de solicitudes del MockWebServer (compartido entre pruebas) limpia.
        drenarSolicitudes(core, 3);
    }

    @Test
    void corridaEnCursoResponde409AQuienLlegaDespues() throws Exception {
        core.enqueue(new MockResponse.Builder().code(201).addHeader("Content-Type", "application/json")
                .body("{\"idEjecucion\":2}").build());
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
                .body("[]").bodyDelay(2, TimeUnit.SECONDS).build());
        core.enqueue(new MockResponse.Builder().code(200).build());

        ExecutorService ejecutor = Executors.newSingleThreadExecutor();
        try {
            Future<?> primeraCorrida = ejecutor.submit(() -> {
                try {
                    mockMvc.perform(post(RUTA + "?inicio=" + INICIO_SEMANA_CERRADA)
                                    .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                            .andExpect(status().isOk());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            // El candado se toma ANTES de la primera llamada HTTP al core: esperar a que esa
            // llamada llegue prueba, sin sondear con sleep, que la primera corrida ya lo tiene.
            RecordedRequest primeraLlamadaAlCore = core.takeRequest(5, TimeUnit.SECONDS);
            assertThat(primeraLlamadaAlCore).isNotNull();

            mockMvc.perform(post(RUTA + "?inicio=" + INICIO_SEMANA_CERRADA)
                            .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Ya hay un reporte semanal en ejecución."));

            primeraCorrida.get(5, TimeUnit.SECONDS);
            // Deja la cola de solicitudes del MockWebServer (compartido entre pruebas) limpia:
            // a la primera corrida le faltan por recibirse la respuesta de "grupos" y el cierre.
            drenarSolicitudes(core, 2);
        } finally {
            ejecutor.shutdown();
        }
    }

    /** Vacía {@code cantidad} solicitudes ya recibidas por el servidor simulado, sin inspeccionarlas. */
    private static void drenarSolicitudes(MockWebServer servidor, int cantidad) throws InterruptedException {
        for (int i = 0; i < cantidad; i++) {
            assertThat(servidor.takeRequest(5, TimeUnit.SECONDS)).isNotNull();
        }
    }

    @Test
    void procesosDesactivadosNoProgramanCorridas() {
        assertThat(contexto.getBeanNamesForType(ProgramadorReportes.class)).isEmpty();
    }

    @Test
    void sinGruposFallidosLaEjecucionQuedaExitosa() throws Exception {
        core.enqueue(new MockResponse.Builder().code(201).addHeader("Content-Type", "application/json")
                .body("{\"idEjecucion\":3}").build());
        core.enqueue(new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body("[]")
                .build());
        core.enqueue(new MockResponse.Builder().code(200).build()); // cierre de la ejecución

        mockMvc.perform(post(RUTA + "?inicio=" + INICIO_SEMANA_CERRADA)
                        .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                .andExpect(status().isOk());

        core.takeRequest(); // ejecuciones POST
        core.takeRequest(); // grupos GET
        RecordedRequest cierre = core.takeRequest();
        assertThat(cierre.getTarget()).isEqualTo("/api/interno/ejecuciones/3");
        assertThat(cierre.getBody().utf8()).contains("\"codigoEstado\":\"EXITOSA\"");
    }

    @Test
    void siFallaLaListaDeGruposLaEjecucionQuedaFallida() throws Exception {
        core.enqueue(new MockResponse.Builder().code(201).addHeader("Content-Type", "application/json")
                .body("{\"idEjecucion\":4}").build());
        core.enqueue(new MockResponse.Builder().code(500).build()); // falla al pedir los grupos
        core.enqueue(new MockResponse.Builder().code(200).build()); // cierre de la ejecución

        mockMvc.perform(post(RUTA + "?inicio=" + INICIO_SEMANA_CERRADA)
                        .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gruposProcesados").value(0));

        core.takeRequest(); // ejecuciones POST
        core.takeRequest(); // grupos GET (falla)
        RecordedRequest cierre = core.takeRequest();
        assertThat(cierre.getTarget()).isEqualTo("/api/interno/ejecuciones/4");
        assertThat(cierre.getBody().utf8()).contains("\"codigoEstado\":\"FALLIDA\"").contains("\"errores\":1");
    }
}
