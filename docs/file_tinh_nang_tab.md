# Tinh nang theo tung tab giao dien

`WorkspaceAppShell` chia ung dung thanh 5 route chinh va mot lop tro ly AI xuyen man hinh. Tai lieu nay tong hop nhanh pham vi tung tab de de onboarding va phan tich blast radius UI.

## 1. `map`

Tab mac dinh va la trung tam nghiep vu hien truong.

Tinh nang chinh:

- hien thi node va route tren ban do
- chon node/route, xem detail card
- zoom, my location, doi base map, do khoang cach
- loc theo contractor, vat tu, tim kiem doi tuong
- mo note/task/anh lien ket voi doi tuong
- mo capture flow va report preview nhanh

Thanh phan lien quan:

- `WorkspaceAppShell.kt`
- `MapHubScreen.kt`
- `GisScreen.kt`
- `GisViewModel.kt`
- `MapBridgeInstaller.kt`

## 2. `progress`

Tab tong hop thi cong va nhat ky.

Tinh nang chinh:

- dashboard tong quan du an
- nhap va cap nhat tien do thi cong
- nhap daily log
- quan ly work category, work plan, task du an
- lay weather auto theo vi tri khi can

Thanh phan lien quan:

- `ProgressHubRoute.kt`
- `ProgressHubScreen.kt`
- `TimelineViewModel.kt`

## 3. `data`

Tab thao tac nhap lieu va du lieu thiet ke.

Tinh nang chinh:

- upload file thiet ke
- preview va mapping Excel
- preview va mapping non-Excel
- retry import loi
- combine imported files
- xoa file import, sua geometry, mo doi tuong len map

Thanh phan lien quan:

- `DataHubRoute.kt`
- `DataHubScreen.kt`
- `ExcelMappingDialog.kt`
- `NonExcelMappingDialog.kt`
- `storage-import/...`

## 4. `reports`

Tab bao cao va artifact.

Tinh nang chinh:

- tao reporting snapshot
- draft noi dung bang AI
- export `PDF`
- export `DOCX`
- export `ZIP` package du an

Thanh phan lien quan:

- `ReportingScreen.kt`
- `ReportingViewModel.kt`
- `PdfReportGenerator.kt`
- `DocxReportGenerator.kt`
- `ProjectPackageService.kt`

## 5. `materials`

Tab vat tu va ho so lien quan.

Tinh nang chinh:

- xem va cap nhat material handover
- theo doi material declaration
- doi chieu tien do vat tu va du lieu thi cong

Thanh phan lien quan:

- `MaterialsHubScreen.kt`
- du lieu vat tu trong `WorkspaceState`
- repository vat tu trong `domain` va `data`

## 6. Floating AI layer

Khong nam trong mot tab rieng, nhung hien dien xuyen suot workspace.

Tinh nang chinh:

- chat theo context project
- note summary
- task suggestion
- draft bao cao
- support mapping/import

Thanh phan lien quan:

- `FloatingChatBubble`
- `GemmaChatViewModel.kt`
- cac module `ai-*`

## 7. Goc nhin blast radius

- Sua `map` de anh huong `photo`, `reporting`, `note/task`.
- Sua `data` de anh huong `map`, `progress`, `database`, `reporting`.
- Sua `progress` de anh huong `timeline`, `reporting`, dashboard.
- Sua `reports` de anh huong `photo`, `daily log`, `material`, `project package`.
- Sua `materials` de anh huong dashboard va bao cao.
