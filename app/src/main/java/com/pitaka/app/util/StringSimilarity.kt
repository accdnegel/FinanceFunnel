package com.pitaka.app.util

private fun levenshtein(a: String, b: String): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) {
                dp[i - 1][j - 1]
            } else {
                1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
    }
    return dp[a.length][b.length]
}

private fun similarity(a: String, b: String): Double {
    val (x, y) = a.lowercase() to b.lowercase()
    if (x.isEmpty() || y.isEmpty()) return 0.0
    val distance = levenshtein(x, y)
    val maxLen = maxOf(x.length, y.length)
    return 1.0 - (distance.toDouble() / maxLen)
}

/**
 * Finds the closest existing category to what the user is typing, if any exist that
 * are similar-but-not-identical. Returns null if there's an exact match already,
 * or nothing similar enough.
 */
fun findSimilarCategory(input: String, existing: List<String>, threshold: Double = 0.72): String? {
    val trimmed = input.trim()
    if (trimmed.length < 3) return null

    val exactMatch = existing.any { it.equals(trimmed, ignoreCase = true) }
    if (exactMatch) return null

    return existing
        .map { it to similarity(trimmed, it) }
        .filter { it.second >= threshold }
        .maxByOrNull { it.second }
        ?.first
}
