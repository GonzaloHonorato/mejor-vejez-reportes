package cl.adndigital.mejorvejez.reportes.calculo;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * El período de un reporte semanal: una semana completa de lunes a domingo
 * en America/Santiago. {@code fin} es siempre el domingo ({@code inicio +
 * 6 días}).
 */
public record Periodo(LocalDate inicio, LocalDate fin) {

    public Periodo {
        if (inicio.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("El inicio del período debe ser un lunes.");
        }
        if (!fin.equals(inicio.plusDays(6))) {
            throw new IllegalArgumentException("El fin del período debe ser el domingo siguiente al inicio.");
        }
    }

    /** El período que empieza el {@code lunes} dado. Lanza si {@code lunes} no es un lunes. */
    public static Periodo desdeInicio(LocalDate lunes) {
        return new Periodo(lunes, lunes.plusDays(6));
    }

    /**
     * La semana lunes-domingo inmediatamente anterior a la semana de "hoy"
     * en Santiago (p. ej. un lunes a las 08:00 da la semana que terminó
     * ayer).
     */
    public static Periodo semanaAnterior(Clock reloj) {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate lunesDeEstaSemana = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return desdeInicio(lunesDeEstaSemana.minusWeeks(1));
    }

    /** El período de la semana inmediatamente anterior a este. */
    public Periodo anterior() {
        return desdeInicio(inicio.minusWeeks(1));
    }
}
