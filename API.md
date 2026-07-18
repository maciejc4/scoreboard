# Scoreboard Library — Public API

Reference for every type and public method exposed by the library. Derived from
`SPECIFICATION.md` (§14 Public API, §15 Implementation). Target: **Java 21**.

- **Package:** `com.mc4.scoreboard`.
- **Entry point:** the `Scoreboard` interface, obtained via static factory.
- **Thread-safety:** every method on a `Scoreboard` instance is safe for concurrent use by
  multiple threads (single internal `ReentrantReadWriteLock`).
- **Immutability:** all returned types (`MatchId`, `MatchSummary`, `FinishedMatch`,
  `ScoreboardConfig`) are immutable; returned collections are unmodifiable point-in-time snapshots.

---

## 1. `Scoreboard` (interface)

The library's single entry point.

### Factory methods

| Signature | Description |
|-----------|-------------|
| `static Scoreboard inMemory()` | Creates a scoreboard with default configuration (`historyLimit = 1000`). |
| `static Scoreboard inMemory(ScoreboardConfig config)` | Creates a scoreboard with the given configuration. |

`inMemory(config)` throws `NullPointerException` if `config` is `null`.

### Operations

#### `MatchId startMatch(String homeTeam, String awayTeam)`
Starts a new match at `0 – 0` and places it on the live board.

- **Returns:** a fresh, unique `MatchId` — the handle for all later operations on this match.
- **Notes:** the same team may play in multiple simultaneous matches, and the same pair may be
  started more than once; each call returns a distinct `MatchId`.
- **Throws:**
  - `NullPointerException` — `homeTeam` or `awayTeam` is `null`.
  - `IllegalArgumentException` — either name is blank (after trim), or the two names are equal
    after normalization (`trim` + `toUpperCase(Locale.ROOT)`), i.e. self-play.

#### `void recordHomeGoal(MatchId matchId)` / `void recordAwayGoal(MatchId matchId)`
Increments the home / away score by exactly **1**.

- **Throws:**
  - `NullPointerException` — `matchId` is `null`.
  - `MatchNotFoundException` — the match is unknown or already finished.

#### `void correctHomeGoal(MatchId matchId)` / `void correctAwayGoal(MatchId matchId)`
Decrements the home / away score by exactly **1** (referee correction).

- **Throws:**
  - `NullPointerException` — `matchId` is `null`.
  - `MatchNotFoundException` — the match is unknown or already finished.
  - `IllegalArgumentException` — the score is already `0` (would go negative); the score is left
    unchanged.

#### `void finishMatch(MatchId matchId)`
Removes the match from the live board and appends a `FinishedMatch` record to history, as a single
atomic transition. Afterwards the `matchId` is no longer live.

- **Throws:**
  - `NullPointerException` — `matchId` is `null`.
  - `MatchNotFoundException` — the match is unknown or already finished.

#### `Optional<MatchSummary> getMatch(MatchId matchId)`
Looks up a single live match (e.g. read-after-write).

- **Returns:** `Optional` of the current `MatchSummary`, or `Optional.empty()` if the id is unknown
  or already finished. **Does not throw** for unknown ids.
- **Throws:** `NullPointerException` — `matchId` is `null`.

#### `List<MatchSummary> getSummary()`
Returns all matches in progress as an immutable, point-in-time snapshot.

- **Ordering:** total score **descending**; ties broken by **most recently started first**.
- **Returns:** an unmodifiable `List<MatchSummary>` (never `null`; empty if no live matches).
  Safe to iterate; never throws `ConcurrentModificationException`.

#### `List<FinishedMatch> getHistory()`
Returns the history of finished matches as an immutable snapshot.

- **Ordering:** **most recently finished first**.
- **Bounding:** at most `historyLimit` entries; oldest evicted once full.
- **Returns:** an unmodifiable `List<FinishedMatch>` (never `null`; empty if none finished).

#### `void clearHistory()`
Removes all finished-match records from history. Does not affect live matches.

---

## 2. `ScoreboardConfig` (immutable value / record)

Tunable configuration, built via a fluent builder.

| Accessor | Type | Description |
|----------|------|-------------|
| `historyLimit()` | `int` | Maximum finished matches retained. Finite, `>= 1`. Default `1000`. |

### Builder

```java
ScoreboardConfig config = ScoreboardConfig.builder()
        .historyLimit(500)
        .build();
```

| Method | Description |
|--------|-------------|
| `static Builder builder()` | New builder pre-populated with defaults. |
| `Builder historyLimit(int limit)` | Sets the history cap. |
| `ScoreboardConfig build()` | Builds the immutable config. |

- **Throws:** `IllegalArgumentException` if `historyLimit < 1` (including `0` and negatives). There
  is **no** "unlimited" option — a finite cap is enforced.

---

## 3. `MatchId` (immutable final value class)

Opaque handle identifying a match. **Capability handle** — clients only ever receive one from
`startMatch` and cannot construct or forge one.

- **Construction:** no public constructor (constructor is package-private).
- **Equality:** value-based `equals` / `hashCode`.
- **`toString`:** opaque, for debugging only (e.g. `Match#42`); **not** a documented or parseable
  format.
- **Ordering:** intentionally **not** publicly `Comparable`; recency ordering is internal.

---

## 4. `MatchSummary` (immutable record)

A point-in-time view of a live match.

| Accessor | Type | Description |
|----------|------|-------------|
| `matchId()` | `MatchId` | The match handle. |
| `homeTeam()` | `String` | Home team name, original casing as supplied (trimmed). |
| `awayTeam()` | `String` | Away team name, original casing as supplied (trimmed). |
| `homeScore()` | `int` | Current home score (`>= 0`). |
| `awayScore()` | `int` | Current away score (`>= 0`). |
| `total()` | `int` | Derived: `homeScore + awayScore`. |
| `startedAt()` | `Instant` | When the match started (display only; not the ordering key). |

---

## 5. `FinishedMatch` (immutable record)

A record of a match that has been finished.

| Accessor | Type | Description |
|----------|------|-------------|
| `matchId()` | `MatchId` | The (now inactive) match handle. |
| `homeTeam()` | `String` | Home team name (original casing, trimmed). |
| `awayTeam()` | `String` | Away team name (original casing, trimmed). |
| `homeScore()` | `int` | Final home score. |
| `awayScore()` | `int` | Final away score. |
| `startedAt()` | `Instant` | When the match started. |
| `finishedAt()` | `Instant` | When the match was finished. |
| `duration()` | `Duration` | Derived: `Duration.between(startedAt, finishedAt)`. |

> The normalized (upper-cased) name form used for the self-play check is an internal detail and is
> not exposed on the public record.

---

## 6. Exceptions

```
RuntimeException
 └─ ScoreboardException              (unchecked base for all library-specific failures)
     └─ MatchNotFoundException       (record/correct/finish on an unknown or finished MatchId)
```

- **`ScoreboardException extends RuntimeException`** — unchecked base type. Catch this to handle any
  library-specific failure.
- **`MatchNotFoundException extends ScoreboardException`** — the referenced match is not live.

Standard JDK exceptions used for input contracts:

- **`NullPointerException`** — any `null` argument (`Objects.requireNonNull`, with a named message).
- **`IllegalArgumentException`** — invalid *values*: blank team name, self-play, decrement below
  zero, `historyLimit < 1`.

---

## 7. Usage example

```java
Scoreboard board = Scoreboard.inMemory();

MatchId mexCan = board.startMatch("Mexico", "Canada");
MatchId spaBra = board.startMatch("Spain", "Brazil");

// Mexico 0 – Canada 5
for (int i = 0; i < 5; i++) board.recordAwayGoal(mexCan);

// Spain 10 – Brazil 2
for (int i = 0; i < 10; i++) board.recordHomeGoal(spaBra);
for (int i = 0; i < 2;  i++) board.recordAwayGoal(spaBra);

List<MatchSummary> summary = board.getSummary();
// -> [ Spain 10–2 Brazil (total 12), Mexico 0–5 Canada (total 5) ]

board.finishMatch(mexCan);
List<FinishedMatch> history = board.getHistory();
// -> [ Mexico 0–5 Canada, finishedAt=... ]
```
