package com.example.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Java version must give the same tokens and (practically) the same vectors as the Python
 * sentence-transformers library. fixtures.json holds Python's output, generated once.
 */
class EmbeddingParityTest {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fixture(String text, @JsonProperty("input_ids") long[] inputIds, float[] embedding) {
        @Override
        public String toString() { // shown as the test name
            if (text.isBlank()) {
                return "(empty text)";
            }
            String shown = text.length() > 50 ? text.substring(0, 50) + "..." : text;
            return shown.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FixtureFile(List<Fixture> fixtures) {
    }

    static final Path MODEL_DIR = Path.of("models/all-MiniLM-L6-v2");
    static MiniLmEmbedder embedder;
    static WordPieceTokenizer tokenizer;

    @BeforeAll
    static void loadModel() throws Exception {
        embedder = new MiniLmEmbedder(MODEL_DIR, 256);
        tokenizer = new WordPieceTokenizer(MODEL_DIR.resolve("vocab.txt"), 256);
    }

    static List<Fixture> fixtures() throws Exception {
        try (InputStream in = EmbeddingParityTest.class.getResourceAsStream("/fixtures.json")) {
            return new ObjectMapper().readValue(in, FixtureFile.class).fixtures();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void sameTokensAndVectorAsPython(Fixture fixture) {
        assertThat(tokenizer.encode(fixture.text())).containsExactly(fixture.inputIds());
        assertThat(cosine(embedder.embed(fixture.text()), fixture.embedding())).isGreaterThanOrEqualTo(0.9999);
    }

    static double cosine(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
