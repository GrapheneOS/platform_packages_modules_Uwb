/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.ranging.common;

import android.app.AlarmManager;
import android.os.SystemClock;

import com.android.server.ranging.RangingInjector;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class TimeoutListener {
    private final AlarmManager.OnAlarmListener mOnAlarmListener;
    private final AtomicBoolean mIsStarted;
    private final AlarmManager mAlarmManager;
    private final RangingInjector mInjector;

    public TimeoutListener(Runnable onTimeout, RangingInjector injector) {
        mOnAlarmListener = new AlarmManager.OnAlarmListener() {
            @Override
            public void onAlarm() {
                onTimeout.run();
            }
        };
        mIsStarted = new AtomicBoolean(false);
        mAlarmManager = injector.getContext().getSystemService(AlarmManager.class);
        mInjector = injector;
    }

    public void start(Duration timeout) {
        if (mIsStarted.compareAndSet(false, true)) {
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + timeout.toMillis(), null, mOnAlarmListener,
                    mInjector.getAlarmHandler());
        }
    }

    public void cancel() {
        if (mIsStarted.compareAndSet(true, false)) {
            mAlarmManager.cancel(mOnAlarmListener);
        }
    }

    public void reset(Duration timeout) {
        cancel();
        start(timeout);
    }
}
