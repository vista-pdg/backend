# Quizzes interactivos (HU-34)

Paquete `com.vista.pdg.quiz`. Un docente asignado al curso —o un administrador— crea un quiz, la
plataforma genera con IA un banco de `preguntas por estudiante × estudiantes` preguntas, el docente
lo revisa y publica, y cada estudiante matriculado presenta un único intento dentro de la ventana.

## Modelo

| Tabla | Contenido |
|---|---|
| `quizzes` | Curso, título, temas (claves del catálogo de algoritmos), tamaños, tiempo límite, ventana, visibilidad de nota, estado `DRAFT`/`PUBLISHED`. |
| `quiz_questions` | Banco: tema, escenario, enunciado, cuatro opciones (JSON), índice correcto, justificación. |
| `quiz_attempts` | Intento único por `(quiz, estudiante)`: inicio, plazo, cierre, estado, aciertos, incidentes. |
| `quiz_attempt_items` | Pregunta asignada, posición, permutación de opciones y opción elegida (índice original). |

Son tablas nuevas que crea `ddl-auto=update`; ninguna tabla existente cambia. Las FK a curso y a
estudiante llevan `ON DELETE CASCADE` y `created_by` lleva `SET NULL`, así que eliminar una cuenta
sigue funcionando.

## Generación del banco

`POST /api/quizzes/manage/{id}/bank/generate` genera **un lote** (hasta `quiz.generation.batch-size`,
4 por defecto) y devuelve el avance. El cliente repite la llamada hasta `complete=true`. Cada lote:

1. Elige el tema con menos preguntas en el banco, sin superar su cuota (`⌈banco / temas⌉`).
2. `QuizScenarioFactory` sortea una instancia por pregunta —valores o grafo ponderado conexo— y la
   ejecuta con el algoritmo real (`AlgorithmDispatcher`). Las operaciones sobre el lienzo se
   construyen antes con su operación `values` (insert, heapify, push, enqueue).
3. `QuizQuestionWriter` envía al modelo el escenario y los **hechos del rastro verificado**
   (`prompts/quiz/system-prompt.txt`) mediante `LlmAdapter.completeJson`.
4. Descarta las preguntas con estructura inválida, opciones repetidas o vacías, comodines («todas
   las anteriores»), enunciado trivial o longitudes que delatan la respuesta.

El modelo redacta; no calcula. Si el algoritmo rechaza la instancia sorteada, el escenario viaja sin
hechos y la pregunta es conceptual aplicada. Un fallo del proveedor responde `503
QUIZ_GENERATION_UNAVAILABLE` y el banco conserva lo ya generado.

`StubLlmAdapter` (perfil `e2e`) y `FakeLlmConfig` (pruebas) usan `QuizStubResponses`: una pregunta
válida por escenario, con la correcta en `id % 4`, sin red ni cuota.

## API

Gestión — `TEACHER` asignado al curso o `ADMIN` (`/api/quizzes/manage/**`):

| Método y ruta | Uso |
|---|---|
| `GET /courses` | Cursos gestionables con su número de matriculados. |
| `GET /topics` | Temas disponibles (catálogo de algoritmos). |
| `GET /?courseCode=` · `POST /` | Lista y creación (queda en `DRAFT`). |
| `GET /{id}` · `DELETE /{id}` | Detalle con banco y respuestas; eliminar si no hay intentos. |
| `POST /{id}/bank/generate` | Genera un lote. |
| `DELETE /{id}/questions/{questionId}` | Descarta una pregunta (solo `DRAFT`). |
| `POST /{id}/publish` | Publica; exige banco completo y cierre futuro. |
| `PUT /{id}/grade-visibility` | Muestra u oculta la nota a los estudiantes. |
| `GET /{id}/results` · `results.csv` · `results.xlsx` | Resultados, estadísticas y reportes. |

Estudiante — `STUDENT` con curso (`/api/quizzes/**`); el curso sale de la cuenta, nunca de la petición:

| Método y ruta | Uso |
|---|---|
| `GET /` | Curso y quizzes publicados con estado `UPCOMING`, `OPEN`, `IN_PROGRESS`, `FINISHED`, `MISSED`. |
| `POST /{id}/attempt` | Inicia o retoma el intento (idempotente). Nunca incluye la respuesta correcta. |
| `GET /{id}/attempt` | Estado del intento. |
| `PUT /{id}/attempt/answers/{posición}` | Responde o cambia una respuesta. |
| `POST /{id}/attempt/integrity` | Registra un incidente del modo de prueba. |
| `POST /{id}/attempt/submit` | Entrega. |

Errores (`ApiError`): `400 INVALID_QUIZ` con `fieldErrors`, `403 QUIZ_FORBIDDEN`, `404
QUIZ_NOT_FOUND` / `ATTEMPT_NOT_FOUND`, `409 QUIZ_NOT_OPEN`, `QUIZ_CLOSED`, `ATTEMPT_CLOSED`,
`QUIZ_BANK_INCOMPLETE`, `QUIZ_ALREADY_PUBLISHED`, `QUIZ_HAS_ATTEMPTS`, `QUIZ_WINDOW_PASSED`, y `422
VALIDATION_ERROR` de Bean Validation. Un quiz de otro curso, en borrador o inexistente responde el
mismo 404 al estudiante.

## Reglas

- **Plazo:** `min(inicio + tiempo límite, cierre)`, fijado al iniciar y decidido con el reloj del
  servidor. Vencido el plazo, la siguiente lectura o escritura cierra el intento como `EXPIRED` y lo
  califica con lo respondido; la vista de resultados cierra todos los vencidos del quiz.
- **Asignación:** se sortean las preguntas menos usadas, prefiriendo temas distintos; con un banco
  completo cada estudiante recibe preguntas diferentes.
- **Nota:** `aciertos / total × 5`, un decimal. El estudiante la ve solo con `showGrade`.
- **Incidentes:** se cuentan, no se sancionan automáticamente.
- **Reportes:** CSV UTF-8 con BOM y XLSX mínimo escrito con `java.util.zip` (sin dependencias);
  las celdas que empiezan por `=`, `+`, `-` o `@` se neutralizan.

Pruebas: `QuizManagementTest`, `QuizAttemptTest`, `QuizResultsTest`, `QuizScenarioFactoryTest`,
`QuizQuestionWriterTest`, `QuizReportWriterTest`. El paquete entra en la puerta de cobertura JaCoCo.
