package com.mapsupervision.app.workspace

import com.mapsupervision.domain.model.GisNode
import com.mapsupervision.domain.util.Haversine
import kotlin.math.floor

internal class WorkspaceSpatialIndex private constructor(
    private val bucketSizeDegrees: Double,
    private val buckets: Map<Long, List<GisNode>>
) {
    fun nearestNode(latitude: Double, longitude: Double, radiusMeters: Double): GisNode? {
        val targetBucketLat = floor(latitude / bucketSizeDegrees).toInt()
        val targetBucketLon = floor(longitude / bucketSizeDegrees).toInt()
        var bestNode: GisNode? = null
        var bestDistance = radiusMeters

        for (latBucket in targetBucketLat - 1..targetBucketLat + 1) {
            for (lonBucket in targetBucketLon - 1..targetBucketLon + 1) {
                buckets[bucketKey(latBucket, lonBucket)].orEmpty().forEach { node ->
                    val distance = Haversine.distanceInMeters(
                        latitude,
                        longitude,
                        node.latitude,
                        node.longitude
                    )
                    if (distance <= bestDistance) {
                        bestDistance = distance
                        bestNode = node
                    }
                }
            }
        }
        return bestNode
    }

    companion object {
        fun from(nodes: List<GisNode>, bucketSizeDegrees: Double = 0.01): WorkspaceSpatialIndex {
            val buckets = LinkedHashMap<Long, MutableList<GisNode>>(nodes.size * 2 + 1)
            nodes.forEach { node ->
                if (node.latitude !in -90.0..90.0 || node.longitude !in -180.0..180.0) return@forEach
                val latBucket = floor(node.latitude / bucketSizeDegrees).toInt()
                val lonBucket = floor(node.longitude / bucketSizeDegrees).toInt()
                buckets.getOrPut(bucketKey(latBucket, lonBucket)) { mutableListOf() } += node
            }
            return WorkspaceSpatialIndex(
                bucketSizeDegrees = bucketSizeDegrees,
                buckets = buckets
            )
        }

        private fun bucketKey(latBucket: Int, lonBucket: Int): Long =
            (latBucket.toLong() shl 32) xor (lonBucket.toLong() and 0xffffffffL)
    }

    private fun bucketKey(latBucket: Int, lonBucket: Int): Long =
        Companion.bucketKey(latBucket, lonBucket)
}
