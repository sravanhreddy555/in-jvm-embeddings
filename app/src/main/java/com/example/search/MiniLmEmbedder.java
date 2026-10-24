package com.example.search;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/**
 * Runs all-MiniLM-L6-v2 inside the JVM with ONNX Runtime and turns any text into a 384-number vector.
 * Texts with similar meaning get vectors that point in a similar direction.
 */
@Component
public class MiniLmEmbedder implements AutoCloseable {

    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final OrtSession session;
    private final WordPieceTokenizer tokenizer;

    public MiniLmEmbedder(@Value("${model.dir}") Path modelDir,
                          @Value("${model.max-length:256}") int maxLength) throws IOException, OrtException {
        Path modelFile = modelDir.resolve("model.onnx");
        Path vocabFile = modelDir.resolve("vocab.txt");
        if (!Files.exists(modelFile) || !Files.exists(vocabFile)) {
            throw new IllegalStateException("model.onnx and vocab.txt not found in " + modelDir.toAbsolutePath()
                    + " (set model.dir or MODEL_DIR to the folder that contains them)");
        }
        // Load the model once and share it: session.run() is thread-safe.
        this.session = env.createSession(modelFile.toString());
        this.tokenizer = new WordPieceTokenizer(vocabFile, maxLength);
    }

    /** text -> token ids -> one vector per token -> average -> unit length. */
    public float[] embed(String text) {
        long[] tokenIds = tokenizer.encode(text);
        float[][] tokenVectors = runModel(tokenIds);
        float[] sentenceVector = meanPool(tokenVectors);
        return normalize(sentenceVector);
    }

    /** Runs the ONNX model. It returns one 384-number vector per token ("last_hidden_state"). */
    private float[][] runModel(long[] tokenIds) {
        long[] attentionMask = new long[tokenIds.length]; // 1 = real token (we never pad)
        long[] tokenTypeIds = new long[tokenIds.length];  // 0 = first sentence (we only have one)
        Arrays.fill(attentionMask, 1);

        try (OnnxTensor ids = OnnxTensor.createTensor(env, new long[][] {tokenIds});
             OnnxTensor mask = OnnxTensor.createTensor(env, new long[][] {attentionMask});
             OnnxTensor types = OnnxTensor.createTensor(env, new long[][] {tokenTypeIds});
             OrtSession.Result result = session.run(Map.of(
                     "input_ids", ids, "attention_mask", mask, "token_type_ids", types))) {
            float[][][] output = (float[][][]) result.get(0).getValue(); // [batch=1][tokens][384]
            return output[0];
        } catch (OrtException e) {
            throw new IllegalStateException("Model inference failed", e);
        }
    }

    /** One vector for the whole text: the average of its token vectors. */
    private static float[] meanPool(float[][] tokenVectors) {
        float[] average = new float[tokenVectors[0].length];
        for (float[] token : tokenVectors) {
            for (int i = 0; i < average.length; i++) {
                average[i] += token[i] / tokenVectors.length;
            }
        }
        return average;
    }

    /** Scale to length 1, so comparing two vectors is just a dot product (= cosine similarity). */
    private static float[] normalize(float[] vector) {
        double sumOfSquares = 0;
        for (float v : vector) {
            sumOfSquares += v * v;
        }
        float length = (float) Math.sqrt(sumOfSquares);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= length;
        }
        return vector;
    }

    @Override
    public void close() throws OrtException {
        session.close();
    }
}
