package cl.adndigital.mejorvejez.reportes.calculo;

/**
 * Las alertas de un adulto detectadas dentro del período. {@code
 * minutosPromedioCierre} es el promedio, en minutos, de {@code
 * fechaRevision − fechaDeteccion} solo de las alertas cerradas al cierre;
 * {@code null} si no hubo ninguna.
 */
public record ResumenAlertas(int total, int atrasos, int citas, int reposiciones, int cerradas, int abiertas,
        Double minutosPromedioCierre) {
}
