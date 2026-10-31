package cl.adndigital.mejorvejez.reportes.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import cl.adndigital.mejorvejez.reportes.calculo.FilaTarea;
import cl.adndigital.mejorvejez.reportes.calculo.GrupoReportado;
import cl.adndigital.mejorvejez.reportes.calculo.Periodo;
import cl.adndigital.mejorvejez.reportes.calculo.ReporteSemanal;
import cl.adndigital.mejorvejez.reportes.calculo.ResumenAdulto;
import cl.adndigital.mejorvejez.reportes.calculo.ResumenAlertas;
import cl.adndigital.mejorvejez.reportes.calculo.ResumenResponsable;
import cl.adndigital.mejorvejez.reportes.calculo.Variacion;
import cl.adndigital.mejorvejez.reportes.core.RelevoSemana;

/**
 * Lee el PDF generado con PDFBox para comprobar sus secciones y cifras.
 * Nunca pasa por disco: el PDF viaja de {@link GeneradorPdf#generar} como
 * {@code byte[]} directo a {@link Loader#loadPDF(byte[])}.
 */
class GeneradorPdfTest {

    private static final ZoneOffset SANTIAGO = ZoneOffset.of("-03:00");

    private final GeneradorPdf generador = new GeneradorPdf();

    private static OffsetDateTime hora(int dia, int mes, int hora, int minuto) {
        return OffsetDateTime.of(2026, mes, dia, hora, minuto, 0, 0, SANTIAGO);
    }

    private static String textoDe(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    private static ResumenAdulto adultoConTareas(String nombre, List<FilaTarea> tareas, boolean conAlertaAbierta) {
        return adultoConTareas(nombre, tareas, conAlertaAbierta, null);
    }

    private static ResumenAdulto adultoConTareas(String nombre, List<FilaTarea> tareas, boolean conAlertaAbierta,
            Variacion variacion) {
        ResumenAlertas alertas = conAlertaAbierta ? new ResumenAlertas(1, 1, 0, 0, 0, 1, null)
                : new ResumenAlertas(0, 0, 0, 0, 0, 0, null);
        List<RelevoSemana> relevos = List.of(new RelevoSemana(1, 1, hora(2, 11, 20, 0),
                new cl.adndigital.mejorvejez.reportes.core.Persona(10L, "Cuidadora Saliente"),
                new cl.adndigital.mejorvejez.reportes.core.Persona(11L, "Cuidador Entrante"), 3, 1, "COMPLETADA"));
        return new ResumenAdulto(nombre, tareas.size(), 1, 0, null, 0, 0, Math.max(tareas.size() - 1, 0), tareas,
                relevos, alertas, variacion);
    }

    private static FilaTarea fila(int dia, int hora, String responsable) {
        return filaCompleta(dia, hora, responsable, responsable, false);
    }

    private static FilaTarea filaCompleta(int dia, int hora, String responsable, String confirmadoPor,
            boolean reasignada) {
        return new FilaTarea("MEDICAMENTOS", "Dar pastilla", hora(dia, 11, hora, 0), responsable, "COMPLETADA",
                hora(dia, 11, hora, 5), confirmadoPor, null, reasignada);
    }

    @Test
    void pdfContieneSeccionesYCifras() throws Exception {
        List<FilaTarea> tareas = List.of(filaCompleta(2, 9, "Cuidadora Ana", "Cuidadora Ana", true),
                fila(3, 9, "Sin asignar"));
        Variacion variacionAdulto = new Variacion(1, 1, 0, 0, 0, 10.0);
        ResumenAdulto adulto = adultoConTareas("Adulto Uno", tareas, true, variacionAdulto);
        // asignadas (8) = aTiempo(2) + conAtraso(3) + noRealizadas(1) + canceladas(1) + pendientes(1).
        ResumenResponsable responsableConSenal = new ResumenResponsable("Cuidadora Ana", 8, 2, 3, 12.0, 1, 1, 1,
                40.0, new Variacion(1, 0, 1, 1, 0, -5.0), true);
        ResumenResponsable sinAsignar = new ResumenResponsable("Sin asignar", 1, 1, 0, null, 0, 0, 0, 100.0, null,
                false);
        ReporteSemanal reporte = new ReporteSemanal(new GrupoReportado(1, "Familia Pérez"),
                Periodo.desdeInicio(java.time.LocalDate.of(2026, 11, 2)), List.of(adulto),
                List.of(responsableConSenal, sinAsignar), OffsetDateTime.of(2026, 11, 9, 8, 30, 0, 0, SANTIAGO));

        byte[] pdf = generador.generar(reporte);
        String texto = textoDe(pdf);

        assertThat(texto).contains("Familia Pérez");
        assertThat(texto).contains("lunes 02-11-2026");
        assertThat(texto).contains("domingo 08-11-2026");
        assertThat(texto).contains("Adulto Uno");
        assertThat(texto).contains("Cumplimiento por responsable");
        assertThat(texto).contains("Sin asignar");
        assertThat(texto).contains("Requiere seguimiento");
        // Tipo y estado de tarea con etiqueta legible, no el código crudo del catálogo.
        assertThat(texto).contains("Medicamentos").contains("Completada");
        // Columnas nuevas de la tabla de tareas: quién confirmó y si fue reasignada.
        assertThat(texto).contains("Confirmó").contains("Reasignada");
        assertThat(texto).contains("Cuidadora Ana (02-11-2026 09:05)");
        assertThat(texto).contains("Sí");
        // Variación del adulto contra la semana anterior.
        assertThat(texto).contains("Variación vs. semana anterior");
        // Columnas nuevas de "Cumplimiento por responsable", cada encabezado completo en una sola
        // línea (ningún ancho de columna puede partir una palabra a mitad de camino).
        assertThat(texto).contains("Asig.").contains("Atraso prom.").contains("No realizadas").contains("Canceladas")
                .contains("Pend.");
        assertThat(texto).doesNotContain("Asigna\n").doesNotContain("Pendiente\n")
                .doesNotContain("No\nrealizadas").doesNotContain("Atraso\nprom.").doesNotContain("Cancela\n");
        // Las columnas numéricas de "Cumplimiento por responsable" suman "Asig.".
        assertThat(responsableConSenal.asignadas()).isEqualTo(responsableConSenal.aTiempo()
                + responsableConSenal.conAtraso() + responsableConSenal.noRealizadas()
                + responsableConSenal.canceladas() + responsableConSenal.pendientes());
        assertThat(texto).contains("-5,0 pts");
        // Encabezados renombrados de la tabla de relevos.
        assertThat(texto).contains("Tareas pendientes").contains("Alertas abiertas");
        // Nota al pie, texto exacto.
        assertThat(texto)
                .contains("Reporte generado automáticamente por Cuida+. No se almacena una copia.");
    }

    @Test
    void pdfSinSenalDeSeguimientoNoMencionaRequiereSeguimiento() throws Exception {
        ResumenAdulto adulto = adultoConTareas("Adulto Dos", List.of(fila(2, 9, "Cuidadora Ana")), false);
        ResumenResponsable responsable = new ResumenResponsable("Cuidadora Ana", 1, 1, 0, null, 0, 0, 0, 100.0, null,
                false);
        ReporteSemanal reporte = new ReporteSemanal(new GrupoReportado(2, "Familia Soto"),
                Periodo.desdeInicio(java.time.LocalDate.of(2026, 11, 2)), List.of(adulto), List.of(responsable),
                OffsetDateTime.of(2026, 11, 9, 8, 30, 0, 0, SANTIAGO));

        byte[] pdf = generador.generar(reporte);
        String texto = textoDe(pdf);

        assertThat(texto).doesNotContain("Requiere seguimiento");
        // El adulto no tuvo tareas la semana anterior: sin variación, nunca un "-" ambiguo.
        assertThat(texto).contains("Sin semana anterior");
    }

    @Test
    void alertasUsanLasEtiquetasDeTipoDeLaWebConSuCantidad() throws Exception {
        ResumenAlertas alertas = new ResumenAlertas(6, 3, 1, 2, 5, 1, null);
        ResumenAdulto adulto = new ResumenAdulto("Adulto Uno", 1, 1, 0, null, 0, 0, 0,
                List.of(fila(2, 9, "Cuidadora Ana")), List.of(), alertas, null);
        ResumenResponsable responsable = new ResumenResponsable("Cuidadora Ana", 1, 1, 0, null, 0, 0, 0, 100.0, null,
                false);
        ReporteSemanal reporte = new ReporteSemanal(new GrupoReportado(5, "Familia Alertas"),
                Periodo.desdeInicio(java.time.LocalDate.of(2026, 11, 2)), List.of(adulto), List.of(responsable),
                OffsetDateTime.of(2026, 11, 9, 8, 30, 0, 0, SANTIAGO));

        String texto = textoDe(generador.generar(reporte)).replaceAll("\\s+", " ");

        assertThat(texto).contains("Alertas: 6 en total");
        assertThat(texto).contains("Tarea atrasada: 3");
        assertThat(texto).contains("Cita próxima: 1");
        assertThat(texto).contains("Reposición de medicamento: 2");
        assertThat(texto).contains("5 cerradas").contains("1 abierta");
        assertThat(texto).doesNotContain("atrasos").doesNotContain("reposición 2").doesNotContain("citas 1");
    }

    @Test
    void alertasEnSingularYSinAlertas() throws Exception {
        ResumenAlertas una = new ResumenAlertas(1, 1, 0, 0, 1, 0, null);
        ResumenAlertas ninguna = new ResumenAlertas(0, 0, 0, 0, 0, 0, null);
        ResumenAdulto conUna = new ResumenAdulto("Adulto Uno", 1, 1, 0, null, 0, 0, 0,
                List.of(fila(2, 9, "Cuidadora Ana")), List.of(), una, null);
        ResumenAdulto sinAlertas = new ResumenAdulto("Adulto Dos", 1, 1, 0, null, 0, 0, 0,
                List.of(fila(3, 9, "Cuidadora Ana")), List.of(), ninguna, null);
        ResumenResponsable responsable = new ResumenResponsable("Cuidadora Ana", 2, 2, 0, null, 0, 0, 0, 100.0, null,
                false);
        ReporteSemanal reporte = new ReporteSemanal(new GrupoReportado(6, "Familia Singular"),
                Periodo.desdeInicio(java.time.LocalDate.of(2026, 11, 2)), List.of(conUna, sinAlertas),
                List.of(responsable), OffsetDateTime.of(2026, 11, 9, 8, 30, 0, 0, SANTIAGO));

        String texto = textoDe(generador.generar(reporte)).replaceAll("\\s+", " ");

        assertThat(texto).contains("Alertas: 1 en total").contains("Tarea atrasada: 1").contains("1 cerrada")
                .contains("0 abiertas");
        assertThat(texto).contains("Alertas: 0 en total").contains("0 cerradas");
    }

    @Test
    void duracionesDeUnaHoraOMasSeMuestranEnHorasYMinutos() throws Exception {
        ResumenAlertas alertasConPromedioLargo = new ResumenAlertas(1, 1, 0, 0, 1, 0, 434.9);
        ResumenAdulto adulto = new ResumenAdulto("Adulto Uno", 1, 0, 1, 667.5, 0, 0, 0,
                List.of(fila(2, 9, "Cuidadora Ana")), List.of(), alertasConPromedioLargo, null);
        ResumenResponsable responsable = new ResumenResponsable("Cuidadora Ana", 1, 0, 1, 667.5, 0, 0, 0, null, null,
                false);
        ReporteSemanal reporte = new ReporteSemanal(new GrupoReportado(4, "Familia Duraciones"),
                Periodo.desdeInicio(java.time.LocalDate.of(2026, 11, 2)), List.of(adulto), List.of(responsable),
                OffsetDateTime.of(2026, 11, 9, 8, 30, 0, 0, SANTIAGO));

        byte[] pdf = generador.generar(reporte);
        String texto = textoDe(pdf);

        // 667,5 min = 11 h 8 min (redondeado); 434,9 min = 7 h 15 min: más fácil de leer que los
        // minutos crudos.
        assertThat(texto).contains("11 h 8 min");
        assertThat(texto).contains("7 h 15 min");
        assertThat(texto).doesNotContain("667,5 min").doesNotContain("434,9 min");
    }

    @Test
    void pdfMuchasTareasVariasPaginas() throws Exception {
        List<FilaTarea> tareas = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            int dia = 2 + (i % 6);
            tareas.add(fila(dia, 8 + (i % 10), "Cuidadora Ana"));
        }
        ResumenAdulto adulto = adultoConTareas("Adulto Con Muchas Tareas", tareas, false);
        ResumenResponsable responsable = new ResumenResponsable("Cuidadora Ana", 200, 200, 0, null, 0, 0, 0, 100.0,
                null, false);
        ReporteSemanal reporte = new ReporteSemanal(new GrupoReportado(3, "Familia Muchas Tareas"),
                Periodo.desdeInicio(java.time.LocalDate.of(2026, 11, 2)), List.of(adulto), List.of(responsable),
                OffsetDateTime.of(2026, 11, 9, 8, 30, 0, 0, SANTIAGO));

        byte[] pdf = generador.generar(reporte);

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isGreaterThan(1);
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Adulto Con Muchas Tareas");
        }
    }
}
