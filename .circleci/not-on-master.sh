#!/usr/bin/env bash
set -Eeuo pipefail

if [ "${CIRCLE_BRANCH}" == "master" ]; then
  echo "Skipping this step on master..."
else
  echo "Skipping this step on my branch, too..."
fi
