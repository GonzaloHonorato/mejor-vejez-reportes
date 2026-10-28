package cl.adndigital.mejorvejez.reportes.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;

import cl.adndigital.mejorvejez.reportes.alertas.ClienteAlertas;
import cl.adndigital.mejorvejez.reportes.calculo.CalculadoraReporte;
import cl.adndigital.mejorvejez.reportes.core.ClienteCore;
import cl.adndigital.mejorvejez.reportes.pdf.GeneradorPdf;

/**
 * Prueba {@link ServicioReporteSemanal#ejecutar} de punta a punta contra el
 * core y alertas simulados (cada uno con su propio {@link MockWebServer}):
 * el orden del flujo por grupo, los conteos de {@link ResumenReportes} y
 * que el PDF nunca pase por disco.
 */
class ServicioReporteSemanalTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final LocalDate LUNES_ANTERIOR = LocalDate.of(2026, 10, 26);
    private static final ZoneOffset SANTIAGO = ZoneOffset.of("-03:00");
    private static final String CLAVE = "clave-de-prueba";

    private MockWebServer core;
    private MockWebServer alertas;
    private ServicioReporteSemanal servicio;

    @BeforeEach
    void iniciar() throws Exception {
        core = new MockWebServer();
        core.start();
        alertas = new MockWebServer();
        alertas.start();
        ClienteCore clienteCore = new ClienteCore(core.url("/").toString(), CLAVE);
        ClienteAlertas clienteAlertas = new ClienteAlertas(alertas.url("/").toString(), CLAVE);
        Clock reloj = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), SANTIAGO);
        CalculadoraReporte calculadora = new CalculadoraReporte(reloj);
        GeneradorPdf generadorPdf = new GeneradorPdf();
        servicio = new ServicioReporteSemanal(clienteCore, clienteAlertas, calculadora, generadorPdf, reloj);
    }

    @AfterEach
    void cerrar() throws Exception {
        core.close();
        alertas.close();
    }

    // ---- constructores de respuestas JSON ----

    private static void encolar(MockWebServer servidor, int codigo, String cuerpo) {
        MockResponse.Builder constructor = new MockResponse.Builder().code(codigo);
        if (cuerpo != null) {
            constructor.addHeader("Content-Type", "application/json").body(cuerpo);
        }
        servidor.enqueue(constructor.build());
    }

    private static String idEjecucionJson(long idEjecucion) {
        return "{\"idEjecucion\":" + idEjecucion + "}";
    }

    private static String grupoJson(long idGrupo, String nombre) {
        return "{\"idGrupo\":" + idGrupo + ",\"nombreGrupo\":\"" + nombre + "\",\"destinatarios\":["
                + "{\"idUsuario\":10,\"nombre\":\"Ana\",\"correo\":\"ana@example.com\"},"
                + "{\"idUsuario\":11,\"nombre\":\"Luis\",\"correo\":\"luis@example.com\"}]}";
    }

    private static String gruposJson(String... gruposJson) {
        return "[" + String.join(",", gruposJson) + "]";
    }

    private static String tareaJson(long idTarea, long idAdulto) {
        return tareaJsonConResponsable(idTarea, idAdulto, 100, "Cuidadora Ana");
    }

    private static String tareaJsonConResponsable(long idTarea, long idAdulto, long idResponsable,
            String nombreResponsable) {
        return "{\"idTarea\":" + idTarea + ",\"idAdulto\":" + idAdulto + ",\"tipoTarea\":\"MEDICAMENTOS\","
                + "\"titulo\":\"Dar pastilla\",\"fechaHorario\":\"2026-11-02T09:00:00-03:00\","
                + "\"minutosTolerancia\":10,\"codigoEstadoAlCierre\":\"COMPLETADA\","
                + "\"fechaConfirmacion\":\"2026-11-02T09:05:00-03:00\","
                + "\"confirmadoPor\":{\"idUsuario\":" + idResponsable + ",\"nombre\":\"" + nombreResponsable + "\"},"
                + "\"responsableAlCierre\":{\"idUsuario\":" + idResponsable + ",\"nombre\":\"" + nombreResponsable
                + "\"},\"reasignada\":false}";
    }

    private static String adultoJson(long idAdulto, String nombre) {
        return "{\"idAdulto\":" + idAdulto + ",\"nombre\":\"" + nombre + "\"}";
    }

    private static String datosSemanaJson(long idGrupo, LocalDate inicio, String... tareasJson) {
        return datosSemanaJsonConAdultos(idGrupo, inicio, new String[0], tareasJson);
    }

    private static String datosSemanaJsonConAdultos(long idGrupo, LocalDate inicio, String[] adultosJson,
            String... tareasJson) {
        return "{\"idGrupo\":" + idGrupo + ",\"nombreGrupo\":\"Grupo " + idGrupo + "\",\"inicio\":\"" + inicio
                + "\",\"fin\":\"" + inicio.plusDays(6) + "\",\"adultos\":[" + String.join(",", adultosJson)
                + "],\"tareas\":[" + String.join(",", tareasJson) + "],\"relevos\":[],\"alertas\":[]}";
    }

    private static String extraerPdfBase64(String cuerpoSolicitud) {
        Matcher m = Pattern.compile("\"pdfBase64\":\"([^\"]+)\"").matcher(cuerpoSolicitud);
        if (!m.find()) {
            throw new IllegalStateException("No se encontró 'pdfBase64' en el cuerpo de la solicitud.");
        }
        return m.group(1);
    }

    private static String textoDelPdfEnviado(String cuerpoSolicitud) throws Exception {
        byte[] pdf = Base64.getDecoder().decode(extraerPdfBase64(cuerpoSolicitud));
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    private static String reservaJson(long idEnvio, String estado, String... pendientesJson) {
        return "{\"idEnvio\":" + idEnvio + ",\"estado\":\"" + estado + "\",\"intentos\":0,\"pendientes\":["
                + String.join(",", pendientesJson) + "]}";
    }

    private static String destinatarioJson(long idUsuario, String nombre, String correo) {
        return "{\"idUsuario\":" + idUsuario + ",\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\"}";
    }

    private static String respuestaEnvioAlertasJson(String... resultadosJson) {
        return "{\"resultados\":[" + String.join(",", resultadosJson) + "]}";
    }

    private static String resultadoDestinatarioJson(long idUsuario, boolean enviado) {
        return "{\"idUsuario\":" + idUsuario + ",\"enviado\":" + enviado + ",\"detalleError\":null}";
    }

    // ---- escenarios ----

    @Test
    void ejecutarEnviaYReportaResultado() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Pérez")));
        encolar(core, 200, datosSemanaJson(1, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(3, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 200, datosSemanaJson(1, LUNES_ANTERIOR));
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(10, true)));
        encolar(core, 200, "{\"estado\":\"ENVIADO\"}");
        encolar(core, 200, null);

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.idEjecucion()).isEqualTo(42L);
        assertThat(resumen.gruposProcesados()).isEqualTo(1);
        assertThat(resumen.enviados()).isEqualTo(1);
        assertThat(resumen.fallidos()).isZero();
        assertThat(resumen.omitidos()).isZero();

        RecordedRequest solicitudAlertas = alertas.takeRequest();
        assertThat(solicitudAlertas.getTarget()).isEqualTo("/api/interno/envios-reporte");
        String cuerpoSolicitud = solicitudAlertas.getBody().utf8();
        assertThat(cuerpoSolicitud).contains("\"claveEnvio\":\"REP-SEM-1-20261102\"")
                .contains("\"nombreArchivo\":\"reporte-semanal-2026-11-02.pdf\"");
        // Asunto y cuerpo exactos del correo.
        assertThat(cuerpoSolicitud)
                .contains("\"asunto\":\"Mejor Vejez · Reporte semanal Familia Pérez (02-11 al 08-11)\"");
        assertThat(cuerpoSolicitud).contains("\"cuerpo\":\"Se adjunta el reporte semanal de cumplimiento del "
                + "grupo Familia Pérez. Este correo se genera automáticamente; no es necesario responder.\"");
    }

    @Test
    void semanaEnCursoLanzaExcepcionSinLlamarAlCore() {
        // Reloj fijo: el propio lunes de esa semana, a las 08:00 (todavía no termina).
        Clock relojSemanaEnCurso = Clock
                .fixed(LUNES.atStartOfDay(SANTIAGO).toInstant().plusSeconds(8 * 3600), SANTIAGO);
        ClienteCore clienteCoreMock = mock(ClienteCore.class);
        ClienteAlertas clienteAlertasMock = mock(ClienteAlertas.class);
        CalculadoraReporte calculadora = new CalculadoraReporte(relojSemanaEnCurso);
        ServicioReporteSemanal servicioSemanaEnCurso = new ServicioReporteSemanal(clienteCoreMock,
                clienteAlertasMock, calculadora, new GeneradorPdf(), relojSemanaEnCurso);

        assertThatThrownBy(() -> servicioSemanaEnCurso.ejecutar(LUNES)).isInstanceOf(SemanaEnCursoException.class);

        // La validación ocurre antes de llamar a cualquier otro servicio.
        verifyNoInteractions(clienteCoreMock, clienteAlertasMock);
    }

    @Test
    void relojLunesOchoDeLaMananaAceptaLaSemanaQueTerminoAyer() throws Exception {
        // El cierre de LUNES es el lunes siguiente a las 00:00; el reloj se fija ese mismo
        // lunes a las 08:00 (la semana terminó ayer, domingo).
        Instant cierre = LUNES.plusDays(7).atStartOfDay(SANTIAGO).toInstant();
        Clock relojLunesOcho = Clock.fixed(cierre.plusSeconds(8 * 3600), SANTIAGO);
        ClienteCore clienteCoreMock = mock(ClienteCore.class);
        ClienteAlertas clienteAlertasMock = mock(ClienteAlertas.class);
        when(clienteCoreMock.iniciarEjecucion(any(), any(), any())).thenReturn(1L);
        when(clienteCoreMock.grupos()).thenReturn(List.of());
        CalculadoraReporte calculadora = new CalculadoraReporte(relojLunesOcho);
        ServicioReporteSemanal servicioLunesOcho = new ServicioReporteSemanal(clienteCoreMock, clienteAlertasMock,
                calculadora, new GeneradorPdf(), relojLunesOcho);

        ResumenReportes resumen = servicioLunesOcho.ejecutar(LUNES); // nunca lanza SemanaEnCursoException.

        assertThat(resumen.gruposProcesados()).isZero();
    }

    @Test
    void semanaSeAceptaJustoAlCierreYSeRechazaUnSegundoAntes() throws Exception {
        Instant cierre = LUNES.plusDays(7).atStartOfDay(SANTIAGO).toInstant();

        Clock relojDomingoUltimoSegundo = Clock.fixed(cierre.minusSeconds(1), SANTIAGO);
        ClienteCore coreAntes = mock(ClienteCore.class);
        ServicioReporteSemanal servicioAntes = new ServicioReporteSemanal(coreAntes, mock(ClienteAlertas.class),
                new CalculadoraReporte(relojDomingoUltimoSegundo), new GeneradorPdf(), relojDomingoUltimoSegundo);
        assertThatThrownBy(() -> servicioAntes.ejecutar(LUNES)).isInstanceOf(SemanaEnCursoException.class);
        verifyNoInteractions(coreAntes);

        Clock relojLunesMedianoche = Clock.fixed(cierre, SANTIAGO);
        ClienteCore coreAlCierre = mock(ClienteCore.class);
        when(coreAlCierre.iniciarEjecucion(any(), any(), any())).thenReturn(1L);
        when(coreAlCierre.grupos()).thenReturn(List.of());
        ServicioReporteSemanal servicioAlCierre = new ServicioReporteSemanal(coreAlCierre, mock(ClienteAlertas.class),
                new CalculadoraReporte(relojLunesMedianoche), new GeneradorPdf(), relojLunesMedianoche);
        assertThat(servicioAlCierre.ejecutar(LUNES).gruposProcesados()).isZero();
    }

    @Test
    void semanaAnteriorYaCerradaSeAcepta() throws Exception {
        // LUNES ya está cerrado respecto al reloj fijo de @BeforeEach (lunes siguiente a las 09:00).
        encolar(core, 201, idEjecucionJson(99));
        encolar(core, 200, gruposJson());
        encolar(core, 200, null);

        ResumenReportes resumen = servicio.ejecutar(LUNES); // nunca lanza SemanaEnCursoException.

        assertThat(resumen.idEjecucion()).isEqualTo(99L);
    }

    @Test
    void grupoYaEnviadoSeOmite() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Pérez")));
        encolar(core, 200, datosSemanaJson(1, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(3, "ENVIADO"));
        encolar(core, 200, null);

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.gruposProcesados()).isEqualTo(1);
        assertThat(resumen.omitidos()).isEqualTo(1);
        assertThat(resumen.enviados()).isZero();
        assertThat(resumen.fallidos()).isZero();
        // ejecuciones(POST) + grupos(GET) + semana(GET) + envios(POST) + ejecuciones(PUT).
        assertThat(core.getRequestCount()).isEqualTo(5);
        assertThat(alertas.getRequestCount()).isZero();
    }

    @Test
    void grupoSinTareasSeOmiteSinEnviar() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Pérez")));
        encolar(core, 200, datosSemanaJson(1, LUNES));
        encolar(core, 200, null);

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.omitidos()).isEqualTo(1);
        assertThat(resumen.enviados()).isZero();
        assertThat(resumen.fallidos()).isZero();
        // ejecuciones(POST) + grupos(GET) + semana(GET) + ejecuciones(PUT): nunca llama a reservar.
        assertThat(core.getRequestCount()).isEqualTo(4);
        assertThat(alertas.getRequestCount()).isZero();
    }

    @Test
    void coreCaidoEnUnGrupoSigueConLosDemas() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Rota"), grupoJson(2, "Familia Pérez")));
        encolar(core, 500, null); // semana del grupo 1 falla
        encolar(core, 200, datosSemanaJson(2, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(3, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 200, datosSemanaJson(2, LUNES_ANTERIOR));
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(10, true)));
        encolar(core, 200, "{\"estado\":\"ENVIADO\"}");
        encolar(core, 200, null);

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.gruposProcesados()).isEqualTo(2);
        assertThat(resumen.fallidos()).isEqualTo(1);
        assertThat(resumen.enviados()).isEqualTo(1);
        assertThat(resumen.omitidos()).isZero();
    }

    @Test
    void alertasCaidoMarcaFallidoParaReintento() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Pérez")));
        encolar(core, 200, datosSemanaJson(1, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(3, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 200, datosSemanaJson(1, LUNES_ANTERIOR));
        encolar(alertas, 500, null); // alertas no responde
        encolar(core, 200, "{\"estado\":\"PENDIENTE\"}");
        encolar(core, 200, null);

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.fallidos()).isEqualTo(1);
        assertThat(resumen.enviados()).isZero();

        RecordedRequest solicitudResultado = core.takeRequest(); // ejecuciones POST
        core.takeRequest(); // grupos GET
        core.takeRequest(); // semana actual GET
        core.takeRequest(); // envios POST
        core.takeRequest(); // semana anterior GET
        RecordedRequest resultadoAlCore = core.takeRequest(); // resultado PUT
        assertThat(resultadoAlCore.getTarget()).isEqualTo("/api/interno/reportes/envios/3/resultado");
        String cuerpoResultado = resultadoAlCore.getBody().utf8();
        assertThat(cuerpoResultado).contains("\"idUsuario\":10").contains("\"enviado\":false");
        assertThat(cuerpoResultado).doesNotContain("ana@example.com");
        // detalleError es exactamente el nombre simple de la excepción que lanzó el cliente REST.
        assertThat(cuerpoResultado)
                .contains("\"detalleError\":\"" + HttpServerErrorException.InternalServerError.class.getSimpleName()
                        + "\"");
    }

    @Test
    void envioSoloAPendientes() throws Exception {
        // El grupo tiene dos destinatarios, pero la reserva solo deja uno pendiente (el otro ya fue enviado).
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Pérez")));
        encolar(core, 200, datosSemanaJson(1, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(3, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 200, datosSemanaJson(1, LUNES_ANTERIOR));
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(10, true)));
        encolar(core, 200, "{\"estado\":\"ENVIADO\"}");
        encolar(core, 200, null);

        servicio.ejecutar(LUNES);

        RecordedRequest solicitudAlertas = alertas.takeRequest();
        String cuerpo = solicitudAlertas.getBody().utf8();
        assertThat(cuerpo).contains("\"idUsuario\":10");
        assertThat(cuerpo).doesNotContain("\"idUsuario\":11");
        assertThat(cuerpo).doesNotContain("luis@example.com");
    }

    @Test
    void falloAlPedirSemanaAnteriorCuentaComoFallidoYNoInformaResultado() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Pérez")));
        encolar(core, 200, datosSemanaJson(1, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(3, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 500, null); // semana anterior falla
        encolar(core, 200, null); // ejecuciones PUT

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.fallidos()).isEqualTo(1);
        assertThat(resumen.enviados()).isZero();
        assertThat(alertas.getRequestCount()).isZero();
        // 6 llamadas: ejecuciones(POST), grupos(GET), semana actual(GET), envios(POST), semana anterior(GET),
        // ejecuciones(PUT); nunca llama a resultado.
        assertThat(core.getRequestCount()).isEqualTo(6);
        for (RecordedRequest solicitud : List.of(core.takeRequest(), core.takeRequest(), core.takeRequest(),
                core.takeRequest(), core.takeRequest(), core.takeRequest())) {
            assertThat(solicitud.getTarget()).doesNotContain("/resultado");
        }
    }

    @Test
    void conteosYEstadoDeLaEjecucionConGruposMixtos() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200,
                gruposJson(grupoJson(1, "Familia Enviada"), grupoJson(2, "Familia Omitida"),
                        grupoJson(3, "Familia Fallida")));
        // Grupo 1: enviado.
        encolar(core, 200, datosSemanaJson(1, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(10, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 200, datosSemanaJson(1, LUNES_ANTERIOR));
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(10, true)));
        encolar(core, 200, "{\"estado\":\"ENVIADO\"}");
        // Grupo 2: sin tareas, omitido.
        encolar(core, 200, datosSemanaJson(2, LUNES));
        // Grupo 3: core caído al reservar.
        encolar(core, 200, datosSemanaJson(3, LUNES, tareaJson(2, 2)));
        encolar(core, 500, null);
        // Cierre de la ejecución.
        encolar(core, 200, null);

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.idEjecucion()).isEqualTo(42L);
        assertThat(resumen.gruposProcesados()).isEqualTo(3);
        assertThat(resumen.enviados()).isEqualTo(1);
        assertThat(resumen.omitidos()).isEqualTo(1);
        assertThat(resumen.fallidos()).isEqualTo(1);

        // La última solicitud al core es el cierre de la ejecución, con el código de estado PARCIAL.
        List<RecordedRequest> solicitudes = Stream.generate(() -> {
            try {
                return core.takeRequest();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }).limit(core.getRequestCount()).collect(Collectors.toList());
        RecordedRequest cierre = solicitudes.get(solicitudes.size() - 1);
        assertThat(cierre.getTarget()).isEqualTo("/api/interno/ejecuciones/42");
        assertThat(cierre.getBody().utf8()).contains("\"codigoEstado\":\"PARCIAL\"").contains("\"creadas\":1")
                .contains("\"errores\":1");
    }

    @Test
    void nadaSeEscribeEnDisco() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Pérez")));
        encolar(core, 200, datosSemanaJson(1, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(3, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 200, datosSemanaJson(1, LUNES_ANTERIOR));
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(10, true)));
        encolar(core, 200, "{\"estado\":\"ENVIADO\"}");
        encolar(core, 200, null);

        Path directorioTemporal = Path.of(System.getProperty("java.io.tmpdir"));
        Set<String> antes = listarArchivos(directorioTemporal);

        servicio.ejecutar(LUNES);

        Set<String> despues = listarArchivos(directorioTemporal);
        assertThat(despues).isEqualTo(antes);
    }

    private static Set<String> listarArchivos(Path directorio) throws Exception {
        try (Stream<Path> listado = Files.list(directorio)) {
            return listado.map(Path::toString).collect(Collectors.toSet());
        }
    }

    @Test
    void gruposConRespuestaVaciaDelCoreNoLanzaYCierraFallida() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, null); // 200 sin cuerpo: ClienteCore.grupos() devuelve null.
        encolar(core, 200, null); // cierre de la ejecución.

        ResumenReportes resumen = servicio.ejecutar(LUNES); // nunca debe lanzar.

        assertThat(resumen.idEjecucion()).isEqualTo(42L);
        assertThat(resumen.gruposProcesados()).isZero();
        assertThat(resumen.enviados()).isZero();
        // La ejecución queda FALLIDA (errores=1): el conteo de fallidos lo refleja, en vez de un
        // 0 indistinguible de "no había grupos que procesar".
        assertThat(resumen.fallidos()).isEqualTo(1);
        assertThat(resumen.omitidos()).isZero();

        core.takeRequest(); // ejecuciones POST
        core.takeRequest(); // grupos GET (sin cuerpo)
        RecordedRequest cierre = core.takeRequest();
        assertThat(cierre.getTarget()).isEqualTo("/api/interno/ejecuciones/42");
        assertThat(cierre.getBody().utf8()).contains("\"codigoEstado\":\"FALLIDA\"").contains("\"errores\":1");
    }

    @Test
    void corridaEnCursoLanzaExcepcionAlLlamarDeNuevo() throws Exception {
        ClienteCore clienteCoreMock = mock(ClienteCore.class);
        ClienteAlertas clienteAlertasMock = mock(ClienteAlertas.class);
        Clock reloj = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), SANTIAGO);
        CalculadoraReporte calculadora = new CalculadoraReporte(reloj);
        GeneradorPdf generadorPdf = new GeneradorPdf();
        ServicioReporteSemanal servicioBloqueante = new ServicioReporteSemanal(clienteCoreMock, clienteAlertasMock,
                calculadora, generadorPdf, reloj);

        CountDownLatch dentroDeLaCorrida = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        when(clienteCoreMock.iniciarEjecucion(any(), any(), any())).thenReturn(42L);
        when(clienteCoreMock.grupos()).thenAnswer(invocacion -> {
            dentroDeLaCorrida.countDown();
            liberar.await();
            return List.of();
        });

        ExecutorService ejecutor = Executors.newSingleThreadExecutor();
        try {
            Future<ResumenReportes> primeraCorrida = ejecutor.submit(() -> servicioBloqueante.ejecutar(LUNES));
            assertThat(dentroDeLaCorrida.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> servicioBloqueante.ejecutar(LUNES)).isInstanceOf(CorridaEnCursoException.class);

            liberar.countDown();
            ResumenReportes resultado = primeraCorrida.get(5, TimeUnit.SECONDS);
            assertThat(resultado.gruposProcesados()).isZero();
        } finally {
            ejecutor.shutdown();
        }
    }

    @Test
    void grupoConRespuestaVaciaDelCoreNoRompeLaCorridaYSigueConLosDemas() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Vacía"), grupoJson(2, "Familia Pérez")));
        encolar(core, 200, null); // 200 sin cuerpo: ClienteCore.semana() devuelve null para el grupo 1.
        encolar(core, 200, datosSemanaJson(2, LUNES, tareaJson(1, 1)));
        encolar(core, 200, reservaJson(3, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 200, datosSemanaJson(2, LUNES_ANTERIOR));
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(10, true)));
        encolar(core, 200, "{\"estado\":\"ENVIADO\"}");
        encolar(core, 200, null); // cierre de la ejecución: debe llegar aunque el grupo 1 haya fallado feo.

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.gruposProcesados()).isEqualTo(2);
        assertThat(resumen.fallidos()).isEqualTo(1);
        assertThat(resumen.enviados()).isEqualTo(1);

        List<RecordedRequest> solicitudes = Stream.generate(() -> {
            try {
                return core.takeRequest();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }).limit(core.getRequestCount()).collect(Collectors.toList());
        RecordedRequest cierre = solicitudes.get(solicitudes.size() - 1);
        assertThat(cierre.getTarget()).isEqualTo("/api/interno/ejecuciones/42");
    }

    @Test
    void alertasSinResultadoParaUnPendienteCuentaComoNoEnviado() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Pérez")));
        encolar(core, 200, datosSemanaJson(1, LUNES, tareaJson(1, 1)));
        // La reserva deja DOS destinatarios pendientes.
        encolar(core, 200, reservaJson(3, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com"),
                destinatarioJson(11, "Luis", "luis@example.com")));
        encolar(core, 200, datosSemanaJson(1, LUNES_ANTERIOR));
        // Alertas solo informa el resultado de uno de los dos pendientes.
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(10, true)));
        encolar(core, 200, "{\"estado\":\"PARCIAL\"}");
        encolar(core, 200, null);

        ResumenReportes resumen = servicio.ejecutar(LUNES);

        assertThat(resumen.fallidos()).isEqualTo(1);
        assertThat(resumen.enviados()).isZero();

        core.takeRequest(); // ejecuciones POST
        core.takeRequest(); // grupos GET
        core.takeRequest(); // semana actual GET
        core.takeRequest(); // envios POST
        core.takeRequest(); // semana anterior GET
        RecordedRequest resultadoAlCore = core.takeRequest(); // resultado PUT
        String cuerpo = resultadoAlCore.getBody().utf8();
        assertThat(cuerpo).contains("\"idUsuario\":10,\"enviado\":true");
        assertThat(cuerpo).contains("\"idUsuario\":11,\"enviado\":false")
                .contains("Sin resultado del servicio de alertas");
    }

    @Test
    void pdfEnviadoNoContieneDatosDeOtroGrupo() throws Exception {
        encolar(core, 201, idEjecucionJson(42));
        encolar(core, 200, gruposJson(grupoJson(1, "Familia Primera"), grupoJson(2, "Familia Segunda")));

        // Grupo 1: adulto y responsable propios.
        encolar(core, 200,
                datosSemanaJsonConAdultos(1, LUNES, new String[] { adultoJson(1, "Adulto Primero") },
                        tareaJsonConResponsable(1, 1, 100, "Cuidador Primero")));
        encolar(core, 200, reservaJson(10, "PENDIENTE", destinatarioJson(10, "Ana", "ana@example.com")));
        encolar(core, 200,
                datosSemanaJsonConAdultos(1, LUNES_ANTERIOR, new String[] { adultoJson(1, "Adulto Primero") }));
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(10, true)));
        encolar(core, 200, "{\"estado\":\"ENVIADO\"}");

        // Grupo 2: adulto y responsable propios, distintos de los del grupo 1.
        encolar(core, 200,
                datosSemanaJsonConAdultos(2, LUNES, new String[] { adultoJson(2, "Adulto Segundo") },
                        tareaJsonConResponsable(2, 2, 200, "Cuidador Segundo")));
        encolar(core, 200, reservaJson(11, "PENDIENTE", destinatarioJson(11, "Luis", "luis@example.com")));
        encolar(core, 200,
                datosSemanaJsonConAdultos(2, LUNES_ANTERIOR, new String[] { adultoJson(2, "Adulto Segundo") }));
        encolar(alertas, 200, respuestaEnvioAlertasJson(resultadoDestinatarioJson(11, true)));
        encolar(core, 200, "{\"estado\":\"ENVIADO\"}");

        encolar(core, 200, null); // cierre

        servicio.ejecutar(LUNES);

        RecordedRequest solicitudGrupo1 = alertas.takeRequest();
        RecordedRequest solicitudGrupo2 = alertas.takeRequest();

        String textoGrupo1 = textoDelPdfEnviado(solicitudGrupo1.getBody().utf8());
        String textoGrupo2 = textoDelPdfEnviado(solicitudGrupo2.getBody().utf8());

        assertThat(textoGrupo1).contains("Adulto Primero").contains("Cuidador Primero");
        assertThat(textoGrupo1).doesNotContain("Adulto Segundo").doesNotContain("Cuidador Segundo");

        assertThat(textoGrupo2).contains("Adulto Segundo").contains("Cuidador Segundo");
        assertThat(textoGrupo2).doesNotContain("Adulto Primero").doesNotContain("Cuidador Primero");
    }
}
