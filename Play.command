#!/bin/sh
set -eu
cd "$(dirname "$0")"
exec sh tools/gradle.sh runClient
