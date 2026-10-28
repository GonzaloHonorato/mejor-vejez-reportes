package cl.adndigital.mejorvejez.reportes.comun;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Filtro servlet (sin Spring Security) que exige la cabecera {@value
 * #CABECERA_CLAVE_SERVICIO} en las rutas internas y la compara con {@link
 * ClaveServicio}. Se registra solo en {@code /api/interno/*} mediante un
 * {@code FilterRegistrationBean}; {@code /api/salud} queda público. Si la
 * cabecera falta o no coincide, corta la cadena con 401 {@link
 * ProblemDetail} "Clave de servicio inválida", sin más datos.
 *
 * <p>No valida ni mira un eventual {@code Authorization: Bearer <jwt>}: una
 * llamada con un JWT de usuario también cae acá y recibe el mismo 401.
 */
public class FiltroClaveServicio extends OncePerRequestFilter {

    public static final String CABECERA_CLAVE_SERVICIO = "X-Clave-Servicio";

    private static final String DETALLE_CLAVE_INVALIDA = "Clave de servicio inválida";

    private final ClaveServicio claveServicio;
    private final JsonMapper jsonMapper;

    public FiltroClaveServicio(ClaveServicio claveServicio, JsonMapper jsonMapper) {
        this.claveServicio = claveServicio;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta,
            FilterChain cadena) throws ServletException, IOException {
        String claveRecibida = solicitud.getHeader(CABECERA_CLAVE_SERVICIO);
        if (!claveServicio.coincideCon(claveRecibida)) {
            escribirRechazo(respuesta);
            return;
        }
        cadena.doFilter(solicitud, respuesta);
    }

    private void escribirRechazo(HttpServletResponse respuesta) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, DETALLE_CLAVE_INVALIDA);
        problema.setTitle(DETALLE_CLAVE_INVALIDA);

        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(respuesta.getWriter(), problema);
    }
}
