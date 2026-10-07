# Samsung Phone Engine (Rust Native Core)

This directory contains the high-performance Rust core engine for the Samsung Phone app.

## Modules

- **`t9`**: ITU-T E.161 standard T9 predictive search algorithm mapping dialed digits (`2`-`9`) to contact names and normalized phone numbers.
- **`dtmf`**: ITU-T Q.23 standard Dual-Tone Multi-Frequency mathematical sine wave synthesis for keypad audio tones with anti-click fade envelopes.
- **`call_manager`**: Telephony call state machine managing states (`Idle`, `Dialing`, `Ringing`, `Active`, `Ended`), active controls (Mute, Speaker, Hold, Record), and elapsed duration tracking.
- **`contacts`**: Fast phone number formatting and alphabetical indexing.
- **`jni`**: JNI bindings linking to `com.example.rust.RustPhoneBridge` in Kotlin.

## Android NDK Cross-Compilation

To cross-compile this Rust crate into Android `.so` libraries for deployment:

```bash
# 1. Install cargo-ndk
cargo install cargo-ndk

# 2. Add Android targets
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# 3. Build for target architecture
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release
```

The Kotlin layer (`RustPhoneBridge.kt`) provides automatic fallback to the high-performance Kotlin implementation if the native `.so` library is not bundled at runtime, ensuring 100% crash-free stability across all environments.
