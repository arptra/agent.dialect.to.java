package com.example.agent.rules;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Segments source code into logical statements using data-driven rules.
 */
public final class SegmentEngine {

    public List<String> segment(String src, List<RuleV2> segmentRules) {
        // Apply the first supported strategy rule; fallback to simple semicolon split.
        for (RuleV2 r : segmentRules) {
            if ("regex_outside_quotes_parens".equalsIgnoreCase(r.strategy) && r.regex != null) {
                return splitOutsideQuotesAndParens(src, Pattern.compile(r.regex));
            }
            if ("regex".equalsIgnoreCase(r.strategy) && r.regex != null) {
                return Arrays.asList(src.split(r.regex));
            }
        }
        // Fallback:
        return splitOutsideQuotesAndParens(src, Pattern.compile(";"));
    }

    private List<String> splitOutsideQuotesAndParens(String s, Pattern sep) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int paren = 0;
        boolean inQ = false;
        char qc = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQ) {
                sb.append(c);
                if (c == qc) inQ = false;
                continue;
            }
            if (c == '\'' || c == '"') {
                inQ = true;
                qc = c;
                sb.append(c);
                continue;
            }
            if (c == '(') {
                paren++;
                sb.append(c);
                continue;
            }
            if (c == ')') {
                paren = Math.max(0, paren - 1);
                sb.append(c);
                continue;
            }
            if (paren == 0 && sep.matcher(String.valueOf(c)).matches()) {
                String t = sb.toString().trim();
                if (!t.isEmpty()) out.add(t);
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        String t = sb.toString().trim();
        if (!t.isEmpty()) out.add(t);
        return out;
    }
}
