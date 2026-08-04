#!/bin/bash
# sync_keystore.sh - Install the repository's development debug keystore locally.
#
# This does not manage the production signing key. Stable and Beta CI releases use
# RELEASE_KEYSTORE_* secrets and must retain that certificate continuously.

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
        echo "Production Stable/Beta signing is configured only with RELEASE_KEYSTORE_* secrets."
        exit 1
        ;;
esac
