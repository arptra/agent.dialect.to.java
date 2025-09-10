package com.example.agent.rules;

import java.util.List;

public final class RewriteEngine {
  public String applyAll(String s, List<RuleV2> rewrite) {
    if (s == null) return null;
    String out = s;
    for (RuleV2 r : rewrite) {
      if (r.pattern == null || r.replace == null) continue;
      try { out = out.replaceAll(r.pattern, r.replace); } catch (Exception ignored) {}
    }
    return out;
  }
}
