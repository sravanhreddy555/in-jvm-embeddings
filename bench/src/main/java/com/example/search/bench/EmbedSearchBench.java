package com.example.search.bench;

import com.example.search.MiniLmEmbedder;
import com.example.search.SemanticSearchService;
import com.example.search.SemanticSearchService.Document;
import com.example.search.SemanticSearchService.Hit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Latency (p50/p99) of embedding a query, embedding the longest posting, and a top-5 search.
 * Run from the repo root so app/models and app/data resolve.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class EmbedSearchBench {

    static final Path MODEL_DIR = Path.of("app/models/all-MiniLM-L6-v2");
    static final Path DATA_PATH = Path.of("app/data/jobs.json");
    static final List<String> FULL_POSTING = List.of("title", "description", "requirements");
    static final String QUERY = "someone to keep production Kubernetes clusters reliable";

    @State(Scope.Benchmark)
    public static class Model {
        MiniLmEmbedder embedder;
        String longestPosting;

        @Setup
        public void load() throws Exception {
            embedder = new MiniLmEmbedder(MODEL_DIR, 256);
            longestPosting = SemanticSearchService.readDocuments(DATA_PATH, FULL_POSTING).stream()
                    .map(Document::text).max(Comparator.comparingInt(String::length)).orElseThrow();
        }

        @TearDown
        public void close() throws Exception {
            embedder.close();
        }
    }

    /** Random unit vectors stand in for embedded documents: search time depends only on how many there are. */
    @State(Scope.Benchmark)
    public static class Index {
        @Param({"500", "10000", "100000"})
        int size;
        SemanticSearchService service;
        float[] query;

        @Setup
        public void fill() {
            Random random = new Random(42);
            service = new SemanticSearchService(null, null, List.of()); // add() and search(float[]) don't use the model
            for (int i = 0; i < size; i++) {
                service.add(new Document("doc-" + i, "", ""), randomUnitVector(random));
            }
            query = randomUnitVector(random);
        }
    }

    @Benchmark
    public float[] embedQuery(Model model) {
        return model.embedder.embed(QUERY);
    }

    @Benchmark
    public float[] embedPosting(Model model) {
        return model.embedder.embed(model.longestPosting);
    }

    @Benchmark
    public List<Hit> search(Index index) {
        return index.service.search(index.query, 5);
    }

    static float[] randomUnitVector(Random random) {
        float[] v = new float[384];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) random.nextGaussian();
        }
        return normalize(v);
    }

    static float[] normalize(float[] v) {
        double sumOfSquares = 0;
        for (float x : v) {
            sumOfSquares += x * x;
        }
        float length = (float) Math.sqrt(sumOfSquares);
        for (int i = 0; i < v.length; i++) {
            v[i] /= length;
        }
        return v;
    }
}
