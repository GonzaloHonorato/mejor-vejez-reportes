package cl.adndigital.mejorvejez.reportes.comun;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expone el reloj de negocio como bean inyectable. Todo cálculo de fechas
 * debe recibir este {@link Clock} por inyección y usarlo; nunca llamar a
 * {@code now()} sin reloj, para que las pruebas puedan fijar la hora.
 */
@Configuration
public class ConfiguracionTiempo {

    @Bean
    public Clock reloj() {
        return Clock.system(ZonaNegocio.ZONA);
    }
}
