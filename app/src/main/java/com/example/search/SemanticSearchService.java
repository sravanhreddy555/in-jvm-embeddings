package com.example.search;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Semantic search over documents kept in memory: embed every document once, then compare a query's
 * vector against all of them. Fine for a few thousand documents; beyond that use a vector database.
 */
@Service
public class SemanticSearchService {

    public record Document(String id, String title, String text) {
    }

    public record Hit(String id, String title, float score) {
    }

    private record Entry(Document document, float[] vector) {
    }

    private final MiniLmEmbedder embedder;
    private final Path dataPath;
    private final List<String> textFields;
    private final List<Entry> entries = new ArrayList<>();

    public SemanticSearchService(MiniLmEmbedder embedder,
                                 @Value("${data.path}") Path dataPath,
                                 @Value("${data.text-fields}") List<String> textFields) {
        this.embedder = embedder;
        this.dataPath = dataPath;
        this.textFields = textFields;
    }

    /** At startup: read the JSON file and embed every document. */
    @PostConstruct
    public void load() {
        for (Document document : readDocuments(dataPath, textFields)) {
            index(document);
        }
    }

    /** Embed a document and keep it. Call this from your own code to add documents later. */
    public void index(Document document) {
        add(document, embedder.embed(document.text()));
    }

    /** Keep a document whose vector you already have (it must be unit length). */
    public synchronized void add(Document document, float[] vector) {
        entries.add(new Entry(document, vector));
    }

    public List<Hit> search(String query, int k) {
        return search(embedder.embed(query), k);
    }

    /** Score every document against the query and return the k best. */
    public synchronized List<Hit> search(float[] queryVector, int k) {
        return entries.stream()
                .map(e -> new Hit(e.document().id(), e.document().title(), dot(queryVector, e.vector())))
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(k)
                .toList();
    }

    public synchronized int size() {
        return entries.size();
    }

    /** Both vectors have length 1, so their dot product is their cosine similarity (1 = same meaning). */
    private static float dot(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /** Reads {@code [{"id", "title", ...}]}; the text to embed is the given fields joined with ". ". */
    public static List<Document> readDocuments(Path file, List<String> textFields) {
        List<Document> documents = new ArrayList<>();
        for (JsonNode node : new ObjectMapper().readTree(file.toFile())) {
            List<String> parts = new ArrayList<>();
            for (String field : textFields) {
                JsonNode value = node.path(field);
                if (value.isArray()) {
                    value.forEach(item -> parts.add(item.asString())); // e.g. a list of requirements
                } else if (!value.isMissingNode()) {
                    parts.add(value.asString());
                }
            }
            documents.add(new Document(node.get("id").asString(), node.get("title").asString(), String.join(". ", parts)));
        }
        return documents;
    }
}
