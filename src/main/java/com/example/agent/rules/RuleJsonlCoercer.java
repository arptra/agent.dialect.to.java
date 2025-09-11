package com.example.agent.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Robust parser/coercer for various JSONL shapes that LLMs may return.
 * Accepts canonical RuleV2 lines AND nested wrappers like {"stmt":{...}}, {"block":{...}}, etc.
 * Drops invalid items; generates deterministic ids if missing.
 */
public final class RuleJsonlCoercer {

    private static final ObjectMapper M = new ObjectMapper();

    public static List<RuleV2> parse(String raw) {
        List<RuleV2> out = new ArrayList<>();
        if (raw == null) return out;

        for (String line : raw.split("\\R")) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("```")) continue; // strip code fences
            if (!(s.startsWith("{") && s.endsWith("}"))) continue;

            try {
                JsonNode node = M.readTree(s);
                RuleV2 r = coerce(node);
                if (r != null) {
                    if ("segment".equalsIgnoreCase(r.type) &&
                        "regex_replace".equalsIgnoreCase(r.strategy)) {
                        r.type = "rewrite";
                        r.pattern = r.regex;
                        r.regex = null;
                    }
                    if (isSane(r)) {
                        if (r.id == null || r.id.isBlank()) r.id = genId(r);
                        if (r.priority == 0) r.priority = defaultPriority(r.type);
                        out.add(r);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static RuleV2 coerce(JsonNode node) {
        RuleV2 r = new RuleV2();

        // 1) Canonical flat object (has "type")?
        if (node.hasNonNull("type")) {
            r.type = node.get("type").asText();
            readCommonFlat(node, r);
            return r;
        }

        // 2) Nested wrapper: {"stmt":{...}} / {"block":{...}} / {"segment":{...}} / {"rewrite":{...}}
        for (String kind : List.of("stmt", "block", "segment", "rewrite")) {
            if (node.has(kind)) {
                r.type = mapType(kind);
                readCommonFlat(node.get(kind), r);
                return r;
            }
        }

        // 3) Unknown
        return null;
    }

    private static void readCommonFlat(JsonNode n, RuleV2 r) {
        if (n == null) return;
        if (n.has("id") && !n.get("id").isNull()) r.id = n.get("id").asText();

        // regex / strategy
        if (n.has("regex") && !n.get("regex").isNull()) r.regex = n.get("regex").asText();
        if (n.has("strategy") && !n.get("strategy").isNull()) r.strategy = n.get("strategy").asText();

        // irType
        if (n.has("irType") && !n.get("irType").isNull()) r.irType = n.get("irType").asText();

        // fields: array expected; if object -> use keys order (best effort)
        if (n.has("fields") && !n.get("fields").isNull()) {
            if (n.get("fields").isArray()) {
                List<String> fs = new ArrayList<>();
                n.get("fields").forEach(x -> fs.add(x.asText()));
                r.fields = fs.toArray(new String[0]);
            } else if (n.get("fields").isObject()) {
                List<String> fs = new ArrayList<>();
                n.get("fields").fieldNames().forEachRemaining(fs::add);
                r.fields = fs.toArray(new String[0]);
            }
        }

        if (n.has("listFields") && !n.get("listFields").isNull()) {
            List<String> lf = new ArrayList<>();
            if (n.get("listFields").isArray()) {
                n.get("listFields").forEach(x -> lf.add(x.asText()));
            } else if (n.get("listFields").isObject()) {
                n.get("listFields").fieldNames().forEachRemaining(lf::add);
            }
            r.listFields = lf.toArray(new String[0]);
        }

        // block parts
        if (n.has("open") && !n.get("open").isNull()) r.open = ensureAnchors(n.get("open").asText());
        if (n.has("close") && !n.get("close").isNull()) r.close = ensureAnchors(n.get("close").asText());
        if (n.has("middle") && !n.get("middle").isNull() && n.get("middle").isArray()) {
            List<String> mids = new ArrayList<>();
            n.get("middle").forEach(x -> mids.add(ensureAnchors(x.asText())));
            r.middle = mids.toArray(new String[0]);
        }

        // rewrite
        if (n.has("pattern") && !n.get("pattern").isNull()) r.pattern = n.get("pattern").asText();
        if (n.has("replace") && !n.get("replace").isNull()) r.replace = n.get("replace").asText();

        // priority
        if (n.has("priority") && !n.get("priority").isNull()) r.priority = n.get("priority").asInt();
    }

    private static String mapType(String kind) {
        switch (kind) {
            case "stmt":
                return "stmt";
            case "block":
                return "block";
            case "segment":
                return "segment";
            case "rewrite":
                return "rewrite";
            default:
                return kind;
        }
    }

    private static boolean isSane(RuleV2 r) {
        if (r.type == null) return false;

        switch (r.type.toLowerCase()) {
            case "stmt":
                // require regex and irType
                return notBlank(r.regex) && notBlank(r.irType);
            case "block":
                // require open & close & irType
                return notBlank(r.open) && notBlank(r.close) && notBlank(r.irType);
            case "segment":
                // require strategy and regex
                return notBlank(r.strategy) && notBlank(r.regex);
            case "rewrite":
                return notBlank(r.pattern) && r.replace != null;
            default:
                return false;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String ensureAnchors(String re) {
        if (re == null) return null;
        String t = re.trim();
        boolean hasStart = t.startsWith("^");
        boolean hasEnd = t.endsWith("$");
        if (!hasStart) t = "^" + t;
        if (!hasEnd) t = t + "$";
        return t;
    }

    private static int defaultPriority(String type) {
        if (type == null) return 1;
        switch (type.toLowerCase()) {
            case "block":
                return 50;
            case "stmt":
                return 20;
            case "segment":
                return 10;
            case "rewrite":
                return 5;
            default:
                return 1;
        }
    }

    private static String genId(RuleV2 r) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            String key = (r.type + "|" +
                    nullToEmpty(r.regex) + "|" + nullToEmpty(r.strategy) + "|" +
                    nullToEmpty(r.irType) + "|" + nullToEmpty(r.open) + "|" + nullToEmpty(r.close) + "|" +
                    nullToEmpty(r.pattern) + "|" + nullToEmpty(r.replace));
            byte[] dig = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", dig[i]));
            return r.type + "_" + sb.toString();
        } catch (Exception e) {
            return r.type + "_" + UUID.randomUUID();
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
