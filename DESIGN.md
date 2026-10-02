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
- One saturated accent colour per context — the career's field — carried by its hero picture,
  icon tiles, eyebrow, CTA and timeline together.
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

**Career accents** — every career belongs to one family, and the family's colour is the
colour of everything about that career: its hero card, its icon tiles, the eyebrow, the CTA,
the timeline. Inside the app shell, the bottom bar wears the colour of the student's goal.

| Family | Token | Value |
|---|---|---|
| Defence and uniformed services | `accent-defence` | `#2B5CE6` |
| Medicine and health | `accent-medical` | `#0E9F6E` |
| Engineering and technology | `accent-engineering` | `#6D4AFF` |
| Commerce, finance, CA | `accent-commerce` | `#E8830C` |
| Law and civil services | `accent-civic` | `#8B3A62` |
| Design and architecture | `accent-design` | `#E2574C` |
| Maritime and aviation | `accent-maritime` | `#0D7C8C` |

**Strong accent** — several accents fail 4.5:1 under white text (medicine's green is 3.3:1).
Anything that carries white text or a white icon — hero cards, filled tiles, the primary CTA —
uses the accent darkened towards `ink` just until white on it reaches 5.6:1, which leaves room
for the hero's faint shapes. `AccentContrastTest` checks every family; a new family colour
that fails it fails the build. Pale washes of the plain accent (12–14%) back icon tiles and
chips, with the icon or text in the strong shade.

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

## Pictures and icons

The app is meant to feel bright and alive without costing a byte of network or a frame on a
cheap phone. Every picture is drawn in code or is a vector line icon; there are no bitmaps.

- **Career icons** — one 24dp line icon per career (2dp stroke, round caps, never coloured in
  the file; tinted at the call site). A career this build has no icon for falls back to its
  family's icon, so new content never shows a blank. Icons are art, not facts, so they live in
  code (`ui/art/CareerArt.kt`).
- **Hero card** — a career's picture: a diagonal gradient of the strong accent, one soft
  circle, one ring and the family's motif scattered faintly (white at 8%, drawn as one layer
  so overlaps never stack), and the career's icon on a white disc. Placement is seeded by the
  career id, so each career has its own arrangement and keeps it. Used for the goal on Today,
  the head of Pathway, a career's own page, and the student's card on You.
- **Tiles** — the same backdrop at small size, for field filters in Explore and specialisation
  carousels on Today. A sideways-scrolling row excludes the system back gesture over
  its own height, so a swipe near the edge scrolls the tiles instead of leaving the screen.
- **Icon tiles** — a rounded square in a pale wash of the accent with the icon in the strong
  shade, leading every row and card header. Decoration only: a tile's colour never means a
  gate state.
- **Empty states** carry a picture too: every field as overlapping coloured discs.

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
Fill is the strong career accent, or `cta-dark` where no career is in context. One per screen.

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
`hairline`. It scrolls. Its steps rise in once on first visit, like every card, and never
again.

**Deadline card** — date, days remaining in tabular figures, one action. Amber only inside
14 days. Never red, never a countdown that ticks.

## App shell

After onboarding the student is *in* the app, not in a flow. Onboarding stays full-screen
with no bars; everything after it sits in this shell.

**Tabs** — four, in this order, and no more without a design change:

| Tab | Holds |
|---|---|
| Today | The dashboard: goal and position in time, the next step, gates to watch, go further |
| Pathway | The timeline for the saved goal |
| Explore | Fields → careers → specialisations. Choosing a goal happens here, and only here |
| You | What the student told us (each answer editable), goal, content last checked, about, start over |

**Top bar** — 64 high, `canvas`, title 20/26 700 left-aligned. A back arrow on pushed
screens only. The `hairline` under it appears only once content scrolls beneath; no shadow.
A pushed screen with a large title in its content shows the bar title only after that
large title has scrolled away.

**Bottom bar** — custom, not Material defaults. `canvas`, `hairline` top border, 24dp line
icons with 12sp labels. One pill, in a 14% wash of the goal's accent, slides to the selected
tab, whose icon and label take the strong shade; unselected is `ink-muted`. With no goal the
bar is neutral `ink`. A light haptic tick on change. **Never a badge, dot or count.** The
shell draws the bar once, above the tabs, so it stays still while tabs slide beneath it; on
a pushed screen it slides down out of the way. Tab roots reserve its height at their foot.

**Backdrop** — tab roots and pushed screens use `surface` behind white cards, so a screen
reads as a dashboard of cards rather than a page.

**Primary CTA** — only on pushed screens (career detail, edit an answer), where no bottom
bar competes for the bottom edge. A tab root has no bottom-anchored CTA; its cards carry
their own links.

**Back** — pops within the tab, then a non-Today tab returns to Today, then the app exits.
Back never opens a screen the student has not been to.

## Motion

- Durations 150–320ms. Ease-out curves. Nothing bounces, nothing springs, nothing overshoots.
- **Between tabs**: the screen slides the way the bar reads — a tab to the right comes in
  from the right — 300ms. **Within a tab**: a 220ms horizontal push, the bar sliding away
  beneath it. Sheets: slide up with a scrim at 40% `ink`.
- **First visit**: a screen's cards rise 20dp and fade in, each a beat (55ms) after the one
  before, five beats at most. Once per screen: coming back to a tab shows it settled. A
  hero's icon settles in the same way.
- **Touch**: a tappable card or tile presses in to 97% and back. The bar's pill slides.
- No looping animation anywhere. No confetti, no pulsing badges, no animated numbers, and
  nothing celebrates choosing a goal.
- Respect the system's animation setting — and treat it as a real requirement, not a nicety.
  Compose scales every animation above by it, so "Remove animations" turns them all off.

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
copy asserting something about the student we were never told · white text on a plain
accent (use the strong shade) · a bitmap where a drawn picture would do.
