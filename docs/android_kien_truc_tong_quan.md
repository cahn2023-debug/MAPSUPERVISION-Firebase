# Kien truc tong quan Android

## 1. Muc tieu cua app

`MapSupervision` la ung dung Android cho giam sat cong trinh theo ban do so. App tap trung vao 6 nhom nghiep vu:

- Quan ly project va workspace.
- Nhap du lieu thiet ke/GIS.
- Giam sat map, tien do, vat tu, nhat ky.
- Chup anh hien truong gan vi tri.
- Ho tro AI offline/on-device.
- Xuat bao cao va dong goi du an.

## 2. Entry point va runtime startup

### 2.1 `MapSupervisionApplication`

`app/src/main/java/com/mapsupervision/app/MapSupervisionApplication.kt`

Trach nhiem chinh:

- Khoi tao `AppLogger`.
- Cai `MapBridgeInstaller` cho lop ban do.
- Cau hinh `Coil ImageLoader` voi memory/disk cache, tu dong giam footprint tren thiet bi low-RAM.
- Cap `WorkManager Configuration` qua `HiltWorkerFactory`.
- Don memory cache khi he thong goi `onTrimMemory`.

### 2.2 `MainActivity`

`app/src/main/java/com/mapsupervision/app/MainActivity.kt`

Trach nhiem chinh:

- Entry point cua UI Compose.
- Nhap `IPhotoPipelineService` va `IPhotoLocationProvider`.
- Parse `ACTION_SEND` va `ACTION_SEND_MULTIPLE` ngay khi mo app.
- Boc `WorkspaceAppShell` trong `StartupPermissionWrapper`.

### 2.3 Permission gate luc khoi dong

`StartupPermissionWrapper` hien tai yeu cau:

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `CAMERA`

Neu chua duoc cap, app dung o `PermissionIntroScreen` va chua vao workspace.

## 3. Navigation shell

`WorkspaceAppShell` la root UI shell cua ung dung.

### 3.1 Cac tab chinh

State enum nam o `WorkspaceStateModels.kt`:

- `MAP`
- `PROGRESS`
- `DATA`
- `REPORTS`
- `MATERIALS`

### 3.2 Route trong `NavHost`

Theo `WorkspaceAppShell.kt`, app co 5 route chinh:

- `map`
- `progress`
- `data`
- `reports`
- `materials`

### 3.3 Layout mode

App co 2 mode hien thi:

- `COMPACT`: cho phone/man hinh hep.
- `EXPANDED`: cho tablet/man hinh rong.

`WorkspaceAppShell` tu dong doi mode dua theo `screenWidthDp >= 840`.

## 4. ViewModel trung tam

### 4.1 `WorkspaceViewModel`

Day la orchestration layer lon nhat cua app Android. No:

- Lang nghe `activeProjectRepository.activeProjectId`.
- Subscribe `ObserveWorkspaceSnapshotUseCase`.
- Giu `WorkspaceState` va `WorkspaceUiState`.
- Dieu phoi import, map filter, progress, note/task, weather, photo, AI va report preview.
- Phat `WorkspaceEffect` de UI mo file export hoac snackbar message.

### 4.2 ViewModel theo feature

- `ProjectViewModel`: project lifecycle, import/export project, chuyen project active.
- `TimelineViewModel`: tong hop progress + daily log + AI summary theo project active.
- `ReportingViewModel`: tao snapshot bao cao, draft AI, export `PDF`, `DOCX`, `ZIP`.
- `PhotoViewModel`: quan ly gallery anh, watermark, tag, offset thoi gian, review anh.
- `DataHubViewModel`, `ProgressHubViewModel`, `GemmaChatViewModel`: gom logic UI cuc bo cho tung hub/man hinh.

## 5. Layering kien truc

Du an dang di theo layering ro rang:

- `app`: compose shell, navigation, orchestration, bridge UI.
- `domain`: model, repository contracts, use case, service contract.
- `data`: Room DB, DAO, repository implementation, migration va scoped DB.
- `project`, `timeline`, `reporting`, `photo`, `gis`, `gis-maplibre`, `storage-*`, `ai-*`: feature/module phu tro.

## 6. Event va dong bo du lieu

`ProjectSyncRepository` cung cap `SharedFlow<ProjectSyncEvent>`.

No duoc dung de:

- Phat thong bao sau import file.
- Phat su kien clone/archive project.
- Bao cho `ProjectViewModel`, `WorkspaceViewModel`, `TimelineViewModel`, `ReportingViewModel`, `PhotoViewModel` refresh lai du lieu khi project hien tai thay doi.

Mo hinh nay giup cac tab khong phai query lai lien tuc, nhung van dong bo sau cac tac vu thay doi du lieu.

## 7. Share intent media

App co intent-filter cho:

- `android.intent.action.SEND`
- `android.intent.action.SEND_MULTIPLE`

Mime type cho phep:

- `image/*`
- `video/*`
- `*/*`

`ShareIntentParser.kt` se:

- Rut `Uri` tu `EXTRA_STREAM` va `ClipData`.
- Chuan hoa mime type.
- Loai bo uri trung lap.
- Chi giu image/video hop le.
- Tao `IncomingSharePayload` de day vao `WorkspaceAppShell`.

## 8. Thanh phan he thong Android khac

### 8.1 WorkManager

- `GemmaModelDownloadWorker`: tai model AI va cap nhat foreground notification.

### 8.2 Widget

- `DiaryWidgetReceiver`
- `DiaryCalendarWidget`

Widget dung de hien nhat ky theo lich thang.

### 8.3 FileProvider

`AndroidManifest.xml` dang ky `FileProvider` voi authority `${applicationId}.fileprovider` de chia se tep export/an toan truy cap file noi bo.

## 9. Manifest va permission can chu y

Ngoai location/camera, manifest con co:

- `INTERNET`
- `RECORD_AUDIO`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `WAKE_LOCK`
- `POST_NOTIFICATIONS`
- `READ_MEDIA_IMAGES`
- `READ_MEDIA_VIDEO`
- `READ_EXTERNAL_STORAGE` cho SDK cu
- `WRITE_EXTERNAL_STORAGE` cho SDK <= 28
- `READ_CALENDAR`
- `WRITE_CALENDAR`

Khong phai permission nao cung duoc xin ngay luc startup. Mot so quyen chi phuc vu feature sau nay va can xem lai khi hardening production.

## 10. Nhan xet quan trong cho team Android

- App dang co 1 shell trung tam rat manh o `WorkspaceViewModel`, nen moi thay doi flow nghiep vu can xem impact lien tab.
- App uu tien local-first va offline-first cho du lieu project va AI.
- Phan project DB dang co logic bridge tu shared DB sang scoped DB; day la diem can doc ky truoc khi sua migration, import hoac export.
