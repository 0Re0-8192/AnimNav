# Changelog

## 0.17 — first public release

Development before this version was private; this entry summarizes it.

- Draw GIF, WebP, single PNG or PNG-sequence (ZIP) images over each of the
  Back / Home / Recents buttons, with a separate "Close keyboard" image that
  replaces Back while the keyboard is open.
- Follow the navigation bar: fade and sink together with it when an app goes
  fullscreen, show only Back on the lock screen, move to the side when the bar
  is vertical.
- Handle a bar swiped in on a fullscreen app: the images slide in from the side
  after the bar (Android reports the window about 0.3 s late). Choose the
  entrance: right button first, left button first, or as a row.
- Alignment guides and sliders; positions are saved separately for small
  screens, large screens and a side bar.
- Experimental swipe prediction (off by default) using thin invisible touch
  strips on the screen edges.
- Material 3 settings screen with live image previews, dynamic color and dark
  mode; English and Japanese.
