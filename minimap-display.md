# Review Minimap Display rendering

Review and refine the minimap display rendering (visual layout of the minimap tile, pegman marker, view cone, and coordinates label) for visual harmony, correctness under device rotation, and consistent scaling between preview and final captured output.

## Proposed Changes

### Photo Component (`photo`)

#### [MODIFY] [PhotoStampRenderer.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/photo/src/main/java/com/mapsupervision/photo/worker/PhotoStampRenderer.kt)
- Review pegman dot radii (`outerDotRadius`, `innerDotRadius`, `coreDotRadius`) and view cone scale multipliers to ensure perfect visual clarity and premium styling.
- Adjust `drawMinimap` bearing math to confirm alignment with standard compass headings and handle any coordinate offset anomalies.
- Fine-tune tile rendering bounds and clipping path calculations.

#### [MODIFY] [PhotoStampLayout.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/photo/src/main/java/com/mapsupervision/photo/worker/PhotoStampLayout.kt)
- Optimize layout parameters (e.g. `mapDotOuterRadius`, `mapDotInnerRadius`, `mapDotCoreRadius`, `mapCornerRadius`, and `mapBorderWidth`) for different screen and image aspect ratios.

### App Component (`app`)

#### [MODIFY] [CameraOverlayState.kt](file:///d:/Code%20Antinigaty/MAPSUPERVISION-NonAuth/app/src/main/java/com/mapsupervision/app/CameraOverlayState.kt)
- Review device orientation / sensor bearing updates to ensure display rotation angles (portrait/landscape offsets) are correctly handled.

## Verification Plan

### Automated Tests
- Run layout tests: `.\gradlew :photo:test`

### Manual Verification
- Deploy to an Android device or emulator.
- Open the camera overlay screen.
- Rotate the device to confirm the pegman view cone rotates accurately.
- Compare the size and visual placement of the minimap on the live preview screen against a captured photo.
