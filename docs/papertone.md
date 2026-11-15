# PaperTone / 纸风

Enable **Settings → Appearance → PaperTone** (设置 → 外观 → PaperTone · 纸风).
It is also a choice in the color-theme picker. Turning the switch off selects
system colors; choosing another palette exits PaperTone. Light/dark mode and the
selected font family remain independent preferences.

The reference is the locally supplied Timo 3.11.0 APK, observed on the Android 35
emulator: white canvas, ink-colored controls, fine gray card borders, nearly square
corners, left-aligned tabs with a short underline, metadata below note content,
and a writing dock. Its dark palette uses a charcoal canvas and graphite cards.
PaperTone keeps MemosM navigation and opens the existing editor from the dock.

## Luori bottom navigation

Only PaperTone uses the PaperWorld / 落日 bottom-bar presentation: a 64dp row
(growing with system font scale), 40dp animated selection circles, a fine divider,
and system navigation-bar insets. Icons and colors come from MemosM / PaperTone.

Hold the bar for 180ms or drag horizontally to collapse its buttons into a preview
rail. Movement starts from the currently selected page regardless of where the
finger lands, with 160dp thumb reach and Luori's default 1.35 sensitivity. Crossing
a page gives haptic feedback; the page previews while held, release selects it,
and cancellation or a second pointer restores the original page. Edge overshoot
uses the same elastic resistance. A short tap and existing double-tap refresh
continue to work. The gesture is confined to the bottom bar.

`ui/theme/PaperToneNavigationBar.kt` and `PaperToneScrub.kt` adapt PaperWorld's
`libraries/android-shared/.../components/AppNavigationBar.kt`, `AppBottomBarItem.kt`
and `ScrubSelection.kt`. MemosM owns the selected/preview destinations and saved
page state; the port adds no dependency on the PaperWorld checkout. Other themes
retain Material navigation and its existing dimensions/interactions.

## Editor presentation

PaperTone also adapts the existing editor: a compact header with a Unicode
character count, a continuous paper-colored writing area with 24dp margins and
26sp line spacing, a flat divider above the attachment/recording/location and
visibility controls, and a flat send/update/comment action. Suggestion popovers
use small corners and fine borders. Attachment previews and location chips share
the same paper geometry. Busy upload/submission states disable the toolbar and
show progress in the send control. The keyboard remains managed by Android;
existing IME insets and immersive-editing behavior are retained.

`PaperToneEditor.kt` owns the header and submit presentation. The existing
composer, input, picker, draft session and outbox remain the owners of content
and writes. Normal themes keep their original header, tools card and publish FAB.

## Other surfaces and interactions

PaperTone surfaces also cover memo details and their bottom comment action, action
and attachment-info sheets, account switching, profile cards and activity calendar,
settings groups, search date/tag/sort controls, drafts, notifications and archive
headers, sync/reaction panels, pending-sync cards, error/retry presentation and map controls. Fine borders keep white cards and
calendar cells visible; short weekday names keep localized day labels distinct.
Avatar shapes, media viewing gestures, reaction selection, account swipe thresholds
and destructive confirmations retain their existing behavior.

`PaperToneSurfaces.kt` centralizes conditional divider, control-shape, elevation,
sheet-handle and page-header helpers. Other themes keep their original values.
The attachment timeline has a PaperTone title above its existing pinchable grid;
pagination, viewing and pinch handling stay with the existing owner.

## Following upstream

- `ui/theme/PaperTone.kt` owns the fixed palettes, typography, shapes and small
  presentation helpers. No assets, fonts or dependencies are added.
- `ColorTheme.PAPERTONE` uses the existing appearance preference, persistence and
  settings-backup path. No DataStore keys, Room schema or migration are added.
- `MemosMTheme` provides `LocalPaperTone` for all saved-theme activities and live
  previews. `ProfileTheme` yields to it so expressive settings cannot override it.
- The existing search bar, feed tabs, memo card, navigation and compose action
  have small opt-in hooks. The memo metadata composable is reused above or below
  the content; the composer and data flows are shared with the normal appearance.
- The writing dock retains the existing draft prompt and editor launcher. User
  writes still use the durable outbox and existing attachment queue.
- `strings_papertone.xml` supplies the labels and character-count plural in all
  seven supported locales.

When rebasing, keep the upstream business flow and reapply only these presentation
hooks. New standard Material components inherit PaperTone automatically; add a
local hook only for a hardcoded component style that conflicts with it.

## Focused validation

Build with `./gradlew :app:assembleDebug`. On an emulator with a local mock account,
check enabling/disabling and process restart, both light/dark palettes, search and
feed switching, opening/publishing from the writing dock, and settings previews.
Scratch screenshots, logs and APKs belong under `tmp/`.
