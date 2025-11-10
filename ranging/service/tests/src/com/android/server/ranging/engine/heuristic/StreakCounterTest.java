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

package com.android.server.ranging.engine.heuristic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.AlarmManager.OnAlarmListener;
import android.os.Handler;
import android.ranging.RangingData;
import android.ranging.RangingManager;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

@RunWith(JUnit4.class)
@SmallTest
public class StreakCounterTest {

    private static class TestHeuristic extends RangeHeuristic {
        TestHeuristic() {
            // Run the inner heuristic listener in a thread pool to test that streak counting is
            // thread-safe.
            super(Executors.newFixedThreadPool(10));
        }

        @Override
        public void onData(@NonNull RangingData data) {
            onHeuristicUpdated(0);
        }
    }

    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private @Mock RangingData mMockRangingData;
    private @Mock AlarmManager mMockAlarmManager;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingInjector mMockInjector;

    private final RangeHeuristic mInnerHeuristic = new TestHeuristic();

    private StreakCounter mStreakCounter;
    private StreakCounter.Streak mStreak;
    private int mLastStreakValue;

    @Before
    public void setup() {
        when(mMockInjector.getContext().getSystemService(eq(AlarmManager.class)))
                .thenReturn(mMockAlarmManager);

        @RangingManager.RangingTechnology int technology = RangingManager.UWB;
        TechnologyConfig mockConfig = mock(TechnologyConfig.class, Answers.RETURNS_DEEP_STUBS);

        when(mockConfig.getTechnology().getValue()).thenReturn(technology);
        when(mockConfig.getRangingInterval()).thenReturn(Duration.ofSeconds(1));
        when(mMockRangingData.getRangingTechnology()).thenReturn(technology);

        mLastStreakValue = 0;
        // Run the streak heuristic listener in the current thread so that updates to the streak
        // happen sequentially with the test logic.
        mStreakCounter = new StreakCounter(
                mockConfig, MoreExecutors.directExecutor(), mMockInjector);
        mStreak = mStreakCounter.count(mInnerHeuristic);
        mStreak.registerListener(streak -> mLastStreakValue = (int) streak);
    }

    public OnAlarmListener verifyIntervalListenerStarted() {
        ArgumentCaptor<OnAlarmListener> captor = ArgumentCaptor.forClass(OnAlarmListener.class);
        verify(mMockAlarmManager, atLeastOnce()).setExact(
                anyInt(), anyLong(), nullable(String.class), captor.capture(), any(Handler.class));
        return captor.getValue();
    }

    @Test
    public void onData_startsIntervalListener() {
        mStreakCounter.onData(mMockRangingData);
        verifyIntervalListenerStarted();
    }

    @Test
    public void onData_setsSuccessStreakTo1() {
        mStreakCounter.onData(mMockRangingData);
        Assert.assertEquals(1, mLastStreakValue);
    }

    @Test
    public void intervalTimeout_setsFailureStreakTo1() {
        mStreakCounter.onData(mMockRangingData);
        OnAlarmListener listener = verifyIntervalListenerStarted();
        listener.onAlarm();
        Assert.assertEquals(-1, mLastStreakValue);
    }

    @Test
    public void onData_100Times_setsSuccessStreakTo100() {
        IntStream.range(0, 100).forEach(unused -> mStreakCounter.onData(mMockRangingData));
        Assert.assertEquals(100, mLastStreakValue);
    }

    @Test
    public void intervalTimeout_100Times_setsFailureStreakTo100() {
        mStreakCounter.onData(mMockRangingData);
        OnAlarmListener listener = verifyIntervalListenerStarted();
        IntStream.range(0, 100).forEach(unused -> listener.onAlarm());
        Assert.assertEquals(-100, mLastStreakValue);
    }

    @Test
    public void intervalTimeout_startsFailureStreak_whenSuccessStreakPreExisting() {
        IntStream.range(0, 3).forEach(unused -> mStreakCounter.onData(mMockRangingData));
        OnAlarmListener listener = verifyIntervalListenerStarted();
        Assert.assertEquals(3, mLastStreakValue);

        listener.onAlarm();
        Assert.assertEquals(-1, mLastStreakValue);
    }

    @Test
    public void onData_startsSuccessStreak_whenFailureStreakPreExisting() {
        mStreakCounter.onData(mMockRangingData);
        OnAlarmListener listener = verifyIntervalListenerStarted();
        IntStream.range(0, 3).forEach(unused -> listener.onAlarm());
        Assert.assertEquals(-3, mLastStreakValue);

        mStreakCounter.onData(mMockRangingData);
        Assert.assertEquals(1, mLastStreakValue);
    }

    @Test
    public void onData_forDifferentTechnology_doesNotAffectStreak() {
        mStreakCounter.onData(mMockRangingData);

        RangingData mockRangingData = mock(RangingData.class);
        when(mockRangingData.getRangingTechnology()).thenReturn(RangingManager.BLE_CS);
        mStreakCounter.onData(mockRangingData);

        Assert.assertEquals(1, mLastStreakValue);
    }
}
