# Róża Edge guidance

## Subjects

Agents must not modify any submodule under `subjects/` unless the user
specifically and explicitly requests changes to that submodule. Its `AGENTS.md`
instructions apply only when the user has requested such changes; otherwise,
agents must not follow them.

## Skills

At the end of the final response, list the names of all skills invoked
automatically during the task.

The following skills must not be executed in this project:

- `make-library:verify-work`

## Project decisions

Record adopted project decisions in [docs/decisions.md](docs/decisions.md),
following its existing entry format and appending each new entry. Do not
delete, renumber, or edit existing decisions, including their wording, dates,
and references. Record corrections, changes, or additions in a new decision.
When it changes or adds to an earlier decision, link both entries using the
relationship that describes its effect:

| New decision    | Earlier decision   | Meaning                               |
| --------------- | ------------------ | ------------------------------------- |
| **Supersedes:** | **Superseded by:** | Replaces it entirely.                 |
| **Amends:**     | **Amended by:**    | Changes part; the rest remains valid. |
| **Extends:**    | **Extended by:**   | Adds requirements; keeps prior ones.  |

Place relationship links in a bulleted list immediately below each entry's
heading, with one relationship per item. Use the format
`- **Amends:** [000N](#decision-heading-anchor)` with the appropriate label.
The new entry must explain exactly what it replaces, changes, or adds. Only
these relationship links may be added to existing entries; keep their
original content unchanged, even when a referenced file or section moves.
Use ordinary links in prose for other connections rather than introducing
additional relationship types.

## Markdown documentation

Do not duplicate explanations or rules across Markdown files. Keep each in one
file and use links when another document needs to refer to it.

Wrap prose at the last space that keeps each line within 80 characters. Treat
inline code and Markdown links as indivisible units, moving them to the next line
when needed; allow a longer line only when a unit cannot fit within the limit.
Separate paragraphs with one blank line. When editing a paragraph, rewrap the
whole paragraph so earlier line breaks do not leave unnecessarily short lines.

Preserve the layout of headings, tables, fenced code blocks, and standalone
relationship links rather than applying prose wrapping to them.

## Code readability

Use explicit types for all Java variable declarations; do not use `var`.

When a variable declaration wraps immediately after `=`, separate the whole
declaration from adjacent declarations or statements with one blank line.

Do not put stream pipelines inside `if` or `while` conditions. Extract them
into named methods returning `boolean` or local `boolean` variables.

Use `String.formatted(...)` instead of string concatenation when inserting
values into text.
