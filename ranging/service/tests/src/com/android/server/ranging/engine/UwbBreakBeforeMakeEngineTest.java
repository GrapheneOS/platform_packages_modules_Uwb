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

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.HandlerExecutor;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

/**
 * Unit tests for {@link BreakBeforeMakeEngine}.
 */
@SmallTest
@RunWith(JUnit4.class)
public class UwbBreakBeforeMakeEngineTest {
    private static final RangingTechnology ALT_TECH = RangingTechnology.CS;
    private static final int UNREASONABLY_LONG_ALARM_WAIT_MS = 2_000;
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
    private AlarmManager mAlarmManager;
    @Mock
    private Context mContext;

    private InOrder mListenerInOrder;
    private UwbBreakBeforeMakeEngine mEngine;
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

        mEngine = new UwbBreakBeforeMakeEngine(
                ALT_TECH, mListener, new HandlerExecutor(handler), mInjector);
    }

    @After
    public void tearDown() throws Exception {
        // Stop the handler thread.
        mEngineThread.quitSafely();
        mEngineThread.join();
    }

    @Test
    public void start_withUwbAndAltConfigs_startsAlt() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));
        verify(mListener).startTechnologies(Set.of(ALT_TECH));
    }

    @Test
    public void start_withOnlyUwbConfig_throwsException() {
        assertThrows(IllegalStateException.class, () -> mEngine.start(Set.of(mUwbConfig)));
    }

    @Test
    public void start_withOnlyAltConfig_throwsException() {
        assertThrows(IllegalStateException.class, () -> mEngine.start(Set.of(mAltConfig)));
    }

    @Test
    public void onTechnologyStopped_notFromEneing() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));
        // By default, ALT is started.
        mListenerInOrder.verify(mListener).startTechnologies(Set.of(ALT_TECH));

        // Simulate technology stopped callback with unexpected reason. Engine should skip the
        // onTechnologyStopped.
        mEngine.onTechnologyStopped(ALT_TECH, InternalReason.UNSUPPORTED);
        mListenerInOrder.verify(mListener, never()).startTechnologies(
                Set.of(RangingTechnology.UWB));
    }

    @Test
    public void start_altFailure() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));
        // By default, ALT is started.
        mListenerInOrder.verify(mListener).startTechnologies(Set.of(ALT_TECH));
        // Simulate error on ALT_TECH. This should result in the stopTechnologies on the ALT_TECH
        EngineEventFactory.EngineEvent event = mEngine.getAltFailure();
        event.complete(event);
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS)).stopTechnologies(Set.of(ALT_TECH));
    }

    @Test
    public void start_uwbFailure() {
        // Transit to UWB
        altToUwbTransition();

        // Simulate error on UWB. This should result in the stopTechnologies on the UWB
        EngineEventFactory.EngineEvent event = mEngine.getUwbFailure();
        event.complete(event);
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS)).stopTechnologies(
                Set.of(RangingTechnology.UWB));
    }

    @Test
    public void testAltToUwbTransition() {
        altToUwbTransition();
    }

    private void altToUwbTransition() {
        mEngine.start(Set.of(mUwbConfig, mAltConfig));
        // By default, ALT is started.
        mListenerInOrder.verify(mListener).startTechnologies(Set.of(ALT_TECH));

        // A switch event should result in the ALT tech being stopped.
        EngineEventFactory.EngineEvent event = mEngine.getNextEvent();
        event.complete(event);
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS)).stopTechnologies(Set.of(ALT_TECH));

        // Simulate technology stopped callback, then verify UWB is started.
        mEngine.onTechnologyStopped(ALT_TECH, InternalReason.ENGINE_REQUEST);
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS)).startTechnologies(
                Set.of(RangingTechnology.UWB));
    }

    @Test
    public void testUwbToAltTransition() {
        // By default, ALT is started. Switch to Uwb first.
        altToUwbTransition();

        EngineEventFactory.EngineEvent event = mEngine.getNextEvent();
        event.complete(event);
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS)).stopTechnologies(
                Set.of(RangingTechnology.UWB));

        // Simulate technology stopped callback, then verify UWB is started.
        mEngine.onTechnologyStopped(RangingTechnology.UWB, InternalReason.ENGINE_REQUEST);
        mListenerInOrder.verify(mListener, timeout(TIMEOUT_MS)).startTechnologies(Set.of(ALT_TECH));
    }
}
