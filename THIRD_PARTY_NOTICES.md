# Third-party notices

## YAMNet

YAMNet, TensorFlow Authors / Google, Apache License 2.0. Bundled license:
`android-app/app/src/main/assets/YAMNET-LICENSE.txt`.

Unmodified TensorFlow Lite classification model, version 1:
https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/audio_classification/android/lite-model_yamnet_classification_tflite_1.tflite

SHA-256: `10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de`

The published label map contains 521 classes; index 38 is Snoring.
https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/yamnet_class_map.csv

Model documentation: https://www.tensorflow.org/hub/tutorials/yamnet

The model is general-purpose sound classification, not a validated medical detector.
Scores are not calibrated probabilities, and it cannot identify the person producing sound.

## Android libraries

AndroidX / Jetpack Compose and LiteRT are distributed under Apache License 2.0.
Their Maven dependency versions are pinned in the Android project.
