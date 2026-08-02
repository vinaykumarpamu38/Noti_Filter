package com.techy.noti_filter.AI_Model;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * Loads notification_model.tflite (via ModelFileSource - bundled default,
 * or a downloaded+accepted retrain if one is active) and runs inference.
 *
 * IMPORTANT: the underlying model now outputs 4 raw probabilities (the
 * merged 4-class scheme: [label0, label2, label3, label4]), not 5. This
 * class remaps that into a 5-length array where array INDEX equals the
 * real LABEL VALUE (index 1, "label 1", is always 0 since that class was
 * merged away) - this keeps NotificationService.java's existing
 * "index == label" assumption correct with zero changes needed there.
 * The mapping itself comes from feature_spec.json's class_index_to_label,
 * so this stays correct even if a future retrain changes the scheme
 * (e.g. back to 5-class) without needing a code change here either.
 */
public class NotificationPredictor {

    private static final String TAG = "NotificationPredictor";
    private static final int OUTPUT_CONTRACT_LENGTH = 5; // NotificationService.java expects this length

    private Interpreter interpreter;
    private final int expectedInputSize;
    private int[] classIndexToLabel; // e.g. [0, 2, 3, 4]
    private int rawOutputSize;       // e.g. 4

    public NotificationPredictor(Context context) {
        loadClassMapping(context);
        this.expectedInputSize = loadModel(context);
    }

    private void loadClassMapping(Context context) {
        try {
            ModelFileSource fileSource = new ModelFileSource(context);
            InputStream is = fileSource.open("feature_spec.json");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            org.json.JSONObject json = new org.json.JSONObject(sb.toString());
            org.json.JSONArray arr = json.getJSONArray("class_index_to_label");
            classIndexToLabel = new int[arr.length()];
            for (int i = 0; i < arr.length(); i++) classIndexToLabel[i] = arr.getInt(i);
            rawOutputSize = classIndexToLabel.length;
            Log.d(TAG, "class_index_to_label = " + Arrays.toString(classIndexToLabel));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load class_index_to_label, defaulting to 4-class merged scheme", e);
            classIndexToLabel = new int[]{0, 2, 3, 4};
            rawOutputSize = 4;
        }
    }

    private int loadModel(Context context) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            interpreter = new Interpreter(loadModelFile(context), options);
            printModelInfo();

            int[] inputShape = interpreter.getInputTensor(0).shape();
            int inputSize = inputShape.length > 1 ? inputShape[1] : -1;

            Log.d(TAG, "✅ Model loaded successfully. Expected input size: " + inputSize);
            return inputSize;

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to load TFLite model", e);
            e.printStackTrace();
            return -1;
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        ModelFileSource fileSource = new ModelFileSource(context);

        if (fileSource.hasLocalFile("notification_model.tflite")) {
            // A downloaded model is active - internal storage file, not an
            // asset, so it's mapped via a plain FileInputStream/FileChannel.
            File local = fileSource.localFile("notification_model.tflite");
            try (FileInputStream fis = new FileInputStream(local)) {
                FileChannel channel = fis.getChannel();
                return channel.map(FileChannel.MapMode.READ_ONLY, 0, local.length());
            }
        }

        android.content.res.AssetFileDescriptor fileDescriptor =
                context.getAssets().openFd("notification_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void printModelInfo() {
        if (interpreter == null) return;
        try {
            Log.d(TAG, "=== TFLite Model Information ===");
            int inputCount = interpreter.getInputTensorCount();
            for (int i = 0; i < inputCount; i++) {
                int[] shape = interpreter.getInputTensor(i).shape();
                String type = interpreter.getInputTensor(i).dataType().name();
                Log.d(TAG, "Input " + i + ": shape=" + Arrays.toString(shape) + ", type=" + type);
            }
            int outputCount = interpreter.getOutputTensorCount();
            for (int i = 0; i < outputCount; i++) {
                int[] shape = interpreter.getOutputTensor(i).shape();
                Log.d(TAG, "Output " + i + ": shape=" + Arrays.toString(shape));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not print model details", e);
        }
    }

    /**
     * Predict using a preprocessed float array. Returns a 5-length array
     * where index == real label value (index 1 always 0, since label 1
     * doesn't exist in the merged scheme) - NOT the model's own raw
     * 4-length output. Callers (NotificationService.java) are unchanged.
     */
    public float[] predict(float[] inputFeatures) {
        float[] zero5 = new float[]{0f, 0f, 0f, 0f, 0f};

        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null. Model failed to load.");
            return zero5;
        }
        if (inputFeatures == null || inputFeatures.length == 0) {
            Log.w(TAG, "Empty or null inputFeatures passed to model");
            return zero5;
        }
        if (expectedInputSize > 0 && inputFeatures.length != expectedInputSize) {
            Log.e(TAG, "Input size mismatch! Expected: " + expectedInputSize + ", Got: " + inputFeatures.length);
            return zero5;
        }

        try {
            float[][] inputTensor = new float[1][inputFeatures.length];
            System.arraycopy(inputFeatures, 0, inputTensor[0], 0, inputFeatures.length);

            float[][] rawOutputTensor = new float[1][rawOutputSize];
            interpreter.run(inputTensor, rawOutputTensor);

            // Remap raw model output (length = rawOutputSize, e.g. 4) into
            // the fixed 5-length, index-equals-label contract.
            float[] remapped = new float[OUTPUT_CONTRACT_LENGTH];
            for (int i = 0; i < rawOutputSize; i++) {
                int label = classIndexToLabel[i];
                if (label >= 0 && label < OUTPUT_CONTRACT_LENGTH) {
                    remapped[label] = rawOutputTensor[0][i];
                }
            }
            return remapped;

        } catch (Exception e) {
            Log.e(TAG, "Error during model inference", e);
            return zero5;
        }
    }

    /** Get predicted class and confidence - predictedClass is now a real
     * label value (0/2/3/4), matching predict()'s remapped output. */
    public PredictionResult getPredictionResult(float[] probabilities) {
        if (probabilities == null || probabilities.length == 0) {
            return new PredictionResult(0, 0f);
        }
        int bestClass = 0;
        float maxProb = probabilities[0];
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i];
                bestClass = i;
            }
        }
        return new PredictionResult(bestClass, maxProb);
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
            Log.d(TAG, "Interpreter closed successfully");
        }
    }

    public int getExpectedInputSize() {
        return expectedInputSize;
    }
}
