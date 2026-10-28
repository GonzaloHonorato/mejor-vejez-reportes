package cl.adndigital.mejorvejez.reportes.envio;

/**
 * El período pedido para el reporte semanal todavía no termina (su cierre
 * es posterior a la fecha y hora actuales): solo se pueden pedir semanas
 * cerradas, para no generar un PDF con tareas que aún no ocurren ni
 * reservar un envío que bloquee la corrida real de esa semana.
 */
public class SemanaEnCursoException extends RuntimeException {
}
