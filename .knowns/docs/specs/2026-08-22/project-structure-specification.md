---
title: MapSupervision Project Structure & Build Conventions Specification
description: Comprehensive build system specification covering AGP 8.13.2, Kotlin 2.2.21, low-memory execution profile, enforceModuleBoundaries verification, and release runbooks.
tags: [spec, project-structure, gradle, toolchains, build, approved]
---

# MapSupervision Project Structure & Build Conventions Specification

> **Status:** APPROVED  
> **Date:** 2026-08-22  
> **Version:** 1.0  
> **Authors:** Principal Build Engineer, DevOps Specialist, Security Lead  
> **Target Toolchain:** Android Gradle Plugin 8.13.2, Kotlin 2.2.21, Java 17, Gradle 8.11+

---

## 1. Project Directory Layout & File Hierarchy

The MapSupervision repository is organized as a multi-module Gradle project containing 18 sub-modules, verification scripts, Firebase infrastructure definitions, and Knowns AI operating memory:

```plaintext
MAPSUPERVISION-Firebase/
├── .agents/                    # AI Agent skills, workflows, and memory index
├── .knowns/                    # Knowns memory layer (docs, tasks, decisions, specs)
│   ├── docs/                   # Markdown documentation catalog
│   │   ├── architecture-overview.md
│   │   ├── build-conventions.md
│   │   ├── guides/
│   │   ├── learnings/
│   │   ├── patterns/
│   │   └── specs/2026-08-22/   # Formal SDD specifications
│   ├── tasks/                  # Task management database
│   └── decisions/              # System Architectural Decision Records (ADR)
├── app/                        # Main Android application module (UI Shell & DI Root)
│   ├── src/main/AndroidManifest.xml
│   └── src/main/java/com/mapsupervision/app/
│       ├── auth/               # Firebase authentication & session state
│       ├── ai/                 # Application AI bridges & heavy background workers
│       ├── sync/               # Background media upload worker & scheduler
│       ├── ui/                 # App theming and global dialogs
│       ├── widget/             # Android Home Screen Diary Calendar AppWidget
│       └── workspace/          # WorkspaceAppShell, Hub screens, and ViewModels
├── core/                       # Core utilities, coroutines, logging, AppResult<T>
├── domain/                     # Pure Kotlin domain entities, contracts, use cases
├── data/                       # Room DB v48, DAOs, Firestore sync, Outbox, Repositories
├── project/                    # Multi-project workspace switcher & management UI
├── gis/                        # GIS domain models, styling contracts, spatial math
├── gis-maplibre/               # MapLibre SDK integration & vector tile renderer
├── photo/                      # CameraX HUD, GPS watermark, Exif stamping, Gallery UI
├── timeline/                   # Construction timeline, diary log calendar UI
├── reporting/                  # PDF report generator, DOCX exporter, CSV/Excel export
├── storage-core/               # Active project state, folder resolution, ZIP export
├── storage-crypto/             # Keystore encryption & AES-GCM encrypted persistence
├── storage-import/             # Multi-format parsers (Excel, KML, KMZ, GeoJSON, DOCX)
├── ai-core/                    # AI contracts, engine interfaces, execution policies
├── ai-agent/                   # Multi-agent orchestrator & summary aggregator
├── ai-model/                   # Concrete AI engines (Gemini, LiteRT, Gemma, ML Kit)
├── ai-rag/                     # Local document embedding & RAG retrieval builder
├── ai-prompt/                  # Prompt builders, Vietnamese parser, dictionary resolver
├── buildSrc/                   # Custom Gradle build tasks (EnforceModuleBoundariesTask)
├── docs/                       # Human-facing technical manuals & legacy docs
├── specs/2026-08-22/           # Release runbooks, auth verification scripts
├── firestore.rules             # Cloud Firestore security rules
├── storage.rules               # Cloud Storage security rules
├── firestore.indexes.json      # Cloud Firestore composite query indexes
├── firebase.json               # Firebase CLI project configuration
├── build.gradle.kts            # Root Gradle build script & boundary rules
├── settings.gradle.kts         # Module inclusion definitions
└── gradle.properties           # JVM memory and build daemon tuning
```

---

## Locked Decisions

- D1: Android Gradle Plugin 8.13.2 with Kotlin 2.2.21 and Java 17 toolchain.
- D2: Low-memory build profile (-Xmx1536m, maxParallelForks=1) to prevent memory thrashing.
- D3: Automated quality gates via enforceModuleBoundaries and ./gradlew check.

## System Decision Impact

- Impact: none — preserves and validates existing build profile constraints.

---

## 2. Toolchain Versions & Dependency Specifications

### 2.1 Core Build System
- **Android Gradle Plugin (AGP):** `8.13.2`
- **Kotlin Language Version:** `2.2.21`
- **KSP (Kotlin Symbol Processing):** `2.2.21-2.0.5`
- **Compose Compiler:** Bundled Jetpack Compose plugin for Kotlin `2.2.21`
- **Dagger Hilt:** `2.57.2`
- **Google Services Plugin:** `4.4.2`
- **Kotlin Serialization:** `2.2.21`
- **Java Version:** `17` (Target & Source compatibility across all modules)

### 2.2 Android SDK Versions
- **Compile SDK:** `35` (Android 15)
- **Target SDK:** `35` (Android 15)
- **Min SDK:** `26` (Android 8.0 Oreo - guarantees compatibility across field tablets)

### 2.3 Key Third-Party SDKs & Library Pins
| Library | Version | Purpose |
| :--- | :--- | :--- |
| **Room Database** | `2.6.1` (KSP enabled) | Local SQLite persistence |
| **MapLibre Android** | `11.5.1` | Native vector tile mapping |
| **CameraX** | `1.4.0` | Camera capture & image analysis |
| **Firebase BOM** | `33.7.0` | Firebase Auth, Firestore, Cloud Storage |
| **Play Services Auth**| `21.3.0` | Google Sign-In with SHA verification |
| **Google Gemini AI** | `0.9.0` | Cloud generative reasoning |
| **MediaPipe Text** | `0.10.32` | Gemma on-device LLM runner |
| **LiteRT LM** | `0.13.1` | On-device tensor execution |
| **Apache POI (POI-OOXML)**| `5.2.5` | Excel (.xlsx) & Word (.docx) generation |
| **iText7 Core** | `7.2.5` | High-precision PDF report rasterization |

---

## 3. Build Performance & Low-Memory Profile Constraints

To guarantee reliable, crash-free compilation on developer laptops and continuous integration runners without memory starvation:

### 3.1 Deliberately Serialized Low-Memory Configuration
Configured in root `build.gradle.kts` and `gradle.properties`:
```kotlin
subprojects {
    tasks.withType<JavaCompile>().configureEach {
        exclude("**/byRounds/**")
        options.encoding = "UTF-8"
    }
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
    tasks.withType<Test>().configureEach {
        maxParallelForks = 1
        jvmArgs("-Xmx1536m", "-Djava.awt.headless=true")
    }
}
```

### 3.2 Compilation Invariants
- **Headless Mode:** `-Djava.awt.headless=true` prevents graphics context allocation during test execution.
- **KSP Cache Optimization:** Incremental processing enabled to speed up Room and Hilt code generation without ballooning JVM heap.

---

## 4. Verification & Quality Assurance Protocols

### 4.1 Module Boundary Enforcement (`enforceModuleBoundaries`)
The custom Gradle verification task scans all sub-module `build.gradle.kts` files and compares dependencies against `allowedProjectDependencies`:
```bash
./gradlew enforceModuleBoundaries
```
Any unauthorized cross-module coupling (e.g. `:data` referencing UI in `:app` or `:core` importing any other module) causes an immediate build failure.

### 4.2 Automated Quality Gates (`./gradlew check`)
Running `./gradlew check` executes:
1. `enforceModuleBoundaries`
2. Kotlin lint and compiler checks
3. Unit test suites across all 18 modules
4. Room schema validation against `schemas/` directory

---

## 5. Release Gate & Deployment Pipeline

### 5.1 Signed APK Build Runbook
1. **Keystore Configuration:** Verify release keystore and alias in `local.properties`:
   ```properties
   RELEASE_STORE_FILE=/path/to/mapsupervision.keystore
   RELEASE_STORE_PASSWORD=***
   RELEASE_KEY_ALIAS=mapsupervision
   RELEASE_KEY_PASSWORD=***
   ```
2. **SHA Fingerprint Verification:**
   Ensure release SHA-1 and SHA-256 fingerprints are registered in the Firebase Console and Google Cloud Console OAuth 2.0 Client IDs.
3. **Execution Scripts:**
   - Powershell: `./specs/2026-08-22/run_release_check.ps1`
   - Signed APK Build: `./gradlew assembleRelease`
4. **ProGuard / R8 Rules:**
   - Keep Room entities and database type converters.
   - Keep MapLibre native JNI bindings.
   - Keep Gemma / LiteRT tensor models and model loaders.

---

## 6. Knowns AI Operating Integration

This project is fully instrumented with Knowns (v0.30.0) as the central memory layer:
- **Specs Directory:** Stored under `.knowns/docs/specs/yyyy-mm-dd/` following Spec-Driven Development (SDD).
- **Decisions Directory:** Stored under `.knowns/decisions/` as immutable Architectural Decision Records.
- **Tasks Directory:** Tracked in `.knowns/tasks/` with acceptance criteria linked to specs.
- **Validation:** Continuously validated using `knowns validate`.
