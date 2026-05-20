# Base Architecture — Spring Boot

## Layers

```
Controller  →  Service  →  Repository
    ↕               ↕
  DTO/Mapper     Domain
```

- **Controller** — receives HTTP requests, delegates to the service, never contains business logic.
- **DTO + Mapper** — the controller only exposes and receives DTOs, never domain entities.
- **Service** — business logic, orchestrates repositories and external services.
- **Repository** — data access, extends `JpaRepository` or similar.

## Package Structure

```
src/main/java/com/tuapp/
├── controller/
│   ├── StructureController.java
│   └── dto/
│       ├── GenerateRequest.java
│       └── GenerateResponse.java
├── mapper/
│   └── StructureMapper.java          ← MapStruct or manual
├── service/
│   ├── llm/
│   ├── sdd/
│   ├── generator/
│   └── layout/
├── repository/
│   └── StructureHistoryRepository.java
├── domain/
│   └── StructureHistory.java
└── exception/
    ├── GlobalExceptionHandler.java
    ├── InvalidContractException.java
    ├── LlmExhaustedException.java
    └── UnsupportedStructureException.java
```

## Global Exception Handler

`@RestControllerAdvice` centralizes all errors. No controller contains try/catch blocks — they throw exceptions and the handler captures them.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidContractException.class)
    public ResponseEntity<ErrorResponse> handleInvalidContract(InvalidContractException ex) {
        return ResponseEntity.unprocessableEntity()
            .body(new ErrorResponse("INVALID_CONTRACT", ex.getMessage()));
    }

    @ExceptionHandler(LlmExhaustedException.class)
    public ResponseEntity<ErrorResponse> handleLlmExhausted(LlmExhaustedException ex) {
        return ResponseEntity.status(503)
            .body(new ErrorResponse("LLM_EXHAUSTED", ex.getMessage(), ex.attempts()));
    }

    @ExceptionHandler(UnsupportedStructureException.class)
    public ResponseEntity<ErrorResponse> handleUnsupported(UnsupportedStructureException ex) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("UNSUPPORTED_TYPE", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", "Unexpected error"));
    }
}
```

`ErrorResponse` is a simple record: `{ code, message, details? }`.

## Properties by Environment

```
src/main/resources/
├── application.properties        ← shared values
├── application-dev.properties    ← dev: verbose logs, H2, Claude sandbox
└── application-prod.properties   ← prod: real DB, no debug logs
```

Activation: `SPRING_PROFILES_ACTIVE=dev` or `prod` in the environment variable.

```

## Formatter + Lefthook

Checkstyle validates style on every commit and apply formatting for Java files and added to git staging area. Lefthook executes it automatically.


Install lefthook: `npx lefthook install` or `brew install lefthook`.