# JARVIS WALLPAPER V4 — LOW-POWER RENDERER

Android live wallpaper with a fixed cyberpunk dot-matrix grid.

## V4 performance changes
- Static image mode samples once and draws once; no 30 FPS idle redraw loop.
- Dot spacing increased to 10 dp to reduce the number of drawing operations.
- Image/video sampling uses a single bulk pixel read instead of repeated `getPixel()` calls.
- Video source frames are downscaled to at most 360 px wide before sampling.
- Video mode updates the wallpaper only when a new sampled frame is ready.
- Video sampling is limited to approximately 4 frames/second as a conservative thermal-safety fallback.
- Removed per-dot blur and pulsing effects, which were expensive to redraw.

## System-wide volume dots
The optional `JARVIS Volume Dots` Accessibility Service observes hardware volume-key presses and briefly shows a dot-grid level indicator. It returns false for key events so Android retains normal volume behavior. It does not inspect screen content. Enable it manually in Android Accessibility settings and grant display-over-other-apps permission.

## Video alarm
Choose a video clip and set an alarm. At alarm time the full-screen alarm activity plays the selected clip with sound and provides a dismiss button. Android exact-alarm and notification/full-screen permissions may be required.

## Important
This is a conservative low-power renderer, not the editor-style MP4 pre-render/export pipeline discussed earlier. Video still uses frame extraction, deliberately capped at a low rate to reduce load. If the phone heats up abnormally, disable the wallpaper and volume overlay and let it cool before testing again.

## Build
Java 17 and Android SDK platform 35 are configured in the GitHub Actions workflow.
