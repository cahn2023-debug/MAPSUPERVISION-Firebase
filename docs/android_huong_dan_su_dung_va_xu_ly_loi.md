# Huong dan su dung va xu ly loi Android

## 1. Checklist truoc khi chay app

- Co `local.properties` hop le neu can signing hoac cac duong dan local.
- Cap quyen `Location` va `Camera`.
- Kiem tra thiet bi con du bo nho neu su dung AI model, xu ly anh/video hoac map lon.
- Dam bao da chon hoac tao `project active` truoc khi import/chup anh/xuat bao cao.

## 2. Cach su dung theo tac vu

## 2.1 Tao du an

1. Mo khu quan ly project.
2. Nhap ten du an.
3. Neu can, chon `customPath`.
4. Tao xong, kiem tra project da duoc set active.

Neu thay message tao thanh cong nhung khong active:

- Mo lai danh sach project.
- Chon thu cong project vua tao.

## 2.2 Nhap file thiet ke

1. Vao tab `DATA`.
2. Chon file:
   - `xlsx/xls`: di qua preview va mapping.
   - `kml/kmz/geojson/json`: di qua field mapping.
   - nhieu file: import hang loat.
3. Xac nhan mapping roi parse.
4. Sau import, qua tab `MAP` de kiem tra node/route da len chua.

## 2.3 Chup anh hien truong

1. Chon node/route lien quan.
2. Bat camera trong app.
3. Chup anh hoac quay video.
4. Kiem tra watermark, GPS, object code va project.
5. Mo danh sach photo neu can gan tag, sua offset thoi gian hoac review AI.

## 2.4 Cap nhat tien do va nhat ky

1. Vao tab `PROGRESS` hoac thao tac ngay tren workspace.
2. Cap nhat khoi luong thuc te.
3. Them nhat ky, note, task neu can.
4. Neu can tong hop nhanh, goi AI summary/task suggestion.

## 2.5 Xuat bao cao

1. Dam bao project active da co du lieu progress, photo, daily log.
2. Vao `REPORTS`.
3. Cho `ReportingViewModel` nap snapshot.
4. Xuat `PDF`, `DOCX` hoac `ZIP`.
5. Neu file khong mo duoc, kiem tra lai `FileProvider` va quyen doc file cua app dich.

## 3. Bang loi thuong gap va cach fix

## 3.1 App dung o man hinh cap quyen

Trieu chung:

- Khong vao duoc workspace.

Nguyen nhan thuong gap:

- Chua cap `Location` hoac `Camera`.

Cach fix:

- Cap du quyen trong system settings.
- Tat/mo lai app sau khi cap.

## 3.2 Import file xong nhung map khong hien node/route

Nguyen nhan thuong gap:

- Mapping sai cot.
- File vao nhom `failed`.
- Project active khong dung.
- Geometry bi replace boi remap file cu.

Cach fix:

- Kiem tra lai `importedFiles` cua project dang active.
- Re-open preview/mapping va doi field.
- Kiem tra xem file dang nam trong `imports/processed` hay `imports/failed`.
- Chuyen sang dung project roi refresh.

## 3.3 Import thanh cong nhung reporting/timeline chua doi

Nguyen nhan thuong gap:

- Event sync chua phat hoac UI chua refresh snapshot moi.

Cach fix:

- Refresh lai workspace/report.
- Chuyen project qua lai de kich refresh.
- Kiem tra logic `ProjectSyncRepository` neu dang debug code.

## 3.4 Chup anh xong khong thay trong gallery photo cua project

Nguyen nhan thuong gap:

- `activeProjectId` chua san sang.
- Save that bai sau watermark/pipeline.
- GPS/location provider tra ve du lieu rong.

Cach fix:

- Dam bao da mo project active truoc khi chup.
- Thu chup lai sau khi app lay duoc vi tri.
- Kiem tra folder storage cua project va log pipeline.

## 3.5 Anh co watermark nhung object code/tag sai

Nguyen nhan thuong gap:

- Chon sai target node/route.
- Tag CSV chua duoc luu lai.
- `matchedNodeCode`/`matchedRouteCode` chua duoc update sau khi doi tag.

Cach fix:

- Mo photo review.
- Sua tag.
- Bam save review de ghi lai `SitePhoto`.

## 3.6 Xuat PDF/DOCX that bai

Nguyen nhan thuong gap:

- Snapshot report chua tai xong.
- Du lieu project rong.
- Loi ghi file vao storage.

Cach fix:

- Doi `isExporting` ket thuc ro rang truoc khi thao tac lai.
- Kiem tra project co daily log/progress/photo hay khong.
- Thu xuat lai sau khi refresh report data.

## 3.7 AI draft/report/timeline summary tra ve rong

Nguyen nhan thuong gap:

- Snapshot qua it du lieu.
- Model chua san sang.
- Request moi bi bo qua vi trung voi request cu.

Cach fix:

- Bo sung du lieu thuc te.
- Kiem tra worker tai model AI.
- Huy draft hien tai roi goi lai.

## 3.8 Share intent tu app ngoai vao nhung khong mo sheet gan media

Nguyen nhan thuong gap:

- Mime type khong phai image/video hop le.
- Uri rong, trung lap, hoac app nguon cap sai permission doc.

Cach fix:

- Thu share lai bang file image/video ro rang.
- Kiem tra app nguon co cap `content://` truy cap hop le hay khong.
- Neu dang debug, xem `ShareIntentParser` co loc bo uri nao khong.

## 3.9 Du lieu project bi lech giua cac tab

Nguyen nhan thuong gap:

- Van de bridge giua shared DB va scoped DB.
- Mot repository doc nham nguon du lieu.

Cach fix:

- Xac nhan `project.projectDbPath` hop le.
- Kiem tra migration/hydration trong `ProjectScopedDatabaseProvider`.
- Sau thay doi schema, test lai import, map, report va photo tren cung mot project.

## 3.10 Chuoi tieng Viet hien thi loi dau

Trieu chung:

- Label/chuoi hien thi thanh ky tu mojibake, vi du trong mot so docs cu va ca `AndroidManifest.xml`.

Nguyen nhan thuong gap:

- File da tung duoc luu sai encoding.

Cach fix:

- Chuan hoa file ve UTF-8.
- Ra soat chuoi hardcode trong manifest/UI.
- Uu tien dua chuoi vao `strings.xml` de giam rui ro.

## 4. Checklist debug cho dev Android

Khi gap bug khong ro nguon, debug theo thu tu:

1. Kiem tra project active dung chua.
2. Kiem tra DB scope dang dung la shared hay project DB.
3. Kiem tra storage file da vao `pending/processed/failed` chua.
4. Kiem tra `ProjectSyncEvent` co duoc phat sau thao tac khong.
5. Kiem tra ViewModel nao dang so huu state hien thi.
6. Neu lien quan bao cao, doi chieu `ReportingSnapshot`.
7. Neu lien quan photo, doi chieu `SitePhoto`, tag codes, matched node/route va offset time.

## 5. Khu vuc nen viet test khi co thay doi

- Import pipeline va remap geometry.
- Scoped DB migration/hydration.
- Share intent parsing.
- Photo pipeline va save dedupe.
- Report snapshot va export.
- Event sync giua project/workspace/report/photo/timeline.
