package com.example.agent.rules;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Segments source code into logical statements using data-driven rules.
 */
public final class SegmentEngine {

    public List<String> segment(String src, List<RuleV2> segmentRules, List<RuleLoaderV2.KeywordBoundary> boundaries) {
        List<String> tokens = new ArrayList<>();
        tokens.add(src);
        for (RuleV2 r : segmentRules) {
            List<String> next = new ArrayList<>();
            if ("regex_outside_quotes_parens".equalsIgnoreCase(r.strategy) && r.regex != null) {
                Pattern p = Pattern.compile(r.regex);
                for (String t : tokens) next.addAll(splitOutsideQuotesAndParens(t, p));
            } else if ("regex_after".equalsIgnoreCase(r.strategy) && r.regex != null) {
                Pattern p = Pattern.compile(r.regex, Pattern.CASE_INSENSITIVE);
                for (String t : tokens) next.addAll(splitAfterRegex(t, p));
            } else if ("regex".equalsIgnoreCase(r.strategy) && r.regex != null) {
                Pattern p = Pattern.compile(r.regex);
                for (String t : tokens) {
                    for (String s : p.split(t)) {
                        s = s.trim();
                        if (!s.isEmpty()) next.add(s);
                    }
                }
            } else {
                next.addAll(tokens);
            }
            tokens = next;
        }
        tokens = splitKeywordBoundaries(tokens, boundaries);
        if (tokens.isEmpty()) return splitOutsideQuotesAndParens(src, Pattern.compile(";"));
        return tokens;
    }

    private List<String> splitKeywordBoundaries(List<String> tokens, List<RuleLoaderV2.KeywordBoundary> boundaries) {
        Set<String> openSplit = new HashSet<>();
        Set<String> closers = new HashSet<>();
        for (RuleLoaderV2.KeywordBoundary kb : boundaries) {
            if (kb.close != null) closers.add(kb.close);
            if (kb.open != null && kb.close != null && "END".equalsIgnoreCase(kb.close)) {
                openSplit.add(kb.open);
            }
        }
        List<String> out = new ArrayList<>();
        for (String t : tokens) {
            String trimmed = t.trim();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            boolean handled = false;

            for (String kw : openSplit) {
                if (upper.startsWith(kw + " ")) {
                    out.add(kw);
                    String rest = trimmed.substring(kw.length()).trim();
                    if (!rest.isEmpty()) out.add(rest);
                    handled = true;
                    break;
                }
            }
            if (handled) continue;

            for (String kw : closers) {
                if (upper.startsWith(kw + " ")) {
                    out.add(kw);
                    String rest = trimmed.substring(kw.length()).trim();
                    if (!rest.isEmpty()) out.add(rest);
                    handled = true;
                    break;
                }
            }
            if (!handled) out.add(trimmed);
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
                sb.append(c);
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

    private List<String> splitAfterRegex(String s, Pattern p) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        int last = 0;
        while (m.find()) {
            String before = s.substring(last, m.start()).trim();
            if (!before.isEmpty()) out.add(before);
            String match = m.group().trim();
            if (!match.isEmpty()) out.add(match);
            last = m.end();
        }
        String after = s.substring(last).trim();
        if (!after.isEmpty()) out.add(after);
        return out;
    }
}
