package com.mapsupervision.domain.util

object StringSimilarity {
    /**
     * Calculates the Levenshtein distance between two strings.
     */
    fun levenshtein(s1: String, s2: String): Int {
        val a = s1.trim().lowercase()
        val b = s2.trim().lowercase()
        
        val costs = IntArray(b.length + 1)
        for (j in costs.indices) {
            costs[j] = j
        }
        for (i in 1..a.length) {
            costs[0] = i
            var nw = i - 1
            for (j in 1..b.length) {
                val cj = Math.min(
                    1 + Math.min(costs[j], costs[j - 1]),
                    if (a[i - 1] == b[j - 1]) nw else nw + 1
                )
                nw = costs[j]
                costs[j] = cj
            }
        }
        return costs[b.length]
    }

    /**
     * Returns a similarity score between 0.0 and 1.0 based on Levenshtein distance.
     * 1.0 means exactly identical (ignoring case and whitespace).
     */
    fun similarityScore(s1: String, s2: String): Double {
        val a = s1.trim().lowercase()
        val b = s2.trim().lowercase()
        if (a == b) return 1.0
        val maxLen = Math.max(a.length, b.length)
        if (maxLen == 0) return 1.0
        val distance = levenshtein(a, b)
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    /**
     * Helper to determine if two names are effectively the same entity
     * (e.g. "Công ty ABC" vs "Cong ty abc", or minor spelling errors).
     */
    fun isSimilar(s1: String, s2: String, threshold: Double = 0.8): Boolean {
        return similarityScore(s1, s2) >= threshold
    }
}
