package cl.adndigital.mejorvejez.reportes.comun;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Clave compartida entre los microservicios de Mejor Vejez para autenticar
 * las llamadas a {@code /api/interno/**}. Se lee de {@code
 * mejorvejez.interno.clave-servicio} (variable de entorno {@code
 * CLAVE_SERVICIO}, igual en los tres servicios) y se compara en tiempo
 * constante con {@link MessageDigest#isEqual(byte[], byte[])}.
 *
 * <p>Si la clave configurada está vacía, {@link #coincideCon} siempre
 * devuelve {@code false}: una clave recibida vacía no debe coincidir con una
 * clave configurada vacía; de lo contrario, olvidar configurar {@code
 * CLAVE_SERVICIO} abriría las rutas internas a cualquiera que mande la
 * cabecera vacía o en blanco.
 */
public class ClaveServicio {

    private final String claveConfigurada;

    public ClaveServicio(String claveConfigurada) {
        this.claveConfigurada = claveConfigurada == null ? "" : claveConfigurada;
    }

    public boolean coincideCon(String claveRecibida) {
        if (claveConfigurada.isEmpty() || claveRecibida == null || claveRecibida.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                claveConfigurada.getBytes(StandardCharsets.UTF_8),
                claveRecibida.getBytes(StandardCharsets.UTF_8));
    }
}
