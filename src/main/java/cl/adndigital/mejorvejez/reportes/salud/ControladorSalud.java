package cl.adndigital.mejorvejez.reportes.salud;

import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Responde 200 mientras el proceso viva. Es pública: la usa el healthcheck del contenedor. */
@RestController
@RequestMapping("/api/salud")
public class ControladorSalud {

    static final String DISPONIBLE = "DISPONIBLE";

    private final String servicio;
    private final String version;
    private final Clock reloj;

    public ControladorSalud(@Value("${spring.application.name}") String servicio,
            @Value("${app.version:dev}") String version, Clock reloj) {
        this.servicio = servicio;
        this.version = version;
        this.reloj = reloj;
    }

    @GetMapping
    public RespuestaSalud salud() {
        return new RespuestaSalud(DISPONIBLE, servicio, version, OffsetDateTime.now(reloj));
    }
}
