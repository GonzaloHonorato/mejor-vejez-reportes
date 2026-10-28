package cl.adndigital.mejorvejez.reportes.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cl.adndigital.mejorvejez.reportes.envio.CorridaEnCursoException;
import cl.adndigital.mejorvejez.reportes.envio.InicioNoLunesException;
import cl.adndigital.mejorvejez.reportes.envio.SemanaEnCursoException;

/**
 * Traduce las excepciones a respuestas {@link ProblemDetail} uniformes, en
 * español, sin filtrar detalles internos (mensajes de la causa, nombres de
 * clases, stack traces).
 *
 * <p>Las excepciones estándar de Spring MVC que ya traen su estado HTTP
 * ({@link ErrorResponse}) conservan ese estado 4xx con un texto genérico en
 * español.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarCuerpoIlegible(HttpMessageNotReadableException excepcion) {
        LOG.debug("Cuerpo de solicitud ilegible: {}", excepcion.getMessage());
        return construirProblema(HttpStatus.BAD_REQUEST, "Datos inválidos", "El cuerpo de la solicitud no es válido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail manejarParametroFaltante(MissingServletRequestParameterException excepcion) {
        LOG.debug("Parámetro obligatorio faltante: {}", excepcion.getParameterName());
        return construirProblema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Falta el parámetro obligatorio '" + excepcion.getParameterName() + "'.");
    }

    @ExceptionHandler(InicioNoLunesException.class)
    public ProblemDetail manejarInicioNoLunes(InicioNoLunesException excepcion) {
        LOG.debug("El inicio del reporte semanal no es un lunes");
        return construirProblema(HttpStatus.BAD_REQUEST, "Datos inválidos", "El parámetro 'inicio' debe ser un lunes.");
    }

    @ExceptionHandler(SemanaEnCursoException.class)
    public ProblemDetail manejarSemanaEnCurso(SemanaEnCursoException excepcion) {
        LOG.debug("El período pedido para el reporte semanal todavía no termina");
        return construirProblema(HttpStatus.BAD_REQUEST, "Datos inválidos", "La semana todavía no termina.");
    }

    @ExceptionHandler(CorridaEnCursoException.class)
    public ProblemDetail manejarCorridaEnCurso(CorridaEnCursoException excepcion) {
        LOG.debug("Ya hay una corrida del reporte semanal en curso");
        return construirProblema(HttpStatus.CONFLICT, "Corrida en curso", "Ya hay un reporte semanal en ejecución.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejarErrorInesperado(Exception excepcion) {
        if (excepcion instanceof ErrorResponse respuestaSpring
                && respuestaSpring.getStatusCode().is4xxClientError()) {
            return manejarErrorDeCliente(respuestaSpring);
        }
        LOG.error("Error no controlado", excepcion);
        return ResponseEntity.internalServerError()
                .body(construirProblema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado."));
    }

    private ResponseEntity<ProblemDetail> manejarErrorDeCliente(ErrorResponse respuestaSpring) {
        HttpStatusCode codigo = respuestaSpring.getStatusCode();
        LOG.debug("Error de cliente {}: {}", codigo.value(), respuestaSpring.getBody().getDetail());
        HttpStatus estado = HttpStatus.resolve(codigo.value());
        ProblemDetail problema = construirProblema(estado != null ? estado : HttpStatus.BAD_REQUEST, "Datos inválidos",
                "La solicitud no es válida.");
        return ResponseEntity.status(codigo).headers(respuestaSpring.getHeaders()).body(problema);
    }

    private ProblemDetail construirProblema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
