package com.mapsupervision.ai.agent

import com.mapsupervision.ai.core.*
import com.mapsupervision.ai.prompt.ChatActionParser
import com.mapsupervision.ai.prompt.DailyLogCanonicalizer
import com.mapsupervision.domain.model.TaskStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatActionParserTest {
    @Test
    fun `parses construction progress with accents and no accents`() {
        val accented = ChatActionParser.parse("cập nhật tiến độ node N01 planned 100 actual 80", selectedNodeCode = null)
        val plain = ChatActionParser.parse("cap nhat tien do node N01 planned 100 actual 80", selectedNodeCode = null)

        assertNotNull(accented.pendingAction)
        assertNotNull(plain.pendingAction)
        assertEquals(ChatActionType.UPDATE_CONSTRUCTION_PROGRESS, accented.pendingAction?.type)
        assertEquals(ChatActionType.UPDATE_CONSTRUCTION_PROGRESS, plain.pendingAction?.type)
        assertEquals("N01", accented.pendingAction?.constructionProgress?.nodeCode)
        assertEquals("N01", plain.pendingAction?.constructionProgress?.nodeCode)
    }

    @Test
    fun `parses daily log with accents and no accents`() {
        val accented = ChatActionParser.parse("thêm nhật ký node N01 công việc xây lắp nhân lực 5", selectedNodeCode = null)
        val plain = ChatActionParser.parse("them nhat ky node N01 cong viec xay lap manpower 5", selectedNodeCode = null)

        assertNotNull(accented.pendingAction)
        assertNotNull(plain.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, accented.pendingAction?.type)
        assertEquals(ChatActionType.ADD_DAILY_LOG, plain.pendingAction?.type)
    }

    @Test
    fun `parses construction progress draft`() {
        val result = ChatActionParser.parse("cap nhat node A planned 100 actual 65", selectedNodeCode = null)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.UPDATE_CONSTRUCTION_PROGRESS, result.pendingAction?.type)
        assertEquals("A", result.pendingAction?.constructionProgress?.nodeCode)
        assertEquals(100f, result.pendingAction?.constructionProgress?.planned)
        assertEquals(65f, result.pendingAction?.constructionProgress?.actual)
    }

    @Test
    fun `parses daily log draft`() {
        val result = ChatActionParser.parse("them nhat ky node B work xay lap manpower 12", selectedNodeCode = null)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, result.pendingAction?.type)
        assertEquals("B", result.pendingAction?.dailyLog?.nodeCode)
        assertEquals(12, result.pendingAction?.dailyLog?.manpower)
    }

    @Test
    fun `parses daily log with route and explicit date`() {
        val response = buildString {
            append("Nhật ký đã sẵn sàng.\n")
            append("[ACTION: ADD_DAILY_LOG workItem=\"thi cong\" manpower=8 note=\"khoi luong 12m3\" weather=\"Nang\" ")
            append("temperature=32 nodeCode=\"N01\" routeCode=\"R01\" date=\"10/06/2026\" volume=12 unit=\"m3\" categoryName=\"Be tong\"]")
        }

        val result = ChatActionParser.parseLlmResponse(response)

        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, result.pendingAction?.type)
        assertEquals("N01", result.pendingAction?.dailyLog?.nodeCode)
        assertEquals("R01", result.pendingAction?.dailyLog?.routeCode)
        assertEquals(LocalDate.of(2026, 6, 10).toEpochDay(), result.pendingAction?.dailyLog?.dateEpochDay)
        assertEquals(12.0, result.pendingAction?.dailyLog?.volume ?: 0.0, 0.001)
    }

    @Test
    fun `parses route only daily log draft as pending action`() {
        val response = "[ACTION: ADD_DAILY_LOG workItem=\"bao tri\" manpower=4 note=\"kiem tra tuyen\" weather=\"Mua\" temperature=28 routeCode=\"RT-02\" date=\"2026-06-11\" volume=0 unit=\"\" categoryName=\"\"]"
        val result = ChatActionParser.parseLlmResponse(response)

        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, result.pendingAction?.type)
        assertNull(result.pendingAction?.dailyLog?.nodeCode)
        assertEquals("RT-02", result.pendingAction?.dailyLog?.routeCode)
    }

    @Test
    fun `parses llm response with selected route context`() {
        val response = "[ACTION: ADD_DAILY_LOG workItem=\"kiem tra\" manpower=3 note=\"route aware\" weather=\"Nang\" temperature=31 volume=0 unit=\"\" categoryName=\"\"]"
        val result = ChatActionParser.parseLlmResponse(
            response,
            selectedRouteCode = "R-17"
        )

        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, result.pendingAction?.type)
        assertEquals("R-17", result.pendingAction?.dailyLog?.routeCode)
    }

    @Test
    fun `parses natural language note draft`() {
        val result = ChatActionParser.parse("ghi chu node C3 can kiem tra lai moc cot")
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_NOTE, result.pendingAction?.type)
    }

    @Test
    fun `parses natural language task draft`() {
        val result = ChatActionParser.parse("tao nhiem vu node D4 bo sung vat tu")
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_TASK, result.pendingAction?.type)
    }

    @Test
    fun `missing fields does not create pending action`() {
        val result = ChatActionParser.parse("cap nhat node A", selectedNodeCode = null)
        assertNull(result.pendingAction)
    }

    @Test
    fun `parses llm response with update construction progress tag`() {
        val response = "Mình đã cập nhật tiến độ trạm A.\n[ACTION: UPDATE_CONSTRUCTION_PROGRESS nodeCode=\"A\" planned=100.0 actual=85.0]"
        val result = ChatActionParser.parseLlmResponse(response)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.UPDATE_CONSTRUCTION_PROGRESS, result.pendingAction?.type)
        assertEquals("A", result.pendingAction?.constructionProgress?.nodeCode)
        assertEquals(85f, result.pendingAction?.constructionProgress?.actual)
        assertEquals("Mình đã cập nhật tiến độ trạm A.", result.answer)
    }

    @Test
    fun `parses llm response with add daily log tag`() {
        val response = "Nhật ký thi công đã sẵn sàng.\n[ACTION: ADD_DAILY_LOG workItem=\"xây lắp\" manpower=5 note=\"hoàn thành móng\" nodeCode=\"B\"]"
        val result = ChatActionParser.parseLlmResponse(response)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, result.pendingAction?.type)
        assertEquals("B", result.pendingAction?.dailyLog?.nodeCode)
        assertEquals(5, result.pendingAction?.dailyLog?.manpower)
        assertEquals("xây lắp", result.pendingAction?.dailyLog?.workItem)
        assertEquals("Nhật ký thi công đã sẵn sàng.", result.answer)
    }

    @Test
    fun `parses llm response with update site photo tag`() {
        val response = "Đã cập nhật ảnh.\n[ACTION: UPDATE_SITE_PHOTO photoId=\"img_001\" tagCodesCsv=\"CHECKED\" matchedNodeCode=\"NodeA\" latitude=10.7 longitude=106.6]"
        val result = ChatActionParser.parseLlmResponse(response)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.UPDATE_SITE_PHOTO, result.pendingAction?.type)
        assertEquals("img_001", result.pendingAction?.sitePhotoUpdate?.photoId)
        assertEquals("CHECKED", result.pendingAction?.sitePhotoUpdate?.tagCodesCsv)
        assertEquals("NodeA", result.pendingAction?.sitePhotoUpdate?.matchedNodeCode)
        assertEquals(10.7, result.pendingAction?.sitePhotoUpdate?.latitude ?: 0.0, 0.001)
        assertEquals("Đã cập nhật ảnh.", result.answer)
    }

    @Test
    fun `parses llm response with save report draft tag`() {
        val response = "Báo cáo giám sát.\n[ACTION: SAVE_REPORT_DRAFT projectId=\"PRJ001\" title=\"Báo cáo số 1\" executiveSummary=\"Tốt\" riskSection=\"Không\" recommendedActions=\"Họp giao ban|Bổ sung nhân sự\"]"
        val result = ChatActionParser.parseLlmResponse(response)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.SAVE_REPORT_DRAFT, result.pendingAction?.type)
        assertEquals("PRJ001", result.pendingAction?.reportDraftSave?.projectId)
        assertEquals("Báo cáo số 1", result.pendingAction?.reportDraftSave?.title)
        assertEquals("Tốt", result.pendingAction?.reportDraftSave?.executiveSummary)
        assertEquals("Không", result.pendingAction?.reportDraftSave?.riskSection)
        assertEquals(listOf("Họp giao ban", "Bổ sung nhân sự"), result.pendingAction?.reportDraftSave?.recommendedActions)
        assertEquals("Báo cáo giám sát.", result.answer)
    }

    @Test
    fun `parses llm response with add note tag`() {
        val response = "Ghi chú đã sẵn sàng.\n[ACTION: ADD_NOTE objectCode=\"A1\" content=\"Kiểm tra lại mặt bằng\"]"
        val result = ChatActionParser.parseLlmResponse(response)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_NOTE, result.pendingAction?.type)
        assertEquals("A1", result.pendingAction?.noteDraft?.objectCode)
        assertEquals("Kiểm tra lại mặt bằng", result.pendingAction?.noteDraft?.content)
    }

    @Test
    fun `parses llm response with add task tag`() {
        val response = "Nhiệm vụ đã sẵn sàng.\n[ACTION: ADD_TASK objectCode=\"B2\" title=\"Bổ sung vật tư\" description=\"Gấp\" status=IN_PROGRESS]"
        val result = ChatActionParser.parseLlmResponse(response)
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_TASK, result.pendingAction?.type)
        assertEquals("B2", result.pendingAction?.taskDraft?.objectCode)
        assertEquals("Bổ sung vật tư", result.pendingAction?.taskDraft?.title)
        assertEquals("Gấp", result.pendingAction?.taskDraft?.description)
        assertEquals("IN_PROGRESS", result.pendingAction?.taskDraft?.status)
    }

    @Test
    fun `canonicalizes daily log category and unit from normalization context`() {
        val params = mapOf(
            "workItem" to "Daily log",
            "manpower" to "3",
            "note" to "Concrete work",
            "categoryName" to "be tong",
            "routeCode" to "R01"
        )

        val draft = DailyLogCanonicalizer.canonicalize(
            params = params,
            message = "be tong",
            normalizationContext = "work_categories=Be tong:m3,Cap:m,San lap:m2",
            selectedNodeCode = "N01",
            selectedRouteCode = "R01"
        )

        assertEquals("Be tong", draft.categoryName)
        assertEquals("m3", draft.unit)
        assertEquals("N01", draft.nodeCode)
        assertEquals("R01", draft.routeCode)
        assertEquals(3, draft.manpower)
    }

    @Test
    fun `canonicalizes cable category to meter unit`() {
        val params = mapOf(
            "workItem" to "Nhật ký thi công",
            "manpower" to "2",
            "note" to "Kéo cáp trục chính",
            "categoryName" to "cáp"
        )

        val draft = DailyLogCanonicalizer.canonicalize(
            params = params,
            message = "cáp",
            normalizationContext = "work_categories=Be tong:m3,Cap:m",
            selectedNodeCode = null
        )

        assertEquals("Cap", draft.categoryName)
        assertEquals("m", draft.unit)
    }

    @Test
    fun `test missing non-critical fields returns confirmation status with helpful text`() {
        val context = "node_codes=N-1"
        // 1. Missing only planned (non-critical) -> should be REQUIRE_CONFIRMATION
        val result1 = ChatActionParser.parse(
            message = "cap nhat tien do node N-1 thuc te 80",
            normalizationContext = context
        )
        assertEquals(WriteDisposition.REQUIRE_CONFIRMATION, result1.writeDisposition)
        assertNotNull(result1.pendingAction)
        assertTrue(result1.answer.contains("thiếu thông tin: kế hoạch"))

        // 2. Missing node code (critical) -> should be REJECT
        val result2 = ChatActionParser.parse(
            message = "cap nhat tien do thuc te 80 ke hoach 100",
            normalizationContext = context
        )
        assertEquals(WriteDisposition.REJECT, result2.writeDisposition)
        assertNull(result2.pendingAction)
        assertTrue(result2.answer.contains("cung cấp thêm: trạm/node"))
    }

    @Test
    fun `parses Vietnamese percentage and xong keywords`() {
        val context = "node_codes=N14"
        val result1 = ChatActionParser.parse(
            message = "nút 14 xong kéo cáp 80%",
            normalizationContext = context
        )
        assertNotNull(result1.pendingAction)
        assertEquals(ChatActionType.UPDATE_CONSTRUCTION_PROGRESS, result1.pendingAction?.type)
        assertEquals("N14", result1.pendingAction?.constructionProgress?.nodeCode)
        assertEquals(80f, result1.pendingAction?.constructionProgress?.actual)

        val result2 = ChatActionParser.parse(
            message = "nút 14 hoàn thành",
            normalizationContext = context
        )
        assertNotNull(result2.pendingAction)
        assertEquals(ChatActionType.UPDATE_CONSTRUCTION_PROGRESS, result2.pendingAction?.type)
        assertEquals(100f, result2.pendingAction?.constructionProgress?.actual)
    }

    @Test
    fun `parses generate summary action`() {
        val result = ChatActionParser.parse("tổng hợp theo nhà thầu tuần này")
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.GENERATE_SUMMARY, result.pendingAction?.type)
        assertEquals("contractor", result.pendingAction?.summaryRequest?.scope)
        assertEquals("contractor", result.pendingAction?.summaryRequest?.groupBy)
        assertNotNull(result.pendingAction?.summaryRequest?.dateFromEpochDay)
    }

    @Test
    fun `parses node code with Vietnamese node labels`() {
        val context = "node_codes=HG01"
        val result = ChatActionParser.parse(
            message = "ghi nhật ký hôm qua hố ga HG01 công việc xây lắp",
            normalizationContext = context
        )
        assertNotNull(result.pendingAction)
        assertEquals(ChatActionType.ADD_DAILY_LOG, result.pendingAction?.type)
        assertEquals("HG01", result.pendingAction?.dailyLog?.nodeCode)
    }
}

