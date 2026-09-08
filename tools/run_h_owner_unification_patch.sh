#!/usr/bin/env bash
set -euo pipefail
ci_file=".github/workflows/h-cloud-runtime-ci.yml"
backup="$(mktemp)"
cp "$ci_file" "$backup"
git show origin/main:"$ci_file" > "$ci_file"
python3 tools/h_owner_unification_patch.py
cp "$backup" "$ci_file"
rm -f "$backup"
