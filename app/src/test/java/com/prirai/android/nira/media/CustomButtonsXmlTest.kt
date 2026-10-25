package com.prirai.android.nira.media

import com.prirai.android.nira.media.alpha.*
import org.junit.Assert.*
import org.junit.Test

class CustomButtonsXmlTest {
    private val xml="""<?xml version="1.0" encoding="UTF-8"?><customButtons>
        <button slot="3"><id>stable-synthetic</id><title>&amp;Test</title><enabled>true</enabled>
        <alwaysVisible>false</alwaysVisible><textColor>-65536</textColor>
        <content><![CDATA[mp.commandv("script-message-to", "synthetic", "cycle")]]></content>
        <longPressContent><![CDATA[return 2]]></longPressContent><onStartup><![CDATA[error('never run')]]></onStartup>
        <icon>synthetic-icon</icon></button></customButtons>"""
    @Test fun canonicalStandaloneButtonsPreserveExplicitSlotsColorsAndSourceAndUndo() {
        val plan=AlphaSettingsImporter.parse(xml.byteInputStream());val slots=plan.slots!!
        assertEquals(43,slots.size);assertNull(slots[0]);assertNull(slots[2]);assertNull(slots[35])
        val row=slots[3]!!;assertEquals("stable-synthetic",row.id);assertEquals("&Test",row.title)
        assertEquals(-65536,row.textColor);assertEquals(false,row.alwaysVisible)
        assertTrue(row.content.contains("script-message-to"));assertEquals("return 2",row.longPressContent)
        assertEquals("error('never run')",row.onStartup);assertEquals("synthetic-icon",row.icon)
        val base=AlphaLuaConfig.defaults();val after=plan.applyTo(base,AlphaImportScope.ALL)
        assertEquals(after,plan.applyTo(after,AlphaImportScope.ALL))
        assertEquals(base,plan.applyTo(base,AlphaImportScope.APPEARANCE))
    }
    @Test fun duplicateSlotsIdsFieldsXxeAndInvalidDataFailClosed() {
        val bad=listOf(xml.replace("slot=\"3\"","slot=\"43\""),
            xml.replace("</customButtons>",xml.substringAfter("<customButtons>").substringBefore("</customButtons>")+"</customButtons>"),
            xml.replace("</title>","</title><title>duplicate</title>"),
            xml.replace("true</enabled>","maybe</enabled>"),
            xml.replace("<customButtons>","<!DOCTYPE customButtons [<!ENTITY xxe SYSTEM 'file:///private'>]><customButtons>"))
        for(input in bad)try{AlphaSettingsImporter.parse(input.byteInputStream());fail()}catch(_:AlphaImportException){}
    }
}
