package cl.adndigital.mejorvejez.reportes.calculo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * El reporte semanal de cumplimiento de un grupo familiar: lo calcula
 * {@link CalculadoraReporte} y se usa para generar el PDF.
 */
public record ReporteSemanal(GrupoReportado grupo, Periodo periodo, List<ResumenAdulto> adultos,
        List<ResumenResponsable> responsables, OffsetDateTime generadoEn) {
}
