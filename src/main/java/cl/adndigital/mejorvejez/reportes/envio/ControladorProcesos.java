package cl.adndigital.mejorvejez.reportes.envio;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.adndigital.mejorvejez.reportes.calculo.Periodo;

/**
 * Ruta interna que dispara manualmente la corrida del reporte semanal (la
 * consume el core, al reenviar la corrida del administrador). Protegida
 * solo por la cadena interna ({@code FiltroClaveServicio}).
 */
@RestController
@RequestMapping("/api/interno/procesos")
public class ControladorProcesos {

    private final ServicioReporteSemanal servicio;
    private final Clock reloj;

    public ControladorProcesos(ServicioReporteSemanal servicio, Clock reloj) {
        this.servicio = servicio;
        this.reloj = reloj;
    }

    /** {@code inicio} es opcional (por omisión, el lunes de la semana anterior); si viene, debe ser un lunes. */
    @PostMapping("/reporte-semanal")
    public ResumenReportes reporteSemanal(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio) {
        LocalDate inicioEfectivo = inicio;
        if (inicioEfectivo == null) {
            inicioEfectivo = Periodo.semanaAnterior(reloj).inicio();
        } else if (inicioEfectivo.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new InicioNoLunesException();
        }
        return servicio.ejecutar(inicioEfectivo);
    }
}
