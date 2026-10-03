# Verificación del correo institucional

El registro público ahora exige un código enviado al buzón Icesi. No necesitas comprar un
dominio ni desplegar tu propio SMTP: usa una cuenta **Gmail dedicada** como remitente.
La cuenta VISTA y sus tokens se crean solo al confirmar el código. Las cuentas anteriores
conservan su acceso; `email_verified_at` sigue siendo NULL hasta una verificación real.
La verificación prueba control del buzón, no matrícula activa ni identidad institucional.

## Gmail: configuración local

1. Crea/elige la cuenta dedicada y habilita verificación en dos pasos.
2. Genera una [contraseña de aplicación](https://support.google.com/accounts/answer/185833).
   Si Google no ofrece esta opción, revisa las restricciones de la cuenta; no uses la contraseña
   normal ni desactives la protección de la cuenta.
3. En `backend/.env` (ignorado por Git) añade estos valores. Sustituye los marcadores **localmente**;
   no compartas claves por chat, screenshots ni logs. Evita espacios de formato en la clave Google.

```dotenv
MAIL_ENABLED=true
MAIL_FROM=TU_CUENTA_DEDICADA@gmail.com
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=TU_CUENTA_DEDICADA@gmail.com
SMTP_PASSWORD=CONTRASENA_DE_APLICACION_LOCAL
SMTP_AUTH=true
SMTP_STARTTLS=true
VERIFICATION_HASH_SECRET=SECRETO_ALEATORIO_INDEPENDIENTE_DE_AL_MENOS_32_CARACTERES
```

Genera un secreto independiente con `openssl rand -hex 32` y guárdalo directamente en `.env` o
Secret Manager. No reutilices la clave JWT. Reinicia backend con `make run`; frontend no recibe
las credenciales. STARTTLS, validación del certificado y tiempos de espera están configurados.
El remitente debe ser tu cuenta o un alias autorizado; no uses un remitente `@icesi.edu.co`
sin permiso. Consulta los [parámetros SMTP oficiales](https://support.google.com/mail/answer/7104828).

Realiza después una prueba con tu propio buzón Icesi autorizado: solicita el código, revisa bandeja
y spam, confirma y comprueba el acceso. Que SMTP acepte el mensaje no demuestra su entrega.
La implementación se probó con SMTP local; el usuario confirmó además el envío Gmail en local.
La entrega desde Cloud Run requiere su propia comprobación después del despliegue.

## Desarrollo y Cypress sin enviar correos reales

```sh
# Desde backend; el resto de servicios sigue el compose existente.
docker compose --profile mail up -d
```

Mailpit captura mensajes, sin reenviarlos por internet. Interfaz en `http://127.0.0.1:8025`;
SMTP en 1025. Para este entorno **local**, sustituye la configuración por:

```dotenv
MAIL_ENABLED=true
MAIL_FROM=qa@vista.invalid
SMTP_HOST=127.0.0.1
SMTP_PORT=1025
SMTP_AUTH=false
SMTP_STARTTLS=false
VERIFICATION_HASH_SECRET=CLAVE_ALEATORIA_LOCAL_DE_AL_MENOS_32_CARACTERES
```

Usa perfil `dev,e2e` para Cypress (LLM determinista), nunca en producción. Los helpers de registro
leen el código del buzón de prueba, no de un endpoint que permita saltarse verificación.
Desde frontend: `npx cypress run --browser chrome`. Con otros puertos, ajusta `--config baseUrl=...`
y `--env mailpitUrl=http://127.0.0.1:PUERTO`. Mailpit no se publica ni se despliega con Cloud Run.
Las pruebas backend usan un mailer de captura exclusivamente bajo `src/test`, Postgres y Redis
reales de Testcontainers; `make test` no necesita Gmail. Playwright en dev-workflow usa fixtures
explícitas para revisar los estados visuales, sin afirmar entrega real.

## Contrato y protección

- `POST /api/auth/registration-code`: campos originales del registro; 202 con `verificationId`,
  `expiresAt`, `resendAvailableAt`. Sin tokens, código en respuesta ni usuario pendiente.
- `POST /api/auth/register`: campos originales más UUID `verificationId` y `verificationCode`
  de seis dígitos; 201 con el contrato de sesión existente **tras verificar**.
- Código aleatorio, 10 minutos, un uso, máximo 5 intentos. PostgreSQL almacena HMAC, no el código.
- Reenvío: 60 segundos; máximo 5 por correo/hora. Rotación invalida el código anterior.
- Presupuesto compartido del remitente: 50/hora y 200/día por defecto. 429 incluye `Retry-After`.
  Son ventanas fijas desde el primer envío, no medianoche. PostgreSQL comparte los límites entre
  instancias. Consumen presupuesto incluso si falla SMTP, para evitar tormentas de reintentos.
- 422 para prueba inválida/vencida; 503 si correo está deshabilitado/no configurado o falla SMTP.
  No hay registro alternativo sin código. Los fallos SMTP conservan el desafío anterior.
- Contraseñas y códigos permanecen en memoria del formulario; recargar exige empezar de nuevo.
  DTO sensibles redactan `toString()` para evitar exposición en logs Spring DEBUG.
- Se conserva el 409 del registro existente para correo duplicado (también al solicitar código).
  Este contrato sigue revelando existencia de cuentas; no se presenta como protección contra
  enumeración. Los límites globales acotan el abuso, pero no reemplazan controles del proveedor.
- `VERIFICATION_TTL`, `VERIFICATION_COOLDOWN`, `VERIFICATION_EMAIL_HOURLY_LIMIT`,
  `VERIFICATION_HOURLY_LIMIT`, `VERIFICATION_DAILY_LIMIT` son configurables. Mantén límites bajos
  para Gmail y revisa [sus límites](https://support.google.com/mail/answer/22839).

## Cloud Run y base de datos

`terraform-iac.registration_mail` es opcional y por defecto NULL (envío apagado). Configúralo
con remitente, username y **IDs de dos secretos existentes** en Secret Manager: contraseña SMTP
y clave HMAC. El módulo actual da acceso a esos secretos únicamente al runtime backend.
No guardes valores secretos en tfvars/state/CI. Ejemplo con nombres ficticios:

```hcl
registration_mail = {
  username           = "TU_CUENTA_DEDICADA@gmail.com"
  from               = "TU_CUENTA_DEDICADA@gmail.com"
  password_secret_id = "vista-smtp-password"
  hash_secret_id     = "vista-verification-hash"
}
```

La activación sigue el CI/CD existente: crear versiones SMTP/HMAC en Secret Manager por stdin,
configurar `REGISTRATION_MAIL` (JSON con remitente y los IDs, sin valores secretos) como variable
GitHub del repo terraform-iac, y publicar en main. Terraform plan/apply configura entorno y permisos;
los pushes de backend/frontend ejecutan calidad, E2E y actualizan sus imágenes Cloud Run.
Verifica en staging salida SMTP 587, aceptación Gmail, entrega institucional y reenvíos antes
de activar registro en producción. Configura secretos y correo junto al despliegue: mientras
`MAIL_ENABLED=false`, login existente funciona pero nuevas altas devuelven 503.

El repo utiliza `hibernate.ddl-auto=update`; este cambio añade tablas `email_verifications`,
`verification_quotas` y columna nullable `users.email_verified_at`. Revisa el esquema generado
en staging y realiza backup antes de desplegar. No modifica ni marca verificadas cuentas antiguas.
Limpieza de desafíos vencidos hace más de un día ocurre al pedir códigos. Las cuotas son dos filas.
Para desactivar envío, `MAIL_ENABLED=false`; nunca elimines la exigencia de código como rollback.
