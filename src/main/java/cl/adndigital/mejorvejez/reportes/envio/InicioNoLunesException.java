package cl.adndigital.mejorvejez.reportes.envio;

/**
 * El parámetro {@code inicio} de {@code POST
 * /api/interno/procesos/reporte-semanal} no es un lunes. Se valida acá, no
 * con la excepción interna de {@code Periodo}, para no depender de un
 * manejador global de {@code IllegalArgumentException}.
 */
public class InicioNoLunesException extends RuntimeException {
}
