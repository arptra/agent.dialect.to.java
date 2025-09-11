package com.example.agent.rules;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Segments source code into logical statements using data-driven rules.
 */
public final class SegmentEngine {

    public List<String> segment(String src, List<RuleV2> segmentRules) {
        List<String> tokens = List.of(src);
        boolean applied = false;
        for (RuleV2 r : segmentRules) {
            if ("regex_boundary_keep".equalsIgnoreCase(r.strategy) && r.regex != null) {
                List<String> next = new ArrayList<>();
                Pattern p = Pattern.compile(r.regex, Pattern.CASE_INSENSITIVE);
                for (String t : tokens) next.addAll(boundaryKeep(t, p));
                tokens = next; applied = true; continue;
            }
            if ("regex_outside_quotes_parens".equalsIgnoreCase(r.strategy) && r.regex != null) {
                List<String> next = new ArrayList<>();
                Pattern p = Pattern.compile(r.regex);
                for (String t : tokens) next.addAll(splitOutsideQuotesAndParens(t, p));
                tokens = next; applied = true; continue;
            }
            if ("regex".equalsIgnoreCase(r.strategy) && r.regex != null) {
                List<String> next = new ArrayList<>();
                for (String t : tokens) next.addAll(Arrays.asList(t.split(r.regex)));
                tokens = next; applied = true; continue;
            }
        }
        if (!applied) {
            return splitOutsideQuotesAndParens(src, Pattern.compile(";"));
        }
        return tokens;
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

    private List<String> boundaryKeep(String s, Pattern p) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        int pos = 0;
        while (m.find()) {
            if (m.start() > pos) {
                String before = s.substring(pos, m.start()).trim();
                if (!before.isEmpty()) out.add(before);
            }
            String tok = m.group().trim();
            if (!tok.isEmpty()) out.add(tok);
            pos = m.end();
        }
        if (pos < s.length()) {
            String tail = s.substring(pos).trim();
            if (!tail.isEmpty()) out.add(tail);
        }
        return out;
    }
}
