# List all available recipes (default when running `just` with no arguments)
default:
    @just --list

# Build & launch the app on a running Android emulator or iOS simulator.
# `demo` has it talk to an invented robot instead of a real one (debug builds only).
# Usage: just install android
#        just install ios demo
install platform demo="":
    #!/usr/bin/env bash
    set -euo pipefail
    export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
    case "{{ platform }}" in
      android)
        ./gradlew :androidApp:installDebug
        extras=""
        [ "{{ demo }}" = "demo" ] && extras="--ez demo true"
        # The debug build has its own application id; its classes keep the plain package.
        adb shell am start -n xyz.felixb.congatrol.debug/xyz.felixb.congatrol.MainActivity $extras >/dev/null
        ;;
      ios)
        udid="$(just _ios-build-install)"
        args=""
        [ "{{ demo }}" = "demo" ] && args="-demo true"
        xcrun simctl launch "$udid" xyz.felixb.congatrol $args
        ;;
      *)
        echo "usage: just install <android|ios> [demo]" >&2
        exit 1
        ;;
    esac

# Install the optimised release build on every connected Android device. This is what the app
# really feels like — a debug build scrolls noticeably worse — but it takes no `demo` flag. It is
# "Congatrol"; the debug build installs next to it as "Congatrol (debug)", each with its own data.
install-release:
    #!/usr/bin/env bash
    set -euo pipefail
    export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
    ./gradlew :androidApp:installRelease
    adb shell am start -n xyz.felixb.congatrol/.MainActivity >/dev/null

# Builds the iOS app for the simulator, boots one, installs the app and prints the device's UDID.
# Everything but that UDID goes to stderr, so callers can capture it.
_ios-build-install:
    #!/usr/bin/env bash
    set -euo pipefail
    cd iosApp
    dd="build/just-ios"
    # The shared module only targets iosSimulatorArm64; pin arm64 so xcodebuild doesn't try the
    # unsupported x86_64 simulator slice.
    xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
      -sdk iphonesimulator -derivedDataPath "$dd" \
      ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build >&2
    app=$(find "$dd/Build/Products" -maxdepth 2 -name "*.app" | head -1)
    # A fixed default device, so the app always lands on the same screen size rather than on
    # whichever simulator happened to be booted or listed first. Override with IOS_SIMULATOR.
    device="${IOS_SIMULATOR:-iPhone 17}"
    devices=$(xcrun simctl list devices available)
    udid=$(echo "$devices" | grep -F "    $device (" | grep -oE '[0-9A-Fa-f-]{36}' | tail -1 || true)
    if [ -z "$udid" ]; then
      echo "No '$device' simulator available, using the first iPhone instead." >&2
      udid=$(echo "$devices" | grep -i iphone | grep -oE '[0-9A-Fa-f-]{36}' | head -1)
    fi
    # Boot it and wait unless it's already up.
    if ! xcrun simctl list devices booted | grep -q "$udid"; then
      xcrun simctl boot "$udid" >&2
      xcrun simctl bootstatus "$udid" -b >&2
    fi
    xcrun simctl install "$udid" "$app" >&2
    echo "$udid"

# Open the app in a phone-sized desktop window with Compose Hot Reload: save a file and the running
# window swaps the change in, no rebuild-and-install. Talks to the invented robot, or to a real one
# with CONGATROL_ROBOT=<address>. The first run downloads a JetBrains Runtime, which Hot Reload needs.
hot:
    ./gradlew :shared:hotRunDesktop --auto

# Run ktlint (with the Compose rules) across all modules, plus Android lint on the app.
lint:
    ./gradlew ktlintCheck :androidApp:lint

# Auto-format all modules with ktlint
format:
    ./gradlew ktlintFormat

# Regenerate the open source licence reports the app shows under Robot › Open source licences:
# one JSON per shipped platform, listing exactly what that platform's binary links. Run it after
# adding, removing or bumping a dependency and commit the result — the reports are checked in, not
# built, so a stale one ships stale. See the aboutLibraries block in shared/build.gradle.kts.
#
# Two invocations because the plugin exports one variant per run: `android` is the Android
# compile + runtime classpath, `iosLinked` the alias configuration over the iOS klibs.
licenses:
    ./gradlew :shared:exportLibraryDefinitions -PaboutLibraries.exportVariant=android -PaboutLibraries.outputFile=src/commonMain/composeResources/files/licenses/android.json
    ./gradlew :shared:exportLibraryDefinitions -PaboutLibraries.exportVariant=iosLinked -PaboutLibraries.outputFile=src/commonMain/composeResources/files/licenses/ios.json

# Run the unit tests.
test:
    ./gradlew :shared:desktopTest

# Run the Maestro end-to-end flows against a running Android emulator or iOS simulator. They use
# the invented robot, never a real one. Optional patterns narrow the run to flow files or
# directories, relative to maestro/.
# Usage: just e2e android
#        just e2e ios map
e2e platform *patterns:
    #!/usr/bin/env bash
    set -euo pipefail
    export PATH="$PATH:$HOME/.maestro/bin:$HOME/Library/Android/sdk/platform-tools"
    targets=()
    for pattern in {{ patterns }}; do
      if [ -e "maestro/$pattern" ]; then targets+=("maestro/$pattern"); else targets+=("maestro/$pattern.yaml"); fi
    done
    [ "${#targets[@]}" -eq 0 ] && targets=(maestro)
    case "{{ platform }}" in
      android)
        # An emulator unless ANDROID_SERIAL says otherwise: the flows wipe the app's data and tap
        # through it, which is not something to do to a phone that happens to be plugged in.
        serial="${ANDROID_SERIAL:-$(adb devices | awk '/^emulator-.*device$/ { print $1; exit }')}"
        if [ -z "$serial" ]; then
          echo "No running emulator. Start one, or set ANDROID_SERIAL to the device to drive." >&2
          exit 1
        fi
        ANDROID_SERIAL="$serial" ./gradlew :androidApp:installDebug
        maestro --platform android --device "$serial" test -e APP_ID=xyz.felixb.congatrol.debug "${targets[@]}"
        ;;
      ios)
        udid="$(just _ios-build-install)"
        maestro --platform ios --device "$udid" test -e APP_ID=xyz.felixb.congatrol "${targets[@]}"
        ;;
      *)
        echo "usage: just e2e <android|ios> [patterns...]" >&2
        exit 1
        ;;
    esac
