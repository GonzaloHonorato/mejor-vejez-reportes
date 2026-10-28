package cl.adndigital.mejorvejez.reportes.envio;

/**
 * Ya hay una corrida del reporte semanal en curso (candado en memoria, no
 * persistente): {@link ServicioReporteSemanal} libera el candado al
 * terminar, haya ido bien o mal.
 */
public class CorridaEnCursoException extends RuntimeException {
}
