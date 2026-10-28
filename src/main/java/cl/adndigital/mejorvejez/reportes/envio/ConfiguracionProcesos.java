package cl.adndigital.mejorvejez.reportes.envio;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita {@code @Scheduled} para la corrida programada ({@link ProgramadorReportes}). */
@Configuration
@EnableScheduling
public class ConfiguracionProcesos {
}
