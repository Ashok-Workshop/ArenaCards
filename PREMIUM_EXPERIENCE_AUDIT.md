# ArenaCards Premium Experience Audit

**Audit date:** 2026-11-14  
**Scope:** Static inspection of the current Android source, Gradle module configuration, Compose screens, design-system components, game engine, and existing project status documents.  
**Status:** The original audit was static-only. A later Batch 6 visual-refinement implementation is documented below; game rules, architecture, Firebase services, and production data were not changed.  

## Batch 6 implementation follow-up

The static audit above identified the main gaps. A subsequent visual-refinement pass has now added shared design tokens, elevated panel framing, improved card contrast and selection treatment, original Canvas avatar emblems, a lightweight table backdrop, event-keyed card reveals, and Compose component tests. The initial API 37 Espresso attempt failed during setup before assertions because that image lacks `InputManager.getInstance`; the later API 35 run passed all four component tests. This is not a claim that every screen is visually certified: the broader inventory below, TalkBack, font scaling, low-end performance, audio, and physical-device QA remain outstanding.

### Batch 6.1 implementation and screen inventory

The current in-progress pass reworks the guest/player home dashboard, makes offline/online selection route to real existing flows, and improves the local and online table interactions. A new API 35 emulator was used to inspect onboarding, home, offline setup, practice gameplay, pause, and exit confirmation. Four Compose instrumentation tests passed there, including drag-to-discard. The screenshots are retained under the Copilot session files; they are development evidence, not release certification.

| Screen / flow | Current state | Verification and remaining gap |
|---|---|---|
| Splash and onboarding | Original Compose card/brand art; onboarding copy and dark theme | Onboarding visually inspected. Full screen-reader, animation-scale, and copy review remains. |
| Home | Personalized dashboard with existing profile/wallet/progression state, offline/online entry points, feature routes, and notifications | Guest home visually inspected on API 35. Authenticated data states and narrow/large-font variants remain unverified. |
| Game selection | Real local player-count setup and route into the multiplayer lobby; no CPU difficulty picker | Offline setup visually inspected. Multiplayer selection still needs signed-in flow QA. |
| Offline practice | Shared table treatment, direct tap and drag-to-discard, draw-pile interaction, pause/rules/restart/exit, results and local history; ten-second UNO call window and two-card missed-call penalty | Table, pause, and exit confirmation visually inspected; drag test passed. Wild Draw Four and UNO engine behavior have unit coverage. UNO gameplay UI has not yet been visually inspected. |
| Online multiplayer | Server-confirmed actions, own-hand legality, timeout/status state, room/match confirmations, and existing lobby/history | Source/build/unit coverage exists; no authenticated end-to-end or visual emulator QA was performed in this pass. |
| Authentication, profile, wallet, settings | Existing functional screens inherit the shared theme; profile and wallet data remain source/backend dependent | No new visual inspection or complete large-font/accessibility review in this pass. |
| Friends, chat, notifications, leaderboard/tournaments | Existing feature modules and navigation remain present | No screen-by-screen visual QA in this pass; empty/loading/error states and authenticated navigation require follow-up. |

This inventory makes the boundary explicit: Batch 6.1 is not a full app-wide redesign or acceptance of every prompt requirement. Complete visual verification, accessibility review, and CPU/game-rule work remain open. UNO rule implementation is present in local and server-authoritative game engines; it has not been deployed or production-verified. No Firebase deployment, production data, or billing change occurred.

## Executive summary

ArenaCards has a credible native Android foundation: Kotlin, Jetpack Compose, Material 3, separate feature modules, a reusable design system, Firebase authentication/profile source, and a deterministic-testable offline game engine. The current playable experience is primarily a functional Compose prototype rather than a finished premium game presentation.

The strongest existing product area is the offline rules engine. Its current screen provides a scrollable table, player counts, hand controls, pause/resume, CPU difficulty selection, and Wild Draw Four challenge dialogs. The presentation has a consistent navy/purple/blue/gold direction, but card faces are geometric gradients with text, avatars are initials or emoji, and the gameplay is not animated beyond selection lift. There is no game audio or haptic-feedback system.

The most important near-term experience gaps are:

1. Improve card-face readability and disabled/selected/playable affordances, especially white text on the yellow card color.
2. Provide real accessibility, audio, haptic, and reduced-motion preferences rather than placeholder settings.
3. Add purposeful game feedback: deal, play, draw, turn/penalty, and round-complete transitions.
4. Make Easy, Medium, and Hard CPU behavior meaningfully distinct and testable.
5. Replace placeholder-only card/avatar art with an original, reusable ArenaCards art direction.

This is a code-based assessment, not a visual-device certification. No emulator screenshots, physical-device behavior, frame-time measurements, screen-reader session, or audio-session tests were performed. Those remain final QA activities.

## 1. Existing architecture

### What exists

- The project is a multi-module Kotlin/Compose Android application. Current modules include `app`, `core:designsystem`, `core:navigation`, `data`, `domain`, and features for onboarding, home, auth, profile, game, wallet, offline, multiplayer, friends, chat, leaderboard, settings, and notifications.
- `app` owns `MainActivity`, the Compose `NavHost`, startup routing, and composition of the app-level repositories/ViewModels.
- Feature screens collect `StateFlow` UI state and call ViewModel actions. The game rules live in the pure Kotlin `GameEngine`; `GameViewModel` maps the engine session to UI state and persists the practice session in `SavedStateHandle`.
- Auth and profile define repository contracts in their feature modules; Firebase implementations live in `data`. ViewModel factories are manually supplied from the app composition root.
- The game engine accepts `kotlin.random.Random`, allowing seeded deterministic tests. Game session serialization includes the pending Wild Draw Four challenge.
- Navigation uses a central `ArenaCardsDestinations` object with Compose Navigation routes.

### Scalability observations

- **Good foundation:** Feature boundaries and a shared design-system module are already established. The game engine is separated from Compose, which is an important boundary to preserve.
- **Not yet a full clean-architecture split:** `domain`, `core:common`, `core:network`, `core:security`, and several future feature modules are currently empty. The `data` module depends on the auth/profile feature modules for their repository contracts, which is a dependency direction to reconsider as the app grows.
- **Manual dependency wiring is acceptable at current size:** `MainActivity` directly creates Firebase repositories and factories. Introducing Hilt/Koin now is not necessary solely for fashion; revisit DI when the number of repositories, feature entry points, or test configurations makes manual construction cumbersome.
- **Composition-root responsibility is growing:** startup/auth/profile gating, repository wiring, routing, and global dialogs are concentrated in `MainActivity`. Keep it as the app boundary, but avoid putting feature behavior and presentation logic there as modules grow.
- **State collection:** screens use `collectAsState()`. Lifecycle-aware collection should be considered for long-lived state observers, especially profile/Firebase streams.
- **Future online play is a separate architecture problem:** the local CPU loop and `GameEngine` are not a server-authoritative multiplayer implementation. Do not reuse a client-owned local session as the trusted source for competitive matches.

### Recommended architectural direction

Preserve the current app and module structure. Do not do a broad rewrite. As features are implemented, move shared business contracts/use cases into the existing `domain` boundary where justified, keep Firebase/network implementations in `data`, and let UI features depend on those contracts. Keep `GameEngine` pure and deterministic. Keep one-shot sound/haptic/animation events outside the rules engine, exposed by the UI state holder as effects. Add a DI framework only when the actual object graph warrants it.

## 2. Existing design system

### Existing strengths

- `Theme.kt` defines a recognizable ArenaCards palette: navy surfaces with purple/blue accents and gold highlights, with dark and light Material 3 schemes.
- Typography is consistently based on Android sans-serif with a deliberate heavier display/headline hierarchy.
- `ArenaPanel`, `ArenaPrimaryButton`, `ArenaSecondaryButton`, `PlayerAvatar`, `CoinBalance`, `ArenaInfoDialog`, loading indicators, and `ArenaCardTile` provide reusable primitives.
- Buttons have minimum heights around 50–52 dp; panels and dialogs use consistent rounded shapes.
- Several controls and cards define content descriptions and selected state semantics.
- Most content pages use scrolling and padding rather than fixed-height content, and home/game selection use width-aware layouts.

### Gaps and risks

- The card face is currently a colored gradient and text label, not finished card artwork. The Wild face is a gradient, not a custom illustrated identity.
- `ArenaCardTile` uses white foreground text for all card colors. On the light yellow card background, white text is likely too low-contrast for comfortable and accessible reading. Validate with contrast tooling and use a dark foreground treatment for yellow.
- Playable, disabled, selected, and recently played cards need a stronger, consistent visual language. A disabled card currently remains visually similar to an active card even though click handling is disabled.
- Avatars are initials or emoji rather than a coherent set of original player art. Profile selection does not yet read as a premium avatar-customization experience.
- Light/dark palettes exist, but no visual regression or contrast test evidence was found.
- Several surfaces are content-rich cards. The final hierarchy, elevation, density, and touch comfort have not been verified from rendered screens.
- Some onboarding copy advertises friend/private-room and coin/reward experiences that are not implemented. Home actions disclose several unavailable services, but copy across the flow should remain aligned with actual availability.
- Game selection text calls CPU seats “placeholders for this UI phase,” although local CPU gameplay exists; that wording is stale and understates the current feature.

## 3. Screen-by-screen quality assessment

| Screen / flow | Existing implementation | Assessment and enhancement opportunity |
|---|---|---|
| Splash | Branded text, three card tiles, gradient background, one scale animation. | Good lightweight identity start. It is not a custom logo/illustration yet. Respect reduced motion and avoid delaying startup longer than needed. |
| Onboarding | Three scrollable pages, card/initial/coin illustrations built from Compose shapes, skip/back/next/get-started controls, page semantics. | Clear structure and responsive scrolling. Artwork is placeholder-style rather than commissioned/original illustration. Two pages describe future social/reward features as though available; make promises match release scope. |
| Home | Profile summary, guest/auth actions, online/offline entry points, placeholder coin balance, friends/rewards/leaderboard/profile/settings/help cards. Uses a scrollable responsive layout. | Consistent visual language, but it is a feature hub with several nonfunctional destinations. Clearly separate available from preview/coming-soon items. The coin placeholder and profile status text should not imply balances/statistics are live. Review card density at narrow widths and large font scale. |
| Game selection | Player-count selection for 2–4, separate offline/online messaging, truthful online-unavailable dialog. | Good non-deceptive online handling. The offline description incorrectly calls CPU seats UI placeholders. Clarify CPU difficulty/settings before starting and keep disabled/unavailable states visually distinct. |
| Authentication | Welcome, login, registration, forgot-password, email verification, phone/OTP/linking, account-provider screens; progress/errors/messages and accessible field descriptions. | Broad functional coverage and explicit loading/error feedback. Long flows rely on scrolling; verify keyboard/IME behavior, focus order, error announcement, password visibility affordance, resend timing, and narrow-screen usability. Country selection is a long menu without search. |
| Profile gate | Loading/checking state and retry error state before routing to profile setup or home. | Necessary state separation exists. Verify slow/offline behavior and ensure loading does not appear indefinite; runtime Firebase status is not established by this source audit. |
| Profile creation/edit | Username availability, display name, avatar choices, visibility switch, save/loading/error feedback. | Solid field coverage and privacy default. Avatar options are emoji chips, not polished selectable artwork; selected avatar state could be clearer. Verify text scaling, username feedback announcement, and save failure/retry path. |
| Profile summary | Avatar, display name, username, Gamer ID copy, join date, level/XP, matches/wins/losses/draws. | Useful identity summary, but stats/progression depend on trusted backend data not deployed/verified. Copy action should give accessible success feedback. Connected providers/account privacy/deletion are not part of this screen. |
| Practice game | CPU seats and public card counts, active turn/status/color/direction, draw/discard table, hand score, card hand, play/draw/end-turn actions, CPU difficulty dialog, Wild color selection, challenge prompt, pause/resume, replay. | The core interaction flow is present and unit-tested. Presentation is static apart from selected-card lift; winner/draw state is a text panel rather than a polished result moment. Cards are in a horizontal `LazyRow`; the overall screen scrolls vertically. Check narrow widths, landscape, large fonts, many cards, dialogs, and touch behavior. |
| Global dialogs / unavailable actions | Shared information dialog for unavailable friends, rewards, leaderboard, settings, and support. | Explicitly reports some unavailable functionality, which is preferable to fake behavior. The repeated preview-only destinations make the product feel unfinished; prioritize either implementing useful destinations or reducing their prominence until ready. |

## 4. Animation assessment

### Existing

- Splash uses `animateFloatAsState` for a simple scale-in.
- Onboarding uses `AnimatedContent` between pages.
- Selected cards use `animateDpAsState` to lift.
- No deal, card-play, draw, penalty, turn-change, challenge-resolution, or victory animation was found.

### Recommended

- Use Compose animation primitives first; the current app does not need a third-party animation library to deliver the initial set.
- Animate deal-in from the draw pile with short staggered offsets, then animate played cards to the discard pile and drawn cards into the hand. Use stable item identity so list changes do not animate the wrong card.
- Add modest turn/active-seat transitions and visible penalty feedback; use a clear status/announcement as well as motion so the result is not conveyed by animation alone.
- Add a restrained round-complete celebration and replay transition. Keep effects short, optional, and non-blocking.
- Consider a small Compose `Canvas`/particle overlay for win effects only if profiling shows it is inexpensive; avoid continuous particle/background animation.
- Add a reduced-motion preference and honor system animation settings. All important feedback must remain understandable with motion disabled.

## 5. Graphics assessment

### Existing

- Cards are rendered natively as rounded gradient Compose shapes with numeric/action text.
- Onboarding and splash use these card tiles, initials/emoji avatar badges, and simple geometric gradients.
- The app resources inspected contain values XML but no drawable/image asset set or custom font files.

### Recommended

- Define an original card-face system: consistent corner indices, distinct special-card glyphs, center illustration/symbol, contrast-safe text, and a recognizable ArenaCards back. Do not copy commercial card-game artwork, names, logos, or trade dress.
- Start with scalable vector artwork (VectorDrawable or Compose drawing) for card symbols, suit/color markers, and avatar badges. Use raster textures only where they materially improve the art and are optimized for Android density/size.
- Create a cohesive original avatar set and limited profile frames before introducing complex animated character art.
- Use a small number of layered table/background treatments; preserve text/card contrast and avoid distracting live backgrounds.
- Establish an asset naming, licensing, density, and dark/light contrast review before adding external art.

## 6. Audio assessment

### Existing

- No audio playback abstraction, sound assets, audio settings, or haptic feedback implementation was found.
- Home settings currently explain that sound/accessibility/account settings are future functionality.

### Recommended

- Add a small app-level feedback service for short one-shot effects: card selection/play, draw, special-card action, challenge outcome, turn, win, and loss.
- Use Android `SoundPool` for short low-latency effects. If background music is later approved, use Media3 with lifecycle, audio focus, pause/resume, and interruption handling rather than using a short-effect player for music.
- Use Compose `LocalHapticFeedback` or a narrowly scoped Android haptics adapter for optional confirmation and major outcomes; do not vibrate for every repeated state update.
- Persist independent music, sound-effect, and haptic toggles (and optionally volume) in a settings repository/DataStore. Respect Android ringer/audio focus and provide a silent default until the player opts in.
- Emit feedback as one-shot UI effects, not from `GameEngine`, so rules remain pure, testable, and usable without sound.

## 7. CPU intelligence assessment

### Existing behavior

- **Easy:** randomly selects one of the legal playable cards.
- **Medium:** selects the maximum of a simple priority score.
- **Hard:** uses the same priority score and then breaks ties by the number of matching-color cards held.
- Medium/Hard prioritize action cards; action use receives extra urgency when the next player has two or fewer cards.
- Wild-card color selection chooses the color most represented in the CPU hand.
- Wild Draw Four challenge willingness is randomized by difficulty. CPU card selection excludes illegal Wild Draw Four plays.
- The decision API receives the CPU's hand, current/top card, active color, next player's public card count, difficulty, and seeded random source; it does not need to inspect opponent card contents.

### Assessment

The difficulty labels exist, but Medium and Hard are only weakly differentiated: they share the same score and Hard adds a single color-count tie-breaker. Easy is random among legal plays, not necessarily a simple strategy. Current rules safety and deterministic testability are good foundations; competitive strategic depth is not yet present.

### Recommended progression

1. Keep a single legal-move generator and add a pure, deterministic strategy scorer on top.
2. Easy: choose a legal move with simple color continuity and mild randomness.
3. Medium: include remaining hand size, current color retention, action-card value, and the next seat's public card count.
4. Hard: evaluate the full public turn order/direction and each visible opponent card count; preserve wild cards when appropriate; use probability only for unknown opponent cards and calibrate it without reading hidden hands.
5. Add scenario-based tests that assert legality, seed repeatability, strategic preference, and no hidden-information dependency. Avoid claiming stronger intelligence merely because the difficulty enum differs.

## 8. Performance assessment

### Positive indicators

- Compose UI and native text/shapes avoid unneeded bitmap loading in the current screens.
- The player hand uses `LazyRow`, which is appropriate for a potentially growing hand.
- The local deck is small, and game-engine transitions operate on a bounded number of cards.
- The game CPU loop is synchronous, but the current local game has only a few players and 108 cards, so the inspected workload is small.

### Risks / unknowns

- No macrobenchmark, frame-time, memory, or battery evidence was found. Static inspection cannot establish smoothness on low-end devices.
- Game state is collected at screen level; each state emission can recompose a relatively broad game layout. Profile before splitting state or adding optimization complexity.
- Screens use `collectAsState()` rather than lifecycle-aware collection. Consider lifecycle-aware collection for state backed by long-lived repositories.
- Several screens use `remember` for transient inputs/page selection; decide which user-entered state should survive process recreation and use saved-state handling where needed.
- Layout adaptations are mainly width thresholds and scrolling. There is no evidence of landscape, window-size-class, or large-font rendering validation.
- The release build currently has minification disabled. This is not a gameplay bottleneck, but release size/startup configuration should be reviewed in the later release phase.

### Recommended performance checks

- Add Compose UI/screenshot coverage for important states and small/large window configurations.
- Run Macrobenchmark/JankStats only after the actual flows and assets are present; measure startup, game entry, card play, and long-hand scrolling on a representative low-end device.
- Keep game animations bounded, avoid infinite shader/particle loops, and use appropriately sized vectors/raster assets.
- Prefer lifecycle-aware state collection and profile-driven recomposition changes; do not preemptively fragment the UI into many tiny composables.

## 9. Accessibility assessment

### Existing support

- Several card, avatar, player-count, form, loading, and profile controls have content descriptions or selected-state semantics.
- Material buttons/text fields provide baseline keyboard and TalkBack behavior.
- Buttons have minimum heights near the Android 48 dp touch-target guideline.
- Cards include labels for card value and color, rather than relying solely on color.
- Layouts generally scroll and use Material typography in `sp`.

### Gaps to validate or improve

- Yellow card faces use white text, a likely contrast failure. Validate every card and state combination with a contrast checker.
- Reduced-motion setting, sound toggles, font-scale QA, and comprehensive TalkBack/manual accessibility testing are absent.
- Game turn/status changes and challenge outcomes need screen-reader announcements; static labels alone do not guarantee that dynamic updates are announced.
- Verify semantic roles/actions for clickable card tiles and whether disabled cards are both announced and visually disabled.
- Ensure card color is never the only distinction: keep readable names/icons and add non-color markers for color-vision support.
- Check focus order when dialogs open/close, when a hand changes after a play, and when errors appear in auth/profile forms.
- Test large text and display scaling on Home, Game, Profile, OTP, and dialogs; scrolling alone does not guarantee no clipping or an unusable game table.

## 10. Recommended improvements

All items below are **proposals**, not existing functionality or changes made by this audit.

1. **Contrast and state system:** introduce card foregrounds by color, visible playable/disabled/selected states, and a consistent turn/penalty status treatment.
2. **Premium card and avatar art:** create an original card-back, card symbols, action glyphs, avatar set, and profile frames with documented asset provenance.
3. **Game feedback layer:** add optional, reduced-motion-aware deal/play/draw/penalty/turn/win animations and one-shot sound/haptic events.
4. **Settings that work:** replace settings placeholder with persistent sound, music, haptic, reduced-motion, and display/accessibility choices; preserve existing game behavior when controls are off.
5. **CPU strategy depth:** make difficulty levels measurably distinct through public-information-only strategies and deterministic tests.
6. **Player progression presentation:** once trusted backend progression exists, present match history, achievements, and meaningful stats without showing placeholder balances or unverified values.
7. **Truthful onboarding and lobby copy:** revise future-feature promises and stale “CPU placeholder” text when those screens are next touched; distinguish implemented, preview, and unavailable capabilities.
8. **Responsive and accessibility validation:** add UI/screenshot tests now, then perform owner phone/device QA in the planned final QA phase.
9. **Performance baseline:** instrument representative low-end gameplay before adding expensive art, effects, or continuous backgrounds.

## 11. Priority order

| Priority | Improvement | Why first | Complexity |
|---|---|---|---|
| P0 | Fix card contrast and define accessible card/action states | Readability and feedback affect every game turn; yellow/white contrast is a specific risk. | Small–Medium |
| P0 | Keep preview claims truthful and correct stale CPU-placeholder wording | Avoid promising unavailable online/social/reward features and avoid understating existing CPU play. | Small |
| P1 | Build a persistent settings model for sound, haptics, and reduced motion | Enables inclusive controls and establishes preference behavior before effects are added. | Medium |
| P1 | Add core gameplay animations and winner/penalty feedback with UI-state tests | Highest impact on perceived game quality while keeping rules unchanged. | Medium–Large |
| P1 | Strengthen Easy/Medium/Hard CPU differentiation and scenario tests | Makes the existing three-level feature meaningful without exposing hidden cards. | Medium |
| P2 | Add original card-face/card-back/avatar artwork and profile visual polish | Establishes distinctive, legally safe ArenaCards identity. | Large |
| P2 | Add optional short sound effects and restrained haptics | Completes feedback after settings and accessibility controls exist. | Medium |
| P2 | Add durable local practice statistics and replay/result presentation | Provides offline progression without granting competitive coins. | Medium |
| P3 | Add performance and screenshot baselines across screen sizes/font scales | Establishes objective quality gates before final device QA. | Medium–Large |

## 12. Recommended technical architecture

- **Keep:** Compose + Material 3, current multi-module boundaries, `GameEngine` purity, `GameViewModel` state mapping, `SavedStateHandle` persistence, Firebase repository separation, and `ArenaCardsDestinations`.
- **App composition:** keep Firebase and app-wide construction at the app boundary. Gradually move route-level feature wiring into feature navigation entry points only when route complexity increases.
- **Business/data dependency direction:** as `domain` is implemented, put reusable game/settings/stat/profile use cases and contracts there where useful; have `data` implement contracts. Avoid a large migration of existing auth/profile code without an explicit design/review.
- **Game UI state:** continue immutable `StateFlow` state for renderable information. Add a separate one-shot effect stream for play/draw/turn/challenge/win audio, haptics, and accessibility announcements; never trigger those effects from the rules engine.
- **Settings:** use a typed settings model/repository, persist local preferences with Android DataStore, and inject it into settings/game feedback consumers. Keep sound, music, haptics, and reduced-motion preferences independent.
- **Animation:** implement initial effects with Compose `AnimatedVisibility`, `AnimatedContent`, `Animatable`, and transitions. Use custom Canvas/particle rendering only for bounded, profiled effects. Keep every game action available without animation.
- **Graphics:** use original vector/Compose-drawn symbols and optimized raster art only where needed. Define semantic labels, contrast variants, and fallback content for all custom assets.
- **Audio/haptics:** isolate short-effect playback and audio focus/lifecycle handling behind a small feedback service; do not let screens own unmanaged player instances.
- **Testing:** retain pure engine tests; add ViewModel effect tests, Compose semantics/UI tests, screenshot tests for themes/font scales/screen sizes, and later macrobenchmarks. Use an emulator/device only for final hardware-specific verification, not as the only feedback mechanism.
- **DI:** continue manual factories until wiring complexity justifies Hilt/Koin. If introduced later, do so incrementally and preserve test seams.
- **Future online mode:** implement an explicit online repository and server-authoritative state model separately from local offline game sessions; do not couple animation/audio to network protocol or game rules.

## 13. Estimated implementation complexity

These are relative estimates for an experienced Android developer familiar with the current project; they exclude product-art production, backend deployment, and owner device-test time.

| Work item | Complexity | Notes |
|---|---|---|
| Card contrast/state treatment and copy corrections | Small–Medium | A few shared components and focused UI tests; does not change rules. |
| Persistent settings and reduced-motion behavior | Medium | Settings model/persistence, UI, preference propagation, and tests. |
| Core Compose game animations | Medium–Large | Stable card identity, transition states, event mapping, accessibility alternatives, and UI tests. |
| Sound-effect/haptic feedback service | Medium | Asset sourcing/creation, lifecycle/audio focus, preferences, interruption handling, and tests. |
| Original card/avatar graphics system | Large | Art direction, original assets, variant/contrast support, integration, and performance validation. |
| Meaningfully differentiated CPU strategy | Medium | Pure strategy scoring, difficulty tuning, deterministic scenario coverage, and hidden-information review. |
| Durable offline stats/result history | Medium | Local persistence, schema/versioning, result rules, privacy, and tests; no coin rewards. |
| Full UI/accessibility/performance QA baseline | Medium–Large | Compose UI/screenshot automation plus later real-device and low-end profiling. |

## Final: five highest-priority improvements for ArenaCards

1. **Make card text and action states unambiguously readable**, beginning with dark text on yellow cards and clear disabled/playable/selected visuals.
2. **Implement usable accessibility and settings foundations**: TalkBack announcements for turn/results, font-scale validation, reduced motion, sound and haptic toggles.
3. **Give every game action immediate premium feedback** with restrained deal/play/draw/penalty/turn/win animations, followed by optional sound and haptics.
4. **Make CPU levels genuinely different** using deterministic strategies that use only the CPU's hand and public table information.
5. **Create original ArenaCards card and avatar artwork**, then validate it across light/dark themes, screen sizes, and performance budgets.

**Audit boundary:** This document records source-inspection findings and proposals only. It does not certify rendered UI quality, accessibility conformance, performance, real-device behavior, Firebase integration, or production readiness.
