package com.mapsupervision.domain.model

sealed interface Geometry {
    data class Point(
        val latitude: Double,
        val longitude: Double
    ) : Geometry

    data class Line(
        val points: List<Pair<Double, Double>>
    ) : Geometry

    data class Polygon(
        val rings: List<List<Pair<Double, Double>>>
    ) : Geometry

    data class MultiLine(
        val lines: List<List<Pair<Double, Double>>>
    ) : Geometry

    data class MultiPolygon(
        val polygons: List<List<List<Pair<Double, Double>>>>
    ) : Geometry
}

