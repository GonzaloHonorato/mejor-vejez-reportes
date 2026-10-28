package cl.adndigital.mejorvejez.reportes.core;

import java.time.OffsetDateTime;

/**
 * Una tarea de cuidado del período, reconstruida AL CIERRE desde el
 * historial: parte de {@link DatosSemana#tareas}.
 *
 * <ul>
 * <li>{@code codigoEstadoAlCierre}: el {@code ID_ESTADO_NUEVO} de la última
 * entrada del historial con fecha de cambio antes del cierre; {@code
 * PENDIENTE} si no hay ninguna.</li>
 * <li>{@code fechaConfirmacion}/{@code confirmadoPor}: los de esa misma
 * entrada, solo cuando lleva a {@code COMPLETADA} o {@code NO_REALIZADA};
 * {@code null} en cualquier otro caso.</li>
 * <li>{@code responsableAlCierre}: la asignación vigente al cierre (antes
 * del cierre y sin terminar antes del cierre); {@code null} si no hay
 * ninguna.</li>
 * <li>{@code reasignada}: la tarea tiene más de una asignación antes del
 * cierre.</li>
 * </ul>
 */
public record TareaSemana(long idTarea, long idAdulto, String tipoTarea, String titulo, OffsetDateTime fechaHorario,
        int minutosTolerancia, String codigoEstadoAlCierre, OffsetDateTime fechaConfirmacion, Persona confirmadoPor,
        Persona responsableAlCierre, boolean reasignada) {
}
