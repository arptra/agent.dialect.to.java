package com.example.agent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class RuleStore {

    private final Path dir;
    private final Path rulesFile;
    private final Path processedFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Rule> rules = new ArrayList<>();
    private final Set<String> processed = new HashSet<>();

    public RuleStore(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
        this.rulesFile = dir.resolve("rules.jsonl");
        this.processedFile = dir.resolve("processed_files.jsonl");
        load();
    }

    private void load() throws IOException {
        rules.clear();
        processed.clear();
        if (Files.exists(rulesFile)) {
            try (BufferedReader br = Files.newBufferedReader(rulesFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    Rule r = mapper.readValue(line, Rule.class);
                    rules.add(r);
                }
            }
        }
        if (Files.exists(processedFile)) {
            try (BufferedReader br = Files.newBufferedReader(processedFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    processed.add(line.trim());
                }
            }
        }
    }

    public synchronized void save() throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(rulesFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Rule r : rules) {
                bw.write(mapper.writeValueAsString(r));
                bw.write("\n");
            }
        }
        try (BufferedWriter bw = Files.newBufferedWriter(processedFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String p : processed) {
                bw.write(p);
                bw.write("\n");
            }
        }
    }

    public List<Rule> allRules() { return Collections.unmodifiableList(rules); }

    public void addOrUpdateRule(Rule r) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id.equals(r.id)) { rules.set(i, r); return; }
        }
        rules.add(r);
    }

    public boolean isProcessed(Path repoRoot, Path file) {
        return processed.contains(fingerprint(repoRoot, file));
    }

    public void markProcessed(Path repoRoot, Path file) {
        processed.add(fingerprint(repoRoot, file));
    }

    private String fingerprint(Path repoRoot, Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(repoRoot.toString().getBytes(StandardCharsets.UTF_8));
            md.update((byte)0);
            md.update(file.toString().getBytes(StandardCharsets.UTF_8));
            md.update((byte)0);
            md.update(java.nio.file.Files.readAllBytes(file));
            byte[] h = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
