#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
export GRADLE_USER_HOME="$PWD/.gradle-home"
exec ./gradlew "$@"
