# 3D Discrete Structures Generation System

## 1. General Overview

The system allows a student to describe a discrete structure in natural language — a graph, a tree, a lattice, or a relation — and obtain an interactive, animated 3D visualization in the browser.

For example, the student writes *"AVL tree with values 10, 5, 15, 3, and 7"* and sees the tree being built node-by-node in space, colored by depth, navigable with the mouse.

The backend calculates everything — topology and XYZ positions — and returns a complete JSON response. The frontend animates the construction locally using the received data without depending on the server for the animation.

---

## 2. General Architecture

```
[Student]
    │  natural language
    ▼
[React + Three.js]
    │  POST /api/generate
    ▼
[Spring Boot]
    ├── LlmService         → Claude (structured outputs) + retry loop
    ├── ContractBuilder    → JSON → typed record
    ├── ContractValidator  → validates semantics
    ├── GeneratorDispatcher → Strategy: builds topology
    └── LayoutDispatcher   → Strategy: calculates XYZ positions
    │
    │  Complete JSON: nodes with XYZ + edges + metadata
    ▼
[React + Three.js]
    │  animates the construction locally
    ▼
[Student sees the structure]
```

Communication is standard REST: a `POST` returns a `200 OK` with the complete JSON. There is no streaming — the backend calculates everything before responding. Animation is the responsibility of the frontend, which has all the data to construct it in any way it wants (level-by-level, with delays, with transitions).

---

## 3. Backend Layers

### 3.1 LlmService — GEMINI Call

Single responsibility: take the student's text and return a typed and validated contract.

It uses **structured outputs** from the Anthropic API. The model receives the contract schema and guarantees that its response satisfies it by construction — it cannot emit malformed JSON or fields outside the schema. This eliminates the entire class of JSON syntax errors.

The system prompt includes few-shot examples for each type of structure. Claude produces only JSON, without explanations or markdown.

#### Retry Loop

The call to Claude is wrapped in a loop of up to 3 attempts with exponential backoff. The key is that each retry includes the error from the previous attempt in the prompt — Claude self-corrects instead of repeating the same mistake.

```
Attempt 1 → Claude generates JSON → ContractBuilder validates
  If fails: wait 1s
  Updated Prompt: "Your previous response failed with: {error}. Please correct it."

Attempt 2 → same, wait 2s if it fails

Attempt 3 → if fails: LlmExhaustedException
  Controller returns 422 with details of the 3 attempts
```

Handled errors:
- **Rate limit (429)** — respects the `Retry-After` header if present
- **503 / ServiceUnavailable** — retry with backoff
- **Semantic validator error** — asymmetric matrix, non-existent references, etc. The error message is included in the next prompt.

### 3.2 SDD Layer — Schema-Driven Design

The design starts from the contract. Before writing the generator or the layout, the schema of each type of structure is defined. That schema is the source of truth that guides Claude's prompt, the validator, and the generator.

Three components form this layer:

#### Contracts (`model/contract/`)

Immutable Java records, one per type of structure. They represent exactly what Claude can produce — nothing more, nothing less.

`StructureContract` is the base interface with `type()` and `visual()`. The four concrete contracts are `GraphContract`, `TreeContract`, `LatticeContract`, and `RelationContract`. Each has the minimum necessary fields so that the generator can build the structure without ambiguity.

The `visual` field is optional in all of them. If Claude omits it, the layout dispatcher selects the most appropriate strategy automatically.

#### ContractBuilder (`service/sdd/ContractBuilder`)

The single entry point from `LlmService` to the typed domain. It deserializes Claude's JSON into the correct record based on the `type` field and calls `ContractValidator` before returning. If validation fails, it throws `InvalidContractException` with the message that the retry loop includes in the next attempt to Claude.

#### ContractValidator (`service/sdd/ContractValidator`)

Validates semantics that structured outputs cannot express. The most important checks:

- Symmetric adjacency matrix for undirected graphs
- Matrix dimensions == number of labels
- Edge references point to nodes that exist in labels
- Incidence columns sum to exactly 2 (simple undirected graphs) or 0 (directed)
- Non-empty operations for trees
- Order pairs do not form cycles

### 3.3 Strategy Pattern — Generators

Each type of structure has its own generator. The `GeneratorDispatcher` registers them automatically via Spring injection — there is no `if` or `switch` statements. Adding support for a new structure consists of creating a class annotated with `@Service`.

**`GraphGenerator`** — converts the matrix (adjacency or incidence) into a list of nodes and edges. For adjacency, it traverses the upper triangle for undirected graphs. For incidence, it extracts the two participating nodes of each column.

**`TreeGenerator`** — executes the contract operations on the specified structure. For AVL, it implements rotations. For heap, it implements heapify. It returns nodes with `depth`, `parent`, and type-specific properties (`balanceFactor` for AVL).

**`LatticeGenerator`** — for `divisors`, it calculates the complete poset and coverage relations (Hasse). For `power_set`, it generates all subsets. For `custom`, it uses the contract's order directly.

**`RelationGenerator`** — if there is a `rule`, it computes all pairs of the Cartesian product that satisfy it. It verifies the properties specified in `check`. It calculates equivalence classes when the relation is reflexive + symmetric + transitive.

### 3.4 Strategy Pattern — 3D Layouts

The layout converts topology into XYZ coordinates. The contract never includes positions — that is the exclusive responsibility of the layout. The `LayoutDispatcher` selects the strategy based on `visual.layout` from the contract, or automatically if not present.

**`TreeLayout3D`** — Adapted Reingold-Tilford. Y-axis = depth. XZ-plane = angular arc proportional to the width of the subtree, calculated bottom-up. No subtree invades the XZ space of another.

**`ForceDirectedLayout3D`** — 3D Fruchterman-Reingold with a cooling temperature. For graphs with multiple components, it initializes each component in a separate position before running forces.

**`RankLayout3D`** — for lattices and posets. Y-axis = rank of the element (longest path from the minimum). XZ-plane = circle per rank level. All Hasse edges point upwards in Y.

**`CircularLayout3D`** — nodes in a circle in the XZ plane. For general relations without hierarchy.

**`ClusterLayout3D`** — equivalence classes as separate clusters in XZ. The space visually encodes the partition induced by the relation.

---

## 4. Backend Response

The response is a JSON with four fields:

```json
{
  "contract": { "...validated contract as generated by Claude..." },
  "nodes": [
    {
      "id": "n1", "label": "10",
      "x": 0.0, "y": 0.0, "z": 0.0,
      "depth": 0,
      "properties": { "balanceFactor": 0 }
    }
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

In case of error after exhausting retries:

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

---

## 5. Frontend Responsibilities

The frontend receives the full JSON and owns the entire animation experience. It can build the tree level-by-level with a 150ms delay, show nodes with a scale transition, draw edges one-by-one after the nodes, or any other strategy. The backend does not impose any constraints.

The nodes in the response already include `depth` so that the frontend can sort them by level without recalculating. Edges come separated from the nodes so that the frontend can decide when to draw them.

---

## 6. Key Design Decisions

**Why REST and not SSE?**
Animation is the responsibility of the frontend — it has all the data to construct it locally with delays and transitions. SSE would add complexity to the backend (reactive WebFlux) and frontend (handling a partial stream) without offering anything the frontend cannot do with the full JSON.

**Why structured outputs and not free JSON?**
LLMs can produce malformed JSON under load or with complex inputs. Structured outputs guarantee syntactic validity — the model cannot deviate from the schema. The `ContractValidator` covers the rest: semantics that the schema cannot express.

**Why does the LLM not calculate XYZ positions?**
Positions depend on deterministic algorithms (Reingold-Tilford, Fruchterman-Reingold, poset ranking). If the LLM calculates them, the positions are arbitrary between calls and lack mathematical guarantees. Topology is the LLM's responsibility; geometry is the layout's responsibility.

**Why Strategy with Spring injection?**
Adding support for a new structure (bipartite graph, B-tree, etc.) is as simple as creating a `@Service` class. The dispatcher detects it automatically. No existing class needs to change.

---

## 7. Extensibility

To add support for a new type of structure:

1. Create the record in `model/contract/`
2. Create the generator in `service/generator/` annotated with `@Service`
3. Create the layout in `service/layout/` if it requires a specific one
4. Add a few-shot example to the system prompt in `LlmService`
5. Update the structured outputs schema in `AnthropicConfig`

No other class changes.

---

## 8. Acceptance Criteria

- [ ] The retry loop handles rate limit, semantic error, and 503 correctly.
- [ ] Each retry includes the previous error in Claude's prompt.
- [ ] `ContractValidator` rejects asymmetric matrices for undirected graphs.
- [ ] `ContractValidator` rejects references to non-existent nodes.
- [ ] `ContractBuilder` is the sole entry point to the typed domain.
- [ ] `GeneratorDispatcher` does not use `if`/`switch` statements.
- [ ] `LayoutDispatcher` automatically selects layout when `visual.layout` is missing.
- [ ] The response includes nodes with XYZ, depth, edges, and meta.
- [ ] Errors return 422 with the details of each attempt.
- [ ] Adding a new structure does not require modifying existing classes.