# Release gate runbook

Tai lieu nay mo ta release gate cap repo cho `MapSupervision`, khong chi rieng DATA Hub. Noi dung duoc dong bo voi `scripts/release_gate.sh` va workflow CI hien tai.

## 1. Muc tieu

- Chan release khi test quan trong, lint hoac boundary check chua dat.
- Dam bao tai lieu van hanh bat buoc van ton tai.
- Giam rui ro khi thay doi import, database, workspace shell, reporting va storage.

## 2. Gate bat buoc

Chi duoc xem la san sang release khi tat ca buoc sau xanh:

- `:app:testDebugUnitTest`
- `:storage-import:testDebugUnitTest`
- `:data:testDebugUnitTest`
- `lint`
- `assembleDebug`
- `enforceModuleBoundaries`

Va cac file bat buoc phai ton tai:

- `docs/release_gate_runbook.md`
- `docs/tab_nhap_lieu_data_hub.md`
- `production-ready-roadmap.md`

## 3. Lenh gate

```bash
sh ./scripts/release_gate.sh
```

Noi dung script hien tai:

1. chay 3 nhom unit test
2. chay `lint`, `assembleDebug`, `enforceModuleBoundaries`
3. verify su ton tai cua tai lieu va roadmap

## 4. Truoc khi release

1. Dong bo code moi nhat.
2. Xac nhan khong co thay doi ngoai pham vi release.
3. Chay gate tong hop.
4. Neu co sua import/database:
   - kiem tra migration
   - kiem tra lai file mau Excel va non-Excel
5. Neu co sua workspace shell/map/progress/report:
   - smoke test luong chinh tren thiet bi/emulator

## 5. Smoke test toi thieu

- Mo app va vao workspace thanh cong.
- Chuyen duoc project active.
- Mo tab `data` va import 1 file mau.
- Xac nhan map, dashboard va imported files duoc cap nhat.
- Mo tab `reports` va tao duoc preview/export.
- Neu co media flow lien quan, test them capture hoac share intent.

## 6. Tinh huong can canh giac

- migration hoac bridge shared DB/scoped DB
- remap import lam thay doi geometry
- thay doi `WorkspaceViewModel` lam lech state lien tab
- sua package/export service anh huong ZIP va artifact bao cao
- doi dependency module co nguy co vi pham boundary

## 7. Rollback

Neu release gap su co:

1. dung phat hanh ban loi
2. xac dinh commit/tag on dinh gan nhat
3. rollback code ve moc on dinh
4. neu loi nam o migration/storage:
   - danh gia kha nang khoi phuc tu backup
   - kiem tra scoped DB cua project bi anh huong
5. chay lai smoke test toi thieu truoc khi phat hanh lai

## 8. Goi y tai lieu doc kem

- `build_kiem_thu_va_release.md`
- `tong_hop_du_an_v1.1.md`
- `tab_nhap_lieu_data_hub.md`
