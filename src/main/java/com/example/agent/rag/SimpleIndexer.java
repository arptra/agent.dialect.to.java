package com.example.agent.rag;

import java.util.*;
import java.util.regex.Pattern;

/** Extremely simple in-memory TF-IDF index for code snippets. */
public class SimpleIndexer {
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\S");

    private final List<String> docs = new ArrayList<>();
    private final List<Map<String, Integer>> termFreq = new ArrayList<>();
    private final Map<String, Integer> docFreq = new HashMap<>();

    public int addDocument(String content) {
        int id = docs.size();
        docs.add(content);
        Map<String, Integer> tf = new HashMap<>();
        var m = TOKEN.matcher(content);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            String t = m.group();
            tf.put(t, tf.getOrDefault(t, 0) + 1);
            if (!seen.contains(t)) {
                docFreq.put(t, docFreq.getOrDefault(t, 0) + 1);
                seen.add(t);
            }
        }
        termFreq.add(tf);
        return id;
    }

    public List<String> topKSimilar(String query, int k) {
        var qtf = new HashMap<String, Integer>();
        var m = TOKEN.matcher(query);
        while (m.find()) qtf.put(m.group(), qtf.getOrDefault(m.group(), 0) + 1);

        int N = docs.size();
        List<int[]> order = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            double score = 0.0;
            for (var e : qtf.entrySet()) {
                String t = e.getKey();
                int q = e.getValue();
                int tf = termFreq.get(i).getOrDefault(t, 0);
                int df = docFreq.getOrDefault(t, 0);
                if (df == 0) continue;
                double idf = Math.log(1 + (double)N / df);
                score += q * tf * idf;
            }
            order.add(new int[]{i, (int)(score * 1000)});
        }
        order.sort((a,b) -> Integer.compare(b[1], a[1]));
        List<String> res = new ArrayList<>();
        for (int i = 0; i < Math.min(k, order.size()); i++) res.add(docs.get(order.get(i)[0]));
        return res;
    }
}
