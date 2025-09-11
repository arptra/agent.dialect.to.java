package com.example.agent.rules;

import java.util.List;

public final class RewriteEngine {
  public String applyAll(String s, List<RuleV2> rewrite) {
    if (s == null) return null;
    String out = s;
    for (RuleV2 r : rewrite) {
      if ("rw_amp_macro_strict".equals(r.id)) {
        out = rewriteAmpMacroStrict(out);
        continue;
      }
      if (r.pattern == null || r.replace == null) continue;
      try { out = out.replaceAll(r.pattern, r.replace); } catch (Exception ignored) {}
    }
    return out;
  }

  private String rewriteAmpMacroStrict(String s) {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < s.length()) {
      char c = s.charAt(i);
      if (c == '&' && i + 1 < s.length() &&
          (Character.isLetter(s.charAt(i + 1)) || s.charAt(i + 1) == '_')) {
        int j = i + 1;
        while (j < s.length() &&
               (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) {
          j++;
        }
        int k = j;
        while (k < s.length() && Character.isWhitespace(s.charAt(k))) k++;
        if (k < s.length() && s.charAt(k) == '(') {
          int depth = 0;
          int m = k;
          while (m < s.length()) {
            char ch = s.charAt(m);
            if (ch == '(') depth++;
            else if (ch == ')') {
              depth--;
              if (depth == 0) break;
            }
            m++;
          }
          if (depth == 0) {
            sb.append(s, i + 1, k); // name without '&' and spaces
            sb.append(s, k, m + 1); // full arg list
            i = m + 1;
            continue;
          }
        }
      }
      sb.append(c);
      i++;
    }
    return sb.toString();
  }
}
