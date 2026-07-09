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
