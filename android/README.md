# Android app

This native Android client uses **CameraX** for the front-camera preview and frame analysis, plus **MediaPipe Tasks Vision** for face landmarks and object detection. It reports sustained eye closure (eye-aspect ratio), no face for 5 seconds, and a COCO `cell phone` detection; warnings appear over the preview and repeat as short alarm-tone beeps. Camera frames are rotated upright before inference. MediaPipe Tasks handles model input resizing/normalization and detector box decoding/NMS; the app filters the returned COCO categories for phone labels.

## Models

- Face landmarks: uses the repository's existing `../face_landmarker.task`; Gradle copies it to generated build assets, so the model is not duplicated in this directory.
- Phone detection: download MediaPipe's quantized EfficientDet-Lite0 COCO object detector (includes task metadata):

  ```sh
  mkdir -p app/src/main/assets
  curl -L \
    https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite \
    -o app/src/main/assets/efficientdet_lite0.tflite
  ```

  Model source and usage details: [MediaPipe object detector models](https://developers.google.com/mediapipe/solutions/vision/object_detector#models). The download is intentionally not checked in.

## Build and run

1. Install Android Studio (or JDK 17, Android SDK Platform 35, and Gradle 8.9).
2. Download the phone model as above.
3. Open this `android/` directory in Android Studio and let Gradle sync, or run `gradle :app:assembleDebug` from it.
4. Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 8.0+ device with a front camera. Grant camera access when prompted; the app starts monitoring automatically.

Audio alerts use Android's `ToneGenerator`; playback does not require microphone permission. `MODIFY_AUDIO_SETTINGS` and `VIBRATE` are declared for alarm-stream behavior/device alert support. No microphone capture or network access occurs in the app.
