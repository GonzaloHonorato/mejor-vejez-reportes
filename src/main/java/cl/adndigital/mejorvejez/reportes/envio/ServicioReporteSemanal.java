package cl.adndigital.mejorvejez.reportes.envio;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cl.adndigital.mejorvejez.reportes.alertas.ClienteAlertas;
import cl.adndigital.mejorvejez.reportes.alertas.RespuestaEnvioReporte;
import cl.adndigital.mejorvejez.reportes.alertas.SolicitudEnvioReporte;
import cl.adndigital.mejorvejez.reportes.calculo.CalculadoraReporte;
import cl.adndigital.mejorvejez.reportes.calculo.ReporteSemanal;
import cl.adndigital.mejorvejez.reportes.comun.ZonaNegocio;
import cl.adndigital.mejorvejez.reportes.core.ClienteCore;
import cl.adndigital.mejorvejez.reportes.core.DatosSemana;
import cl.adndigital.mejorvejez.reportes.core.GrupoReporte;
import cl.adndigital.mejorvejez.reportes.core.ResultadoDestinatario;
import cl.adndigital.mejorvejez.reportes.core.RespuestaReservaEnvio;
import cl.adndigital.mejorvejez.reportes.core.SolicitudReservarEnvio;
import cl.adndigital.mejorvejez.reportes.core.SolicitudResultadoEnvio;
import cl.adndigital.mejorvejez.reportes.pdf.GeneradorPdf;

/**
 * Ejecuta la corrida del reporte semanal: por cada grupo, pide la semana
 * actual, reserva el envío en el core, pide la semana anterior, calcula el
 * reporte, genera el PDF y lo envía a través de Alertas, informando el
 * resultado al core. Un grupo que falla no detiene a los demás: cualquier
 * {@link RuntimeException} al procesar un grupo (una respuesta sin cuerpo,
 * un dato inconsistente, un fallo de OpenPDF, etc.) queda contenida y el
 * grupo cuenta como fallido. Nada escapa de {@link #ejecutar(LocalDate)}
 * salvo {@link CorridaEnCursoException} cuando ya hay una corrida en
 * curso; la ejecución registrada en el core siempre se cierra, aunque
 * algo inesperado ocurra al recorrer los grupos.
 *
 * <p>Orden elegido para no dejar envíos {@code PENDIENTE} eternos de
 * grupos sin tareas: primero se pide la semana actual y, si no tiene
 * tareas, el grupo se omite sin llegar a reservar nada en el core.
 *
 * <p>Solo se aceptan semanas ya cerradas: si {@code inicio + 7 días} (su
 * cierre) es posterior a la fecha y hora actuales, {@link #ejecutar}
 * lanza {@link SemanaEnCursoException} antes de llamar a cualquier otro
 * servicio, para que ningún llamador pueda generar un PDF con tareas que
 * aún no ocurren ni reservar un envío que bloquee la corrida real de esa
 * semana.
 */
@Service
public class ServicioReporteSemanal {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioReporteSemanal.class);

    private static final String TIPO_EJECUCION = "REPORTE_SEMANAL";
    private static final String ESTADO_EXITOSA = "EXITOSA";
    private static final String ESTADO_PARCIAL = "PARCIAL";
    private static final String ESTADO_FALLIDA = "FALLIDA";
    private static final String SEPARADOR_ERRORES = "\n";
    private static final String SIN_RESULTADO_ALERTAS = "Sin resultado del servicio de alertas";

    private static final DateTimeFormatter CLAVE_FECHA = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter ASUNTO_FECHA = DateTimeFormatter.ofPattern("dd-MM");

    private final ClienteCore clienteCore;
    private final ClienteAlertas clienteAlertas;
    private final CalculadoraReporte calculadora;
    private final GeneradorPdf generadorPdf;
    private final Clock reloj;

    private final AtomicBoolean corridaEnCurso = new AtomicBoolean(false);

    public ServicioReporteSemanal(ClienteCore clienteCore, ClienteAlertas clienteAlertas,
            CalculadoraReporte calculadora, GeneradorPdf generadorPdf, Clock reloj) {
        this.clienteCore = clienteCore;
        this.clienteAlertas = clienteAlertas;
        this.calculadora = calculadora;
        this.generadorPdf = generadorPdf;
        this.reloj = reloj;
    }

    /**
     * Corre el reporte semanal del período que empieza en {@code inicio} (debe ser un lunes).
     * Lanza {@link SemanaEnCursoException} si esa semana todavía no termina.
     */
    public ResumenReportes ejecutar(LocalDate inicio) {
        validarSemanaCerrada(inicio);
        if (!corridaEnCurso.compareAndSet(false, true)) {
            throw new CorridaEnCursoException();
        }
        try {
            return ejecutarSinCandado(inicio);
        } finally {
            corridaEnCurso.set(false);
        }
    }

    /** Solo se pueden pedir semanas ya cerradas: su cierre ({@code inicio + 7 días}) no puede ser futuro. */
    private void validarSemanaCerrada(LocalDate inicio) {
        OffsetDateTime cierre = inicio.plusDays(7).atStartOfDay(ZonaNegocio.ZONA).toOffsetDateTime();
        if (cierre.isAfter(OffsetDateTime.now(reloj))) {
            throw new SemanaEnCursoException();
        }
    }

    private ResumenReportes ejecutarSinCandado(LocalDate inicio) {
        OffsetDateTime inicioVentana = inicio.atStartOfDay(ZonaNegocio.ZONA).toOffsetDateTime();
        OffsetDateTime finVentana = inicio.plusDays(7).atStartOfDay(ZonaNegocio.ZONA).toOffsetDateTime();
        Long idEjecucion = iniciarEjecucion(inicioVentana, finVentana);

        List<GrupoReporte> grupos;
        try {
            grupos = clienteCore.grupos();
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: no se pudo obtener la lista de grupos del core", ex);
            finalizarEjecucion(idEjecucion, ESTADO_FALLIDA, 0, 1, "Core: " + ex.getClass().getSimpleName());
            return new ResumenReportes(idEjecucion, 0, 0, 1, 0);
        }
        if (grupos == null) {
            // Un 200 sin cuerpo (el core no debería responder así, pero nunca debe tumbar la corrida):
            // se trata igual que si la lista de grupos hubiera fallado.
            LOG.error("Reporte semanal: el core respondió sin cuerpo al pedir la lista de grupos");
            finalizarEjecucion(idEjecucion, ESTADO_FALLIDA, 0, 1, "Core: respuesta sin cuerpo al pedir los grupos");
            return new ResumenReportes(idEjecucion, 0, 0, 1, 0);
        }

        int enviados = 0;
        int fallidos = 0;
        int omitidos = 0;
        List<String> errores = new ArrayList<>();
        try {
            for (GrupoReporte grupo : grupos) {
                ResultadoGrupo resultado = procesarGrupo(grupo, inicio, errores);
                switch (resultado) {
                    case ENVIADO -> enviados++;
                    case FALLIDO -> fallidos++;
                    case OMITIDO -> omitidos++;
                }
            }
        } catch (RuntimeException ex) {
            // Red de seguridad: procesarGrupo ya atrapa sus propios fallos, pero esto garantiza
            // que la ejecución se cierre aunque algo inesperado escape del recorrido.
            LOG.error("Reporte semanal: fallo general recorriendo los grupos", ex);
            errores.add("Proceso: " + ex.getClass().getSimpleName());
            fallidos++;
        }

        String codigoEstado = fallidos == 0 ? ESTADO_EXITOSA : (enviados > 0 ? ESTADO_PARCIAL : ESTADO_FALLIDA);
        String resumen = errores.isEmpty() ? null : String.join(SEPARADOR_ERRORES, errores);
        finalizarEjecucion(idEjecucion, codigoEstado, enviados, fallidos, resumen);
        return new ResumenReportes(idEjecucion, grupos.size(), enviados, fallidos, omitidos);
    }

    private enum ResultadoGrupo {
        ENVIADO, FALLIDO, OMITIDO
    }

    /** Contiene cualquier {@link RuntimeException} del grupo: nunca deja que un grupo tumbe a los demás. */
    private ResultadoGrupo procesarGrupo(GrupoReporte grupo, LocalDate inicio, List<String> errores) {
        try {
            return procesarGrupoInterno(grupo, inicio, errores);
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: fallo inesperado procesando el grupo {}", grupo.idGrupo(), ex);
            errores.add("Grupo " + grupo.idGrupo() + ": " + ex.getClass().getSimpleName());
            return ResultadoGrupo.FALLIDO;
        }
    }

    private ResultadoGrupo procesarGrupoInterno(GrupoReporte grupo, LocalDate inicio, List<String> errores) {
        DatosSemana actual;
        try {
            actual = clienteCore.semana(grupo.idGrupo(), inicio);
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: no se pudo obtener la semana actual del grupo {}", grupo.idGrupo(), ex);
            errores.add("Grupo " + grupo.idGrupo() + ": " + ex.getClass().getSimpleName());
            return ResultadoGrupo.FALLIDO;
        }
        if (actual.tareas().isEmpty()) {
            return ResultadoGrupo.OMITIDO;
        }

        RespuestaReservaEnvio reserva;
        try {
            reserva = clienteCore.reservarEnvio(new SolicitudReservarEnvio(grupo.idGrupo(), inicio));
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: no se pudo reservar el envío del grupo {}", grupo.idGrupo(), ex);
            errores.add("Grupo " + grupo.idGrupo() + ": " + ex.getClass().getSimpleName());
            return ResultadoGrupo.FALLIDO;
        }
        if (reserva.pendientes().isEmpty()) {
            return ResultadoGrupo.OMITIDO;
        }

        DatosSemana anterior;
        try {
            anterior = clienteCore.semana(grupo.idGrupo(), inicio.minusDays(7));
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: no se pudo obtener la semana anterior del grupo {}", grupo.idGrupo(), ex);
            errores.add("Grupo " + grupo.idGrupo() + ": " + ex.getClass().getSimpleName());
            // No se informa resultado: el envío queda PENDIENTE en el core y se reintenta en la próxima corrida.
            return ResultadoGrupo.FALLIDO;
        }

        ReporteSemanal reporte = calculadora.calcular(actual, anterior)
                .orElseThrow(() -> new IllegalStateException(
                        "El grupo " + grupo.idGrupo() + " no tiene tareas pese a la validación previa."));
        byte[] pdf = generadorPdf.generar(reporte);

        return enviarYRegistrar(grupo, inicio, reporte, pdf, reserva, errores);
    }

    private ResultadoGrupo enviarYRegistrar(GrupoReporte grupo, LocalDate inicio, ReporteSemanal reporte, byte[] pdf,
            RespuestaReservaEnvio reserva, List<String> errores) {
        String claveEnvio = "REP-SEM-" + grupo.idGrupo() + "-" + inicio.format(CLAVE_FECHA);
        String nombreArchivo = "reporte-semanal-" + inicio + ".pdf";
        String asunto = "Mejor Vejez · Reporte semanal " + grupo.nombreGrupo() + " (" + inicio.format(ASUNTO_FECHA)
                + " al " + reporte.periodo().fin().format(ASUNTO_FECHA) + ")";
        String cuerpo = "Se adjunta el reporte semanal de cumplimiento del grupo " + grupo.nombreGrupo()
                + ". Este correo se genera automáticamente; no es necesario responder.";
        List<cl.adndigital.mejorvejez.reportes.alertas.Destinatario> destinatarios = reserva.pendientes().stream()
                .map(ServicioReporteSemanal::aDestinatarioAlertas)
                .toList();

        List<ResultadoDestinatario> resultadosParaCore;
        boolean todosEnviados;
        try {
            String pdfBase64 = Base64.getEncoder().encodeToString(pdf);
            RespuestaEnvioReporte respuesta = clienteAlertas.enviarReporte(
                    new SolicitudEnvioReporte(claveEnvio, asunto, cuerpo, nombreArchivo, pdfBase64, destinatarios));
            resultadosParaCore = cruzarConPendientes(reserva, respuesta);
            todosEnviados = resultadosParaCore.stream().allMatch(ResultadoDestinatario::enviado);
            if (!todosEnviados) {
                errores.add("Grupo " + grupo.idGrupo() + ": envío parcial o fallido en alertas");
            }
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: alertas no respondió para el grupo {}", grupo.idGrupo(), ex);
            errores.add("Grupo " + grupo.idGrupo() + ": " + ex.getClass().getSimpleName());
            String detalle = ex.getClass().getSimpleName();
            resultadosParaCore = reserva.pendientes().stream()
                    .map(d -> new ResultadoDestinatario(d.idUsuario(), false, detalle))
                    .toList();
            todosEnviados = false;
        }

        try {
            clienteCore.registrarResultadoEnvio(reserva.idEnvio(), new SolicitudResultadoEnvio(resultadosParaCore));
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: no se pudo informar el resultado del envío del grupo {}", grupo.idGrupo(),
                    ex);
        }

        return todosEnviados ? ResultadoGrupo.ENVIADO : ResultadoGrupo.FALLIDO;
    }

    /** Convierte el destinatario del contrato del core al contrato de alertas (misma forma, otro paquete). */
    private static cl.adndigital.mejorvejez.reportes.alertas.Destinatario aDestinatarioAlertas(
            cl.adndigital.mejorvejez.reportes.core.Destinatario destinatario) {
        return new cl.adndigital.mejorvejez.reportes.alertas.Destinatario(destinatario.idUsuario(),
                destinatario.nombre(), destinatario.correo());
    }

    /**
     * Cruza la respuesta de Alertas con {@code reserva.pendientes()}: un pendiente sin resultado
     * (Alertas no respondió por él) cuenta como no enviado, nunca como enviado por omisión. Si
     * Alertas repitiera un {@code idUsuario} (no debería: la reserva ya garantiza pendientes
     * únicos), se prefiere el resultado enviado, para no degradar uno ya exitoso.
     */
    private List<ResultadoDestinatario> cruzarConPendientes(RespuestaReservaEnvio reserva,
            RespuestaEnvioReporte respuesta) {
        Map<Long, cl.adndigital.mejorvejez.reportes.alertas.ResultadoDestinatario> resultadosPorUsuario = respuesta
                .resultados().stream()
                .collect(Collectors.toMap(cl.adndigital.mejorvejez.reportes.alertas.ResultadoDestinatario::idUsuario,
                        r -> r, (a, b) -> a.enviado() ? a : b));
        return reserva.pendientes().stream()
                .map(destinatario -> {
                    cl.adndigital.mejorvejez.reportes.alertas.ResultadoDestinatario resultado = resultadosPorUsuario
                            .get(destinatario.idUsuario());
                    if (resultado == null) {
                        return new ResultadoDestinatario(destinatario.idUsuario(), false, SIN_RESULTADO_ALERTAS);
                    }
                    return aResultadoCore(resultado);
                })
                .toList();
    }

    /** Convierte el resultado de un destinatario del contrato de alertas al contrato del core. */
    private static ResultadoDestinatario aResultadoCore(
            cl.adndigital.mejorvejez.reportes.alertas.ResultadoDestinatario resultado) {
        return new ResultadoDestinatario(resultado.idUsuario(), resultado.enviado(), resultado.detalleError());
    }

    private Long iniciarEjecucion(OffsetDateTime inicioVentana, OffsetDateTime finVentana) {
        try {
            return clienteCore.iniciarEjecucion(TIPO_EJECUCION, inicioVentana, finVentana);
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: no se pudo registrar el inicio de la corrida en el core", ex);
            return null;
        }
    }

    private void finalizarEjecucion(Long idEjecucion, String codigoEstado, int creadas, int errores,
            String resumen) {
        if (idEjecucion == null) {
            return;
        }
        try {
            clienteCore.finalizarEjecucion(idEjecucion, codigoEstado, creadas, errores, resumen);
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: no se pudo registrar el cierre de la corrida {}", idEjecucion, ex);
        }
    }
}
