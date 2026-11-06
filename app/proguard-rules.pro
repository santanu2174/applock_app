# Keep data models & serialization
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Jetpack Security & Crypto
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }

# Biometric & CameraX
-keep class androidx.biometric.** { *; }
-keep class androidx.camera.** { *; }

# TensorFlow Lite & ML Kit
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.mlkit.** { *; }

# Keep App Security Engines
-keep class com.security.lockguard.security.** { *; }
-keep class com.security.lockguard.intruder.** { *; }
-keep class com.security.lockguard.ml.** { *; }

