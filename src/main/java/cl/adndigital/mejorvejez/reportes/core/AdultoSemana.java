package cl.adndigital.mejorvejez.reportes.core;

/**
 * Un adulto mayor del grupo con al menos una tarea en el período: parte de
 * {@link DatosSemana#adultos}. Los adultos sin ninguna tarea en la semana no
 * aparecen.
 */
public record AdultoSemana(long idAdulto, String nombre) {
}
