# TensorFlow Lite
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options$GpuBackend
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options
-dontwarn org.tensorflow.lite.**

# MapLibre SDK
-keep class org.maplibre.android.** { *; }
-keep interface org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# GeoJSON Models
-keep class org.maplibre.geojson.** { *; }
-dontwarn org.maplibre.geojson.**

# Room Database Entities & DAOs
-keep class com.mapsupervision.data.db.entity.** { *; }
-keep class com.mapsupervision.data.db.dao.** { *; }
-keep class com.mapsupervision.domain.model.** { *; }

# Media3 & Transformer Video Processing
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-dontwarn androidx.media3.**

# CameraX Video & Camera Extensions
-keep class androidx.camera.video.** { *; }
-keep class androidx.camera.extensions.** { *; }
-dontwarn androidx.camera.video.**
-dontwarn androidx.camera.extensions.**

# Video Decoder & Pipeline Services
-keep class com.mapsupervision.photo.worker.** { *; }
-keep class coil.decode.VideoFrameDecoder** { *; }

