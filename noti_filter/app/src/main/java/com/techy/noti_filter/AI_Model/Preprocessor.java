package com.techy.noti_filter.AI_Model;

import android.content.Context;
import android.util.Log;

import com.techy.noti_filter.model.NotificationData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds, on-device, the EXACT input vector the (rewritten) Python
 * training pipeline produces: [ TF-IDF block ] + [ target-encoded
 * categorical block: app, sender, category, type - 4 columns x n_classes
 * each ] + [ numeric block, scaled ].
 *
 * This replaced the original one-hot scheme (ohe_features.json) - see
 * train_notification_model.py's docstring for why: sender carries real
 * signal (some senders are >90% one label) and was never used before,
 * target encoding captures that; one-hot couldn't.
 *
 * Reads through ModelFileSource, so this transparently picks up a
 * downloaded+accepted retrained model instead of the bundled default,
 * with zero code changes needed when that happens.
 *
 * Requires these files (from feature_spec.json / vocab.json / idf.json /
 * app_encoding.json / sender_encoding.json / category_encoding.json /
 * type_encoding.json, all produced by train_notification_model.py):
 *   - feature_spec.json
 *   - vocab.json                 { "term": columnIndex, ... }
 *   - idf.json                   { "term": idfWeight, ... }
 *   - app_encoding.json          { "value": [c0,c1,c2,c3], ..., "__default__": [...] }
 *   - sender_encoding.json       (same shape)
 *   - category_encoding.json     (same shape)
 *   - type_encoding.json         (same shape)
 */
public class Preprocessor {

    private static final String TAG = "Preprocessor";

    // Must match numeric_columns_in_order in feature_spec.json exactly.
    private static final String[] NUM_COLUMNS = {
            "priority", "hour", "day", "title_len", "body_len",
            "has_media", "is_financial", "is_spammy"
    };

    // Must match categorical_columns_in_order in feature_spec.json exactly.
    private static final String[] CAT_COLUMNS = {"app", "sender", "category", "type"};

    private final ModelFileSource fileSource;

    private JSONArray scalerMean;
    private JSONArray scalerScale;
    private int[] classIndexToLabel; // model output index -> real label value, e.g. [0,2,3,4]

    private JSONObject vocabularyJson;   // term -> index
    private JSONObject idfJson;          // term -> idf weight

    private final Map<String, JSONObject> encodingTables = new HashMap<>(); // column -> {value: [..], "__default__": [..]}

    private List<String> financialKeywords = new ArrayList<>();
    private List<String> spamKeywords = new ArrayList<>();

    private int tfidfWidth = 0;
    private int tfidfStart = 0;
    private int catStart = 0;
    private int numStart = 0;
    private int inputDim = 0;
    private int numClasses = 0;

    public Preprocessor(Context context) {
        this.fileSource = new ModelFileSource(context);
        loadFeatureSpec();
        loadVocabulary();
        loadIdf();
        loadEncodingTables();
    }

    // ==========================================================
    // LOADING
    // ==========================================================

    private String readFile(String filename) throws Exception {
        InputStream is = fileSource.open(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private void loadFeatureSpec() {
        try {
            JSONObject json = new JSONObject(readFile("feature_spec.json"));

            this.inputDim = json.getInt("input_dim");

            JSONArray classArr = json.getJSONArray("class_index_to_label");
            classIndexToLabel = new int[classArr.length()];
            for (int i = 0; i < classArr.length(); i++) classIndexToLabel[i] = classArr.getInt(i);
            numClasses = classIndexToLabel.length;

            JSONObject layout = json.getJSONObject("block_layout");
            tfidfStart = layout.getJSONObject("1_tfidf").getInt("start");
            catStart = layout.getJSONObject("2_categorical").getInt("start");
            numStart = layout.getJSONObject("3_numeric").getInt("start");

            this.scalerMean = json.getJSONArray("numeric_scaler_mean");
            this.scalerScale = json.getJSONArray("numeric_scaler_scale");

            JSONObject kw = json.getJSONObject("keyword_flags");
            JSONArray fin = kw.getJSONArray("financial_keywords");
            for (int i = 0; i < fin.length(); i++) financialKeywords.add(fin.getString(i));

            JSONArray spam = kw.getJSONArray("spam_keywords");
            for (int i = 0; i < spam.length(); i++) spamKeywords.add(spam.getString(i));

            Log.d(TAG, "Feature spec loaded | inputDim=" + inputDim
                    + " classes=" + numClasses
                    + " tfidfStart=" + tfidfStart + " catStart=" + catStart + " numStart=" + numStart);

        } catch (Exception e) {
            Log.e(TAG, "Failed to load feature_spec.json", e);
        }
    }

    private void loadVocabulary() {
        try {
            vocabularyJson = new JSONObject(readFile("vocab.json"));
            tfidfWidth = vocabularyJson.length();
            Log.d(TAG, "Vocabulary loaded: " + tfidfWidth + " terms");
        } catch (Exception e) {
            Log.e(TAG, "Failed loading vocab.json", e);
        }
    }

    private void loadIdf() {
        try {
            idfJson = new JSONObject(readFile("idf.json"));
            Log.d(TAG, "IDF loaded: " + idfJson.length() + " terms");
        } catch (Exception e) {
            Log.e(TAG, "Failed loading idf.json", e);
        }
    }

    private void loadEncodingTables() {
        for (String col : CAT_COLUMNS) {
            try {
                JSONObject table = new JSONObject(readFile(col + "_encoding.json"));
                encodingTables.put(col, table);
                Log.d(TAG, "Loaded " + col + "_encoding.json: " + (table.length() - 1) + " known values");
            } catch (Exception e) {
                Log.e(TAG, "Failed loading " + col + "_encoding.json", e);
            }
        }
    }

    // ==========================================================
    // MAIN ENTRY POINT
    // ==========================================================

    public float[] process(NotificationData data) {
        try {
            if (data == null) {
                Log.e(TAG, "NotificationData is null");
                return new float[0];
            }

            float[] vector = new float[inputDim];

            // Training's combined_text = cleaned(title) + " | " + cleaned(body).
            // Sender is NOT part of this text block - it's used separately,
            // as its own categorical column, below.
            String cleanedTitle = cleanText(safe(data.title));
            String cleanedBody = cleanText(safe(data.body));
            String combinedText = cleanedTitle + " | " + cleanedBody;
            String tokenSource = cleanText(combinedText);

            writeTfidf(tokenSource, vector);
            writeCategorical(data, vector);
            writeNumeric(data, vector);

            Log.d(TAG, "Feature vector built, dim=" + vector.length);
            return vector;

        } catch (Exception e) {
            Log.e(TAG, "Error during preprocessing", e);
            return new float[0];
        }
    }

    /** Model output index -> real label value (e.g. index 1 -> label 2 in
     * the merged 4-class scheme). NotificationPredictor uses this to keep
     * its own output contract (index == label, length 5) working
     * unchanged regardless of which scheme is actually active. */
    public int[] getClassIndexToLabel() {
        return classIndexToLabel;
    }

    public int getNumClasses() {
        return numClasses;
    }

    // ==========================================================
    // TF-IDF  (unchanged algorithm from the one-hot version - unigrams +
    // bigrams, then L2-normalized, matching sklearn exactly)
    // ==========================================================

    private void writeTfidf(String text, float[] vector) throws Exception {
        String[] rawTokens = text.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String t : rawTokens) {
            if (t.length() >= 2) tokens.add(t);
        }

        Map<Integer, Float> weighted = new HashMap<>();
        for (String token : tokens) {
            addTermWeight(weighted, token);
        }
        for (int i = 0; i < tokens.size() - 1; i++) {
            String bigram = tokens.get(i) + " " + tokens.get(i + 1);
            addTermWeight(weighted, bigram);
        }

        double sumSquares = 0.0;
        for (float v : weighted.values()) sumSquares += (double) v * v;
        float norm = sumSquares > 0 ? (float) Math.sqrt(sumSquares) : 1f;

        for (Map.Entry<Integer, Float> entry : weighted.entrySet()) {
            vector[tfidfStart + entry.getKey()] = entry.getValue() / norm;
        }
    }

    private void addTermWeight(Map<Integer, Float> weighted, String term) throws Exception {
        if (!vocabularyJson.has(term)) return;
        int index = vocabularyJson.getInt(term);
        if (index < 0 || index >= tfidfWidth) return;
        if (!idfJson.has(term)) return;
        float weight = (float) idfJson.getDouble(term);
        weighted.merge(index, weight, Float::sum);
    }

    // ==========================================================
    // TARGET-ENCODED CATEGORICAL BLOCK
    // (app, sender, category, type - each contributes numClasses values,
    // looked up by value with a documented fallback for anything unseen)
    // ==========================================================

    private void writeCategorical(NotificationData data, float[] vector) {
        Map<String, String> values = new HashMap<>();
        values.put("app", safe(data.packageName));
        values.put("sender", safe(data.sender));
        values.put("category", safe(data.category));
        values.put("type", safe(data.type));

        int offset = catStart;
        for (String col : CAT_COLUMNS) {
            JSONObject table = encodingTables.get(col);
            float[] scores = lookupEncoding(table, values.get(col));
            for (int c = 0; c < numClasses; c++) {
                vector[offset + c] = c < scores.length ? scores[c] : 0f;
            }
            offset += numClasses;
        }
    }

    private float[] lookupEncoding(JSONObject table, String value) {
        if (table == null) return new float[numClasses];
        try {
            JSONArray arr = table.has(value) ? table.getJSONArray(value) : table.getJSONArray("__default__");
            float[] result = new float[arr.length()];
            for (int i = 0; i < arr.length(); i++) result[i] = (float) arr.getDouble(i);
            return result;
        } catch (Exception e) {
            return new float[numClasses];
        }
    }

    // ==========================================================
    // NUMERIC FEATURES  (must match NUM_COLUMNS order exactly)
    // ==========================================================

    private void writeNumeric(NotificationData data, float[] vector) throws JSONException {
        String raw = (safe(data.title) + " " + safe(data.body) + " " + safe(data.sender)).toLowerCase();
        boolean isFinancial = containsAny(raw, financialKeywords);
        boolean isSpammy = containsAny(raw, spamKeywords);

        double[] rawValues = new double[]{
                data.priority, data.hour, data.day, data.titleLength, data.bodyLength,
                data.hasMedia ? 1.0 : 0.0, isFinancial ? 1.0 : 0.0, isSpammy ? 1.0 : 0.0
        };

        for (int i = 0; i < NUM_COLUMNS.length; i++) {
            double mean = scalerMean.getDouble(i);
            double scale = scalerScale.getDouble(i);
            if (scale == 0.0) scale = 1.0;
            vector[numStart + i] = (float) ((rawValues[i] - mean) / scale);
        }
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    // ==========================================================
    // TEXT CLEANING  (must match Python clean_text exactly - unchanged)
    // ==========================================================

    private String cleanText(String text) {
        if (text == null) return "";
        text = text.toLowerCase();
        text = text.replaceAll("https?://\\S+|www\\.\\S+", "");
        text = text.replaceAll("[^a-z0-9\\s]", " ");
        text = text.replaceAll("\\s+", " ");
        return text.trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    public boolean isReady() {
        return scalerMean != null && scalerScale != null
                && vocabularyJson != null && idfJson != null
                && encodingTables.size() == CAT_COLUMNS.length
                && inputDim > 0 && numClasses > 0;
    }

    public int getExpectedInputDim() {
        return inputDim;
    }
}
