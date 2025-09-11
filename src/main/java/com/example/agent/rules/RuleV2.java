package com.example.agent.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Self-learning rule model (data-driven). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleV2 {
  public String id;
  public String type; // "segment" | "block" | "stmt" | "rewrite"
  public String strategy;        // segment: e.g., "regex_outside_quotes_parens"
  public String regex;           // stmt/segment
  public String irType;          // stmt/block -> IR$*
  public String[] fields;        // constructor field order
  public String[] listFields;    // CSV list fields
  public String open;            // block open-regex
  public String[] middle;        // block middle markers (optional)
  public String close;           // block close-regex
  public String pattern;         // rewrite
  public String replace;         // rewrite
  public int priority = 0;       // ranking within type
  public double confidence = 0.0;
  public int support = 0;

  public RuleV2() {}

  private static final ObjectMapper M = new ObjectMapper();

  @Override
  public String toString() {
    try {
      return M.writeValueAsString(this);
    } catch (Exception e) {
      return "RuleV2{id='" + id + "', type='" + type + "'}";
    }
  }
}
