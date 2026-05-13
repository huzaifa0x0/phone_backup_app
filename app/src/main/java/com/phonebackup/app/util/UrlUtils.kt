package com.phonebackup.app.util

fun normalizeServerUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return trimmed
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return if (withScheme.endsWith("/")) withScheme.dropLast(1) else withScheme
}
