#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

PYTHON="${PYTHON:-python3}"
if [[ -x "$ROOT/.venv/bin/python" ]]; then
    PYTHON="$ROOT/.venv/bin/python"
fi

for asset in yolov8n.pt alarm.mp3 faudio.mp3 paudio.mp3; do
    if [[ ! -f "$ROOT/$asset" ]]; then
        echo "Missing required file: $asset" >&2
        exit 1
    fi
done

if grep -q '^version https://git-lfs.github.com/spec/v1$' "$ROOT/yolov8n.pt"; then
    echo "yolov8n.pt is a Git LFS pointer, not model weights. Run 'git lfs pull' first." >&2
    exit 1
fi

if ! "$PYTHON" -m PyInstaller --version >/dev/null 2>&1; then
    echo "PyInstaller is not installed for $PYTHON." >&2
    echo "Install project dependencies with: $PYTHON -m pip install -r requirements.txt" >&2
    exit 1
fi

build_executable() {
    local name="$1"
    local entry_point="$2"

    "$PYTHON" -m PyInstaller \
        --noconfirm \
        --clean \
        --onefile \
        --name "$name" \
        --add-data "yolov8n.pt:." \
        --add-data "alarm.mp3:." \
        --add-data "faudio.mp3:." \
        --add-data "paudio.mp3:." \
        --collect-all ultralytics \
        --collect-all mediapipe \
        --collect-all torchvision \
        "$entry_point"
}

build_executable SmartStudyMonitor app.py
build_executable SmartStudyMonitorLite app_lite.py

echo
echo "Original executable: $ROOT/dist/SmartStudyMonitor"
echo "Lite executable:     $ROOT/dist/SmartStudyMonitorLite"
