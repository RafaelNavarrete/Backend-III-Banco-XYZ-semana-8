# Instrucciones para ejecutar y probar el sistema

Todos los comandos son para PowerShell, ejecutados desde la raiz del repositorio.

## 1. Requisitos

- Docker Desktop activo (Engine running) con Docker Compose.
- No se necesita Java ni Maven: las imagenes se compilan dentro de Docker.
- Puertos libres en el equipo: 8080, 8443, 8888, 8761, 9000, 9092 y 5433.

## 2. Levantar el sistema

```powershell
git clone https://github.com/RafaelNavarrete/Backend-III-Banco-XYZ-semana-8.git
cd Backend-III-Banco-XYZ-semana-8
docker compose up -d --build
docker compose ps
```

La primera construccion demora varios minutos. Esperar a que los 10 contenedores esten `Up` (postgres y config-server en `healthy`).

Verificacion: abrir `http://localhost:8761`. Deben aparecer `API-GATEWAY`, `BANK-BATCH`, `CLIENTES-SERVICE`, `CUENTAS-SERVICE` y `PAGOS-SERVICE`. Los servicios tardan 1 a 2 minutos en registrarse.

Ver logs de un servicio:

```powershell
docker compose logs bank-batch --tail 50
```

Detener todo:

```powershell
docker compose down        # conserva los datos de PostgreSQL
docker compose down -v     # elimina tambien el volumen
```

## 3. Obtener tokens (auth-server)

Cada canal tiene su propio client y scope. El parametro `scope` es obligatorio.

```powershell
$token   = (curl.exe -s -u web:web123     -d "grant_type=client_credentials&scope=web"    http://localhost:9000/oauth2/token | ConvertFrom-Json).access_token
$movil   = (curl.exe -s -u movil:movil123 -d "grant_type=client_credentials&scope=movil"  http://localhost:9000/oauth2/token | ConvertFrom-Json).access_token
$cajero  = (curl.exe -s -u cajero:cajero123 -d "grant_type=client_credentials&scope=cajero" http://localhost:9000/oauth2/token | ConvertFrom-Json).access_token
```

Resultado esperado: tres variables con un JWT. Las siguientes pruebas usan `$token` (canal web) salvo que se indique otro.

## 4. Seguridad (OAuth 2.0)

| Prueba | Comando | Esperado |
|---|---|---|
| Sin token | `curl.exe -i http://localhost:8080/api/clientes` | `401 Unauthorized` |
| Con token | `curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/clientes` | `200` con la lista de clientes |
| Token de otro canal | `curl.exe -k -i -H "Authorization: Bearer $movil" https://localhost:8443/api/bff/web/resumen` | `403 Forbidden` |

## 5. Microservicios por el API Gateway

Los microservicios no publican puertos: solo se accede por el Gateway (`localhost:8080`).

```powershell
curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/clientes
curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/cuentas
curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/cuentas/201
curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/pagos
```

Esperado: `200` con JSON en todos. Cuenta `201` parte con saldo 450000 y la `202` con 1200000.

Apertura de una cuenta nueva:

```powershell
$body = '{"clienteId":101,"tipo":"ahorro","saldoInicial":10000}'
$body | curl.exe -s -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@-" http://localhost:8080/api/cuentas
```

Esperado: `201` con la cuenta creada. El tipo solo acepta `corriente` o `ahorro` (si no, `400`).

## 6. Pagos, Kafka y Resilience4j

**Transferencia (genera evento en Kafka):**

```powershell
$body = '{"cuentaOrigen":201,"cuentaDestino":202,"monto":5000}'
$body | curl.exe -s -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@-" http://localhost:8080/api/pagos/transferencia
```

Esperado: `200` con `"estado":"COMPLETADO"`.

**Efecto del evento (esperar unos 3 segundos):**

```powershell
curl.exe -H "Authorization: Bearer $token" http://localhost:8080/api/cuentas/201
docker compose logs cuentas-service --tail 5
```

Esperado: el saldo de la cuenta 201 bajo 5000 (445000) y el log muestra el evento aplicado (topico `pagos.completados`).

**Alerta de seguridad (monto alto):**

```powershell
$body = '{"cuentaOrigen":202,"cuentaDestino":201,"monto":1000000}'
$body | curl.exe -s -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@-" http://localhost:8080/api/pagos/transferencia
docker compose logs bank-batch --tail 20 | findstr "ALERTA"
```

Esperado: el log de `bank-batch` muestra `ALERTA DE SEGURIDAD recibida` (topico `alertas.seguridad`).

**Validacion de negocio (cuenta inexistente):**

```powershell
$body = '{"cuentaOrigen":999,"cuentaDestino":202,"monto":5000}'
$body | curl.exe -s -i -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@-" http://localhost:8080/api/pagos/transferencia
```

Esperado: `400 Bad Request`.

**Fallback (Retry + Circuit Breaker):**

```powershell
docker compose stop cuentas-service
$body = '{"cuentaOrigen":201,"cuentaDestino":202,"monto":5000}'
$body | curl.exe -s -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "@-" http://localhost:8080/api/pagos/transferencia
docker compose start cuentas-service
```

Esperado: `200` con `"estado":"PENDIENTE_VALIDACION"` (sin error 500). Esperar 1 minuto a que `cuentas-service` se registre de nuevo en Eureka antes de seguir.

## 7. Procesos batch (bank-batch, HTTPS 8443)

El certificado es autofirmado, por eso se usa `-k`. Los jobs requieren el token del canal web.

```powershell
curl.exe -k -s -X POST -H "Authorization: Bearer $token" https://localhost:8443/api/batch/transaccionJob
curl.exe -k -s -X POST -H "Authorization: Bearer $token" https://localhost:8443/api/batch/interesJob
curl.exe -k -s -X POST -H "Authorization: Bearer $token" https://localhost:8443/api/batch/estadoCuentaJob
```

Esperado: JSON con `executionId` y estado `COMPLETED`. Si el job termina `FAILED` por la politica de calidad de datos (mas de 10% de registros omitidos), el `exitCode` es `CALIDAD_INSUFICIENTE`.

Ejecucion con reejecucion automatica (reinicia hasta 3 veces si el job queda en `FAILED`):

```powershell
curl.exe -k -s -X POST -H "Authorization: Bearer $token" https://localhost:8443/api/batch/transaccionJob/auto
```

Esperado: JSON con el estado final y el numero de `intentos` (1 si no hubo fallas).

Verificar resultados en PostgreSQL:

```powershell
docker compose exec postgres psql -U postgres -d banco -c "SELECT * FROM resumen_diario LIMIT 5;"
docker compose exec postgres psql -U postgres -d banco -c "SELECT * FROM cuentas_intereses LIMIT 5;"
docker compose exec postgres psql -U postgres -d banco -c "SELECT * FROM resumen_anual LIMIT 5;"
```

## 8. BFF por canal

**Web (token web):**

```powershell
curl.exe -k -s -H "Authorization: Bearer $token" https://localhost:8443/api/bff/web/resumen
```

Esperado: `200` con el resumen completo (4 indicadores). Ejecutar antes los 3 jobs.

**Movil (token movil):**

```powershell
curl.exe -k -s -H "Authorization: Bearer $movil" https://localhost:8443/api/bff/movil/resumen
```

Esperado: `200` con la respuesta liviana (2 datos). Con el token web responde `403`.

**Cajero (token cajero):** el saldo se lee de `cuentas_intereses`, por lo que requiere haber ejecutado `interesJob`. Elegir un `cuenta_id` existente:

```powershell
docker compose exec postgres psql -U postgres -d banco -c "SELECT cuenta_id, saldo_final FROM cuentas_intereses LIMIT 5;"
```

Usando ese `cuenta_id` (ejemplo `1`):

```powershell
curl.exe -k -s -H "Authorization: Bearer $cajero" https://localhost:8443/api/bff/cajero/saldo/1

$body = '{"monto":1000}'
$body | curl.exe -k -s -X POST -H "Authorization: Bearer $cajero" -H "Content-Type: application/json" -d "@-" https://localhost:8443/api/bff/cajero/retiro/1
```

Esperado: saldo de la cuenta y retiro exitoso con saldo anterior y posterior. Un monto mayor al saldo responde `400`, una cuenta inexistente `404`. Cada retiro queda en la tabla `retiros_cajero`.

## 9. Escalabilidad horizontal

```powershell
docker compose up -d --scale cuentas-service=2 --scale pagos-service=2 --scale clientes-service=2
docker compose ps
```

Esperado: en `http://localhost:8761` cada servicio aparece con 2 instancias y el Gateway reparte las solicitudes entre ellas. Volver a una instancia:

```powershell
docker compose up -d --scale cuentas-service=1 --scale pagos-service=1 --scale clientes-service=1
```

Limitacion: los datos de clientes, cuentas y pagos estan en memoria de cada instancia (ver `readme.md`).

## 10. Monitoreo

```powershell
curl.exe http://localhost:8761                 # Eureka (navegador)
docker compose logs <servicio> --tail 50       # logs de un contenedor
docker compose ps                              # estado de los contenedores
```