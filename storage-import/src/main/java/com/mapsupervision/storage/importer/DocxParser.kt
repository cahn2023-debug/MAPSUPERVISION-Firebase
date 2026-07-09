package com.mapsupervision.storage.importer

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile

object DocxParser {

    /**
     * Parse a .docx file and return its text content as a plain string, 
     * preserving paragraph breaks.
     */
    fun parse(file: File): String {
        val stringBuilder = java.lang.StringBuilder()
        try {
            ZipFile(file).use { zip ->
                val documentEntry = zip.getEntry("word/document.xml")
                if (documentEntry != null) {
                    zip.getInputStream(documentEntry).use { inputStream ->
                        val factory = XmlPullParserFactory.newInstance()
                        factory.isNamespaceAware = true
                        val parser = factory.newPullParser()
                        parser.setInput(InputStreamReader(inputStream, "UTF-8"))

                        var eventType = parser.eventType
                        var inTextTag = false
                        
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            val tagName = parser.name ?: ""

                            when (eventType) {
                                XmlPullParser.START_TAG -> {
                                    if (tagName == "t") { // <w:t> tag contains text
                                        inTextTag = true
                                    } else if (tagName == "p") { // <w:p> tag is a paragraph
                                        // add a space or newline before a new paragraph if needed
                                        if (stringBuilder.isNotEmpty() && !stringBuilder.endsWith("\n")) {
                                            stringBuilder.append("\n")
                                        }
                                    }
                                }
                                XmlPullParser.TEXT -> {
                                    if (inTextTag) {
                                        stringBuilder.append(parser.text)
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    if (tagName == "t") {
                                        inTextTag = false
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return stringBuilder.toString().trim()
    }
}
