package cl.adndigital.mejorvejez.reportes.core;

import java.time.LocalDate;
import java.util.List;

/**
 * Respuesta de {@code GET /api/interno/reportes/grupos/{idGrupo}/semana}:
 * los datos de la semana de un grupo, reconstruidos AL CIERRE del período
 * desde el historial (nunca desde el estado actual de las tablas). El
 * período es {@code [inicio 00:00, inicio+7 días 00:00)} en Santiago;
 * {@code fin} es el domingo ({@code inicio + 6 días}). {@code adultos}
 * solo trae los que tienen al menos una tarea en el período.
 */
public record DatosSemana(long idGrupo, String nombreGrupo, LocalDate inicio, LocalDate fin,
        List<AdultoSemana> adultos, List<TareaSemana> tareas, List<RelevoSemana> relevos,
        List<AlertaSemana> alertas) {
}
