# HyperCeiler onboarding layout adaptation

## Native Compose rewrite, 2026-11-07

Reference revision: `293cbc08d65fafe0179a49a7b0d7338261572b87`.
Source: https://github.com/ReChronoRain/HyperCeiler/tree/293cbc08d65fafe0179a49a7b0d7338261572b87/library/provision

`native-reference/` retains the welcome, terms, permissions, completion and title
layouts, dimensions, GlowPainter defaults, RenderViewLayout and AnimHelper.
`native/app/src/main/res/raw/provision_glow.glsl` is the unmodified upstream shader.
All are Copyright (C) 2023-2026 HyperCeiler Contributions, AGPL-3.0, with the license
retained in this directory. Native adaptation by Pluvist.

`OnboardingPage.kt` now replaces the prior Cresto guide with a full-screen flowing
color welcome, 70dp circular forward button, centered blue step icons and 32sp
titles, rounded content groups and bottom primary buttons. Xachet retains its
agreement, account login (password/Cookie/web), permission and widget functions.
The welcome deliberately has no application emblem, retaining the user's choice.
Completion uses the same flowing background with an outlined check-circle.

`ProvisionMotion.kt` uses Android RuntimeShader with the original uniforms and
0.2-resolution offscreen rendering, matching upstream's render scaling. Shader
initialization runs off the main thread; frame updates only invalidate drawing
and pause when the step is hidden or the activity is not resumed. The round-button
reveal and cancellable step transitions are Compose implementations, not the
OEM-only `fan.transition` runtime, so identical system transition timing is not
claimed. Account and permission handling continue to use Xachet's repositories.

## Earlier Flutter adaptation

Source: https://github.com/ReChronoRain/HyperCeiler
Revision: 5e4686069dd7ab1f3697e256d5fc7d68fb73e317
Copyright (C) 2023-2026 HyperCeiler Contributions. AGPL-3.0.
Adapted by Pluvist on 2026-10-13, in lib/pages/intro_screen/intro_screen.dart.

The original XML sources and license are retained here. The Flutter adaptation
uses the centered 90dp mark, 32sp step title, 14sp description, 35dp inset,
70dp circular forward control and 44dp bottom gap. The flow is welcome/glow, account login, then completion.
HyperCeiler marks and text are replaced with Xachet branding. The original glow.glsl noise, color interpolation, stripes, opening circle and
GlowPainter uniform defaults are preserved in the Flutter fragment shader. The
Android runtime shader entry point is adapted to FlutterFragCoord; system-specific
MIUI/fan activity transitions are not included. The mark is redrawn in monochrome. Smaller screens and larger text scroll instead of overlapping.


2026-10-17 source-view expansion correction: retain StartupFragment.java and
ViewUtils.java from the same revision, specifically buildPickPageAnimationContext,
captureRoundedBitmap and makeScaleUpAnim(..., 102). Upstream captures the next
button bounds and rounded bitmap, then delegates the actual activity transition
to fan.transition.ActivityOptionsHelper from fan.miuix:transition:1.0.13.0.
That system library is not included in this Flutter app. source_expand_route.dart
now captures the visible icon, hides its original during the transition and grows
a rounded reveal from its original edges to cover every screen corner, keeping
the destination at full layout size. Its 500ms cubic easing and foreground/content
cross-fade are Flutter adaptation parameters, not verified library constants;
pixel-identical/native-system timing is not claimed. Home navigation uses each
icon's own bounds rather than the containing card or padded touch target.


2026-10-17 control-boundary revision (supersedes the icon-only adaptation above):
The captured source is now the entire visible card/button/row and its actual
rounded shape. The reveal interpolates within viewport bounds rather than
rendering an oversized circle. Source content fades at its natural size, and
readback is capped at 200,000 pixels. Standard navigation uses 400ms; completion
uses one 600ms route clock shared by home entrance effects. The welcome glow
pauses while leaving. Completion and automatic refresh start after the handoff;
no native fan.transition animation implementation is included.


2026-11-08: Glasmio guide controls and LiquidMenuMorphMotion replace the earlier Compose circular reveal. The light-mode source shader is retained byte-for-byte. The host prepends a dark-mode uniform and changes only the final colour blend in its compiled variant: both modes use the same upstream simplex-noise/time fields, while dark mode emits blue/purple/pink light over black. Completion is now plain black/white, with a user-supplied SVG foreground recoloured blue; no HyperCeiler system animation runtime is included.
