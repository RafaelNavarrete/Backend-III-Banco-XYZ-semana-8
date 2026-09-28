# Backend III - Sistema Bancario con Spring Batch, Microservicios, Seguridad y Eventos

Proyecto grupal (Backend III, PBY2203) que evoluciona el sistema bancario `bank-batch` hacia una arquitectura de microservicios usando Spring Cloud. Se agregan configuracion centralizada, service discovery, tolerancia a fallos, microservicios adicionales y mensajeria asincrona con Apache Kafka, manteniendo el procesamiento batch y las APIs BFF ya desarrolladas en entregas anteriores.

## Arquitectura general

| Componente | Puerto | Rol |
|---|---|---|
| `config-server` | 8888 | Servidor de configuracion centralizada (modo native) |
| `eureka-server` | 8761 | Service Discovery |
| `bank-batch` | 8443 (HTTPS) | Microservicio principal: batch bancario + BFFs + JWT + Circuit Breaker |
| `clientes-service` | 8091 | Microservicio de clientes, seguridad Basic Auth |
| `cuentas-service` | 8092 | Microservicio de cuentas, seguridad Basic Auth |
| `banco-postgres` | 5433 -> 5432 | Base de datos PostgreSQL local |
| `banco-kafka` | 9092 -> 9092 | Broker Apache Kafka local |

Todos los microservicios (`bank-batch`, `clientes-service`, `cuentas-service`) se registran en `eureka-server` y consumen configuracion desde `config-server`.

### Nota de diseño: Tolerancia a fallos

El Circuit Breaker (Resilience4j) se implementa desde `bank-batch`, actuando como consumidor de `clientes-service`. Esta decision sigue el patron estandar de microservicios: el mecanismo de tolerancia a fallos se ubica en quien realiza la llamada remota, no en el servicio que solo expone datos. Por restriccion de tiempo, no se replico el mismo patron para `cuentas-service`, pero la implementacion es identica y facilmente escalable a otros consumidores.

## Estructura del repositorio

```text
.
|-- bank-batch/
|-- config-server/
|-- eureka-server/
|-- clientes-service/
|-- cuentas-service/
`-- README.md
```

## Tecnologias generales

- Java 21
- Spring Boot 4.1.0
- Spring Cloud 2025.1.2 (Config Server, Eureka, Resilience4j)
- Spring Security
- PostgreSQL 16 (Docker Compose)
- Apache Kafka (Docker Compose)
- Maven Wrapper

## Orden de arranque

```powershell
# 1. Infraestructura Docker: PostgreSQL y Kafka
cd bank-batch
docker compose up -d

# 2. Comprobar contenedores
docker ps

# 3. Config Server
cd ..\config-server
.\mvnw.cmd spring-boot:run

# 4. Eureka Server
cd ..\eureka-server
.\mvnw.cmd spring-boot:run

# 5. clientes-service
cd ..\clientes-service
.\mvnw.cmd spring-boot:run

# 6. cuentas-service
cd ..\cuentas-service
.\mvnw.cmd spring-boot:run

# 7. bank-batch
cd ..\bank-batch
.\mvnw.cmd spring-boot:run
```

Verificacion: `http://localhost:8761` debe mostrar los tres microservicios (`BANK-BATCH`, `CLIENTES-SERVICE`, `CUENTAS-SERVICE`) con estado `UP`.

---

# Microservicio: Config Server

Servidor de configuracion centralizada en modo `native`, que sirve archivos de configuracion locales a los microservicios que lo consumen.

## Configuracion

```properties
server.port=8888
spring.application.name=config-server
spring.profiles.active=native
spring.cloud.config.server.native.search-locations=classpath:/config
```

## Prueba

```text
GET http://localhost:8888/bank-batch/default
```

Devuelve un JSON con la configuracion centralizada disponible para `bank-batch`.

---

# Microservicio: Eureka Server

Servidor de Service Discovery. Los demas microservicios se registran aqui para poder ser localizados por nombre logico en vez de IP/puerto fijo.

## Configuracion

```properties
server.port=8761
spring.application.name=eureka-server
eureka.client.register-with-eureka=false
eureka.client.fetch-registry=false
```

## Panel de administracion

```text
http://localhost:8761
```

Muestra todas las instancias registradas con su estado (`UP`/`DOWN`).

---

# Microservicio: Bank Batch

Proyecto Java/Spring Boot para procesamiento batch de datos bancarios con PostgreSQL y exposicion de APIs BFF protegidas con JWT para los canales Web, Movil y Cajero. Ahora tambien consume Config Server, se registra en Eureka, implementa tolerancia a fallos con Resilience4j y publica/consume eventos mediante Apache Kafka.

## Descripcion

La aplicacion procesa archivos CSV bancarios, valida y normaliza la informacion, guarda los resultados en PostgreSQL y expone datos consolidados mediante endpoints REST separados por canal.

El sistema incluye:

- Procesamiento batch de transacciones diarias.
- Calculo de intereses de cuentas.
- Procesamiento de movimientos anuales.
- Generacion de resumenes diarios y anuales.
- APIs BFF para Web, Movil y Cajero.
- Autenticacion con JWT.
- Autorizacion por rol de canal.
- HTTPS local con keystore PKCS12.
- Configuracion centralizada via Config Server.
- Registro en Eureka Service Discovery.
- Tolerancia a fallos con Resilience4j (Circuit Breaker + Fallback) al consumir `clientes-service`.
- Mensajeria asincrona con Apache Kafka sobre el topic `banco.transacciones`.

## Tecnologias

- Java 21
- Spring Boot 4.1.0
- Spring Cloud 2025.1.2 (Config Client, Eureka Client, Resilience4j)
- Spring Batch
- Spring JDBC
- Spring Web
- Spring Security
- Spring Kafka
- JJWT 0.12.6
- PostgreSQL 16
- Apache Kafka
- Docker Compose
- Maven Wrapper

## Estructura principal

```text
bank-batch/
|-- docker-compose.yml
|-- pom.xml
|-- src
|   |-- main
|   |   |-- java/cl/duoc/bank_batch
|   |   |   |-- BankBatchApplication.java
|   |   |   |-- bff
|   |   |   |   |-- cajero
|   |   |   |   |-- movil
|   |   |   |   `-- web
|   |   |   |-- client
|   |   |   |-- config
|   |   |   |-- model
|   |   |   |-- policy
|   |   |   |-- processor
|   |   |   `-- security
|   |   `-- resources
|   |       |-- application.properties
|   |       |-- bank-batch.p12
|   |       |-- schema.sql
|   |       `-- data
|   |           |-- cuentas_anuales.csv
|   |           |-- intereses.csv
|   |           `-- transacciones.csv
|   `-- test
```

## Arquitectura BFF

El proyecto usa una arquitectura BFF, o Backend For Frontend, separada por canal. Cada canal tiene su propio controlador, servicio o logica de negocio asociada, y DTOs especificos para exponer solamente los datos que necesita ese consumidor.

Flujo actual por canal:

```text
Web    -> WebBffController    -> WebBffService   -> WebResumenDTO
Movil  -> MovilBffController  -> MovilBffService -> MovilResumenDTO
Cajero -> CajeroBffController -> CajeroService   -> SaldoCajeroDTO / RetiroResponseDTO
```

### Mejora implementada: DTOs por canal

Como mejora aplicada a partir de la sugerencia docente de la entrega anterior, las respuestas de los BFF ahora usan DTOs especificos en vez de construir respuestas directamente con `Map<String, Object>`.

DTOs implementados:

- `WebResumenDTO`
- `MovilResumenDTO`
- `SaldoCajeroDTO`
- `RetiroResponseDTO`

Esta mejora deja explicito el contrato de datos de cada canal, facilita entender que campos devuelve cada endpoint y reduce el acoplamiento entre controladores y estructura de respuesta. Tambien mejora la modularidad, mantenibilidad y escalabilidad del proyecto, porque cada BFF puede evolucionar su respuesta sin afectar innecesariamente a los otros canales.

## Jobs batch

El proyecto define tres jobs:

| Job | Archivo de entrada | Resultado principal |
|---|---|---|
| `transaccionJob` | `data/transacciones.csv` | `transacciones_procesadas` y `resumen_diario` |
| `interesJob` | `data/intereses.csv` | `cuentas_intereses` |
| `estadoCuentaJob` | `data/cuentas_anuales.csv` | `movimientos_anuales` y `resumen_anual` |

Los steps trabajan con chunks de 5 registros, procesamiento multihilo, reintentos para errores transitorios de base de datos y una politica personalizada de skips.

### Calidad de datos

`CustomSkipPolicy` permite omitir errores controlados hasta un limite de 100 registros, entre ellos:

- `FlatFileParseException`
- `NumberFormatException`
- `DateTimeParseException`
- `IllegalArgumentException`

`DataQualityDecider` valida el porcentaje de omisiones. Si supera el 10% de los registros evaluados, el job falla con estado `CALIDAD_INSUFICIENTE`.

### Validaciones principales

Transacciones:

- Normaliza fechas en formatos `yyyy-MM-dd`, `dd-MM-yyyy`, `dd/MM/yyyy` y `yyyy/MM/dd`.
- Valida montos nulos, negativos o iguales a cero.
- Normaliza el tipo de transaccion.
- Acepta `credito` y `debito`.
- Marca anomalias en vez de descartar registros procesables.

Intereses:

- Valida nombre, saldo, edad y tipo de cuenta.
- Acepta cuentas `ahorro` y `prestamo`.
- Calcula 1% de interes para ahorro.
- Calcula 2% de interes para prestamo.
- Guarda tasa, interes calculado, saldo final, validez y observacion.

Movimientos anuales:

- Normaliza fechas.
- Valida monto, tipo de movimiento y descripcion.
- Acepta `deposito`, `retiro` y `compra`.
- Genera resumen anual por cuenta.

## Infraestructura Docker

La infraestructura local de `bank-batch` se levanta con `docker-compose.yml` e incluye PostgreSQL y Apache Kafka.

### PostgreSQL

| Configuracion | Valor |
|---|---|
| Imagen | `postgres:16` |
| Contenedor | `banco-postgres` |
| Base de datos | `banco` |
| Usuario | `postgres` |
| Password | `postgres` |
| Puerto local | `5433` |

### Apache Kafka

| Configuracion | Valor |
|---|---|
| Imagen | `bitnamilegacy/kafka:4.0.0-debian-12-r10` |
| Contenedor | `banco-kafka` |
| Puerto local | `9092` |
| Listener anunciado | `PLAINTEXT://localhost:9092` |
| Protocolo | `PLAINTEXT` |

El archivo `src/main/resources/schema.sql` crea estas tablas:

- `transacciones_procesadas`
- `cuentas_intereses`
- `movimientos_anuales`
- `resumen_anual`
- `resumen_diario`
- `retiros_cajero`

## Configuracion

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/banco
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver

spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:schema.sql

spring.batch.jdbc.initialize-schema=never
spring.batch.job.enabled=false

server.port=8443
server.ssl.enabled=true
server.ssl.key-store=classpath:bank-batch.p12
server.ssl.key-store-password=changeit
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=bank-batch

# Spring Cloud
spring.config.import=optional:configserver:http://localhost:8888
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.prefer-ip-address=true

# Resilience4j - Circuit Breaker hacia clientes-service
resilience4j.circuitbreaker.instances.clientesService.sliding-window-size=5
resilience4j.circuitbreaker.instances.clientesService.minimum-number-of-calls=3
resilience4j.circuitbreaker.instances.clientesService.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.clientesService.wait-duration-in-open-state=5000

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=bank-batch-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer

# Mensajeria
mensaje.origen=bank-batch
```

Importante: `spring.batch.job.enabled=false` evita que los jobs se ejecuten automaticamente al iniciar la aplicacion. Para ejecutar un job desde consola, se debe habilitar explicitamente en los argumentos.

### Dependencias Kafka verificadas

En `bank-batch/pom.xml` la integracion con Kafka se encuentra declarada mediante:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-kafka</artifactId>
</dependency>
```

## Requisitos

- JDK 21
- Docker Desktop o Docker Engine
- PowerShell, CMD o terminal compatible
- Infraestructura Docker activa: `banco-postgres` y `banco-kafka`
- `config-server`, `eureka-server` y `clientes-service` corriendo previamente

## Ejecucion

### 1. Levantar infraestructura local

```powershell
docker compose up -d
docker ps
```

Debe quedar activo `banco-postgres` para la base de datos y `banco-kafka` para la mensajeria asincrona.

### 2. Compilar

```powershell
.\mvnw.cmd compile
```

### 3. Ejecutar pruebas

```powershell
.\mvnw.cmd test
```

### 4. Ejecutar la aplicacion como API

```powershell
.\mvnw.cmd spring-boot:run
```

La API queda disponible en:

```text
https://localhost:8443
```

Como el certificado es local/autofirmado, en `curl` se usa `-k`.

En PowerShell, para aceptar el certificado local en esta version del entorno, ejecutar primero:

```powershell
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
```

### 5. Ejecutar jobs batch

Para ejecutar un job especifico:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.enabled=true --spring.batch.job.name=transaccionJob"
```

Jobs disponibles:

```text
transaccionJob
interesJob
estadoCuentaJob
```

Para que las APIs BFF tengan datos completos, se recomienda ejecutar al menos:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.enabled=true --spring.batch.job.name=transaccionJob"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.enabled=true --spring.batch.job.name=interesJob"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.batch.job.enabled=true --spring.batch.job.name=estadoCuentaJob"
```

## Seguridad

La seguridad esta implementada con Spring Security y JWT.

Endpoint publico:

```http
POST /auth/login
```

Usuarios en memoria:

| Canal | Usuario | Password | Rol |
|---|---|---|---|
| Web | `web` | `web123` | `ROLE_WEB` |
| Movil | `movil` | `movil123` | `ROLE_MOVIL` |
| Cajero | `cajero` | `cajero123` | `ROLE_CAJERO` |

Reglas de autorizacion:

- `/api/bff/web/**` requiere rol `WEB`.
- `/api/bff/movil/**` requiere rol `MOVIL`.
- `/api/bff/cajero/**` requiere rol `CAJERO`.
- `/api/debug/**` y `/api/kafka/**` estan permitidos publicamente para pruebas academicas locales.
- El resto de rutas requiere autenticacion.

El token JWT dura 1 hora.

### Obtener token

```powershell
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }

$login = @{ usuario = "web"; password = "web123" } | ConvertTo-Json
$tokenWeb = (Invoke-RestMethod -Uri "https://localhost:8443/auth/login" -Method POST -ContentType "application/json" -Body $login).token
```

Respuesta del login:

```json
{
  "usuario": "web",
  "roles": ["ROLE_WEB"],
  "token": "jwt-generado",
  "tipo": "Bearer"
}
```

## APIs BFF

### BFF Web

```http
GET /api/bff/web/resumen
```

```json
{
  "canal": "web",
  "descripcion": "BFF Web Banco XYZ",
  "resumen": {
    "transaccionesProcesadas": 1000,
    "resumenesDiarios": 338,
    "cuentasConIntereses": 50,
    "resumenesAnuales": 20
  }
}
```

### BFF Movil

```http
GET /api/bff/movil/resumen
```

```json
{
  "canal": "movil",
  "transacciones": 1000,
  "resumenesDiarios": 338
}
```

### BFF Cajero

```http
GET /api/bff/cajero/saldo/{cuentaId}
POST /api/bff/cajero/retiro/{cuentaId}
```

Consultar saldo:

```json
{
  "canal": "cajero",
  "cuentaId": 101,
  "saldoDisponible": 7960.00
}
```

Realizar retiro:

```json
{
  "canal": "cajero",
  "cuentaId": 101,
  "montoRetirado": 100,
  "saldoAnterior": 8060.00,
  "saldoDisponible": 7960.00
}
```

## Arquitectura orientada a eventos

La nueva implementacion incorpora Apache Kafka para desacoplar la generacion de eventos de su procesamiento. En este enfoque, `TransaccionProducer` publica mensajes en un topic y `TransaccionConsumer` los procesa de forma asincrona, sin que el cliente que invoca el endpoint dependa directamente del consumidor.

Kafka es apropiado para este caso porque permite separar productores y consumidores, mantener eventos disponibles en el broker y procesarlos mediante un grupo consumidor. Para la demostracion academica, el mensaje enviado representa una transaccion o evento bancario procesado por el sistema.

### Flujo de eventos

```text
Cliente / Postman
        |
        v
KafkaTestController
        |
        v
TransaccionProducer
        |
        v
Apache Kafka
Topic: banco.transacciones
        |
        v
TransaccionConsumer
Consumer Group: bank-batch-group
```

| Elemento | Implementacion |
|---|---|
| Broker | Apache Kafka |
| Topic | `banco.transacciones` |
| Producer | `TransaccionProducer` |
| Consumer | `TransaccionConsumer` |
| Consumer Group | `bank-batch-group` |
| Endpoint de prueba | `POST /api/kafka/enviar` |
| Tipo de comunicacion | Asincrona |

### Endpoint de prueba Kafka

```http
POST /api/kafka/enviar?mensaje={mensaje}
```

Ejemplo probado:

```powershell
curl.exe -k -X POST "https://localhost:8443/api/kafka/enviar?mensaje=SegundoEventoKafka"
```

Respuesta observada:

```text
Evento enviado a Kafka: SegundoEventoKafka
```

### Pruebas Kafka

Enviar evento:

```powershell
curl.exe -k -X POST "https://localhost:8443/api/kafka/enviar?mensaje=PruebaKafkaSemana7"
```

Debe responder con el texto `Evento enviado a Kafka: PruebaKafkaSemana7`.

Listar topics:

```powershell
docker exec banco-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Debe aparecer el topic `banco.transacciones`.

Ver mensajes:

```powershell
docker exec banco-kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic banco.transacciones --from-beginning --timeout-ms 10000
```

Debe mostrar los mensajes enviados al topic durante las pruebas.

Comprobar Consumer Group:

```powershell
docker exec banco-kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group bank-batch-group
```

En la prueba realizada se observo `CURRENT-OFFSET = 2`, `LOG-END-OFFSET = 2` y `LAG = 0`. Esto demuestra que el grupo consumidor proceso todos los mensajes disponibles y no tenia eventos pendientes.

### Diagrama de arquitectura de eventos

Espacio reservado para insertar posteriormente el diagrama realizado en draw.io.

```markdown
<!-- Agregar aqui la imagen cuando exista dentro del repositorio. Ejemplo:
![Arquitectura de eventos y tolerancia a fallos](ruta-de-la-imagen)
-->
```

El diagrama debe representar:

- Cliente/Postman -> Bank Batch -> KafkaTestController -> TransaccionProducer -> Apache Kafka -> Topic `banco.transacciones` -> TransaccionConsumer.
- Bank Batch / ClientesClient -> clientes-service -> Resilience4j -> `fallbackClientes()`.

## Tolerancia a fallos

`ClientesClient` implementa un Circuit Breaker (Resilience4j) sobre las llamadas hacia `clientes-service`. Si el servicio no responde o falla repetidamente, el circuito se abre y las siguientes peticiones reciben una respuesta de fallback en lugar de un error 500.

La implementacion real utiliza la anotacion:

```java
@CircuitBreaker(name = "clientesService", fallbackMethod = "fallbackClientes")
```

`ClientesClient` consume el endpoint:

```text
http://localhost:8091/api/clientes
```

```http
GET /api/debug/clientes
```

Comportamiento esperado:

- Con `clientes-service` activo: devuelve la lista de clientes obtenida en tiempo real.
- Con `clientes-service` detenido: Resilience4j ejecuta `fallbackClientes()` y devuelve:

```json
{"error":"clientes-service no disponible temporalmente"}
```

- Con `clientes-service` iniciado nuevamente: el endpoint vuelve a entregar la lista de clientes.

Esto demuestra tolerancia a fallos y recuperacion del servicio consumidor sin provocar la caida de `bank-batch`.

### Pruebas Resilience4j

```powershell
curl.exe -k "https://localhost:8443/api/debug/clientes"
```

Resultados esperados:

- Con `clientes-service` activo: lista de clientes.
- Con `clientes-service` detenido: `{"error":"clientes-service no disponible temporalmente"}`.
- Con `clientes-service` iniciado nuevamente: lista de clientes.

## Consultas utiles

```sql
SELECT * FROM transacciones_procesadas;
SELECT * FROM resumen_diario;
SELECT * FROM cuentas_intereses;
SELECT * FROM movimientos_anuales;
SELECT * FROM resumen_anual;
SELECT * FROM retiros_cajero;
```

## Evidencias sugeridas para la entrega

Para el informe o presentacion final se recomienda respaldar la implementacion con capturas de Docker, envio y consumo de eventos Kafka, estado del Consumer Group con `LAG = 0`, prueba del fallback de Resilience4j y recuperacion de `clientes-service`. Las imagenes no se referencian en este README porque todavia no existen archivos de evidencias dentro del repositorio.

---

# Microservicio: Clientes Service

Microservicio que expone datos de clientes migrados, registrado en Eureka y protegido con Basic Auth. Es consumido por `bank-batch` a traves de un Circuit Breaker con Fallback.

## Tecnologias

- Java 21
- Spring Boot 4.1.0
- Spring Cloud 2025.1.2 (Config Client, Eureka Client)
- Spring Web
- Spring Security (Basic Auth)

## Configuracion

```properties
server.port=8091
spring.application.name=clientes-service
spring.config.import=optional:configserver:http://localhost:8888
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.prefer-ip-address=true

app.security.user=admin
app.security.password=admin123
```

## Endpoints

```http
GET /api/clientes
GET /api/clientes/{id}
```

Requieren autenticacion Basic Auth (`admin` / `admin123`).

## Ejecucion

```powershell
.\mvnw.cmd spring-boot:run
```

## Prueba

```text
GET http://localhost:8091/api/clientes
```

- Sin credenciales: `401 Unauthorized`
- Con Basic Auth: `200 OK` con la lista de clientes

---

# Microservicio: Cuentas Service

Microservicio que expone datos de cuentas migradas, registrado en Eureka y protegido con Basic Auth.

## Tecnologias

- Java 21
- Spring Boot 4.1.0
- Spring Cloud 2025.1.2 (Config Client, Eureka Client)
- Spring Web
- Spring Security (Basic Auth)

## Configuracion

```properties
server.port=8092
spring.application.name=cuentas-service
spring.config.import=optional:configserver:http://localhost:8888
eureka.client.service-url.defaultZone=http://localhost:8761/eureka/
eureka.instance.prefer-ip-address=true

app.security.user=admin
app.security.password=admin123
```

## Endpoints

```http
GET /api/cuentas
GET /api/cuentas/{id}
```

Requieren autenticacion Basic Auth (`admin` / `admin123`).

## Ejecucion

```powershell
.\mvnw.cmd spring-boot:run
```

## Prueba

```text
GET http://localhost:8092/api/cuentas
```

- Sin credenciales: `401 Unauthorized`
- Con Basic Auth: `200 OK` con la lista de cuentas

---

## Detener entorno

Detener infraestructura local:

```powershell
docker compose down
```

Detener infraestructura local y eliminar el volumen de PostgreSQL:

```powershell
docker compose down -v
```

## Notas

- El certificado HTTPS incluido en `bank-batch` es para ejecucion local.
- Las credenciales estan en memoria y son adecuadas solo para demostracion o entorno academico.
- Para produccion se deberian externalizar secretos, cifrar passwords, renovar la clave JWT y usar un mecanismo de identidad robusto.
