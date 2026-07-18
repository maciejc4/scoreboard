# Scoreboard Library — Acceptance Tests

Behavioural acceptance tests covering `SPECIFICATION.md` and `API.md`. Written as
**Given / When / Then**, grouped by feature area, each with a stable id (`AT-nn`). These are
black-box tests against the public `Scoreboard` API only — they must not depend on internal types.

Traceability tags in brackets reference spec decisions (`I#`, `F#`, `P2#`, `P3#`).

---

## A. Starting a match

**AT-01 — Start creates a 0–0 match** `[I#1]`
- Given a new scoreboard
- When `startMatch("Mexico", "Canada")`
- Then a non-null `MatchId` is returned, and `getMatch(id)` shows `homeScore=0`, `awayScore=0`,
  `homeTeam="Mexico"`, `awayTeam="Canada"`, and it appears in `getSummary()`.

**AT-02 — Each start yields a distinct id** `[I#5]`
- Given a scoreboard
- When the same pair `startMatch("Spain", "Brazil")` is called twice
- Then two **different** `MatchId`s are returned and `getSummary()` contains **two** live matches.

**AT-03 — Same team in two simultaneous matches is allowed** `[I#5]`
- Given `startMatch("Spain", "Brazil")`
- When `startMatch("Spain", "Italy")`
- Then both matches are live (no exception).

**AT-04 — startedAt is populated** `[I#7]`
- Given a match started
- Then its `MatchSummary.startedAt()` is a non-null `Instant`.

## B. Recording goals

**AT-05 — recordHomeGoal increments home by 1** `[I#2, F#3]`
- Given a live match at 0–0
- When `recordHomeGoal(id)` is called 3 times
- Then the match reads `3–0`.

**AT-06 — recordAwayGoal increments away by 1** `[I#2, F#3]`
- Given a live match at 0–0
- When `recordAwayGoal(id)` is called 5 times
- Then the match reads `0–5`.

**AT-07 — Home and away scores are independent**
- Given a live match
- When home is incremented 10 times and away 2 times
- Then the match reads `10–2`.

## C. Correcting a score (decrement)

**AT-08 — correctHomeGoal decrements by 1** `[I#3, F#4]`
- Given a match at `3–0`
- When `correctHomeGoal(id)`
- Then the match reads `2–0`.

**AT-09 — Decrement below zero throws and leaves score unchanged** `[F#4]`
- Given a match at `0–0`
- When `correctHomeGoal(id)`
- Then `IllegalArgumentException` is thrown and the match still reads `0–0`.

**AT-10 — correctAwayGoal symmetric behaviour** `[F#4]`
- Given a match at `0–1`
- When `correctAwayGoal(id)` twice
- Then the first call yields `0–0`, the second throws `IllegalArgumentException`.

## D. Finishing a match

**AT-11 — Finish removes from summary and adds to history** `[I#6, I#10]`
- Given a live match `Mexico 0–5 Canada`
- When `finishMatch(id)`
- Then it no longer appears in `getSummary()`, and `getHistory()` contains a `FinishedMatch` with
  `homeScore=0`, `awayScore=5`, and a non-null `finishedAt`.

**AT-12 — Operations on a finished match throw** `[I#6]`
- Given a finished match id
- When `recordHomeGoal(id)` / `correctHomeGoal(id)` / `finishMatch(id)` is called
- Then each throws `MatchNotFoundException`.

**AT-13 — getMatch on a finished id returns empty** `[P2#5]`
- Given a finished match id
- When `getMatch(id)`
- Then `Optional.empty()` is returned (no exception).

## E. Summary ordering

**AT-14 — Worked example ordering (canonical)** `[I#7]`
- Given, started in this order and scored as shown:
  - Mexico 0 – Canada 5
  - Spain 10 – Brazil 2
  - Germany 2 – France 2
  - Uruguay 6 – Italy 6
  - Argentina 3 – Australia 1
- When `getSummary()`
- Then the order is exactly:
  1. Uruguay 6 – Italy 6
  2. Spain 10 – Brazil 2
  3. Mexico 0 – Canada 5
  4. Argentina 3 – Australia 1
  5. Germany 2 – France 2

**AT-15 — Total score descending is primary key** `[I#7]`
- Given two matches with totals 5 and 12
- Then the total-12 match precedes the total-5 match.

**AT-16 — Tie on total → most recently started first** `[I#7]`
- Given two matches with equal totals, started at different times
- Then the later-started match precedes the earlier-started one.

**AT-17 — Empty board → empty summary**
- Given a new scoreboard
- Then `getSummary()` is an empty list (not null).

## F. Single-match lookup

**AT-18 — getMatch returns current state** `[P2#5]`
- Given a live match updated to `2–1`
- When `getMatch(id)`
- Then it returns a `MatchSummary` reading `2–1`.

**AT-19 — getMatch on unknown id returns empty** `[P2#5]`
- Given a scoreboard
- When `getMatch(someUnrelatedFinishedOrUnknownId)`
- Then `Optional.empty()`.

## G. History

**AT-20 — History ordered most-recently-finished first** `[F#5a]`
- Given matches A, B, C finished in that order
- When `getHistory()`
- Then the order is `[C, B, A]`.

**AT-21 — FinishedMatch carries full payload** `[F#5b]`
- Given a match started, scored `1–2`, then finished
- Then its `FinishedMatch` exposes `matchId`, `homeTeam`, `awayTeam`, `homeScore=1`,
  `awayScore=2`, non-null `startedAt`, `finishedAt`, and a `duration()` equal to
  `Duration.between(startedAt, finishedAt)`.

**AT-22 — History is capped and evicts oldest** `[F#5c]`
- Given a scoreboard configured with `historyLimit = 2`
- When three matches are finished (oldest → newest: A, B, C)
- Then `getHistory()` contains exactly `[C, B]` (A evicted), size 2.

**AT-23 — clearHistory empties history only**
- Given some finished matches and one live match
- When `clearHistory()`
- Then `getHistory()` is empty and the live match still appears in `getSummary()`.

## H. Validation & normalization

**AT-24 — Null team name throws NPE** `[P3#3]`
- When `startMatch(null, "France")` or `startMatch("Germany", null)`
- Then `NullPointerException`.

**AT-25 — Blank team name throws IAE** `[I#4]`
- When `startMatch("   ", "France")` or `startMatch("", "France")`
- Then `IllegalArgumentException`.

**AT-26 — Self-play rejected (exact)** `[I#4]`
- When `startMatch("Spain", "Spain")`
- Then `IllegalArgumentException`.

**AT-27 — Self-play rejected after normalization (case + whitespace)** `[F#1, F#2]`
- When `startMatch("Spain", "  spain ")`
- Then `IllegalArgumentException` (normalized names equal).

**AT-28 — Display preserves original casing** `[F#2]`
- Given `startMatch("Mexico", "Canada")`
- Then the summary shows `homeTeam="Mexico"`, `awayTeam="Canada"` (not upper-cased).

**AT-29 — Names are trimmed for display** `[I#4]`
- Given `startMatch("  Mexico  ", "Canada")`
- Then `homeTeam()` returns `"Mexico"` (trimmed).

**AT-30 — Locale-independent normalization** `[F#1]`
- Given the default locale is Turkish (`tr-TR`)
- When `startMatch("istanbul", "ISTANBUL")`
- Then normalization uses `Locale.ROOT`, so the two are treated as equal → `IllegalArgumentException`
  (verifies no Turkish-`i` divergence).

## I. Error handling

**AT-31 — Unknown id on mutators throws MatchNotFound** `[I#6]`
- When `recordHomeGoal` / `recordAwayGoal` / `correctHomeGoal` / `correctAwayGoal` / `finishMatch`
  is called with an id that was never issued (or already finished)
- Then `MatchNotFoundException`.

**AT-32 — Null MatchId throws NPE** `[P3#3]`
- When any operation is called with `null` as the `MatchId`
- Then `NullPointerException`.

**AT-33 — MatchNotFoundException is a ScoreboardException** `[P3#3]`
- Then `MatchNotFoundException` is assignable to `ScoreboardException` and to `RuntimeException`.

## J. Immutability of returned data

**AT-34 — Summary list is unmodifiable** `[I#8]`
- Given a non-empty summary
- When attempting `summary.add(...)` / `summary.remove(0)`
- Then `UnsupportedOperationException`.

**AT-35 — Summary snapshot is stable after later mutations** `[I#8]`
- Given `list = getSummary()`
- When a new match is started / a goal recorded afterwards
- Then the previously returned `list` is unchanged.

**AT-36 — History list is unmodifiable** `[F#5]`
- When attempting to mutate the list from `getHistory()`
- Then `UnsupportedOperationException`.

## K. Configuration & construction

**AT-37 — Default factory uses historyLimit 1000** `[P2#1, P2#6]`
- Given `Scoreboard.inMemory()`
- Then finishing more than 1000 matches keeps history size at 1000 (oldest evicted).

**AT-38 — historyLimit below 1 rejected** `[P2#6]`
- When `ScoreboardConfig.builder().historyLimit(0).build()` (and `-1`)
- Then `IllegalArgumentException`.

**AT-39 — Null config rejected** `[P3#3]`
- When `Scoreboard.inMemory(null)`
- Then `NullPointerException`.

## L. Concurrency `[I#9, P3#2, P3#5]`

**AT-40 — No lost updates under concurrent goals**
- Given one live match
- When N threads each call `recordHomeGoal(id)` M times concurrently
- Then the final home score is exactly `N * M`.

**AT-41 — Finish is atomic w.r.t. readers**
- Given a live match, with one thread repeatedly calling `finishMatch(id)` while other threads
  repeatedly call `getSummary()` + `getHistory()`
- Then no reader ever observes the match in **both** summary and history simultaneously, nor in
  **neither** after finish completes (exactly-once transition).

**AT-42 — Concurrent starts produce unique ids**
- Given many threads calling `startMatch(...)` concurrently
- Then all returned `MatchId`s are distinct and `getSummary()` size equals the number of successful
  starts.

**AT-43 — Concurrent readers never throw**
- Given continuous concurrent mutation
- When many threads iterate `getSummary()` / `getHistory()`
- Then no `ConcurrentModificationException` (or other exception) is thrown.

---

## Coverage matrix (spec → tests)

| Requirement | Tests |
|-------------|-------|
| Start match (I#1, I#5) | AT-01..04 |
| Record goal (I#2, F#3) | AT-05..07 |
| Correct/decrement (I#3, F#4) | AT-08..10 |
| Finish + lifecycle (I#6) | AT-11..13, AT-31 |
| Summary ordering (I#7) | AT-14..17 |
| getMatch (P2#5) | AT-13, AT-18, AT-19 |
| History (I#10, F#5) | AT-20..23, AT-37 |
| Validation & normalization (I#4, F#1, F#2) | AT-24..30 |
| Error handling (I#6, P3#3) | AT-09, AT-31..33, AT-38, AT-39 |
| Immutability / snapshots (I#8) | AT-34..36 |
| Configuration (P2#1, P2#6) | AT-37..39 |
| Concurrency (I#9, P3#2, P3#5) | AT-40..43 |
