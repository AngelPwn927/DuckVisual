#!/bin/sh
# Builds "Duck Launcher.exe" (Windows) and a Linux CLI build into dist/.
# Requires Go 1.24+. Run from the DuckLauncher folder.
set -e
cd "$(dirname "$0")"

# 1. the mod jar that gets embedded into the launcher
if [ -f ../DuckVisual/build/libs/duck-visual-1.0.0.jar ]; then
  cp ../DuckVisual/build/libs/duck-visual-1.0.0.jar embed/duck-visual.jar
elif [ -f ../DuckVisual/release/duck-visual-1.0.0.jar ]; then
  cp ../DuckVisual/release/duck-visual-1.0.0.jar embed/duck-visual.jar
fi

# 2. Windows resources (icon, manifest, version info)
go run github.com/tc-hib/go-winres@v0.3.3 make --in winres/winres.json --out rsrc

# 3. binaries
mkdir -p dist
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags "-H windowsgui -s -w" -o "dist/Duck Launcher.exe" .
CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o dist/ducklauncher-linux .

# 4. code signing (optional): removes the "unknown publisher" SmartScreen warning.
#    Needs a code signing certificate (.pfx) from a CA such as Certum, Sectigo, SSL.com,
#    or Azure Trusted Signing. A self-signed certificate does NOT help with SmartScreen.
#    DUCK_SIGN_PFX=cert.pfx DUCK_SIGN_PASS=... ./build.sh
if [ -n "$DUCK_SIGN_PFX" ]; then
  command -v osslsigncode >/dev/null || { echo "install osslsigncode to sign"; exit 1; }
  osslsigncode sign -pkcs12 "$DUCK_SIGN_PFX" -pass "$DUCK_SIGN_PASS" \
    -n "Duck Launcher" -i "https://t.me/duckvisual" -h sha256 \
    -ts http://timestamp.sectigo.com \
    -in "dist/Duck Launcher.exe" -out "dist/Duck Launcher.signed.exe"
  mv "dist/Duck Launcher.signed.exe" "dist/Duck Launcher.exe"
  echo "signed"
fi
echo "built: dist/Duck Launcher.exe"
