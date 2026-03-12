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

package com.android.server.ranging.engine;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.ranging.RangingData;
import android.ranging.RangingMeasurement;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.HandlerExecutor;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.heuristic.RangeHeuristicEventFactory.RangeHeuristicEvent;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

/**
 * Unit tests for {@link UwbMakeBeforeBreakEngine}.
 */
@SmallTest
@RunWith(JUnit4.class)
public class UwbMakeBeforeBreakEngineTest {
    private static final RangingTechnology ALT_TECH = RangingTechnology.CS;
    private static final long TIMEOUT_MS = 1_000L;

    @Mock
    private RangingEngine.EngineListener mListener;
    @Mock
    private RangingInjector mInjector;
    @Mock
    private TechnologyConfig mUwbConfig;
    @Mock
    private TechnologyConfig mAltConfig;
    @Mock
    private Context mContext;
    @Mock
    private AlarmManager mAlarmManager;

    private InOrder mListenerInOrder;
    private UwbMakeBeforeBreakEngine mEngine;
    private HandlerThread mEngineThread;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mListenerInOrder = inOrder(mListener);
        mEngineThread = new HandlerThread("TestEngine");
        mEngineThread.start();
        final Handler handler = new Handler(mEngineThread.getLooper());

        when(mUwbConfig.getTechnology()).thenReturn(RangingTechnology.UWB);
        when(mAltConfig.getTechnology()).thenReturn(ALT_TECH);
        when(mInjector.getContext()).thenReturn(mContext);
        when(mContext.getSystemService(AlarmManager.class)).thenReturn(mAlarmManager);

        mEngine = new UwbMakeBeforeBreakEngine(
                ALT_TECH, mListener, new HandlerExecutor(handler), mInjector);
    }

    @After
    public void tearDown() throws Exception {
        mEngineThread.quitSafely();
        mEngineThread.join();
    }

    @Test
    public void swappingToUwbOnly() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));

        // In SWAPPING state, mNextEvent is whenAny(mOkToStopUwb, mOkToStopAlt)
        RangeHeuristicEvent okToStopAlt = mEngine.getOkToStopAlt();
        RangeHeuristicEvent swappingEvent = mEngine.getNextEvent();

        okToStopAlt.complete(okToStopAlt);

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .stopTechnologies(eq(Set.of(ALT_TECH)), eq(InternalReason.ENGINE_REQUEST));
    }

    @Test
    public void swappingToAltOnly() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));

        RangeHeuristicEvent okToStopUwb = mEngine.getOkToStopUwb();
        okToStopUwb.complete(okToStopUwb);

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .stopTechnologies(eq(Set.of(RangingTechnology.UWB)),
                                  eq(InternalReason.ENGINE_REQUEST));
    }

    @Test
    public void uwbOnlyToSwapping() {
        // Start and move to UWB_ONLY
        mEngine.start(Set.of(mUwbConfig, mAltConfig));
        mEngine.getOkToStopAlt().complete(mEngine.getOkToStopAlt());
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .stopTechnologies(eq(Set.of(ALT_TECH)), anyInt());

        // Now in UWB_ONLY state. mNextEvent should be mStartAlt.
        RangeHeuristicEvent startAlt = mEngine.getStartAlt();
        startAlt.complete(startAlt);

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .startTechnologies(eq(Set.of(ALT_TECH)));
    }

    @Test
    public void altOnlyToSwapping() {
        // Start and move to ALT_ONLY
        mEngine.start(Set.of(mUwbConfig, mAltConfig));
        mEngine.getOkToStopUwb().complete(mEngine.getOkToStopUwb());
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .stopTechnologies(eq(Set.of(RangingTechnology.UWB)), anyInt());

        // Now in ALT_ONLY state. mNextEvent should be mStartUwb.
        RangeHeuristicEvent startUwb = mEngine.getStartUwb();
        startUwb.complete(startUwb);

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .startTechnologies(eq(Set.of(RangingTechnology.UWB)));
    }

    @Test
    public void uwbFailureInUwbOnly_resets() {
        // Start and move to UWB_ONLY
        mEngine.start(Set.of(mUwbConfig, mAltConfig));
        mEngine.getOkToStopAlt().complete(mEngine.getOkToStopAlt());
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .stopTechnologies(eq(Set.of(ALT_TECH)), anyInt());

        // Simulate UWB failure
        mEngine.onTechnologyStopped(RangingTechnology.UWB, InternalReason.UNSUPPORTED);

        // Should restart both
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .startTechnologies(eq(Set.of(RangingTechnology.UWB, ALT_TECH)));
    }

    @Test
    public void altFailureInAltOnly_resets() {
        // Start and move to ALT_ONLY
        mEngine.start(Set.of(mUwbConfig, mAltConfig));
        mEngine.getOkToStopUwb().complete(mEngine.getOkToStopUwb());
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .stopTechnologies(eq(Set.of(RangingTechnology.UWB)), anyInt());

        // Simulate Alt failure
        mEngine.onTechnologyStopped(ALT_TECH, InternalReason.UNSUPPORTED);

        // Should restart both
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .startTechnologies(eq(Set.of(RangingTechnology.UWB, ALT_TECH)));
    }

    @Test
    public void uwbFailureInSwapping_movesToAltOnly() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));

        // Simulate UWB failure
        mEngine.onTechnologyStopped(RangingTechnology.UWB, InternalReason.UNSUPPORTED);

        // Verify it's in ALT_ONLY by checking if getNextEvent() returns mStartUwb.
        // Also verify mStartUwb can trigger transition back to swapping.
        RangeHeuristicEvent startUwb = mEngine.getStartUwb();
        startUwb.complete(startUwb);

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .startTechnologies(eq(Set.of(RangingTechnology.UWB)));
    }

    @Test
    public void altFailureInSwapping_movesToUwbOnly() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));

        // Simulate Alt failure
        mEngine.onTechnologyStopped(ALT_TECH, InternalReason.UNSUPPORTED);

        // Verify it's in UWB_ONLY by checking if getNextEvent() returns mStartAlt.
        RangeHeuristicEvent startAlt = mEngine.getStartAlt();
        startAlt.complete(startAlt);

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .startTechnologies(eq(Set.of(ALT_TECH)));
    }

    @Test
    public void technologyFailureEvent_stopsTechnology() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));

        // Simulate UWB failure event from heuristic
        RangeHeuristicEvent uwbFailure = mEngine.getUwbFailure();
        uwbFailure.complete(uwbFailure);

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .stopTechnologies(eq(Set.of(RangingTechnology.UWB)),
                                  eq(InternalReason.SYSTEM_POLICY));
    }

    @Test
    public void noDataTimeout_stopsSession() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));

        ArgumentCaptor<AlarmManager.OnAlarmListener> captor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager, timeout(TIMEOUT_MS).atLeast(3)).setExact(
                anyInt(), anyLong(), eq(null), captor.capture(), eq(null));

        AlarmManager.OnAlarmListener noDataListener = captor.getAllValues().get(2);
        noDataListener.onAlarm();

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS)).stopSession();
    }

    @Test
    public void uwbFailure_detected_whileAltWorking() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));

        // Simulate Alt producing data. This resets mNoDataTimeout.
        mEngine.onData(new RangingData.Builder()
                .setRangingTechnology((int) ALT_TECH.getValue())
                .setDistance(new RangingMeasurement.Builder().setMeasurement(5.0).build())
                .setTimestampMillis(1000L)
                .build());

        // Trigger UWB failure event from heuristic.
        // Even though Alt is working, UWB failure should be detected and UWB stopped.
        RangeHeuristicEvent uwbFailure = mEngine.getUwbFailure();
        uwbFailure.complete(uwbFailure);

        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .stopTechnologies(eq(Set.of(RangingTechnology.UWB)),
                                  eq(InternalReason.SYSTEM_POLICY));
    }

    @Test
    public void dualFailureInSwapping_resets() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));

        // Simulate UWB failure first. Should move to ALT_ONLY.
        mEngine.onTechnologyStopped(RangingTechnology.UWB, InternalReason.UNSUPPORTED);

        // Simulate Alt failure next. Should trigger reset().
        mEngine.onTechnologyStopped(ALT_TECH, InternalReason.UNSUPPORTED);

        // Should restart both
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS))
                .startTechnologies(eq(Set.of(RangingTechnology.UWB, ALT_TECH)));
    }
}
