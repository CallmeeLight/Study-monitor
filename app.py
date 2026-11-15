import sys
from pathlib import Path

import cv2
import cvzone
from cvzone.FaceMeshModule import FaceMeshDetector
from ultralytics import YOLO
import pygame


ROOT = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
AUDIO_FILES = {
    "sleep": ROOT / "alarm.mp3",
    "facehide": ROOT / "faudio.mp3",
    "phone": ROOT / "paudio.mp3",
}


def load_alarm(name: str):
    path = AUDIO_FILES.get(name)
    if path is None or not path.exists():
        return None
    return pygame.mixer.Sound(str(path))


def play_alarm(alarm_name: str, current_playing: str, audio_busy: bool, sounds):
    if audio_busy or alarm_name not in sounds or sounds[alarm_name] is None:
        return current_playing
    sounds[alarm_name].play(0)
    return alarm_name


def main(lite_mode: bool = False):
    audio_available = True
    try:
        pygame.mixer.init()
    except pygame.error:
        print("Audio device unavailable; continuing without sound alerts.")
        audio_available = False

    sounds = {
        "sleep": load_alarm("sleep") if audio_available else None,
        "facehide": load_alarm("facehide") if audio_available else None,
        "phone": load_alarm("phone") if audio_available else None,
    }

    current_playing = None

    model_path = ROOT / "yolov8n.pt"
    if not model_path.exists():
        raise FileNotFoundError(f"YOLO model not found: {model_path}")

    cap = cv2.VideoCapture(0)
    if lite_mode:
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
    if not cap.isOpened():
        raise RuntimeError("Could not open webcam. Please check camera permissions or availability.")

    face_detector = FaceMeshDetector(maxFaces=1)
    phone_detector = YOLO(str(model_path))
    class_names = getattr(phone_detector, "names", {}) or {}

    LEFT_EYE_TOP = 159
    LEFT_EYE_BOTTOM = 145
    FACE_LEFT = 130
    FACE_RIGHT = 243

    closed_frames = 0
    SLEEP_THRESHOLD_FRAMES = 15
    covered_frames = 0
    COVER_THRESHOLD_FRAMES = 20
    phone_detection_interval = 3 if lite_mode else 1
    frame_number = 0
    phone_detected = False
    phone_boxes = []

    try:
        while True:
            success, img = cap.read()
            if not success:
                break
            frame_number += 1

            is_audio_busy = pygame.mixer.get_busy() if audio_available else False
            if not is_audio_busy:
                current_playing = None

            img, faces = face_detector.findFaceMesh(img, draw=False)
            is_sleepy = False
            is_face_covered = False

            if faces:
                covered_frames = 0
                face = faces[0]

                eye_dist, _ = face_detector.findDistance(face[LEFT_EYE_TOP], face[LEFT_EYE_BOTTOM])
                face_dist, _ = face_detector.findDistance(face[FACE_LEFT], face[FACE_RIGHT])

                if face_dist <= 0:
                    face_dist = 1

                ratio = (eye_dist / face_dist) * 100

                if ratio < 11.0:
                    closed_frames += 1
                else:
                    closed_frames = 0

                if closed_frames >= SLEEP_THRESHOLD_FRAMES:
                    is_sleepy = True

                cvzone.putTextRect(img, f"Eye Ratio: {int(ratio)}", (30, 40), scale=1, thickness=1)
            else:
                closed_frames = 0
                covered_frames += 1

                if covered_frames >= COVER_THRESHOLD_FRAMES:
                    is_face_covered = True

            if frame_number % phone_detection_interval == 0:
                phone_boxes = []
                phone_detected = False
                if lite_mode:
                    results = phone_detector.predict(img, imgsz=320, stream=True, verbose=False)
                else:
                    results = phone_detector.predict(img, stream=True, verbose=False)

                for r in results:
                    if r.boxes is None:
                        continue
                    for box in r.boxes:
                        if len(box.cls) == 0 or len(box.conf) == 0:
                            continue

                        cls_id = int(box.cls[0])
                        conf = float(box.conf[0])

                        if str(class_names.get(cls_id, "")).lower() == "cell phone" and conf > 0.5:
                            phone_detected = True
                            x1, y1, x2, y2 = map(int, box.xyxy[0])
                            phone_boxes.append((x1, y1, x2, y2, conf))

            for x1, y1, x2, y2, conf in phone_boxes:
                cv2.rectangle(img, (x1, y1), (x2, y2), (255, 0, 255), 2)
                cvzone.putTextRect(
                    img,
                    f"Phone detected! {int(conf * 100)}%",
                    (x1, max(y1 - 10, 30)),
                    scale=1,
                    thickness=1,
                    colorR=(255, 0, 255),
                )

            if is_face_covered:
                cvzone.putTextRect(img, "DONT COVER YOUR FACE!", (50, 100), scale=2, thickness=3, colorR=(0, 0, 255))
                if not is_audio_busy:
                    current_playing = play_alarm("facehide", current_playing, is_audio_busy, sounds)
            elif is_sleepy:
                cvzone.putTextRect(img, "WAKE UP & STUDY!", (50, 100), scale=2, thickness=3, colorR=(0, 0, 255))
                if not is_audio_busy:
                    current_playing = play_alarm("sleep", current_playing, is_audio_busy, sounds)
            elif phone_detected:
                cvzone.putTextRect(img, "PUT THE PHONE AWAY!", (50, 100), scale=2, thickness=3, colorR=(0, 165, 255))
                if not is_audio_busy:
                    current_playing = play_alarm("phone", current_playing, is_audio_busy, sounds)
            elif is_audio_busy and current_playing:
                if current_playing == "facehide":
                    cvzone.putTextRect(img, "DONT COVER YOUR FACE!", (50, 100), scale=2, thickness=3, colorR=(0, 0, 255))
                elif current_playing == "sleep":
                    cvzone.putTextRect(img, "WAKE UP & STUDY!", (50, 100), scale=2, thickness=3, colorR=(0, 0, 255))
                elif current_playing == "phone":
                    cvzone.putTextRect(img, "PUT THE PHONE AWAY!", (50, 100), scale=2, thickness=3, colorR=(0, 165, 255))

            cv2.imshow("Smart Study Monitor", img)
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
    finally:
        cap.release()
        cv2.destroyAllWindows()
        if audio_available:
            pygame.mixer.quit()


def run(lite_mode: bool = False):
    try:
        main(lite_mode=lite_mode)
    except (FileNotFoundError, RuntimeError) as exc:
        print(f"Error: {exc}")
        raise SystemExit(1)
    except KeyboardInterrupt:
        print("Application stopped by user.")


if __name__ == "__main__":
    run()
