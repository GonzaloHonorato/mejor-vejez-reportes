package cl.adndigital.mejorvejez.reportes.comun;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Prueba con contexto real la cadena de seguridad interna (filtro + {@code
 * FilterRegistrationBean}): {@code /api/salud} queda público y cualquier
 * ruta {@code /api/interno/*} (aunque no tenga controlador) exige la
 * cabecera de la clave de servicio.
 */
@SpringBootTest(properties = { "mejorvejez.interno.clave-servicio=clave-correcta",
        "mejorvejez.procesos.habilitados=false" })
@AutoConfigureMockMvc
class ConfiguracionFiltroInternoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rutaInternaSinClaveDa401() throws Exception {
        mockMvc.perform(get("/api/interno/procesos/reporte-semanal"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Clave de servicio inválida"));
    }

    @Test
    void rutaInternaConJwtDa401() throws Exception {
        mockMvc.perform(get("/api/interno/procesos/reporte-semanal")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer un-jwt-de-usuario"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Clave de servicio inválida"));
    }

    @Test
    void rutaInternaConClaveIncorrectaDa401() throws Exception {
        mockMvc.perform(get("/api/interno/procesos/reporte-semanal")
                        .header(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-incorrecta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Clave de servicio inválida"));
    }

    @Test
    void rutaPublicaSinClaveDa200() throws Exception {
        mockMvc.perform(get("/api/salud")).andExpect(status().isOk());
    }
}
