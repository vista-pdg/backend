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
| `e2e` | Cypress y CI | `/api/generate` responde un grafo fijo (`StubLlmAdapter`): sin clave de Gemini, sin red, sin cuota. Arrancar con él deja una advertencia en el log. |

`SPRING_PROFILES_ACTIVE=e2e make run`

## Variables de entorno

| Variable | Defecto | Notas |
|---|---|---|
| `GEMINI_API_KEY` | — | obligatoria fuera de `e2e` |
| `GEMINI_API_MODEL` | `gemini-2.1-flash-lite` | |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | compose local | |
| `JWT_SECRET` | valor de desarrollo | **cámbialo en despliegue** (≥ 32 caracteres) |
| `JWT_ACCESS_EXPIRATION` | `900000` ms (15 min) | token de acceso, JWT, no revocable |
| `JWT_REFRESH_EXPIRATION` | `604800000` ms (7 días) | token de refresco, opaco, persistido y revocable |
| `AUTH_ALLOWED_EMAIL_DOMAINS` | `u.icesi.edu.co,icesi.edu.co` | vacío = cualquier dominio |
| `AUTH_MIN_PASSWORD_LENGTH` | `8` | |
| `TELEMETRY_PSEUDONYM_SECRET` | valor de desarrollo | **cámbialo en despliegue**; independiente de `JWT_SECRET` a propósito |

## Módulos

```
auth/        registro, login, par de tokens con detección de reuso, roles STUDENT/TEACHER/ADMIN
academic/    periodos académicos y cursos; GET /api/courses (público)
telemetry/   eventos de generación seudonimizados; GET /api/analytics/summary (solo TEACHER)
security/    JwtAuthFilter, 401 sin autenticación / 403 con rol insuficiente
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
**`VISTA_REPO_TOKEN`** (PAT de solo lectura sobre `backend` y `frontend`, que son privados).
