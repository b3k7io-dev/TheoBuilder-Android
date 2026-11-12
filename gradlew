#!/usr/bin/env sh
# bootstrap helper, not the official Gradle wrapper; requires JDK 17+
set -eu
V=9.6.0
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
BASE="${GRADLE_USER_HOME:-${HOME:?HOME is not set}/.gradle}/theobuilder-bootstrap"
DIST="$BASE/gradle-$V"
if [ ! -x "$DIST/bin/gradle" ]; then
  for c in curl unzip; do command -v "$c" >/dev/null 2>&1 || { echo "Missing command: $c" >&2; exit 127; }; done
  mkdir -p "$BASE"; ZIP="$BASE/gradle-$V-bin.zip"
  curl --fail --location --silent --show-error "https://services.gradle.org/distributions/gradle-$V-bin.zip" -o "$ZIP"
  curl --fail --location --silent --show-error "https://services.gradle.org/distributions/gradle-$V-bin.zip.sha256" -o "$ZIP.sha256"
  WANT=$(tr -d '[:space:]' < "$ZIP.sha256")
  if command -v sha256sum >/dev/null 2>&1; then GOT=$(sha256sum "$ZIP" | cut -d ' ' -f1); elif command -v shasum >/dev/null 2>&1; then GOT=$(shasum -a 256 "$ZIP" | cut -d ' ' -f1); else echo "sha256sum or shasum required" >&2; exit 127; fi
  [ "$WANT" = "$GOT" ] || { echo "Gradle checksum mismatch" >&2; rm -f "$ZIP"; exit 1; }
  unzip -q "$ZIP" -d "$BASE"; rm -f "$ZIP" "$ZIP.sha256"
fi
exec "$DIST/bin/gradle" "$@"
