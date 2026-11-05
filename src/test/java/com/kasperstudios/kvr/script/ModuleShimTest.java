package com.kasperstudios.kvr.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModuleShimTest {

    @Test
    void namedKvrImportBecomesDestructuring() throws Exception {
        String out = ModuleShim.apply("import { Window, Button as Btn } from 'kvr';\nnew Window();");
        assertEquals("const { Window, Button: Btn } = kvr;\nnew Window();", out);
    }

    @Test
    void defaultImportAndDoubleQuotes() throws Exception {
        assertTrue(ModuleShim.apply("import k from \"kvr\"\nk.toast({});").startsWith("const k = kvr;"));
    }

    @Test
    void codeWithoutImportsIsUntouched() throws Exception {
        String src = "const a = 1; // import later";
        assertEquals(src, ModuleShim.apply(src));
    }

    @Test
    void relativeImportsAreRejectedClearly() {
        JsEngine.JsException e = assertThrows(JsEngine.JsException.class, () -> ModuleShim.apply("import { X } from './components.js';"));
        assertTrue(e.getMessage().contains("./components.js"));
    }
}
