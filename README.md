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

## Boletas por RabbitMQ

En Pedidos, Generar / ver boleta solicita un comprobante interno del pedido.
POST /api/v1/orders/{id}/receipt devuelve 202 PENDING o 200 READY si ya existe.
GET en la misma ruta devuelve NOT_REQUESTED, PENDING o READY con el HTML generado.
Ambas operaciones aplican los roles y la propiedad del pedido; un Cliente no puede consultar boletas ajenas.

La tabla order_receipt conserva una instantanea de los importes al solicitar el documento.
Un despachador revisa solicitudes pendientes cada 5 segundos y envia el ID a
receipts.exchange / receipts.generate / receipts.queue. El consumidor genera y guarda
el comprobante. Las entregas duplicadas son idempotentes y las solicitudes pendientes
se reintentan tras una caida de RabbitMQ. Los mensajes fallidos van a receipts.queue.dlq.
La cola se declara al arrancar ms-orders; no requiere cambios en ms-notifications.

La pagina consulta el estado, muestra el documento y permite descargar HTML o usar
Imprimir / guardar PDF. El comprobante conserva los precios historicos y muestra el ID
cuando el pedido no conserva el nombre del producto. No acredita pago ni emite documentos tributarios.
Una boleta ya emitida conserva su instantanea aunque se edite posteriormente el pedido.
Eliminar el pedido elimina tambien su boleta. La migracion 004 crea la tabla sin alterar los pedidos existentes.

Pruebas: mvn test (17 pruebas, incluyendo acceso, instantanea, duplicados y reintento del publicador).
## Correo de cambios de estado (prueba local)

El Compose general inicia Mailpit. Bandeja: http://localhost:8025, SMTP interno: mailpit:1025.
Los mensajes quedan capturados localmente y no se reenvian a Gmail ni Microsoft.
Documentacion de Mailpit: https://mailpit.axllent.org/docs/install/docker/

Al crear el pedido se guarda el correo de su creador desde los claims firmados email,
preferred_username o upn, validando que sean una direccion. No se usa el correo del
administrador que cambia el estado ni customerId para decidir el destinatario.
El correo no se incluye en las respuestas publicas de pedidos ni en el mensaje RabbitMQ.

Para pedidos anteriores, el creador debe abrir Pedidos: la pagina hace POST
/api/v1/orders/notification-contact y completa solo los correos faltantes de los pedidos
pertenecientes al issuer y subject de esa cuenta. Esta operacion no modifica otros pedidos,
incluso cuando quien la ejecuta es Admin. Si el token no trae correo, la pagina lo indica;
es necesario configurar Entra ID para incluir un claim de correo apropiado en el access token.
No se deduce un correo a partir del identificador numerico de cliente.

Cada cambio real de estado escribe un registro status_email en la misma transaccion del pedido.
No-op (mismo estado), errores de permisos y transiciones invalidas no generan correos.
El despachador publica el ID de evento cada 5 segundos por orders.status.exchange,
routing key status.changed, cola orders.status.email.queue. Su consumidor en ms-orders
recupera el destinatario y los estados de la base y envia por SMTP. La cola es durable,
con orders.status.email.queue.dlq para entregas rechazadas. El correo pendiente permanece
registrado aunque RabbitMQ no este disponible. El pedido no depende de la disponibilidad de SMTP.

Estados de seguimiento en status_email: PENDING, SENT, NO_RECIPIENT y FAILED.
Los fallos SMTP se reintentan hasta 5 veces, con espera de 30 segundos multiplicada
por el intento. Al agotarlos queda FAILED para revision. NO_RECIPIENT indica que no habia
correo al cambiar el estado; esos eventos no se envian retroactivamente al sincronizar contacto.
El consumidor bloquea el registro y omite eventos ya enviados. SMTP no garantiza exactamente
una entrega ante una caida entre la aceptacion del correo y el commit de la base.
La tabla conserva el evento historico incluso si se elimina el pedido.

Configuracion disponible en application.properties: SMTP_HOST, SMTP_PORT, SMTP_USERNAME,
SMTP_PASSWORD, SMTP_AUTH, SMTP_STARTTLS y MAIL_FROM. El Compose local fija mailpit y un
remitente ficticio; para envio real hay que cambiar esos valores de entorno de ms-orders.
Nunca guardar credenciales reales en el repositorio. Mailpit no tiene relay configurado.

Validacion: 22 pruebas normales del backend y EmailLocalSmokeTest, que ejecuta un cambio
por MockMvc con JWT simulado y usa RabbitMQ + SMTP reales en un vhost aislado y H2.
La prueba real solo corre con RUN_LOCAL_MAIL_SMOKE=true; requiere el vhost codex-mail-smoke
con permisos para el usuario RabbitMQ y que el contenedor de pruebas este en la red del Compose.
El mensaje de ejemplo en Mailpit usa cliente-prueba@example.test y el pedido #1 de una base
H2 temporal; no corresponde a un pedido del usuario en PostgreSQL.