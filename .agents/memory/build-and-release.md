# Build System & Release Pipeline Memory

## Low-Memory Gradle Profile
- Heap max size: `-Xmx1536m` configured in `gradle.properties` and root `build.gradle.kts`.
- Test serialization: `tasks.withType<Test>().configureEach { maxParallelForks = 1 }` prevents concurrent daemon memory thrashing.
- Module boundaries: Verified via `./gradlew enforceModuleBoundaries`.

## Release APK & Firebase Signing
- Release builds require keystore properties in `keystore.properties`.
- Ensure SHA-1 and SHA-256 fingerprints of both debug and release keys are registered in the Firebase Console (`mapsupervision-cahn`).
- ProGuard rules keep Room entities, MapLibre JNI, and MediaPipe / LiteRT binaries.
