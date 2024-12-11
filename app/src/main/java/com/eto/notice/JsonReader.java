package com.eto.notice;

import android.content.Context;
import android.content.res.AssetManager;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class JsonReader {

    public static String readJsonFromAsset(Context context, String fileName) {
        try {
            AssetManager assetManager = context.getAssets();
            InputStream inputStream = assetManager.open(fileName);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            bufferedReader.close();
            return stringBuilder.toString();
        } catch (IOException e) {
            Toast.makeText(context, "JSON读取错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return null;
    }
}
