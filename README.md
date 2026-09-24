# ms-orders

Servicio de pedidos de Pedidos360, Java 21 / Spring Boot. PostgreSQL y Liquibase.

## Ejecutar

Con Docker Desktop y Java 21:

```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run
```

API: http://localhost:8081/api/v1/orders
Swagger: http://localhost:8081/swagger-ui/index.html
Catalogo: http://localhost:8080 (configurable con MS_CATALOG).
La imagen Docker escucha en 8080; configurar la publicacion del puerto y la base por entorno.

Configuracion por entorno: DB_URL (host:puerto/base, predeterminado localhost:5433/mydatabase),
DB_USERNAME, DB_PASSWORD, SERVER_PORT, ENTRA_ISSUER y ENTRA_AUDIENCE.
Las credenciales locales de ejemplo no son para despliegue. Se conserva soporte para .env.
El puerto 5433 coincide con db-orders del compose general: usar una sola de esas bases.
Hibernate valida; solo Liquibase cambia el esquema.
Las migraciones anteriores se conservan. La migracion 003 agrega version e identidad y convierte importes a decimales.
Los pedidos existentes sin identidad solo son visibles para Admin/Operador.
Los datos ficticios se cargan solo con SPRING_LIQUIBASE_CONTEXTS=demo, en PostgreSQL y bases nuevas.
No activar demo en una base con pedidos reales.

## API y roles

Swagger es publico. La API exige un access token Entra con issuer y audience configurados.
Los roles distinguen mayusculas/minusculas. Orders reenvia el bearer token al catalogo;
ambos servicios deben aceptar su audience. No se usa el hash del usuario como identidad.

| Metodo | Ruta | Roles |
| --- | --- | --- |
| GET | /api/v1/orders | Admin, Operador; Cliente solo identidad propia |
| GET | /api/v1/orders/{id} | Admin, Operador; Cliente solo identidad propia |
| POST | /api/v1/orders | Admin, Operador |
| PUT | /api/v1/orders/{id} | Admin, Operador; solo CREADO |
| PATCH | /api/v1/orders/{id}/status?status=ACEPTADO | Admin, Operador |
| DELETE | /api/v1/orders/{id} | Admin |

POST y PUT:

```json
{"customerId":101,"items":[{"productId":1,"quantity":2}]}
```

El servidor toma precios del catalogo y calcula el total con decimales. Establece ID, estado y fechas.
No se pueden repetir productos ni usar productos inactivos o cantidades no positivas.
POST devuelve 201 y Location; lista vacia devuelve 200 con []; DELETE borra permanentemente y devuelve 204.
Errores: 400 datos invalidos, 401 token ausente/invalido, 403 rol insuficiente,
404 pedido inexistente/no visible, 409 transicion o actualizacion concurrente en conflicto,
502 catalogo inaccesible o respuesta invalida. Errores de dominio usan ProblemDetail.

Transiciones: CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO.
Se permite CANCELADO desde CREADO o ACEPTADO. Repetir el estado actual no modifica el pedido.
Los precios guardados son historicos; consultar pedidos no depende del catalogo.
El campo opcional product de cada item se devuelve null.

## Limites de integracion

Como ms-catalog permite leer productos solo a Admin/Operador, esta version permite crear/editar
pedidos con esos mismos roles. customerId es un identificador comercial; NO concede acceso.
La identidad propietaria es issuer + subject del creador. Crear por cuenta de otro cliente no
le asigna acceso automaticamente: requiere una relacion de clientes con identidades verificada.

El cambio a ACEPTADO registra el estado, pero aun no reserva ni descuenta stock.
El PATCH de catalogo reemplaza stock; no proporciona una reserva atomica/idempotente.
La reserva distribuida, compensaciones, RabbitMQ y Kafka quedan como integracion posterior.
No usar este flujo como garantia de disponibilidad de inventario.

La pantalla OrdersPage actual del frontend usa estados en ingles (PENDING, CONFIRMED, etc.).
Debe usar los estados de esta API en espanol antes de gestionar transiciones desde esa pantalla.

## Pruebas

```powershell
.\mvnw.cmd test
```

H2 en modo PostgreSQL, catalogo y decodificacion JWT simulados. Sin Docker ni llamadas a Entra.
Cubren migraciones, CRUD, precios, validacion, estados y permisos.
No reemplazan una prueba real de PostgreSQL/Entra/catalogo.
