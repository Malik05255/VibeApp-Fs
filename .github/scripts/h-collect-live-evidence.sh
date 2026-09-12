#!/usr/bin/env bash
set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

OUTPUT_DIR="${H_LIVE_EVIDENCE_DIR:-h-live-evidence}"
WORK_DIR="${H_LIVE_EVIDENCE_DOWNLOAD_DIR:-.ledger-downloads}"
mkdir -p "$OUTPUT_DIR" "$WORK_DIR"

gh api --paginate --slurp "/repos/${GITHUB_REPOSITORY}/actions/artifacts?per_page=100" > "$WORK_DIR/pages.json"

for target in backup standby-preflight failover-active whatsapp-voice; do
  prefix="h-live-evidence-${target}-"
  artifact_id="$(jq -r --arg prefix "$prefix" '
    [.[].artifacts[] | select(.expired == false and (.name | startswith($prefix)))]
    | sort_by(.created_at) | reverse | .[0].id // empty
  ' "$WORK_DIR/pages.json")"

  if [[ -z "$artifact_id" ]]; then
    echo "No non-expired artifact found for ${target}."
    continue
  fi

  zip_path="$WORK_DIR/${target}.zip"
  out_dir="$WORK_DIR/${target}"
  rm -rf "$out_dir"
  mkdir -p "$out_dir"
  curl --fail --silent --show-error --location \
    -H "Authorization: Bearer ${GH_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/artifacts/${artifact_id}/zip" \
    --output "$zip_path"
  unzip -q "$zip_path" -d "$out_dir"

  if [[ -f "$out_dir/h-live-evidence.json" ]]; then
    cp "$out_dir/h-live-evidence.json" "$OUTPUT_DIR/${target}.json"
  else
    echo "Newest artifact for ${target} has no h-live-evidence.json; creating fail-closed evidence."
    printf '{"schemaVersion":2,"ok":false,"target":"%s","completedAt":"%s","error":"artifact_missing_evidence_document"}\n' \
      "$target" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$OUTPUT_DIR/${target}.json"
  fi
done
