# HU-06 · API de analítica pedagógica

API integrada con el tablero de HU-06. La evidencia de validación local, los pendientes de
revisión humana y el estado de CI se registran en la spec HU-06 de dev-workflow.

## Acceso y asignación

Se requiere sesión autenticada. `STUDENT` recibe 403 y la ausencia de sesión/token válido 401.
`TEACHER` solo consulta cursos asignados; `ADMIN` consulta cualquier curso existente. Cada
consulta JSON/CSV revisa la asignación en base de datos. No se reutiliza `users.course_id`, que
representa la matrícula del estudiante. Un docente sin asignaciones recibe una lista vacía.

La administración de asignaciones usa estos endpoints, todos restringidos a ADMIN:

- `GET /api/admin/courses/{code}/teachers`: docentes asignados (id, nombre y correo, solo en administración).
- `PUT /api/admin/courses/{code}/teachers/{teacherId}`: asigna una cuenta activa con rol TEACHER; sin cuerpo, 204.
- `DELETE /api/admin/courses/{code}/teachers/{teacherId}`: revoca la asignación; 204.

No se conceden asignaciones automáticas al arrancar. Para habilitar al docente de pruebas,
identificar su id mediante el listado administrativo de usuarios y realizar el PUT autenticado
para cada curso. Una revocación se aplica también al CSV y a tokens emitidos anteriormente.

## Lectura

- `GET /api/analytics/courses`: lista de `{code,name,termCode}` autorizados.
- `GET /api/analytics/report?courseCode=CEDI-G1&from=2026-04-01&to=2026-04-30`.
- `GET /api/analytics/report.csv` con exactamente los mismos filtros.

Curso y fechas son obligatorios. Solo se admiten esos tres parámetros, una vez cada uno; las
claves de curso conservan exactamente las mayúsculas/minúsculas del catálogo. Las
fechas usan AAAA-MM-DD. Parámetros individuales, desconocidos, duplicados o fechas inválidas
devuelven 400. Curso no autorizado/inexistente al consultar: 403 sin métricas.

Los días son inclusivos en `America/Bogota`: se consulta desde las 00:00 del primero hasta las
00:00 del día siguiente al último, con límite superior exclusivo. Se filtra también por el
periodo académico del curso para no mezclar cohortes si cambia su periodo. Las tres consultas
comparten una transacción de lectura `REPEATABLE_READ`.

`PedagogicalReport` devuelve curso, fechas, zona, mínimo de participantes (5), umbral de
dificultad, convención de reintentos, total publicable y las listas `structures`, `algorithms`,
`retries`. El estado de una métrica es `AVAILABLE`, `SUPPRESSED` o `UNAVAILABLE`.
JSON y CSV tienen `Cache-Control: no-store`; el CSV añade `Content-Disposition: attachment`.

## Definiciones

**Estructuras:** número de solicitudes de generación por tipo, incluidos resultados fallidos
cuando se conoce el tipo. Eventos antiguos sin `kind` son generaciones. Un tipo desconocido
se mantiene como tal, sin inferirlo del prompt. Los tipos gruesos históricos se normalizan con
el subtipo disponible; un grafo histórico sin detalle se muestra como `grafo_sin_detalle`.
Porcentajes en escala 0–100; orden descendente por frecuencia y empate por nombre.

**Algoritmos:** eventos `algorithm`, agrupados por algoritmo en mayúsculas. Las finalizaciones
no suman otra ejecución. El promedio usa `completed_steps`, los pasos recorridos reportados
para cada ejecución; una ejecución nueva empieza en cero y el máximo observado se conserva.
No se usa `step_count` (longitud del rastro) como si fueran pasos recorridos. La fecha del
filtro es la del lanzamiento y el avance es el conocido al consultar el reporte.

Si alguna ejecución del segmento no tiene medición histórica, `stepsStatus=UNAVAILABLE` y el
promedio es nulo. No se calcula un promedio de una submuestra haciéndolo pasar por el total.

**Reintentos:** una sesión tiene reintentos de una estructura si hay al menos tres solicitudes
de generación en cualquier ventana de 120 segundos. Se agrupa internamente por seudónimo,
sesión y estructura; se excluyen sesiones nulas/vacías y los eventos de algoritmo. El cociente
es sesiones con reintentos / sesiones observadas, en escala 0–1. El resaltado usa comparación
estricta `retryRate > analytics.retry-threshold` (por defecto 0.30). Un evento posterior fuera
de la ventana no invalida una secuencia anterior dentro de ella.

## Supresión

`AnalyticsDisclosure` es la única frontera de divulgación y `AnalyticsCsv` recibe únicamente
su resultado, nunca eventos crudos. Cada fila necesita al menos cinco seudónimos distintos;
cien eventos del mismo participante cuentan como una sola persona para este requisito.

Solo se agregan eventos con `student_participant=true`, derivado del rol STUDENT autenticado
al capturar, sin aceptar ese dato del cliente. Un docente que conserve `course_id` tras un
cambio de rol no cuenta como estudiante. Los históricos con este campo nulo se excluyen:
tener un curso no acredita el rol en el momento de la interacción. No se rellenan basándose
en roles actuales ni se inventa una atribución histórica. Por ello el tablero empieza a
mostrar actividad acreditada a partir de esta versión; la telemetría previa se conserva.

Una fila suprimida conserva el nombre del tema y `SUPPRESSED`, con métricas y resaltado nulos.
Se ordena por nombre al final, sin filtrar su posición relativa por el valor oculto. El texto
que debe usar la UI, y que ya emite el CSV, es:

> Datos insuficientes para garantizar el anonimato

Si una fila de estructuras se suprime, también se suprimen el total y los porcentajes que
permitirían reconstruirla por sustracción. La tasa de reintento protege adicionalmente sus
subgrupos no vacíos (con reintentos y sin reintentos): cada uno debe tener cinco participantes.
Un subgrupo vacío es válido cuando la población del segmento es suficiente. El promedio de
pasos también verifica el mínimo de participantes que aportan la medición.

No se exponen seudónimos, sesiones, prompts, número exacto de participantes ni datos de cuentas.
No se ofrece un parámetro que reduzca el mínimo de cinco ni filtros/orden por estudiante.

El CSV usa UTF-8 con BOM, CRLF, comillas escapadas y celdas textuales protegidas frente a
fórmulas. Los números suprimidos son celdas vacías; el estado explica la supresión.

## Telemetría de progreso y esquema

`POST /api/algorithm/steps` devuelve `X-Analytics-Execution` cuando produjo un rastro.
`POST /api/assistant/events` admite `{event:"algorithm_progress", executionId, stepCount}`.
El servidor deriva al propietario autenticado, valida que la ejecución le pertenezca y actualiza
atómicamente solo si el nuevo avance supera el anterior y no excede el tamaño del rastro.
Identificadores desconocidos, ajenos y valores fuera de rango no modifican datos. La respuesta
es 202; un fallo de telemetría no interrumpe el algoritmo. Retroceder, repetir el final o cambiar
de 2D a 3D no suma pasos. Una nueva ejecución tiene otra correlación aunque el algoritmo y
la longitud coincidan.

El esquema añade `teaching_assignments`, con unicidad (course_id, teacher_id) y borrado en
cascada de sus referencias, además de `generation_events.execution_id` (único, nullable) y
`completed_steps` y `student_participant` (nullable). Hibernate `ddl-auto=update` es el mecanismo existente. No hay
backfill de pasos históricos ni asignaciones inferidas. Se añade un índice curso/periodo/fecha.

## Compatibilidad y pruebas

`/api/analytics/summary`, `/events` y `/retries` responden 410 para analistas. La lectura
individual anterior contradice el mínimo de agregación de HU-06; no se mantiene como bypass.
Las pruebas de persistencia HU-16/18/21 inspeccionan la base o los servicios internos; Cypress
comprueba la captura observable, la correlación y el retiro de rutas individuales.

- `AnalyticsDisclosureTest`: orden, fronteras 4/5, denominadores, subgrupos, historial, CSV y fechas.
- `TeachingAssignmentServiceTest`, `PedagogicalAnalyticsServiceTest`: permisos, revocación,
  configuración y filtros comunes antes de leer datos.
- `PedagogicalAnalyticsTest`: integración con PostgreSQL/Testcontainers para filtros,
  rankings, CSV, identidad, sesiones, progreso autenticado y rutas retiradas.
- Frontend: `graphStore.test.ts`, `analyticsService.test.ts` y el spec Cypress de HU-21 CA-2.

Ejecutar `./mvnw verify` y las suites Cypress con perfil e2e y Chromium/Firefox antes de
publicar. La validación local parcial está registrada en la spec HU-06 de dev-workflow.
