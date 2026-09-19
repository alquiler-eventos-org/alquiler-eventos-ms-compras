# alquiler-eventos-ms-compras

Microservicio de **compras** del sistema de alquiler de equipos para eventos.
Gestiona **proveedores** y **ordenes de compra** (con detalles M:N hacia `Equipo`,
historial de estados y stock), usando las entidades, repositorios y DTOs del JAR
compartido [`alquiler-eventos-common`](https://github.com/alquiler-eventos-org/alquiler-eventos-common).

> Rol del usuario en el sistema: **Encargado de Compras**.

---

## Stack

| Tecnologia | Version |
|---|---|
| Java | 21 (LTS, Temurin) |
| Spring Boot | 3.4.5 |
| PostgreSQL | 17 (Supabase, BD compartida con ms-alquiler) |
| Liquibase | incluido en Spring Boot (changelog del JAR common) |
| SpringDoc OpenAPI | 2.6.0 |
| Maven | 3.9+ |

---

## Requisitos previos

1. **JDK 21** y **Maven 3.9+**.
2. Haber compilado e instalado el JAR common en el repo local de Maven:

   ```bash
   git clone https://github.com/alquiler-eventos-org/alquiler-eventos-common.git
   cd alquiler-eventos-common
   mvn clean install
   ```

---

## Variables de entorno

| Variable | Descripcion | Ejemplo |
|---|---|---|
| `DB_URL` | URL JDBC de PostgreSQL (Supabase) | `jdbc:postgresql://host:5432/postgres` |
| `DB_USER` | Usuario de la BD | `postgres` |
| `DB_PASSWORD` | Contrasena de la BD | `***` |
| `SERVER_PORT` | Puerto HTTP (default `8082`) | `8082` |
| `API_CONTEXT_PATH` | Context path (default `/api`) | `/api` |
| `LOG_DIR` | Carpeta de logs (default `./logs`) | `./logs` |
| `LOGGING_CONFIG` | Config de logback (default `classpath:logback-spring.xml`) | |
| `DEFAULT_PAGE` / `DEFAULT_PAGE_SIZE` | Paginacion por defecto (default `0` / `10`) | |
| `LOG_LEVEL_APP` / `LOG_LEVEL_SQL` | Niveles de log de la app y del SQL (default `INFO` / `DEBUG`) | |

> Las credenciales de la BD **nunca** se commitean: van por variables de entorno
> (o en un `.env` local ignorado por git).

---

## Como correr

```bash
# con las variables DB_URL, DB_USER y DB_PASSWORD definidas
mvn spring-boot:run
```

Al arrancar:

- **Liquibase** aplica los changeSets pendientes del changelog del JAR common
  (la primera corrida agrega las columnas `activo` a `proveedores` y `clientes`).
- **Hibernate** valida las 16 entidades contra el esquema (`ddl-auto: validate`).
- Swagger UI: <http://localhost:8082/api/swagger-ui.html>

Compilar y verificar (debe pasar antes de cada commit):

```bash
mvn clean install
```

---

## Endpoints

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
| `GET` | `/ordenes-compra?estado=&proveedorId=&fechaDesde=&fechaHasta=&page&size` | 200 | Buscar con filtros |
| `DELETE` | `/ordenes-compra/{id}` | 200 / 400 / 404 | Borrado logico: estado `ANULADA` (solo si `PENDIENTE`) |
| `PUT` | `/ordenes-compra/{id}/estado` | 200 / 400 / 404 | Cambiar estado (registra historial) |

Transiciones de estado validas:

- `PENDIENTE -> CONFIRMADA`
- `CONFIRMADA -> RECIBIDA` (suma stock al equipo por cada detalle)
- `PENDIENTE -> ANULADA`

---

## Arquitectura

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

## Estado del trabajo

1. **PASO 1** — Proyecto Maven/Spring Boot base (`pom.xml`, config, logs, README).
2. **PASO 2** — CRUD de Proveedor (mapper + service + controller + excepciones).
3. **PASO 3** — Ordenes de compra (detalles M:N, estados + historial, stock).
