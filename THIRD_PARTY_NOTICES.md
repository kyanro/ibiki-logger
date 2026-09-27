# Third-party notices

## Bundled music

Wabigashi Fumi / OTOGI SHIFT — にゃーにゃーにゃー.
Song page: https://otogishift.com/songs/nyaa-nyaa-nyaa/

This recording is not covered by the Apache License 2.0 for this app's code.
See `ASSET_LICENSES.md` for the separate media rights notice.

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

AndroidX / Jetpack Compose, Copyright The Android Open Source Project, are
distributed under Apache License 2.0. The resolved Compose version is 1.10.6,
Material 3 is 1.4.0, Core is 1.18.0, Activity is 1.13.0, and Lifecycle is 2.10.0.
The original AndroidX license texts included in the Maven artifacts are Apache 2.0.

## LiteRT 1.4.2

Copyright The TensorFlow Authors / Google. Apache License 2.0.
The original `LICENSE` from the distributed `litert-1.4.2.aar` also contains
the Caffe copyright and BSD-style redistribution terms. It is reproduced in full
in `third-party-licenses/LiteRT-1.4.2-LICENSE.txt` and bundled in the APK.
The `litert-api-1.4.2.aar` contains the same license text.

Artifact: https://dl.google.com/dl/android/maven2/com/google/ai/edge/litert/litert/1.4.2/litert-1.4.2.aar

## Kotlin and Kotlin libraries

Kotlin standard library 2.3.20, Copyright JetBrains s.r.o. and Kotlin Programming
Language contributors, is primarily Apache License 2.0. The JVM standard library
also includes portions with the following upstream attribution and terms:

- Collections: derived from GWT, Copyright (C) 2007-08 Google Inc.; Apache 2.0.
- Unsigned JVM operations: derived from Guava's UnsignedLongs, Copyright (C) 2011
  The Guava Authors; Apache 2.0.
- Time: Copyright (c) 2007-present, Stephen Colebourne & Michael Nascimento Santos;
  BSD 3-clause.
- JVM special math functions: Copyright Eric Ford & Hubert Holin 2001;
  Boost Software License 1.0.

The corresponding unmodified license texts are in `third-party-licenses/Kotlin-*.txt`
and bundled in the APK. Their source and scope are documented upstream:
https://github.com/JetBrains/kotlin/blob/v2.3.20/license/README.md

Kotlin coroutines 1.9.0 and serialization core 1.7.3 are also included transitively.
They are Copyright JetBrains s.r.o. and contributors, Apache License 2.0.
The coroutines-test 1.10.2 dependency is used by tests only.

## Other transitive runtime libraries

- Guava ListenableFuture 1.0, The Guava Authors / Google: Apache License 2.0.
- JetBrains annotations 23.0.0, JetBrains s.r.o.: Apache License 2.0.
- JSpecify 1.0.0, The JSpecify Authors: Apache License 2.0.

The resolved runtime inventory and review scope are recorded in
`docs/dependency-licenses.md`. Third-party components retain their own terms;
the application's Apache license does not replace those terms or the separate
music rights notice. Recheck the inventory and bundled notices when dependencies
or the model are updated.
