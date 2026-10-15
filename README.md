# AnimNav

[日本語](README_JA.md)

Draw **animated images** (GIF, WebP, PNG sequences) over the buttons of your
3-button navigation bar on Android. Made for a Galaxy Z Fold7 with Good Lock's
NavStar; it should work on other devices that keep the navigation bar visible,
but expect to adjust the positions.

> This is a personal hobby project. It is not affiliated with Samsung or
> Google, and it ships **no artwork**: you bring your own images.

## How it works

Navigation-bar theming apps such as NavStar can swap the button icons for your
own pictures, but they show them as still images. AnimNav keeps the real
buttons (so taps still work) and draws your animation on top of them:

- It adds one touch-through window of type `TYPE_ACCESSIBILITY_OVERLAY` along
  the navigation bar. This kind of window sits above the navigation bar and the
  keyboard. All touches pass through to the real buttons.
- You set the real button icons to transparent images, so only your animation
  is visible.
- It follows the bar: it fades and sinks together with the bar when an app goes
  fullscreen, shows only a back image on the lock screen, replaces the back
  image with a "close keyboard" image while the keyboard is open, and moves to
  the side when the bar is vertical (a phone in landscape).
- When you swipe the bar in on a fullscreen app, Android tells apps about it a
  moment late (about 0.3 s). AnimNav slides the images in from the side after
  the bar, so the delay looks natural. You can choose how they slide in.

## Setup

1. Install the APK (you need to allow installs from unknown sources).
2. Open the app → **Open app info** → ⋮ → **Allow restricted settings**.
3. **Open accessibility settings** → Installed apps → **AnimNav** → turn it on.
4. In your navigation bar theming app, set the button icons to transparent
   images.
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

## Privacy and permissions

AnimNav is an accessibility service because that is the only way to draw above
the navigation bar. It declares `canRetrieveWindowContent` so it can list the
windows on screen, but it only uses each window's **type, position and title**
to know whether the navigation bar is showing. It never reads text or other
content, has **no internet permission**, and collects nothing. Your images stay
on the device.

## Limitations

- Swiping the bar in on a fullscreen app shows the images about 0.3 s after the
  bar. This is Android reporting the window late; apps can't speed it up.
- Only tested on a Galaxy Z Fold7 (One UI, Android 16). The window title that marks a
  vertical navigation bar is matched in English and Japanese (and, untested,
  Korean) only.
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

## License

[MIT](LICENSE). The images you load are yours; check their licenses before you
share screenshots or recordings.
