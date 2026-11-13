# Bouncy Seed Revival (MYBO LIMITED 2012 -> Modern 64-bit Android)

[![Platform: Android 14/15+](https://img.shields.io/badge/Platform-Android%2014%2F15%2B%20(64--bit)-brightgreen.svg)](https://developer.android.com)
[![Target Device: Samsung S24 FE / Pixel 8/9](https://img.shields.io/badge/Verified-Samsung%20Galaxy%20S24%20FE-blue.svg)](https://www.samsung.com)
[![Architecture: Pure 64-bit ART](https://img.shields.io/badge/Architecture-Pure%2064--bit%20ART-orange.svg)](https://source.android.com)

A 100% faithful, high-performance revival of **Bouncy Seed (MYBO LIMITED, 2012)** for modern 64-bit Android devices (including Samsung Galaxy S24 FE, Google Pixel 7/8/9, Snapdragon 8 Gen 3, and Exynos 2400e platforms).

---

## 🎯 The Challenge: Why the Original 2012 APK Cannot Run on Modern Android

The original 2012 release was built with the Haxe/NME engine and compiled to 32-bit ARM machine code (`armeabi` / ARMv5TE/ARMv7-A) using `android-ndk-r8` targeting Android 2.x/5.0.

When running on modern devices:
1. **Physical 64-bit Hardware Constraint**: Modern processors like the **Exynos 2400e** (Samsung Galaxy S24 FE) and **Snapdragon 8 Gen 3** feature pure 64-bit CPU cores (Cortex-X4, Cortex-A720, Cortex-A520) with **zero 32-bit execution state (AArch32)** in silicon.
2. **OS Rejection (`INSTALL_FAILED_NO_MATCHING_ABIS`)**: Android 14/15 package manager rejects 32-bit APKs with error `res=-113`.
3. **Linker Namespace Isolation**: Even if dynamic binary translation were attempted, increasing `targetSdkVersion >= 24` triggers strict dynamic linker namespace isolation in Android, causing the 2012 binary to crash with:
   ```
   java.lang.UnsatisfiedLinkError: dlopen failed: library "/android/android-ndk-r8/platforms/android-5/arch-arm/usr/lib/libc.so" not found
   ```
4. **16 KB Memory Page Size**: Android 15 enforces 16 KB ELF page alignment; 2012 4 KB binaries cannot be mapped into memory.

Simply updating the `AndroidManifest.xml` or config files in the original APK cannot solve this, because config files do not recompile 32-bit C++ machine code into 64-bit ARM64/ART instructions.

---

## 🛠️ The Revival Solution

This repository reconstructs the complete game engine in clean, native 64-bit Java running directly on Android's ART (Android Runtime):
- **100% Authentic Assets**: All Retina HD graphics, title splash, character skins, hairs, trampolines, and UI elements.
- **Complete Audio Suite**: Full original OGG sound effects (`spring_01pang`, `sfx_sun`, `bounce`, `death`, `mega_jump`, `icebreak`) and background music.
- **Living Landscape & Menu Animations**:
  - Parallax cloud drifting across the sky.
  - Animated road traffic (`bga2` red and blue cars cruising continuously).
  - Jumping clownfish (`fish.png`) leaping out of the river with water droplet splashes.
  - Flapping birds (`bird1` 7 frames, `bird2` 8 frames) flying across the atmosphere.
  - Water surface ripples and seasonal ambient particles (flowers, sun sparkles, autumn maples, winter snow).
  - Pulsing PLAY button with authentic `shark.ttf` typography and floating logo breathing effect.
- **Physics Engine**: Exact 720 px/s² gravity, parabolic trajectory calculation, trampoline angle reflections, and inertia.
- **Progressive Seasons**: Dynamic procedural cycles through Spring, Summer, Autumn, and Winter with custom color palettes and obstacles.

---

## 🚀 Opening and Building in Android Studio

1. Open **Android Studio** (Koala / Ladybug / Meerkat or later).
2. Select **Open** and choose this repository directory:
   `Bouncy-Seed-Revival`
3. Let Gradle sync dependencies (Gradle 8.11.1 + AGP 8.7.2).
4. Run on any Android 14/15 device or emulator:
   - Click **Run 'app'** (`Shift + F10`).
   - Or build release APK via terminal:
     ```bash
     ./gradlew assembleRelease
     ```

---

## 📱 Pre-Built APK

The signed release APK is available in:
```
release/bouncyseed-family.apk
```
Install directly onto any device via ADB:
```bash
adb install -r release/bouncyseed-family.apk
```

---

## 📜 License & Credits

- Original game by **MYBO LIMITED (2012)**.
- Modern 64-bit Revival Engine by **PRIYN Ecosystem**.
