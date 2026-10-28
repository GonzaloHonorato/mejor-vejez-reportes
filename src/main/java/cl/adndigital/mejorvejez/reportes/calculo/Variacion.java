package cl.adndigital.mejorvejez.reportes.calculo;

/**
 * La diferencia (actual − anterior) de los totales de un adulto o de un
 * responsable entre esta semana y la anterior. Ausente ({@code null} en el
 * contenedor) cuando no hay semana anterior para ese adulto o responsable.
 */
public record Variacion(int programadas, int aTiempo, int conAtraso, int noRealizadas, int pendientes,
        Double porcentajeATiempoPuntos) {
}
