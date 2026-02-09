#!/bin/bash
# sync_keystore.sh - Sync debug keystore between CI and local development
#
# Usage:
#   ./sync_keystore.sh generate  - Generate a new consistent debug keystore
#   ./sync_keystore.sh install   - Install CI keystore to local ~/.android/

set -e

KEYSTORE_FILE="app/debug.keystore"
LOCAL_KEYSTORE="$HOME/.android/debug.keystore"

function generate_keystore() {
    echo "Generating consistent debug keystore..."
    keytool -genkey -v \
        -keystore "$KEYSTORE_FILE" \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Android Debug,O=Android,C=US"
    
    echo ""
    echo "Keystore generated at: $KEYSTORE_FILE"
    echo ""
    echo "Base64 for GitHub Actions secret:"
    base64 "$KEYSTORE_FILE"
    echo ""
    echo "Add the above base64 as DEBUG_KEYSTORE secret in GitHub repository settings"
}

function install_keystore() {
    if [ ! -f "$KEYSTORE_FILE" ]; then
        echo "Error: $KEYSTORE_FILE not found. Run './sync_keystore.sh generate' first."
        exit 1
    fi
    
    mkdir -p "$HOME/.android"
    cp "$KEYSTORE_FILE" "$LOCAL_KEYSTORE"
    echo "Keystore installed to: $LOCAL_KEYSTORE"
    echo ""
    echo "Verify with: keytool -list -v -keystore ~/.android/debug.keystore -storepass android"
}

case "${1:-}" in
    generate)
        generate_keystore
        ;;
    install)
        install_keystore
        ;;
    *)
        echo "Usage: $0 {generate|install}"
        echo ""
        echo "Commands:"
        echo "  generate  - Create a new debug.keystore in app/"
        echo "  install   - Copy app/debug.keystore to ~/.android/debug.keystore"
        echo ""
        echo "To fix signature mismatch:"
        echo "  1. If you have local keystore you want to use in CI:"
        echo "     cp ~/.android/debug.keystore app/debug.keystore"
        echo "     base64 app/debug.keystore  # Add to GitHub Secret DEBUG_KEYSTORE"
        echo ""
        echo "  2. If you want to use CI keystore locally:"
        echo "     Download app-debug.apk from GitHub Actions"
        echo "     unzip -p app-debug.apk META-INF/CERT.RSA | keytool -printcert"
        echo "     # Then match that keystore locally"
        exit 1
        ;;
esac
