# Smart Study Monitor

A webcam-based study monitoring application that helps detect common distractions while a person is studying. It uses computer vision to detect:

- sleepy behavior (eyes closed for too long)
- face covering / hidden face
- phone usage

When a distraction is detected, the app shows a warning on screen and plays an alarm sound.

---

## Features

- Real-time webcam monitoring
- Face mesh detection for eye and face analysis
- YOLO object detection for cell phone detection
- Visual warnings on screen
- Audio alerts for sleep, face cover, and phone usage
- Works in a local desktop environment with a webcam

---

## Project Structure

```text
Smart_Study_Monitor-main/
├── app.py
├── android/
├── alarm.mp3
├── faudio.mp3
├── paudio.mp3
├── yolov8n.pt
├── face_landmarker.task
├── hand_landmarker.task
└── README.md
```

### Main files

- `app.py` – Main application logic
- `yolov8n.pt` – YOLO model used for object detection
- `alarm.mp3`, `faudio.mp3`, `paudio.mp3` – sound files used for alerts

---

## Requirements

Before running the project, make sure you have:

- Windows 10 or 11
- A working webcam
- Speaker or audio output enabled
- Python 3.11 installed

Important:

- Python 3.14 may fail with `pygame` due to compatibility issues.
- The project was validated successfully with Python 3.11.

---

## Installation

### 1) Install Python 3.11

If you do not already have Python 3.11, install it from:

- https://www.python.org/downloads/release/python-3119/

or via winget:

```powershell
winget install --id Python.Python.3.11 -e --source winget
```

### 2) Open a terminal in the project folder

```powershell
cd "C:\path\to\Smart_Study_Monitor-main"
```

### 3) Create a virtual environment (recommended)

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\activate
```

### 4) Install dependencies

```powershell
py -3.11 -m pip install --upgrade pip
py -3.11 -m pip install opencv-python cvzone ultralytics pygame mediapipe
```

If you are using the virtual environment, this command is enough:

```powershell
python -m pip install --upgrade pip
python -m pip install opencv-python cvzone ultralytics pygame mediapipe
```

---

## How to Run

From the project folder:

```powershell
cd "C:\path\to\Smart_Study_Monitor-main"
py -3.11 app.py
```

If your virtual environment is active:

```powershell
python app.py
```

The camera window will open. Press `Q` to quit.

## Build a Linux executable

On Linux, install the project dependencies and PyInstaller in the virtual environment:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

If `yolov8n.pt` was checked out as a Git LFS pointer, install Git LFS and fetch the actual model before building:

```bash
sudo apt install git-lfs
git lfs install
git lfs pull
```

Build the standalone executable:

```bash
chmod +x build.sh
./build.sh
```

The original and Lite executables will be created at `dist/SmartStudyMonitor` and `dist/SmartStudyMonitorLite`. Run either from a terminal, for example:

```bash
./dist/SmartStudyMonitor
./dist/SmartStudyMonitorLite
```

The one-file executable includes the YOLO model and alert audio files. It still requires a compatible Linux system with webcam access and the system libraries needed by OpenCV, MediaPipe, and audio playback. PyInstaller builds are specific to the operating system and CPU architecture used to create them.

## Build a Windows executable

The project has two desktop builds:

- **Original:** Default camera resolution, phone detection on every frame, and the original YOLO input size.
- **Lite:** Requests 640×480 camera capture, uses 320-pixel YOLO inference, and checks for phones every third frame. This reduces processing load but can make phone alerts slower and less reliable. It uses the same AI libraries and models, so its baseline memory use is still substantial; it is not guaranteed to run smoothly with only 4 GB of RAM.

On Windows 10 or 11, install Python 3.12 and Git LFS, then run these commands from the project folder in PowerShell:

```powershell
git lfs install
git lfs pull
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
.\build-windows.ps1
```

The executables will be created at `dist\SmartStudyMonitor.exe` and `dist\SmartStudyMonitorLite.exe`. A Windows executable must be built on Windows; it cannot be produced by the Linux build script.

Alternatively, push the repository to GitHub and run the **Build Windows executable** workflow from the Actions tab. Download the `SmartStudyMonitor-Windows-x64` artifact from the completed workflow run; it contains separate ZIP files for both versions. The workflow retains artifacts for 14 days.

## Build an Android APK

The native Android app is in `android/`. It uses CameraX and MediaPipe for front-camera face/eye and phone detection. Follow [android/README.md](./android/README.md) to build locally; its phone detector model is downloaded separately during setup.

Alternatively, push the repository to GitHub and run **Build Android APK** from the Actions tab. Download `SmartStudyMonitor-Android-debug` from the completed workflow run. Install the APK on an Android 8.0+ phone, allow camera permission, and start monitoring. The workflow's debug APK is for testing/sideloading, not Play Store distribution, and artifacts are retained for 14 days.

---

## What the App Does

The application continuously checks three conditions:

1. Sleep detection
   - Detects whether the user’s eyes remain closed for too long.
   - Shows a warning like “WAKE UP & STUDY!”

2. Face cover detection
   - Detects if the face is hidden or partially covered.
   - Shows a warning like “DONT COVER YOUR FACE!”

3. Phone detection
   - Uses YOLO to detect a mobile phone in the frame.
   - Shows a warning like “PUT THE PHONE AWAY!”

If one of these events is detected, the app plays an alarm sound.

---

## Troubleshooting

### ModuleNotFoundError: No module named 'cv2'

Install OpenCV:

```powershell
py -3.11 -m pip install opencv-python
```

### ModuleNotFoundError: No module named 'cvzone'

Install the missing package:

```powershell
py -3.11 -m pip install cvzone
```

### ModuleNotFoundError: No module named 'mediapipe'

Install MediaPipe:

```powershell
py -3.11 -m pip install mediapipe
```

### pygame compatibility issues

Use Python 3.11 instead of Python 3.14.

Example:

```powershell
py -3.11 app.py
```

### Webcam not opening

- Make sure the webcam is connected and not already used by another app.
- Check Windows privacy settings for camera access.
- Try closing other apps that may be using the camera.

### Audio not playing

Make sure:

- speakers are connected
- volume is on
- the following files exist in the project folder:
  - `alarm.mp3`
  - `faudio.mp3`
  - `paudio.mp3`

---

## Notes

- This project relies on GPU/CPU inference for face detection and object detection.
- The YOLO model file `yolov8n.pt` must remain in the project folder.
- The app is intended for local use and is most effective in a quiet environment with stable lighting.

---

## License

This project is provided as-is for educational or personal use. Please check the repository or original source for any license restrictions before commercial use.

---

## Quick Start

```powershell
cd "C:\path\to\Smart_Study_Monitor-main"
py -3.11 -m pip install --upgrade pip
py -3.11 -m pip install opencv-python cvzone ultralytics pygame mediapipe
py -3.11 app.py
```

If everything is set correctly, the webcam monitoring window should open and begin alerting when distractions are detected.
