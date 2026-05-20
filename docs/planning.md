# Planning Prompt — 3D Discrete Structures Generator

## Project Context

You are helping plan and implement an interactive web system that allows students of discrete mathematics to generate 3D visualizations of structures (graphs, trees, lattices, relations) from natural language.

The system consists of two parts:
- **Frontend**: React + Three.js — receives the complete response from the backend and dynamically animates the 3D construction locally.
- **Backend**: Spring Boot — orchestrates the LLM call, validates the contract, generates the topology, and calculates the 3D layout.

## Technology Stack

- Backend: Java 21 + Spring Boot 4.0.6
- LLM: Gemini API
- Frontend: React + react-three-fiber / Three.js
- Communication: Simple REST — POST returns a complete JSON with topology + XYZ positions

---

## General Flow

```
POST /api/generate
Body: { "prompt": "AVL tree with 10, 5, 15, 3" }

    ↓ LlmService         → Claude generates the JSON contract (structured outputs)
    ↓ ContractBuilder    → deserializes JSON → typed record + validates
    ↓ ContractValidator  → validates semantics (symmetry, references, etc.)
    ↓ GeneratorDispatcher → Strategy: builds topology (nodes, edges, properties)
    ↓ LayoutDispatcher   → Strategy: calculates XYZ positions per algorithm

Response 200 OK:
{
  "contract": { ...validated contract... },
  "nodes": [ { "id", "label", "x", "y", "z", "depth", "properties": {} } ],
  "edges": [ { "id", "from", "to", "weight", "directed" } ],
  "meta": { "type", "subtype", "properties": { "reflexive": true, ... } }
}
```

The frontend receives the full JSON and animates the construction in Three.js locally — level-by-level for trees, with configurable delays. The backend knows nothing about the animation.

---

## What needs to be built — full backend

### 1. LLM Layer (`service/llm/`)

Implement `LlmService` which:
- Calls the Anthropic API with a specialized **system prompt** in discrete mathematics.
- Uses **structured outputs** to guarantee that the response is a valid JSON that satisfies the contract schema — the model cannot output fields that do not exist in the schema nor malformed JSON.
- Implements a **retry loop with exponential backoff** to handle:
  - `ResourceExhaustedException` (Anthropic rate limit — respect `Retry-After` header)
  - `ServiceUnavailableException` (transient 503s)
  - Semantic error from `ContractValidator` (asymmetric matrix, non-existent references, etc.)
- Maximum of **3 attempts**. On each retry, include the previous error in the prompt so that Claude can self-correct. On the third failure, throw `LlmExhaustedException`.

```
Attempt 1:
  → Claude generates JSON (structured outputs)
  → ContractBuilder deserializes and validates
  → If OK: return contract
  → If fails: wait 1s, add to prompt:
    "Your previous response failed with: {error}. Please correct it."

Attempt 2:
  → same, wait 2s if it fails

Attempt 3:
  → if fails: throw LlmExhaustedException with all accumulated errors
    → the controller returns 422 with the details of the failure
```

### 2. SDD Layer — Schema-Driven Design (`model/contract/` + `service/sdd/`)

The contract is the single source of truth. It is defined first and guides Claude's prompt, the validator, and the generator.

#### Contracts (`model/contract/`)

**`StructureContract`** — base interface
```java
public interface StructureContract {
    String type();
    VisualOptions visual(); // nullable — layout dispatcher selects layout if null
}
```

**`GraphContract`**
- `directed` — boolean
- `weighted` — boolean
- `labels` — List<String> (name of each node)
- `matrix.kind` — "adjacency" | "incidence"
- `matrix.data` — List<List<Integer>>
- `matrix.edges` — edge metadata (only for incidence: id, label, weight)

**`TreeContract`**
- `subtype` — "bst" | "avl" | "heap" | "trie" | "rb"
- `operations` — List<{op: "insert"|"delete", values: [...]}>
- Alternative: `nodes` with {id, value, parent, side} for already constructed trees

**`LatticeContract`**
- `subtype` — "divisors" | "power_set" | "boolean" | "custom"
- `params` — {n} for divisors and boolean
- `elements` and `order` — only for subtype "custom" (pairs [a,b] indicating a ≤ b)

**`RelationContract`**
- `set` — List of elements
- `rule` — "divides" | "less_than" | "congruent_mod_n" (generator computes the pairs)
- `pairs` — explicit alternative to rule, List<[Object, Object]>
- `check` — List of properties to verify: "reflexive" | "symmetric" | "antisymmetric" | "transitive"

#### ContractBuilder (`service/sdd/ContractBuilder`)

The single entry point from `LlmService` into the typed domain. Responsibilities:
- Deserializes the JSON from Claude into the correct Java record based on the `type` field.
- Calls `ContractValidator` before returning.
- If validation fails, throws `InvalidContractException` with the message that the retry loop includes in the next attempt.

#### ContractValidator (`service/sdd/ContractValidator`)

Validates semantics that structured outputs cannot guarantee:
- Symmetric adjacency matrix for undirected graphs (`data[i][j] == data[j][i]`).
- Matrix dimensions == number of labels (`data.length == labels.size()`).
- Edge references point to nodes that exist in labels.
- Incidence columns sum to exactly 2 (for simple undirected graphs).
- Incidence columns sum to exactly 0 (for directed graphs).
- Non-empty operations for trees.
- Non-empty set for relations.
- Order pairs do not form cycles (for relations with antisymmetry).

### 3. Strategy Pattern — Generators (`service/generator/`)

```java
public interface StructureGenerator<T extends StructureContract> {
    String supportedType();
    GeneratedStructure generate(T contract);
}
```

**`GraphGenerator`**
Converts the matrix to a list of `Node3D` and `Edge3D`.
- Adjacency: traverses the upper triangle for undirected, the full matrix for directed.
- Incidence: traverses columns, extracts the two participating nodes by sign.

**`TreeGenerator`**
Executes the contract operations on the specified structure.
- AVL: implements simple and double rotations.
- Heap: implements heapify up/down.
- Returns nodes with `depth`, `parent`, and specific properties (`balanceFactor` for AVL).

**`LatticeGenerator`**
- `divisors`: calculates the poset of divisors of n and coverage relations (Hasse).
- `power_set`: generates all subsets and inclusion relations.
- `boolean`: Boolean algebra of n variables.
- `custom`: uses elements and order from the contract directly.

**`RelationGenerator`**
- If there is a `rule`: computes all pairs of the Cartesian product that satisfy the rule.
- Verifies properties specified in `check`.
- Calculates equivalence classes if the relation is reflexive + symmetric + transitive.

**`GeneratorDispatcher`**
```java
@Service
public class GeneratorDispatcher {
    private final Map<String, StructureGenerator<?>> generators;

    // Spring automatically injects all StructureGenerator beans
    public GeneratorDispatcher(List<StructureGenerator<?>> all) {
        this.generators = all.stream().collect(
            Collectors.toMap(StructureGenerator::supportedType, g -> g)
        );
    }

    public GeneratedStructure dispatch(StructureContract contract) {
        var gen = generators.get(contract.type());
        if (gen == null) throw new UnsupportedStructureException(contract.type());
        return gen.generate(contract); // Type-safe cast by design
    }
}
```

No `if` or `switch` statements. Adding a new type = creating a `@Service` that implements the interface.

### 4. Strategy Pattern — 3D Layouts (`service/layout/`)

```java
public interface LayoutStrategy {
    String supportedLayout();
    Map<String, Vec3> compute(GeneratedStructure structure);
}
```

**`TreeLayout3D`** — Adapted Reingold-Tilford
- Y = -depth × Y_STEP (root at Y=0, leaves lower).
- XZ = angular arc proportional to the width of the subtree (calculated bottom-up).
- Arc radius grows with depth.
- Guarantees no subtree invades the XZ space of another.

**`ForceDirectedLayout3D`** — 3D Fruchterman-Reingold
- Repulsion between all pairs, attraction along edges.
- Cooling temperature (50-100 iterations).
- Component-wise initialization for disconnected graphs.

**`RankLayout3D`** — lattices and posets
- Y = rank (longest path from the poset minimum).
- XZ = circle per rank level.
- All Hasse edges point upwards in Y.

**`CircularLayout3D`** — general relations
- Nodes in a circle in the XZ plane.

**`ClusterLayout3D`** — equivalence relations
- Equivalence classes as clusters in XZ.

**`LayoutDispatcher`**
- If `contract.visual().layout()` is present → uses that strategy.
- If not → automatically selects:
  - tree/avl/bst/heap/trie → TreeLayout3D
  - graph → ForceDirectedLayout3D
  - lattice / partial order relation → RankLayout3D
  - equivalence relation → ClusterLayout3D
  - general relation → CircularLayout3D

### 5. Controller and Response (`controller/StructureController`)

```java
@RestController
@RequestMapping("/api")
public class StructureController {

    @PostMapping("/generate")
    public ResponseEntity<StructureResponse> generate(@RequestBody UserRequest req) {
        try {
            // 1. LLM → validated contract (with internal retry loop)
            StructureContract contract = llmService.generate(req.prompt());

            // 2. Generator → topology
            GeneratedStructure structure = generatorDispatcher.dispatch(contract);

            // 3. Layout → XYZ positions
            Map<String, Vec3> positions = layoutDispatcher.compute(structure);

            // 4. Assemble response
            return ResponseEntity.ok(StructureResponse.of(contract, structure, positions));

        } catch (LlmExhaustedException e) {
            return ResponseEntity.unprocessableEntity()
                .body(StructureResponse.error(e.getMessage(), e.attempts()));
        } catch (UnsupportedStructureException e) {
            return ResponseEntity.badRequest()
                .body(StructureResponse.error(e.getMessage(), 0));
        }
    }
}
```

**Successful response format:**
```json
{
  "contract": { "...validated contract..." },
  "nodes": [
    { "id": "n1", "label": "10", "x": 0.0, "y": 0.0, "z": 0.0,
      "depth": 0, "properties": { "balanceFactor": 0 } }
  ],
  "edges": [
    { "id": "e1", "from": "n1", "to": "n2", "weight": null, "directed": false }
  ],
  "meta": {
    "type": "tree", "subtype": "avl",
    "nodeCount": 5, "edgeCount": 4,
    "computedProperties": {}
  }
}
```

**Error response format:**
```json
{
  "error": true,
  "message": "Failed to generate a valid contract after 3 attempts",
  "attempts": [
    { "attempt": 1, "error": "Asymmetric matrix at position [1][2]" },
    { "attempt": 2, "error": "Label 'X' on edge does not exist in labels" },
    { "attempt": 3, "error": "ResourceExhaustedException: rate limit" }
  ]
}
```

### 6. Package Structure

```
src/main/java/com/tuapp/
├── controller/
│   └── StructureController.java
├── model/
│   ├── contract/
│   │   ├── StructureContract.java       ← base interface
│   │   ├── GraphContract.java
│   │   ├── TreeContract.java
│   │   ├── LatticeContract.java
│   │   └── RelationContract.java
│   ├── generated/
│   │   ├── GeneratedStructure.java
│   │   ├── Node3D.java
│   │   └── Edge3D.java
│   ├── response/
│   │   └── StructureResponse.java
│   └── Vec3.java
├── service/
│   ├── llm/
│   │   └── LlmService.java              ← Claude API + retry loop
│   ├── sdd/
│   │   ├── ContractBuilder.java         ← deserializes JSON → typed record
│   │   └── ContractValidator.java       ← validates semantics
│   ├── generator/
│   │   ├── StructureGenerator.java      ← Strategy interface
│   │   ├── GraphGenerator.java
│   │   ├── TreeGenerator.java
│   │   ├── LatticeGenerator.java
│   │   ├── RelationGenerator.java
│   │   └── GeneratorDispatcher.java
│   └── layout/
│       ├── LayoutStrategy.java          ← Strategy interface
│       ├── TreeLayout3D.java
│       ├── ForceDirectedLayout3D.java
│       ├── RankLayout3D.java
│       ├── CircularLayout3D.java
│       ├── ClusterLayout3D.java
│       └── LayoutDispatcher.java
└── config/
    └── AnthropicConfig.java
```

### 7. System Prompt for Claude (literal in LlmService)

```
You are an assistant specialized in discrete mathematics.
Your sole function is to convert natural language descriptions of discrete
structures into a structured JSON contract.

STRICT RULES:
- Respond ONLY with valid JSON. No explanations, no markdown, no ```json.
- The first character of your response must be { and the last must be }.
- Always use the "type" field with one of these exact values:
  "graph" | "tree" | "lattice" | "relation"
- Node IDs are always strings.
- Never calculate XYZ positions or colors — that is not your responsibility.
- The "visual" field is optional; omit it if the user does not specify it.
- If the request is ambiguous, choose the simplest and most common interpretation.

EXAMPLES:

User: "a complete graph of 4 vertices with weights 1 to 6"
{"type":"graph","directed":false,"weighted":true,"labels":["A","B","C","D"],"matrix":{"kind":"adjacency","data":[[0,1,2,3],[1,0,4,5],[2,4,0,6],[3,5,6,0]]}}

User: "AVL tree inserting 10, 5, 15, 3, 7"
{"type":"tree","subtype":"avl","operations":[{"op":"insert","values":[10,5,15,3,7]}],"visual":{"layout":"hierarchical3d","colorBy":"depth"}}

User: "Hasse diagram of the divisors of 12"
{"type":"lattice","subtype":"divisors","params":{"n":12},"visual":{"layout":"levels3d","colorBy":"rank"}}

User: "divisibility relation on the set {1, 2, 3, 4, 6}"
{"type":"relation","set":[1,2,3,4,6],"rule":"divides","check":["reflexive","antisymmetric","transitive"]}

User: "directed graph with nodes A, B, C and edges A→B weight 3, B→C weight 1, A→C weight 7"
{"type":"graph","directed":true,"weighted":true,"labels":["A","B","C"],"matrix":{"kind":"adjacency","data":[[0,3,7],[0,0,1],[0,0,0]]}}
```

---

## Acceptance Criteria

- [ ] The retry loop handles rate limit, invalid JSON, and semantic errors correctly.
- [ ] On each retry, the previous error is included in Claude's prompt.
- [ ] `ContractValidator` rejects asymmetric matrices for undirected graphs.
- [ ] `ContractValidator` rejects references to non-existent nodes.
- [ ] `ContractBuilder` is the sole entry point to the typed domain.
- [ ] `GeneratorDispatcher` has no `if`/`switch` statements — uses Spring injection.
- [ ] `LayoutDispatcher` automatically selects layout when `visual.layout` is missing.
- [ ] The response includes nodes with XYZ positions, edges, and metadata.
- [ ] Errors return 422 with the details of each failed attempt.
- [ ] Adding a new structure = creating a `@Service` class without modifying existing code.