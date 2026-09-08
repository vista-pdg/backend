# VISTA · backend

API de VISTA, el generador 3D de estructuras discretas (Proyecto de Grado, Universidad Icesi).
Spring Boot 4 · Java 21 · PostgreSQL · JWT.

## Arranque

```bash
docker compose up -d          # Postgres 17 (:5432) y pgAdmin (:5050)
cp .env.example .env          # y pon tu GEMINI_API_KEY
make run                      # http://localhost:8080
```

Usuarios sembrados: `admin@vista.com` / `admin123` (ADMIN), `docente@u.icesi.edu.co` / `docente123`
(TEACHER), `estudiante@u.icesi.edu.co` / `estudiante123` (STUDENT, vinculado a `CEDI-G1`).

## Perfiles

| Perfil | Uso | Qué cambia |
|---|---|---|
| `dev` (defecto) | desarrollo local | logs en DEBUG |
| `prod` | despliegue | logs en INFO/WARN |
| `e2e` | Cypress y CI | `/api/generate` responde estructuras fijas (`StubLlmAdapter`: K3, C_n, pila, cola) **sin llamar a Gemini**: cualquier instrucción de árbol devuelve el K3 de 3 nodos. La cuota diaria y el límite de tasa sí aplican. Arrancar con él deja una advertencia en el log; para usar la app de verdad, arranca con `dev`. |

`SPRING_PROFILES_ACTIVE=e2e make run`

## Variables de entorno

| Variable | Defecto | Notas |
|---|---|---|
| `GEMINI_API_KEY` | — | clave de AI Studio; obligatoria fuera de `e2e` salvo con `GEMINI_VERTEX=true` |
| `GEMINI_API_MODEL` | `gemini-3.5-flash-lite` | |
| `GEMINI_VERTEX` | `false` | `true` = usar Vertex AI con credenciales de Google Cloud (ADC) y la facturación del proyecto, en vez del monedero prepago de AI Studio |
| `GEMINI_PROJECT` / `GEMINI_LOCATION` | — / `global` | proyecto y región de Vertex AI (sólo con `GEMINI_VERTEX=true`) |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | compose local | |
| `JWT_SECRET` | valor de desarrollo | **cámbialo en despliegue** (≥ 32 caracteres) |
| `JWT_ACCESS_EXPIRATION` | `900000` ms (15 min) | token de acceso, JWT, no revocable |
| `JWT_REFRESH_EXPIRATION` | `604800000` ms (7 días) | token de refresco, opaco, persistido y revocable |
| `AUTH_ALLOWED_EMAIL_DOMAINS` | `u.icesi.edu.co,icesi.edu.co` | vacío = cualquier dominio |
| `AUTH_MIN_PASSWORD_LENGTH` | `8` | |
| `TELEMETRY_PSEUDONYM_SECRET` | valor de desarrollo | **cámbialo en despliegue**; independiente de `JWT_SECRET` a propósito |
| `ASSISTANT_DAILY_QUOTA` | `40` | cuota diaria por estudiante para cursos sin cuota propia |
| `ASSISTANT_WARNING_RATIO` | `0.2` | aviso preventivo cuando restan ≤ ⌈límite·ratio⌉ mensajes |
| `ASSISTANT_RATE_PER_MINUTE` | `5` | ráfaga máxima por usuario en una ventana deslizante de 60 s |
| `ASSISTANT_TIMEZONE` | `America/Bogota` | zona en la que se cuenta el «día» y se reinicia la cuota a las 00:00 |

## Módulos

```
auth/        registro, login, par de tokens con detección de reuso, roles STUDENT/TEACHER/ADMIN
academic/    periodos académicos y cursos; GET /api/courses (público)
telemetry/   eventos de generación seudonimizados; GET /api/analytics/summary (solo TEACHER)
security/    JwtAuthFilter, 401 sin autenticación / 403 con rol insuficiente
assistant/   cuota diaria por estudiante, límite de tasa, cuota por curso (ADMIN) con auditoría
service/     LLM → contrato → validación → generador → layout 3D
seeder/      idempotente; migra USER→STUDENT, siembra periodo activo y curso CEDI-G1
```

### Sesión

Acceso: JWT de 15 min. Refresco: valor opaco de 256 bits guardado **solo como SHA-256**, agrupado por
familia. Cada rotación consume su eslabón; presentar uno ya rotado revoca la familia entera.

### Privacidad de la telemetría (R03)

`generation_events` guarda un **seudónimo** (HMAC-SHA256 del id de cuenta con
`TELEMETRY_PSEUDONYM_SECRET`, truncado a 64 bits), el código de curso, el periodo, el tipo de
estructura y la fecha. **Sin clave foránea al usuario, sin correo, sin nombre.** HMAC y no un hash
simple porque los ids son pequeños y secuenciales: con SHA-256 a secas bastaría probar 1, 2, 3…

### Generación por el asistente: de texto libre a contrato fijo

El modelo no es determinista, así que la cadena que lo rodea sí lo es:

1. `GeminiLlmAdapter` pide **salida JSON** (`responseMimeType`) con **temperatura 0** y un prompt
   con los seis esquemas (`prompts/graph/system-prompt.txt`).
2. `ContractNormalizer` traduce las formas laxas habituales (alias de tipo en dos idiomas, `values`
   sin `operations`, aristas en vez de matriz, números como texto…) al contrato estricto.
3. `ContractValidator` rechaza lo que no se pueda normalizar y el adaptador reintenta (máx. 3) con el
   error como corrección.
4. Los fallos del proveedor —créditos/cuota (429), clave (401/403), modelo (404), caída (503)— no se
   reintentan: `LlmUnavailableException` → HTTP 503 con el motivo en el mensaje.
5. `LayoutDispatcher` garantiza una posición por nodo (bosques, aristas rotas, pistas de layout
   desconocidas): nada se dibuja encima del origen.

`GenerationPipelineTest` recorre esa cadena por tipo de estructura sin modelo. Para probar el modelo de
verdad (consume créditos, no corre en CI):

```bash
set -a; source .env; set +a
GEMINI_LIVE_TESTS=true ./mvnw -Dtest=GeminiLiveGenerationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

#### Si AI Studio responde «prepayment credits are depleted»

Ese 429 es el **monedero prepago del proyecto de AI Studio** (https://ai.studio/projects → Billing),
independiente de la cuenta de facturación de Google Cloud. Opciones: recargar ahí, o usar Vertex AI
con la facturación de Cloud (créditos de prueba incluidos):

```bash
gcloud auth application-default login
gcloud services enable aiplatform.googleapis.com --project TU_PROYECTO
# backend/.env
GEMINI_VERTEX=true
GEMINI_PROJECT=TU_PROYECTO
GEMINI_LOCATION=global
```

### Cuota del asistente (HU-17)

Cada `POST /api/generate` **reserva** antes de llamar al modelo: primero el contador diario
(`assistant_usage`, una fila por usuario y día calendario en `ASSISTANT_TIMEZONE`, incrementada con
`UPDATE … WHERE count < :limit` para que dos peticiones simultáneas no pasen del límite), luego la
ventana de ráfaga en memoria (`RateLimiter`, por instancia). Si algo falla no se toca Gemini:

| Situación | Estado | Código | Cabeceras |
|---|---|---|---|
| dentro de límites | 200 | — | `X-Quota-Limit`, `X-Quota-Remaining`, `X-Quota-Reset` |
| cuota diaria agotada | 429 | `DAILY_QUOTA_EXCEEDED` (mensaje literal de la HU) | `X-Quota-Remaining: 0`, `X-Quota-Reset` |
| más de 5 en un minuto | 429 | `RATE_LIMITED` | `Retry-After` (segundos, redondeado hacia arriba) |

`GET /api/assistant/quota` devuelve el estado (`limit`, `used`, `remaining`, `resetsAt`, `warning`).
La cuota por curso la fija el administrador con `PUT /api/admin/courses/{code}/quota`
(1–1000) y queda en `quota_changes` con autor, valores y marca de tiempo
(`GET /api/admin/courses/{code}/quota-history`). El cambio aplica de inmediato a todos los
estudiantes del curso; la ráfaga no descuenta cuota.

## Pruebas

```bash
make test        # unitarias + integración (Testcontainers levanta Postgres)
./mvnw verify    # además aplica la puerta JaCoCo: 80 % de líneas en los módulos de la HU vigente
```

La suite sustituye Gemini por `FakeLlmConfig`. Los códigos de estado se prueban también contra un
servidor real (`HttpStatusContractTest`): MockMvc no ejecuta el reenvío a `/error` y no ve que
pisaba el 403.

## CI

`.github/workflows/ci.yml`: Spotless → `verify` → E2E. El E2E es el flujo reutilizable
`vista-pdg/dev-workflow/.github/workflows/e2e.yml`, que levanta este backend con el frontend de
`main` y corre Cypress en Chromium y Firefox. Requiere el secreto de organización
**`VISTA_REPO_TOKEN`** (PAT de solo lectura sobre `backend` y `frontend`).
