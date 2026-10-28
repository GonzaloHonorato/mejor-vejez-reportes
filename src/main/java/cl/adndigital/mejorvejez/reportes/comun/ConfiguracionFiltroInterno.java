package cl.adndigital.mejorvejez.reportes.comun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Registra {@link FiltroClaveServicio} solo en las rutas internas ({@code
 * /api/interno/*}), sin Spring Security: el resto de las rutas (como {@code
 * /api/salud}) queda público.
 */
@Configuration
public class ConfiguracionFiltroInterno {

    @Bean
    public ClaveServicio claveServicio(@Value("${mejorvejez.interno.clave-servicio}") String claveConfigurada) {
        return new ClaveServicio(claveConfigurada);
    }

    @Bean
    public FilterRegistrationBean<FiltroClaveServicio> filtroClaveServicio(ClaveServicio claveServicio,
            JsonMapper jsonMapper) {
        FilterRegistrationBean<FiltroClaveServicio> registro = new FilterRegistrationBean<>(
                new FiltroClaveServicio(claveServicio, jsonMapper));
        registro.addUrlPatterns("/api/interno/*");
        return registro;
    }
}
