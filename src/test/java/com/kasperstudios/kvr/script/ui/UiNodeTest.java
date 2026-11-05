package com.kasperstudios.kvr.script.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiNodeTest {

    @Test
    void parsesTheTreeThePreludeCommits() {
        String json = "{\"id\":1,\"type\":\"window\",\"title\":\"KAV\",\"width\":300,\"height\":190,"
            + "\"style\":{\"background\":\"linear(#7f1d1d, #f43f5e)\"},\"children\":["
            + "{\"id\":2,\"type\":\"button\",\"label\":\"Scan\",\"variant\":\"success\"},"
            + "{\"id\":3,\"type\":\"slider\",\"min\":0,\"max\":100,\"value\":42,\"readOnly\":true},"
            + "{\"id\":4,\"type\":\"stack\",\"dir\":\"row\",\"children\":[{\"id\":5,\"type\":\"text\",\"text\":\"a\"}]}]}";
        UiNode root = UiNode.parse(json);
        assertEquals("window", root.type());
        assertEquals("KAV", root.str("title", ""));
        assertEquals("linear(#7f1d1d, #f43f5e)", root.obj("style").get("background").getAsString());
        assertEquals(3, root.children().size());
        assertEquals("Scan", root.children().get(0).str("label", ""));
        assertEquals(42, root.children().get(1).num("value", 0), 1e-9);
        assertTrue(root.children().get(1).bool("readOnly"));
        assertEquals("a", root.children().get(2).children().get(0).str("text", ""));
    }

    @Test
    void hugeTreesAreCapped() {
        StringBuilder sb = new StringBuilder("{\"type\":\"window\",\"children\":[");
        for (int i = 0; i < 2000; i++) sb.append(i == 0 ? "" : ",").append("{\"id\":").append(i).append(",\"type\":\"text\"}");
        sb.append("]}");
        assertTrue(UiNode.parse(sb.toString()).children().size() < 600);
    }
}
