# DESIGN.md

Visual and interaction rules for the app. Read before building any screen.

## Reference

The base is **Brilliant (iOS)** — see Mobbin: [lesson](https://mobbin.com/screens/2c5bd852-6876-49ae-a8d8-3e7a7f2c5ff9),
[home](https://mobbin.com/screens/8ad69ea6-81ed-4197-863d-ccba9f524a8f),
[explanation sheet](https://mobbin.com/screens/10dd3b68-b7fa-4cfd-b470-d6939539e8e7).
We take its structure and restraint, not its content or its reward loop.

**What we take**

- Light, near-white canvas. Heavy near-black headings. Very high contrast.
- One idea per screen, with real empty space left empty.
- One saturated accent colour per context, applied to eyebrow, CTA and progress together.
- A single full-width pill CTA, bottom-anchored, always in the same place.
- Content visual as the hero — Brilliant's diagrams become our criteria visuals.
- Floating white cards with generous radius over a soft grey backdrop.
- Modal explanation sheets with step-through arrows for "why does this rule exist?"

**What we deliberately drop**

- Streaks, lightning bolts, day calendars, "max streak", lessons-complete counters.
  Per product principle 4 we do not optimise for engagement. No screen shows a streak.
- Progress bars that imply the student is behind. Progress is shown as *position in
  time*, never as a score.
- Celebration animations on completion. A student marking an exam registration done gets
  an acknowledgement, not confetti.

## Principles

1. **Calm over urgent.** This app carries news that changes lives. It must never feel like
   a game or an alarm.
2. **Bad news is not an error.** An eligibility gate the student fails is the product
   working. Never style it with the error colour. See "Gate states".
3. **Every number carries its receipt.** No criterion renders without its source and
   verification date attached.
4. **One decision per screen.** If a screen asks two things, it is two screens.
5. **Legible at arm's length on a cheap phone in daylight.** Minimum body size 16sp,
   contrast ratio 4.5:1 minimum, 7:1 for anything load-bearing.

## Colour

Tokens only. No literal hex in components.

**Neutrals**

| Token | Value | Use |
|---|---|---|
| `canvas` | `#FFFFFF` | Page background |
| `surface` | `#F6F6F8` | Recessed backdrop behind floating cards |
| `card` | `#FFFFFF` | Card fill |
| `ink` | `#111114` | Headings, primary text |
| `ink-muted` | `#6B6B73` | Body secondary, labels |
| `ink-faint` | `#9A9AA3` | Metadata, source chips |
| `hairline` | `#E7E7EC` | Borders, dividers |
| `cta-dark` | `#232329` | Neutral primary button (Brilliant's "Continue") |

**Career accents** — every career belongs to one family. The accent colours the eyebrow
label, the primary CTA, the active timeline node, and nothing else.

| Family | Token | Value |
|---|---|---|
| Defence and uniformed services | `accent-defence` | `#2B5CE6` |
| Medicine and health | `accent-medical` | `#0E9F6E` |
| Engineering and technology | `accent-engineering` | `#6D4AFF` |
| Commerce, finance, CA | `accent-commerce` | `#E8830C` |
| Law and civil services | `accent-civic` | `#8B3A62` |
| Design and architecture | `accent-design` | `#E2574C` |
| Maritime and aviation | `accent-maritime` | `#0D7C8C` |

**Semantic** — never reused as decoration.

| Token | Value | Meaning |
|---|---|---|
| `state-met` | `#0E9F6E` | Criterion satisfied |
| `state-pending` | `#9A9AA3` | Not yet assessed / future |
| `state-attention` | `#C77700` | Deadline near, data going stale, action needed |
| `state-blocked` | `#4A4A57` | Criterion cannot be met — deliberately slate, not red |
| `state-error` | `#D32F2F` | **App failures only.** Network, crash, bad input. Never eligibility. |

`state-blocked` being slate rather than red is deliberate and not negotiable. A colour-blind
student learning they cannot fly commercially should not see their body rendered as a
system error.

## Typography

- Display / headings: **Plus Jakarta Sans** 700–800
- Body / UI: **Inter** 400–600
- Indic scripts: **Noto Sans Devanagari / Tamil / Telugu** — pick fallbacks now, before
  any localisation work, so line heights do not break later.
- Tabular numerals on every marks, score, age, height and date figure.

| Role | Size / line | Weight |
|---|---|---|
| Screen title | 28 / 34 | 800 |
| Section heading | 20 / 26 | 700 |
| Body | 16 / 26 | 400 |
| Body emphasis | 16 / 26 | 600 (inline, mid-sentence — Brilliant does this well) |
| Eyebrow label | 13 / 16, +6% tracking, uppercase | 700, career accent |
| Metadata / source | 12 / 16 | 500, `ink-faint` |

Body text is left-aligned. Screen titles may centre only on the career landing screen.
Never justify. Never centre a paragraph longer than one line.

## Space, radius, elevation

- 4pt base scale: `4, 8, 12, 16, 20, 24, 32, 48, 64`.
- Screen side gutter: `20`. Never less on a small phone.
- Radius: `pill` (999) for CTAs and chips, `20` for cards, `12` for inline diagram frames,
  `8` for inputs.
- Elevation: one soft shadow only — `0 4px 16px rgba(17,17,20,0.06)` — plus a `hairline`
  border. Never stack shadows. Never use more than two depth levels on a screen.

## Components

**Primary CTA** — full width minus gutters, 56 high, pill, bottom-anchored with 24 below.
Fill is the career accent, or `cta-dark` where no career is in context. One per screen.

**Card** — `card` fill on `surface` backdrop, radius 20, hairline border, 20 padding.

**Criterion row** — the workhorse. Status dot (semantic colour) · label · value in tabular
figures · source chip. Tapping opens the explanation sheet.

**Source chip** — mandatory on every criterion. `12/16` `ink-faint`, format:
`UPSC notification · verified Mar 2026`. If past the re-verification window, it turns
`state-attention` and reads `needs re-check`. Build this once, use it everywhere; a
criterion rendered without one is a bug, not a style issue.

**Gate states** — four, and only four:

| State | Dot | Treatment |
|---|---|---|
| Met | `state-met` | Normal weight, quiet |
| Not yet assessed | `state-pending` | Normal, with a "tell us" affordance |
| At risk | `state-attention` | Amber dot, short plain-language note |
| Cannot be met | `state-blocked` | Slate dot, strikethrough on the target value, and a **required** adjacent "here's what this still allows" link |

The last one never appears alone. A closed door is always rendered next to open ones.

**Timeline** — the pathway spine. Vertical, class/year markers as the rail, milestones as
nodes. Past is `ink-faint`, current node is the career accent and larger, future is
`hairline`. It scrolls; it does not animate on load.

**Deadline card** — date, days remaining in tabular figures, one action. Amber only inside
14 days. Never red, never a countdown that ticks.

## Motion

- Durations 150–250ms. Standard ease-out. Nothing bounces, nothing springs.
- Screen transitions: horizontal push. Sheets: slide up with a scrim at 40% `ink`.
- No looping animation anywhere. No confetti, no pulsing badges, no animated numbers.
- Respect `prefers-reduced-motion` — and treat it as a real requirement, not a nicety.

## Android and low-end device rules

Brilliant is an iOS app with rich 3D illustration. We are a cheap-Android app. Adapt:

- Illustration budget: **under 40KB per hero**, WebP or vector. Prefer flat geometric
  visuals over Brilliant's rendered 3D — they compress better and read better on a small
  low-DPI screen.
- No blur effects, no large translucency, no shadow stacks. They are expensive on low-end
  GPUs and the first thing that makes a budget phone feel broken.
- Keep the custom look on Android rather than switching to Material defaults, but honour
  the system back gesture, and honour the user's font-scale setting up to 200% without
  clipping — many users run large text.
- Every screen must be usable at 360dp width. Test there, not on a Pixel Pro.

## Anti-patterns

Do not ship: streaks or day calendars · progress percentages on a pathway · confetti ·
red styling on any eligibility outcome · a criterion without a source chip · a college
surfaced anywhere the student did not ask · a badge or count on the tab bar · notification
copy asserting something about the student we were never told.
