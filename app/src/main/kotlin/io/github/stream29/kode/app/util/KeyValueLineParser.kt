package io.github.stream29.kode.app.util

internal fun parseKeyValueLines(input: String, separator: String): Map<String, String> {
    if (input.isBlank()) {
        return emptyMap()
    }
    return input.lines()
        .asSequence()
        .map(String::trim)
        .filter { line -> line.isNotBlank() && line.contains(separator) }
        .associate { line ->
            val parts = line.split(separator, limit = 2)
            parts[0].trim() to parts[1].trim()
        }
}
