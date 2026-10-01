package app.daybreak.ui

import androidx.compose.ui.platform.UriHandler

/**
 * Opens [url] in the browser, returning false instead of crashing on a phone without one (or with it disabled):
 * [UriHandler.openUri] throws when no activity can handle it. Callers then show the address inline.
 */
fun UriHandler.tryOpen(url: String): Boolean = runCatching { openUri(url) }.isSuccess
