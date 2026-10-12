# HU-33 · Gestión académica de cursos y asignaciones

El administrador crea grupos dentro de un periodo académico existente y gestiona la matrícula
vigente de estudiantes y los docentes responsables. Todas las rutas siguientes requieren
`ROLE_ADMIN` bajo `/api/admin`; el token de un docente o estudiante recibe 403.

| Método | Ruta | Resultado |
|---|---|---|
| `GET` | `/terms` | Periodos con `code` y `active`. |
| `POST` | `/courses` | Crea un grupo con `code`, `name`, `termCode`; devuelve 201 y `CourseDto`. |
| `GET` | `/students` | Estudiantes activos con su `courseCode` actual o `null`. |
| `GET` | `/courses/{code}/students` | Estudiantes matriculados en el grupo. |
| `PUT` | `/students/{id}/course` | Asigna o cambia la matrícula con `{ "courseCode": "CEDI-G2" }`. |
| `DELETE` | `/students/{id}/course` | Retira la matrícula vigente; devuelve 204. |
| `GET` | `/courses/{code}/teachers` | Docentes asignados. |
| `PUT` | `/courses/{code}/teachers/{id}` | Asigna un docente activo; devuelve 204. |
| `DELETE` | `/courses/{code}/teachers/{id}` | Revoca la asignación; devuelve 204. |

El código del grupo se normaliza a mayúsculas, es único y admite de 2 a 32 caracteres
alfanuméricos o guiones. El nombre admite de 2 a 160 caracteres. La matrícula exige una cuenta
activa con rol `STUDENT` y un curso de periodo activo. Un estudiante conserva una sola matrícula
vigente en `users.course_id`; moverlo no modifica eventos de telemetría anteriores. La creación
de cursos en periodos cerrados conserva la capacidad de registrar grupos históricos, pero esos
grupos no aparecen en el registro público ni admiten nuevas matrículas.

`GET /api/analytics/courses` consulta las asignaciones docentes actuales en cada petición:
asignar un docente habilita el grupo y revocarlo elimina el acceso de inmediato. El administrador
ve todos los cursos. La selección de curso y fecha del tablero HU-06 sigue aplicando agregación
mínima de cinco estudiantes; estas rutas no cambian ese umbral.

Pruebas: `CourseManagementTest` cubre creación y catálogo, matrícula y retiro, rechazo de
actores sin permiso, periodos cerrados y concesión/revocación del acceso analítico. La validación
de interfaz en ambos navegadores y la evidencia se registran en
`../dev-workflow/hus/HU-33-gestion-cursos-y-asignaciones.md`.
