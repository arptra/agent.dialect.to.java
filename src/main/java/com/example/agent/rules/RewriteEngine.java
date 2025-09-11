package com.example.agent.rules;

import java.util.List;

/**
 * Applies rewrite rules to a source string. For simple regex-based rewrites
 * {@link String#replaceAll(String, String)} is used. Some rules, like
 * <code>rw_amp_macro_strict</code>, require balanced-parentheses handling
 * and are implemented via a custom parser.
 */

public final class RewriteEngine {
  public String applyAll(String s, List<RuleV2> rewrite) {
    if (s == null) return null;
    String out = s;
    for (RuleV2 r : rewrite) {
      if ("rw_amp_macro_strict".equals(r.id)) {
        out = rewriteAmpMacro(out);
        continue;
      }
      if (r.pattern == null || r.replace == null) continue;
      try { out = out.replaceAll(r.pattern, r.replace); } catch (Exception ignored) {}
    }
    return out;
  }

  /**
   * Rewrites occurrences of <code>&foo(...)</code> to <code>foo(...)</code>,
   * supporting nested parentheses and quoted strings within the argument list.
   */
  private String rewriteAmpMacro(String s) {
    if (s == null || s.indexOf('&') < 0) return s;
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < s.length()) {
      int amp = s.indexOf('&', i);
      if (amp < 0) { out.append(s.substring(i)); break; }
      out.append(s, i, amp);
      int j = amp + 1;
      if (j >= s.length() || !Character.isJavaIdentifierStart(s.charAt(j))) {
        out.append('&');
        i = j;
        continue;
      }
      int k = j + 1;
      while (k < s.length() && Character.isJavaIdentifierPart(s.charAt(k))) k++;
      String id = s.substring(j, k);
      int t = k;
      while (t < s.length() && Character.isWhitespace(s.charAt(t))) t++;
      if (t >= s.length() || s.charAt(t) != '(') {
        out.append('&').append(id);
        i = t;
        continue;
      }
      int depth = 0;
      boolean inString = false; char qc = 0;
      int m = t;
      for (; m < s.length(); m++) {
        char c = s.charAt(m);
        if (inString) {
          if (c == qc) inString = false;
          continue;
        }
        if (c == '\'' || c == '"') { inString = true; qc = c; continue; }
        if (c == '(') depth++;
        else if (c == ')') {
          depth--;
          if (depth == 0) break;
        }
      }
      if (depth != 0) { // unmatched
        out.append(s.substring(amp));
        break;
      }
      String args = s.substring(t + 1, m);
      out.append(id).append('(').append(args).append(')');
      i = m + 1;
    }
    return out.toString();
  }
}
