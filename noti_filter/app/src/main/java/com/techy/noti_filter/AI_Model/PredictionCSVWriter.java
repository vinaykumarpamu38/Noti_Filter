package com.techy.noti_filter.AI_Model;

import android.content.Context;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PredictionCSVWriter {

    private static final String FILE_NAME = "predictions_28Jul.csv";

    public static void writePrediction(Context context, String[] data) {
        try {
            File file = new File(
                    context.getExternalFilesDir(null),
                    FILE_NAME
            );

            boolean fileExists = file.exists();

            FileWriter writer = new FileWriter(file, true);
            CSVWriter csvWriter = new CSVWriter(writer);

            if (!fileExists) {
                String[] header = {
                        "title",
                        "body",
                        "title_len",
                        "body_len",
                        "priority",
                        "hour",
                        "type",
                        "app",
                        "predicted_label"
                };
                csvWriter.writeNext(header);
            }

            csvWriter.writeNext(data);
            csvWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}