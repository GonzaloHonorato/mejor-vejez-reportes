package cl.adndigital.mejorvejez.reportes.core;

import java.time.OffsetDateTime;

/**
 * Un relevo del turno de cuidado detectado dentro del período: parte de
 * {@link DatosSemana#relevos}. {@code totalTareas} y {@code totalAlertas}
 * son el conteo de elementos de ese relevo por tipo. {@code codigoEstado}
 * es el estado actual del relevo ({@code COMPLETADA} | {@code CANCELADA}),
 * no uno "al cierre".
 */
public record RelevoSemana(long idRelevo, long idAdulto, OffsetDateTime fechaTraspaso, Persona saliente,
        Persona entrante, int totalTareas, int totalAlertas, String codigoEstado) {
}
