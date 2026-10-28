package cl.adndigital.mejorvejez.reportes.core;

/**
 * Referencia liviana a una persona (usuario) dentro de {@link DatosSemana}:
 * solo su id y su nombre. La usan {@link TareaSemana} (confirmador,
 * responsable) y {@link RelevoSemana} (saliente, entrante).
 */
public record Persona(Long idUsuario, String nombre) {
}
