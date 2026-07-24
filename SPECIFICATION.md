# Scoreboard Library — Specification

**Status:** Phase 1 (requirements analysis) complete.
**Audience:** used to derive public interfaces, acceptance tests, and the implementation.
**Traceability:** decisions reference the grilling session in `PROMPT_HISTORY.md` (issues `I#n` / follow-ups `F#n`).

---

## 1. Purpose & scope

A Java library that maintains a live **scoreboard** for multiple **simultaneous** football
(World Cup) matches. It supports starting matches, recording/correcting scores, finishing
matches, producing an ordered summary of matches in progress, and retrieving a history of
finished matches.

**In scope:** in-memory state, single JVM, thread-safe concurrent access.
**Out of scope:** persistence, networking, UI, multi-JVM/distributed state, multi-sport
scoring rules, authentication/authorization.

## 2. Assumptions & constraints (I#9, F#3)

| # | Constraint |
|---|------------|
| A1 | Library runs **in-memory**, in a **single JVM**. No persistence. |
| A2 | Must be **thread-safe** for concurrent callers (start / record / finish / read). |
| A3 | Sport is **football**: every goal changes a score by exactly **+1** (I#2, F#3). |
| A4 | Scores are non-negative `int` (I#3). Realistic magnitudes; no overflow handling required. |
| A5 | A team name is an arbitrary non-blank string; the library does not validate against a real team registry. |

## 3. Definitions

- **Match** — a contest between a **home team** and an **away team**, in progress on the board.
- **MatchId** — an opaque, library-generated identifier returned when a match starts; the sole handle for later operations (I#1).
- **Start sequence** — a strictly monotonic `long` assigned at start time; establishes "recency" (I#7). Backs the `MatchId` (higher id = started later — see §7).
- **Total score** — `homeScore + awayScore`.
- **Summary** — an ordered, immutable snapshot of matches in progress (I#8).
- **FinishedMatch** — an immutable record of a match that has been finished (I#10).

## 4. Functional requirements

### 4.1 Start a match
- **Input:** home team name, away team name.
- **Behavior:** creates a match with score `0 – 0`, assigns a new `MatchId` and a start
  sequence, timestamps `startedAt` (`Instant`), and places it on the board.
- **Output:** the new `MatchId`.
- **Validation:** see §6. Rejects null/blank names and self-play (home == away after normalization).
- **Allowed by design (I#5):** the same team may appear in multiple simultaneous matches,
  and the same pair may be started more than once. Each start yields a distinct `MatchId`.
  The library enforces **no** "one match per team" or "no duplicate pair" policy.

### 4.2 Record a goal (I#2, F#3)
- **Operations:** `recordHomeGoal(matchId)`, `recordAwayGoal(matchId)`.
- **Behavior:** increments the respective score by exactly **1**.
- **Errors:** unknown/finished `matchId` → `MatchNotFoundException` (I#6).

### 4.3 Correct a score — decrement (I#3, F#4)
- **Operations:** `correctHomeGoal(matchId)`, `correctAwayGoal(matchId)` (names TBD in Phase 2).
- **Behavior:** decrements the respective score by exactly **1** (referee correction).
- **Errors:**
  - unknown/finished `matchId` → `MatchNotFoundException`.
  - decrement that would make the score negative → `IllegalArgumentException` (score stays at 0).

### 4.4 Finish a match (I#6, I#10)
- **Input:** `matchId`.
- **Behavior:** atomically removes the match from the live board **and** appends a
  `FinishedMatch` record to history (§4.6). After finishing, the `matchId` is no longer live;
  further record/correct/finish calls on it → `MatchNotFoundException`.
- **Errors:** unknown/already-finished `matchId` → `MatchNotFoundException`.
- **Atomicity note:** the remove-from-live and append-to-history steps MUST be observed as a
  single transition — no concurrent reader may see the match in both live summary and history,
  or in neither (§9).

### 4.5 Get summary of matches in progress (I#8, ordering §7)
- **Input:** none.
- **Output:** an **immutable, point-in-time** `List<MatchSummary>`, ordered per §7.
- Iterating the result never throws due to concurrent mutation; it reflects a consistent
  snapshot taken at call time.

### 4.6 Get finished-match history — the additional operation (I#10, F#5)
- **Input:** none.
- **Output:** an **immutable snapshot** `List<FinishedMatch>`, ordered **most-recently-finished
  first** (F#5a).
- **Record contents** (F#5b): `MatchId`, home & away team names (original display form + normalized
  form), final home & away scores, `startedAt`, `finishedAt` (`Instant`), and derived `duration`.
- **Bounding** (F#5c): history is capped at a **configurable maximum** (default **1000**). When
  full, the **oldest** finished match is evicted as new ones are added.
- **`clearHistory()`**: removes all history records.

## 5. Domain model (data types)

| Type | Kind | Fields / notes |
|------|------|----------------|
| `MatchId` | opaque value object | wraps the monotonic start `long`; `equals`/`hashCode` by value; no client-constructable meaning |
| `TeamName` (internal) | immutable value object | `original` (display, trimmed) + `normalized` (`trim` → `toUpperCase(Locale.ROOT)`); `equals`/`hashCode` use `normalized` only |
| `MatchSummary` | immutable | `matchId`, home/away names (original), home/away score, total, `startedAt` |
| `FinishedMatch` | immutable | see §4.6 |
| `Scoreboard` | service interface | the public API entry point |

All returned collections and value objects are immutable / defensively copied.

## 6. Validation & normalization (I#4, F#1, F#2)

Applied on **start** through the internal `TeamName` value object:
1. `home` and `away` MUST be non-null (`NullPointerException`) and non-blank after trimming
   (`IllegalArgumentException`).
2. `TeamName` stores both forms (F#2): `original` (trimmed, original case) for display in
   summaries/history, and `normalized` (`original.toUpperCase(Locale.ROOT)`) for identity (F#1 —
   avoids the Turkish-`i` locale bug).
3. `TeamName.equals` and `hashCode` use only `normalized`; if the home and away values are equal,
   starting the self-play match throws `IllegalArgumentException`.
4. Display MUST preserve the caller's casing (e.g. "Mexico", not "MEXICO").

## 7. Ordering rules (I#7)

**Summary order (§4.5):**
1. **Total score descending.**
2. **Tie-break:** most recently started first.

**Recency source of truth:** a strictly monotonic `long` start sequence (via `AtomicLong`),
assigned at start. It is immune to wall-clock collisions/regressions that an `Instant` tie-break
would suffer. `MatchId` is backed by this same sequence, so recency tie-break = **descending id**.
`Instant startedAt` is retained for **display only**, never for ordering.

**Comparator:** `by(totalScore).reversed().thenComparing(startSequence).reversed()`
(equivalently: higher total first; within equal totals, higher start sequence first).

**Worked example (must hold as an acceptance test):**

| Start order | Match | Total | id/seq |
|---|---|---|---|
| 1 | Mexico 0 – Canada 5 | 5 | 1 |
| 2 | Spain 10 – Brazil 2 | 12 | 2 |
| 3 | Germany 2 – France 2 | 4 | 3 |
| 4 | Uruguay 6 – Italy 6 | 12 | 4 |
| 5 | Argentina 3 – Australia 1 | 4 | 5 |

Expected summary:
1. Uruguay 6 – Italy 6 (total 12, seq 4)
2. Spain 10 – Brazil 2 (total 12, seq 2)
3. Mexico 0 – Canada 5 (total 5, seq 1)
4. Argentina 3 – Australia 1 (total 4, seq 5)
5. Germany 2 – France 2 (total 4, seq 3)

## 8. Error handling

| Condition | Exception |
|-----------|-----------|
| null argument (team name, `MatchId`, config) | `NullPointerException` (`Objects.requireNonNull`) |
| blank team name (after trim) | `IllegalArgumentException` |
| home == away (normalized) | `IllegalArgumentException` |
| decrement below zero | `IllegalArgumentException` |
| `historyLimit < 1` | `IllegalArgumentException` |
| record / correct / finish on unknown / finished `matchId` | `MatchNotFoundException` |

**Null handling ratified in Phase 3 (P3#3): idiomatic split** — `null` → `NullPointerException`;
invalid *values* → `IllegalArgumentException`. This supersedes the earlier "null team →
IllegalArgumentException" wording.

**Hierarchy (ratified, P3#3):** an unchecked base `ScoreboardException extends RuntimeException`;
`MatchNotFoundException extends ScoreboardException`. Rationale: unchecked keeps the fluent API
clean; all failures are programming/usage errors, not recoverable business conditions. Every
exception message names the offending input. Note: `getMatch(unknownId)` does **not** throw — it
returns `Optional.empty()`.

## 9. Concurrency & thread-safety (A2, I#8)

- All public operations are safe under concurrent invocation from multiple threads.
- **Live matches** kept in a concurrent structure keyed by `MatchId`. Per-match score mutation
  (record/correct) is atomic (e.g. per-match lock or atomic score fields) so concurrent
  `recordHomeGoal` calls never lose an increment.
- **Finish** performs the live-remove + history-append as a single atomic transition (§4.4).
- **Summary / history reads** return consistent immutable snapshots; readers never block writers
  into inconsistency and never throw `ConcurrentModificationException`.
- Ordering counter uses `AtomicLong`.
- Exact locking strategy (fine-grained per-match vs. a board-level lock for the finish
  transition) is a Phase 3 decision; the observable guarantees above are normative.

## 10. Non-functional requirements

- **Ease of use:** small, intention-revealing API; `MatchId` is the only handle a caller must retain.
- **Extensibility:** `Scoreboard` is an interface; ordering expressed as a `Comparator` so
  alternative orderings can be introduced without changing core logic; validation/normalization
  isolated so rules can evolve.
- **Immutability:** all outward-facing types are immutable; no internal state escapes by reference.
- **No external dependencies** required for the core (JDK only); test framework aside.

## 11. Proposed public API (informative — **superseded by §14**, kept for context)

```java
public interface Scoreboard {
    MatchId startMatch(String homeTeam, String awayTeam);

    void recordHomeGoal(MatchId matchId);
    void recordAwayGoal(MatchId matchId);
    void correctHomeGoal(MatchId matchId);   // decrement by 1
    void correctAwayGoal(MatchId matchId);   // decrement by 1

    void finishMatch(MatchId matchId);

    List<MatchSummary> getSummary();          // ordered per §7, immutable snapshot
    List<FinishedMatch> getHistory();         // most-recently-finished first, immutable snapshot
    void clearHistory();
}
```

## 12. Acceptance criteria (test scenarios)

Given/When/Then seeds for acceptance tests:

1. **Start** creates a `0–0` match and returns a non-null `MatchId`.
2. **Record goals** increment by exactly 1 each (home/away independent).
3. **Correct** decrements by 1; correcting at 0 → `IllegalArgumentException`, score unchanged.
4. **Finish** removes the match from summary and adds it to history; subsequent ops on that id → `MatchNotFoundException`.
5. **Ordering** — the §7 worked example produces the exact expected order.
6. **Validation** — null/blank/self-play (incl. `"Spain"` vs `"spain "`) → `IllegalArgumentException`.
7. **Case display** — summary shows original casing ("Mexico"), while `"Spain"`/`"SPAIN"` are treated as the same team for self-play.
8. **Multiplicity (I#5)** — starting the same pair twice yields two distinct live matches with distinct ids.
9. **Summary immutability** — returned list is unmodifiable and unaffected by later mutations.
10. **History** — most-recently-finished first; capped at configured max with oldest evicted; `clearHistory()` empties it.
11. **Concurrency** — N threads each recording M goals on one match yields exactly N×M total (no lost updates); concurrent finish + summary never observes a half-finished match.

## 13. Open items (later phases)

- Phase 2: final method names (`correct*` vs `remove*`), whether `MatchId` is exposed as a class vs interface, package layout, factory/builder for `Scoreboard` (incl. configurable history cap).
- Phase 3: locking strategy, exception hierarchy ratification, score storage (atomic fields vs guarded ints), history data structure (bounded deque/ring buffer).

---

## 14. Phase 2 — Public API (ratified)

Decisions P2#1–P2#6.

### 14.1 Obtaining a `Scoreboard` (P2#1)
`Scoreboard` is an **interface**. Instances are created via **static factory**, never a public
impl constructor:

```java
Scoreboard board = Scoreboard.inMemory();                 // defaults
Scoreboard board = Scoreboard.inMemory(config);           // custom config
```

`ScoreboardConfig` is an **immutable value object** with a builder:

```java
ScoreboardConfig config = ScoreboardConfig.builder()
        .historyLimit(500)
        .build();
```

### 14.2 History configuration contract (P2#6)
- `historyLimit` MUST be **finite and >= 1**. **No "unlimited" option** — a finite cap is forced.
- `historyLimit < 1` (including 0 and negatives) → `IllegalArgumentException`.
- Default `historyLimit` = **1000**.

### 14.3 Interface (ratified)

```java
public interface Scoreboard {
    static Scoreboard inMemory() { /* factory */ }
    static Scoreboard inMemory(ScoreboardConfig config) { /* factory */ }

    MatchId startMatch(String homeTeam, String awayTeam);

    void recordHomeGoal(MatchId matchId);    // +1 home
    void recordAwayGoal(MatchId matchId);    // +1 away
    void correctHomeGoal(MatchId matchId);   // -1 home (throws if it would go negative)
    void correctAwayGoal(MatchId matchId);   // -1 away (throws if it would go negative)

    void finishMatch(MatchId matchId);

    Optional<MatchSummary> getMatch(MatchId matchId);   // read-after-write / single lookup (P2#5)
    List<MatchSummary> getSummary();                    // ordered per §7, immutable snapshot
    List<FinishedMatch> getHistory();                   // most-recently-finished first, immutable
    void clearHistory();
}
```

- **Goal API shape (P2#2):** four explicit methods (no `Side` enum) — self-documenting, no
  null-enum failure path.
- **Mutators return `void` (P2#4).** State is read back via `getMatch` / `getSummary`.
- **`getMatch` returns `Optional`** — empty if the id is unknown or already finished
  (distinct from the record/correct/finish path, which *throws* for unknown/finished ids per §8).

### 14.4 `MatchId` contract (P2#3)
- Final, **immutable** value class; backed by the monotonic start sequence (§7).
- **Library-only construction** — no public constructor. Clients only ever *receive* a `MatchId`
  from `startMatch`; they cannot forge one. Prevents `recordHomeGoal(new MatchId(41))` reaching
  another caller's match (treat the id as a **capability handle**).
- Value-based `equals` / `hashCode`.
- `toString` is **opaque** (e.g. `Match#42`) — for debugging only, not a documented/parseable format.
- **Not** publicly `Comparable`; recency ordering stays internal to the summary comparator.

### 14.5 Null-argument handling — *ratified in §8 / §15.3*
Idiomatic split: `null` arguments → `NullPointerException` (`Objects.requireNonNull`); invalid
*values* → `IllegalArgumentException`. See §8 and §15.3.

---

## 15. Phase 3 — Implementation (ratified)

Decisions P3#1–P3#6. Target: **Java 21** (records, sealed types, modern collections API).

### 15.1 Concurrency model (P3#2 = single board lock)
- One `ReentrantReadWriteLock` per `Scoreboard` instance guards all state.
- **Write lock:** `startMatch`, `recordHomeGoal`, `recordAwayGoal`, `correctHomeGoal`,
  `correctAwayGoal`, `finishMatch`, `clearHistory`.
- **Read lock:** `getMatch`, `getSummary`, `getHistory`.
- **`finishMatch` atomicity:** live-remove + history-append occur inside a single write-lock hold,
  so no reader can observe the match in both the summary and history, or in neither (§9, §4.4).
- **Snapshot reads:** under the read lock, copy internal state into immutable records, then sort/
  return outside the lock; returned lists are unmodifiable and never throw
  `ConcurrentModificationException`.
- Rationale: goals are low-frequency, so serialized writes cost nothing meaningful; correctness
  and simplicity dominate. Fine-grained locking (`ConcurrentHashMap` + `AtomicInteger`) is a
  documented future optimization only.

### 15.2 Score storage (P3#5 = plain `int`)
- Each match holds plain `int homeScore` / `int awayScore`.
- Because every read/write occurs under the board lock, plain `int` is fully correct: no lost
  updates, no stale reads, and `correct*`'s check-then-decrement is atomic. `AtomicInteger` would
  be redundant double-synchronization (and would not help the compound `finishMatch`/`getSummary`
  atomicity anyway).

### 15.3 Exception hierarchy & null handling (P3#3 = idiomatic split)
```
RuntimeException
 └─ ScoreboardException            (unchecked base)
     └─ MatchNotFoundException     (unknown / already-finished MatchId on record/correct/finish)
```
- `null` args → `NullPointerException` via `Objects.requireNonNull` (with a named message).
- Invalid values (blank team, self-play, decrement-below-zero, `historyLimit < 1`) →
  `IllegalArgumentException`.
- `getMatch(unknownId)` → `Optional.empty()` (does not throw).

### 15.4 Internal data structures
- **Live matches:** `Map<MatchId, Match>` (e.g. `HashMap`) guarded by the lock. `Match` is an
  internal **mutable** holder (`MatchId`, immutable home/away `TeamName` values, `int` scores,
  `long startSequence`, `Instant startedAt`) — never exposed; only immutable `MatchSummary`
  records escape.
- **Start sequence & id:** an `AtomicLong` supplies a strictly monotonic sequence; each
  `startMatch` takes the next value as both the `MatchId` payload and the recency key.
- **History:** a bounded `ArrayDeque<FinishedMatch>` stored most-recently-finished first.
  `finishMatch` uses `addFirst(record)` and, if the configured limit is exceeded, `removeLast()`
  evicts the oldest entry. `getHistory()` returns an immutable copy in the deque's stored order.

### 15.5 Ordering implementation (§7)
- Sorting is done on **internal** match snapshots by `(total desc, startSequence desc)`, then
  mapped to public `MatchSummary` records — so the start sequence never leaks and `MatchId` stays
  non-`Comparable` publicly.
- Reference comparator:
  ```java
  Comparator<MatchSnapshot> order = Comparator
          .comparingInt(MatchSnapshot::total).reversed()
          .thenComparing(Comparator.comparingLong(MatchSnapshot::startSequence).reversed());
  ```

### 15.6 Value types (P3#6 = records)
- `MatchSummary`, `FinishedMatch`, and `ScoreboardConfig` are `record`s (immutable, value equality
  for free).
- `MatchId` is a final immutable value class because Java requires a public record's canonical
  constructor to be public, which would violate the capability-handle contract in §14.4. Its
  constructor is **package-private** (library-only construction); clients only ever receive one.
  `toString` is opaque (`Match#<seq>`).
- `getSummary` / `getHistory` return `List.copyOf(...)` (unmodifiable) snapshots.

### 15.7 Extensibility hooks
- `Scoreboard` is an interface; the in-memory impl is one implementation.
- Normalization, validation, and the ordering comparator are isolated so rules can evolve without
  touching the core mutation logic.
- `ScoreboardConfig` is the single extension point for tunables (currently `historyLimit`).

**Specification complete — ready for interface extraction, acceptance tests, and implementation.**
