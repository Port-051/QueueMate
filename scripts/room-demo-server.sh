#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose -f compose.room-demo.yml up -d --wait
export DB_URL=jdbc:postgresql://localhost:55432/queuemate
export REDIS_PORT=56379
exec ./backend/gradlew -p backend bootRun --args='--queuemate.rooms.enabled=true --server.address=127.0.0.1'
