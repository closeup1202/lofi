#!/bin/sh
set -e

REPO="closeup1202/lofi"
BINARY="lofi-otelcol"
INSTALL_DIR="${LOFI_INSTALL_DIR:-/usr/local/bin}"

# Detect OS
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
case "$OS" in
  darwin|linux) ;;
  *) echo "Unsupported OS: $OS" && exit 1 ;;
esac

# Detect architecture
ARCH=$(uname -m)
case "$ARCH" in
  x86_64)        ARCH="amd64" ;;
  aarch64|arm64) ARCH="arm64" ;;
  *) echo "Unsupported architecture: $ARCH" && exit 1 ;;
esac

# Resolve version
VERSION="${LOFI_VERSION:-}"
if [ -z "$VERSION" ]; then
  VERSION=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
    | grep '"tag_name"' \
    | sed 's/.*"v\([^"]*\)".*/\1/')
fi

FILENAME="${BINARY}-${OS}-${ARCH}"
URL="https://github.com/${REPO}/releases/download/v${VERSION}/${FILENAME}"
DEST="${INSTALL_DIR}/${BINARY}"

echo "Installing lofi-otelcol v${VERSION} (${OS}/${ARCH})..."
curl -fsSL "$URL" -o "$DEST"
chmod +x "$DEST"

echo ""
echo "✓ Installed: $DEST"
echo ""
echo "Next steps:"
echo "  1. Start lofi-backend:  ./gradlew :lofi-backend:bootRun"
echo "  2. Run the collector:   lofi-otelcol --config collector-config.yaml"
echo "  3. Point your app to:   http://localhost:4318 (HTTP) or localhost:4317 (gRPC)"
