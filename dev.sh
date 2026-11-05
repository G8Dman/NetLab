#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ ! -f .tools/sputnik/de.keksuccino.rinku-neoforge-3.0.4-1.21.1-mod.jar ]]; then
    python3 tools/setup_sputnik_browser.py
fi
if [[ -x "$PWD/.tools/jdk-21/bin/java" ]]; then
    export JAVA_HOME="$PWD/.tools/jdk-21"
elif [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in /usr/lib/jvm/java-21-openjdk /usr/lib/jvm/java-21-openjdk-amd64; do
        if [[ -x "$candidate/bin/javac" ]]; then export JAVA_HOME="$candidate"; break; fi
    done
fi
if [[ -x "$PWD/.tools/gradle-9.2.1/bin/gradle" ]]; then
    exec "$PWD/.tools/gradle-9.2.1/bin/gradle" "$@"
fi
exec ./gradlew "$@"
