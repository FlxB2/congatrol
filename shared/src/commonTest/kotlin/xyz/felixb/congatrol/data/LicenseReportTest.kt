package xyz.felixb.congatrol.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A report in the shape `just licenses` writes: libraries pointing at licences by hash. */
private val REPORT =
    """
    {
      "libraries": [
        {
          "uniqueId": "io.ktor:ktor-client-core",
          "artifactVersion": "3.6.0",
          "name": "ktor-client-core",
          "website": "https://github.com/ktorio/ktor",
          "developers": [{ "name": "JetBrains" }, { "name": " " }, { "name": "JetBrains" }],
          "licenses": ["BSD-3-Clause"]
        },
        {
          "uniqueId": "com.googlefonts:manrope",
          "name": "Manrope",
          "developers": [],
          "licenses": ["named-only"]
        },
        {
          "uniqueId": "androidx.activity:activity",
          "artifactVersion": "1.13.0",
          "name": "Activity",
          "developers": [{ "name": "The Android Open Source Project" }],
          "licenses": ["BSD-3-Clause", "named-only", "not-in-the-report"],
          "somethingNew": true
        },
        { "name": "Nameless, and so left out" }
      ],
      "licenses": {
        "BSD-3-Clause": {
          "name": "BSD 3-Clause License",
          "url": "https://spdx.org/licenses/BSD-3-Clause.html",
          "content": "Copyright (c) <<var;name=copyright;original= <year> <owner>;match=.+>> All rights reserved.",
          "hash": "BSD-3-Clause"
        },
        "named-only": {
          "name": "Some License",
          "url": "https://example.com/license",
          "hash": "named-only"
        }
      }
    }
    """.trimIndent()

class LicenseReportTest {
    private val libraries = parseLicenseReport(REPORT)

    @Test
    fun librariesAreListedByNameIgnoringCase() {
        assertEquals(listOf("Activity", "ktor-client-core", "Manrope"), libraries.map { it.name })
    }

    @Test
    fun aLibraryCarriesItsCoordinateVersionAndDistinctAuthors() {
        val ktor = libraries.first { it.id == "io.ktor:ktor-client-core" }

        assertEquals("3.6.0", ktor.version)
        assertEquals("JetBrains", ktor.author)
        assertEquals("https://github.com/ktorio/ktor", ktor.website)
    }

    @Test
    fun whatAHandWrittenEntryLeavesOutIsNull() {
        val font = libraries.first { it.id == "com.googlefonts:manrope" }

        assertNull(font.version)
        assertNull(font.author)
        assertNull(font.website)
    }

    @Test
    fun licencesAreLookedUpByHashAndUnknownOnesDropped() {
        val activity = libraries.first { it.id == "androidx.activity:activity" }

        assertEquals(listOf("BSD 3-Clause License", "Some License"), activity.licenses.map { it.name })
        assertNull(activity.licenses[1].text)
        assertEquals("https://example.com/license", activity.licenses[1].url)
    }

    @Test
    fun spdxMarkupIsReducedToTheWordingItWraps() {
        val text =
            libraries
                .first { it.id == "io.ktor:ktor-client-core" }
                .licenses
                .single()
                .text

        assertEquals("Copyright (c) <year> <owner> All rights reserved.", text)
    }

    @Test
    fun wrappedLinesAreJoinedAndTheRestKeepsItsLines() {
        val wrapped =
            """
            SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
            -----------------------------------------------------------

            PREAMBLE
            The goals of the Open Font License (OFL) are to stimulate worldwide
            development of collaborative font projects.

            1) Neither the Font Software nor any of its individual components,
            in Original or Modified Versions, may be sold by itself.
            2) Another condition.
            """.trimIndent()

        assertEquals(
            """
            SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
            -----------------------------------------------------------

            PREAMBLE
            The goals of the Open Font License (OFL) are to stimulate worldwide development of collaborative font projects.

            1) Neither the Font Software nor any of its individual components, in Original or Modified Versions, may be sold by itself.
            2) Another condition.
            """.trimIndent(),
            readableLicenseText(wrapped),
        )
    }

    @Test
    fun theReportIsReadOnce() =
        runTest {
            var reads = 0
            val repository =
                LicenseRepository(
                    readReport = {
                        reads++
                        REPORT
                    },
                )

            assertSame(repository.libraries(), repository.libraries())
            assertEquals(1, reads)
        }
}
