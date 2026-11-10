#!/bin/sh
# Resolves a JDK for Xcode's "Compile Kotlin Framework" phase, then builds the
# shared Apple framework. Intended to run with Xcode env vars (PLATFORM_NAME,
# ARCHS, CONFIGURATION, …). Do not hard-code a machine-specific JAVA_HOME here;
# override via JAVA_HOME or local.properties java.home when needed.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
cd "$REPO_ROOT"

read_java_home_property() {
    # local.properties is gitignored and already used for sdk.dir.
    prop_file="$REPO_ROOT/local.properties"
    if [ ! -f "$prop_file" ]; then
        return 1
    fi
    # Take the last java.home= entry; strip CR for Windows-edited files.
    value=$(sed -n 's/^[[:space:]]*java\.home[[:space:]]*=[[:space:]]*//p' "$prop_file" | tail -n 1 | tr -d '\r')
    if [ -z "$value" ]; then
        return 1
    fi
    # Properties may escape ':' and '\' (Gradle/Android style).
    value=$(printf '%s' "$value" | sed 's/\\:/:/g; s/\\\\/\\/g')
    printf '%s\n' "$value"
}

is_usable_java_home() {
    candidate=$1
    [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]
}

resolve_java_home() {
    if is_usable_java_home "${JAVA_HOME:-}"; then
        printf '%s\n' "$JAVA_HOME"
        return 0
    fi

    if prop_home=$(read_java_home_property); then
        if is_usable_java_home "$prop_home"; then
            printf '%s\n' "$prop_home"
            return 0
        fi
        echo "warning: local.properties java.home is set but not usable: $prop_home" >&2
    fi

    if [ -x /usr/libexec/java_home ]; then
        if home=$(/usr/libexec/java_home 2>/dev/null); then
            if is_usable_java_home "$home"; then
                printf '%s\n' "$home"
                return 0
            fi
        fi
    fi

    # Bundled JetBrains Runtime from Android Studio (common on KMP Macs
    # without a separately installed JDK registered with java_home).
    for candidate in \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "/Applications/Android Studio Preview.app/Contents/jbr/Contents/Home" \
        "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    do
        if is_usable_java_home "$candidate"; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    return 1
}

if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" = "YES" ]; then
    echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES"
    exit 0
fi

if ! JAVA_HOME_RESOLVED=$(resolve_java_home); then
    cat >&2 <<'EOF'
error: Unable to locate a Java Runtime for the Kotlin Multiplatform iOS framework build.

Xcode does not inherit shell profile JAVA_HOME. Provide a JDK 17+ in one of these ways:
  1) Add to local.properties (gitignored):
       java.home=/path/to/jdk
  2) Install a JDK that /usr/libexec/java_home can find
  3) Keep Android Studio installed (uses its bundled JBR as a fallback)

This project uses Gradle 9.5.x / Kotlin 2.3.x / AGP 9.x, which support Java 21.
EOF
    exit 1
fi

export JAVA_HOME="$JAVA_HOME_RESOLVED"
export PATH="$JAVA_HOME/bin:$PATH"

echo "Compile Kotlin Framework using JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version
exec ./gradlew :shared:embedAndSignAppleFrameworkForXcode
