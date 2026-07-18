# Prompt History — Scoreboard Library Design Session

This file is a verbatim log of the design/grilling session for the Java scoreboard
library. User prompts are preserved as written (only language and formatting were
lightly corrected for readability). Assistant responses are recorded in full.

---

## Prompt 1 — User

> Let's go through the following description of the library that I am to implement in
> Java. I would like us to first go over the requirements and corner cases, and clarify
> exactly what needs to be built. Next, we will define the interfaces/API that clients
> will use. Finally, we will move on to the technical implementation details in Java,
> selecting solutions that ensure the library is easy to use, extensible, and thread-safe
> (concurrency). Error handling should also be taken into consideration.
>
> For each possible issue you spot during our session, I want you to provide an example of
> how it could occur and suggest a few solutions on how to mitigate it.
>
> One note: I want you to save every single prompt of mine (including this one) and your
> response into a file you will create named `PROMPT_HISTORY.md`. It must contain our
> entire conversation. Do not alter the content of my prompts — the only things you can do
> are correct language errors and improve text formatting to make the file easier to read.
> Once we finish the requirements analysis, I would like you to create a `SPECIFICATION.md`
> document — a detailed specification based on which I will later be able to create
> interfaces, acceptance tests for them, and subsequently, the implementation.
>
> **Initial description of requirements:**
>
> You work for a sports data company and must implement a scoreboard library that supports
> multiple simultaneous matches.
>
> **Core Operations (Required)**
>
> 1. Start a new match
> 2. Update the score
> 3. Finish a match
> 4. Get a summary of matches in progress
>    Return the matches in progress ordered by:
>    - Total score (descending)
>    - If tied → most recently started match first
> 5. Add exactly one additional operation of your choice
>
> **Example Scenario**
>
> If the following matches are started in the specified order and updated with these scores:
>
> - Mexico 0 – Canada 5
> - Spain 10 – Brazil 2
> - Germany 2 – France 2
> - Uruguay 6 – Italy 6
> - Argentina 3 – Australia 1
>
> Expected summary ordering:
>
> - Uruguay 6 – Italy 6
> - Spain 10 – Brazil 2
> - Mexico 0 – Canada 5
> - Argentina 3 – Australia 1
> - Germany 2 – France 2

### Assistant — Response 1

**Session plan**

- **Phase 1 (now):** requirements + corner cases.
- **Phase 2:** API / interfaces.
- **Phase 3:** Java implementation — usability, extensibility, concurrency, error handling.
- Every prompt + response is logged to `PROMPT_HISTORY.md`; `SPECIFICATION.md` is produced once Phase 1 is closed.
- Ground rule (yours): for each issue flagged → concrete example + a few mitigation options. Expect push-back; that is the point.

**The decision everything hangs on**

- **I#1 — How does a client identify a match after starting it?** (a) `startMatch` returns an opaque `MatchId`, and update/finish take it; or (b) matches are keyed by the team pair, and every op takes team names. Example: client starts "Germany vs France", later calls `updateScore("Germany","France",2,2)`; with (b) this becomes ambiguous the moment a team appears in two live matches or a pair repeats. Mitigations: opaque id (no ambiguity, O(1) update/finish, client must hold it); team-pair natural key (ergonomic, but forces "one live match per team" + "no duplicate pair"); hybrid (return id + lookup-by-teams). Recommendation: opaque id.
- **I#2 — Update semantics: absolute or incremental?** Example: client thinks 2–2, sends absolute (3,2); if two absolute updates race, a stale client overwrites a newer score → lost goal. Incremental (+1) composes safely but can't correct mistakes. Mitigations: absolute set (simplest, matches "update the score"); incremental delta; both (absolute core + `homeGoal/awayGoal`). Recommendation: absolute pair as core.
- **I#3 — Score validation.** Illegal candidates: negatives, decreases vs current, absurd values. Example: `updateScore(id,-1,0)` or 5→3. Mitigations: reject negatives / allow decreases; reject both; clamp at 0. Recommendation: reject negatives, allow decreases (real referee corrections), `int`.

**Match lifecycle & validation**

- **I#4 — Team name validation & self-play.** Cases: null, empty, blank, home==away, case/whitespace variants. Example: `startMatch(null,"France")` or "Spain vs Spain". Mitigations: validate up-front (reject null/blank, reject equal teams), decide trim + case policy. Recommendation: reject null/blank/equal, trim, case-sensitive, store raw.
- **I#5 — Same team already playing / duplicate match.** Depends on I#1. Example: "Spain vs Brazil" live, client starts "Spain vs Italy". Mitigations: forbid (team-in-one-match index + `TeamAlreadyPlayingException`); allow (independent matches, needs opaque ids); forbid exact-duplicate pair only.
- **I#6 — Finish semantics / idempotency.** Example: client retries `finishMatch(id)` after timeout. Mitigations: throw `MatchNotFoundException`, or make idempotent no-op. Recommendation: throw on unknown.

**Summary & ordering**

- **I#7 — Tie-break precision under concurrency.** "Most recently started" needs strict monotonic ordering; wall-clock `Instant` can collide within a millisecond or move backwards. Example: Uruguay and Spain both total 12 — equal timestamps → undefined order → breaks the expected output. Mitigations: monotonic `AtomicLong` sequence as ordering source of truth; `Instant` for display only. Recommendation: monotonic insertion sequence.
- **I#8 — Summary snapshot semantics.** Immutable snapshot vs live view. Example: iterating summary while another thread finishes a match → `ConcurrentModificationException`/torn read. Mitigation: return immutable point-in-time `List<MatchSummary>`. Recommendation: immutable snapshot.

**Scope**

- **I#9 — Concurrency & deployment scope.** In-process, single JVM, multi-threaded? In-memory only or persistence? Read-heavy vs write-heavy? Assumption: in-memory, single JVM, thread-safe, no persistence.
- **I#10 — Your 5th operation.** Options: `cancelMatch`, `getMatch`/lookup, `matchesInProgressCount`, `resetBoard`, finished-match history. Your choice.

Requested rulings on I#1–I#10; I#1, I#2, I#9, I#10 are load-bearing.

---

## Prompt 2 — User

> 1. Let's use an artificial id.
> 2. Incremental.
> 3. Reject negatives; additional method to decrease; `int`.
> 4. Reject null, blank, equal; trim; uppercase each team so we are not case-sensitive.
> 5. I want to allow that — it's a scoreboard library, not a football policy check.
> 6. Throw.
> 7. `AtomicLong` for the sequence, `Instant` for display.
> 8. We should return a snapshot.
> 9. In memory, single JVM, thread-safe.
> 10. Matches history (all finished match results).

### Assistant — Response 2

Confirmed I#1–I#10. Five follow-up traps opened by the answers:

- **F#1 — Locale bug in uppercasing.** `String.toUpperCase()` uses the default locale; in a Turkish locale "istanbul" → "İSTANBUL" (dotted capital I), so the same team name normalizes differently on different machines. Mitigation: always `toUpperCase(Locale.ROOT)`. Also normalize (trim+uppercase) *before* the `home == away` check so "Spain" vs "spain " is rejected.
- **F#2 — Uppercasing destroys display case.** Expected output shows "Mexico", "Uruguay" — mixed case. If we store only the uppercased name, summaries render "MEXICO". Mitigation: store the original string for display, keep the normalized (trim+uppercase) form only as the identity/equality key. Options: (a) keep both original + normalized; (b) uppercase everywhere (accept "MEXICO" output); (c) case-insensitive compare without mutating stored value. Recommendation: (a).
- **F#3 — Increment step.** Fixed `+1`, or an arbitrary positive amount? Football = +1, but a generic "sports data" library (rugby/basketball) scores in varying amounts. Options: fixed +1 (`recordHomeGoal`); `+n` with `n > 0` validated; both. Recommendation: `+n` with validation, `+1` convenience.
- **F#4 — Decrement below zero.** Score 0, client decrements → would go negative. Options: throw `IllegalArgumentException`; clamp at 0; allow. Recommendation: reject (would violate the non-negative invariant); symmetric home/away decrement methods.
- **F#5 — History design (from op #10).** Three sub-decisions: (a) **ordering** — finish-time descending (most recently finished first), or insertion order? (b) **record contents** — teams, final score, start + finish `Instant`, duration, id? (c) **unbounded growth** — history grows forever → `OutOfMemoryError` in a long-running JVM. Example: a server up for months records thousands of matches until OOM. Mitigations: bounded ring buffer with a configurable cap; explicit `clearHistory()`; document unbounded + leave capping to the client. Recommendation: bounded cap (configurable, sane default) + finish-time-desc ordering + full immutable record.

Plus one structural note deferred to Phase 3: **MatchId** can wrap the same monotonic `AtomicLong` used for recency (higher id = started later), collapsing two counters into one — id gives identity *and* tie-break ordering.

Requested rulings on F#1–F#5 to lock Phase 1.

---

## Prompt 3 — User

> 1. F#1 — let's use `Locale.ROOT`.
> 2. F#2 — (a).
> 3. F#3 — it's a football World Cup, we don't need to worry about rugby now.
> 4. F#4 — throw.
> 5. F#5 — *(no answer provided)*

### Assistant — Response 3

Confirmed:

- **F#1** — normalize with `toUpperCase(Locale.ROOT)`, trim before the `home == away` check.
- **F#2** — store original name for display + normalized name for identity/equality (option a).
- **F#3** — fixed `+1` per goal; no arbitrary-amount API. Core ops: `recordHomeGoal(id)` / `recordAwayGoal(id)`, each +1; corrections decrement by 1.
- **F#4** — decrement below zero throws `IllegalArgumentException`; symmetric home/away decrement methods.

**F#5 was left blank.** Proposed defaults offered to close Phase 1:

- **(a) Ordering** — history returned most-recently-finished first.
- **(b) Record contents** — immutable `FinishedMatch`: `MatchId`, both team names (original + normalized), final home/away score, start `Instant`, finish `Instant`, duration.
- **(c) Bounding** — configurable cap (default 1000) + `clearHistory()`; oldest evicted when full.

Awaiting confirm/override on F#5 to lock Phase 1 and generate `SPECIFICATION.md`.

---

## Prompt 4 — User

> Sorry — F#5) a.

### Assistant — Response 4

Interpreted as accepting the recommended history defaults: (a) recency ordering
(most-recently-finished first), (b) full `FinishedMatch` payload, (c) configurable cap
(default 1000) with oldest evicted + `clearHistory()`. Flagged that if only sub-decision
(a) was meant, the payload/bounding can still be amended without blocking.

**Phase 1 (requirements analysis) closed.** Generated `SPECIFICATION.md` capturing all locked
decisions I#1–I#10 and F#1–F#5, with: purpose/scope, assumptions, functional requirements
(start / record / correct / finish / summary / history), domain model, validation &
normalization rules, ordering rules with the worked example, error handling, concurrency &
thread-safety guarantees, non-functional requirements, a proposed public API (informative),
acceptance-test scenarios, and open items deferred to Phase 2 (API) and Phase 3 (implementation).

Offered to proceed to Phase 2 (grill the interface/API).

---

## Prompt 5 — User

> Phase 2, Phase 3 — let's follow this order. BTW, the next phases should also be stored in
> `SPECIFICATION.md` (under new sections).

### Assistant — Response 5

Acknowledged: proceed Phase 2 → Phase 3; append each phase's ratified decisions to
`SPECIFICATION.md` under new sections. Opened **Phase 2 — API / interfaces** with six decisions:

- **P2#1 — Construction & config.** Plain constructor vs static factory vs builder, given the
  history cap must be injectable. Example: `new InMemoryScoreboard(1000)` telescopes when a
  second knob is added. Recommendation: static factory `Scoreboard.inMemory()` + immutable
  `ScoreboardConfig` (with builder).
- **P2#2 — Goal API shape.** Four explicit methods (`record/correct` × `Home/Away`) vs two
  methods + a `Side` enum. Trade-off: enum halves surface but adds a null-enum failure path.
  Recommendation: four explicit methods.
- **P2#3 — `MatchId`.** Final immutable value class, library-only construction, value
  `equals/hashCode`, opaque `toString`, not publicly `Comparable`. Trap: guessable sequential
  ids could be forged (`new MatchId(41)`) → mitigation: no public constructor, treat as
  capability handle.
- **P2#4 — Mutator return type.** `void` vs returning updated `MatchSummary`. Recommendation:
  `void`.
- **P2#5 — Single-match read accessor.** Add `Optional<MatchSummary> getMatch(MatchId)` to
  support read-after-write/testing without scanning the summary. Recommendation: add it.
- **P2#6 — History config contract.** `historyLimit >= 1`, default 1000, negative →
  `IllegalArgumentException`, optional `unlimited()` escape hatch (documented memory risk).

Requested rulings on P2#1–P2#6.

---

## Prompt 6 — User

> P2.1 — static factory. P2.2 — four methods. P2.3 — library-only construction. P2.4 — void.
> P2.5 — ok, let's add it. P2.6 — force (a finite cap).

### Assistant — Response 6

Ratified all of Phase 2 and wrote **§14 Phase 2 — Public API (ratified)** into `SPECIFICATION.md`:
static-factory construction with immutable `ScoreboardConfig` (+builder); `historyLimit >= 1`,
default 1000, no unlimited option; the full `Scoreboard` interface (four explicit goal methods,
`void` mutators, `Optional<MatchSummary> getMatch`, `getSummary`, `getHistory`, `clearHistory`);
and the `MatchId` capability-handle contract (library-only construction, value equality, opaque
`toString`, not publicly `Comparable`). Null-argument handling deferred to Phase 3.

Opened **Phase 3 — implementation** with these topics: target Java version; concurrency/locking
model; exception hierarchy + null handling; history data structure; score storage; value types
as records.

---

## Prompt 7 — User

> P3.1 — Java 21. P3.2 — (a). P3.3 — idiomatic-split. P3.4 — ok. P3.5 — could you describe the
> problem in detail? P3.6 — records.

### Assistant — Response 7

Ratified: **P3#1** Java 21; **P3#2** single board-level `ReentrantReadWriteLock`; **P3#3**
idiomatic-split exceptions (null → `NullPointerException`, bad value → `IllegalArgumentException`,
unknown/finished id → `MatchNotFoundException`, unchecked base `ScoreboardException`); **P3#4**
bounded `ArrayDeque` history, reversed-copy on read; **P3#6** value types as `record`s with
`MatchId`'s canonical constructor package-private.

**P3#5 — detailed explanation given (score storage: plain `int` vs `AtomicInteger`):**
A score update is a **read-modify-write** (`read → +1 → write`); concurrent unsynchronized access
loses updates (A and B both read 5, both write 6 → one goal lost), and plain fields also risk
**stale reads** (no memory barrier). Two safe options: `AtomicInteger` (`incrementAndGet` is an
atomic CAS + barrier) or a lock around every access. But atomicity requirements are **bigger than
one field**: `finishMatch` is a compound live-remove + history-append transition, and `getSummary`
needs a consistent snapshot across *all* matches — both force the board-level lock anyway (P3#2a).
Since every score read/write already happens under that lock, plain `int` is fully correct (no
lost updates, no stale reads, `correct*`'s check-then-decrement is atomic). `AtomicInteger` would
be redundant double-synchronization and misleading (implies lock-free access we don't offer); it
would only be the right tool under the rejected fine-grained model (P3#2b).
**Recommendation:** plain `int` guarded by the board lock — awaiting confirmation before writing
the Phase 3 spec section.

---

## Prompt 8 — User

> Ok, plain `int` guarded by the board lock.

### Assistant — Response 8

Confirmed P3#5 (plain `int` under the board lock). **Phase 3 closed.** Wrote **§15 Phase 3 —
Implementation** into `SPECIFICATION.md`: single `ReentrantReadWriteLock` model (write vs read
lock mapping, atomic `finishMatch`, snapshot reads); plain-`int` score storage rationale;
ratified exception hierarchy (`ScoreboardException` → `MatchNotFoundException`) and idiomatic
null split; internal data structures (`HashMap` live matches, `AtomicLong` sequence/id, bounded
`ArrayDeque` history with oldest-eviction and reversed read); ordering implementation on internal
snapshots (start sequence never leaks); `record` value types with package-private `MatchId`
construction; and extensibility hooks. Also amended §8 and §14.5 for the ratified null handling.

All three phases complete; `SPECIFICATION.md` is ready for interface extraction, acceptance tests,
and implementation.

---

## Prompt 9 — User

> I want you to add two new MD files — `API.md`, where you describe all interfaces/public methods
> exposed by our library, and `ACCEPTANCE.TESTS.md`, where you suggest all acceptance tests
> covering the specification we discussed.

### Assistant — Response 9

Created **`API.md`** — full public surface: the `Scoreboard` interface (factory methods + every
operation with signatures, parameters, returns, and thrown exceptions), `ScoreboardConfig` +
builder, `MatchId` (capability-handle contract), `MatchSummary` and `FinishedMatch` records with
their accessors, the exception hierarchy, and an end-to-end usage example.

Created **`ACCEPTANCE.TESTS.md`** — 43 black-box Given/When/Then acceptance tests (`AT-01..AT-43`)
grouped by area: starting, recording, correcting, finishing, summary ordering (incl. the canonical
worked example), single-match lookup, history (ordering/payload/bounding/clear), validation &
normalization (incl. the Turkish-locale case), error handling, immutability, configuration, and
concurrency (lost-update, finish-atomicity, unique-id, reader-safety). Each test is tagged to its
originating spec decision, with a coverage matrix mapping requirements → tests.

---

## Prompt 10 — User

> Add an agents file to this directory describing that each prompt needs to be attached to
> `PROMPT_HISTORY` (and response). Format should follow the current HISTORY format. Include
> this message in the history as well.

### Assistant — Response 10

Created **`AGENTS.md`** — a working agreement for AI agents in this repository. Its core rule
requires that every user prompt and the corresponding assistant response be appended to
`PROMPT_HISTORY.md` (including the prompt that introduced the rule), with user prompts preserved
verbatim except for language/formatting fixes and assistant responses recorded in full. It
documents the exact `PROMPT_HISTORY.md` format to follow: sequentially numbered exchanges,
`## Prompt N — User` headings with the prompt as a `>` blockquote, `### Assistant — Response N`
headings with the full response, and a `---` separator between exchanges. It also mandates writing
the entry in the same turn (never deferred) and keeping the spec documents in sync.

Appended this very exchange to `PROMPT_HISTORY.md` as **Prompt 10 / Response 10**, following that
format.

---

## Prompt 11 — User

> Ok, now commit the content of this directory to the newly created repo:
> https://github.com/maciejc4/scoreboard — commit title: "Add specification and API description
> files". You can fix formatting etc.

### Assistant — Response 11

Committed and pushed all directory contents to `https://github.com/maciejc4/scoreboard` on the
`main` branch. The directory was not yet a git repository, so I initialized it, set `main` as the
default branch, added the `origin` remote, staged all files (`SPECIFICATION.md`, `API.md`,
`ACCEPTANCE.TESTS.md`, `PROMPT_HISTORY.md`, `AGENTS.md`), committed as **`fe65ae6` — "Add
specification and API description files"**, and pushed to the previously empty remote. The existing
files were already well-formatted, so no formatting fixes were needed.

Flagged that, per the `AGENTS.md` rule, this commit exchange was not yet logged in
`PROMPT_HISTORY.md`, and offered to append it and push a follow-up commit.

---

## Prompt 12 — User

> yes

### Assistant — Response 12

Appended the commit exchange (**Prompt 11 / Response 11**) and this confirmation exchange (**Prompt
12 / Response 12**) to `PROMPT_HISTORY.md`, following the established format, then committed and
pushed the updated history to `origin/main` as a follow-up commit.

---
