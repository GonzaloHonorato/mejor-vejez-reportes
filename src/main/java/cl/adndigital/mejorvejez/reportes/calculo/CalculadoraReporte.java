package cl.adndigital.mejorvejez.reportes.calculo;

import java.text.Collator;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import cl.adndigital.mejorvejez.reportes.core.AdultoSemana;
import cl.adndigital.mejorvejez.reportes.core.AlertaSemana;
import cl.adndigital.mejorvejez.reportes.core.DatosSemana;
import cl.adndigital.mejorvejez.reportes.core.Persona;
import cl.adndigital.mejorvejez.reportes.core.RelevoSemana;
import cl.adndigital.mejorvejez.reportes.core.TareaSemana;

/**
 * Calcula el reporte semanal de cumplimiento de un grupo a partir de los
 * datos de la semana actual y de la semana anterior (esta última solo para
 * las variaciones y la señal de seguimiento).
 *
 * <p>Reglas: "a tiempo" es {@code COMPLETADA} con {@code fechaConfirmacion
 * ≤ fechaHorario + tolerancia}; "con atraso" es {@code COMPLETADA} más
 * tarde que eso, con el atraso en minutos redondeado hacia arriba;
 * "pendientes" es {@code PENDIENTE} o {@code EN_CURSO}. El porcentaje a
 * tiempo excluye las canceladas del divisor y no aplica (es {@code null})
 * si el divisor queda en 0. Los adultos se emparejan entre semanas por su
 * id, y los responsables por su id de usuario ("Sin asignar" con "Sin
 * asignar").
 *
 * <p>Una {@code COMPLETADA} sin {@code fechaConfirmacion} es un dato
 * inconsistente (no debería ocurrir, pero nunca debe tumbar el cálculo):
 * cuenta como "con atraso" con minutos de atraso desconocidos ({@code
 * null}), y queda fuera del promedio de atraso de ese adulto o
 * responsable.
 */
@Component
public class CalculadoraReporte {

    private static final String SIN_ASIGNAR = "Sin asignar";
    private static final double UMBRAL_SEGUIMIENTO = 70.0;

    private final Clock reloj;

    public CalculadoraReporte(Clock reloj) {
        this.reloj = reloj;
    }

    /** Vacío si {@code actual} no tiene ninguna tarea en el período: el grupo se omite del reporte. */
    public Optional<ReporteSemanal> calcular(DatosSemana actual, DatosSemana anterior) {
        if (actual.tareas().isEmpty()) {
            return Optional.empty();
        }

        Map<Long, List<TareaSemana>> tareasPorAdultoActual = agruparPorAdulto(actual.tareas());
        Map<Long, List<TareaSemana>> tareasPorAdultoAnterior = agruparPorAdulto(anterior.tareas());
        List<ResumenAdulto> adultos = actual.adultos().stream()
                .sorted(Comparator.comparing(AdultoSemana::nombre, comparadorNombres()))
                .map(adulto -> construirResumenAdulto(adulto, actual, tareasPorAdultoActual, tareasPorAdultoAnterior))
                .toList();

        Map<Long, List<TareaSemana>> tareasPorResponsableActual = agruparPorResponsable(actual.tareas());
        Map<Long, List<TareaSemana>> tareasPorResponsableAnterior = agruparPorResponsable(anterior.tareas());
        List<ResumenResponsable> responsables = ordenarClavesResponsable(tareasPorResponsableActual)
                .map(clave -> construirResumenResponsable(clave, tareasPorResponsableActual,
                        tareasPorResponsableAnterior))
                .toList();

        GrupoReportado grupo = new GrupoReportado(actual.idGrupo(), actual.nombreGrupo());
        Periodo periodo = new Periodo(actual.inicio(), actual.fin());
        return Optional.of(new ReporteSemanal(grupo, periodo, adultos, responsables, OffsetDateTime.now(reloj)));
    }

    // ---- adultos ----

    private ResumenAdulto construirResumenAdulto(AdultoSemana adulto, DatosSemana actual,
            Map<Long, List<TareaSemana>> tareasPorAdultoActual,
            Map<Long, List<TareaSemana>> tareasPorAdultoAnterior) {
        List<TareaSemana> tareas = tareasPorAdultoActual.getOrDefault(adulto.idAdulto(), List.of());
        List<TareaClasificada> clasificadas = clasificarTareas(tareas);
        Estadisticas statsActual = calcularEstadisticas(clasificadas);
        List<TareaSemana> tareasAnterior = tareasPorAdultoAnterior.getOrDefault(adulto.idAdulto(), List.of());
        Estadisticas statsAnterior = tareasAnterior.isEmpty() ? null
                : calcularEstadisticas(clasificarTareas(tareasAnterior));

        List<FilaTarea> filas = clasificadas.stream()
                .sorted(Comparator.comparing(c -> c.tarea().fechaHorario()))
                .map(this::construirFila)
                .toList();
        List<RelevoSemana> relevos = actual.relevos().stream()
                .filter(relevo -> relevo.idAdulto() == adulto.idAdulto())
                .sorted(Comparator.comparing(RelevoSemana::fechaTraspaso))
                .toList();
        ResumenAlertas alertas = construirResumenAlertas(
                actual.alertas().stream().filter(alerta -> alerta.idAdulto() == adulto.idAdulto()).toList());
        Variacion variacion = statsAnterior == null ? null : construirVariacion(statsActual, statsAnterior);

        return new ResumenAdulto(adulto.nombre(), statsActual.total(), statsActual.aTiempo(), statsActual.conAtraso(),
                statsActual.atrasoPromedioMinutos(), statsActual.noRealizadas(), statsActual.canceladas(),
                statsActual.pendientes(), filas, relevos, alertas, variacion);
    }

    private Map<Long, List<TareaSemana>> agruparPorAdulto(List<TareaSemana> tareas) {
        Map<Long, List<TareaSemana>> mapa = new HashMap<>();
        for (TareaSemana tarea : tareas) {
            mapa.computeIfAbsent(tarea.idAdulto(), id -> new ArrayList<>()).add(tarea);
        }
        return mapa;
    }

    // ---- responsables ----

    private record ClaveResponsable(Long idUsuario, String nombre) {
    }

    private Stream<ClaveResponsable> ordenarClavesResponsable(Map<Long, List<TareaSemana>> tareasPorResponsable) {
        return tareasPorResponsable.entrySet().stream()
                .map(entrada -> new ClaveResponsable(entrada.getKey(), nombreResponsableDe(entrada.getValue())))
                .sorted(Comparator.<ClaveResponsable>comparingInt(clave -> clave.idUsuario() == null ? 1 : 0)
                        .thenComparing(ClaveResponsable::nombre, comparadorNombres()));
    }

    /**
     * Comparador de nombres en español (locale "es-CL"): ordena "Ángela" y "Óscar" junto al resto
     * de la A y la O, no al final por el valor del carácter acentuado (nunca con {@code
     * String.compareTo}). Se crea una instancia nueva por llamada: un {@link Collator} no
     * garantiza ser seguro entre hilos.
     */
    private Comparator<String> comparadorNombres() {
        Collator collator = Collator.getInstance(Locale.of("es", "CL"));
        return collator::compare;
    }

    private String nombreResponsableDe(List<TareaSemana> tareas) {
        return tareas.stream()
                .map(TareaSemana::responsableAlCierre)
                .filter(Objects::nonNull)
                .map(Persona::nombre)
                .findFirst()
                .orElse(SIN_ASIGNAR);
    }

    private ResumenResponsable construirResumenResponsable(ClaveResponsable clave,
            Map<Long, List<TareaSemana>> tareasPorResponsableActual,
            Map<Long, List<TareaSemana>> tareasPorResponsableAnterior) {
        List<TareaSemana> tareas = tareasPorResponsableActual.get(clave.idUsuario());
        Estadisticas statsActual = calcularEstadisticas(clasificarTareas(tareas));
        List<TareaSemana> tareasAnterior = tareasPorResponsableAnterior.get(clave.idUsuario());
        Estadisticas statsAnterior = (tareasAnterior == null || tareasAnterior.isEmpty()) ? null
                : calcularEstadisticas(clasificarTareas(tareasAnterior));

        Variacion variacion = statsAnterior == null ? null : construirVariacion(statsActual, statsAnterior);
        boolean senalSeguimiento = clave.idUsuario() != null
                && statsActual.porcentajeATiempo() != null
                && statsActual.porcentajeATiempo() < UMBRAL_SEGUIMIENTO
                && statsAnterior != null
                && statsAnterior.porcentajeATiempo() != null
                && statsAnterior.porcentajeATiempo() < UMBRAL_SEGUIMIENTO;

        return new ResumenResponsable(clave.nombre(), statsActual.total(), statsActual.aTiempo(),
                statsActual.conAtraso(), statsActual.atrasoPromedioMinutos(), statsActual.noRealizadas(),
                statsActual.pendientes(), statsActual.canceladas(), statsActual.porcentajeATiempo(), variacion,
                senalSeguimiento);
    }

    private Map<Long, List<TareaSemana>> agruparPorResponsable(List<TareaSemana> tareas) {
        Map<Long, List<TareaSemana>> mapa = new HashMap<>();
        for (TareaSemana tarea : tareas) {
            Long idUsuario = tarea.responsableAlCierre() == null ? null : tarea.responsableAlCierre().idUsuario();
            mapa.computeIfAbsent(idUsuario, id -> new ArrayList<>()).add(tarea);
        }
        return mapa;
    }

    // ---- estadísticas compartidas (adulto: "programadas"; responsable: "asignadas") ----

    private enum Clasificacion {
        A_TIEMPO, CON_ATRASO, NO_REALIZADA, CANCELADA, PENDIENTE
    }

    /** Una tarea ya clasificada, para no repetir el cálculo entre las estadísticas y la fila del detalle. */
    private record TareaClasificada(TareaSemana tarea, Clasificacion clasificacion, Integer minutosAtraso) {
    }

    private record Estadisticas(int total, int aTiempo, int conAtraso, int noRealizadas, int canceladas,
            int pendientes, Double atrasoPromedioMinutos, Double porcentajeATiempo) {
    }

    private List<TareaClasificada> clasificarTareas(List<TareaSemana> tareas) {
        return tareas.stream().map(this::clasificarTarea).toList();
    }

    private TareaClasificada clasificarTarea(TareaSemana tarea) {
        Clasificacion clasificacion = clasificar(tarea);
        Integer minutosAtraso = clasificacion == Clasificacion.CON_ATRASO ? minutosAtraso(tarea) : null;
        return new TareaClasificada(tarea, clasificacion, minutosAtraso);
    }

    private Estadisticas calcularEstadisticas(List<TareaClasificada> clasificadas) {
        int aTiempo = 0;
        int conAtraso = 0;
        int noRealizadas = 0;
        int canceladas = 0;
        int pendientes = 0;
        List<Integer> atrasos = new ArrayList<>();
        for (TareaClasificada clasificada : clasificadas) {
            switch (clasificada.clasificacion()) {
                case A_TIEMPO -> aTiempo++;
                case CON_ATRASO -> {
                    conAtraso++;
                    // Dato inconsistente: una COMPLETADA sin fechaConfirmacion no tiene
                    // minutos de atraso; se cuenta como con atraso pero queda fuera del promedio.
                    if (clasificada.minutosAtraso() != null) {
                        atrasos.add(clasificada.minutosAtraso());
                    }
                }
                case NO_REALIZADA -> noRealizadas++;
                case CANCELADA -> canceladas++;
                case PENDIENTE -> pendientes++;
            }
        }
        int total = clasificadas.size();
        Double promedioAtraso = atrasos.isEmpty() ? null
                : atrasos.stream().mapToInt(Integer::intValue).average().orElseThrow();
        int divisor = total - canceladas;
        Double porcentaje = divisor == 0 ? null : (aTiempo * 100.0) / divisor;
        return new Estadisticas(total, aTiempo, conAtraso, noRealizadas, canceladas, pendientes, promedioAtraso,
                porcentaje);
    }

    /**
     * Una {@code COMPLETADA} sin {@code fechaConfirmacion} (dato inconsistente) nunca lanza:
     * se clasifica directamente como "con atraso", sin calcular el límite ni comparar fechas.
     */
    private Clasificacion clasificar(TareaSemana tarea) {
        return switch (tarea.codigoEstadoAlCierre()) {
            case "COMPLETADA" -> tarea.fechaConfirmacion() == null || !esATiempo(tarea) ? Clasificacion.CON_ATRASO
                    : Clasificacion.A_TIEMPO;
            case "NO_REALIZADA" -> Clasificacion.NO_REALIZADA;
            case "CANCELADA" -> Clasificacion.CANCELADA;
            default -> Clasificacion.PENDIENTE; // PENDIENTE, EN_CURSO
        };
    }

    private boolean esATiempo(TareaSemana tarea) {
        OffsetDateTime limite = tarea.fechaHorario().plusMinutes(tarea.minutosTolerancia());
        return !tarea.fechaConfirmacion().isAfter(limite);
    }

    /**
     * Minutos desde {@code fechaHorario + tolerancia} hasta {@code fechaConfirmacion}, redondeados
     * hacia arriba. {@code null} si falta {@code fechaConfirmacion} (dato inconsistente): el
     * atraso queda sin cuantificar en vez de lanzar.
     */
    private Integer minutosAtraso(TareaSemana tarea) {
        if (tarea.fechaConfirmacion() == null) {
            return null;
        }
        OffsetDateTime limite = tarea.fechaHorario().plusMinutes(tarea.minutosTolerancia());
        Duration atraso = Duration.between(limite, tarea.fechaConfirmacion());
        return (int) Math.ceil(atraso.toMillis() / 60000.0);
    }

    private Variacion construirVariacion(Estadisticas actual, Estadisticas anterior) {
        Double puntos = (actual.porcentajeATiempo() == null || anterior.porcentajeATiempo() == null) ? null
                : actual.porcentajeATiempo() - anterior.porcentajeATiempo();
        return new Variacion(actual.total() - anterior.total(), actual.aTiempo() - anterior.aTiempo(),
                actual.conAtraso() - anterior.conAtraso(), actual.noRealizadas() - anterior.noRealizadas(),
                actual.pendientes() - anterior.pendientes(), puntos);
    }

    private FilaTarea construirFila(TareaClasificada clasificada) {
        TareaSemana tarea = clasificada.tarea();
        String responsable = tarea.responsableAlCierre() == null ? SIN_ASIGNAR
                : tarea.responsableAlCierre().nombre();
        String confirmadoPor = tarea.confirmadoPor() == null ? null : tarea.confirmadoPor().nombre();
        return new FilaTarea(tarea.tipoTarea(), tarea.titulo(), tarea.fechaHorario(), responsable,
                tarea.codigoEstadoAlCierre(), tarea.fechaConfirmacion(), confirmadoPor,
                clasificada.minutosAtraso(), tarea.reasignada());
    }

    private ResumenAlertas construirResumenAlertas(List<AlertaSemana> alertas) {
        int atrasos = 0;
        int citas = 0;
        int reposiciones = 0;
        int cerradas = 0;
        int abiertas = 0;
        List<Double> minutosCierre = new ArrayList<>();
        for (AlertaSemana alerta : alertas) {
            switch (alerta.tipoAlerta()) {
                case "ATRASO_TAREA" -> atrasos++;
                case "CITA_PROXIMA" -> citas++;
                case "REPOSICION_MEDICAMENTO" -> reposiciones++;
                default -> {
                }
            }
            if ("CERRADA".equals(alerta.codigoEstadoAlCierre())) {
                cerradas++;
                if (alerta.fechaRevision() != null) {
                    double minutos = Duration.between(alerta.fechaDeteccion(), alerta.fechaRevision()).toMillis()
                            / 60000.0;
                    minutosCierre.add(minutos);
                }
            } else {
                abiertas++;
            }
        }
        Double promedio = minutosCierre.isEmpty() ? null
                : minutosCierre.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        return new ResumenAlertas(alertas.size(), atrasos, citas, reposiciones, cerradas, abiertas, promedio);
    }
}
