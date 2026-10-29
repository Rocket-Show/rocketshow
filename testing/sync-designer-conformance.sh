#!/bin/sh
# Copies the conformance cases of the designer (web/projects/designer/conformance of Rocket-Show/show-designer) to
# src/test/resources/designer-conformance, which DesignerConformanceTest plays. Run it after the designer recorded
# new or changed cases (npm run conformance:record there), with the path of a checkout of the designer, by default
# the one next to this repository. CI checks that the copy is the one of the designer.
set -e

designer=${1:-$(dirname "$0")/../../show-designer}
source="$designer/web/projects/designer/conformance"
target=$(dirname "$0")/../src/test/resources/designer-conformance

if [ ! -d "$source/cases" ]; then
  echo "No conformance cases in $source" >&2
  exit 1
fi

rm -rf "$target/cases" "$target/profiles"
mkdir -p "$target"
cp -R "$source/cases" "$source/profiles" "$target/"
echo "Copied the conformance cases of $designer"
