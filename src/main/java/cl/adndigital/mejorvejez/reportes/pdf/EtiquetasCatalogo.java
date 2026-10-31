package cl.adndigital.mejorvejez.reportes.pdf;

import java.util.Map;

/**
 * Traduce los códigos reales del catálogo del core (tipo de tarea, estado
 * de tarea, estado de relevo y tipo de alerta) a una etiqueta legible en
 * español para el PDF, con las mismas etiquetas que muestra la web. Un código que no está en el mapa se muestra tal cual, para que un
 * catálogo nuevo nunca rompa la generación del reporte.
 */
final class EtiquetasCatalogo {

    private static final Map<String, String> TIPOS_TAREA = Map.of(
            "MEDICAMENTOS", "Medicamentos",
            "COMPRA_RETIRO_MEDICAMENTOS", "Compra o retiro de medicamentos",
            "CITAS_CONTROLES_MEDICOS", "Cita o control médico");

    private static final Map<String, String> ESTADOS_TAREA = Map.of(
            "PENDIENTE", "Pendiente",
            "EN_CURSO", "En curso",
            "COMPLETADA", "Completada",
            "NO_REALIZADA", "No realizada",
            "CANCELADA", "Cancelada");

    private static final Map<String, String> ESTADOS_RELEVO = Map.of(
            "BORRADOR", "Borrador",
            "COMPLETADA", "Completada",
            "CANCELADA", "Cancelada");

    private static final Map<String, String> TIPOS_ALERTA = Map.of(
            "ATRASO_TAREA", "Tarea atrasada",
            "REPOSICION_MEDICAMENTO", "Reposición de medicamento",
            "CITA_PROXIMA", "Cita próxima");

    private EtiquetasCatalogo() {
    }

    static String tipoTarea(String codigo) {
        return etiqueta(TIPOS_TAREA, codigo);
    }

    static String estadoTarea(String codigo) {
        return etiqueta(ESTADOS_TAREA, codigo);
    }

    static String estadoRelevo(String codigo) {
        return etiqueta(ESTADOS_RELEVO, codigo);
    }

    static String tipoAlerta(String codigo) {
        return etiqueta(TIPOS_ALERTA, codigo);
    }

    private static String etiqueta(Map<String, String> mapa, String codigo) {
        if (codigo == null) {
            return null;
        }
        return mapa.getOrDefault(codigo, codigo);
    }
}
