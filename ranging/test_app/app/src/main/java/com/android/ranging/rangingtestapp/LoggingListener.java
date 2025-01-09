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

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static androidx.core.content.ContextCompat.startActivity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LoggingListener {
    private static String LOG_FILE_NAME = "logs.txt";

    private final Context mContext;
    private final File mLogFile;
    private final boolean mIsResponder;
    private final MutableLiveData<String> mLogText = new MutableLiveData<>();

    private static LoggingListener sInstance;
    public static LoggingListener getInstance() {
        return sInstance;
    }

    public LoggingListener(Context context, boolean isResponder) {
        mContext = context;
        mIsResponder = isResponder;
        mLogFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        sInstance = this;
    }

    public void log(String log) {
        mLogText.postValue(log);
        Log.i(mIsResponder ? "Responder" : "Initiator", log);
        FileWriter fw = null;
        try {
            fw = new FileWriter(mLogFile);
            fw.write(log);
        } catch (IOException e) {
        } finally {
            try {
                if (fw != null) fw.close();
            } catch (IOException e) {}
        }
    }

    public MutableLiveData<String> getLogText() {
        return mLogText;
    }

    public void openLogFile() {
        Uri path = Uri.fromFile(mLogFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(path);
        intent.setType("text/plain");
        try {
            mContext.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(mContext, "No application found", Toast.LENGTH_SHORT).show();
        }
    }
}
