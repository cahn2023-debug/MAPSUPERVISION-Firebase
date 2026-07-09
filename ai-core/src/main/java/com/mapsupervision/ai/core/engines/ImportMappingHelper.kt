package com.mapsupervision.ai.core.engines

import com.mapsupervision.ai.core.ImportMappingResult

object ImportMappingHelper {
    fun normalize(text: String): String {
        val temp = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(temp).replaceAll("")
            .replace('\u0111', 'd')
            .replace('\u0110', 'd')
            .lowercase(java.util.Locale.US).trim()
    }

    fun suggestMapping(headers: List<String>): ImportMappingResult {
        var nodeCode = ""
        var lat = ""
        var lon = ""
        var contractor = ""
        val items = ArrayList<String>()

        for (h in headers) {
            val hn = normalize(h)
            val isContractorKeyword = hn.contains("contractor") || hn.contains("nha thau") || hn.contains("don vi") || hn.contains("team") || hn.contains("to thi cong") || hn.contains("doi thi cong")
            val isCoordinateKeyword = hn == "lat" || hn == "latitude" || hn.contains("vi do") || hn == "y" ||
                hn == "lon" || hn == "lng" || hn == "longitude" || hn.contains("kinh do") || hn == "x"

            if (nodeCode.isEmpty() && !isContractorKeyword && !isCoordinateKeyword && (
                    hn.contains("code") || hn.contains("ma") || hn.contains("name") || hn.contains("ten") ||
                    hn.contains("node") || hn.contains("tram") || hn.contains("vi tri") || hn.contains("position") ||
                    hn.contains("placemark") || hn.contains("nut") || hn.contains("tuyen") || hn.contains("doi tuong")
                )) {
                nodeCode = h
            } else if (lat.isEmpty() && (hn == "lat" || hn == "latitude" || hn.contains("vi do") || hn == "y")) {
                lat = h
            } else if (lon.isEmpty() && (hn == "lon" || hn == "lng" || hn == "longitude" || hn.contains("kinh do") || hn == "x")) {
                lon = h
            } else if (contractor.isEmpty() && isContractorKeyword) {
                contractor = h
            } else if (hn.contains("vat tu") || hn.contains("khoi luong") || hn.contains("qty") || hn.contains("cap") ||
                hn.contains("may") || hn.contains("thiet bi") || hn.contains("camera") || hn.contains("tu") ||
                hn.contains("dao") || hn.contains("dat") || hn.contains("be tong") || hn.contains("item") ||
                hn.contains("hang muc") || hn.contains("work")) {
                items.add(h)
            }
        }

        val requiresReview = nodeCode.isEmpty() || (lat.isEmpty() && lon.isEmpty())
        return ImportMappingResult(
            nodeCodeColumn = nodeCode,
            latitudeColumn = lat,
            longitudeColumn = lon,
            contractorColumn = contractor,
            itemColumns = items,
            requiresManualReview = requiresReview
        )
    }
}
