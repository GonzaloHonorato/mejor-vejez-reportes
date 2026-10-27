package cl.adndigital.mejorvejez.reportes.salud;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import cl.adndigital.mejorvejez.reportes.comun.ZonaNegocio;

@WebMvcTest(ControladorSalud.class)
@Import(ControladorSaludTest.RelojFijo.class)
class ControladorSaludTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void saludResponde200ConElNombreDelServicio() throws Exception {
        mockMvc.perform(get("/api/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$.servicio").value("mejor-vejez-reportes"))
                .andExpect(jsonPath("$.fechaHora").value("2026-11-02T10:00:00-03:00"));
    }

    @TestConfiguration
    static class RelojFijo {
        @Bean
        Clock reloj() {
            return Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZonaNegocio.ZONA);
        }
    }
}
