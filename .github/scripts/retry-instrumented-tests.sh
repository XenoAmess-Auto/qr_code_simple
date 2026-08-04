#!/usr/bin/env bash
set -u

max_attempts=3
attempt=1

while [ $attempt -le $max_attempts ]; do
  echo "=== Instrumented test attempt $attempt/$max_attempts ==="
  if ./gradlew --no-daemon :app:connectedDebugAndroidTest; then
    echo "=== Instrumented tests passed on attempt $attempt ==="
    exit 0
  fi
  echo "=== Instrumented tests failed on attempt $attempt ==="
  attempt=$((attempt + 1))
  if [ $attempt -le $max_attempts ]; then
    echo "Retrying in 15 seconds..."
    sleep 15
  fi
done

echo "=== Instrumented tests failed after $max_attempts attempts ==="
exit 1
