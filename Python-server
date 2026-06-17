from flask import Flask, request, jsonify
from ultralytics import YOLO
from collections import Counter

app = Flask(__name__)
model = YOLO("yolov8n.pt")

target_objects = [
    "chair", "person", "truck", "train", "car", "stop sign",
    "cow", "ball", "couch", "bed", "dining table", "potted plant",
    "cell phone", "refrigerator"
]
@app.route('/')
def index():
    return "Server is running"
@app.route('/detect', methods=['POST'])
def detect():
    file = request.files['image']
    path = "temp.png"
    file.save(path)
    results = model.predict(path)
    result = results[0]
    detected = []
    for box in result.boxes:
        name = result.names[int(box.cls[0])]
        if name in target_objects:
            detected.append(name)

    counts = Counter(detected)

    if counts:
        message = ", ".join(
            f"{v} {k}{'s' if v>1 else ''}" for k, v in counts.items()
        )
    else:
        message = "No target objects detected"

    return jsonify({"object detected are ": message})
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
