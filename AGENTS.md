# AGENTS.md — Working Agreement for AI Agents

This file defines the rules any AI agent (Claude Code or otherwise) must follow when
working in this repository.

## Rule 1 — Log every prompt and response to `PROMPT_HISTORY.md`

Every single user prompt **and** the corresponding assistant response MUST be appended to
[`PROMPT_HISTORY.md`](./PROMPT_HISTORY.md). This includes the prompt that introduced this
rule. No exchange may be omitted — the file must always contain the entire conversation.

### What to record

- **User prompts** — preserved verbatim. The only permitted edits are correcting language
  errors and improving formatting for readability. Never change the meaning or content.
- **Assistant responses** — recorded in full.

### Format (must match the existing `PROMPT_HISTORY.md`)

`PROMPT_HISTORY.md` follows a fixed structure. New entries MUST continue it exactly:

1. Each exchange is a numbered pair: a user prompt section followed by an assistant response
   section.
2. User prompt heading: `## Prompt N — User`, where `N` is the next sequential number.
3. The user's prompt is quoted as a Markdown blockquote (each line prefixed with `> `).
4. Assistant response heading: `### Assistant — Response N` (same `N`).
5. The assistant's full response follows as normal Markdown.
6. Each complete exchange is separated from the next by a horizontal rule (`---`).

### When to write

Append the entry as part of handling the prompt — do not defer it. The history must be kept
current after every turn, so the file is never behind the actual conversation.

## Rule 2 — Keep specification documents in sync

When a decision changes the design, update the relevant document (`SPECIFICATION.md`,
`API.md`, `ACCEPTANCE.TESTS.md`) in the same turn, and log the exchange per Rule 1.
