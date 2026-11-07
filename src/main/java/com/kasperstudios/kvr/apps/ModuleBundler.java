package com.kasperstudios.kvr.apps;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the ES-module files of a {@code .vrapp} ({@code main.js} importing {@code ./components.js} and
 * {@code ./styles.js}, CONCEPT §2.2) into ONE classic script for the engine — the QuickJS wasm build runs
 * scripts, not a module loader. Every file becomes a function in a small CommonJS-style registry; imports
 * become {@code __kvr_require("components.js")} with paths resolved here, at bundle time, so a missing file
 * fails at install instead of at runtime. {@code import ... from 'kvr'} becomes destructuring of the global
 * {@code kvr} object (same as {@code ModuleShim}).
 *
 * <p>Supported (one statement per line start, imports may span lines):
 * <pre>
 * import { a, b as c } from './x.js'      import X from './x.js'       import * as NS from './x.js'
 * import X, { a } from './x.js'           import './x.js'              import data from './data.json'
 * export function f() / async function / function*      export class C      export const|let|var x = ...
 * export default &lt;expr&gt; | function [name] | class [name]                    export { a, b as c }
 * export { a } from './x.js'              export * from './x.js'
 * </pre>
 * Exports are live getters defined at the top of the module, so circular imports see values once they
 * are initialised. Not supported (rejected with {@code BAD_MODULE}, never silently miscompiled):
 * destructuring exports, bare specifiers other than {@code 'kvr'}. {@code export const a = 1, b = 2}
 * exports only {@code a} — write one declaration per export.
 *
 * <p>A single-file app without exports and without relative imports is returned unchanged, so every
 * existing app keeps its exact semantics (top-level declarations stay global).
 */
public final class ModuleBundler {

    private static final String ID = "[A-Za-z_$][\\w$]*";
    private static final String SPEC = "['\"]([^'\"\\n]+)['\"]";
    private static final String LS = "(?m)^[ \\t]*";
    private static final Pattern IMPORT_DEFAULT_NAMED = Pattern.compile(LS + "import\\s+(" + ID + ")\\s*,\\s*\\{([^}]*)\\}\\s*from\\s*" + SPEC + "[ \\t]*;?");
    private static final Pattern IMPORT_NAMED = Pattern.compile(LS + "import\\s*\\{([^}]*)\\}\\s*from\\s*" + SPEC + "[ \\t]*;?");
    private static final Pattern IMPORT_NS = Pattern.compile(LS + "import\\s*\\*\\s*as\\s+(" + ID + ")\\s*from\\s*" + SPEC + "[ \\t]*;?");
    private static final Pattern IMPORT_DEFAULT = Pattern.compile(LS + "import\\s+(" + ID + ")\\s+from\\s*" + SPEC + "[ \\t]*;?");
    private static final Pattern IMPORT_BARE = Pattern.compile(LS + "import\\s*" + SPEC + "[ \\t]*;?");
    private static final Pattern ANY_IMPORT_SPEC = Pattern.compile(LS + "import\\b[^;'\"]*?" + SPEC);
    private static final Pattern EXPORT_FROM = Pattern.compile(LS + "export\\s*\\{([^}]*)\\}\\s*from\\s*" + SPEC + "[ \\t]*;?");
    private static final Pattern EXPORT_STAR = Pattern.compile(LS + "export\\s*\\*\\s*from\\s*" + SPEC + "[ \\t]*;?");
    private static final Pattern EXPORT_LIST = Pattern.compile(LS + "export\\s*\\{([^}]*)\\}[ \\t]*;?");
    private static final Pattern EXPORT_DESTRUCT = Pattern.compile(LS + "export\\s+(?:const|let|var)\\s*[\\[{]");
    private static final Pattern EXPORT_DECL = Pattern.compile("(?m)^([ \\t]*)export\\s+(default\\s+)?("
        + "(?:async\\s+)?function(?:\\s*\\*\\s*|\\s+)(" + ID + ")"
        + "|class\\s+(" + ID + ")"
        + "|(?:const|let|var)\\s+(" + ID + "))");
    private static final Pattern EXPORT_DEFAULT = Pattern.compile("(?m)^([ \\t]*)export\\s+default\\s+");
    private static final Pattern ANY_EXPORT = Pattern.compile(LS + "export\\s");
    private static final Pattern LEFTOVER = Pattern.compile(LS + "(?:import\\s*[\\w$*{'\"]|export\\s)");
    private static final Pattern USE_STRICT = Pattern.compile("^\\s*(['\"])use strict\\1;?");

    private ModuleBundler() {}

    /**
     * @param sources every text file of the package by path ({@code "main.js"}, {@code "ui/list.js"}, {@code "data.json"})
     * @param entry   the manifest's {@code main}
     */
    public static String bundle(Map<String, String> sources, String entry) throws VrAppException {
        String main = sources.get(entry);
        if (main == null) throw new VrAppException(VrAppException.Code.MISSING_MAIN, entry);
        if (isPlainScript(main)) return main;

        Map<String, String> modules = new LinkedHashMap<>();
        List<String> queue = new ArrayList<>(List.of(entry));
        Set<String> seen = new LinkedHashSet<>(queue);
        while (!queue.isEmpty()) {
            String file = queue.remove(0);
            Set<String> deps = new LinkedHashSet<>();
            modules.put(file, file.endsWith(".json") ? jsonModule(file, sources.get(file)) : transform(file, sources.get(file), sources, deps));
            for (String d : deps) {
                if (seen.add(d)) queue.add(d);
            }
        }

        StringBuilder sb = new StringBuilder(main.length() * 2 + 512);
        sb.append("(function () {\nconst __kvr_defs = {\n");
        for (Map.Entry<String, String> m : modules.entrySet()) {
            sb.append("// --- ").append(m.getKey()).append(" ---\n");
            sb.append(quote(m.getKey())).append(": function (__kvr_exports, __kvr_require) {\n");
            sb.append(m.getValue());
            sb.append("\n},\n");
        }
        sb.append("};\n");
        sb.append("const __kvr_cache = {};\n");
        sb.append("const __kvr_load = (name) => {\n");
        sb.append("  if (__kvr_cache[name]) return __kvr_cache[name];\n");
        sb.append("  const ex = {};\n");
        sb.append("  __kvr_cache[name] = ex;\n");
        sb.append("  __kvr_defs[name](ex, __kvr_load);\n");
        sb.append("  return ex;\n");
        sb.append("};\n");
        sb.append("__kvr_load(").append(quote(entry)).append(");\n");
        sb.append("})();\n");
        return sb.toString();
    }

    /** No exports and only {@code 'kvr'} imports: run as-is (ModuleShim rewrites the kvr imports). */
    static boolean isPlainScript(String src) {
        if (ANY_EXPORT.matcher(src).find()) return false;
        Matcher m = ANY_IMPORT_SPEC.matcher(src);
        while (m.find()) {
            if (!m.group(1).equals("kvr")) return false;
        }
        return true;
    }

    private static String jsonModule(String file, String text) throws VrAppException {
        try {
            JsonParser.parseString(text);
        } catch (RuntimeException e) {
            throw new VrAppException(VrAppException.Code.BAD_MODULE, file + ": invalid JSON: " + e.getMessage());
        }
        return "__kvr_exports.default = " + text.strip() + ";";
    }

    private static String transform(String file, String src, Map<String, String> sources, Set<String> deps) throws VrAppException {
        if (EXPORT_DESTRUCT.matcher(src).find()) throw new VrAppException(VrAppException.Code.BAD_MODULE, file + ": destructuring exports are not supported");
        Map<String, String> getters = new LinkedHashMap<>(); // exported name -> JS expression
        int[] reSeq = {0};
        Resolver r = spec -> {
            if (spec.equals("kvr")) return null;
            String target = resolve(file, spec, sources);
            deps.add(target);
            return target;
        };

        String out = src;
        out = replace(out, IMPORT_DEFAULT_NAMED, m -> {
            String t = r.resolve(m.group(3));
            if (t == null) return "const " + m.group(1) + " = kvr; const { " + names(m.group(2)) + " } = kvr;";
            return "const { default: " + m.group(1) + ", " + names(m.group(2)) + " } = __kvr_require(" + quote(t) + ");";
        });
        out = replace(out, IMPORT_NAMED, m -> {
            String t = r.resolve(m.group(2));
            return "const { " + names(m.group(1)) + " } = " + (t == null ? "kvr" : "__kvr_require(" + quote(t) + ")") + ";";
        });
        out = replace(out, IMPORT_NS, m -> {
            String t = r.resolve(m.group(2));
            return "const " + m.group(1) + " = " + (t == null ? "kvr" : "__kvr_require(" + quote(t) + ")") + ";";
        });
        out = replace(out, IMPORT_DEFAULT, m -> {
            String t = r.resolve(m.group(2));
            return "const " + m.group(1) + " = " + (t == null ? "kvr" : "__kvr_require(" + quote(t) + ").default") + ";";
        });
        out = replace(out, IMPORT_BARE, m -> {
            String t = r.resolve(m.group(1));
            return t == null ? "" : "__kvr_require(" + quote(t) + ");";
        });

        out = replace(out, EXPORT_FROM, m -> {
            String t = r.resolve(m.group(2));
            if (t == null) throw new VrAppException(VrAppException.Code.BAD_MODULE, file + ": re-exporting from 'kvr' is not supported");
            String alias = "__kvr_re" + reSeq[0]++;
            for (String[] pair : pairs(m.group(1))) getters.put(pair[1], alias + "." + pair[0]);
            return "const " + alias + " = __kvr_require(" + quote(t) + ");";
        });
        out = replace(out, EXPORT_STAR, m -> {
            String t = r.resolve(m.group(1));
            if (t == null) throw new VrAppException(VrAppException.Code.BAD_MODULE, file + ": re-exporting from 'kvr' is not supported");
            return "((m) => { for (const k of Object.keys(m)) if (k !== 'default' && !(k in __kvr_exports)) "
                + "Object.defineProperty(__kvr_exports, k, { enumerable: true, get: () => m[k] }); })(__kvr_require(" + quote(t) + "));";
        });
        out = replace(out, EXPORT_LIST, m -> {
            for (String[] pair : pairs(m.group(1))) getters.put(pair[1], pair[0]);
            return "";
        });
        out = replace(out, EXPORT_DECL, m -> {
            boolean isDefault = m.group(2) != null;
            String decl = m.group(3);
            String name = m.group(4) != null ? m.group(4) : m.group(5) != null ? m.group(5) : m.group(6);
            if (isDefault && "extends".equals(m.group(5))) return m.group(1) + "__kvr_exports.default = " + decl; // anonymous class extends X
            getters.put(isDefault ? "default" : name, name);
            return m.group(1) + decl;
        });
        out = replace(out, EXPORT_DEFAULT, m -> m.group(1) + "__kvr_exports.default = ");

        Matcher left = LEFTOVER.matcher(out);
        if (left.find()) {
            int line = 1 + (int) out.substring(0, left.start()).chars().filter(c -> c == '\n').count();
            throw new VrAppException(VrAppException.Code.BAD_MODULE, file + ":" + line + ": unsupported import/export form");
        }

        StringBuilder head = new StringBuilder();
        for (Map.Entry<String, String> g : getters.entrySet()) {
            head.append("Object.defineProperty(__kvr_exports, ").append(quote(g.getKey()))
                .append(", { enumerable: true, get: () => ").append(g.getValue()).append(" }); ");
        }
        Matcher strict = USE_STRICT.matcher(out);
        if (strict.find()) return out.substring(0, strict.end()) + " " + head + out.substring(strict.end());
        return head + out;
    }

    /** {@code ./x}, {@code ../ui/list.js} relative to {@code from}; tries {@code p}, {@code p.js}, {@code p/index.js}. */
    static String resolve(String from, String spec, Map<String, String> sources) throws VrAppException {
        if (!spec.startsWith("./") && !spec.startsWith("../")) {
            throw new VrAppException(VrAppException.Code.BAD_MODULE, from + ": import '" + spec + "' — only relative paths and 'kvr' are allowed");
        }
        List<String> parts = new ArrayList<>();
        int slash = from.lastIndexOf('/');
        if (slash > 0) parts.addAll(List.of(from.substring(0, slash).split("/")));
        for (String s : spec.split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (parts.isEmpty()) throw new VrAppException(VrAppException.Code.BAD_MODULE, from + ": import '" + spec + "' leaves the package");
                parts.remove(parts.size() - 1);
            } else {
                parts.add(s);
            }
        }
        String p = String.join("/", parts);
        for (String c : new String[] {p, p + ".js", p + "/index.js"}) {
            if (sources.containsKey(c) && (c.endsWith(".js") || c.endsWith(".json"))) return c;
        }
        throw new VrAppException(VrAppException.Code.BAD_MODULE, from + ": import '" + spec + "' — file not in the package");
    }

    private interface Resolver {
        String resolve(String spec) throws VrAppException;
    }

    private interface Rewrite {
        String apply(Matcher m) throws VrAppException;
    }

    /** Regex replace that keeps the line count, so errors still point at the author's line numbers. */
    private static String replace(String s, Pattern p, Rewrite r) throws VrAppException {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            String rep = r.apply(m);
            sb.append(rep);
            long lines = m.group().chars().filter(c -> c == '\n').count() - rep.chars().filter(c -> c == '\n').count();
            for (long i = 0; i < lines; i++) sb.append('\n');
            last = m.end();
        }
        sb.append(s, last, s.length());
        return sb.toString();
    }

    /** "a, b as c" → destructuring "a, b: c". */
    private static String names(String list) {
        List<String> out = new ArrayList<>();
        for (String[] pair : pairs(list)) out.add(pair[0].equals(pair[1]) ? pair[0] : pair[0] + ": " + pair[1]);
        return String.join(", ", out);
    }

    /** "a, b as c" → [[a, a], [b, c]] (local/imported name, then exported/bound name). */
    private static List<String[]> pairs(String list) {
        List<String[]> out = new ArrayList<>();
        for (String part : list.split(",")) {
            String p = part.strip();
            if (p.isEmpty()) continue;
            String[] as = p.split("\\s+as\\s+");
            out.add(as.length == 2 ? new String[] {as[0].strip(), as[1].strip()} : new String[] {p, p});
        }
        return out;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
