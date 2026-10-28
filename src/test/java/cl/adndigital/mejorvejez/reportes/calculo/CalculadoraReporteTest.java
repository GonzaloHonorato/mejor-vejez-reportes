package cl.adndigital.mejorvejez.reportes.calculo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import cl.adndigital.mejorvejez.reportes.core.AdultoSemana;
import cl.adndigital.mejorvejez.reportes.core.AlertaSemana;
import cl.adndigital.mejorvejez.reportes.core.DatosSemana;
import cl.adndigital.mejorvejez.reportes.core.Persona;
import cl.adndigital.mejorvejez.reportes.core.RelevoSemana;
import cl.adndigital.mejorvejez.reportes.core.TareaSemana;

class CalculadoraReporteTest {

    private static final ZoneOffset SANTIAGO = ZoneOffset.of("-03:00");
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);
    private static final LocalDate LUNES_ANTERIOR = LocalDate.of(2026, 10, 26);

    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.of("-03:00"));
    private final CalculadoraReporte calculadora = new CalculadoraReporte(reloj);

    private static OffsetDateTime hora(LocalDate dia, int hora, int minuto, int segundo) {
        return OffsetDateTime.of(dia, java.time.LocalTime.of(hora, minuto, segundo), SANTIAGO);
    }

    private static DatosSemana semana(LocalDate inicio, List<AdultoSemana> adultos, List<TareaSemana> tareas,
            List<RelevoSemana> relevos, List<AlertaSemana> alertas) {
        return new DatosSemana(1, "Familia Pérez", inicio, inicio.plusDays(6), adultos, tareas, relevos, alertas);
    }

    private static DatosSemana semanaVacia(LocalDate inicio) {
        return semana(inicio, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void grupoSinTareasSeOmite() {
        DatosSemana actual = semanaVacia(LUNES);
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        Optional<ReporteSemanal> reporte = calculadora.calcular(actual, anterior);

        assertThat(reporte).isEmpty();
    }

    @Test
    void clasificaATiempoConAtrasoYPendiente() {
        Persona cuidador = new Persona(100L, "Cuidadora Ana");
        TareaSemana aTiempoLimite = new TareaSemana(1, 1, "MEDICAMENTO", "Dar pastilla",
                hora(LUNES, 9, 0, 0), 10, "COMPLETADA", hora(LUNES, 9, 10, 0), cuidador, cuidador, false);
        TareaSemana conAtraso = new TareaSemana(2, 1, "MEDICAMENTO", "Dar pastilla tarde",
                hora(LUNES, 9, 0, 0), 10, "COMPLETADA", hora(LUNES, 9, 10, 1), cuidador, cuidador, false);
        TareaSemana pendiente = new TareaSemana(3, 1, "MEDICAMENTO", "Pendiente",
                hora(LUNES, 10, 0, 0), 10, "PENDIENTE", null, null, cuidador, false);
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")),
                List.of(aTiempoLimite, conAtraso, pendiente), List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        ResumenAdulto resumen = reporte.adultos().get(0);
        assertThat(resumen.programadas()).isEqualTo(3);
        assertThat(resumen.aTiempo()).isEqualTo(1);
        assertThat(resumen.conAtraso()).isEqualTo(1);
        assertThat(resumen.pendientes()).isEqualTo(1);
        assertThat(resumen.noRealizadas()).isZero();
        assertThat(resumen.canceladas()).isZero();
        assertThat(resumen.variacion()).isNull();
    }

    @Test
    void redondeoHaciaArribaDelAtraso() {
        Persona cuidador = new Persona(100L, "Cuidadora Ana");
        // 30 segundos de atraso: debe contar como 1 minuto completo.
        TareaSemana conAtrasoCorto = new TareaSemana(1, 1, "MEDICAMENTO", "Dar pastilla",
                hora(LUNES, 9, 0, 0), 0, "COMPLETADA", hora(LUNES, 9, 0, 30), cuidador, cuidador, false);
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")), List.of(conAtrasoCorto),
                List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        ResumenAdulto resumen = reporte.adultos().get(0);
        assertThat(resumen.conAtraso()).isEqualTo(1);
        assertThat(resumen.atrasoPromedioMinutos()).isEqualTo(1.0);
        FilaTarea fila = resumen.tareas().get(0);
        assertThat(fila.minutosAtraso()).isEqualTo(1);
    }

    @Test
    void completadaSinFechaConfirmacionCuentaComoConAtrasoSinNpe() {
        // Dato inconsistente: COMPLETADA sin fechaConfirmacion. Nunca debe lanzar NPE; cuenta
        // como "con atraso" con minutosAtraso null, y queda fuera del promedio de atraso.
        Persona cuidador = new Persona(100L, "Cuidadora Ana");
        TareaSemana sinConfirmacion = new TareaSemana(1, 1, "MEDICAMENTO", "Sin confirmación",
                hora(LUNES, 9, 0, 0), 0, "COMPLETADA", null, null, cuidador, false);
        TareaSemana conAtraso4Min = new TareaSemana(2, 1, "MEDICAMENTO", "Con atraso",
                hora(LUNES, 10, 0, 0), 0, "COMPLETADA", hora(LUNES, 10, 4, 0), cuidador, cuidador, false);
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")),
                List.of(sinConfirmacion, conAtraso4Min), List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        ResumenAdulto resumenAdulto = reporte.adultos().get(0);
        assertThat(resumenAdulto.conAtraso()).isEqualTo(2);
        assertThat(resumenAdulto.atrasoPromedioMinutos()).isEqualTo(4.0); // promedio solo de la que tiene minutos
        FilaTarea filaSinConfirmacion = resumenAdulto.tareas().stream()
                .filter(f -> f.titulo().equals("Sin confirmación")).findFirst().orElseThrow();
        assertThat(filaSinConfirmacion.minutosAtraso()).isNull();
        assertThat(filaSinConfirmacion.codigoEstadoAlCierre()).isEqualTo("COMPLETADA");

        ResumenResponsable resumenResponsable = reporte.responsables().get(0);
        assertThat(resumenResponsable.conAtraso()).isEqualTo(2);
        assertThat(resumenResponsable.atrasoPromedioMinutos()).isEqualTo(4.0);
    }

    @Test
    void atrasoPromedioEnMinutos() {
        Persona cuidador = new Persona(100L, "Cuidadora Ana");
        TareaSemana atraso2Min = new TareaSemana(1, 1, "MEDICAMENTO", "Uno", hora(LUNES, 9, 0, 0), 0, "COMPLETADA",
                hora(LUNES, 9, 2, 0), cuidador, cuidador, false);
        TareaSemana atraso4Min = new TareaSemana(2, 1, "MEDICAMENTO", "Dos", hora(LUNES, 10, 0, 0), 0, "COMPLETADA",
                hora(LUNES, 10, 4, 0), cuidador, cuidador, false);
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")),
                List.of(atraso2Min, atraso4Min), List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        assertThat(reporte.adultos().get(0).atrasoPromedioMinutos()).isEqualTo(3.0);
    }

    @Test
    void atribuyeAlResponsableAlCierreYSinAsignar() {
        Persona ana = new Persona(100L, "Cuidadora Ana");
        TareaSemana conResponsable = new TareaSemana(1, 1, "MEDICAMENTO", "Uno", hora(LUNES, 9, 0, 0), 10,
                "COMPLETADA", hora(LUNES, 9, 5, 0), ana, ana, false);
        TareaSemana sinResponsable = new TareaSemana(2, 1, "MEDICAMENTO", "Dos", hora(LUNES, 10, 0, 0), 10,
                "PENDIENTE", null, null, null, false);
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")),
                List.of(conResponsable, sinResponsable), List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        assertThat(reporte.responsables()).extracting(ResumenResponsable::nombre)
                .containsExactly("Cuidadora Ana", "Sin asignar");
        ResumenResponsable responsableAna = reporte.responsables().get(0);
        assertThat(responsableAna.asignadas()).isEqualTo(1);
        assertThat(responsableAna.aTiempo()).isEqualTo(1);
        ResumenResponsable sinAsignar = reporte.responsables().get(1);
        assertThat(sinAsignar.asignadas()).isEqualTo(1);
        assertThat(sinAsignar.pendientes()).isEqualTo(1);
        assertThat(sinAsignar.senalSeguimiento()).isFalse();

        FilaTarea filaSinResponsable = reporte.adultos().get(0).tareas().stream()
                .filter(f -> f.titulo().equals("Dos")).findFirst().orElseThrow();
        assertThat(filaSinResponsable.responsable()).isEqualTo("Sin asignar");
    }

    @Test
    void responsableSoloEnSemanaAnteriorNoApareceEnLaActual() {
        Persona ana = new Persona(100L, "Cuidadora Ana");
        Persona pedro = new Persona(999L, "Cuidador Pedro");
        TareaSemana tareaActual = tareaATiempo(1, 1, ana, hora(LUNES, 9, 0, 0));
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")), List.of(tareaActual),
                List.of(), List.of());

        // "Pedro" solo tuvo tareas la semana anterior: no debe aparecer en los responsables de esta semana.
        TareaSemana tareaAnaAnterior = tareaATiempo(2, 1, ana, hora(LUNES_ANTERIOR, 9, 0, 0));
        TareaSemana tareaPedroAnterior = tareaATiempo(3, 1, pedro, hora(LUNES_ANTERIOR, 10, 0, 0));
        DatosSemana anterior = semana(LUNES_ANTERIOR, List.of(new AdultoSemana(1, "Adulto Uno")),
                List.of(tareaAnaAnterior, tareaPedroAnterior), List.of(), List.of());

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        assertThat(reporte.responsables()).extracting(ResumenResponsable::nombre)
                .containsExactly("Cuidadora Ana")
                .doesNotContain("Cuidador Pedro");
    }

    @Test
    void ordenConSinAsignarAlFinal() {
        Persona ana = new Persona(100L, "Ana");
        Persona zoe = new Persona(200L, "Zoe");
        TareaSemana tareaAna = new TareaSemana(1, 1, "MEDICAMENTO", "Uno", hora(LUNES, 9, 0, 0), 10, "COMPLETADA",
                hora(LUNES, 9, 5, 0), ana, ana, false);
        TareaSemana tareaZoe = new TareaSemana(2, 2, "MEDICAMENTO", "Dos", hora(LUNES, 9, 0, 0), 10, "COMPLETADA",
                hora(LUNES, 9, 5, 0), zoe, zoe, false);
        TareaSemana tareaSinAsignar = new TareaSemana(3, 1, "MEDICAMENTO", "Tres", hora(LUNES, 10, 0, 0), 10,
                "PENDIENTE", null, null, null, false);
        DatosSemana actual = semana(LUNES,
                List.of(new AdultoSemana(1, "Zoe Adulta"), new AdultoSemana(2, "Ana Adulta")),
                List.of(tareaAna, tareaZoe, tareaSinAsignar), List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        assertThat(reporte.adultos()).extracting(ResumenAdulto::nombre).containsExactly("Ana Adulta", "Zoe Adulta");
        assertThat(reporte.responsables()).extracting(ResumenResponsable::nombre)
                .containsExactly("Ana", "Zoe", "Sin asignar");
    }

    @Test
    void ordenaNombresConTildesComoElAlfabetoEspanol() {
        // Con String.compareTo, "Ángela" y "Óscar" quedarían después de "Zoila" (el carácter
        // acentuado tiene un valor más alto que "Z"); en español van junto al resto de la A y la O.
        Persona angela = new Persona(1L, "Ángela");
        Persona oscar = new Persona(2L, "Óscar");
        Persona zoila = new Persona(3L, "Zoila");
        TareaSemana tareaZoila = new TareaSemana(1, 3, "MEDICAMENTO", "Uno", hora(LUNES, 9, 0, 0), 10, "COMPLETADA",
                hora(LUNES, 9, 5, 0), zoila, zoila, false);
        TareaSemana tareaAngela = new TareaSemana(2, 1, "MEDICAMENTO", "Dos", hora(LUNES, 9, 0, 0), 10, "COMPLETADA",
                hora(LUNES, 9, 5, 0), angela, angela, false);
        TareaSemana tareaOscar = new TareaSemana(3, 2, "MEDICAMENTO", "Tres", hora(LUNES, 9, 0, 0), 10, "COMPLETADA",
                hora(LUNES, 9, 5, 0), oscar, oscar, false);
        DatosSemana actual = semana(LUNES,
                List.of(new AdultoSemana(3, "Zoila"), new AdultoSemana(1, "Ángela"), new AdultoSemana(2, "Óscar")),
                List.of(tareaZoila, tareaAngela, tareaOscar), List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        assertThat(reporte.adultos()).extracting(ResumenAdulto::nombre)
                .containsExactly("Ángela", "Óscar", "Zoila");
        assertThat(reporte.responsables()).extracting(ResumenResponsable::nombre)
                .containsExactly("Ángela", "Óscar", "Zoila");
    }

    @Test
    void porcentajeExcluyeCanceladas() {
        Persona ana = new Persona(100L, "Cuidadora Ana");
        TareaSemana aTiempo = new TareaSemana(1, 1, "MEDICAMENTO", "Uno", hora(LUNES, 9, 0, 0), 10, "COMPLETADA",
                hora(LUNES, 9, 5, 0), ana, ana, false);
        TareaSemana cancelada = new TareaSemana(2, 1, "MEDICAMENTO", "Dos", hora(LUNES, 10, 0, 0), 10, "CANCELADA",
                null, null, ana, false);
        TareaSemana pendiente1 = new TareaSemana(3, 1, "MEDICAMENTO", "Tres", hora(LUNES, 11, 0, 0), 10, "PENDIENTE",
                null, null, ana, false);
        TareaSemana pendiente2 = new TareaSemana(4, 1, "MEDICAMENTO", "Cuatro", hora(LUNES, 12, 0, 0), 10,
                "PENDIENTE", null, null, ana, false);
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")),
                List.of(aTiempo, cancelada, pendiente1, pendiente2), List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        ResumenResponsable responsable = reporte.responsables().get(0);
        assertThat(responsable.asignadas()).isEqualTo(4);
        // divisor = asignadas(4) - canceladas(1) = 3; a tiempo = 1 -> 33.33...%
        assertThat(responsable.porcentajeATiempo()).isCloseTo(33.3333, within(0.01));
        assertThat(responsable.canceladas()).isEqualTo(1);
        // Las categorías de "asignadas" deben sumar exactamente el total: ninguna tarea se pierde
        // ni se cuenta dos veces entre a tiempo, con atraso, no realizadas, canceladas y pendientes.
        assertThat(responsable.asignadas()).isEqualTo(responsable.aTiempo() + responsable.conAtraso()
                + responsable.noRealizadas() + responsable.canceladas() + responsable.pendientes());
    }

    @Test
    void porcentajeNoAplicableConDivisorCero() {
        Persona ana = new Persona(100L, "Cuidadora Ana");
        TareaSemana cancelada1 = new TareaSemana(1, 1, "MEDICAMENTO", "Uno", hora(LUNES, 9, 0, 0), 10, "CANCELADA",
                null, null, ana, false);
        TareaSemana cancelada2 = new TareaSemana(2, 1, "MEDICAMENTO", "Dos", hora(LUNES, 10, 0, 0), 10, "CANCELADA",
                null, null, ana, false);
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")),
                List.of(cancelada1, cancelada2), List.of(), List.of());
        DatosSemana anterior = semanaVacia(LUNES_ANTERIOR);

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        ResumenResponsable responsable = reporte.responsables().get(0);
        assertThat(responsable.porcentajeATiempo()).isNull();
        assertThat(responsable.senalSeguimiento()).isFalse();
    }

    @Test
    void senalSeguimientoDosSemanasBajo70() {
        Persona bajo = new Persona(1L, "Cuidador Bajo");
        Persona mixto = new Persona(2L, "Cuidador Mixto");
        Persona nuevo = new Persona(3L, "Cuidador Nuevo");

        // Esta semana: 60 % para los tres (6 de 10 a tiempo).
        List<TareaSemana> actuales = new ArrayList<>();
        actuales.addAll(tareasConPorcentaje(10, bajo, LUNES, 100));
        actuales.addAll(tareasConPorcentaje(10, mixto, LUNES, 200));
        actuales.addAll(tareasConPorcentaje(10, nuevo, LUNES, 300));
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")), actuales, List.of(),
                List.of());

        // Semana anterior: 65 % para "Bajo" (activa la señal), 75 % para "Mixto" (no la activa),
        // nada para "Nuevo" (sin semana anterior).
        List<TareaSemana> anteriores = new ArrayList<>();
        anteriores.addAll(tareasConPorcentajeExacto(20, 13, bajo, LUNES_ANTERIOR, 400));
        anteriores.addAll(tareasConPorcentajeExacto(4, 3, mixto, LUNES_ANTERIOR, 500));
        DatosSemana anterior = semana(LUNES_ANTERIOR, List.of(new AdultoSemana(1, "Adulto Uno")), anteriores,
                List.of(), List.of());

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        ResumenResponsable resumenBajo = buscarResponsable(reporte, "Cuidador Bajo");
        ResumenResponsable resumenMixto = buscarResponsable(reporte, "Cuidador Mixto");
        ResumenResponsable resumenNuevo = buscarResponsable(reporte, "Cuidador Nuevo");

        assertThat(resumenBajo.porcentajeATiempo()).isEqualTo(60.0);
        assertThat(resumenBajo.senalSeguimiento()).isTrue();
        assertThat(resumenMixto.senalSeguimiento()).isFalse();
        assertThat(resumenNuevo.senalSeguimiento()).isFalse();
        assertThat(resumenNuevo.variacion()).isNull();
    }

    @Test
    void variacionContraSemanaAnterior() {
        Persona ana = new Persona(100L, "Cuidadora Ana");
        // Actual: 5 tareas, 3 a tiempo, 1 con atraso, 1 no realizada, 0 pendientes, 0 canceladas -> 60 %.
        List<TareaSemana> actuales = List.of(
                tareaATiempo(1, 1, ana, hora(LUNES, 9, 0, 0)),
                tareaATiempo(2, 1, ana, hora(LUNES, 10, 0, 0)),
                tareaATiempo(3, 1, ana, hora(LUNES, 11, 0, 0)),
                tareaConAtraso(4, 1, ana, hora(LUNES, 12, 0, 0), 5),
                new TareaSemana(5, 1, "MEDICAMENTO", "No realizada", hora(LUNES, 13, 0, 0), 10, "NO_REALIZADA",
                        null, null, ana, false));
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(1, "Adulto Uno")), actuales, List.of(),
                List.of());

        // Anterior: 4 tareas, 1 a tiempo, 1 con atraso, 0 no realizadas, 2 pendientes -> 25 %.
        List<TareaSemana> anteriores = List.of(
                tareaATiempo(10, 1, ana, hora(LUNES_ANTERIOR, 9, 0, 0)),
                tareaConAtraso(11, 1, ana, hora(LUNES_ANTERIOR, 10, 0, 0), 5),
                new TareaSemana(12, 1, "MEDICAMENTO", "Pendiente", hora(LUNES_ANTERIOR, 11, 0, 0), 10, "PENDIENTE",
                        null, null, ana, false),
                new TareaSemana(13, 1, "MEDICAMENTO", "Pendiente2", hora(LUNES_ANTERIOR, 12, 0, 0), 10, "PENDIENTE",
                        null, null, ana, false));
        DatosSemana anterior = semana(LUNES_ANTERIOR, List.of(new AdultoSemana(1, "Adulto Uno")), anteriores,
                List.of(), List.of());

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        Variacion variacion = reporte.adultos().get(0).variacion();
        assertThat(variacion).isNotNull();
        assertThat(variacion.programadas()).isEqualTo(1); // 5 - 4
        assertThat(variacion.aTiempo()).isEqualTo(2); // 3 - 1
        assertThat(variacion.conAtraso()).isZero(); // 1 - 1
        assertThat(variacion.noRealizadas()).isEqualTo(1); // 1 - 0
        assertThat(variacion.pendientes()).isEqualTo(-2); // 0 - 2
        assertThat(variacion.porcentajeATiempoPuntos()).isEqualTo(35.0); // 60 - 25
    }

    @Test
    void emparejamientoPorIdEntreSemanas() {
        Persona ana = new Persona(100L, "Cuidadora Ana");
        // El adulto conserva el mismo id pero cambia de nombre entre semanas: igual se empareja por id.
        TareaSemana tareaActual = tareaATiempo(1, 5, ana, hora(LUNES, 9, 0, 0));
        DatosSemana actual = semana(LUNES, List.of(new AdultoSemana(5, "Pedro Nuevo Nombre")), List.of(tareaActual),
                List.of(), List.of());

        TareaSemana tareaAnterior = tareaATiempo(2, 5, ana, hora(LUNES_ANTERIOR, 9, 0, 0));
        DatosSemana anterior = semana(LUNES_ANTERIOR, List.of(new AdultoSemana(5, "Pedro Nombre Viejo")),
                List.of(tareaAnterior), List.of(), List.of());

        ReporteSemanal reporte = calculadora.calcular(actual, anterior).orElseThrow();

        ResumenAdulto resumen = reporte.adultos().get(0);
        assertThat(resumen.nombre()).isEqualTo("Pedro Nuevo Nombre");
        assertThat(resumen.variacion()).isNotNull();
        assertThat(resumen.variacion().programadas()).isZero();

        // Responsable "Sin asignar" también se empareja consigo mismo entre semanas (por idUsuario null).
        TareaSemana sinAsignarActual = new TareaSemana(3, 5, "MEDICAMENTO", "Sin responsable", hora(LUNES, 10, 0, 0),
                10, "PENDIENTE", null, null, null, false);
        DatosSemana actualConSinAsignar = semana(LUNES, List.of(new AdultoSemana(5, "Pedro Nuevo Nombre")),
                List.of(tareaActual, sinAsignarActual), List.of(), List.of());
        TareaSemana sinAsignarAnterior = new TareaSemana(4, 5, "MEDICAMENTO", "Sin responsable anterior",
                hora(LUNES_ANTERIOR, 10, 0, 0), 10, "PENDIENTE", null, null, null, false);
        DatosSemana anteriorConSinAsignar = semana(LUNES_ANTERIOR, List.of(new AdultoSemana(5, "Pedro Nombre Viejo")),
                List.of(tareaAnterior, sinAsignarAnterior), List.of(), List.of());

        ReporteSemanal reporteConSinAsignar = calculadora.calcular(actualConSinAsignar, anteriorConSinAsignar)
                .orElseThrow();
        ResumenResponsable sinAsignar = buscarResponsable(reporteConSinAsignar, "Sin asignar");
        assertThat(sinAsignar.variacion()).isNotNull();
    }

    private static TareaSemana tareaATiempo(long idTarea, long idAdulto, Persona responsable,
            OffsetDateTime fechaHorario) {
        return new TareaSemana(idTarea, idAdulto, "MEDICAMENTO", "Tarea " + idTarea, fechaHorario, 10, "COMPLETADA",
                fechaHorario.plusMinutes(5), responsable, responsable, false);
    }

    private static TareaSemana tareaConAtraso(long idTarea, long idAdulto, Persona responsable,
            OffsetDateTime fechaHorario, int minutosAtraso) {
        return new TareaSemana(idTarea, idAdulto, "MEDICAMENTO", "Tarea " + idTarea, fechaHorario, 0, "COMPLETADA",
                fechaHorario.plusMinutes(minutosAtraso), responsable, responsable, false);
    }

    /** {@code total} tareas con el porcentaje exacto de a tiempo dado (en el dominio 0-100, debe dividir limpio). */
    private static List<TareaSemana> tareasConPorcentaje(int total, Persona responsable, LocalDate dia,
            long idBase) {
        return tareasConPorcentajeExacto(total, total * 60 / 100, responsable, dia, idBase);
    }

    private static List<TareaSemana> tareasConPorcentajeExacto(int total, int aTiempo, Persona responsable,
            LocalDate dia, long idBase) {
        List<TareaSemana> tareas = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            long idTarea = idBase + i;
            OffsetDateTime horario = hora(dia, 8, 0, 0).plusHours(i);
            if (i < aTiempo) {
                tareas.add(tareaATiempo(idTarea, 1, responsable, horario));
            } else {
                tareas.add(new TareaSemana(idTarea, 1, "MEDICAMENTO", "Tarea " + idTarea, horario, 10,
                        "NO_REALIZADA", null, null, responsable, false));
            }
        }
        return tareas;
    }

    private static ResumenResponsable buscarResponsable(ReporteSemanal reporte, String nombre) {
        return reporte.responsables().stream().filter(r -> r.nombre().equals(nombre)).findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró el responsable " + nombre));
    }
}
