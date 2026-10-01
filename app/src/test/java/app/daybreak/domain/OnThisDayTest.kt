package app.daybreak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnThisDayTest {
    /** A Commons photo, 3264 x 2448 originally, as the feed gives it. */
    private fun photo(name: String = "X.jpg", width: Int = 3264, height: Int = 2448) = Pictures(
        WikiImage("https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/$name/330px-$name", 330, 330 * height / width),
        WikiImage("https://upload.wikimedia.org/wikipedia/commons/a/ab/$name", width, height),
    )

    /** A Commons drawing rendered to PNG (the original's size is the SVG's nominal one). */
    private fun drawing(name: String = "Flag.svg", width: Int = 600, height: Int = 300) = Pictures(
        WikiImage("https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cd/$name/330px-$name.png", 330, 330 * height / width),
        WikiImage("https://thumb.wikimedia.org/wikipedia/commons/thumb/c/cd/$name/250px-$name.png", width, height),
    )

    /** A non-free picture from English Wikipedia (a film poster, a logo). */
    private fun nonFree(name: String = "Poster.jpg") = Pictures(
        WikiImage("https://upload.wikimedia.org/wikipedia/en/e/ef/$name", 250, 375),
        WikiImage("https://upload.wikimedia.org/wikipedia/en/e/ef/$name", 250, 375),
    )

    private data class Pictures(val thumbnail: WikiImage, val original: WikiImage)

    private fun page(title: String = "Page", extract: String = "", pictures: Pictures? = photo()) =
        WikiPage(title, extract, "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}", pictures?.thumbnail, pictures?.original)

    private fun event(text: String, year: Int = 1969, vararg pages: WikiPage = arrayOf(page())) =
        OnThisDayEvent(year, text, pages.toList())

    private val date = LocalDate.of(2026, 10, 1)

    // --- The grim filter --------------------------------------------------------------------

    @Test fun `violence, disasters, death, persecution and war are grim, whatever the case`() {
        listOf(
            "Twelve people were killed in the attack.",
            "The massacre at the village.",
            "A bombing in the city centre.",
            "Rebels attack the capital.",
            "A mass shooting at a festival.",
            "The king was executed.",
            "The king was beheaded.",
            "Two men were hanged at dawn.",
            "The bishop is burned at the stake.",
            "A pogrom in the city.",
            "The abolition of slavery is debated.",
            "The ship carried enslaved people.",
            "The president is assassinated.",
            "A murder trial begins.",
            "The genocide began.",
            "A terrorist group claims it.",
            "Hostages were freed after a week.",
            "The king dies at the age of 80.",
            "The composer died in Vienna.",
            "The death of the emperor.",
            "An aide is found dead on a rooftop.",
            "Forty fatalities were reported.",
            "The victims are remembered.",
            "Dissidents are persecuted and tortured.",
            "Families are deported to a concentration camp.",
            "A fascist government takes power.",
            "The worst disaster in the country's history.",
            "A plane crash in the mountains.",
            "An EARTHQUAKE strikes the coast.",
            "A tsunami hits the islands.",
            "Famine spreads across the region.",
            "A cholera epidemic breaks out.",
            "An explosion at the factory.",
            "The ferry sinks off the coast.",
            "The shipwreck was found.",
            "The Great Fire of London begins.",
            "The Great Chicago Fire breaks out.",
            "The first atomic bomb is ready.",
            "A nuclear test in the desert.",
            "The country detonates its first device.",
            "War is declared.",
            "The Battle of Hastings.",
            "The invasion of Normandy.",
            "Troops enter the city.",
            "A military coup topples the government.",
        ).forEach { assertTrue(it, OnThisDay.isGrim(event(it))) }
    }

    @Test fun `war inside a hyphenated word is still about a war`() {
        // A hyphen is a word break, deliberately: the years after a war, or a march against one, are still about it.
        listOf(
            "The post-war constitution is adopted.",
            "An anti-war march fills the capital.",
            "War-time rationing ends.",
            "The pre-war borders are restored.",
        ).forEach { assertTrue(it, OnThisDay.isGrim(event(it))) }
    }

    @Test fun `the filter matches whole words, not parts of them, and lets harmless names through`() {
        listOf(
            "The University of Warsaw opens its doors.",
            "Battlestar Galactica premieres on television.",
            "Star Wars opens in cinemas across the United States.",
            "Orson Welles broadcasts The War of the Worlds on the radio.",
            "Billie Jean King wins the Battle of the Sexes.",
            "The Cold War ends with the Malta Summit.",
            "Die Hard premieres in Los Angeles.",
            "The Metamorphosis by Franz Kafka is published in Die Weissen Blaetter.",
            "The first Coupe de France final is played.",
            "The Bombay Stock Exchange opens.",
            "A skilled crew finishes the bridge, which wins an award.",
            "The warm spring brings the first software release toward the coast.",
            "The Dead Sea Scrolls go on show.",
            "A greater firefly is named.",
        ).forEach { assertFalse(it, OnThisDay.isGrim(event(it))) }
    }

    @Test fun `only the first sentence of the subject's article counts`() {
        val grimArticle = page("Old Fort", extract = "The Old Fort was the scene of a massacre in 1857. It is now a museum.")
        assertTrue(OnThisDay.isGrim(event("The Old Fort opens as a museum.", 1920, grimArticle)))
        // A writer's article saying, later on, that he served in a war is no reason to drop his novel.
        val salinger = page("J. D. Salinger", extract = "Jerome David Salinger was an American writer. He served in World War II.")
        assertFalse(OnThisDay.isGrim(event("J. D. Salinger publishes a novel.", 1951, salinger)))
        // Only the subject's: another article's opening sentence doesn't count (its title does).
        val other = page("Leopold III", extract = "Leopold III was king during the invasion of Belgium.")
        assertFalse(OnThisDay.isGrim(event("J. D. Salinger publishes a novel.", 1951, salinger, other)))
        assertEquals("Jerome David Salinger was an American writer.", OnThisDay.firstSentence(salinger.extract))
        assertEquals("J. D. Salinger was born in New York.", OnThisDay.firstSentence("J. D. Salinger was born in New York. He wrote."))
    }

    @Test fun `the titles of the linked articles count too`() {
        val pages = arrayOf(page("Route 91 Harvest"), page("2017 Las Vegas shooting"))
        assertTrue(OnThisDay.isGrim(event("A festival in Las Vegas.", 2017, *pages)))
        assertTrue(OnThisDay.isGrim(event("A test near Alamogordo.", 1945, page("Trinity (nuclear test)"))))
    }

    // --- Scoring ----------------------------------------------------------------------------

    @Test fun `a good picture, fun and firsts score higher, dry politics lower`() {
        val opening = event("Walt Disney World opens near Orlando, Florida.")
        val noPicture = event("Walt Disney World opens near Orlando, Florida.", 1971, page(pictures = null))
        val treaty = event("A treaty is signed by the president and parliament.")
        val first = event("Concorde breaks the sound barrier for the first time.")
        assertTrue(OnThisDay.score(opening) > OnThisDay.score(noPicture))
        assertTrue(OnThisDay.score(opening) > OnThisDay.score(treaty))
        assertTrue(OnThisDay.score(treaty) < OnThisDay.score(event("Something happened.")))
        // Space and flight, plus a first, plus the picture.
        assertEquals(3 + 2 + 1, OnThisDay.score(first))
        // Science and culture each count once, however many of their words appear.
        assertEquals(3 + 2 + 2, OnThisDay.score(event("A scientist discovers a comet and publishes a book about it.")))
    }

    @Test fun `a logo, seal, emblem or drawing is worth less than a photo, and a non-free picture nothing`() {
        val text = "Something happened."
        assertEquals(3, OnThisDay.score(event(text, 1969, page(pictures = photo("Crowd.jpg")))))
        assertEquals(1, OnThisDay.score(event(text, 1969, page(pictures = drawing("Flag_of_Tuvalu.svg")))))
        assertEquals(1, OnThisDay.score(event(text, 1969, page(pictures = photo("Club_logo.png")))))
        assertEquals(1, OnThisDay.score(event(text, 1969, page(pictures = photo("Great_Seal_of_Ohio.jpg")))))
        assertEquals(1, OnThisDay.score(event(text, 1969, page(pictures = photo("Coat_of_arms_of_Peru.png")))))
        assertEquals(0, OnThisDay.score(event(text, 1969, page(pictures = nonFree()))))
    }

    @Test fun `nature and milestones are fun, police and protests are dry`() {
        val none = page(pictures = null)
        assertEquals(2, OnThisDay.score(event("Yosemite National Park is established.", 1890, none)))
        assertEquals(2, OnThisDay.score(event("Women gain the right to vote in New Zealand.", 1893, none)))
        assertEquals(2, OnThisDay.score(event("Tuvalu becomes independent.", 1978, none)))
        assertEquals(2 + 2, OnThisDay.score(event("The Channel Tunnel opens to traffic.", 1994, none))) // and an opening
        assertEquals(-2, OnThisDay.score(event("Police arrest the protesters.", 1968, none)))
        assertEquals(-2, OnThisDay.score(event("Workers go on strike.", 1926, none)))
        assertEquals(-2, OnThisDay.score(event("A deputation meets the viceroy.", 1906, none)))
        // "Congress" alone isn't dry: it as often founds a park as passes a tax.
        assertEquals(0, OnThisDay.score(event("Congress meets.", 1789, none)))
    }

    @Test fun `the picks prefer pictures and fun, and leave the grim ones out`() {
        val events = listOf(
            event("A treaty is signed.", 1800, page("Treaty")),
            event("Troops invade the city.", 1940, page("Invasion")),
            event("The first television broadcast.", 1936, page("Television", pictures = null)),
            event("Walt Disney World opens.", 1971, page("Walt Disney World")),
            event("A levy is imposed.", 2003, page("Levy")),
        )
        val picks = OnThisDay.choose(events, date)
        // The opening with its picture, then the first broadcast without one, then a dry one: the other dry one
        // scores no more than 1 and goes, since three are left without it.
        assertEquals(listOf(1971, 1936), picks.take(2).map { it.year })
        assertEquals(3, picks.size)
    }

    @Test fun `items scoring 1 or less go while at least three others are left`() {
        val good = (1..3).map { event("A museum opens, number $it.", 1900 + 10 * it, page("Museum $it")) }
        val dull = (1..3).map { event("Something happened, number $it.", 1960 + 10 * it, page("Thing $it", pictures = null)) }
        assertEquals(good.map { it.year }.toSet(), OnThisDay.choose(good + dull, date).map { it.year }.toSet())
        // With only two good ones, the best of the dull ones stays to make three.
        assertEquals(3, OnThisDay.choose(good.take(2) + dull, date).size)
        assertEquals(2, OnThisDay.choose(good.take(2), date).size)
    }

    @Test fun `no two picks from the same decade while there are others`() {
        val sixties = (0..4).map { event("A museum opens, number $it.", 1960 + it, page("Sixties $it")) }
        val others = listOf(
            event("A bridge opens.", 1932, page("Bridge", pictures = null)),
            event("A garden opens.", 1890, page("Garden", pictures = null)),
        )
        val picks = OnThisDay.choose(sixties + others, date)
        assertEquals(5, picks.size)
        // The best of the sixties first, then the garden and the bridge, and more sixties only to make up five.
        assertTrue(picks[0].year >= 1960)
        assertEquals(listOf(1890, 1932), picks.subList(1, 3).map { it.year })
        assertTrue(picks.drop(3).all { it.year >= 1960 })
        // A decade the curated list already has is avoided by the full one's picks too.
        val taken = listOf(OnThisDayPick(1965, "", "", ""))
        val more = OnThisDay.choose(sixties + others, date, max = 2, taken = taken)
        assertEquals(setOf(1932, 1890), more.map { it.year }.toSet())
    }

    // --- Choosing for the day ---------------------------------------------------------------

    private val ties = (1..12).map { event("Item $it of the day.", 1800 + 10 * it, page("Page $it")) }

    @Test fun `the same date always gives the same picks, at most five`() {
        val a = OnThisDay.choose(ties, date)
        val b = OnThisDay.choose(ties, LocalDate.of(2026, 10, 1))
        assertEquals(5, a.size)
        assertEquals(a, b)
    }

    @Test fun `ties are broken by the date, so another year can lead with another pick`() {
        val leads = (0L..10L).map { OnThisDay.choose(ties, date.plusYears(it)).first().year }.toSet()
        assertTrue(leads.size > 1)
        assertNotEquals(OnThisDay.choose(ties, date), OnThisDay.choose(ties, date.plusYears(1)))
    }

    @Test fun `the same moment told twice is picked once, also across the two lists`() {
        val curated = event("Walt Disney World opened near Orlando.", 1971, page("Walt Disney World"), page("Orlando"))
        val again = event("Walt Disney World opens in Florida.", 1971, page("Florida"), page("Walt Disney World"))
        assertEquals(1, OnThisDay.choose(listOf(curated, again), date).size)
        // The full list's telling is left out when the curated one has it.
        assertTrue(OnThisDay.choose(listOf(again), date, besides = listOf(curated)).isEmpty())
        // The same article in another year is another moment.
        val later = event("Walt Disney World turns 25.", 1996, page("Walt Disney World"))
        assertEquals(1, OnThisDay.choose(listOf(later), date, besides = listOf(curated)).size)
    }

    @Test fun `an item without an article to link isn't picked`() {
        val bare = OnThisDayEvent(1900, "A museum opens.", emptyList())
        val blank = OnThisDayEvent(1901, "A museum opens.", listOf(WikiPage("", "", "")))
        assertTrue(OnThisDay.choose(listOf(bare, blank), date).isEmpty())
    }

    // --- The subject and its picture --------------------------------------------------------

    @Test fun `the subject is the article named in the text, after any topic lead-in`() {
        val apollo = event(
            "Apollo program: NASA launches the unmanned Apollo 4 test spacecraft.", 1967,
            page("Apollo program"), page("NASA"), page("Apollo 4"),
        )
        // Not the topic in the lead-in: the first article the sentence itself names.
        assertEquals("NASA", OnThisDay.subjectOf(apollo)?.title)
        // A "(film)" note isn't in the text; the match is case-sensitive ("nasa" in a word wouldn't do).
        val gone = event("Hattie McDaniel wins for Gone with the Wind.", 1940, page("African Americans"), page("Gone with the Wind (film)"))
        assertEquals("Gone with the Wind (film)", OnThisDay.subjectOf(gone)?.title)
        val lower = event("the nasa budget grows.", 1970, page("Budget"), page("NASA"))
        assertEquals("Budget", OnThisDay.subjectOf(lower)?.title)
        // None named: the first.
        assertEquals("Joe Frazier", OnThisDay.subjectOf(event("A fight.", 1975, page("Joe Frazier"), page("Muhammad Ali")))?.title)
    }

    @Test fun `a pick links its subject, and takes a photo from another of its articles when the subject has none`() {
        val pick = OnThisDay.choose(
            listOf(event("Muhammad Ali defeats Joe Frazier in the Thrilla in Manila.", 1975,
                page("Thrilla in Manila", pictures = nonFree("Thrilla_poster.jpg")), page("Joe Frazier", pictures = null), page("Muhammad Ali", pictures = photo("Ali.jpg")))),
            date,
        ).single()
        assertEquals("Thrilla in Manila", pick.title)
        assertEquals("https://en.wikipedia.org/wiki/Thrilla_in_Manila", pick.url)
        assertTrue(pick.picture!!.url.contains("/wikipedia/commons/") && pick.picture!!.url.contains("Ali.jpg"))
        val textOnly = OnThisDay.choose(listOf(event("A first.", 1900, page("Words", pictures = null))), date).single()
        assertNull(textOnly.picture)
        assertEquals("Words", textOnly.title)
    }

    @Test fun `only free pictures from Commons, else words only`() {
        assertNull(OnThisDay.pictureOf(page(pictures = nonFree())))
        assertNull(OnThisDay.pictureOf(event("The film premieres.", 1975, page("Film", pictures = nonFree()))))
        assertTrue(OnThisDay.isCommons(OnThisDay.pictureOf(page(pictures = photo()))!!.url))
    }

    @Test fun `a picture from an article about the same thing comes before the others`() {
        // ALH84001: the subject is "Allan Hills" (named in the text), which has no picture; the meteorite's own
        // article does, and comes before the general one about meteorites.
        val alh = event(
            "Researchers announced that the meteorite ALH84001, discovered in the Allan Hills, may contain evidence of life.", 1996,
            page("Meteorite", pictures = photo("Hoba.jpg")), page("Allan Hills 84001", pictures = photo("ALH84001.jpg")), page("Allan Hills", pictures = null),
        )
        assertEquals("Allan Hills", OnThisDay.subjectOf(alh)?.title)
        assertTrue(OnThisDay.pictureOf(alh)!!.url.contains("ALH84001.jpg"))
    }

    @Test fun `a photo is preferred to a drawing, the subject's first`() {
        val flag = page("Tuvalu", pictures = drawing("Flag_of_Tuvalu.svg"))
        val crowd = page("Funafuti", pictures = photo("Funafuti.jpg"))
        assertTrue(OnThisDay.pictureOf(event("Tuvalu becomes independent.", 1978, flag, crowd))!!.url.contains("Funafuti.jpg"))
        // With no photo anywhere, the subject's drawing.
        assertTrue(OnThisDay.pictureOf(event("Tuvalu becomes independent.", 1978, flag, page("Funafuti", pictures = null)))!!.fromSvg)
        val portrait = page("Tuvalu", pictures = photo("Leader.jpg", 600, 800))
        assertTrue(OnThisDay.pictureOf(event("Tuvalu becomes independent.", 1978, portrait, crowd))!!.url.contains("Leader.jpg"))
    }

    @Test fun `a picture under 150 px on its shorter side isn't shown`() {
        assertNull(OnThisDay.pictureOf(page(pictures = photo("Tiny.jpg", 400, 149))))
        assertEquals(150, OnThisDay.pictureOf(page(pictures = photo("Small.jpg", 400, 150)))?.height)
    }

    @Test fun `landscape photos fill the frame, the rest are posters`() {
        assertTrue(OnThisDay.pictureOf(page(pictures = photo("Wide.jpg", 3264, 2448)))!!.fill)
        assertFalse(OnThisDay.pictureOf(page(pictures = photo("Square.jpg", 1000, 1000)))!!.fill)
        assertFalse(OnThisDay.pictureOf(page(pictures = photo("Portrait.jpg", 600, 800)))!!.fill)
        assertFalse(OnThisDay.pictureOf(page(pictures = photo("Small.jpg", 500, 300)))!!.fill) // under 400 px tall
        assertFalse(OnThisDay.pictureOf(page(pictures = drawing("Flag.svg", 1200, 600)))!!.fill)
    }

    @Test fun `the picture is asked for at a size Wikimedia makes that suits the frame`() {
        val base = "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab"
        // Big enough: 960 px, with the feed's 330 px as the fallback.
        val big = OnThisDay.pictureOf(page(pictures = photo("X.jpg", 3264, 2448)))!!
        assertEquals("$base/X.jpg/960px-X.jpg", big.url)
        assertEquals("$base/X.jpg/330px-X.jpg", big.fallbackUrl)
        // A smaller photo: the original itself.
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/a/ab/Y.jpg", OnThisDay.pictureOf(page(pictures = photo("Y.jpg", 800, 600)))!!.url)
        // A drawing: rendered at 500 px.
        assertTrue(OnThisDay.pictureOf(page(pictures = drawing("Flag.svg", 600, 300)))!!.url.endsWith("/500px-Flag.svg.png"))
        // An original that can't be shown as itself (a TIFF): the largest standard width under it.
        val tiff = Pictures(WikiImage("$base/Z.tif/330px-Z.tif.jpg", 330, 248), WikiImage("https://upload.wikimedia.org/wikipedia/commons/a/ab/Z.tif", 800, 600))
        assertEquals("$base/Z.tif/500px-Z.tif.jpg", OnThisDay.pictureOf(page(pictures = tiff))!!.url)
    }

    @Test fun `the picture's page on Commons, for its author and licence`() {
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:GBT_May_2018.jpg",
            OnThisDay.commonsFilePage("https://thumb.wikimedia.org/wikipedia/commons/thumb/d/de/GBT_May_2018.jpg/960px-GBT_May_2018.jpg?utm_source=x"),
        )
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Las_Vegas%2C_Mandalay_Bay.jpg",
            OnThisDay.commonsFilePage("https://upload.wikimedia.org/wikipedia/commons/9/9c/Las_Vegas%2C_Mandalay_Bay.jpg?utm_source=x"),
        )
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Flag_of_Tuvalu.svg",
            OnThisDay.commonsFilePage("https://thumb.wikimedia.org/wikipedia/commons/thumb/3/38/Flag_of_Tuvalu.svg/500px-Flag_of_Tuvalu.svg.png"),
        )
        assertNull(OnThisDay.commonsFilePage("https://upload.wikimedia.org/wikipedia/en/9/98/Poster.jpg"))
    }

    // --- Text -------------------------------------------------------------------------------

    @Test fun `the text is tidied, without notes about the main page's picture`() {
        assertEquals(
            "Tuvalu adopted its national flag on the day that the country gained its independence.",
            OnThisDay.cleanText("  Tuvalu adopted its national flag (pictured) on the day that the country\n gained its independence. "),
        )
        assertEquals(
            "The first political gathering of colonists in Mexican Texas convened.",
            OnThisDay.cleanText("The first political gathering of colonists (president pictured) in Mexican Texas convened."),
        )
        // A note just before a comma or a full stop leaves no space behind.
        assertEquals("The comet, seen from Earth, was named.", OnThisDay.cleanText("The comet (pictured) , seen from Earth (pictured) , was named ."))
    }

    @Test fun `a very long text keeps its first sentence or clause, not stopping at an abbreviation`() {
        val long = "At the encouragement of John Muir, the U.S. Congress established Yosemite National Park in California. " +
            "It was the third national park in the country, after Yellowstone and Sequoia, and remains one of the most visited."
        assertEquals(
            "At the encouragement of John Muir, the U.S. Congress established Yosemite National Park in California.",
            OnThisDay.cleanText(long),
        )
        val clauses = "Sony and Philips launch the compact disc in Japan; on the same day, Sony releases the CDP-101, " +
            "the first compact disc player of its kind, which went on sale for the equivalent of several hundred dollars."
        assertEquals("Sony and Philips launch the compact disc in Japan.", OnThisDay.cleanText(clauses))
        val abbreviations = "At 9 a.m. local time, Gen. Motors Corp. and Apple Computer Inc. unveil a joint prototype that Prof. Smith built. " +
            "The machine was later shown at a fair, where it drew large crowds all week."
        assertEquals(
            "At 9 a.m. local time, Gen. Motors Corp. and Apple Computer Inc. unveil a joint prototype that Prof. Smith built.",
            OnThisDay.cleanText(abbreviations),
        )
        val short = "Walt Disney World opens near Orlando, Florida. It is an instant hit."
        assertEquals(short, OnThisDay.cleanText(short))
    }

    @Test fun `years before the common era read as BC`() {
        assertEquals("1969", OnThisDay.formatYear(1969))
        assertEquals("959", OnThisDay.formatYear(959))
        assertEquals("331 BC", OnThisDay.formatYear(-331))
    }

    @Test fun `how long ago, with BC years counted without a year 0`() {
        assertEquals("51 years ago", OnThisDay.yearsAgo(1975, date))
        assertEquals("last year", OnThisDay.yearsAgo(2025, date))
        assertEquals("this year", OnThisDay.yearsAgo(2026, date))
        assertEquals("1,067 years ago", OnThisDay.yearsAgo(959, date))
        assertEquals("2,356 years ago", OnThisDay.yearsAgo(-331, date))
        assertEquals("2,025 years ago", OnThisDay.yearsAgo(-1, LocalDate.of(2025, 1, 1)))
    }
}
