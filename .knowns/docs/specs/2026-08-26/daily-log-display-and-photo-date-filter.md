---
id: doc-ecd39b0f22f2e5c64c922d8a85122b6b
title: Daily Log Display And Photo Date Filter
description: Specification for filtering diary photos by date and making daily log location optional
createdAt: '2026-08-26T06:56:09.348Z'
updatedAt: '2026-08-26T06:56:09.348Z'
tags:
  - spec
  - approved
  - progress
  - daily-log
---

# Specification: Daily Log Immediate Display & Photo Date Filtering

## Overview

Tai lieu dac ta ky thuat cho viec chuan hoa va toi uu hien thi tai man hinh Nhat ky (Progress & Daily Log Hub) tren ung dung Android:
1. Loc anh nhat ky theo ngay duoc chon: Khu vuc Anh nhat ky gan nhat va Anh doi chieu chi hien thi cac anh duoc chup trong ngay dang chon tren lich (photosForSelectedDate), thay vi lay toan bo anh cua moi ngay trong du an. An hoan toan khoi anh nay neu ngay duoc chon khong co anh chup nao.
2. Luu va hien thi nhat ky tuc thoi: Cho phep nguoi dung ghi nhat ky tu do/toan cong truong (khong bat buoc chon vi tri/tuyen). Khi nguoi dung bam Luu nhat ky tu Form hoac bam Xac nhan tu Tro ly AI (Gemma Assistant), ban ghi nhat ky duoc luu ngay vao Room DB, lich tu dong chon dung ngay cua nhat ky do va hien thi ngay lap tuc trong danh sach NHAT KY NGAY... va the TONG HOP NHAT KY TRONG NGAY.

## Locked Decisions

- **D1 (Loc anh nhat ky theo ngay):** Muc Anh nhat ky gan nhat va Anh doi chieu chi loc va hien thi danh sach anh chup dung theo ngay duoc chon (photosForSelectedDate). Neu ngay duoc chon khong co anh chup nao (photosForSelectedDate.isEmpty()), an hoan toan 2 the nay.
- **D2 (Vi tri thi cong la tuy chon):** Cho phep ghi nhat ky chung ma khong bat buoc lien ket vi tri/tuyen (vi tri la khong bat buoc nhu giao dien ghi chu). Loai bo dieu kien chan luu nhat ky khi nodeCode hoac routeCode null/rong trong WorkspaceMapProgressActions.kt.
- **D3 (Tu dong chuyen ngay & hien thi tuc thoi):** Khi luu thanh cong nhat ky (tu form hoac khi xac nhan tu Tro ly AI Gemma), he thong tu dong chon dung ngay cua nhat ky do tren Lich va hien thi tuc thoi trong danh sach nhat ky va tong hop ngay.

## System Decision Impact

- **Impact:** none
- **Acceptance gate:** Giao dien Compose cap nhat chinh xac, khong con hien thi anh sai ngay va moi hanh dong ghi nhat ky duoc phan anh tuc thi.

## Requirements

### Functional Requirements
- **FR-1:** Sua logic hien thi anh trong tab Nhat ky (ProgressHubScreen.kt): Thay the photos.take(8) bang photosForSelectedDate.take(8). Bao boc khoi Anh doi chieu va Anh nhat ky gan nhat trong dieu kien if (photosForSelectedDate.isNotEmpty()).
- **FR-2:** Sua logic luu nhat ky trong WorkspaceMapProgressActions.kt: Cho phep luu DailyLog khi nodeCode == null va routeCode == null. Khong chan hoac nem loi khi khong co vi tri lien ket.
- **FR-3:** Dam bao tinh nhat quan giua AI Chat (Gemma Assistant) va man hinh Nhat ky: Khi xac nhan hanh dong ChatActionType.ADD_DAILY_LOG, ngay ghi nhat ky (dateEpochDay) duoc truyen chinh xac.

### Non-Functional Requirements
- **NFR-1 (UI/UX):** Tuan thu Design System (Glassmorphic, Dark theme, bang mau cam/xanh mint chuan, khong dung mau tim).
- **NFR-2 (Clean Code & Testability):** Giu ma nguon ngan gon, co unit test kiem tra logic loc anh va luu nhat ky.

## Acceptance Criteria

- [x] **AC-1:** Khi chon bat ky ngay nao tren Lich trong tab Nhat ky, danh sach Anh nhat ky gan nhat chi chua cac anh co ngay chup trung voi ngay duoc chon.
- [x] **AC-2:** Neu ngay duoc chon khong co anh chup nao, the Anh nhat ky gan nhat va Anh doi chieu tu dong an hoan toan khoi man hinh.
- [x] **AC-3:** Nguoi dung nhap cong viec va de trong vi tri diem nut -> Bam Luu nhat ky & dong bo tien do -> Nhat ky duoc luu vao DB thanh cong va xuat hien ngay trong danh sach nhat ky cua ngay do.
- [x] **AC-4:** Khi nguoi dung gui lenh cho Tro ly AI Gemma va bam nut Xac nhan trong the cho -> Nhat ky duoc them vao DB, Lich tu dong chuyen den ngay cua nhat ky va hien thi ngay tren man hinh.
- [x] **AC-5:** The TONG HOP NHAT KY TRONG NGAY tinh toan dung tong nhan cong, vi tri thi cong, va khoi luong luy ke theo ngay dang chon.
