#!/bin/bash
set -e
echo "Building Rin for Android (arm64 only)..."

# Check if cargo is installed
if ! command -v cargo &> /dev/null; then
    echo "Error: Rust/Cargo not found. Please install Rust from https://rustup.rs/"
    exit 1
fi

# Check if cargo-ndk is installed
if ! command -v cargo-ndk &> /dev/null; then
    echo "cargo-ndk not found. Installing..."
    cargo install cargo-ndk
fi

# Check if Android NDK is configured
if [ -z "$ANDROID_NDK_HOME" ] && [ -z "$NDK_HOME" ]; then
    echo "Error: ANDROID_NDK_HOME or NDK_HOME environment variable not set"
    echo "Please set it to your NDK path, e.g.:"
    echo "export ANDROID_NDK_HOME=/path/to/ndk/28.2.13676358"
    exit 1
fi

NDK_PATH="${ANDROID_NDK_HOME:-$NDK_HOME}"
echo "Using NDK: $NDK_PATH"

# Check if adb device is connected
echo ""
echo "Checking for connected device..."
if ! adb devices | grep -q "device$"; then
    echo "Error: No Android device connected. Please connect a device with USB debugging enabled."
    exit 1
fi
echo "Device found!"

# Build for ARM64
echo ""
echo "Building for aarch64-linux-android..."
cargo ndk -t arm64-v8a -o android/app/src/main/jniLibs build --release --features android --lib
if [ $? -ne 0 ]; then
    echo "Build failed for aarch64-linux-android"
    exit 1
fi

# Build rpkg CLI for ARM64
echo ""
echo "Building rpkg CLI for aarch64-linux-android..."
cargo ndk -t arm64-v8a build --release --package rpkg --bin rpkg
if [ $? -ne 0 ]; then
    echo "Build failed for rpkg CLI"
    exit 1
fi

# Copy rpkg binary
mkdir -p android/app/src/main/jniLibs/arm64-v8a
cp target/aarch64-linux-android/release/rpkg android/app/src/main/jniLibs/arm64-v8a/librpkg_cli.so

echo ""
echo "========================================"
echo "Build complete! Installing to device..."
echo "========================================"

# Build APK + install langsung
cd android
./gradlew installDebug
if [ $? -ne 0 ]; then
    echo "Gradle installDebug failed"
    exit 1
fi

# Launch app
echo ""
echo " Starting Activity..."
adb shell am start -n com.rin/com.rin.MainActivity

echo ""
echo " Streaming logs (Ctrl+C to stop)..."
adb logcat -v color -s Rin RinNative AndroidRuntime System.err