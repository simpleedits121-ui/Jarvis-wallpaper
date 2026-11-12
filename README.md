# JARVIS WALLPAPER — Android source project

Standalone native Android app (Kotlin), independent from the existing JARVIS companion app.

## Included
- Live wallpaper service with animated purple/cyan dot-matrix wave/ring field.
- Adaptive render cadence: approximately 25 FPS normal, 15 FPS battery saver, lower cadence under thermal pressure; stops drawing while wallpaper is not visible.
- Gallery video selection with persisted URI permission and looping video playback on the wallpaper surface.
- One-shot alarm scheduling with AlarmClock API, high-priority alarm notification/full-screen intent, and Android TextToSpeech greeting.
- No account, subscription, API key, or network requirement at runtime.

## Build
Open this folder in Android Studio (Hedgehog or newer), let Gradle sync, then Build > Build Bundle(s) / APK(s) > Build APK(s).
Or run `gradle assembleDebug` with Android SDK 35 and JDK 17.

## Free cloud build
Push this project to a GitHub repository and run Actions → Build JARVIS Wallpaper APK. Download the `jarvis-wallpaper-apk` artifact.

## Important Android limitations
- Android may require the user to grant exact-alarm access. The app directs the user to the system setting and then asks them to tap Set Alarm again.
- Full-screen alarm presentation is controlled by Android notification policy and device settings. Some OEM battery controls can delay alarms.
- Live wallpaper rendering is paused when the wallpaper is not visible. Android Battery Saver and thermal management can reduce refresh rate.
- Video wallpaper support depends on the selected video codec and device decoder. A codec failure falls back to the animated matrix.
- The alarm is a simple one-shot alarm, not a repeating alarm or a media-video alarm.
