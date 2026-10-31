package cl.adndigital.mejorvejez.reportes.pdf;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import cl.adndigital.mejorvejez.reportes.calculo.FilaTarea;
import cl.adndigital.mejorvejez.reportes.calculo.ReporteSemanal;
import cl.adndigital.mejorvejez.reportes.calculo.ResumenAdulto;
import cl.adndigital.mejorvejez.reportes.calculo.ResumenAlertas;
import cl.adndigital.mejorvejez.reportes.calculo.ResumenResponsable;
import cl.adndigital.mejorvejez.reportes.calculo.Variacion;
import cl.adndigital.mejorvejez.reportes.comun.ZonaNegocio;
import cl.adndigital.mejorvejez.reportes.core.RelevoSemana;

/**
 * Genera el PDF del reporte semanal con OpenPDF, siempre en memoria (nunca
 * pasa por disco, ni en las pruebas). Página A4 horizontal, para que las
 * tablas más anchas no corten sus encabezados. Usa Helvetica con
 * codificación WinAnsi (tildes y ñ funcionan; nunca caracteres fuera de
 * Latin-1) y formatea los números con el locale "es-CL".
 *
 * <p>Secciones: encabezado (grupo, período y fecha de generación), una
 * sección por adulto (resumen con su variación contra la semana anterior,
 * tabla de tareas, relevos y alertas), la tabla "Cumplimiento por
 * responsable" (con la variación y la señal "Requiere seguimiento" solo
 * cuando corresponde) y la nota al pie.
 */
@Component
public class GeneradorPdf {

    private static final Locale LOCALE_NUMEROS = Locale.of("es", "CL");

    private static final String NOTA_PIE = "Reporte generado automáticamente por Cuida+. "
            + "No se almacena una copia.";
    private static final String SIN_VALOR = "-";
    private static final String SIN_SEMANA_ANTERIOR = "Sin semana anterior";
    private static final String SENAL_SEGUIMIENTO = "Requiere seguimiento";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private static final Font FUENTE_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font FUENTE_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 11);
    private static final Font FUENTE_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font FUENTE_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font FUENTE_TEXTO_NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font FUENTE_TABLA_ENCABEZADO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font FUENTE_TABLA = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font FUENTE_PIE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

    /** Genera el PDF completo del reporte en memoria y devuelve sus bytes; nunca escribe a disco. */
    public byte[] generar(ReporteSemanal reporte) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), 36, 36, 45, 40);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();
            agregarEncabezado(documento, reporte);
            for (ResumenAdulto adulto : reporte.adultos()) {
                agregarSeccionAdulto(documento, adulto);
            }
            agregarCumplimientoPorResponsable(documento, reporte.responsables());
            agregarPie(documento);
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte semanal.", ex);
        } finally {
            documento.close();
        }
        return salida.toByteArray();
    }

    private void agregarEncabezado(Document documento, ReporteSemanal reporte) throws DocumentException {
        documento.add(new Paragraph("Cuida+ · Reporte semanal " + reporte.grupo().nombre(), FUENTE_TITULO));
        String periodo = "lunes " + reporte.periodo().inicio().format(FECHA) + " a domingo "
                + reporte.periodo().fin().format(FECHA);
        documento.add(new Paragraph("Período: " + periodo, FUENTE_SUBTITULO));
        String generado = reporte.generadoEn().atZoneSameInstant(ZonaNegocio.ZONA).format(FECHA_HORA);
        Paragraph fechaGeneracion = new Paragraph("Generado el " + generado, FUENTE_SUBTITULO);
        fechaGeneracion.setSpacingAfter(10f);
        documento.add(fechaGeneracion);
    }

    private void agregarSeccionAdulto(Document documento, ResumenAdulto adulto) throws DocumentException {
        Paragraph titulo = new Paragraph(adulto.nombre(), FUENTE_SECCION);
        titulo.setSpacingBefore(14f);
        titulo.setSpacingAfter(4f);
        documento.add(titulo);

        documento.add(parrafoResumenAdulto(adulto));
        documento.add(parrafoVariacionAdulto(adulto));

        if (!adulto.tareas().isEmpty()) {
            Paragraph subtitulo = new Paragraph("Tareas", FUENTE_TEXTO_NEGRITA);
            subtitulo.setSpacingBefore(6f);
            subtitulo.setSpacingAfter(3f);
            documento.add(subtitulo);
            documento.add(tablaTareas(adulto.tareas()));
        }
        if (!adulto.relevos().isEmpty()) {
            Paragraph subtitulo = new Paragraph("Relevos", FUENTE_TEXTO_NEGRITA);
            subtitulo.setSpacingBefore(6f);
            subtitulo.setSpacingAfter(3f);
            documento.add(subtitulo);
            documento.add(tablaRelevos(adulto.relevos()));
        }
        documento.add(parrafoAlertas(adulto.alertas()));
    }

    private Paragraph parrafoResumenAdulto(ResumenAdulto adulto) {
        StringBuilder texto = new StringBuilder();
        texto.append("Programadas: ").append(adulto.programadas());
        texto.append(" · A tiempo: ").append(adulto.aTiempo());
        texto.append(" · Con atraso: ").append(adulto.conAtraso());
        texto.append(" (prom. ").append(formatearMinutos(adulto.atrasoPromedioMinutos())).append(")");
        texto.append(" · No realizadas: ").append(adulto.noRealizadas());
        texto.append(" · Canceladas: ").append(adulto.canceladas());
        texto.append(" · Pendientes: ").append(adulto.pendientes());
        Paragraph parrafo = new Paragraph(texto.toString(), FUENTE_TEXTO);
        parrafo.setSpacingAfter(2f);
        return parrafo;
    }

    private Paragraph parrafoVariacionAdulto(ResumenAdulto adulto) {
        String texto = "Variación vs. semana anterior: " + textoVariacionOSinSemanaAnterior(adulto.variacion());
        Paragraph parrafo = new Paragraph(texto, FUENTE_TEXTO);
        parrafo.setSpacingAfter(4f);
        return parrafo;
    }

    private PdfPTable tablaTareas(List<FilaTarea> tareas) throws DocumentException {
        PdfPTable tabla = new PdfPTable(8);
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(8f);
        tabla.setWidths(new float[] { 10f, 14f, 10f, 11f, 9f, 8f, 28f, 10f });
        tabla.setHeaderRows(1);
        for (String encabezado : new String[] { "Tipo", "Título", "Fecha y hora", "Responsable", "Estado",
                "Atraso (min)", "Confirmó", "Reasignada" }) {
            agregarCeldaEncabezado(tabla, encabezado);
        }
        for (FilaTarea fila : tareas) {
            agregarCelda(tabla, EtiquetasCatalogo.tipoTarea(fila.tipoTarea()));
            agregarCelda(tabla, fila.titulo());
            agregarCelda(tabla, formatearFechaHora(fila.fechaHorario()));
            agregarCelda(tabla, fila.responsable());
            agregarCelda(tabla, EtiquetasCatalogo.estadoTarea(fila.codigoEstadoAlCierre()));
            agregarCelda(tabla, fila.minutosAtraso() == null ? SIN_VALOR : String.valueOf(fila.minutosAtraso()));
            agregarCelda(tabla, formatearConfirmacion(fila.confirmadoPor(), fila.fechaConfirmacion()));
            agregarCelda(tabla, fila.reasignada() ? "Sí" : "No");
        }
        return tabla;
    }

    private PdfPTable tablaRelevos(List<RelevoSemana> relevos) throws DocumentException {
        PdfPTable tabla = new PdfPTable(6);
        tabla.setWidthPercentage(100);
        tabla.setSpacingAfter(8f);
        tabla.setHeaderRows(1);
        for (String encabezado : new String[] { "Fecha", "Saliente", "Entrante", "Tareas pendientes",
                "Alertas abiertas", "Estado" }) {
            agregarCeldaEncabezado(tabla, encabezado);
        }
        for (RelevoSemana relevo : relevos) {
            agregarCelda(tabla, formatearFechaHora(relevo.fechaTraspaso()));
            agregarCelda(tabla, relevo.saliente() == null ? SIN_VALOR : relevo.saliente().nombre());
            agregarCelda(tabla, relevo.entrante() == null ? SIN_VALOR : relevo.entrante().nombre());
            agregarCelda(tabla, String.valueOf(relevo.totalTareas()));
            agregarCelda(tabla, String.valueOf(relevo.totalAlertas()));
            agregarCelda(tabla, EtiquetasCatalogo.estadoRelevo(relevo.codigoEstado()));
        }
        return tabla;
    }

    private Paragraph parrafoAlertas(ResumenAlertas alertas) {
        StringBuilder texto = new StringBuilder();
        texto.append("Alertas: ").append(alertas.total()).append(" en total");
        texto.append(" (").append(EtiquetasCatalogo.tipoAlerta("ATRASO_TAREA")).append(": ").append(alertas.atrasos());
        texto.append(", ").append(EtiquetasCatalogo.tipoAlerta("CITA_PROXIMA")).append(": ").append(alertas.citas());
        texto.append(", ").append(EtiquetasCatalogo.tipoAlerta("REPOSICION_MEDICAMENTO")).append(": ")
                .append(alertas.reposiciones()).append(")");
        texto.append(" · ").append(conteo(alertas.cerradas(), "cerrada", "cerradas"));
        texto.append(" · ").append(conteo(alertas.abiertas(), "abierta", "abiertas"));
        texto.append(" · prom. cierre: ").append(formatearMinutos(alertas.minutosPromedioCierre()));
        Paragraph parrafo = new Paragraph(texto.toString(), FUENTE_TEXTO);
        parrafo.setSpacingAfter(4f);
        return parrafo;
    }

    /** Cantidad con su palabra en singular o plural ("1 abierta", "0 abiertas"). */
    private static String conteo(int cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    private void agregarCumplimientoPorResponsable(Document documento, List<ResumenResponsable> responsables)
            throws DocumentException {
        Paragraph titulo = new Paragraph("Cumplimiento por responsable", FUENTE_SECCION);
        titulo.setSpacingBefore(16f);
        titulo.setSpacingAfter(4f);
        documento.add(titulo);

        PdfPTable tabla = new PdfPTable(11);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[] { 11f, 6f, 6f, 7f, 9f, 9f, 8f, 6f, 7f, 16f, 15f });
        tabla.setHeaderRows(1);
        for (String encabezado : new String[] { "Responsable", "Asig.", "A tiempo", "Con atraso", "Atraso prom.",
                "No realizadas", "Canceladas", "Pend.", "% a tiempo", "Variación", "Señal" }) {
            agregarCeldaEncabezado(tabla, encabezado);
        }
        for (ResumenResponsable responsable : responsables) {
            agregarCelda(tabla, responsable.nombre());
            agregarCelda(tabla, String.valueOf(responsable.asignadas()));
            agregarCelda(tabla, String.valueOf(responsable.aTiempo()));
            agregarCelda(tabla, String.valueOf(responsable.conAtraso()));
            agregarCelda(tabla, formatearMinutos(responsable.atrasoPromedioMinutos()));
            agregarCelda(tabla, String.valueOf(responsable.noRealizadas()));
            agregarCelda(tabla, String.valueOf(responsable.canceladas()));
            agregarCelda(tabla, String.valueOf(responsable.pendientes()));
            agregarCelda(tabla, formatearPorcentaje(responsable.porcentajeATiempo()));
            agregarCelda(tabla, formatearVariacion(responsable.variacion()));
            agregarCelda(tabla, responsable.senalSeguimiento() ? SENAL_SEGUIMIENTO : SIN_VALOR);
        }
        documento.add(tabla);
    }

    private void agregarPie(Document documento) throws DocumentException {
        Paragraph pie = new Paragraph(NOTA_PIE, FUENTE_PIE);
        pie.setSpacingBefore(16f);
        documento.add(pie);
    }

    private void agregarCeldaEncabezado(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Paragraph(texto, FUENTE_TABLA_ENCABEZADO));
        celda.setHorizontalAlignment(Element.ALIGN_LEFT);
        celda.setPadding(4f);
        tabla.addCell(celda);
    }

    private void agregarCelda(PdfPTable tabla, String texto) {
        PdfPCell celda = new PdfPCell(new Paragraph(texto == null ? SIN_VALOR : texto, FUENTE_TABLA));
        celda.setHorizontalAlignment(Element.ALIGN_LEFT);
        celda.setPadding(4f);
        tabla.addCell(celda);
    }

    private String formatearFechaHora(OffsetDateTime fechaHora) {
        if (fechaHora == null) {
            return SIN_VALOR;
        }
        return fechaHora.atZoneSameInstant(ZonaNegocio.ZONA).format(FECHA_HORA);
    }

    private String formatearConfirmacion(String confirmadoPor, OffsetDateTime fechaConfirmacion) {
        if (confirmadoPor == null && fechaConfirmacion == null) {
            return SIN_VALOR;
        }
        String nombre = confirmadoPor == null ? SIN_VALOR : confirmadoPor;
        return nombre + " (" + formatearFechaHora(fechaConfirmacion) + ")";
    }

    /**
     * Minutos como "X,X min" si son menos de una hora; si no, como "H h M min" (minutos
     * redondeados al entero más cercano), más fácil de leer que una cifra grande de minutos.
     */
    private String formatearMinutos(Double minutos) {
        if (minutos == null) {
            return SIN_VALOR;
        }
        if (minutos < 60) {
            return String.format(LOCALE_NUMEROS, "%.1f min", minutos);
        }
        long totalRedondeado = Math.round(minutos);
        return (totalRedondeado / 60) + " h " + (totalRedondeado % 60) + " min";
    }

    private String formatearPorcentaje(Double porcentaje) {
        return porcentaje == null ? SIN_VALOR : String.format(LOCALE_NUMEROS, "%.1f%%", porcentaje);
    }

    private String textoVariacionOSinSemanaAnterior(Variacion variacion) {
        return variacion == null ? SIN_SEMANA_ANTERIOR : formatearVariacion(variacion);
    }

    private String formatearVariacion(Variacion variacion) {
        if (variacion == null) {
            return SIN_VALOR;
        }
        String puntos = variacion.porcentajeATiempoPuntos() == null ? SIN_VALOR
                : String.format(LOCALE_NUMEROS, "%+.1f pts", variacion.porcentajeATiempoPuntos());
        return "Prog " + conSigno(variacion.programadas()) + " · A tiempo " + conSigno(variacion.aTiempo())
                + " · Atraso " + conSigno(variacion.conAtraso()) + " · No realiz. "
                + conSigno(variacion.noRealizadas()) + " · Pend. " + conSigno(variacion.pendientes()) + " · % "
                + puntos;
    }

    private String conSigno(int valor) {
        return valor >= 0 ? "+" + valor : String.valueOf(valor);
    }
}
