---
id: doc-91779fad2cc654c757a0a6e7e401003f
title: Video Minimap Movement Tracking
description: Specification for live movement marker, route polyline, adaptive zoom, and timeline video stamping in video-mode minimap
createdAt: '2026-08-24T04:52:18.295Z'
updatedAt: '2026-08-24T04:52:18.295Z'
tags:
  - spec
  - draft
  - camera
  - minimap
  - video
---

## Overview

Bổ sung hành vi theo dõi hành trình cho minimap ở chế độ video trong CameraOverlay. Trong cùng một phiên mở màn hình camera, minimap live phải theo sát vị trí GPS hiện tại, vẽ đường di chuyển và tự chọn mức zoom phù hợp. Khi video được đóng dấu, minimap và đường di chuyển tại từng thời điểm phải xuất hiện tương ứng trong video đầu ra.

## Locked Decisions

- D1: Tính năng áp dụng đồng thời cho minimap live trên màn hình camera và minimap được đóng dấu vào video xuất ra.
- D2: Đường di chuyển được tích lũy từ lúc mở màn hình camera; các đoạn quay trong cùng phiên dùng chung hành trình.
- D3: Khi vị trí thay đổi, minimap tự chọn mức zoom theo phạm vi vị trí và đường di chuyển để người dùng và đường đi vẫn quan sát được.
- D4: Khi GPS tạm thời không có dữ liệu, giữ marker và đường đi cuối cùng, tạm dừng thêm điểm; khi GPS có lại thì tiếp tục.

## System Decision Impact

- Impact: existing
- Decision: Không tạo thay đổi hệ thống mới; giữ CameraX HUD, pipeline đóng dấu video và hợp đồng vị trí hiện có.
- Acceptance gate: Unit tests và kiểm tra video phải chứng minh marker, polyline và viewport được đồng bộ giữa preview và video output.

## Requirements

### Functional Requirements

- FR-1: Khi minimap video đang bật và nhận được vị trí hợp lệ mới, biểu tượng vị trí phải chuyển tới tọa độ mới trên minimap live.
- FR-2: Minimap live phải vẽ polyline nối các điểm vị trí hợp lệ theo thứ tự thời gian, bắt đầu từ vị trí đầu tiên nhận được sau khi mở màn hình camera.
- FR-3: Polyline phải tiếp tục tồn tại khi người dùng dừng một đoạn quay và bắt đầu đoạn quay khác trong cùng phiên camera.
- FR-4: Khi video đang quay, mỗi mẫu đóng dấu theo timeline phải chứa vị trí, polyline hành trình và viewport/zoom tương ứng với thời điểm mẫu; video xuất ra phải thể hiện marker và polyline thay đổi theo hành trình.
- FR-5: Khi vị trí thay đổi, viewport minimap phải tự điều chỉnh tâm và mức zoom để bao quát marker hiện tại cùng polyline hành trình trong vùng minimap; không được cắt mất marker hiện tại.
- FR-6: Việc điều chỉnh zoom phải phản ánh phạm vi đường đi hiện có, không dùng một mức zoom cố định cho mọi khoảng cách di chuyển.
- FR-7: Khi GPS tạm thời không có dữ liệu hoặc snapshot không có tọa độ hợp lệ, giữ marker, polyline, tile/map frame và zoom cuối cùng; không thêm điểm giả hoặc đoạn nối giả.
- FR-8: Khi GPS có dữ liệu hợp lệ trở lại, marker, polyline và viewport tiếp tục cập nhật từ điểm mới mà không xóa hành trình trước đó.
- FR-9: Nếu chưa từng nhận được tọa độ hợp lệ trong phiên, minimap không hiển thị marker/polyline giả và video vẫn giữ hành vi thiếu vị trí hiện có.
- FR-10: Hành trình chỉ thuộc phiên CameraOverlay hiện tại; khi đóng màn hình camera và mở phiên mới, polyline bắt đầu lại từ rỗng.

### Non-Functional Requirements

- NFR-1: Cập nhật minimap không được chặn luồng ghi hình hoặc luồng UI; việc tải tile/geocode và dựng frame phải thực hiện theo cơ chế bất đồng bộ hiện có.
- NFR-2: Không làm thay đổi dữ liệu GIS thiết kế hoặc lịch sử vị trí bền vững; hành trình của minimap là state tạm thời của phiên camera.
- NFR-3: Giữ tương thích với cơ chế đóng dấu video theo timeline và các module boundary hiện có.

## Acceptance Criteria

- [ ] AC-1: Với chuỗi GPS hợp lệ A -> B -> C trong cùng phiên camera, marker live lần lượt nằm tại A, B, C và polyline chứa đúng các điểm theo thứ tự.
- [ ] AC-2: Khi thay đổi khoảng cách A -> B làm mở rộng phạm vi hành trình, minimap đổi tâm/zoom để vẫn nhìn thấy marker B và toàn bộ polyline trong khung.
- [ ] AC-3: Sau khi dừng đoạn quay thứ nhất và quay đoạn thứ hai trong cùng phiên, đoạn thứ hai vẫn hiển thị polyline bắt đầu từ hành trình của đoạn thứ nhất.
- [ ] AC-4: Video có ít nhất hai mẫu timeline ở các vị trí khác nhau hiển thị marker và polyline tương ứng theo thời điểm phát, không chỉ dùng minimap của mẫu đầu tiên.
- [ ] AC-5: Khi một hoặc nhiều lần đọc GPS trả về snapshot không có tọa độ, marker/polyline/zoom cuối cùng vẫn được giữ và không có điểm mới được thêm.
- [ ] AC-6: Sau khi GPS hợp lệ trở lại, điểm mới được nối vào polyline cũ và marker/viewport cập nhật theo điểm đó.
- [ ] AC-7: Phiên camera mới không kế thừa polyline của phiên trước.
- [ ] AC-8: Có unit tests cho việc tích lũy điểm, bỏ qua snapshot không hợp lệ, tiếp tục sau khi GPS khôi phục và tính viewport theo phạm vi hành trình.
- [ ] AC-9: Các kiểm thử hiện có của CameraOverlay, PhotoStampRenderer và video timeline vẫn vượt qua.

## Scenarios

### Scenario 1: Di chuyển trong một phiên camera

**Given** người dùng mở CameraOverlay ở chế độ video và nhận vị trí A  
**When** vị trí thay đổi lần lượt tới B và C  
**Then** marker live di chuyển theo A -> B -> C, polyline nối đúng thứ tự, và viewport tự điều chỉnh để bao quát hành trình.

### Scenario 2: Nhiều đoạn video trong cùng phiên

**Given** người dùng đã mở camera, đã di chuyển từ A tới B và dừng đoạn quay thứ nhất  
**When** người dùng di chuyển tới C và quay đoạn thứ hai  
**Then** minimap của đoạn thứ hai tiếp tục hiển thị đường A -> B -> C; timeline của đoạn thứ hai chỉ phản ánh các mẫu trong thời gian đoạn đó.

### Scenario 3: GPS tạm thời mất tín hiệu

**Given** marker và polyline đang ở trạng thái hợp lệ tại B  
**When** provider trả về snapshot không có tọa độ  
**Then** minimap giữ trạng thái tại B, không thêm điểm mới và không nối đường giả.

### Scenario 4: GPS khôi phục

**Given** minimap đang giữ trạng thái cuối tại B do mất GPS  
**When** provider trả về tọa độ hợp lệ C  
**Then** marker chuyển tới C, polyline nối B -> C và viewport được tính lại theo hành trình.

### Scenario 5: Đóng dấu video theo timeline

**Given** video có các mẫu vị trí A, B, C theo thời gian  
**When** pipeline xuất video đã đóng dấu  
**Then** minimap tại từng khung/thời điểm thể hiện marker và polyline tương ứng với mẫu gần nhất không vượt quá thời điểm đó.

## Technical Notes

- Có thể mở rộng dữ liệu map scene/timeline hiện có để mang polyline hành trình riêng với các route GIS thiết kế; không được ghi đè route thiết kế.
- Preview và video phải dùng cùng quy tắc lọc điểm hợp lệ, xây polyline và tính viewport để tránh sai khác hình ảnh.
- Cần xử lý quyền sở hữu/copy bitmap đúng vòng đời để không recycle bitmap đang được preview hoặc timeline sử dụng.

## Task Links

Chưa tạo task. Sau khi spec được duyệt, dùng `/kn-plan --from @doc/specs/2026-08-24/video-minimap-movement-tracking` hoặc `/kn-flow @doc/specs/2026-08-24/video-minimap-movement-tracking`.

## Open Questions

Không còn câu hỏi mở.
