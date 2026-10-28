package cl.adndigital.mejorvejez.reportes.calculo;

import java.time.OffsetDateTime;

/**
 * Una fila del detalle de tareas de un adulto en el reporte semanal.
 * {@code responsable} es "Sin asignar" cuando la tarea no tiene responsable
 * al cierre. {@code minutosAtraso} solo viene con valor cuando la tarea
 * quedó clasificada "con atraso" (se mide desde {@code fechaHorario +
 * tolerancia} hasta {@code fechaConfirmacion}, redondeado hacia arriba).
 */
public record FilaTarea(String tipoTarea, String titulo, OffsetDateTime fechaHorario, String responsable,
        String codigoEstadoAlCierre, OffsetDateTime fechaConfirmacion, String confirmadoPor, Integer minutosAtraso,
        boolean reasignada) {
}
