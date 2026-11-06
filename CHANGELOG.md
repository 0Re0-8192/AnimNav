# Changelog

## 0.19

- Fix the images showing over the always-on display (AOD) and on the lock
  screen right after waking the screen (for example with double-tap to wake).
  The overlay now watches the display state itself (not only the screen-off
  broadcast, which doesn't always arrive for AOD), clears everything as soon as
  the display leaves the "on" state, and doesn't draw while it is dozing.
- While the device is locked, a bar that only the window list reports is no
  longer treated as a swiped-in bar. The Back image on the password screen is
  unchanged.

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
