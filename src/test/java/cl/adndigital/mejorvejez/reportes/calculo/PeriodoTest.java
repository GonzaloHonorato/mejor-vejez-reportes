package cl.adndigital.mejorvejez.reportes.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import cl.adndigital.mejorvejez.reportes.comun.ZonaNegocio;

class PeriodoTest {

    @Test
    void periodoDeLaSemanaAnteriorEnSantiago() {
        // Lunes 2026-11-09 08:00 America/Santiago (-03:00).
        Clock reloj1 = Clock.fixed(Instant.parse("2026-11-09T11:00:00Z"), ZonaNegocio.ZONA);
        Periodo periodo1 = Periodo.semanaAnterior(reloj1);
        assertThat(periodo1.inicio()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(periodo1.fin()).isEqualTo(LocalDate.of(2026, 11, 8));

        // Lunes 2026-09-07 (el reloj de Chile se adelantó el 2026-09-06).
        Clock reloj2 = Clock.fixed(Instant.parse("2026-09-07T11:00:00Z"), ZonaNegocio.ZONA);
        Periodo periodo2 = Periodo.semanaAnterior(reloj2);
        assertThat(periodo2.inicio()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(periodo2.fin()).isEqualTo(LocalDate.of(2026, 9, 6));
    }

    @Test
    void periodoDeLaSemanaAnteriorConElRelojEnMitadDeSemana() {
        // Miércoles 2026-11-11 15:00 America/Santiago (-03:00): la semana de "hoy" es la que
        // empezó el lunes 2026-11-09, así que la anterior es la de 2026-11-02 a 2026-11-08.
        Clock reloj = Clock.fixed(Instant.parse("2026-11-11T18:00:00Z"), ZonaNegocio.ZONA);

        Periodo periodo = Periodo.semanaAnterior(reloj);

        assertThat(periodo.inicio()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(periodo.fin()).isEqualTo(LocalDate.of(2026, 11, 8));
    }

    @Test
    void desdeInicioConstruyeElDomingoComoFin() {
        Periodo periodo = Periodo.desdeInicio(LocalDate.of(2026, 11, 2));

        assertThat(periodo.inicio()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(periodo.fin()).isEqualTo(LocalDate.of(2026, 11, 8));
    }

    @Test
    void desdeInicioLanzaExcepcionSiNoEsLunes() {
        assertThatThrownBy(() -> Periodo.desdeInicio(LocalDate.of(2026, 11, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anteriorDevuelveLaSemanaInmediatamenteAnterior() {
        Periodo periodo = Periodo.desdeInicio(LocalDate.of(2026, 11, 9));

        Periodo anterior = periodo.anterior();

        assertThat(anterior.inicio()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(anterior.fin()).isEqualTo(LocalDate.of(2026, 11, 8));
    }
}
