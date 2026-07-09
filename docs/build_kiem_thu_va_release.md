# Build, kiem thu va release

## 1. Nen tang build

- Android Gradle Plugin: `8.13.2`
- Kotlin: `2.2.21`
- Java target: `17`
- Compile SDK: `36`
- Target SDK: `35`
- Min SDK: `24`

## 2. Lenh build co ban

### 2.1 Build debug

```powershell
.\gradlew.bat assembleDebug
```

### 2.2 Build release

```powershell
.\gradlew.bat :app:assembleRelease
```

### 2.3 Chay gate tong hop

```powershell
sh ./scripts/release_gate.sh
```

Neu chay tren Windows PowerShell va khong co `sh`, co the goi tung lenh Gradle tuong ung.

## 3. Test va verification hien co

Script `scripts/release_gate.sh` dang chay cac buoc sau:

1. `./gradlew :app:testDebugUnitTest`
2. `./gradlew :storage-import:testDebugUnitTest`
3. `./gradlew :data:testDebugUnitTest`
4. `./gradlew lint assembleDebug enforceModuleBoundaries`
5. Kiem tra su ton tai cua:
   - `docs/release_gate_runbook.md`
   - `docs/tab_nhap_lieu_data_hub.md`
   - `production-ready-roadmap.md`

## 4. CI

Workflow GitHub Actions:

- File: `.github/workflows/android.yml`
- Trigger:
  - `push` len `main`/`master`
  - `pull_request` vao `main`/`master`
- Muc tieu:
  - setup JDK 17
  - setup Gradle
  - chay `scripts/release_gate.sh`
  - dump `hs_err_pid*.log` neu co loi JVM

## 5. Module boundary guard

Repo co custom task `enforceModuleBoundaries` trong `buildSrc/src/main/kotlin/EnforceModuleBoundariesTask.kt`.

Task nay:

- doc cac `build.gradle.kts` cua module
- tim dependency `project(":module")`
- fail build neu module tham chieu module khong nam trong danh sach cho phep

Y nghia:

- giu layering ro rang
- giam coupling nguoc
- bat su co kien truc som ngay tu CI

## 6. Ky ten release

`app/build.gradle.kts` dang cau hinh:

- `applicationId = "com.mapsupervision"`
- `versionCode = 2`
- `versionName = "1.1"`
- split ABI:
  - `arm64-v8a`
  - `armeabi-v7a`

Signing release doc tu `local.properties`:

- `RELEASE_STORE_FILE`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

## 7. Artifact dau ra

Vi release dang bat ABI split, artifact thuong nam tai:

- `app/build/outputs/apk/release/`

Co the sinh nhieu APK theo ABI thay vi mot universal APK.

## 8. Goc nhin test coverage cap module

Module co nhieu test hon:

- `app`
- `data`
- `ai-agent`
- `gis-maplibre`
- `photo`

Module hien rat it hoac chua co test rieng:

- `ai-core`
- `ai-model`
- `ai-rag`
- `ai-prompt`
- `reporting`
- `timeline`

Day la thong tin huu ich khi danh gia rui ro thay doi.

## 9. Checklist truoc khi merge thay doi lon

- Chay lai test cua module bi anh huong truc tiep.
- Chay `lint` neu co sua UI/Android resource/manifest.
- Chay `enforceModuleBoundaries` neu co sua dependency Gradle.
- Neu co sua import/database, chay them gate `:storage-import` va `:data`.
- Neu co sua shell/workspace flow, chay them `:app:testDebugUnitTest`.

## 10. Tai lieu lien quan

- `release_gate_runbook.md`
- `tong_hop_du_an_v1.1.md`
- `module_matrix_chi_tiet.md`
