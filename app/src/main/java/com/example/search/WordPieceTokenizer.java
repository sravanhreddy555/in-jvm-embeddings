package com.example.search;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns text into the token ids the model was trained on (BERT WordPiece, using the model's vocab.txt).
 *
 * <p>"Embeddings are great!" becomes {@code [CLS] em ##bed ##ding ##s are great ! [SEP]}, as ids.
 */
public class WordPieceTokenizer {

    /** A word is a run of letters/digits; every punctuation mark is a word of its own. */
    private static final Pattern WORD = Pattern.compile("[\\p{Punct}\\p{P}]|[^\\s\\p{Punct}\\p{P}]+");

    private final Map<String, Integer> vocab = new HashMap<>();
    private final int maxLength;

    public WordPieceTokenizer(Path vocabFile, int maxLength) throws IOException {
        List<String> lines = Files.readAllLines(vocabFile);
        for (int i = 0; i < lines.size(); i++) {
            vocab.put(lines.get(i), i); // the line number is the token id
        }
        this.maxLength = maxLength;
    }

    /** {@code [CLS]} + word pieces + {@code [SEP]}, cut to maxLength like sentence-transformers does. */
    public long[] encode(String text) {
        List<String> tokens = new ArrayList<>();
        for (String word : splitWords(text)) {
            tokens.addAll(wordPieces(word));
        }
        if (tokens.size() > maxLength - 2) {
            tokens = tokens.subList(0, maxLength - 2);
        }

        long[] ids = new long[tokens.size() + 2];
        ids[0] = vocab.get("[CLS]");
        for (int i = 0; i < tokens.size(); i++) {
            ids[i + 1] = vocab.get(tokens.get(i));
        }
        ids[ids.length - 1] = vocab.get("[SEP]");
        return ids;
    }

    /** Lowercase, remove accents ("café" -> "cafe"), split into words and punctuation. */
    List<String> splitWords(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{Mn}", "");

        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(noAccents);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    /**
     * Split one word into the longest pieces found in the vocabulary, e.g. "embeddings" -> em, ##bed,
     * ##ding, ##s. "##" marks a piece that continues a word. Unknown words become [UNK].
     */
    List<String> wordPieces(String word) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < word.length()) {
            String piece = null;
            int end = word.length();
            while (end > start) {
                String candidate = (start == 0 ? "" : "##") + word.substring(start, end);
                if (vocab.containsKey(candidate)) {
                    piece = candidate;
                    break;
                }
                end--;
            }
            if (piece == null) {
                return List.of("[UNK]");
            }
            pieces.add(piece);
            start = end;
        }
        return pieces;
    }
}
