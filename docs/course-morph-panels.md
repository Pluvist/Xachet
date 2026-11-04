# Course source-to-panel transitions

The course-status accessory opens into a lower floating glass rectangle. Schedule lessons and calendar days open into a centered rectangle. Source title/location/teacher move along curved paths; fixed-size details fade in without stretching. Both panel types retain Kyant G2 corners with a final 31dp radius (`NavigationBarSurfaceHeight / 2`). The page behind an expanded panel progressively dims, softens and sinks; foreground text stays sharp.

## Shared footer surface (2026-11-02)

The persistent course-status capsule and both expanded course windows now call the same `CourseGlassSurface`, which wraps the original Glasmio `GlassSurface` with Navigation style. Opening changes its bounds and continuous corners; it does not substitute a separate panel-glass renderer. The footer samples the page-only `edgeBackdrop`; the expanded window samples its derived depth scene, so through-glass content follows the visible background. Existing curved title motion, close/input interception, interruptible return and companion departure remain independent.

The old CourseThumbnailBackdrop capture and 1/8→1/16→1/32→1/64 prefilter chain have been removed. `CourseMaterialCache.kt` now contains only target geometry. There is no extra panel-specific blur pipeline. The component uses the original renderer's backdrop transform and reduced-resolution paths (half size for footer controls and down to quarter size for large rounded windows), with exported panel material retained for inner/close controls. The source graph stays acyclic: raw page → resting footer; raw page → depth scene → moving footer / expanded surface → inner controls. Footer chrome and the expanded panel never enter the depth source.

Regular material base frost is reduced from 5dp to 1dp. The shared footer multiplier remains 4.5, preserving its existing sampling fast path, so the resting footer uses a nominal 4.5dp blur. Expanded surfaces explicitly request **1dp actual frost**, compensating for the shared multiplier when passing the original component's parameter. The old approximately 32dp panel prefilter no longer exists. Direct component reuse samples the live scene during animation, so these parameter reductions alone are not a claim of a particular frame-rate gain.

Ordinary native material white/black tone coating is disabled through `materialFaceOpacity=0`, without reducing text, edge, shadow or refraction opacity. The resting footer coating uses black at alpha 0.10 in dark mode and white at alpha 0.10 in light mode. The expanded window fades from that resting coat to the restored dark gray `#202022` at alpha 0.75 in dark mode or a substantially darker light gray `#B8BAC0` at alpha 0.78 in light mode, reversing continuously during dismissal/interruption. The stable `frostOverlayColorProvider` reads progress in drawing/effect scopes rather than recomposing the panel each frame. The coating remains inside the exported material, so nested controls sample the same visible panel. Text and close controls retain the matching theme. Intentional action-button tints remain for foreground contrast. Shared chromatic strength is 1 and refraction multiplier is 2. Outside dimming is separate: black at up to 0.62 in dark mode and 0.50 in light mode.

The backdrop follows the existing reversible spring progress. It sinks by up to 8dp and uniformly scales to 98.5%, with a quarter-resolution GPU multi-sample filter crossfaded over the sharp scene (1.5dp background softness). Sharp and softened page layers are recorded once per source flight, with invalidation for viewport, theme, schedule and appearance changes. There is no CPU bitmap readback. The footer and foreground panel remain live outside this filter. The derived depth source keeps the glass sampling aligned with the visually moved background. Drawing transforms preserve layout/source bounds; return-stage page taps are inverse-mapped before retarget hit testing, while footer taps retain raw coordinates. At rest the extra depth pass is bypassed, and the fully softened endpoint avoids redundant sharp-layer overdraw.

## Input and interruption rules

- While a panel is opening or already open, a tap outside its moving rectangle only dismisses it. It never activates another source or an underlying page control.
- The gesture is classified at pointer-down and consumed through release. A single tap cannot become a replacement open merely because dismissal starts before its release. Background dragging is consumed and does not turn into a click.
- During return, a new source may still be opened without waiting for full closure. Same-source reopening reverses the existing flight with its current velocity. A different source accelerates the old return (0.90/1100 spring), then opens the latest requested source. Repeated replacement requests overwrite the pending intent.
- Footer chrome wins hit testing over page cards, and covered course cards are excluded. Blank taps during return cancel pending replacement.
- Status/day opening uses a 0.84/480 spring and closing uses 0.88/620. Individual lesson opening uses 0.90/480 and closing uses 0.92/620. Sampled zero-velocity curves keep overshoot below 1%; interruption retains current velocity. Inside-panel lesson selection updates the existing panel instead of nesting another panel.
- The close control uses an explicit live material backdrop and the default MeiloX/Glasmio regular sampled material, with a theme-aware icon color. It never falls back to a missing ambient backdrop.

## Current validation (2026-11-02)

Optimized preview and benchmark builds pass. All 105 app JVM tests pass; the glass module reports 58 tests, with 55 passing and 3 platform-specific skips. The draw-state observation tests cover dynamic coating invalidation, theme color/alpha endpoints, reverse fade and independent face opacity.

On the connected OnePlus, both expanded panel types were visually inspected in dark and light themes using an isolated synthetic timetable. A short status open/close and interrupted-reversal run kept the same process alive; Android gfxinfo reported 13 missed deadlines out of 479 rendered frames (2.71%, P95 31ms). This is a bounded observation, not a universal frame-rate guarantee or a real-account academic-service regression. Earlier samples use different material/motion candidates and are not treated as directly comparable performance gains. Raw captures and measurement files remain in ignored `native/artifacts`.

Fresh UI hierarchies confirm lesson opening, outside-only dismissal, status opening with next-lesson details, and back dismissal. That shared-surface candidate was installed without clearing app data; its APK SHA-256 is `15D0A1ECBC77C23E8B220548AFA02D0BF4829F76AF27A9F17834AD660CD2D141`. Later color adjustments restored the dark gray panel tint and darkened the light tint to `#B8BAC0`; the expanded opacity remains higher for readability.

## Historical validation (2026-10-31)

71 JVM tests pass, including geometry/curve/radius checks, pending-open exclusion, return reversal, latest-intent replacement, cancellation and opening-state outside taps that cannot create another open. Optimized preview compilation passes; Android vital lint is excluded from the local build.

The final thumbnail renderer and pointer-down interceptor have not yet completed a physical-device visual/latency run. The independent emulator could not start because this machine lacks the Android Emulator hardware-acceleration driver; no virtualization configuration was changed. Earlier frame counts belong to superseded renderers and are not used to claim latency or a <=5% missed-deadline rate for this candidate.

Preview upgrades preserve saved account data and do not replace the formal app. Raw screenshots, UI dumps and timing files stay in ignored `native/artifacts`.

Installed preview APK SHA-256: 4619B4C41862A9EBE54022B8E6BD7F37080539ECA0F2932A37048C1C875FF8CB

### Stripe/distortion repair

The reported device screenshot showed plaid/ghost copies across the status panel. The sparse quarter-resolution sampler was replaced by the progressive whole-window pyramid above. Glasmio's existing normalized sampler is exposed for reuse; other consumers retain their existing behavior.

The repaired preview was installed preserving app data. Four real-device smoke checks passed: status remains open, close button returns, outside tap over a course while opening only dismisses, and the same outside tap while open only dismisses. The inspected first screenshot had no plaid. The follow-up visual comparison on populated week 10 was interrupted by an incoming call and user navigation, so that comparison is incomplete; automation stopped without interacting with the call. No timing claim is based on these checks.

### MeiloX opacity restoration

MeiloX 537c05f0158c2a489a06b9152dad08e3e8e97242 uses Glasmio 864e958676e38c5416d573c6affeaf4b0279be98 with ordinary GlassAppearance transparency defaults. The app removes the 0.85/0.95 forced blur-opacity floors, the bottom white 0.14 coating, the modal black/white 0.08/0.12 coatings and the bottom group's static 0.86/0.92 alpha. The original regular face recipe and default sampled close-button material are restored. Page-edge gradients, modal dimming and visibility/text animation fades remain separate from glass material opacity.

The additional upstream material test task could not resolve its separate runtime classpath in offline mode. The normal app build/test tasks are verified separately; source parity of the restored renderer is checked directly against the pinned clean upstream checkout.

Opacity candidate verification: optimized preview build and 71 application tests passed; the restored NavigationMaterialRenderer source matches the clean pinned upstream checkout after newline normalization. Preview replacement installation succeeded. The resting bottom controls were visually inspected. The automated open/close smoke assertion did not complete successfully in this run (the expected open panel was absent at inspection); no fresh crash was recorded, and this is not counted as a passed panel check.

Opacity-restored preview SHA-256: E6510BC0DDD7276484331E203FA2D782024801F24B245D752BD3CD3870508859

Follow-up visual inspection: the light-theme resting footer and dark-theme open status panel/close control were captured and inspected after the update. The default sampled close control rendered as a normal gray glass circle with a white icon in dark appearance. No black-source artifact was observed in that capture. On the final inspection the panel was already closed, so no extra click was sent. These are visual observations, not a completed timing benchmark.

### Clearer material and stronger refraction

The latest visual tuning supersedes the exact-default opacity restoration: regular light white fill is reduced from 20% to 12%, with the light black/lift coefficient reduced from 0.40 to 0.30. Dark roll-off is reduced by 25%, consistently for both explicitly selected materials and automatic size classes. Refraction increases by 25% in the app, bottom-control and course-panel scopes. The GPU blur pyramid and all animation/input rules are retained. Existing library size-class test expectations reflect the new dark endpoints; the separate upstream test task still requires uncached runtime dependencies, so application checks are reported separately.

Clarity candidate: optimized preview build and 71 application tests passed. Installed APK SHA-256: `A8F214BF617AC9099627C9EFB315A2C8A8E43D4230087D29B3C4E4BECFE0CFAF`. The captured final phone frame showed the dark weekly schedule's resting footer; an open detail panel was not present in that capture, so no panel visual/performance pass is claimed from it.

### Subtle course-detail rebound

Individual course-card detail flights now use damping ratio 0.88 for both normal opening and closing, retaining stiffness 430/520 and interruption velocity. Sampled zero-velocity curves keep overshoot below 1% and settle within one second. The course-status/day panels and accelerated replacement returns keep their previous motion. Geometry, corner curvature, GPU thumbnail glass and outside-tap/retarget rules are unchanged.

### Simultaneous status and companion flights

The accepted status request drives both panel expansion and an independent companion spring on the same frame; neither waits for the other. Main navigation leaves to the left, the personal control and contextual search circle leave to the right, using the existing backdrop-aware navigation placement and fade guards. The departure/return spring uses damping 0.9 and stiffness 500. Closing immediately retargets it home; reopening during return reverses its current position/velocity. Detail pages with already-hidden navigation retain that route visibility, and the search owner/width reservation stays intact. Individual course detail requests do not trigger this departure.

The panel/source geometry, status capsule width and glass material remain unchanged. Pending cancellation cannot leave companions latched offscreen. Unit tests cover cancellation, return reversal and excluding non-status details. The final implementation intentionally has no pre-expansion wait, superseding the initially considered sequential flight.

Controlled device verification completed: status expansion and companion departure overlap in the recorded opening frames; rapid dismissal/reopening during return keeps the panel working; main navigation restores after closure. On the textbook page, the contextual search circle disappears for the status panel and returns afterward. The subsequent GPA-filter disclosure fix leaves this motion implementation unchanged. No frame-rate percentage is inferred from the screen recordings.
