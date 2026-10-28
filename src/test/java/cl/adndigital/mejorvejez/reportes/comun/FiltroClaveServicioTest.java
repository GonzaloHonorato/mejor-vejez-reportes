package cl.adndigital.mejorvejez.reportes.comun;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class FiltroClaveServicioTest {

    @Test
    void rutaInternaSinClaveDa401() throws Exception {
        FiltroClaveServicio filtro = new FiltroClaveServicio(new ClaveServicio("clave-correcta"),
                JsonMapper.builder().build());
        MockHttpServletRequest solicitud = new MockHttpServletRequest("POST",
                "/api/interno/procesos/reporte-semanal");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        boolean[] cadenaLlamada = { false };
        FilterChain cadena = (req, res) -> cadenaLlamada[0] = true;

        filtro.doFilter(solicitud, respuesta, cadena);

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        assertThat(respuesta.getContentAsString()).contains("Clave de servicio inválida");
        assertThat(cadenaLlamada[0]).isFalse();
    }

    @Test
    void rutaInternaConClaveCorrectaDejaPasar() throws Exception {
        FiltroClaveServicio filtro = new FiltroClaveServicio(new ClaveServicio("clave-correcta"),
                JsonMapper.builder().build());
        MockHttpServletRequest solicitud = new MockHttpServletRequest("POST",
                "/api/interno/procesos/reporte-semanal");
        solicitud.addHeader(FiltroClaveServicio.CABECERA_CLAVE_SERVICIO, "clave-correcta");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        boolean[] cadenaLlamada = { false };
        FilterChain cadena = (req, res) -> cadenaLlamada[0] = true;

        filtro.doFilter(solicitud, respuesta, cadena);

        assertThat(cadenaLlamada[0]).isTrue();
    }
}
