# Keep TensorFlow Lite classes — they are accessed via JNI / reflection.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep model asset intact (handled by aapt noCompress for tflite).
