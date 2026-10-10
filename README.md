# alquiler-eventos-ms-compras

Microservicio de **compras** del sistema *Alquiler de Equipos para Eventos*.
Gestiona **proveedores** y **ordenes de compra** (con detalles M:N hacia `Equipo`,
historial de estados y suma de stock), exponiendo todo como API REST documentada
con Swagger (OpenAPI).

> **Este README es autocontenido**: explica el contexto general del sistema, el
> rol de este microservicio y como correrlo y probarlo paso a paso.

---

## 1. Contexto general del sistema (leer primero)

El sistema completo es una aplicacion distribuida (microservicios) para una
empresa que **alquila equipos para eventos** (sonido, iluminacion, mobiliario, etc.):

- **Entrega 1** — Un JAR compartido
  [`alquiler-eventos-common`](https://github.com/alquiler-eventos-org/alquiler-eventos-common)
  con las **entidades JPA**, **repositorios Spring Data**, **DTOs de la API**
  (generados desde OpenAPI), el **changelog de Liquibase** y el mapeo de errores.
  Este JAR no tiene logica de negocio ni endpoints: es la base comun.
- **Entrega 2 (este repo)** — El **microservicio de compras**: API REST que usa
  las entidades y repositorios del JAR common y agrega las reglas de negocio del
  area de compras. Repositorios hermanos siguen el mismo patron
  (`alquiler-eventos-ms-alquiler` para el area de alquiler, etc.).
- **Base de datos** — Una sola PostgreSQL 17 en **Supabase**, compartida por todos
  los microservicios. El esquema lo administra **unicamente Liquibase** (el
  changelog vive dentro del JAR common); Hibernate solo valida (`ddl-auto: validate`).

### Roles de usuario del sistema

Cada microservicio atiende un rol:

| Microservicio | Rol | Puede |
|---|---|---|
| **ms-compras (este)** | **Encargado de Compras** | ABM de proveedores, crear/confirmar/recibir/anular ordenes de compra |
| ms-alquiler | Encargado de Alquiler | Gestionar clientes y ordenes de alquiler de equipos |

Flujo de negocio que cubre este microservicio:

```
Se registra un PROVEEDOR  ->  se crea una ORDEN DE COMPRA (PENDIENTE)
   ->  se CONFIRMA (queda comprometida, ya no se puede editar)
   ->  se RECIBE (llega la mercaderia: se SUMA el stock de cada equipo)
   ->  (o bien se ANULA si estaba PENDIENTE: borrado logico)
Todo cambio de estado queda registrado en un HISTORIAL auditable.
```

### Decisiones tecnicas clave

- **El servidor calcula los montos**: subtotales (`cantidad * costoUnitario`) y
  total de la orden se calculan en el backend; el cliente no los envia.
- **Borrado logico**: proveedores nunca se borran fisicamente, se marcan
  `activo=false`; ordenes de compra se marcan `ANULADA`.
- **Transiciones de estado controladas** (ver matriz en la seccion Endpoints).
- **Manejo centralizado de errores**: toda respuesta de error usa el DTO
  `ApiError` del JAR common y nunca expone stacktraces.

---

## 2. Stack

| Tecnologia | Version |
|---|---|
| Java | 21 (LTS, Temurin) |
| Spring Boot | 3.4.5 |
| PostgreSQL | 17 (Supabase, BD compartida con ms-alquiler) |
| Liquibase | incluido en Spring Boot (changelog del JAR common) |
| SpringDoc OpenAPI | 2.8.14 (2.6.0 es incompatible con Spring Boot 3.4; 2.8.15+ tiene el bug de /swagger-ui, issue springdoc #3210) |
| Maven | 3.9+ |

---

## 3. Requisitos previos

1. **JDK 21** y **Maven 3.9+**.
2. Haber compilado e instalado el JAR common en el repo local de Maven:

   ```bash
   git clone https://github.com/alquiler-eventos-org/alquiler-eventos-common.git
   cd alquiler-eventos-common
   mvn clean install
   ```

3. Acceso a la BD (Supabase) y **datos de referencia cargados**: al menos un
   `usuario` y unos `equipos` en la BD (los necesita la orden de compra; la tabla
   `proveedores` puede arrancar vacia porque este servicio crea proveedores).

---

## 4. Variables de entorno

| Variable | Descripcion | Ejemplo |
|---|---|---|
| `DB_URL` | URL JDBC de PostgreSQL (Supabase) | `jdbc:postgresql://host:5432/postgres` |
| `DB_USER` | Usuario de la BD | `postgres` |
| `DB_PASSWORD` | Contrasena de la BD | `***` |
| `DB_POOL_MAX_SIZE` | Tamano maximo del pool Hikari de conexiones (default `3`) | `3` |
| `DB_POOL_MIN_IDLE` | Conexiones minimas en reposo del pool Hikari (default `1`) | `1` |
| `SERVER_PORT` | Puerto HTTP (default `8082`) | `8082` |
| `API_CONTEXT_PATH` | Context path (default `/api`) | `/api` |
| `APP_NAME` | Nombre de servicio en Eureka (default `ms-compras`) | `ms-compras` |
| `EUREKA_ENABLED` | Activa/desactiva el registro (default `true`) | `true` |
| `EUREKA_DEFAULT_ZONE` | URL del Eureka Server (default `http://localhost:8761/eureka/`) | |
| `EUREKA_RENEWAL_INTERVAL` | Heartbeat en segundos (default `5`) | `5` |
| `OPENAPI_SERVER_URL` | URL que el Swagger usa en "Try it out" (default `http://localhost:8082/api`; para la demo apuntar al Gateway) | `http://localhost:8080/api` |
| `LOG_DIR` | Carpeta de logs (default `./logs`) | `./logs` |
| `LOGGING_CONFIG` | Config de logback (default `classpath:logback-spring.xml`) | |
| `DEFAULT_PAGE` / `DEFAULT_PAGE_SIZE` | Paginacion por defecto (default `0` / `10`) | |
| `LOG_LEVEL_APP` / `LOG_LEVEL_SQL` | Niveles de log de la app y del SQL (default `INFO` / `DEBUG`) | |

> Las credenciales de la BD **nunca** se commitean: van por variables de entorno
> (o en un `.env` local ignorado por git).

---

## 5. Registro en Eureka y ejecucion via API Gateway

Este microservicio se registra en el **Eureka Server** con el nombre
`MS-COMPRAS` (configurable con `APP_NAME`). Cuando el Eureka Server esta
arriba (`http://localhost:8761`), la instancia aparece en el panel con estado
`UP` automaticamente.

Para la demo completa se levantan, en orden:

```bash
# 1. Eureka Server (puerto 8761)
cd alquiler-eventos-eureka && mvn spring-boot:run

# 2. Este microservicio (puerto 8082) -- se registra solo en Eureka
cd alquiler-eventos-ms-compras && mvn spring-boot:run

# 3. API Gateway (puerto 8080) -- enruta via lb://ms-compras usando Eureka
cd alquiler-eventos-api-gateway && mvn spring-boot:run
```

A partir de eso, TODOS los endpoints se prueban desde el Swagger unificado del
Gateway o desde el Swagger del propio microservicio, segun como se levante:

- **Swagger del API Gateway**: <http://localhost:8080/swagger-ui.html>
  (desplegable con la documentacion de `ms-compras`, datos en
  `/compras/v3/api-docs`).
- Para que las pruebas del Swagger peguen al Gateway (y no al microservicio
  directo), al levantar ms-compras se define
  `OPENAPI_SERVER_URL=http://localhost:8080/api`; si no se define, cada
  microservicio sirve su documentacion standalone con su propia URL.
- Tambien es posible seguir probando standalone: <http://localhost:8082/api/swagger-ui.html>

El ruteo es **dinamico via Eureka** (balanceo `lb://`), sin IPs fijas: si
ms-compras cambia de puerto/host, el Gateway lo resuelve solo.

## 5.bis. Como correr (standalone)

```bash
# con las variables DB_URL, DB_USER y DB_PASSWORD definidas
mvn spring-boot:run
```

Al arrancar:

- **Liquibase** aplica los changeSets pendientes del changelog del JAR common
  (la primera corrida agrega las columnas `activo` a `proveedores` y `clientes`).
- **Hibernate** valida las 16 entidades contra el esquema (`ddl-auto: validate`).
- Swagger UI queda en: <http://localhost:8082/api/swagger-ui.html>

Compilar y verificar (debe pasar antes de cada commit):

```bash
mvn clean install
```

---

## 6. Pruebas

### 6.1 Tests automatizados (53 tests)

```bash
mvn test
```

Cobertura:

- **Mappers**: mapeo bidireccional Entidad <-> DTO (Proveedor, OrdenCompra y detalles).
- **Services** (con Mockito): calculo de subtotales/total en el servidor, validaciones
  de detalles, transiciones de estado permitidas/prohibidas, suma de stock al recibir,
  historial y borrado logico.
- **Controllers** (con MockMvc): codigos HTTP 200/201/400/404, formato JSON y el
  manejo centralizado de errores con el DTO `ApiError`.

### 6.2 Guia de pruebas manuales con Swagger UI (paso a paso)

Con la aplicacion corriendo, abrir:

**<http://localhost:8082/api/swagger-ui.html>**

(El JSON de OpenAPI esta en <http://localhost:8082/api/v3/api-docs>.)

Para probar end-to-end se necesita en la BD: un `usuario` valido (id numerico) y
uno o mas `equipos` (ids numericos). Los pasos:

**Paso 1 — Crear un proveedor** (`POST /api/proveedores`, boton *Try it out*):

```json
{
  "nombre": "Eventos Sonido SA",
  "contacto": "Juan Perez",
  "telefono": "+5491122334455",
  "email": "ventas@eventossound.com",
  "direccion": "Av. Siempre Viva 742"
}
```

Respuesta esperada: **201** con el DTO `Proveedor` (incluye `id` y `activo: true`).
Anotar el `id`. Probar tambien el caso de error: enviar `email` invalido -> **400**.

**Paso 2 — Verificar listado y busqueda**:

- `GET /api/proveedores?page=0&size=10` -> **200**, solo activos, paginado.
- `GET /api/proveedores/buscar?nombre=sonido` -> **200**, filtro por nombre/email/contacto.

**Paso 3 — Crear una orden de compra** (`POST /api/ordenes-compra`), usando el
`id` del proveedor del Paso 1 y un `usuarioId` + `equipoId` existentes en la BD:

```json
{
  "proveedorId": 1,
  "usuarioId": 1,
  "fechaCompra": "2026-09-20",
  "detalles": [
    { "equipoId": 1, "cantidad": 5, "costoUnitario": 150.0 },
    { "equipoId": 2, "cantidad": 2, "costoUnitario": 400.0 }
  ]
}
```

Respuesta esperada: **201**. El servidor calcula subtotales (750 y 800) y
**total: 1550**; el estado inicial es `PENDIENTE`. Anotar el `id` de la orden.
Casos de error a probar: proveedor inexistente/inactivo -> **400**; detalles
vacios o cantidad <= 0 -> **400**.

**Paso 4 — Ciclo de vida de la orden** (`PUT /api/ordenes-compra/{id}/estado`),
cuerpo `{"estado": "..."}`:

1. `CONFIRMADA` -> **200** (ya no se puede editar: probar `PUT /api/ordenes-compra/{id}`
   ahora -> **400** "Solo se puede modificar una orden en estado PENDIENTE").
2. `RECIBIDA` -> **200** (suma el stock de cada equipo en la BD; verificar con
   `SELECT stock FROM equipos WHERE id IN (1,2)` o en la tabla desde Supabase).
3. Transicion invalida (ej. intentar `ANULADA` sobre una orden ya `RECIBIDA`)
   -> **400** "Transicion de estado no permitida".

**Paso 5 — Anulacion (borrado logico)**: crear otra orden y anularla con
`DELETE /api/ordenes-compra/{id}` (solo funciona si esta `PENDIENTE` -> si no,
**400**). La orden queda con estado `ANULADA`, no se borra fisicamente.

**Paso 6 — Borrado logico de proveedor**: `DELETE /api/proveedores/{id}` ->
**200**; el proveedor desaparece del listado pero sigue en la BD con
`activo=false`. Buscarlo por id -> **404**.

**Formato de todos los errores** (DTO `ApiError` del JAR common):

```json
{
  "codigo": 404,
  "mensaje": "Proveedor no encontrado: 99",
  "ruta": "/api/proveedores/99",
  "fecha": "2026-09-20T10:15:30Z"
}
```

La API fue probada end-to-end contra PostgreSQL (Supabase): CRUD completo de
proveedores, ciclo de vida de ordenes (PENDIENTE -> CONFIRMADA -> RECIBIDA con
suma de stock verificada en BD), anulacion de ordenes y errores de negocio.

---

## 7. Endpoints

Base path: `/api` (configurable con `API_CONTEXT_PATH`).

### Proveedores (`/api/proveedores`)

| Metodo | Ruta | Codigos | Descripcion |
|---|---|---|---|
| `POST` | `/proveedores` | 201 / 400 | Crear proveedor |
| `PUT` | `/proveedores/{id}` | 200 / 404 | Actualizar proveedor |
| `GET` | `/proveedores/{id}` | 200 / 404 | Obtener por id |
| `GET` | `/proveedores?page&size` | 200 | Listar (solo activos, paginado) |
| `GET` | `/proveedores/buscar?nombre=&email=&contacto=&page&size` | 200 | Buscar con filtros |
| `DELETE` | `/proveedores/{id}` | 200 / 404 | Borrado **logico** (`activo=false`) |

### Ordenes de compra (`/api/ordenes-compra`)

| Metodo | Ruta | Codigos | Descripcion |
|---|---|---|---|
| `POST` | `/ordenes-compra` | 201 / 400 | Crear con detalles (valida proveedor y equipos, calcula subtotal/total) |
| `PUT` | `/ordenes-compra/{id}` | 200 / 400 / 404 | Actualizar (solo si esta `PENDIENTE`) |
| `GET` | `/ordenes-compra/{id}` | 200 / 404 | Obtener por id (con detalles) |
| `GET` | `/ordenes-compra?page&size` | 200 | Listar ordenes (paginado) |
| `DELETE` | `/ordenes-compra/{id}` | 200 / 400 / 404 | Borrado logico: estado `ANULADA` (solo si `PENDIENTE`) |
| `PUT` | `/ordenes-compra/{id}/estado` | 200 / 400 / 404 | Cambiar estado (registra historial) |

Transiciones de estado validas:

- `PENDIENTE -> CONFIRMADA`
- `CONFIRMADA -> RECIBIDA` (suma stock al equipo por cada detalle)
- `PENDIENTE -> ANULADA`

---

## 8. Arquitectura

Capas estrictas: **Controller -> Service -> Repository** (el controller nunca
accede a un repository directamente). Los repositories vienen del JAR common.

```
com.alquilereventos.compras
├── ComprasApplication.java
├── controller/   ProveedorController, OrdenCompraController
├── service/      ProveedorService, OrdenCompraService (interfaces + impl)
├── mapper/       Mapper<E,D> (generico), ProveedorMapper, OrdenCompraMapper
└── exception/    RecursoNoEncontradoException (404), ReglaNegocioException (400),
                  ApiExceptionHandler (@RestControllerAdvice -> DTO ApiError)
```

---

## 9. Estado del trabajo

1. **PASO 1** — Proyecto Maven/Spring Boot base (`pom.xml`, config, logs, README).
2. **PASO 2** — CRUD de Proveedor (mapper + service + controller + excepciones).
3. **PASO 3** — Ordenes de compra (detalles M:N, estados + historial, stock).
