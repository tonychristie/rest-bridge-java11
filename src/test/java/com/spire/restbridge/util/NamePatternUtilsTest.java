package com.spire.restbridge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NamePatternUtilsTest {

    @Test
    void toFilter_noWildcard_givesExactMatch() {
        assertEquals("name='dm_document'", NamePatternUtils.toFilter("name", "dm_document"));
    }

    @Test
    void toFilter_wildcardAfterText_givesStartsWithOnTextBeforeFirstWildcard() {
        assertEquals("starts-with(name,'dm_')", NamePatternUtils.toFilter("name", "dm_*"));
        assertEquals("starts-with(name,'dm')", NamePatternUtils.toFilter("name", "dm*cab*"));
    }

    @Test
    void toFilter_leadingWildcard_givesNoFilter() {
        assertNull(NamePatternUtils.toFilter("name", "*_folder"));
    }

    @Test
    void toFilter_quote_isDoubled() {
        assertEquals("starts-with(name,'o''b')", NamePatternUtils.toFilter("name", "o'b*"));
    }

    @Test
    void matches_trailingWildcard() {
        assertTrue(NamePatternUtils.matches("dm_*", "dm_document"));
        assertFalse(NamePatternUtils.matches("dm_*", "dmi_queue_item"));
    }

    @Test
    void matches_leadingWildcard() {
        assertTrue(NamePatternUtils.matches("*_folder", "dm_folder"));
        assertFalse(NamePatternUtils.matches("*_folder", "dm_folder_x"));
    }

    @Test
    void matches_wildcardInMiddle() {
        assertTrue(NamePatternUtils.matches("dm*cab*", "dm_cabinet"));
    }

    @Test
    void matches_noWildcard_matchesOnlyExactName() {
        assertFalse(NamePatternUtils.matches("dm_document", "dm_document_x"));
    }

    @Test
    void matches_wildcardOnly_matchesAnyName() {
        assertTrue(NamePatternUtils.matches("*", "dm_document"));
    }

    @Test
    void matches_regexCharacter_matchesItself() {
        assertFalse(NamePatternUtils.matches("dm.*", "dmXabc"));
    }

    @Test
    void matches_isCaseSensitive() {
        assertFalse(NamePatternUtils.matches("DM_*", "dm_document"));
    }
}
