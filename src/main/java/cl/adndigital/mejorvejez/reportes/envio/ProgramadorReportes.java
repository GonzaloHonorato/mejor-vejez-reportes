package cl.adndigital.mejorvejez.reportes.envio;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import cl.adndigital.mejorvejez.reportes.calculo.Periodo;

/**
 * Dispara la corrida del reporte semanal, en America/Santiago: primera
 * corrida a las 08:00 del lunes y luego cada hora hasta las 20:00 ({@code
 * mejorvejez.reportes.cron}); los grupos ya enviados se omiten solos
 * gracias a la reserva. Se apaga con {@code
 * mejorvejez.procesos.habilitados=false} ({@code PROCESOS_HABILITADOS}).
 */
@Component
@ConditionalOnProperty(name = "mejorvejez.procesos.habilitados", havingValue = "true", matchIfMissing = true)
public class ProgramadorReportes {

    private static final Logger LOG = LoggerFactory.getLogger(ProgramadorReportes.class);

    private final ServicioReporteSemanal servicio;
    private final Clock reloj;

    public ProgramadorReportes(ServicioReporteSemanal servicio, Clock reloj) {
        this.servicio = servicio;
        this.reloj = reloj;
    }

    /** Si ya hay una corrida en curso, se salta esta disparada (la reserva evita duplicados en la siguiente). */
    @Scheduled(cron = "${mejorvejez.reportes.cron}", zone = "America/Santiago")
    public void ejecutarReporteSemanal() {
        LocalDate inicio = Periodo.semanaAnterior(reloj).inicio();
        try {
            ResumenReportes resumen = servicio.ejecutar(inicio);
            LOG.info("Reporte semanal: ejecución {}, {} grupos, {} enviados, {} fallidos, {} omitidos",
                    resumen.idEjecucion(), resumen.gruposProcesados(), resumen.enviados(), resumen.fallidos(),
                    resumen.omitidos());
        } catch (CorridaEnCursoException ex) {
            LOG.info("Reporte semanal: ya hay una corrida en curso; se salta esta disparada programada.");
        } catch (RuntimeException ex) {
            LOG.error("Reporte semanal: la corrida programada falló", ex);
        }
    }
}
