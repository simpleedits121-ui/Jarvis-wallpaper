# JARVIS WALLPAPER

Android live wallpaper with a fixed-position cyberpunk dot-matrix renderer.

## Video dot-matrix mode
Choose a video in the app. The wallpaper decodes frames in a background thread (target cadence: 8 fps), samples each frame onto one fixed grid, and updates only dot colour/brightness. Dot centres, spacing, and radius stay constant throughout playback. The video loops automatically. Image-to-dot-matrix mode is also available.

## Build
Open in Android Studio or use the repository GitHub Actions workflow to build a debug APK. Android SDK platform 35 and Java 17 are configured by the project workflow.

## Notes
- Video decoding uses Android `MediaMetadataRetriever`; no third-party video library is required.
- Frame extraction is intentionally limited to about 8 fps to reduce CPU use. Actual cadence depends on the device and source video.
- Live wallpaper rendering slows down in battery saver / elevated thermal conditions.
