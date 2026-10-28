# mejor-vejez-reportes

Microservicio de reportes de Mejor Vejez: cada lunes genera, en memoria, un PDF con el cumplimiento de la semana anterior por grupo familiar y se lo entrega al microservicio de alertas y notificaciones para que lo envíe por correo. No tiene base de datos ni guarda archivos: consulta los datos al servicio core por REST.

Forma parte de la arquitectura de microservicios de Mejor Vejez:

| Microservicio | Repositorio | Puerto | Base de datos |
|---|---|---|---|
| Core (reglas del cuidado) | `mejor-vejez-api` | 8080 | Oracle (única) |
| Alertas y notificaciones | `mejor-vejez-alertas` | 8081 | No |
| Reportes | `mejor-vejez-reportes` | 8082 | No |

La aplicación web (`mejor-vejez-web`, puerto 4200) solo habla con el core. Los microservicios se comunican por REST en rutas `/api/interno/**`, protegidas con la cabecera `X-Clave-Servicio` (variable `CLAVE_SERVICIO`, la misma en los tres; comparación en tiempo constante, 401 "Clave de servicio inválida" si falta o no coincide).

## Requisitos

- Java 21 o superior (no hace falta instalar Maven: se usa `./mvnw`).

## Rutas que expone

| Método y ruta | Auth | Quién la llama | Para qué |
|---|---|---|---|
| `GET /api/salud` | pública | Dokploy / humano | `200` si el proceso vive (healthcheck) |
| `POST /api/interno/procesos/reporte-semanal?inicio=YYYY-MM-DD` | `X-Clave-Servicio` | Core (`/api/administracion/procesos/reporte-semanal`) | `inicio` opcional (por omisión, el lunes de la semana anterior; debe ser un lunes y la semana debe estar cerrada, si no, 400). Genera y envía el PDF semanal de cada grupo; devuelve `ResumenReportes(idEjecucion, gruposProcesados, enviados, fallidos, omitidos)`. El cron (`REPORTES_CRON`) no llama esta ruta por HTTP: dispara la misma lógica en el propio proceso |

## Rutas que consume

- En el core (`CORE_URL`), con `X-Clave-Servicio`:
  - `GET /api/interno/reportes/grupos` — grupos activos con sus destinatarios.
  - `GET /api/interno/reportes/grupos/{idGrupo}/semana?inicio=YYYY-MM-DD` — datos de la semana (tareas, relevos, alertas) de un grupo.
  - `POST /api/interno/reportes/envios` — reserva el envío de un grupo/semana (idempotente).
  - `PUT /api/interno/reportes/envios/{idEnvio}/resultado` — informa a qué destinatarios se les envió el correo.
  - `POST /api/interno/ejecuciones` y `PUT /api/interno/ejecuciones/{idEjecucion}` — registra el inicio y el cierre de la corrida.
- En alertas (`ALERTAS_URL`), con `X-Clave-Servicio`:
  - `POST /api/interno/envios-reporte` — entrega el PDF (base64) y la lista de destinatarios para que alertas lo envíe por correo.

## Variables de entorno

Ver también `.env.example`.

| Variable | Obligatoria | Por omisión | Uso |
|---|---|---|---|
| `SERVER_PORT` | no | `8082` | Puerto HTTP (fijo en `8082` en el `docker-compose.yml` de Dokploy) |
| `CORE_URL` | no | `http://localhost:8080` (local) / `http://mejor-vejez-api:8080` (Dokploy) | URL interna del servicio core |
| `CLAVE_SERVICIO` | **sí** | — (`:?falta CLAVE_SERVICIO` en el compose de Dokploy) | Cabecera `X-Clave-Servicio` de las llamadas internas; la misma en core, alertas y reportes |
| `ALERTAS_URL` | no | `http://localhost:8081` (local) / `http://mejor-vejez-alertas:8081` (Dokploy) | URL interna del microservicio de alertas y notificaciones (para entregarle el PDF) |
| `REPORTES_CRON` | no | `0 0 8-20 * * MON` | Cron del reporte semanal (America/Santiago): primera corrida a las 08:00 y luego cada hora hasta las 20:00; la reserva de envío evita duplicados |
| `PROCESOS_HABILITADOS` | no | `true` | Apaga la corrida programada del reporte semanal sin detener el servicio; la ruta manual sigue disponible |
| `JAVA_OPTS` | no | `-XX:MaxRAMPercentage=75` | Opciones de la JVM (`docker-compose.yml`) |

## Desarrollo y pruebas en local

```sh
./mvnw spring-boot:run      # http://localhost:8082/api/salud
./mvnw test                 # sin Docker: usa MockWebServer (simula core y alertas) y PDFBox solo para leer el texto del PDF en memoria
```

Para que el servicio consulte el core y entregue el PDF a alertas en local (ver la guía de instalación: `planes/guia-instalacion-local.md`):

```sh
CORE_URL=http://localhost:8080 \
CLAVE_SERVICIO=<la misma clave que usan core y alertas> \
ALERTAS_URL=http://localhost:8081 \
./mvnw spring-boot:run
```

Para probar a mano, con el core y alertas levantados:

```sh
curl -s -X POST "http://localhost:8082/api/interno/procesos/reporte-semanal?inicio=2026-10-19" \
  -H "X-Clave-Servicio: <la clave>"
```

(o, equivalente, disparando la ruta de administración del core — ver la guía de instalación local). El correo con el PDF adjunto aparece en la bandeja de Mailpit, <http://localhost:8025> (Mailpit lo levanta y lo usa alertas).

Si la ruta manual responde `503`, el core esperó demasiado la respuesta síncrona de esta corrida (puede tardar hasta 3 × 10 s en el core más 60 s en alertas por grupo, p. ej. con un SMTP lento): la corrida sigue en curso y puede terminar enviando. Antes de reintentar, revisar Mailpit y el estado de la ejecución en el core.

## Despliegue en Dokploy

App de tipo **Compose** apuntando a este repositorio, archivo `docker-compose.yml`.

1. **Environment:** las variables de arriba. `CLAVE_SERVICIO` es obligatoria y debe ser **la misma** que en `mejor-vejez-api` y `mejor-vejez-alertas`.
2. **Domains:** no se configura ninguno. Su única ruta interna solo la llama el core, por la red interna `dokploy-network`.
3. Deploy. Verificar desde el propio contenedor o con `docker exec`: `curl http://localhost:8082/api/salud` (no tiene dominio público).
