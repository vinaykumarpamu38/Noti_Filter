package com.techy.noti_filter.AI_Model;

public class PredictionResult {
    public final int predictedClass;
    public final float confidence;

    public PredictionResult(int predictedClass, float confidence) {
        this.predictedClass = predictedClass;
        this.confidence = confidence;
    }
}