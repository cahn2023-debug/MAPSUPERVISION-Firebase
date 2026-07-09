# Muc luc tai lieu du an MapSupervision

Tai lieu nay dong vai tro "cua vao" cho toan bo thu muc `docs`. Muc tieu la giup team tim nhanh tai lieu theo dung nhu cau: onboarding, kien truc, Android feature, database, AI, build/release va van hanh.

## 1. Tai lieu nen doc dau tien

- `README.md`: chi muc tong hop toan bo thu muc `docs`.
- `tong_hop_du_an_v1.1.md`: ban tom tat phat hanh va hien trang du an.
- `tong_quan_kien_truc_toan_du_an.md`: ban do kien truc tong the cua workspace multi-module.
- `module_matrix_chi_tiet.md`: bang vai tro, dependency, file chinh va muc do test cua tung module.
- `build_kiem_thu_va_release.md`: cach build, test, CI va release gate.

## 2. Tai lieu Android va nghiep vu

- `android_kien_truc_tong_quan.md`: entry point Android, navigation shell, permission, runtime.
- `android_cau_truc_module_va_du_lieu.md`: module Android, Room DB, scoped DB, sync event.
- `android_flow_nghiep_vu.md`: flow khoi dong, project, import, map, media, AI, reporting.
- `android_huong_dan_su_dung_va_xu_ly_loi.md`: checklist su dung va troubleshooting.
- `file_tinh_nang_tab.md`: tong hop tinh nang theo tung tab trong workspace.
- `tab_nhap_lieu_data_hub.md`: tai lieu chi tiet rieng cho DATA Hub.

## 3. Tai lieu kien truc va codebase

- `file_knowledge.md`: mo ta cau truc ma nguon, layering va vai tro cac khoi chinh.
- `tong_quan_kien_truc_toan_du_an.md`: architecture map cap repo.
- `module_matrix_chi_tiet.md`: module matrix cap build va source.
- `adr/0001-gis-bridge-and-module-boundaries.md`: ADR ve GIS bridge va ranh gioi module.

## 4. Tai lieu du lieu va database

- `database.md`: tong hop schema va luong du lieu.
- `file_database.md`: dien giai chi tiet cac bang va y nghia nghiep vu.
- `database_tables.html`: artifact schema HTML de tra cuu nhanh.

## 5. Tai lieu build, release va van hanh

- `build_kiem_thu_va_release.md`: cach build app, chay test, CI, release artifact.
- `release_gate_runbook.md`: tieu chi gate, smoke test va rollback cho release.
- `redundant-loop-cleanup-2026-06-28.md`: ghi chu cleanup ky thuat theo dot.

## 6. Artifact va tai san

- `MapSupervision_v1.1_release.zip`: goi release da dong goi.
- `mapsupervision_logo.png`: logo su dung trong tai lieu va chia se noi bo.
- `files.zip`: goi tep tham chieu cu.

## 7. Thu tu doc de onboarding nhanh

1. Doc `README.md`.
2. Doc `tong_hop_du_an_v1.1.md`.
3. Doc `tong_quan_kien_truc_toan_du_an.md`.
4. Doc `module_matrix_chi_tiet.md`.
5. Neu lam Android feature, doc them bo `android_*.md`.
6. Neu sua import/database, doc them `database.md`, `file_database.md`, `tab_nhap_lieu_data_hub.md`.
7. Truoc khi chuan bi release, doc `build_kiem_thu_va_release.md` va `release_gate_runbook.md`.
