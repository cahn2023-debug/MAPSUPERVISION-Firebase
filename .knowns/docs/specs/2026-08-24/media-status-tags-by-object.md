---
id: doc-5ebc14fa48a9919bfb9838e46079c885
title: Media Status Tags by Object
description: Specification for single-status tagging, object-scoped media folders, Android filters, reporting filters, and offline Cloud synchronization.
createdAt: '2026-08-24T11:25:40.669Z'
updatedAt: '2026-08-24T11:29:42.329Z'
tags:
  - spec
  - approved
  - android
  - media
  - tags
  - reporting
  - sync
---

## Overview

Bổ sung cơ chế phân loại media theo một thẻ trạng thái trong phần Hình ảnh của ứng dụng Android. Media vẫn thuộc một đối tượng node/tuyến, đồng thời có thể mang tối đa một thẻ trạng thái để tổ chức thư mục, lọc tại đối tượng hiện tại và lọc trong thẻ Báo cáo.

Phạm vi media gồm cả ảnh và video. Bốn thẻ hệ thống là `Hiện trạng`, `Thi công`, `Hoàn trả`, `Vướng mắc`; người dùng có thể bổ sung tag tùy chỉnh dùng chung trong dự án.

## Locked Decisions

- D1: Mỗi media có tối đa một thẻ trạng thái. Người dùng có thể chọn thẻ trước khi chụp/import, đổi hoặc bỏ thẻ sau đó. Media không có thẻ nằm trực tiếp trong thư mục đối tượng; khi thẻ thay đổi, tệp phải được di chuyển vật lý đến đúng thư mục.
- D2: Cấu trúc lưu trữ là `<đối tượng>/<thẻ>/<media>`; media không thẻ dùng `<đối tượng>/<media>`. Bốn thẻ hệ thống là `Hiện trạng`, `Thi công`, `Hoàn trả`, `Vướng mắc`. Tag tùy chỉnh dùng chung cho mọi đối tượng trong cùng dự án, chỉ hỗ trợ thêm và chọn trong phạm vi này, và không được trùng tên sau khi chuẩn hóa hoa/thường cùng khoảng trắng đầu/cuối.
- D3: Cơ chế thẻ và di chuyển thư mục áp dụng cho cả ảnh lẫn video, trên cả bộ nhớ thiết bị và Cloud/Google Drive. Media tồn tại trước khi phát hành tính năng giữ nguyên vị trí cho đến khi người dùng gắn, đổi hoặc bỏ thẻ.
- D4: Trong phần Hình ảnh, thẻ vừa dùng để chọn phân loại khi chụp/import/chỉnh sửa, vừa dùng để lọc media của đối tượng đang chọn. Không bổ sung thẻ `Tất cả` hoặc `Chưa phân loại` tại đây. Lựa chọn thẻ được giữ trong phiên cho đến khi người dùng đổi hoặc bỏ chọn.
- D5: Đổi thẻ tuân theo offline-first: di chuyển local ngay, hiển thị `Chờ đồng bộ`, rồi cập nhật Cloud khi có mạng. Nếu hết lượt thử tự động mà vẫn lỗi, giữ trạng thái local, hiển thị lỗi và cho phép `Thử lại`. Xung đột nhiều thiết bị dùng nguyên tắc thay đổi có thời điểm cập nhật mới nhất thắng; các thiết bị phải hội tụ về cùng thẻ và thư mục.
- D6: Trong thẻ Báo cáo Android, bổ sung dropdown `Tất cả` và toàn bộ thẻ trạng thái ngay bên trái nút `Ẩn/Hiện ảnh`, phía trên danh sách media nhóm theo đối tượng. `Tất cả` bỏ lọc; chọn một thẻ lọc media mang thẻ đó trên toàn dự án nhưng vẫn giữ cách nhóm kết quả theo đối tượng.

## System Decision Impact

- Impact: none
- Decision: Không có System Decision hiện hành nào bị thay đổi. Tính năng tuân theo các pattern offline-first và đồng bộ media hiện có.
- Acceptance gate: Không yêu cầu System Decision mới trước khi phê duyệt spec.

## Requirements

### Functional Requirements

- FR-1: Hệ thống phải duy trì thẻ trạng thái như một thuộc tính riêng của media, không thay thế mã đối tượng hoặc quan hệ node/tuyến hiện có.
- FR-2: Mỗi ảnh hoặc video được phép có không hoặc một thẻ trạng thái; không được có nhiều thẻ trạng thái đồng thời.
- FR-3: Phần Hình ảnh phải hiển thị bốn thẻ hệ thống và các tag tùy chỉnh của dự án, cùng hành động `Thêm tag`.
- FR-4: Tag tùy chỉnh phải dùng chung cho mọi đối tượng trong dự án hiện tại, chỉ hỗ trợ thêm mới và chọn sử dụng; đổi tên và xóa tag nằm ngoài phạm vi.
- FR-5: Tên tag tùy chỉnh phải được cắt khoảng trắng đầu/cuối và kiểm tra trùng không phân biệt hoa/thường trong cùng dự án, bao gồm cả tên bốn thẻ hệ thống.
- FR-6: Người dùng có thể chọn hoặc bỏ chọn thẻ trước khi chụp/import. Lựa chọn hiện tại phải được giữ cho các media tiếp theo trong cùng phiên cho đến khi người dùng thay đổi.
- FR-7: Người dùng có thể đổi hoặc bỏ thẻ của media đã lưu từ giao diện chỉnh sửa.
- FR-8: Media có thẻ phải được lưu dưới `<thư mục đối tượng>/<thư mục thẻ>/<tệp media>`; media không thẻ phải được lưu trực tiếp dưới `<thư mục đối tượng>/<tệp media>`.
- FR-9: Khi thẻ được thêm, đổi hoặc bỏ, hệ thống phải di chuyển tệp local và đồng bộ thao tác tương ứng lên Cloud/Google Drive; metadata thẻ và đường dẫn phải phản ánh cùng một trạng thái logic.
- FR-10: Ảnh và video tồn tại trước tính năng này không được tự động di chuyển trong quá trình nâng cấp hoặc khởi động. Chúng chỉ được di chuyển khi người dùng thực hiện thao tác gắn, đổi hoặc bỏ thẻ.
- FR-11: Trong phần Hình ảnh, chọn một thẻ phải chỉ lọc media của đối tượng đang được chọn. Không hiển thị lựa chọn `Tất cả` hoặc `Chưa phân loại` tại bộ lọc này; media không thẻ vẫn truy cập từ thư mục gốc của đối tượng.
- FR-12: Trong thẻ Báo cáo Android, dropdown phải xuất hiện ngay bên trái nút `Ẩn/Hiện ảnh`, gồm `Tất cả`, bốn thẻ hệ thống và các tag tùy chỉnh của dự án.
- FR-13: Dropdown Báo cáo phải lọc media của tất cả đối tượng trong dự án theo thẻ đã chọn, sau đó tiếp tục nhóm kết quả theo đối tượng như giao diện hiện tại. `Tất cả` phải bao gồm cả media có thẻ và không thẻ.
- FR-14: Thay đổi thẻ phải được ghi nhận và áp dụng local khi offline, có trạng thái `Chờ đồng bộ`, tự thử lại khi có mạng, và chuyển sang trạng thái lỗi có hành động `Thử lại` nếu hết lượt thử tự động.
- FR-15: Khi cùng một media có thay đổi thẻ xung đột từ nhiều thiết bị, thay đổi có thời điểm cập nhật mới nhất phải thắng; metadata và vị trí tệp trên các thiết bị/Cloud phải hội tụ theo thay đổi thắng cuộc.

### Non-Functional Requirements

- NFR-1: Di chuyển media không được làm mất tệp. Sau một thao tác thành công chỉ được có một bản chính tại đường dẫn đích, còn bản nguồn phải được loại bỏ.
- NFR-2: Thao tác mạng và di chuyển Cloud phải chạy nền, không chặn giao diện hoặc giữ khóa cơ sở dữ liệu trong khi gọi mạng.
- NFR-3: Đồng bộ phải giữ nguyên phạm vi dự án, quyền truy cập dự án và cơ chế xác thực hiện có.
- NFR-4: Tên thư mục tag phải sử dụng cơ chế chuẩn hóa tên thư mục an toàn hiện có, đồng thời giao diện vẫn hiển thị đúng nhãn tag người dùng đã tạo.
- NFR-5: Retry nền phải tuân theo chính sách backoff và giới hạn số lần thử hiện có của media worker.

## Acceptance Criteria

- [ ] AC-1: Với một dự án bất kỳ, phần Hình ảnh hiển thị đúng bốn thẻ hệ thống và hành động `Thêm tag`.
- [ ] AC-2: Khi thêm một tag hợp lệ, tag xuất hiện để chọn cho mọi đối tượng trong dự án hiện tại nhưng không xuất hiện ở dự án khác.
- [ ] AC-3: Khi nhập tên tag rỗng hoặc trùng tên sau khi bỏ khoảng trắng và không phân biệt hoa/thường, hệ thống không tạo tag thứ hai và hiển thị phản hồi rõ ràng.
- [ ] AC-4: Khi chụp/import ảnh hoặc video với một thẻ đã chọn, media được gắn đúng một thẻ và nằm trong `<đối tượng>/<thẻ>/` ở local.
- [ ] AC-5: Khi chụp/import mà không chọn thẻ, thao tác vẫn thành công và media nằm trực tiếp trong thư mục đối tượng.
- [ ] AC-6: Khi người dùng chọn một thẻ rồi tạo nhiều media trong cùng phiên, các media tiếp theo dùng thẻ đó cho đến khi người dùng đổi hoặc bỏ chọn.
- [ ] AC-7: Khi đổi thẻ của media đã lưu, tệp được di chuyển khỏi thư mục cũ sang thư mục thẻ mới; metadata và đường dẫn sau thao tác khớp nhau.
- [ ] AC-8: Khi bỏ thẻ của media đã lưu, tệp được di chuyển từ thư mục thẻ về thư mục gốc của đối tượng.
- [ ] AC-9: Các hành vi gắn thẻ, đổi thẻ, bỏ thẻ và di chuyển thư mục hoạt động nhất quán cho cả ảnh và video.
- [ ] AC-10: Sau nâng cấp, media cũ vẫn ở nguyên đường dẫn cho đến khi người dùng gắn, đổi hoặc bỏ thẻ.
- [ ] AC-11: Trong phần Hình ảnh, chọn một thẻ chỉ hiển thị media thuộc thẻ đó của đối tượng đang chọn; không có bộ lọc `Tất cả` hoặc `Chưa phân loại`.
- [ ] AC-12: Trong thẻ Báo cáo Android, dropdown nằm ngay bên trái nút `Ẩn/Hiện ảnh` và chứa `Tất cả`, bốn thẻ hệ thống cùng tag tùy chỉnh của dự án.
- [ ] AC-13: Chọn một thẻ trong Báo cáo lọc đúng media trên toàn dự án và kết quả vẫn nhóm theo đối tượng; chọn `Tất cả` hiển thị cả media có thẻ lẫn không thẻ.
- [ ] AC-14: Khi đổi thẻ lúc offline, local cập nhật ngay và hiển thị `Chờ đồng bộ`; khi mạng trở lại, Cloud/Google Drive được cập nhật và trạng thái chờ biến mất.
- [ ] AC-15: Khi đồng bộ Cloud thất bại hết lượt thử, media local vẫn sử dụng được, giao diện hiển thị lỗi và hành động `Thử lại` có thể khởi động lại đồng bộ.
- [ ] AC-16: Khi hai thiết bị đổi thẻ cùng media, sau đồng bộ cả hai thiết bị và Cloud dùng thay đổi mới nhất, không mất media và không còn bản chính ở thư mục thẻ thua cuộc.
- [ ] AC-17: Sau mỗi lần di chuyển thành công, tệp nguồn không còn tồn tại, tệp đích đọc được, và bản ghi media trỏ tới đường dẫn đích.

## Scenarios

### Scenario 1: Chụp media có thẻ

**Given** người dùng đang ở một đối tượng và đã chọn thẻ `Thi công`  
**When** người dùng chụp ảnh hoặc video  
**Then** media được lưu vào thư mục `<đối tượng>/Thi công/`, mang thẻ `Thi công`, và lựa chọn này được giữ cho lần chụp tiếp theo trong phiên.

### Scenario 2: Chụp media không thẻ

**Given** người dùng đang ở một đối tượng và không chọn thẻ  
**When** người dùng chụp hoặc import media  
**Then** thao tác vẫn thành công và media nằm trực tiếp trong thư mục đối tượng.

### Scenario 3: Tạo tag dùng chung trong dự án

**Given** dự án chưa có tag tùy chỉnh `Nghiệm thu`  
**When** người dùng chọn `Thêm tag` và nhập `Nghiệm thu`  
**Then** tag có thể được chọn cho mọi đối tượng trong dự án, xuất hiện trong dropdown Báo cáo, và không xuất hiện ở dự án khác.

### Scenario 4: Đổi thẻ khi offline

**Given** media đang ở `<đối tượng>/Hiện trạng/` và thiết bị không có mạng  
**When** người dùng đổi thẻ sang `Hoàn trả`  
**Then** tệp local chuyển ngay sang `<đối tượng>/Hoàn trả/`, giao diện hiển thị `Chờ đồng bộ`, và Cloud tự hội tụ khi có mạng.

### Scenario 5: Media cũ

**Given** một media được tạo trước khi tính năng thẻ trạng thái tồn tại  
**When** ứng dụng được nâng cấp và media chưa được chỉnh thẻ  
**Then** media giữ nguyên đường dẫn cũ; chỉ khi người dùng gắn hoặc đổi thẻ thì hệ thống mới di chuyển nó.

### Scenario 6: Lọc trong phần Hình ảnh

**Given** đối tượng A đang được chọn và có media thuộc nhiều thẻ  
**When** người dùng chọn thẻ `Vướng mắc`  
**Then** chỉ media `Vướng mắc` của đối tượng A được hiển thị, không lấy media của đối tượng khác.

### Scenario 7: Lọc trong thẻ Báo cáo

**Given** nhiều đối tượng trong dự án có media mang thẻ `Thi công`  
**When** người dùng chọn `Thi công` trong dropdown cạnh nút `Ẩn/Hiện ảnh`  
**Then** báo cáo hiển thị media `Thi công` của toàn dự án và vẫn chia danh sách theo từng đối tượng.

### Scenario 8: Xung đột nhiều thiết bị

**Given** hai thiết bị đổi thẻ của cùng một media khi chưa nhận thay đổi của nhau  
**When** cả hai thay đổi được đồng bộ  
**Then** thay đổi có thời điểm cập nhật mới nhất thắng và mọi bản sao hội tụ về cùng metadata cùng thư mục mà không mất tệp.

## Technical Notes

- Tái sử dụng quan hệ media với object code và cấu trúc Node/Route hiện có; thẻ trạng thái không được ghi đè danh sách mã node/tuyến đang dùng cho đối chiếu.
- Tuân theo pattern offline-first tại @memory/k957f0: ghi local và xếp hàng đồng bộ trước, xử lý Cloud nền khi có mạng.
- Tuân theo giới hạn worker tại @memory/592gua: không giữ khóa database qua network call và dùng retry/backoff có giới hạn.
- Việc tạo đường dẫn phải tái sử dụng cơ chế sanitize tên thư mục hiện có cho cả local và Cloud.
- Triển khai phải xác định một định danh media ổn định độc lập với đường dẫn để thao tác move và giải quyết xung đột không làm đổi danh tính media.

- @task-yuh98l [media-status-tags-by-object-01] Lưu catalog và trạng thái thẻ media — todo
- @task-7qsrje [media-status-tags-by-object-02] Di chuyển và đồng bộ media theo thẻ — todo
- @task-f6csl2 [media-status-tags-by-object-03] Phân loại và lọc media trong phần Hình ảnh — todo
- @task-qs2pyx [media-status-tags-by-object-04] Lọc media theo thẻ trong Báo cáo — todo

## Open Questions

- Không còn câu hỏi mở.
