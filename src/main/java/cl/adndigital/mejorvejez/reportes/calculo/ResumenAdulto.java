package cl.adndigital.mejorvejez.reportes.calculo;

import java.util.List;

import cl.adndigital.mejorvejez.reportes.core.RelevoSemana;

/**
 * El resumen semanal de un adulto mayor del grupo. {@code programadas}
 * incluye las canceladas; {@code atrasoPromedioMinutos} es {@code null} si
 * no hubo tareas con atraso. {@code variacion} es {@code null} si el
 * adulto no tuvo tareas en la semana anterior.
 */
public record ResumenAdulto(String nombre, int programadas, int aTiempo, int conAtraso, Double atrasoPromedioMinutos,
        int noRealizadas, int canceladas, int pendientes, List<FilaTarea> tareas, List<RelevoSemana> relevos,
        ResumenAlertas alertas, Variacion variacion) {
}
