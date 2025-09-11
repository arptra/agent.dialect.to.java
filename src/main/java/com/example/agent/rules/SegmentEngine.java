package com.example.agent.rules;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Segments source code into logical statements using data-driven rules.
 */
public final class SegmentEngine {

    public List<String> segment(String src, List<RuleV2> segmentRules) {
        if (segmentRules == null || segmentRules.isEmpty()) {
            return splitOutsideQuotesAndParens(src, Pattern.compile(";"));
        }

        List<RuleV2> rules = new ArrayList<>(segmentRules);
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));

        List<String> tokens = new ArrayList<>();
        tokens.add(src);

        for (RuleV2 r : rules) {
            Pattern p = r.regex != null ? Pattern.compile(r.regex, Pattern.CASE_INSENSITIVE) : null;
            List<String> next = new ArrayList<>();
            for (String t : tokens) {
                if (t.isBlank()) continue;
                if ("regex_outside_quotes_parens".equalsIgnoreCase(r.strategy) && p != null) {
                    next.addAll(splitOutsideQuotesAndParens(t, p));
                } else if ("regex_keep".equalsIgnoreCase(r.strategy) && p != null) {
                    next.addAll(splitKeep(t, p));
                } else if ("regex_after".equalsIgnoreCase(r.strategy) && p != null) {
                    next.addAll(splitAfter(t, p));
                } else if ("regex".equalsIgnoreCase(r.strategy) && r.regex != null) {
                    for (String part : t.split(r.regex)) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) next.add(trimmed);
                    }
                } else {
                    next.add(t.trim());
                }
            }
            tokens = next;
        }

        List<String> out = new ArrayList<>();
        for (String t : tokens) {
            String tt = t.trim();
            if (!tt.isEmpty()) out.add(tt);
        }
        return out;
    }

    private List<String> splitKeep(String s, Pattern p) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                out.add(s.substring(last, m.start()).trim());
            }
            out.add(m.group().trim());
            last = m.end();
        }
        if (last < s.length()) {
            out.add(s.substring(last).trim());
        }
        return out;
    }

    private List<String> splitAfter(String s, Pattern p) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        int last = 0;
        while (m.find()) {
            out.add(s.substring(last, m.end()).trim());
            last = m.end();
        }
        if (last < s.length()) {
            out.add(s.substring(last).trim());
        }
        return out;
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
