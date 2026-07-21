#!/usr/bin/env bash
#
# Downloads the RAG corpus for the incident management agent.
#
# Source: https://github.com/prometheus-operator/runbooks (Apache-2.0)
# Real production runbooks for kube-prometheus alerts. Each runbook follows a
# Meaning / Impact / Diagnosis / Mitigation structure, which is what the agent
# retrieves against when resolving an incident.
#
# Usage: ./scripts/download-corpus.sh
set -euo pipefail

REPO="prometheus-operator/runbooks"
BRANCH="main"
COMPONENTS=(alertmanager etcd general kube-state-metrics kubernetes node prometheus-operator prometheus)

DEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/src/main/resources/rag-corpus/runbooks"
mkdir -p "$DEST"

echo "Downloading runbooks from ${REPO}@${BRANCH} -> ${DEST}"

# One tarball instead of ~120 API calls: avoids GitHub's 60 req/hr unauthenticated limit.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

curl -fsSL "https://codeload.github.com/${REPO}/tar.gz/refs/heads/${BRANCH}" -o "$TMP/runbooks.tar.gz"
tar -xzf "$TMP/runbooks.tar.gz" -C "$TMP"

SRC="$(find "$TMP" -type d -path '*/content/runbooks' | head -1)"
if [ -z "$SRC" ]; then
  echo "ERROR: could not locate content/runbooks in the downloaded archive" >&2
  exit 1
fi

count=0
for comp in "${COMPONENTS[@]}"; do
  [ -d "$SRC/$comp" ] || { echo "  skip: $comp (not present)"; continue; }
  n=0
  for f in "$SRC/$comp"/*.md; do
    [ -e "$f" ] || continue
    base="$(basename "$f")"
    # _index.md files are Hugo section headers, not runbooks.
    [ "$base" = "_index.md" ] && continue
    # Flatten to <component>__<Alert>.md so the component survives as metadata.
    cp "$f" "$DEST/${comp}__${base}"
    n=$((n + 1))
  done
  count=$((count + n))
  printf '  %-22s %3d runbooks\n' "$comp" "$n"
done

echo "Done: $count runbooks in $DEST"
