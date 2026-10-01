package app.daybreak.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.daybreak.data.ImageLoader
import app.daybreak.data.ImageRequest
import app.daybreak.domain.OnThisDay
import app.daybreak.domain.OnThisDayPick
import app.daybreak.domain.OnThisDayPicture
import java.time.LocalDate

/**
 * One cheerful moment from [today]'s date in history: its picture (when there's a good free one) across the top,
 * "1975 · 51 years ago", what happened, and the article's title as the link. Tapping the card opens the article;
 * "Another" (when the day has more than one pick) moves on to the next, sliding it in. The footer credits
 * Wikipedia and, with a picture, links the picture's page on Commons for its author and licence. Without
 * [images] (or a picture) the card is words only. [next] is the pick "Another" would show, whose picture is
 * fetched once this one's is in.
 */
@Composable
fun OnThisDayCard(
    pick: OnThisDayPick,
    today: LocalDate,
    onAnother: (() -> Unit)?,
    modifier: Modifier = Modifier,
    images: ImageLoader? = null,
    next: OnThisDayPick? = null,
) {
    val uriHandler = LocalUriHandler.current
    // The address to show when there's no browser to open it in.
    var unopened by remember { mutableStateOf<String?>(null) }
    val year = OnThisDay.formatYear(pick.year)
    val ago = OnThisDay.yearsAgo(pick.year, today)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val slot = with(density) { HeroSlot(IntSize(maxWidth.roundToPx(), HeroHeight.roundToPx()), PosterPadding.roundToPx()) }
        if (images != null) {
            LaunchedEffect(pick, next, slot) {
                // This one first (shared with the picture's own load), then the next, so "Another" shows it at once.
                pick.picture?.let { images.load(slot.request(it)) }
                next?.picture?.let { images.load(slot.request(it)) }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .clickable(onClickLabel = "Read on Wikipedia") { unopened = pick.url.takeUnless { uriHandler.tryOpen(it) } }
                // One stop for TalkBack (the words inside are cleared), read again when "Another" changes it.
                .semantics {
                    contentDescription = "$year, $ago. ${pick.text} ${pick.title}, on Wikipedia."
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            AnimatedContent(
                targetState = pick,
                transitionSpec = { fadeIn(tween(250)) + slideInHorizontally { it / 8 } togetherWith fadeOut(tween(150)) },
                label = "On this day pick",
            ) { shown ->
                Column {
                    shown.picture?.let { Hero(it, images, slot) }
                    Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp).clearAndSetSemantics {}) {
                        YearLine(OnThisDay.formatYear(shown.year), OnThisDay.yearsAgo(shown.year, today))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            shown.text,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = if (LocalDensity.current.fontScale >= 1.3f) 7 else 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                shown.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(4.dp))
                            // 16dp at the normal font size, growing with the title at a larger one.
                            val arrow = with(LocalDensity.current) { 16.sp.toDp() }
                            Icon(OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(arrow))
                        }
                    }
                }
            }
            unopened?.let {
                Text(
                    "Couldn't open a browser. On another device, visit ${it.removePrefix("https://")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                )
            }
            Footer(pick.picture?.filePage, onAnother, onOpen = { url -> unopened = url.takeUnless { uriHandler.tryOpen(it) } })
        }
    }
}

/** "1975 · 51 years ago": the year as the card's lead, how long ago quieter. */
@Composable
private fun YearLine(year: String, ago: String) {
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Text(
        buildAnnotatedString {
            withStyle(type.titleMedium.toSpanStyle().copy(color = colors.onSurface)) { append(year) }
            // Kept together: at a large font it goes under the year whole, not "136 years" and then "ago".
            withStyle(type.bodyMedium.toSpanStyle().copy(color = colors.onSurfaceVariant)) { append("\u00A0· ${ago.replace(' ', '\u00A0')}") }
        },
    )
}

/**
 * "From Wikipedia · CC BY-SA" (spoken as part of the card), "Picture" for the picture's page on Commons when
 * there's one, and "Another" at the end of the line. At a large font the credit has a line of its own, and the two
 * buttons share the one under it, so neither is squeezed.
 */
@Composable
private fun Footer(filePage: String?, onAnother: (() -> Unit)?, onOpen: (String) -> Unit) {
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val credit = @Composable { modifier: Modifier ->
        Text(
            "From Wikipedia · CC\u00A0BY-SA",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.semantics { contentDescription = "From Wikipedia, licensed CC BY-SA" },
        )
    }
    val buttons: @Composable RowScope.() -> Unit = {
        if (filePage != null) {
            TextButton(
                { onOpen(filePage) },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.semantics { contentDescription = "Picture's source on Wikimedia Commons" },
            ) {
                Text("Picture", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.weight(1f))
        if (onAnother != null) {
            TextButton(onAnother, Modifier.semantics { contentDescription = "Another moment from this day" }) {
                Text("Another")
            }
        }
    }
    if (stacked) {
        credit(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
        // The Picture button's own padding lines its word up with the credit's.
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            buttons()
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            credit(Modifier.padding(end = 4.dp))
            buttons()
        }
    }
}

private val HeroHeight = 180.dp
private val PosterPadding = 16.dp

/** The hero's size in pixels, and the poster's padding: what a picture is decoded for. */
private data class HeroSlot(val size: IntSize, val padding: Int) {
    /** A landscape photo covers the slot; anything else fits inside it, within the padding. */
    fun request(picture: OnThisDayPicture): ImageRequest =
        if (picture.fill) ImageRequest(picture.url, picture.fallbackUrl, size.width, size.height, fit = false)
        else ImageRequest(picture.url, picture.fallbackUrl, size.width - 2 * padding, size.height - 2 * padding, fit = true)
}

/**
 * Whether the poster's backdrop is blurred with a render effect (Android 12 and up) rather than drawn from a tiny
 * copy of the picture scaled up. Screenshot tests set it, since their renderer doesn't apply render effects.
 */
internal val LocalBlurBackdrop = staticCompositionLocalOf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

/**
 * The picture across the top of the card, 180dp tall at any width, so nothing moves when it lands: the slot is in
 * the skeleton tint until the picture fades in over 300 ms (one already in memory shows at once; one that can't
 * be had leaves the tint). A landscape photo fills it, top-biased so heads aren't cut off; anything else is a
 * "poster": shown whole, framed, over a blurred and dimmed copy of itself. Decorative: the card says what it's about.
 */
@Composable
private fun Hero(picture: OnThisDayPicture, images: ImageLoader?, slot: HeroSlot) {
    val request = remember(picture, slot) { slot.request(picture) }
    val initial = remember(request) { images?.cached(request) }
    var bitmap by remember(request) { mutableStateOf(initial) }
    val alpha = remember(request) { Animatable(if (initial != null) 1f else 0f) }
    LaunchedEffect(request, images) {
        if (bitmap == null && images != null) bitmap = images.load(request)
        if (bitmap != null) alpha.animateTo(1f, tween(durationMillis = 300))
    }
    Box(
        Modifier.fillMaxWidth().height(HeroHeight).background(MaterialTheme.weatherColors.skeleton).clearAndSetSemantics {},
    ) {
        val loaded = bitmap ?: return@Box
        val image = remember(loaded) { loaded.asImageBitmap() }
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }) {
            if (picture.fill) {
                Image(image, null, Modifier.fillMaxSize(), alignment = BiasAlignment(0f, -0.5f), contentScale = ContentScale.Crop)
            } else {
                Poster(loaded, image)
            }
        }
    }
}

@Composable
private fun Poster(bitmap: Bitmap, image: ImageBitmap) {
    if (LocalBlurBackdrop.current) {
        Image(image, null, Modifier.fillMaxSize().blur(24.dp), contentScale = ContentScale.Crop)
    } else {
        // A blur without a render effect: a 64 px copy, box-blurred in software.
        val tiny = remember(bitmap) { softBackdrop(bitmap).asImageBitmap() }
        Image(tiny, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
    }
    val scrim = if (MaterialTheme.isDark) 0.55f else 0.40f
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = scrim)))
    Box(Modifier.fillMaxSize().padding(PosterPadding), contentAlignment = Alignment.Center) {
        val frame = RoundedCornerShape(8.dp)
        Image(
            image,
            null,
            Modifier
                .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .clip(frame)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, frame),
            contentScale = ContentScale.Fit,
        )
    }
}

/** [bitmap] shrunk to [width] px across and blurred, for a poster's backdrop where render effects aren't there. */
private fun softBackdrop(bitmap: Bitmap, width: Int = 64): Bitmap {
    val w = minOf(width, bitmap.width)
    val h = maxOf(1, w * bitmap.height / bitmap.width)
    val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
    val pixels = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
    repeat(3) { boxBlur(pixels, w, h, radius = 4) } // three box blurs come close to a Gaussian
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

/**
 * Blurs ARGB [pixels] ([width] x [height]) in place: each channel averaged over [radius] pixels either side,
 * across then down, with the edges repeated.
 */
internal fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int) {
    fun pass(count: Int, length: Int, index: (Int, Int) -> Int) {
        val line = IntArray(length)
        for (n in 0 until count) {
            for (i in 0 until length) line[i] = pixels[index(n, i)]
            for (i in 0 until length) {
                var a = 0; var r = 0; var g = 0; var b = 0
                for (k in -radius..radius) {
                    val c = line[(i + k).coerceIn(0, length - 1)]
                    a += c ushr 24; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                }
                val d = 2 * radius + 1
                pixels[index(n, i)] = ((a / d) shl 24) or ((r / d) shl 16) or ((g / d) shl 8) or (b / d)
            }
        }
    }
    pass(height, width) { y, x -> y * width + x }
    pass(width, height) { x, y -> y * width + x }
}

/** Material's "open in new" arrow (not in the core icon set), for the link to the article. */
private val OpenInNew: ImageVector by lazy {
    ImageVector.Builder("OpenInNew", 24.dp, 24.dp, 24f, 24f, autoMirror = true).addPath(
        addPathNodes(
            "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7z" +
                "M14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}
