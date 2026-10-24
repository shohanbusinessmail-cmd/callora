package com.shohankhan.callora.core.ui

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationParityTest {

    private val placeholderRegex = Regex("""%\d+\$[sd]""")

    private fun resolveResFile(relativeFromModuleOrRoot: String, rootFallback: String): File {
        val direct = File(relativeFromModuleOrRoot)
        if (direct.exists()) return direct
        return File(rootFallback)
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        assertTrue(file.exists(), "Expected resource file at ${file.absolutePath}")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            val text = el.textContent ?: ""
            map[name] = text
        }
        return map
    }

    @Test
    fun `English and Bengali string catalogs have 100 percent key and placeholder parity`() {
        val enFile = resolveResFile(
            "src/main/res/values/strings.xml",
            "core/ui/src/main/res/values/strings.xml"
        )
        val bnFile = resolveResFile(
            "src/main/res/values-bn/strings.xml",
            "core/ui/src/main/res/values-bn/strings.xml"
        )

        val enMap = parseStringsXml(enFile)
        val bnMap = parseStringsXml(bnFile)

        assertTrue(enMap.size >= 250, "Expected comprehensive localized catalog, got ${enMap.size}")
        assertEquals(
            emptySet(),
            enMap.keys - bnMap.keys,
            "Missing Bengali translations for keys"
        )
        assertEquals(
            emptySet(),
            bnMap.keys - enMap.keys,
            "Orphan keys in Bengali strings.xml"
        )

        for ((key, enValue) in enMap) {
            val bnValue = bnMap.getValue(key)
            assertTrue(bnValue.isNotBlank(), "Blank Bengali translation for key '$key'")
            val enPlaceholders = placeholderRegex.findAll(enValue).map { it.value }.sorted().toList()
            val bnPlaceholders = placeholderRegex.findAll(bnValue).map { it.value }.sorted().toList()
            assertEquals(
                enPlaceholders,
                bnPlaceholders,
                "Format placeholder mismatch for key '$key'"
            )
        }
    }
}
