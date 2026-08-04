#!/bin/bash
# sync_keystore.sh - Install the repository's development debug keystore locally.
#
# This is the signing baseline for main-branch Debug, Beta, and Stable APKs.
# Optional RELEASE_KEYSTORE_* CI secrets must contain this same certificate.

set -e

KEYSTORE_FILE="app/debug.keystore"
LOCAL_KEYSTORE="$HOME/.android/debug.keystore"

function install_keystore() {
    if [ ! -f "$KEYSTORE_FILE" ]; then
        echo "Error: $KEYSTORE_FILE is missing from the repository checkout."
        exit 1
    fi
    
    mkdir -p "$HOME/.android"
    cp "$KEYSTORE_FILE" "$LOCAL_KEYSTORE"
    echo "Keystore installed to: $LOCAL_KEYSTORE"
    echo ""
    echo "Verify with: keytool -list -v -keystore ~/.android/debug.keystore -storepass android"
}

case "${1:-}" in
    ""|install)
        install_keystore
        ;;
    *)
        echo "Usage: $0 [install]"
        echo ""
        echo "Copies app/debug.keystore to ~/.android/debug.keystore for local debug builds."
        echo ""
        echo "Stable/Beta signing uses this certificate; optional RELEASE_KEYSTORE_* secrets must match it."
        exit 1
        ;;
esac
