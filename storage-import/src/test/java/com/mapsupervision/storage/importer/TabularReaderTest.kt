package com.mapsupervision.storage.importer

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabularReaderTest {

    @Test
    fun test_csv_comma_utf8_with_vietnamese() {
        val csv = """
            STT,Mã vị trí,Tọa độ,Nhà thầu,Loại đối tượng
            1,NODE_DN_01,"16.0678, 108.2208",Công ty Viễn Thông Miền Trung,Node
            2,NODE_DN_02,"16.0712, 108.2245",Công ty Viễn Thông Miền Trung,Node
        """.trimIndent()

        val table = TabularReader.readCsvFromBytes(csv.toByteArray(Charsets.UTF_8))
        assertEquals(listOf("STT", "Mã vị trí", "Tọa độ", "Nhà thầu", "Loại đối tượng"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("NODE_DN_01", table.rows[0][1])
        assertEquals("16.0678, 108.2208", table.rows[0][2])
        assertEquals("Công ty Viễn Thông Miền Trung", table.rows[0][3])
        assertEquals(',', table.detectedDelimiter)
        assertEquals("UTF-8", table.detectedEncoding)
    }

    @Test
    fun test_csv_semicolon_utf8() {
        val csv = """
            Mã vị trí;Vĩ độ;Kinh độ;Nhà thầu
            NODE_HN_01;21.0285;105.8542;Đội thi công Hà Nội
            NODE_HN_02;21.0311;105.8599;Đội thi công Hà Nội
        """.trimIndent()

        val table = TabularReader.readCsvFromBytes(csv.toByteArray(Charsets.UTF_8))
        assertEquals(listOf("Mã vị trí", "Vĩ độ", "Kinh độ", "Nhà thầu"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("NODE_HN_01", table.rows[0][0])
        assertEquals(';', table.detectedDelimiter)
    }

    @Test
    fun test_csv_tab_delimiter() {
        val tsv = "Mã vị trí\tTọa độ\tNhà thầu\nNODE_01\t10.762622, 106.660172\tViettel\nNODE_02\t10.765000, 106.665000\tViettel"
        val table = TabularReader.readCsvFromBytes(tsv.toByteArray(Charsets.UTF_8))
        assertEquals(listOf("Mã vị trí", "Tọa độ", "Nhà thầu"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals('\t', table.detectedDelimiter)
    }

    @Test
    fun test_csv_windows_1258_encoding() {
        val winCharset = listOf("windows-1258", "Cp1258", "x-windows-1258", "CP1258")
            .firstOrNull { runCatching { Charset.isSupported(it) }.getOrDefault(false) }
            ?.let { Charset.forName(it) } ?: Charsets.UTF_8

        // Vietnamese text with Windows-1258 specific chars: Đà Nẵng, Hà Nội, Đồng Nai
        val rawCsv = """
            Mã;Vị trí;Tọa độ;Nhà thầu
            N1;Đà Nẵng;16.0, 108.0;Đơn vị thi công Đồng Nai
            N2;Hà Nội;21.0, 105.8;Tập đoàn Bưu chính
        """.trimIndent()

        val encodedBytes = rawCsv.toByteArray(winCharset)
        val table = TabularReader.readCsvFromBytes(encodedBytes)

        assertEquals(4, table.headers.size)
        assertTrue(table.headers[0].startsWith("M"))
        assertEquals(2, table.rows.size)
        assertEquals("N1", table.rows[0][0])
        assertTrue("Expected decoded text to contain content", table.rows[0][1].isNotBlank())
    }

    @Test
    fun test_csv_leading_notes_and_comments_skipped() {
        val csv = """
            BÁO CÁO KHẢO SÁT TUYẾN CÁP QUANG ĐÀ NẴNG
            Ngày lập: 20/08/2026 - Người thực hiện: Kỹ sư Nguyễn Văn A
            
            STT,Mã vị trí,Vĩ độ,Kinh độ,Nhà thầu,Khối lượng kéo cáp
            1,DN_01,16.0678,108.2208,Nhà thầu Miền Trung,150m
            2,DN_02,16.0712,108.2245,Nhà thầu Miền Trung,200m
        """.trimIndent()

        val table = TabularReader.readCsvFromBytes(csv.toByteArray(Charsets.UTF_8))
        assertEquals(listOf("STT", "Mã vị trí", "Vĩ độ", "Kinh độ", "Nhà thầu", "Khối lượng kéo cáp"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("DN_01", table.rows[0][1])
        assertEquals("150m", table.rows[0][5])
    }

    @Test
    fun test_csv_multiline_and_escaped_quotes() {
        val csv = """
            "Mã đối tượng","Tọa độ","Ghi chú mô tả"
            "NODE_01","10.5, 106.5","Dữ liệu ghi chú có ""dấu ngoặc kép"" và
            xuống dòng nhiều hàng"
            "NODE_02","10.6, 106.6","Ghi chú bình thường"
        """.trimIndent()

        val table = TabularReader.readCsvFromBytes(csv.toByteArray(Charsets.UTF_8))
        assertEquals(listOf("Mã đối tượng", "Tọa độ", "Ghi chú mô tả"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals("NODE_01", table.rows[0][0])
        assertTrue(table.rows[0][2].contains("dấu ngoặc kép"))
        assertTrue(table.rows[0][2].contains("xuống dòng nhiều hàng"))
    }

    @Test
    fun test_xlsx_sheet_selection_and_notes() {
        // Create an in-memory XLSX zip
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStringTable+xml"/>
</Types>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/workbook.xml with 2 sheets: "DanhSachTram" and "TuyenCap"
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="DanhSachTram" sheetId="1" r:id="rId1"/>
    <sheet name="TuyenCap" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/sharedStrings.xml
            val strings = listOf(
                "BẢNG TỔNG HỢP VỊ TRÍ", // 0
                "STT", // 1
                "Mã trạm", // 2
                "Tọa độ", // 3
                "Nhà thầu", // 4
                "TR_01", // 5
                "10.8231, 106.6297", // 6
                "VNPT", // 7
                "TR_02", // 8
                "10.8250, 106.6320", // 9
                "FPT", // 10
                "Mã tuyến", // 11
                "TUYEN_01" // 12
            )
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            val sstXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""")
                for (s in strings) {
                    append("<si><t>$s</t></si>")
                }
                append("</sst>")
            }
            zos.write(sstXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // sheet1.xml (DanhSachTram) has row 1: banner note, row 2: headers, row 3-4: data
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1"><c r="A1" t="s"><v>0</v></c></row>
    <row r="2">
      <c r="A2" t="s"><v>1</v></c>
      <c r="B2" t="s"><v>2</v></c>
      <c r="C2" t="s"><v>3</v></c>
      <c r="D2" t="s"><v>4</v></c>
    </row>
    <row r="3">
      <c r="A3"><v>1</v></c>
      <c r="B3" t="s"><v>5</v></c>
      <c r="C3" t="s"><v>6</v></c>
      <c r="D3" t="s"><v>7</v></c>
    </row>
    <row r="4">
      <c r="A4"><v>2</v></c>
      <c r="B4" t="s"><v>8</v></c>
      <c r="C4" t="s"><v>9</v></c>
      <c r="D4" t="s"><v>10</v></c>
    </row>
  </sheetData>
</worksheet>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // sheet2.xml (TuyenCap)
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
    <row r="1">
      <c r="A1" t="s"><v>11</v></c>
      <c r="B1" t="s"><v>3</v></c>
    </row>
    <row r="2">
      <c r="A2" t="s"><v>12</v></c>
      <c r="B2" t="s"><v>6</v></c>
    </row>
  </sheetData>
</worksheet>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val bytes = baos.toByteArray()
        val tempFile = File.createTempFile("test_multi_", ".xlsx")
        try {
            tempFile.writeBytes(bytes)
            val sheets = TabularReader.listSheets(tempFile, "xlsx")
            assertEquals(listOf("DanhSachTram", "TuyenCap"), sheets)

            val table1 = TabularReader.readTable(tempFile, "DanhSachTram")
            assertEquals(listOf("STT", "Mã trạm", "Tọa độ", "Nhà thầu"), table1.headers)
            assertEquals(2, table1.rows.size)
            assertEquals("TR_01", table1.rows[0][1])

            val table2 = TabularReader.readTable(tempFile, "TuyenCap")
            assertEquals(listOf("Mã tuyến", "Tọa độ"), table2.headers)
            assertEquals(1, table2.rows.size)
            assertEquals("TUYEN_01", table2.rows[0][0])
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun test_xls_biff8_parsing() {
        // Construct a binary BIFF8 stream with BOF, BOUNDSHEET, SST, LABELSST, NUMBER, EOF
        val baos = ByteArrayOutputStream()

        fun writeRecord(type: Int, data: ByteArray) {
            baos.write(type and 0xFF)
            baos.write((type shr 8) and 0xFF)
            baos.write(data.size and 0xFF)
            baos.write((data.size shr 8) and 0xFF)
            baos.write(data)
        }

        // BOF (Workbook Globals): Type 0x0809, Substream 0x0005
        val bofGlobals = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0x0600) // BIFF8
            .putShort(0x0005) // Workbook globals
            .putShort(0x0DBB.toShort())
            .putShort(0x0CC9.toShort())
            .array()
        writeRecord(0x0809, bofGlobals)

        // BOUNDSHEET: pos=0, hidden=0, type=0, name="TramBTS"
        val sheetName = "TramBTS"
        val boundBytes = ByteBuffer.allocate(8 + sheetName.length * 2).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0) // pos (4 bytes)
            .put(0.toByte()) // hidden (1 byte)
            .put(0.toByte()) // type (1 byte)
            .put(sheetName.length.toByte()) // len (1 byte)
            .put(1.toByte()) // unicode (1 byte)
        for (ch in sheetName) boundBytes.putChar(ch)
        writeRecord(0x0085, boundBytes.array())

        // SST: 4 unique strings ("Mã trạm", "Tọa độ", "BTS_01", "10.5, 106.5")
        val sstStrings = listOf("Mã trạm", "Tọa độ", "BTS_01", "10.5, 106.5")
        val sstBaos = ByteArrayOutputStream()
        val sstHeader = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(sstStrings.size)
            .putInt(sstStrings.size)
            .array()
        sstBaos.write(sstHeader)
        for (str in sstStrings) {
            val strHeader = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(str.length.toShort())
                .put(1.toByte()) // Unicode flag
                .array()
            sstBaos.write(strHeader)
            for (c in str) {
                sstBaos.write(c.code and 0xFF)
                sstBaos.write((c.code shr 8) and 0xFF)
            }
        }
        writeRecord(0x00FC, sstBaos.toByteArray())

        // EOF globals
        writeRecord(0x000A, ByteArray(0))

        // BOF Worksheet
        val bofSheet = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0x0600)
            .putShort(0x0010) // Worksheet
            .putShort(0x0DBB.toShort())
            .putShort(0x0CC9.toShort())
            .array()
        writeRecord(0x0809, bofSheet)

        // Row 0: LABELSST (row 0, col 0 -> SST 0 "Mã trạm"), LABELSST (row 0, col 1 -> SST 1 "Tọa độ")
        val label00 = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0) // row 0
            .putShort(0) // col 0
            .putShort(0x0F) // xf
            .putInt(0) // sst 0
            .array()
        writeRecord(0x00FD, label00)

        val label01 = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0) // row 0
            .putShort(1) // col 1
            .putShort(0x0F)
            .putInt(1) // sst 1
            .array()
        writeRecord(0x00FD, label01)

        // Row 1: LABELSST (row 1, col 0 -> SST 2 "BTS_01"), LABELSST (row 1, col 1 -> SST 3 "10.5, 106.5")
        val label10 = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1) // row 1
            .putShort(0) // col 0
            .putShort(0x0F)
            .putInt(2) // sst 2
            .array()
        writeRecord(0x00FD, label10)

        val label11 = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(1) // row 1
            .putShort(1) // col 1
            .putShort(0x0F)
            .putInt(3) // sst 3
            .array()
        writeRecord(0x00FD, label11)

        // EOF Worksheet
        writeRecord(0x000A, ByteArray(0))

        val bytes = baos.toByteArray()
        val table = TabularReader.readTableFromBytes(bytes, "xls")
        assertEquals(listOf("Mã trạm", "Tọa độ"), table.headers)
        assertEquals(1, table.rows.size)
        assertEquals("BTS_01", table.rows[0][0])
        assertEquals("10.5, 106.5", table.rows[0][1])
    }
}
