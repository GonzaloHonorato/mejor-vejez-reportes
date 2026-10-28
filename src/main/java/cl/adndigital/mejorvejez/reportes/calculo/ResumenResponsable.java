package cl.adndigital.mejorvejez.reportes.calculo;

/**
 * El resumen semanal de un responsable (cuidador) del grupo, o del grupo
 * "Sin asignar" (tareas sin responsable al cierre). {@code asignadas}
 * incluye las {@code canceladas}; {@code porcentajeATiempo} las excluye del
 * divisor y es {@code null} si no aplica (divisor 0). {@code
 * senalSeguimiento} exige porcentaje aplicable bajo 70 % en esta semana y
 * en la anterior; nunca aplica a "Sin asignar".
 */
public record ResumenResponsable(String nombre, int asignadas, int aTiempo, int conAtraso,
        Double atrasoPromedioMinutos, int noRealizadas, int pendientes, int canceladas, Double porcentajeATiempo,
        Variacion variacion, boolean senalSeguimiento) {
}
