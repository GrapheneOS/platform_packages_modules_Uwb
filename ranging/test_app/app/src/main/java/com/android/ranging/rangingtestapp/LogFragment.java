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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class LogFragment extends Fragment {

    private TextView mLogText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_log, container, false);
        mLogText = root.findViewById(R.id.text_log);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // We might need a more robust way to get the current LoggingListener
        // but for now, we'll try to get it from the singleton.
        observeLogs();
    }

    private void observeLogs() {
        LoggingListener loggingListener = LoggingListener.getInstance();
        if (loggingListener != null) {
            loggingListener.getLogText().observe(getViewLifecycleOwner(), log -> {
                if (mLogText != null) {
                    mLogText.setText(log);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-check in case instance changed while we were paused
        observeLogs();
    }
}
