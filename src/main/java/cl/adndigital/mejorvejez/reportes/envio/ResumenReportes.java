package cl.adndigital.mejorvejez.reportes.envio;

/**
 * Resultado de una corrida del reporte semanal. {@code idEjecucion} es
 * {@code null} cuando el servicio core no respondió al registrar el
 * inicio de la corrida. {@code gruposProcesados} son todos los grupos
 * recorridos; {@code enviados}, los que terminaron con todos sus
 * pendientes enviados en esta corrida; {@code fallidos}, los que tuvieron
 * algún fallo; {@code omitidos}, los que no tenían tareas o ya estaban
 * enviados.
 */
public record ResumenReportes(Long idEjecucion, int gruposProcesados, int enviados, int fallidos, int omitidos) {
}
