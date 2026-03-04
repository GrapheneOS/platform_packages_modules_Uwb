/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ranging.rangingtestapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.ranging.RangingManager;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class CanvasView extends View {
    private static final String LOG_TAG = "CanvasView";
    private static final int MAX_NODE_SIZE = 20;
    private static final int INITIAL_MAX_Y = 5;

    private final Map<Integer, ArrayList<Node>> mDataListMap;
    private final Map<Integer, Integer> mPreviousYMap;
    private final Paint mPaint;
    private final Paint mTextPaint;
    private final Paint mPointPaint;
    private final String mTitle;

    private int mMaxYValue = INITIAL_MAX_Y;
    private int mNodeCount = 1;

    CanvasView(Context context, String title) {
        super(context);
        mDataListMap = new HashMap<>();
        mPreviousYMap = new HashMap<>();
        mTitle = title;
        mPaint = new Paint();
        mTextPaint = new Paint();
        mPointPaint = new Paint();
    }

    private int getColorForTechnology(int technology) {
        switch (technology) {
            case RangingManager.UWB: return Color.BLUE;
            case RangingManager.BLE_CS: return Color.GREEN;
            case RangingManager.WIFI_NAN_RTT: return Color.RED;
            case RangingManager.WIFI_STA_RTT: return Color.YELLOW;
            case RangingManager.WIFI_PD: return Color.rgb(255, 165, 0); // Orange
            case RangingManager.BLE_RSSI: return Color.MAGENTA;
            default: return Color.CYAN;
        }
    }

    private String getTechnologyName(int technology) {
        switch (technology) {
            case RangingManager.UWB: return "UWB";
            case RangingManager.BLE_CS: return "BLE CS";
            case RangingManager.WIFI_NAN_RTT: return "Wi-Fi RTT";
            case RangingManager.WIFI_STA_RTT: return "Wi-Fi STA RTT";
            case RangingManager.WIFI_PD: return "Wi-Fi PD";
            case RangingManager.BLE_RSSI: return "BLE RSSI";
            default: return "Other (" + technology + ")";
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int viewWidth = getWidth();
        int viewHeight = getHeight();
        int startX = 100;
        int endX = viewWidth - 50;
        int startY = 80;
        int endY = viewHeight - 100;

        mTextPaint.setTextSize(24);
        mPaint.setColor(Color.WHITE);
        canvas.drawRect(0, 0, viewWidth, viewHeight, mPaint);

        mPaint.setColor(Color.GRAY);
        mPaint.setStrokeWidth(3);
        canvas.drawLine(startX, startY, startX, endY, mPaint);
        canvas.drawLine(startX, endY, endX, endY, mPaint);

        // Draw grid lines
        mPaint.setStrokeWidth(1);
        mPaint.setColor(Color.LTGRAY);
        int intervalY = (endY - startY) / 5;
        for (int i = 1; i <= 5; i++) {
            int y = endY - intervalY * i;
            int yValue = mMaxYValue / 5 * i;
            canvas.drawLine(startX, y, endX, y, mPaint);
            canvas.drawText(yValue + "", 40, y, mTextPaint);
        }
        canvas.drawText("0", 40, endY, mTextPaint);

        // Draw Title
        mTextPaint.setTextSize(32);
        canvas.drawText(mTitle, viewWidth / 2 - mTitle.length() * 6, startY - 40, mTextPaint);

        // Draw Legend
        int legendX = startX;
        int legendY = startY - 10;
        mTextPaint.setTextSize(20);
        for (Map.Entry<Integer, ArrayList<Node>> entry : mDataListMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                mTextPaint.setColor(getColorForTechnology(entry.getKey()));
                String techName = getTechnologyName(entry.getKey());
                String text = "■ " + techName;
                float width = mTextPaint.measureText(text);
                if (legendX + width > endX) {
                    legendX = startX;
                    legendY += 25;
                }
                canvas.drawText(text, legendX, legendY, mTextPaint);
                legendX += width + 20;
            }
        }
        mTextPaint.setColor(Color.BLACK); // reset

        // Draw Nodes
        int intervalX = (endX - startX) / MAX_NODE_SIZE;

        for (Map.Entry<Integer, ArrayList<Node>> entry : mDataListMap.entrySet()) {
            int technology = entry.getKey();
            ArrayList<Node> dataList = entry.getValue();
            int previousY = mPreviousYMap.getOrDefault(technology, endY);

            int currentX = startX + intervalX;
            mPointPaint.setColor(getColorForTechnology(technology));
            mPointPaint.setStrokeWidth(3);
            mPaint.setTextSize(16);
            mTextPaint.setTextSize(24);

            for (int i = 0; i < dataList.size(); i++) {
                Node node = dataList.get(i);
                if (node.abort) {
                    mPointPaint.setColor(Color.RED);
                    canvas.drawLine(
                            currentX - intervalX, previousY, currentX, previousY, mPointPaint);
                    canvas.drawCircle(currentX, previousY, 5, mPointPaint);
                    canvas.drawText("abort", currentX - 15, previousY - 10, mPaint);
                } else {
                    mPointPaint.setColor(getColorForTechnology(technology));
                    double distance = node.value;
                    int y = endY - (int) ((endY - startY) * (distance / mMaxYValue));
                    canvas.drawLine(currentX - intervalX, previousY, currentX, y, mPointPaint);
                    canvas.drawCircle(currentX, y, 5, mPointPaint);
                    mPaint.setColor(getColorForTechnology(technology));
                    canvas.drawText(String.format("%.1f", distance), currentX - 15, y - 10, mPaint);
                    previousY = y;
                }

                // Draw X-axis numbers only once (based on the first technology's list)
                if (technology == mDataListMap.keySet().iterator().next()) {
                    String number = node.number + "";
                    if (node.number % (MAX_NODE_SIZE / 20 * 5) == 0) {
                        mTextPaint.setColor(Color.BLACK);
                        if (number.length() > 2) {
                            canvas.rotate(-60, currentX - 15, endY + 50);
                            canvas.drawText(number, currentX - 15, endY + 50, mTextPaint);
                            canvas.rotate(60, currentX - 15, endY + 50);
                        } else {
                            canvas.drawText(number, currentX, endY + 30, mTextPaint);
                        }
                    }
                }
                currentX += intervalX;
            }
        }
    }

    void cleanUp() {
        mDataListMap.clear();
        mPreviousYMap.clear();
        mNodeCount = 0;
        mMaxYValue = INITIAL_MAX_Y;
        invalidate();
    }

    void addNode(int technology, double distance, boolean abort) {
        Log.d(LOG_TAG, "Add Node " + mNodeCount + " for tech: " + technology
                + " with distance:" + distance);

        if (!mDataListMap.containsKey(technology)) {
            mDataListMap.put(technology, new ArrayList<>());
            mPreviousYMap.put(technology, 650); // Default END_Y approximation
        }
        ArrayList<Node> dataList = mDataListMap.get(technology);

        if (abort && !dataList.isEmpty()) {
            distance = dataList.get(dataList.size() - 1).value;
        }
        dataList.add(new Node(distance, mNodeCount++, abort));
        if (distance > mMaxYValue) {
            mMaxYValue = ((int) (distance / 10)) * 10 + 10;
        }

        if (dataList.size() > MAX_NODE_SIZE) {
            double firstValue = dataList.get(0).value;
            // Use local approximation for previous Y if needed,
            // but addNode should ideally just store data and let onDraw handle scaling.
            mPreviousYMap.put(technology, 650 - (int) ((650 - 80) * (firstValue / mMaxYValue)));
            dataList.remove(0);
        }
        invalidate();
    }

    static class Node {
        private final double value;
        private final int number;
        private final boolean abort;

        Node(double value, int number, boolean abort) {
            this.value = value;
            this.number = number;
            this.abort = abort;
        }
    }
}
