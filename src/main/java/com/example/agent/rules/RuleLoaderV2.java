package com.example.agent.rules;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Loads/saves JSONL rules (RuleV2) from runtime/rules.jsonl */
public class RuleLoaderV2 {
  private final Path rulesFile;
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<RuleV2> rules = new ArrayList<>();

  public RuleLoaderV2(Path runtimeDir) throws IOException {
    Files.createDirectories(runtimeDir);
    this.rulesFile = runtimeDir.resolve("rules.jsonl");
    load();
  }

  public List<RuleV2> all() { return Collections.unmodifiableList(rules); }

  public List<RuleV2> ofType(String type) {
    List<RuleV2> out = new ArrayList<>();
    for (RuleV2 r : rules) if (type.equalsIgnoreCase(r.type)) out.add(r);
    out.sort((a,b)->Integer.compare(b.priority, a.priority));
    return out;
  }

  public synchronized void addOrMerge(RuleV2 r) {
    // naive merge by id
    for (int i=0;i<rules.size();i++) {
      if (Objects.equals(rules.get(i).id, r.id)) { rules.set(i, r); return; }
    }
    rules.add(r);
  }

  public synchronized void save() throws IOException {
    System.out.println("[LEARN] rules path = " + rulesFile.toAbsolutePath());
    if (rules.isEmpty() && Files.exists(rulesFile) && Files.size(rulesFile) > 0) {
      System.err.println("[WARN] repo has 0 rules; skip overwriting non-empty " + rulesFile.toAbsolutePath());
      return;
    }
    Path tmp = rulesFile.resolveSibling("rules.jsonl.tmp");
    try (var bw = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
      for (RuleV2 r : rules) {
        bw.write(mapper.writeValueAsString(r));
        bw.write('\n');
      }
    }
    Files.move(tmp, rulesFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    System.out.println("[SAVE] wrote " + rules.size() + " rules to " + rulesFile.toAbsolutePath());
  }

  private void load() throws IOException {
    System.out.println("[LEARN] rules path = " + rulesFile.toAbsolutePath());
    rules.clear();
    if (!Files.exists(rulesFile)) return;
    try (var br = Files.newBufferedReader(rulesFile, StandardCharsets.UTF_8)) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) continue;
        try {
          RuleV2 r = mapper.readValue(line, RuleV2.class);
          // backward compat: if no 'type', treat as stmt
          if (r.type == null || r.type.isBlank()) r.type = "stmt";
          rules.add(r);
        } catch (Exception ignored) {}
      }
    }
  }
}
