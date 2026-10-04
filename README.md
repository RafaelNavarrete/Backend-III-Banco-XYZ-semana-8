# Backend III - Exp3 S8: Microservicios seguros y resilientes en la nube

Actividad sumativa individual (PBY2203) de Rafael Navarrete. Evoluciona el sistema bancario `bank-batch` hacia una arquitectura de microservicios lista para la nube: autenticacion con **OAuth 2.0**, tolerancia a fallos con **Resilience4j**, mensajeria asincrona con **Apache Kafka** y despliegue completo con **Docker** y **docker-compose**.

## Objetivo

- Proteger el sistema con OAuth 2.0 (flujo `client_credentials`) mediante un servidor de autorizacion propio.
- Dockerizar todos los microservicios con imagenes multi-stage.
- Orquestar todos los componentes con un unico `docker-compose.yml`.
- Aplicar Circuit Breaker, Retry y Bulkhead (Resilience4j) en las llamadas entre microservicios.
- Integrar mensajeria asincrona con Kafka.

## Arquitectura

| Componente | Puerto | Rol |
|---|---|---|
| `auth-server` | 9000 | Servidor de autorizacion OAuth 2.0 (Spring Authorization Server) |
| `config-server` | 8888 | Configuracion centralizada (modo native) |
| `eureka-server` | 8761 | Service Discovery |
| `bank-batch` | 8443 (HTTPS) | Batch bancario + BFFs + Resource Server OAuth2 + Resilience4j + Kafka |
| `clientes-service` | 8091 | Microservicio de clientes (Basic Auth interno) |
| `cuentas-service` | 8092 | Microservicio de cuentas (Basic Auth interno) |
| `postgres` | 5433 -> 5432 | Base de datos PostgreSQL 16 |
| `kafka` | 9092 | Broker Apache Kafka |

```text
Cliente --(1. token)--> auth-server
Cliente --(2. Bearer token)--> bank-batch --(Resilience4j)--> clientes-service
                                  |
                                  +--> PostgreSQL
                                  +--> Kafka (topic banco.transacciones)
bank-batch / clientes / cuentas --> Eureka + Config Server
```

## Estructura del repositorio

```text
.
|-- docker-compose.yml
|-- auth-server/        (Dockerfile, pom.xml, src)
|-- config-server/      (Dockerfile, pom.xml, src)
|-- eureka-server/      (Dockerfile, pom.xml, src)
|-- clientes-service/   (Dockerfile, pom.xml, src)
|-- cuentas-service/    (Dockerfile, pom.xml, src)
|-- bank-batch/         (Dockerfile, pom.xml, src)
`-- README.md
```

## Tecnologias

- Java 21, Spring Boot 4.1.x
- Spring Cloud (Config Server, Eureka, Resilience4j)
- Spring Security: Authorization Server y Resource Server (OAuth 2.0 / JWT)
- Spring Batch, Spring JDBC, Spring Kafka
- PostgreSQL 16, Apache Kafka
- Docker y Docker Compose

## Como ejecutar

Requisitos: Docker Desktop activo (Engine running). No se necesita Java ni Maven en la maquina, porque las imagenes se compilan dentro de Docker.

```powershell
# Desde la raiz del repositorio
docker compose up -d --build
docker compose ps
```

La primera construccion demora unos minutos. Deben quedar los 8 contenedores en estado `Up` (Postgres en `healthy`).

Verificacion: `http://localhost:8761` debe mostrar `BANK-BATCH`, `CLIENTES-SERVICE` y `CUENTAS-SERVICE`.

Ver logs de un servicio:

```powershell
docker compose logs bank-batch --tail 50
```

Detener todo:

```powershell
docker compose down        # conserva los datos de PostgreSQL
docker compose down -v     # elimina tambien el volumen
```

## Dockerizacion

Cada microservicio tiene un `Dockerfile` multi-stage:

1. Etapa de construccion: `maven:3.9-eclipse-temurin-21`, compila con `mvn clean package -DskipTests`.
2. Etapa final: `eclipse-temurin:21-jre`, solo contiene el `.jar` resultante.

### docker-compose.yml

Orquesta los 8 componentes en una red comun donde los servicios se resuelven por nombre (`postgres`, `kafka`, `auth-server`, etc.). Decisiones principales:

- `postgres` tiene `healthcheck` (`pg_isready`) y `bank-batch` espera a que este `healthy`.
- `restart: on-failure` en los servicios que dependen de otros, para tolerar el orden de arranque.
- Kafka expone dos listeners: `localhost:9092` para el host y `kafka:29092` para los contenedores.
- La configuracion cambia por variables de entorno (sin tocar el codigo):

| Variable | Valor en Docker |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/banco` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:29092` |
| `SPRING_CONFIG_IMPORT` | `optional:configserver:http://config-server:8888` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://eureka-server:8761/eureka/` |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI` | `http://auth-server:9000/oauth2/jwks` |
| `CLIENTES_URL` | `http://clientes-service:8091` |

## Seguridad: OAuth 2.0

`auth-server` (Spring Authorization Server) emite tokens JWT con el flujo `client_credentials`. Hay un client por canal y cada uno recibe su propio scope:

| Client (canal) | client_id | client_secret | Scope |
|---|---|---|---|
| Web | `web` | `web123` | `web` |
| Movil | `movil` | `movil123` | `movil` |
| Cajero | `cajero` | `cajero123` | `cajero` |

`bank-batch` actua como Resource Server: valida la firma del token contra `jwk-set-uri` del `auth-server` y autoriza por scope.

| Ruta | Requiere |
|---|---|
| `/api/bff/web/**` | scope `web` |
| `/api/bff/movil/**` | scope `movil` |
| `/api/bff/cajero/**` | scope `cajero` |
| Cualquier otra ruta (incluye `/api/debug/**` y `/api/kafka/**`) | token valido |

Sin token responde `401`. Con un token valido pero de otro canal responde `403`.

Nota: `clientes-service` y `cuentas-service` conservan Basic Auth (`admin` / `admin123`) como credencial interna de servicio a servicio; el punto de entrada publico del sistema, `bank-batch`, esta protegido con OAuth 2.0.

### Obtener un token y usarlo

```powershell
$token = (curl.exe -s -u web:web123 -d "grant_type=client_credentials&scope=web" http://localhost:9000/oauth2/token | ConvertFrom-Json).access_token

curl.exe -k -H "Authorization: Bearer $token" https://localhost:8443/api/bff/web/resumen
```

Importante: el parametro `scope` es obligatorio en la peticion del token. Sin el, el token se emite sin scopes y el acceso se rechaza con `403`.

### Pruebas de seguridad

| Prueba | Resultado esperado |
|---|---|
| `GET /api/bff/web/resumen` sin token | `401 Unauthorized` |
| Mismo endpoint con token del client `web` | `200 OK` |
| Mismo endpoint con token del client `movil` | `403 Forbidden` |

## Tolerancia a fallos: Resilience4j

`ClientesClient` (en `bank-batch`) aplica tres mecanismos sobre las llamadas a `clientes-service`:

| Mecanismo | Configuracion |
|---|---|
| Retry | 3 intentos, 500 ms de espera |
| Circuit Breaker | ventana de 5 llamadas, minimo 3, abre con 50% de fallos, 5 s en estado abierto |
| Bulkhead | maximo 5 llamadas concurrentes |

Si el servicio no responde, tras los reintentos se ejecuta `fallbackClientes()` y se devuelve una respuesta controlada en lugar de un error 500. El mecanismo se ubica en quien realiza la llamada remota; `cuentas-service` no tiene consumidores, por lo que no requiere el patron.

Prueba:

```powershell
curl.exe -k -H "Authorization: Bearer $token" https://localhost:8443/api/debug/clientes   # lista de clientes

docker compose stop clientes-service
curl.exe -k -H "Authorization: Bearer $token" https://localhost:8443/api/debug/clientes   # fallback

docker compose start clientes-service
```

Con el servicio detenido responde: `{"error":"clientes-service no disponible temporalmente"}`.

## Mensajeria asincrona: Kafka

| Elemento | Implementacion |
|---|---|
| Broker | Apache Kafka |
| Topic | `banco.transacciones` |
| Producer | `TransaccionProducer` |
| Consumer | `TransaccionConsumer` (grupo `bank-batch-group`) |
| Endpoint de prueba | `POST /api/kafka/enviar?mensaje={mensaje}` (requiere token) |

```text
Cliente -> KafkaTestController -> TransaccionProducer -> Kafka (banco.transacciones) -> TransaccionConsumer
```

Prueba:

```powershell
curl.exe -k -X POST -H "Authorization: Bearer $token" "https://localhost:8443/api/kafka/enviar?mensaje=PruebaKafkaS8"

docker compose logs bank-batch | findstr "Evento"
```

En el log aparecen `Evento enviado a Kafka [banco.transacciones]: ...` y `Evento recibido desde Kafka: ...`.

## Microservicio principal: bank-batch

Procesa archivos CSV bancarios con Spring Batch, guarda los resultados en PostgreSQL y expone los datos mediante APIs BFF separadas por canal (Web, Movil y Cajero), con DTOs especificos por canal.

| Canal | Endpoint |
|---|---|
| Web | `GET /api/bff/web/resumen` |
| Movil | `GET /api/bff/movil/resumen` |
| Cajero | `GET /api/bff/cajero/saldo/{cuentaId}` y `POST /api/bff/cajero/retiro/{cuentaId}` |

Jobs batch disponibles:

| Job | Entrada | Resultado |
|---|---|---|
| `transaccionJob` | `transacciones.csv` | `transacciones_procesadas` y `resumen_diario` |
| `interesJob` | `intereses.csv` | `cuentas_intereses` |
| `estadoCuentaJob` | `cuentas_anuales.csv` | `movimientos_anuales` y `resumen_anual` |

Los jobs no se ejecutan al iniciar (`spring.batch.job.enabled=false`). 

Los steps usan chunks de 5 registros, procesamiento multihilo, reintentos para errores transitorios y una politica de skips con limite de calidad de datos (si se omite mas del 10%, el job falla con `CALIDAD_INSUFICIENTE`).

## Evidencia de ejecucion

Las capturas de pantalla estan en el informe de la entrega (OAuth 2.0 con respuestas 401, 200 y 403, construccion de imagenes, `docker compose ps`, Eureka, fallback de Resilience4j y eventos de Kafka).

## Notas

- El certificado HTTPS de `bank-batch` es autofirmado y solo para ejecucion local (`-k` en `curl`).
- Los secretos (clients OAuth2, base de datos) estan en archivos de configuracion y son adecuados solo para un entorno academico. En produccion se externalizarian y se cifrarian.