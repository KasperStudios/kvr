package com.kasperstudios.kvr.script;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lets apps write the SPEC style {@code import { Window, Button } from 'kvr';} before the engine's
 * own module loader is wired (5d): imports of {@code 'kvr'} become destructuring of the global
 * {@code kvr} object. Relative imports are rejected with a clear message for now.
 */
public final class ModuleShim {

    private static final Pattern NAMED = Pattern.compile("(?m)^\\s*import\\s*\\{([^}]*)\\}\\s*from\\s*['\"]kvr['\"]\\s*;?");
    private static final Pattern DEFAULT = Pattern.compile("(?m)^\\s*import\\s+(\\w+)\\s+from\\s*['\"]kvr['\"]\\s*;?");
    private static final Pattern OTHER = Pattern.compile("(?m)^\\s*import\\s.*from\\s*['\"]([^'\"]+)['\"]");

    private ModuleShim() {}

    public static String apply(String source) throws JsEngine.JsException {
        String out = replace(NAMED, source, m -> "const { " + String.join(", ", names(m.group(1))) + " } = kvr;");
        out = replace(DEFAULT, out, m -> "const " + m.group(1) + " = kvr;");
        Matcher other = OTHER.matcher(out);
        if (other.find()) throw new JsEngine.JsException("import '" + other.group(1) + "': multi-file apps arrive with the .vrapp format (5d)");
        return out;
    }

    private static List<String> names(String list) {
        List<String> out = new ArrayList<>();
        for (String part : list.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] as = p.split("\\s+as\\s+");
            out.add(as.length == 2 ? as[0].trim() + ": " + as[1].trim() : p);
        }
        return out;
    }

    private interface Rewrite {
        String apply(Matcher m);
    }

    private static String replace(Pattern p, String s, Rewrite r) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(r.apply(m)));
        m.appendTail(sb);
        return sb.toString();
    }
}
