package app.daybreak.domain

import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random

/** An image on Wikimedia, as the feed describes it. */
data class WikiImage(val url: String, val width: Int, val height: Int)

/** One Wikipedia article an "On this day" item links to. */
data class WikiPage(
    /** The readable title ("Thrilla in Manila"). */
    val title: String,
    /** The article's opening paragraph, in plain text. */
    val extract: String,
    /** The article on the mobile site. */
    val url: String,
    /** About 330 px wide; null for an article without a picture. */
    val thumbnail: WikiImage? = null,
    /** The full-size picture (or, for a small one, the same thumbnail). */
    val original: WikiImage? = null,
)

/** One item of Wikipedia's "On this day" feed: what happened in [year] (negative for BC) and the articles it links. */
data class OnThisDayEvent(val year: Int, val text: String, val pages: List<WikiPage>)

/**
 * A pick's picture, always a free one from Wikimedia Commons. [url] is the size the card asks for (see
 * [OnThisDay.heroUrl]) and [fallbackUrl] the feed's own thumbnail in case that size can't be had. [width] and
 * [height] are the original's, which decide how the card shows it; [fromSvg] marks a drawing rendered to PNG (a
 * flag, a seal, a map), which never fills the frame edge to edge.
 */
data class OnThisDayPicture(
    val url: String,
    val fallbackUrl: String?,
    val width: Int,
    val height: Int,
    val fromSvg: Boolean,
) {
    /**
     * A landscape photo fills the card's frame edge to edge; anything else (portraits, squares, flags, seals, maps)
     * is shown whole as a "poster" over a blurred copy of itself, since cropping would cut it badly.
     */
    val fill: Boolean get() = !fromSvg && width >= height * FILL_ASPECT && minOf(width, height) >= FILL_MIN_SIDE

    /** The picture's page on Commons, with its author and licence ("https://commons.wikimedia.org/wiki/File:X.jpg"). */
    val filePage: String? get() = OnThisDay.commonsFilePage(url)

    private companion object {
        const val FILL_ASPECT = 1.15
        const val FILL_MIN_SIDE = 400
    }
}

/**
 * One of the day's picks, ready for the card: the [text] tidied up, the one article it's shown with (its [title]
 * and link), and a [picture] when there's a good free one.
 */
data class OnThisDayPick(
    val year: Int,
    val text: String,
    val title: String,
    val url: String,
    val picture: OnThisDayPicture? = null,
)

/**
 * Picks a few pleasant or interesting moments from Wikipedia's "On this day" feed: grim ones (violence, disasters,
 * deaths, persecution and wars) are dropped, and the rest are scored so that science, culture, sport, space, nature
 * and milestones, firsts, openings and inventions, and anything with a good picture, come first. Everything here
 * is pure and tested.
 */
object OnThisDay {
    /** The card cycles through at most this many a day. */
    const val MAX_PICKS = 5

    /** With fewer survivors than this from the curated list, the full list of events is asked for too. */
    const val MIN_SELECTED = 3

    /**
     * Words that make an item grim, matched case-insensitively as whole words (so "war" and "wars" but never
     * "Warsaw", "battle" but never "Battlestar"). Each ending is listed, so nothing matches by accident. A hyphen
     * counts as a word break, so "post-war" and "anti-war" are grim too: an item about the years after a war, or a
     * protest against one, is still about the war. They're checked in the item's text, in the titles of every
     * article it links ("2017 Las Vegas shooting"), and in the first sentence of the article it's shown with, which
     * says what the subject is ("The Great Boston Fire of 1872 was...") without the life story that follows (a
     * writer "served in World War II", an aircraft "saw combat in the Korean War"). ASCII only.
     */
    private val GRIM = wordsRegex(
        // Violence
        "kill", "kills", "killed", "killing", "killings", "killer", "killers",
        "massacre", "massacres", "massacred",
        "bomb", "bombs", "bombed", "bombing", "bombings", "bomber", "bombers", "atomic bomb",
        "attack", "attacks", "attacked", "attacking",
        "shooting", "shootings", "gunman", "gunmen", "gunfire", "stabbing", "stabbed",
        "executed", "execution", "executions", "beheaded", "hanged", "lynched", "lynching", "crucified", "burned",
        "assassinate", "assassinated", "assassination", "assassinations",
        "murder", "murders", "murdered", "murderer",
        "genocide", "genocides", "pogrom", "pogroms", "terrorist", "terrorists", "terrorism", "hostage", "hostages",
        "hijack", "hijacked", "hijacking", "kidnapped", "kidnapping",
        "riot", "riots", "stampede", "crush", "injured", "injuries", "wounded", "casualties", "fatalities", "victims",
        "dead", "tortured", "persecuted", "deported", "militants",
        // Oppression
        "nazi", "nazis", "holocaust", "fascist", "fascists", "concentration camp", "concentration camps",
        "slavery", "slave", "slaves", "enslaved", "suicide",
        // Disasters
        "disaster", "disasters", "crash", "crashes", "crashed",
        "earthquake", "earthquakes", "tsunami", "tsunamis", "famine", "famines",
        "epidemic", "epidemics", "pandemic", "plague", "explosion", "explosions", "explodes", "exploded",
        "sink", "sinks", "sank", "sunk", "sinking", "shipwreck", "shipwrecks", "shipwrecked",
        "hurricane", "typhoon", "cyclone", "tornado", "landslide", "flood", "floods", "flooding",
        // Weapons
        "nuclear test", "nuclear tests", "detonation", "detonate", "detonates", "detonated",
        // War
        "war", "wars", "warfare", "battle", "battles", "invasion", "invasions", "invade", "invades", "invaded",
        "troops", "coup", "coups", "siege", "besieged", "conquered", "occupation", "annexed", "annexation",
    )

    /** "The Great Fire of London", "the Great Boston Fire": a city's great fire, with or without its name between. */
    private val GREAT_FIRE = Regex("""\bgreat\s+(?:[a-z]+\s+)?fire\b""", RegexOption.IGNORE_CASE)

    /**
     * Death, only in the item's own text: an article often says when its subject died. Not "die", which is as
     * often German ("Die Weissen Blaetter") or a film ("Die Hard").
     */
    private val DEATH = wordsRegex("dies", "died", "dying", "death", "deaths")

    /**
     * Names that would trip the list but aren't grim at all ("Star Wars" opened in cinemas, the "Battle of the
     * Sexes" was a tennis match). Taken out before the list is checked.
     */
    private val HARMLESS = wordsRegex(
        "star wars", "war of the worlds", "battle of the sexes", "cold war ends",
        "dead sea", "grateful dead", "day of the dead",
    )

    /** True when [event] is about violence, disaster, death, persecution or war, and shouldn't be on a cheerful Home. */
    fun isGrim(event: OnThisDayEvent): Boolean {
        val text = event.text.replace(HARMLESS, "")
        if (grim(text) || DEATH.containsMatchIn(text)) return true
        val subject = subjectOf(event)?.extract?.let(::firstSentence).orEmpty()
        return (event.pages.map { it.title } + subject).any { grim(it.replace(HARMLESS, "")) }
    }

    private fun grim(s: String) = GRIM.containsMatchIn(s) || GREAT_FIRE.containsMatchIn(s)

    /** What makes an item fun to read over breakfast, each worth [BOOST] once. */
    private val BOOSTS = listOf(
        // Science and discovery, and inventions
        wordsRegex(
            "discover", "discovers", "discovered", "discovery", "scientist", "scientists", "scientific", "science",
            "telescope", "comet", "planet", "element", "vaccine", "fossil", "dinosaur", "experiment", "laboratory",
            "invent", "invents", "invented", "invention", "patent", "patented", "prototype", "scanner", "computer",
            "nobel", "physics", "chemistry", "meteorite",
        ),
        // Culture
        wordsRegex(
            "film", "films", "movie", "premiere", "premieres", "premiered", "album", "song", "single", "novel",
            "book", "published", "publishes", "opera", "ballet", "museum", "gallery", "painting", "art", "music",
            "musical", "concert", "theatre", "theater", "television", "broadcast", "disney", "comic", "cartoon",
            "flag", "academy award", "oscar",
        ),
        // Sport
        wordsRegex(
            "olympic", "olympics", "world cup", "world series", "championship", "champion", "record", "marathon",
            "match", "tournament", "baseball", "football", "boxing", "tennis", "cricket", "game",
        ),
        // Space and flight
        wordsRegex(
            "space", "spacecraft", "spaceflight", "nasa", "orbit", "orbits", "satellite", "astronaut", "astronauts",
            "rocket", "moon", "lunar", "apollo", "sputnik", "flies", "flight", "sound barrier",
        ),
        // Openings and beginnings
        wordsRegex(
            "open", "opens", "opened", "opening", "inaugurated", "unveiled", "unveils", "debut", "debuts",
            "launch", "launches", "launched", "founded", "railway", "bridge", "zoo", "university",
        ),
        // Nature and milestones
        wordsRegex(
            "national park", "park", "garden", "gardens", "island", "mountain", "summit", "expedition", "lighthouse",
            "canal", "tunnel", "tower", "cathedral", "independence", "independent", "legalised", "legalized",
            "suffrage", "right to vote",
        ),
    )

    /** Firsts are fun, but "first" is a common word, so it's worth a little less than a category. */
    private val FIRST = wordsRegex("first")

    /** Dry politics, law and unrest: fine, but not what the card is for. */
    private val DRY = wordsRegex(
        "treaty", "parliament", "court", "tribunal", "constitution", "constitutional", "legislature", "legislative",
        "government", "political", "president", "minister", "chancellor", "election", "referendum", "party",
        "agency", "levy", "tax", "communist", "military", "sentenced", "abdicate",
        "police", "arrest", "arrested", "protest", "protests", "protesters", "strike", "ruled", "ruling",
        "deputation", "viceroy",
    )

    /** Words in a picture's file name that mean a logo or an emblem rather than a photo or a painting. */
    private val EMBLEM = Regex("""logo|seal|emblem|coat_of_arms""", RegexOption.IGNORE_CASE)

    private const val GOOD_PICTURE = 3
    private const val PLAIN_PICTURE = 1
    private const val BOOST = 2
    private const val FIRST_BOOST = 1
    private const val DRY_PENALTY = 2

    /** At or under this, an item is dropped as long as at least [MIN_SELECTED] better ones are left. */
    private const val LOW_SCORE = 1

    /**
     * How much the card wants [event]: 3 for a good picture (a photo or a painting) or 1 for a logo, seal, emblem
     * or drawing; 2 for each kind of fun (science, culture, sport, space, openings, nature and milestones); 1 for
     * a first; and 2 off for dry politics, law or unrest. Only the item's own text counts for the words.
     */
    fun score(event: OnThisDayEvent): Int {
        val text = event.text
        val picture = pictureOf(event)
        var score = when {
            picture == null -> 0
            picture.fromSvg || EMBLEM.containsMatchIn(fileName(picture.url).orEmpty()) -> PLAIN_PICTURE
            else -> GOOD_PICTURE
        }
        score += BOOSTS.count { it.containsMatchIn(text) } * BOOST
        if (FIRST.containsMatchIn(text)) score += FIRST_BOOST
        if (DRY.containsMatchIn(text)) score -= DRY_PENALTY
        return score
    }

    /**
     * The day's picks, best first, at most [max]:
     * - items without an article to link are dropped, and so are the grim ones;
     * - an item told twice (the same year, and an article in common) is kept once, and one already among [besides]
     *   (the curated list, when this is the full one making it up) not at all;
     * - the rest are ordered by [score], with equal scores ordered by a shuffle seeded with [date], so the same day
     *   always gives the same picks (the card never changes on a relaunch) while the same date next year may lead
     *   with another;
     * - items scoring 1 or less are dropped as long as at least 3 others are left;
     * - no two picks are from the same decade (including those in [taken]) while there are others to choose.
     */
    fun choose(
        events: List<OnThisDayEvent>,
        date: LocalDate,
        max: Int = MAX_PICKS,
        besides: List<OnThisDayEvent> = emptyList(),
        taken: List<OnThisDayPick> = emptyList(),
    ): List<OnThisDayPick> {
        val ranked = events
            .filter { e -> subjectOf(e)?.let { it.title.isNotBlank() && it.url.isNotBlank() } == true } // a card needs a link
            .filterNot(::isGrim)
            .shuffled(Random(date.toEpochDay()))
            .map { it to score(it) }
            .sortedByDescending { it.second } // stable: the shuffle only breaks ties
        val unique = mutableListOf<Pair<OnThisDayEvent, Int>>()
        for (item in ranked) {
            if ((besides + unique.map { it.first }).none { sameMoment(it, item.first) }) unique += item
        }
        while (unique.size > MIN_SELECTED && unique.last().second <= LOW_SCORE) unique.removeAt(unique.lastIndex)
        // One per decade first, then the best of the rest if that leaves too few, after them.
        val decades = taken.map { decade(it.year) }.toMutableSet()
        val spread = unique.filter { decades.add(decade(it.first.year)) }.take(max)
        val extra = unique.filterNot { item -> spread.any { it === item } }.take(max - spread.size)
        return (spread + extra).map { pickOf(it.first) }
    }

    private fun decade(year: Int) = Math.floorDiv(year, 10)

    /** The same moment, told twice: the same year, and an article in common. */
    private fun sameMoment(a: OnThisDayEvent, b: OnThisDayEvent): Boolean =
        a.year == b.year && a.pages.any { p -> b.pages.any { it.title == p.title } }

    /**
     * The article an item is about, for its link and title: the first one whose title (without a "(film)" note)
     * appears, with its capitals, in the text after any "Topic:" lead-in ("Apollo program: NASA launches Apollo 4"
     * links NASA rather than the whole programme); else the first named anywhere; else the first. The feed lists
     * the articles in the order the text links them, so this is the first one the text names. (The longest named
     * title was tried, and picked the broader article too often: "Palo Alto, California" for Stanford's founding.)
     */
    fun subjectOf(event: OnThisDayEvent): WikiPage? {
        val lead = LEAD_IN.find(event.text)?.value.orEmpty()
        val body = event.text.removePrefix(lead)
        fun firstIn(s: String) = event.pages.firstOrNull { name(it).let { n -> n.isNotBlank() && n in s } }
        return firstIn(body) ?: firstIn(event.text) ?: event.pages.firstOrNull()
    }

    private fun name(page: WikiPage) = page.title.replace(DISAMBIGUATION, "")

    private val LEAD_IN = Regex("""^[^.:]{1,60}:\s""")
    private val DISAMBIGUATION = Regex("""\s*\([^()]*\)$""")

    /**
     * The picture an item is shown with: always from Commons (never a non-free poster or logo from English
     * Wikipedia), at least 150 px on its shorter side. A photo or painting from the subject's article first; else
     * one from another of the item's articles, those about the same thing first; else a drawing (a flag, a seal)
     * from the subject, then from the others; else none.
     */
    fun pictureOf(event: OnThisDayEvent): OnThisDayPicture? {
        val subject = subjectOf(event)
        // After the subject, the articles about the same thing ("Allan Hills 84001" for "Allan Hills"), then the rest.
        val others = event.pages.filter { it !== subject }
            .sortedByDescending { p -> subject != null && (name(p) in name(subject) || name(subject) in name(p)) }
        val pages = listOfNotNull(subject) + others
        val pictures = pages.mapNotNull(::pictureOf)
        return pictures.firstOrNull { !it.fromSvg } ?: pictures.firstOrNull()
    }

    /** [page]'s picture if it's a free one, big enough; null otherwise. */
    fun pictureOf(page: WikiPage): OnThisDayPicture? {
        val thumb = page.thumbnail ?: return null
        if (!isCommons(thumb.url)) return null
        val original = page.original ?: thumb
        if (minOf(original.width, original.height) < MIN_PICTURE_SIDE) return null
        val svg = SVG.containsMatchIn(thumb.url)
        val url = heroUrl(thumb, original, svg)
        return OnThisDayPicture(url, thumb.url.takeIf { it != url }, original.width, original.height, svg)
    }

    /** Smaller than this on its shorter side, a picture would only look blurry. */
    private const val MIN_PICTURE_SIDE = 150

    private val SVG = Regex("""\.svg(?:/|\.png|$|\?)""", RegexOption.IGNORE_CASE)
    private val THUMB_WIDTH = Regex("""/(\d+)px-""")
    private val RASTER = Regex("""\.(?:jpe?g|png|webp)(?:\?|$)""", RegexOption.IGNORE_CASE)

    fun isCommons(url: String) = "/wikipedia/commons/" in url

    /**
     * Wikimedia makes thumbnails only at standard widths and answers HTTP 400 for others. Probed in October 2026
     * on thumb.wikimedia.org: 20, 40, 60, 120, 250, 330, 500, 960, 1280, 1920 and 3840 work; 220, 300, 320, 400,
     * 480, 600, 640, 750, 800, 1000, 1024, 1200, 1500 and 2560 don't.
     */
    private val THUMB_STEPS = listOf(3840, 1920, 1280, 960, 500, 330, 250, 120, 60, 40, 20)

    /**
     * The card's frame is the phone's width (about 1,000 px on a typical phone), so 960 px suits it: 1280 works
     * too but costs about two thirds more to download for no difference most phones can show.
     */
    private const val HERO_WIDTH = 960

    /** The largest original shown as itself (over Wikimedia's thumbnail steps). */
    private const val MAX_ORIGINAL = 1280

    /**
     * The picture's address at a size that suits the card's frame:
     * - an original at least 960 px wide: its 960 px thumbnail;
     * - a drawing (SVG) narrower than that: its 500 px rendering, since a drawing has no real size and the
     *   poster frame is about 400 px tall;
     * - a smaller photo (JPEG, PNG or WebP, at most 1280 px): the original itself, every pixel of it;
     * - anything else: the largest standard width no wider than the original;
     * - and the feed's own thumbnail as it is when its address can't be resized.
     */
    fun heroUrl(thumb: WikiImage, original: WikiImage, svg: Boolean): String {
        if (!THUMB_WIDTH.containsMatchIn(thumb.url)) return thumb.url
        fun sized(width: Int) = thumb.url.replace(THUMB_WIDTH, "/${width}px-")
        return when {
            original.width >= HERO_WIDTH -> sized(HERO_WIDTH)
            svg -> sized(500)
            isCommons(original.url) && !THUMB_WIDTH.containsMatchIn(original.url) && RASTER.containsMatchIn(original.url) &&
                original.width <= MAX_ORIGINAL -> original.url
            else -> THUMB_STEPS.firstOrNull { it <= original.width }?.let(::sized) ?: thumb.url
        }
    }

    /**
     * The Commons page of the file at [url], a thumbnail's or the original's address
     * (".../commons/thumb/d/de/X.jpg/960px-X.jpg" or ".../commons/d/de/X.jpg"); null for any other address.
     */
    fun commonsFilePage(url: String): String? = fileName(url)?.takeIf { isCommons(url) }?.let { "https://commons.wikimedia.org/wiki/File:$it" }

    private val FILE = Regex("""/wikipedia/[a-z]+/(?:thumb/)?[0-9a-f]/[0-9a-f]{2}/([^/?#]+)""")

    private fun fileName(url: String): String? = FILE.find(url)?.groupValues?.get(1)

    private fun pickOf(event: OnThisDayEvent): OnThisDayPick {
        val page = subjectOf(event)
        return OnThisDayPick(
            year = event.year,
            text = cleanText(event.text),
            title = page?.title.orEmpty(),
            url = page?.url.orEmpty(),
            picture = pictureOf(event),
        )
    }

    /** Past this the text keeps only its first sentence (or clause), when that alone says enough. */
    private const val LONG_TEXT = 160
    private const val MIN_SENTENCE = 40

    /** Abbreviations that end in a full stop without ending the sentence ("U.S. Congress", "St. Pancras"). */
    private val NOT_AN_END = Regex(
        """(?:\b[A-Z]|\b(?:St|Mt|Dr|Mr|Mrs|Jr|Sr|No|vs|c|ca|Co|Corp|Gen|Lt|Capt|Rev|Prof|Inc|Ltd)|\b[ap]\.m)$""",
    )
    private val PICTURED = Regex("""\s*\([^()]*\bpictured\)""")
    private val SPACES = Regex("""\s+""")
    private val SPACE_BEFORE_STOP = Regex("""\s+([,.;:])""")

    /**
     * The item's text for the card: "(pictured)" notes dropped (the card's picture may be another one), spaces
     * tidied (also before a comma or full stop a dropped note leaves behind), and a very long text cut to its first
     * sentence or clause when that's long enough on its own.
     */
    fun cleanText(text: String): String {
        val tidy = text.replace(PICTURED, "").replace(SPACES, " ").replace(SPACE_BEFORE_STOP, "$1").trim()
        if (tidy.length <= LONG_TEXT) return tidy
        val end = sentenceEnd(tidy, MIN_SENTENCE, clauses = true) ?: return tidy
        return tidy.substring(0, end).trimEnd() + "."
    }

    /** An article's first sentence ("Jerome David Salinger was an American writer..."), for the grim check. */
    fun firstSentence(text: String): String {
        val end = sentenceEnd(text, 0, clauses = false) ?: return text
        return text.substring(0, end + 1)
    }

    /** Where the first sentence (or, with [clauses], clause) of [s] ends, from [from] on; null if it doesn't. */
    private fun sentenceEnd(s: String, from: Int, clauses: Boolean): Int? {
        var i = from
        while (i < s.length - 1) {
            val c = s[i]
            if (s[i + 1] == ' ' && ((clauses && c == ';') || (c == '.' && !NOT_AN_END.containsMatchIn(s.substring(0, i))))) return i
            i++
        }
        return null
    }

    /** "1969", or "331 BC" for the feed's negative years. */
    fun formatYear(year: Int): String = if (year > 0) year.toString() else "${-year} BC"

    /**
     * How long ago [year] was in [today]'s year: "51 years ago", "last year", "this year", or "2,356 years ago" for
     * 331 BC (there was no year 0).
     */
    fun yearsAgo(year: Int, today: LocalDate): String {
        val years = if (year > 0) today.year - year else today.year - year - 1
        return when {
            years <= 0 -> "this year"
            years == 1 -> "last year"
            else -> String.format(Locale.US, "%,d years ago", years)
        }
    }

    /** Plain words and phrases (letters and spaces only) as one case-insensitive whole-word pattern. */
    private fun wordsRegex(vararg words: String): Regex =
        Regex(words.joinToString("|", prefix = """\b(?:""", postfix = """)\b""") { it.replace(" ", """\s+""") }, RegexOption.IGNORE_CASE)
}
