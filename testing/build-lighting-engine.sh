#!/bin/sh
# Builds engine.wasm of the shared lighting engine (Rocket-Show/lighting-engine) at the revision Rocket Show plays
# with, and copies it to src/main/resources/lighting-engine, from where the build packs it into the jar (see
# docs/lighting-engine.md). The file is not in this repository: without it, Rocket Show plays with its own engine
# only, shadow mode can't be turned on and the tests needing it are skipped.
#
# Pass the path of a checkout of the engine, by default the one next to this repository; without one, the engine is
# cloned from GitHub (it is open source) into a temporary folder. A checkout is left alone: the revision is exported
# from it and built in a temporary folder. Needs Rust with the target wasm32-unknown-unknown.
set -e

revision=5f84480e22ff7088d12d24c86dc044e8eaf1abab

engine="${1:-$(dirname "$0")/../../lighting-engine}"

if [ ! -d "$engine/.git" ] && [ ! -f "$engine/.git" ]; then
  engine="${TMPDIR:-/tmp}/lighting-engine-clone"

  if [ ! -d "$engine/.git" ]; then
    git clone --quiet https://github.com/Rocket-Show/lighting-engine.git "$engine"
  fi
fi

engine=$(cd "$engine" && pwd)
mkdir -p "$(dirname "$0")/../src/main/resources/lighting-engine"
target=$(cd "$(dirname "$0")/../src/main/resources/lighting-engine" && pwd)
build="${TMPDIR:-/tmp}/lighting-engine-$revision"

if ! git -C "$engine" cat-file -e "$revision^{commit}" 2>/dev/null; then
  git -C "$engine" fetch origin
fi

if [ ! -f "$build/Cargo.toml" ]; then
  mkdir -p "$build"
  git -C "$engine" archive "$revision" | tar -x -C "$build"
fi

(cd "$build" && cargo build -p lighting-engine-wasm --target wasm32-unknown-unknown --release)
cp "$build/target/wasm32-unknown-unknown/release/engine.wasm" "$target/engine.wasm"
echo "$revision" > "$target/engine.revision"
echo "Built engine.wasm of the lighting engine at $revision into $target"
