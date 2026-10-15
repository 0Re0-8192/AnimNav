# AnimNav

[日本語](README_JA.md) · [Changelog](CHANGELOG.md)

Draw **animated images** (GIF, WebP, PNG sequences) over the buttons of your
3-button navigation bar on Android.

> A personal hobby project, not affiliated with Samsung or Google. It ships
> **no artwork**: you bring your own images.

## Features

- A separate image for **Back**, **Home**, **Recents** and **Close keyboard**.
  GIF, WebP, a single PNG, or a ZIP of numbered PNG frames.
- Taps still go to the real buttons; AnimNav only draws on top.
- Follows the bar: fades and sinks with it in fullscreen apps, shows only Back
  on the lock screen, swaps Back for "Close keyboard" while the keyboard is
  open, and moves to the side when the bar is vertical.
- Swipe the bar in on a fullscreen app and the images slide in after it. You
  choose how.
- Material 3 settings with live previews, dark mode, English and Japanese.

Made and tested on a **Galaxy Z Fold7** (One UI, Android 16) with Good Lock's
NavStar. Other devices that show a 3-button bar should work, but you will need
to adjust the positions.

## Download

Get the latest APK from the [Releases](../../releases) page. Requires Android 11
or newer. The APK is signed with the release key; its certificate SHA-256 is

```
60:DD:D7:32:34:BD:EC:E0:BB:1D:76:7F:8D:88:09:CC:16:A8:03:B6:25:5E:A2:F7:7A:96:52:56:3E:57:59:37
```

## Setup

1. Install the APK (you need to allow installs from unknown sources).
2. Open the app → **Open app info** → ⋮ → **Allow restricted settings**.
3. **Open accessibility settings** → Installed apps → **AnimNav** → turn it on.
4. In your navigation bar theming app (for example NavStar), set the button
   icons to transparent images so the real icons don't show through.
5. In AnimNav, choose an image for each button.

## Images

| Format | Behaviour |
|---|---|
| GIF, WebP | Animated as the file says |
| PNG | A still image |
| ZIP of PNGs | Played in file-name order (numbers compare as numbers); frame length is a setting (150 ms by default) |

Images are copied into the app's private storage. Pixel art stays crisp when
"Crisp pixels" is on (it scales by whole numbers).

## Position

Positions are stored as fractions of the screen size, separately for small
screens, large screens (600 dp wide or more) and a side navigation bar. The
defaults were measured on a Galaxy Z Fold7 with NavStar:

| Screen | Middle button | Spacing |
|---|---|---|
| Small (folded) | 50.0 % | 27.9 % |
| Large (unfolded) | 71.8 % | 13.2 % |
| Small, bar on the side | 50.0 % (of the height) | 28.2 % |

On other devices, turn on **Show alignment guides** and use the sliders.

## How it works

Navigation-bar theming apps can swap the button icons for your pictures, but
only as still images. AnimNav keeps the real buttons and draws on top of them:

- One touch-through window of type `TYPE_ACCESSIBILITY_OVERLAY` along the bar.
  This kind of window sits above the navigation bar and the keyboard.
- You make the real icons transparent, so only the animation shows.
- It knows whether the bar is showing from the window insets and, for a bar
  swiped in on a fullscreen app (which Android hides from apps), from the list
  of windows on screen. Android updates that list about 0.3 s late, so the
  images slide in after the bar.

## Privacy and permissions

AnimNav is an accessibility service because that is the only way to draw above
the navigation bar. It declares `canRetrieveWindowContent` so it can list the
windows on screen, but it only uses each window's **type, position and title**
to know whether the navigation bar is showing. It never reads text or other
content, has **no internet permission**, and collects nothing. Your images stay
on the device.

## Limitations

- A bar swiped in on a fullscreen app gets its images about 0.3 s late. This is
  Android reporting the window late; apps can't speed it up.
- Only tested on a Galaxy Z Fold7. The window title that marks a vertical
  navigation bar is matched in English and Japanese (and, untested, Korean)
  only.
- **Swipe prediction** (experimental, off by default) puts thin invisible touch
  strips on the screen edges while an app is fullscreen to notice the swipe
  earlier. Touches that start inside a strip don't reach the app, so don't use
  it in rhythm games or similar.

## Build

You need the Android SDK (platform 36, build-tools 36) and JDK 17 or newer.

```
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

To sign a release build, create `keystore.properties` next to `build.gradle`
(it is git-ignored):

```
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then run `./gradlew assembleRelease`.

## Issues

Bug reports are welcome. Please include your device, Android version, and a
screenshot or screen recording with **Show alignment guides** turned on.

## License

[MIT](LICENSE). The images you load are yours; check their licenses before you
share screenshots or recordings.
