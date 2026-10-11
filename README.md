# Backend III - Evaluacion Final Transversal: Banco XYZ

Evaluacion final transversal individual (PBY2203) de Rafael Navarrete. Modernizacion del sistema del Banco XYZ (legacy COBOL + scripts Shell en mainframe) hacia una arquitectura de microservicios lista para la nube, con **Spring Batch**, **Backend for Frontend (BFF)**, **Spring Cloud**, **OAuth 2.0**, **Resilience4j**, **Apache Kafka** y **Docker Compose**.

Repositorio: https://github.com/RafaelNavarrete/Backend-III-Banco-XYZ-semana-8.git

## Documentacion de la entrega

| Documento | Contenido |
|---|---|
| `readme.md` | Este archivo: vision general del sistema |
| `instrucciones.md` | Paso a paso para ejecutar y probar cada componente |
| `despliegue.md` | Pasos para desplegar el sistema en la nube (AWS) |
| Informe tecnico (PDF) | Arquitectura, componentes, pruebas y conclusiones |
| Video de presentacion (MP4) | Resumen, resultados, desafios y mejoras |

## Alcance

- **Parte 1 - Spring Batch:** migracion de los 3 procesos legacy (transacciones diarias, intereses mensuales, estados de cuenta anuales) con reintentos, omision de registros, paralelismo y reejecucion automatica.
- **Parte 2 - BFF:** una API por canal (web, movil, cajero) con respuestas y seguridad propias.
- **Parte 3 - Microservicios:** clientes, cuentas y pagos, con Config Server, Eureka, API Gateway, OAuth2, Resilience4j y Kafka, orquestados con Docker Compose.

## Arquitectura

| Componente | Puerto | Rol |
|---|---|---|
| `api-gateway` | 8080 | Punto de entrada. Enruta a clientes, cuentas y pagos con balanceo de carga (`lb://`) |
| `auth-server` | 9000 | Servidor de autorizacion OAuth 2.0 (Spring Authorization Server), emite JWT |
| `config-server` | 8888 | Configuracion centralizada (Spring Cloud Config, modo native) |
| `eureka-server` | 8761 | Descubrimiento de servicios |
| `clientes-service` | 8091 | Gestion de clientes (acceso interno por el Gateway) |
| `cuentas-service` | 8092 | Apertura, mantenimiento y cierre de cuentas; consume eventos de pagos |
| `pagos-service` | 8093 | Pagos, transferencias y depositos; produce eventos Kafka |
| `bank-batch` | 8443 (HTTPS) | Jobs Spring Batch, BFF por canal, consumidor de alertas Kafka |
| `postgres` | 5433 -> 5432 | Base de datos de los procesos batch y del BFF (PostgreSQL 16) |
| `kafka` | 9092 | Broker Apache Kafka |

```text
Canal --(1. token)--> auth-server
Canal --(2. Bearer token)--> api-gateway --(lb:// via Eureka)--> clientes / cuentas / pagos
Canal --(2. Bearer token, HTTPS)--> bank-batch (jobs + BFF web / movil / cajero) --> PostgreSQL

pagos-service --(Resilience4j)--> cuentas-service
pagos-service --> Kafka [pagos.completados]  --> cuentas-service (actualiza saldos)
pagos-service --> Kafka [alertas.seguridad]  --> bank-batch (registra alerta)
Todos los servicios --> Eureka + Config Server
```

## Estructura del repositorio

```text
.
|-- docker-compose.yml
|-- api-gateway/        (Dockerfile, pom.xml, src)
|-- auth-server/        (Dockerfile, pom.xml, src)
|-- config-server/      (Dockerfile, pom.xml, src: config/*.yml por servicio)
|-- eureka-server/      (Dockerfile, pom.xml, src)
|-- clientes-service/   (Dockerfile, pom.xml, src)
|-- cuentas-service/    (Dockerfile, pom.xml, src)
|-- pagos-service/      (Dockerfile, pom.xml, src)
|-- bank-batch/         (Dockerfile, pom.xml, src: config, processor, policy, kafka, controller, client, bff)
|-- readme.md
|-- instrucciones.md
`-- despliegue.md
```

## Tecnologias

- Java 21, Spring Boot 4.1.x, Spring Cloud 2025.1.x
- Spring Batch, Spring JDBC, Spring Kafka
- Spring Cloud: Config Server, Eureka, Gateway, LoadBalancer, Resilience4j
- Spring Security: Authorization Server y Resource Server (OAuth 2.0 / JWT)
- PostgreSQL 16, Apache Kafka
- Docker y Docker Compose

## Como ejecutar (resumen)

Requisitos: Docker Desktop activo. No se necesita Java ni Maven, las imagenes se compilan dentro de Docker.

```powershell
git clone https://github.com/RafaelNavarrete/Backend-III-Banco-XYZ-semana-8.git
cd Backend-III-Banco-XYZ-semana-8
docker compose up -d --build
docker compose ps
```

Deben quedar los 10 contenedores en estado `Up` (PostgreSQL y config-server en `healthy`). Verificacion: `http://localhost:8761` debe mostrar `API-GATEWAY`, `BANK-BATCH`, `CLIENTES-SERVICE`, `CUENTAS-SERVICE` y `PAGOS-SERVICE`.

Detener todo:

```powershell
docker compose down        # conserva los datos de PostgreSQL
docker compose down -v     # elimina tambien el volumen
```

El detalle de cada prueba esta en `instrucciones.md`.

## Parte 1: procesos batch (Spring Batch)

| Job | Entrada | Resultado |
|---|---|---|
| `transaccionJob` | `transacciones.csv` | `transacciones_procesadas` y `resumen_diario` |
| `interesJob` | `intereses.csv` | `cuentas_intereses` |
| `estadoCuentaJob` | `cuentas_anuales.csv` | `movimientos_anuales` y `resumen_anual` |

- Procesamiento por bloques (chunk) de 100 registros y multihilo (`AsyncTaskExecutor`, configurable con `batch.threads.core`, `batch.threads.max` y `batch.queue.capacity`). El lector CSV se envuelve en `SynchronizedItemStreamReader`.
- Reintentos (hasta 3) ante `TransientDataAccessException`.
- `CustomSkipPolicy` omite registros con errores de formato (maximo 100) y `DataQualityDecider` hace fallar el job con `CALIDAD_INSUFICIENTE` si se omite mas del 10%.
- Escritura idempotente (`INSERT ... ON CONFLICT DO UPDATE`): ejecutar un job dos veces no duplica registros.
- Los jobs no se ejecutan al iniciar (`spring.batch.job.enabled=false`).

| Endpoint (`bank-batch`, requiere token) | Funcion |
|---|---|
| `POST /api/batch/{job}` | Ejecuta el job una vez |
| `POST /api/batch/{job}/auto` | Si el job termina en `FAILED`, lo reinicia con `restart()` hasta 3 intentos |

## Parte 2: BFF por canal

Implementados dentro de `bank-batch`, en paquetes separados por canal, cada uno con su controlador, servicio y DTO.

| Canal | Endpoint | Respuesta |
|---|---|---|
| Web | `GET /api/bff/web/resumen` | Resumen completo (4 indicadores) |
| Movil | `GET /api/bff/movil/resumen` | Resumen liviano (2 datos) |
| Cajero | `GET /api/bff/cajero/saldo/{cuentaId}` y `POST /api/bff/cajero/retiro/{cuentaId}` | Saldo y retiros con validaciones y bloqueo de fila (`SELECT ... FOR UPDATE`) |

## Parte 3: microservicios

| Servicio | Funcion principal |
|---|---|
| `clientes-service` | Consulta, creacion y actualizacion de clientes (validaciones 400, 404 y 409) |
| `cuentas-service` | Apertura (`POST`), cambio de tipo (`PUT`) y cierre (`DELETE`, solo con saldo cero) |
| `pagos-service` | `POST /api/pagos/pago`, `/transferencia` y `/deposito` |

### Seguridad: OAuth 2.0

`auth-server` emite JWT con el flujo `client_credentials`, un client y un scope por canal. Todos los servicios (`bank-batch`, clientes, cuentas y pagos) son Resource Server y validan la firma contra `/oauth2/jwks`.

| Client (canal) | client_id | client_secret | Scope |
|---|---|---|---|
| Web | `web` | `web123` | `web` |
| Movil | `movil` | `movil123` | `movil` |
| Cajero | `cajero` | `cajero123` | `cajero` |

| Ruta | Requiere |
|---|---|
| `/api/bff/web/**` | scope `web` |
| `/api/bff/movil/**` | scope `movil` |
| `/api/bff/cajero/**` | scope `cajero` |
| Resto de rutas y microservicios | token valido |

Sin token responde `401`; con un token de otro canal, `403`. El Gateway no valida el token, lo reenvia, y cada servicio lo valida. Entre servicios se reenvia el mismo token (token relay).

```powershell
$token = (curl.exe -s -u web:web123 -d "grant_type=client_credentials&scope=web" http://localhost:9000/oauth2/token | ConvertFrom-Json).access_token

curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/clientes
curl.exe -k -H "Authorization: Bearer $token" https://localhost:8443/api/bff/web/resumen
```

El parametro `scope` es obligatorio al pedir el token.

### Resiliencia: Resilience4j

| Llamada | Mecanismos |
|---|---|
| `pagos-service` -> `cuentas-service` (`CuentasClient`) | Retry (3 intentos, 500 ms), Circuit Breaker (ventana 5, minimo 3, 50%, 5 s abierto), Bulkhead (5 concurrentes) y fallback |
| `bank-batch` -> `clientes-service` (`ClientesClient`) | Mismas anotaciones, con fallback controlado |

Si `cuentas-service` no esta disponible, el pago se acepta con estado `PENDIENTE_VALIDACION` en lugar de responder 500.

### Mensajeria: Kafka

| Topico | Productor | Consumidor |
|---|---|---|
| `banco.transacciones` | `bank-batch` | `bank-batch` |
| `pagos.completados` | `pagos-service` | `cuentas-service` (actualiza saldos) |
| `alertas.seguridad` | `pagos-service` (montos desde $1.000.000) | `bank-batch` (registra la alerta) |

Cada instancia de `cuentas-service` usa su propio grupo de consumo, para que todas apliquen los mismos eventos sobre sus datos en memoria.

### Configuracion centralizada y descubrimiento

- `config-server` (modo native) entrega un archivo por servicio (`bank-batch.yml`, `pagos-service.yml`, `cuentas-service.yml`, `clientes-service.yml`). Cada servicio conserva valores por defecto en su `application.properties`.
- Los servicios se registran en Eureka y se resuelven por nombre; el Gateway balancea con `lb://`.

## Escalabilidad horizontal

Los microservicios de negocio no publican puertos en el host, por lo que se pueden replicar:

```powershell
docker compose up -d --scale cuentas-service=2 --scale pagos-service=2 --scale clientes-service=2
```

Cada instancia se registra en Eureka y el Gateway reparte las solicitudes (round-robin).

## Despliegue en la nube (AWS)

El sistema esta preparado para desplegarse en una instancia EC2 con Docker Compose. Los pasos estan en `despliegue.md`.

## Limitaciones y mejoras futuras

- Clientes, cuentas y pagos guardan sus datos en memoria. En produccion se usaria una base de datos compartida (por ejemplo Amazon RDS).
- Kafka corre con un solo broker (en la nube: Amazon MSK).
- Las credenciales estan en archivos de configuracion, solo aptas para entorno academico (en produccion: AWS Secrets Manager).
- El Config Server lee archivos locales; en un entorno real se conectaria a un repositorio Git.
- El certificado HTTPS de `bank-batch` es autofirmado, de uso local (`-k` en `curl`).