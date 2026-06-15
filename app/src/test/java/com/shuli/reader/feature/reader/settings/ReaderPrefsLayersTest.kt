package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ReaderPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReaderPrefsLayersTest {

    @Test
    fun overlayPrefs_onlyContainsTier0Fields() {
        val prefs = ReaderPreferences()
        val overlay = prefs.toOverlayPrefs()
        assertEquals(prefs.colorTemperature, overlay.colorTemperature, 0.001f)
        assertEquals(prefs.brightness, overlay.brightness, 0.001f)
        // hapticFeedback 和 eyeCareReminderInterval 属于 tier -1（不参与重组），不在 OverlayPrefs 中
    }

    @Test
    fun chromePrefs_containsHeaderFooterFields() {
        val prefs = ReaderPreferences()
        val chrome = prefs.toChromePrefs()
        assertEquals(prefs.header.visibility.name, chrome.headerVisibility)
        assertEquals(prefs.footer.visibility.name, chrome.footerVisibility)
        assertEquals(prefs.headerFooterAlpha, chrome.headerFooterAlpha, 0.001f)
        assertEquals(prefs.showProgress, chrome.showProgress)
        assertEquals(prefs.progressStyle, chrome.progressStyle)
        assertEquals(prefs.backgroundTexture, chrome.backgroundTexture)
    }

    @Test
    fun stylePrefs_containsFontAndThemeFields() {
        val prefs = ReaderPreferences()
        val style = prefs.toStylePrefs()
        assertEquals(prefs.readingFont, style.readingFont)
        assertEquals(prefs.fontWeight, style.fontWeight)
        assertEquals(prefs.textAlign, style.textAlign)
        assertEquals(prefs.backgroundColor, style.backgroundColor)
        assertEquals(prefs.bionicReading, style.bionicReading)
        assertEquals(prefs.adFiltering, style.adFiltering)
        assertEquals(prefs.titleFont, style.titleFont)
    }

    @Test
    fun layoutPrefs_containsGeometryFields() {
        val prefs = ReaderPreferences()
        val layout = prefs.toLayoutPrefs()
        assertEquals(prefs.fontSize, layout.fontSize, 0.001f)
        assertEquals(prefs.lineSpacing, layout.lineSpacing, 0.001f)
        assertEquals(prefs.marginHorizontal, layout.marginHorizontal, 0.001f)
        assertEquals(prefs.marginVertical, layout.marginVertical, 0.001f)
        assertEquals(prefs.marginTop, layout.marginTop)
        assertEquals(prefs.marginBottom, layout.marginBottom)
        assertEquals(prefs.marginLeft, layout.marginLeft)
        assertEquals(prefs.marginRight, layout.marginRight)
        assertEquals(prefs.paragraphDivider, layout.paragraphDivider)
        assertEquals(prefs.dualPageMode, layout.dualPageMode)
    }

    @Test
    fun modifyingOverlayField_doesNotChangeLayoutPrefs() {
        val a = ReaderPreferences()
        val b = a.copy(colorTemperature = 4000f)
        assertEquals(a.toLayoutPrefs(), b.toLayoutPrefs())
        assertNotEquals(a.toOverlayPrefs(), b.toOverlayPrefs())
    }

    @Test
    fun modifyingLayoutField_doesNotChangeOverlayPrefs() {
        val a = ReaderPreferences()
        val b = a.copy(fontSize = 22f)
        assertEquals(a.toOverlayPrefs(), b.toOverlayPrefs())
        assertNotEquals(a.toLayoutPrefs(), b.toLayoutPrefs())
    }

    @Test
    fun modifyingChromeField_doesNotChangeStylePrefs() {
        val a = ReaderPreferences()
        val b = a.copy(headerFooterAlpha = 0.8f)
        assertEquals(a.toStylePrefs(), b.toStylePrefs())
        assertNotEquals(a.toChromePrefs(), b.toChromePrefs())
    }
}
