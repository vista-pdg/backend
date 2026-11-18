# Transporte Vertex/Gemini

Google GenAI 1.78.0 sustituye 1.5.0. Se conserva gemini-3.5-flash-lite y ADC/Vertex con
la identidad Cloud Run actual. Las pruebas locales no consumen cuota: GeminiTransportTest
usa el SDK real contra HttpServer con respuestas controladas.

## Presupuesto por generación

- Aplicación: máximo tres intentos compartidos entre formato inválido y fallos transitorios.
- SDK: HttpRetryOptions.attempts=1; OkHttp.retryOnConnectionFailure=false evita otra capa
  de repetición. Un 503 conserva prompt/contexto y no se convierte en corrección de JSON.
- Por instancia: semáforo justo de diez llamadas síncronas. Los límites de Dispatcher solos
  regulan las llamadas asíncronas de OkHttp; no bastan para generateContent síncrono.
- Cola: hasta 5s; saturación/interrupción devuelven LlmUnavailableException sin reintento local.
- Llamada: hasta 25s, reducidos al tiempo restante. Presupuesto total 55s desde generate,
  incluyendo esperas y backoff. Compatible con cliente y Hosting de 60s, proxy nginx de 58s.
- Logs INFO registran modelo, espera, duración y ocupación; sin prompts ni credenciales.
  El contador active se toma después de liberar el permiso, no es un máximo histórico.

Variables opcionales: GEMINI_MAX_CONCURRENT_REQUESTS (1–40, default10),
GEMINI_QUEUE_TIMEOUT_MS (1–10000, default5000), GEMINI_REQUEST_TIMEOUT_MS
(1–55000, default25000), GEMINI_TOTAL_TIMEOUT_MS (1–55000, default55000).
Validación al arrancar. No elevar límites sin medir cuota, tiempos y memoria.

## Dependencias de seguridad

Spring Boot 4.0.8; parches dentro de las líneas administradas: Tomcat 11.0.27,
Jackson2 2.21.8 y Jackson3 3.1.8. Los overrides BOM son temporales hasta que Boot los
incluya. Se mantienen ambas ramas Jackson: el código y algunas librerías usan Jackson2,
y Boot usa Jackson3. No mezclar versiones de un mismo BOM.

OSV se revisa con dependency:tree JSON y dev-workflow/scripts/audit-maven-osv.py.
Un resultado sin avisos solo significa que OSV no conoce entradas para esas versiones en
ese momento; no certifica ausencia de vulnerabilidades ni escanea la imagen del SO.
Evidencia completa en dev-workflow/performance/results/2026-10-19-sdk178.
