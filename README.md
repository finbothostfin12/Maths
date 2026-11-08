# MathLab

Material 3 scientific calculator for Android (Kotlin + Jetpack Compose).

* Scientific calculator: full trig (deg/rad), inverse + hyperbolic, logs, roots, powers, factorial/gamma,
  nCr/nPr, gcd/lcm, mod, percentages, numeric derivative `deriv(f(x), x0)` and integral `integ(f(x), a, b)`,
  `ans`, fractions, step-by-step evaluation.
* Graphing: up to 6 functions, pan/pinch-zoom, trace, roots / extrema / intersections.
* History with the date and time of every calculation.
* Changeable palettes (Material You dynamic colour, 7 presets, free hue slider), light/dark/AMOLED.
* Scan & solve: camera/gallery photo -> drag a box -> on-device OCR (ML Kit) -> expression, equation or
  triangle solver with several solving methods and every step shown.

## Build
Push this folder to GitHub. The workflow `.github/workflows/build.yml` builds the APK on every push:
**Actions -> latest run -> Artifacts -> MathLab-release-apk** (or `-debug-apk`).
Tag a commit `v1.0` to also publish the APKs as a GitHub Release.

Local build: `gradle assembleDebug` (Gradle 8.10+, JDK 17), or `gradle wrapper` once to create `./gradlew`.
