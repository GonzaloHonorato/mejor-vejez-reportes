package cl.adndigital.mejorvejez.reportes.core;

import java.time.OffsetDateTime;

/**
 * Una alerta detectada dentro del período: parte de {@link
 * DatosSemana#alertas}. {@code codigoEstadoAlCierre} es {@code CERRADA} si
 * la revisión fue anterior al cierre, {@code ABIERTA} en cualquier otro
 * caso (sin revisar, o revisada después del cierre); {@code fechaRevision}
 * es la real de la alerta (puede ser posterior al cierre o {@code null}),
 * no una "al cierre".
 */
public record AlertaSemana(long idAlerta, long idAdulto, String tipoAlerta, OffsetDateTime fechaDeteccion,
        String codigoEstadoAlCierre, OffsetDateTime fechaRevision) {
}
