# ms-compras — Microservicio de Compras

> Sistema de Alquiler de Equipos para Eventos (FIUNI — Sistemas Web y Distribuidos, Entrega #2)
> Microservicio desarrollado por **Setrini**. Comparte la base de datos y el modelo con `ms-alquiler` y `api-gateway` a través del JAR `common`.

---

## 1. Contexto general del sistema (lo que toda IA / nuevo integrante debe saber)

El sistema **"Alquiler de Equipos para Eventos"** está dividido en **microservicios Java con Spring Boot** que se comunican a través de un **API Gateway** y se registran y descubren dinámicamente mediante **Eureka Server**. Todos los microservicios usan una **base de datos remota compartida (Supabase / PostgreSQL)** cuyo esquema se administra **exclusivamente con Liquibase en el módulo `alquiler-eventos-common`** (los microservicios solo `validate`, nunca modifican la BD).

### Repos del sistema

| Repositorio | Puerto | Rol |
|---|---|---|
| `alquiler-eventos-eureka` | 8761 | Servidor de registro y descubrimiento (Eureka) |
| `alquiler-eventos-ms-alquiler` | 8081 | Microservicio de **alquiler**: Clientes y Órdenes de Alquiler |
| `alquiler-eventos-ms-compras` (este repo) | 8082 | Microservicio de **compras**: Proveedores y Órdenes de Compra |
| `alquiler-eventos-api-gateway` | 8080 | API Gateway (Spring Cloud Gateway): punto único de entrada, rutas por Eureka (`lb://`), Swagger unificado |
| `alquiler-eventos-common` | — | JAR compartido: entidades JPA, DTOs, repositorios Spring Data y changelog Liquibase (mantiene la BD) |
| `alquiler-eventos-ui` | 5173 | Frontend React (Vite) que consulta por el gateway |

### Flujo de una petición (arquitectura en runtime)

```
Cliente HTTP / Swagger / UI React
        │
        ▼
API Gateway :8080   (ruta lb://ms-compras)   ← descubre la instancia via Eureka
        │  /api/proveedores/**  /api/ordenes-compra/**
        ▼
Eureka :8761  ◄── heartbeats ──  ms-compras :8082  ──►  Supabase (PostgreSQL)
```

- El gateway **no tiene IPs quemadas**: resuelve `lb://ms-compras` consultando a Eureka en runtime (descubrimiento dinámico).
- La instancia se registra con `prefer-ip-address: false` para que el nombre de host sea resoluble en la misma máquina (evita timeouts por firewall).
- Los microservicios tienen `context-path: /api` (configurable), por eso las rutas del gateway son `/api/proveedores` y `/api/ordenes-compra`.

---

## 2. Qué hace este microservicio (ms-compras)

Expone la lógica de **compras de equipos a proveedores** para reposición de stock:

1. **Proveedores** (`/api/proveedores`): CRUD completo + búsqueda paginada por nombre. Borrado lógico.
2. **Órdenes de compra** (`/api/ordenes-compra`): flujo de compra con detalle por equipo, total calculado en el servidor, transiciones de estado válidas y registro de historial. Al `RECIBIDA` **suma stock** a los equipos. Borrado lógico (`ANULADA`).

### Entidades gestionadas (vienen del JAR `common`, sin modificar su definición)

| Entidad | Tipo | Relación |
|---|---|---|
| `Proveedor` | simple (`activo` para borrado lógico) | — |
| `OrdenCompra` | maestro-detalle (orden ↔ equipos: many-to-many gestionada mediante la tabla intermedia `ordenes_compra_detalle`) | N proveedor / 1 usuario, N detalles con N equipos |
| `OrdenCompraDetalle` | puente | `orden_compra_id` ↔ `equipo_id`, con `cantidad`, `costo_unitario`, `subtotal` |
| `OrdenCompraHistorial` | auditoría | `estado_anterior` ↔ `estado_nuevo` por cambio de estado |

### Reglas de negocio implementadas (`OrdenCompraServiceImpl`)

- Estado inicial de una nueva orden: **`PENDIENTE`** (nunca se acepta otro en el POST).
- El **total y los subtotales se calculan en el servidor** (`cantidad × costo_unitario`); el cliente no los envía.
- Transiciones válidas: `PENDIENTE → CONFIRMADA`, `CONFIRMADA → RECIBIDA`, `PENDIENTE → ANULADA`. Cualquier otra da `400` con mensaje de negocio.
- Al llegar a **`RECIBIDA`** se suma el stock de cada equipo (`equipo.stock += cantidad`).
- **Borrado lógico**: solo se puede anular (`ANULADA`) una orden en `PENDIENTE`; el registro no se elimina físicamente de la BD compartida.
- **Historial**: cada cambio de estado inserta una fila en `ordenes_compra_historial` con `estado_anterior`/`estado_nuevo` (hereda de `HistorialEstado` del common).
- Proveedor debe existir y estar `activo` para asociarlo a una orden.

---

## 3. Arquitectura en capas (estricta: Controller → Service → Repository)

```
com.alquilereventos.compras
├── controller/          # @RestController: solo reciben HTTP y delegan.
│   ├── ProveedorController      /api/proveedores        (6 endpoints)
│   └── OrdenCompraController    /api/ordenes-compra     (6 endpoints)
├── service/             # @Service: lógica de negocio, transacciones y validaciones.
│   ├── ProveedorService / ProveedorServiceImpl
│   └── OrdenCompraService / OrdenCompraServiceImpl
├── mapper/              # Conversión bidireccional Entity <-> DTO.
│   ├── Mapper<E,D>      (interfaz genérica: reutilización de código)
│   ├── ProveedorMapper
│   └── OrdenCompraMapper
├── repository/          # Solo búsquedas específicas de este micro (el resto viene del common).
│   └── ProveedorBusquedaRepository   (busca excluyendo borrado lógico)
└── exception/           # Manejador global de excepciones (respuestas estandarizadas).
```

**Nunca** salta capas: el controller jamás llama a un repository; pasa por el service. Las entidades/DTOs usados son los del JAR `common` (`com.alquilereventos.common.*`), tal como exige la consigna (reutilización de Entrega #1).

---

## 4. Endpoints

### `/api/proveedores`

| Método | Path | Efecto | Códigos |
|---|---|---|---|
| POST | `/proveedores` | Crea proveedor (nombre obligatorio) | 201 / 400 |
| PUT | `/proveedores/{id}` | Actualiza contacto/teléfono/email/dirección | 200 / 400 / 404 |
| GET | `/proveedores/{id}` | Obtiene un proveedor activo | 200 / 404 |
| GET | `/proveedores?page=&size=` | Lista paginada (excluye inactivos) | 200 |
| GET | `/proveedores/buscar?nombre=&page=&size=` | Búsqueda paginada por nombre | 200 |
| DELETE | `/proveedores/{id}` | **Borrado lógico** (`activo = false`) | 200 / 404 |

### `/api/ordenes-compra`

| Método | Path | Efecto | Códigos |
|---|---|---|---|
| POST | `/ordenes-compra` | Crea con detalles, calcula total, `PENDIENTE` + historial | 201 / 400 |
| PUT | `/ordenes-compra/{id}` | Actualiza solo si `PENDIENTE` (reemplaza detalles) | 200 / 400 / 404 |
| GET | `/ordenes-compra/{id}` | Obtiene con detalles | 200 / 404 |
| GET | `/ordenes-compra?page=&size=` | Lista paginada (más recientes primero) | 200 |
| PUT | `/ordenes-compra/{id}/estado` | Transición de estado + historial (+ stock si `RECIBIDA`) | 200 / 400 / 404 |
| DELETE | `/ordenes-compra/{id}` | Borrado lógico → estado `ANULADA` (solo `PENDIENTE`) | 200 / 400 / 404 |

### Cuerpos de ejemplo

Crear proveedor:
```json
{ "nombre": "Distribuidora Sonido SA", "contacto": "Laura Perez", "telefono": "0981 123 456", "email": "ventas@distribuidorasonido.com", "direccion": "Av. Mcal. Lopez 1234" }
```

Crear orden de compra (se confirma y luego se recibe → suma stock):
```json
{ "proveedorId": 1, "fechaCompra": "2026-10-20", "detalles": [ { "equipoId": 1, "cantidad": 5, "costoUnitario": 150000 } ] }
```
Cambiar estado:
```json
{ "estado": "CONFIRMADA" }
```

---

## 5. Manejo centralizado de errores

`ApiExceptionHandler` (`@RestControllerAdvice`) intercepta:

| Excepción | HTTP | Comportamiento |
|---|---|---|
| `RecursoNoEncontradoException` (controlada) | **404** | Respuesta estandarizada `ApiError` |
| `ReglaNegocioException` (controlada) | **400** | Respuesta estandarizada `ApiError` |
| Cualquier otra (no controlada) | **500** | `Mensaje genérico` sin stacktrace ni detalle técnico |

Formato de respuesta de error (igual en ambos micros):
```json
{ "codigo": 404, "mensaje": "Proveedor no encontrado: 99999", "ruta": "/api/proveedores/99999", "fecha": "2026-10-11T15:26:27.2027928-03:00" }
```

## 6. Logs (SLF4J / Logback)

- `logback-spring.xml` con rolling diario, ruta configurable con `LOG_DIR` (default `./logs`). Archivo: `logs/ms-compras.log`.
- Niveles correctos por capa (parametrizables por env):
  - `INFO` en **controller** (cada request, paths, parámetros).
  - `INFO`/`DEBUG` en **service** (pasos de negocio, cálculos, cambios de estado).
  - `DEBUG` en SQL (Hibernate).
- `LOG_LEVEL_APP`, `LOG_LEVEL_SQL` y similares controlan el nivel en runtime.

## 7. Configurabilidad (`src/main/resources/application.yml`)

**TODO es configurable por variables de entorno o yml, nada está quemado en código:**

| Variable | Default | Uso |
|---|---|---|
| `APP_NAME` | `ms-compras` | Nombre para registrar en Eureka (debe coincidir con `lb://` del gateway) |
| `SERVER_PORT` | `8082` | Puerto HTTP |
| `API_CONTEXT_PATH` | `/api` | Context path |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | — (requeridas) | Conexión a Supabase |
| `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` | `3` / `1` | Hikari (respeta límite del pooler de Supabase) |
| `spring.liquibase.change-log` | `classpath:db/changelog/db.changelog-master.yaml` (vive en el JAR common) | Liquibase corre las migraciones desde el changelog del common; Hibernate solo valida (`ddl-auto: validate`) |
| `EUREKA_ENABLED` | `true` | Permite correr sin Eureka si se necesita |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/` | Dónde está Eureka |
| `EUREKA_PREFER_IP` | **`false`** | `true` registra la IP de LAN (falla en local por firewall) |
| `EUREKA_RENEWAL_INTERVAL` / `EUREKA_EXPIRATION` | `5` / `15` | Heartbeats acelerados para demos |

## 8. Cómo correr

```bash
# Requisitos: JDK 17+, Maven, variables de entorno DB_URL/DB_USER/DB_PASSWORD seteadas.
# En Windows (PowerShell) las variables ya están persistidas en el perfil del usuario.

# 1) Instala primero el JAR common (si se actualizó):
cd alquiler-eventos-common && git pull && mvn clean install

# 2) En una terminal, arranca Eureka (recomendado como terminal 1):
cd alquiler-eventos-eureka && mvn spring-boot:run

# 3) En otra terminal, ms-alquiler:
cd alquiler-eventos-ms-alquiler && mvn spring-boot:run

# 4) En otra terminal, este micro (ms-compras):
cd alquiler-eventos-ms-compras && mvn spring-boot:run

# 5) Al final, gateway:
cd alquiler-eventos-api-gateway && mvn spring-boot:run
```

Verificar: `http://localhost:8761` → `MS-COMPRAS` UP. Probar por gateway: `http://localhost:8080/api/proveedores?page=0&size=10` → 200.

⚠️ Si Supabase está pausado (error `EMAXCONNSESSION` u `ENOTFOUND` en el arranque): darle "Resume project" en el dashboard.

## 9. Tests

```bash
mvn test        # corre los unitarios de controller, service y mappers
```

Cobertura actual: `OrdenCompraControllerTest`, `ProveedorControllerTest`, `OrdenCompraServiceImplTest`, `ProveedorServiceImplTest`, `OrdenCompraMapperTest`, `ProveedorMapperTest`, `ComprasSecurityRulesTest` (401/403/200 con `@WebMvcTest`), `CacheKeyFactoryTest` y `LoggingCacheTest` (**64 tests**).

## 10. Swagger

Unificado desde el gateway: `http://localhost:8080/swagger-ui.html` → en el desplegable de arriba a la derecha, elegir **ms-compras** (o directo `/compras/v3/api-docs`).

## 11. Mapeo a los criterios de evaluación (Entrega #2)

| Criterio | Cómo lo cumple este micro |
|---|---|
| Respeto estricto de capas | Controllers → Service → Repository/Mapper. El repo del micro solo delega en el common. Sin saltos. |
| Eureka | Self-registra (`APP_NAME:id`) via `EUREKA_DEFAULT_ZONE`; el gateway lo descubre con `lb://ms-compras` en runtime. |
| Entidades simples via gateway | `Proveedor` probado por `http://localhost:8080/api/proveedores` (POST/GET/PUT/DELETE con paginación). |
| Many-to-many via gateway | `OrdenCompra` ↔ `OrdenCompraDetalle` ↔ `Equipo` (`equipo_id` en cada detalle), probado por `http://localhost:8080/api/ordenes-compra`. |
| Borrado lógico | `DELETE` marca `activo=false` (proveedor) o `estado=ANULADA` (orden). Nada físico. |
| Manejo de fallos | `ApiExceptionHandler` → `ApiError` estandarizado; 500 genérico sin stacktrace. |
| Logs | `logback-spring.xml` a disco; INFO controller, DEBUG service/SQL. |
| Configurabilidad | `application.yml` 100% parametrizado por variables de entorno. |

---

## 12. Entrega #3 — Seguridad JWT y caché Redis

### Seguridad (segunda capa, además del Gateway)

- `ComprasSecurityConfig` monta un **Resource Server** que valida el mismo JWT
  (firma + `iss` + `exp`) aunque se llame directo al puerto `8082`.
- Por URL: `/proveedores/**` y `/ordenes-compra/**` → `ADMIN` o
  `ENCARGADO_COMPRAS`. Público: Swagger, `/v3/api-docs/**` y `/actuator/health`.
- Por método: `@EnableMethodSecurity` + `@PreAuthorize`.
- **401** (token ausente/inválido) y **403** (rol insuficiente) se devuelven como
  `ApiError` sin trazas (`SecurityErrorHandler`).
- El claim `roles` se convierte a `ROLE_*`; el **actor** de una orden se toma del
  `uid` del JWT (`usuarioId(Jwt)`), nunca del body.

### Caché Redis (cache-aside sobre DTOs)

| Sección | Objeto | TTL | Clave |
|---|---|---|---|
| `compras:proveedores` | Proveedor por ID | 1800 s | `ae:local:compras:proveedor:v1:{id}` |
| `compras:ordenes-compra` | OrdenCompra por ID | 120 s | `ae:local:compras:orden-compra:v1:{id}` |

- `@Cacheable` (GET), `@CachePut` (crear/actualizar/cambio de estado) y
  `@CacheEvict` (baja lógica); las páginas **no** se cachean como colección, solo
  se calientan sus ítems individuales (`CacheManager`).
- `LoggingCache` registra **toda** operación:
  `CACHE GET cache=... key=... result=HIT|MISS`, `CACHE SET ...`,
  `CACHE DELETE ...`, `CACHE ERROR operation=...`.
- Si Redis cae, el servicio sigue contra PostgreSQL (`CACHE ERROR`, nunca 500).
  Con `CACHE_ENABLED=false` corre sin caché (`NoOpCacheManager`).

### Variables nuevas

| Variable | Default | Uso |
|---|---|---|
| `SECURITY_JWT_SECRET` | — | Base64 32+ bytes (obligatoria) |
| `SECURITY_JWT_ISSUER` | `alquiler-eventos-seguridad` | Emisor esperado |
| `SECURITY_JWT_ALGORITHM` | `HS256` | `HS256`/`HS384`/`HS512` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis compartido |
| `CACHE_ENABLED` | `true` | Interruptor de caché |
| `CACHE_KEY_PREFIX`, `CACHE_ENVIRONMENT`, `CACHE_KEY_VERSION` | `ae`, `local`, `v1` | Formato de claves |
| `CACHE_PROVEEDORES_TTL_SECONDS` | `1800` | TTL de proveedor |
| `CACHE_ORDENES_COMPRA_TTL_SECONDS` | `120` | TTL de orden de compra |

Guía de prueba completa: `../GUIA-PRUEBAS-ENTREGA-3.md`.

> **Documentos compartidos del equipo** (viven en la carpeta del workspace, un
> nivel arriba, junto a todos los repos; no están dentro de cada repo):
> `README-ENTREGA-3.md` (plan y criterios de la Entrega 3),
> `GUIA-PRUEBAS-ENTREGA-3.md` (demo paso a paso),
> `GUIA-ADAPTAR-ENTREGA-3.md` (cómo replicar en `ms-alquiler`) y
> `cambios-para-tobi.md`.

---
*README regenerado el 2026-10-11 para reflejar el código real del repo. Sección 12 agregada para la Entrega #3.*
