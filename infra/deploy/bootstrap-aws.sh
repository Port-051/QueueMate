#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 infra/deploy/bootstrap-aws.py "${1:-E256CECAKU833U}"
