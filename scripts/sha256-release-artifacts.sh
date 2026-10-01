#!/bin/sh
set -eu
if [ "$#" -eq 0 ]; then
    echo 'Usage: sha256-release-artifacts.sh FILE [FILE ...]' >&2
    exit 2
fi
for artifact in "$@"; do
    if [ ! -f "$artifact" ]; then
        echo "Not a regular artifact file: $artifact" >&2
        exit 2
    fi
done
sha256sum -- "$@"
