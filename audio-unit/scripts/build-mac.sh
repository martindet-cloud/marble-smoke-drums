#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
[[ "$(uname -s)" == Darwin ]] || { echo "Serve macOS" >&2; exit 1; }
if command -v cmake >/dev/null; then
  cmake_bin="$(command -v cmake)"
elif [[ -x /Applications/CMake.app/Contents/bin/cmake ]]; then
  cmake_bin=/Applications/CMake.app/Contents/bin/cmake
elif [[ -x "$HOME/Applications/CMake.app/Contents/bin/cmake" ]]; then
  cmake_bin="$HOME/Applications/CMake.app/Contents/bin/cmake"
else
  echo "CMake non trovato nel Terminale. Sposta CMake.app in Applicazioni oppure aggiungi il suo bin al PATH." >&2
  exit 1
fi
xcrun --find clang >/dev/null
"$cmake_bin" -S . -B build -DCMAKE_BUILD_TYPE=Release '-DCMAKE_OSX_ARCHITECTURES=arm64;x86_64' -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0
"$cmake_bin" --build build --config Release --target MarbleSmokeDrums_AU --parallel 4
open build/MarbleSmokeDrums_artefacts/Release/AU/
