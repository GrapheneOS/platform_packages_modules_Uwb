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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.ranging.RangingData;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.server.ranging.engine.EngineEventFactory.EngineEvent;
import com.android.server.ranging.engine.heuristic.RangeHeuristic;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;


@RunWith(JUnit4.class)
@SmallTest
public class EngineEventFactoryTest {

    private static class TestHeuristic extends RangeHeuristic {
        TestHeuristic() {
            super(MoreExecutors.newDirectExecutorService());
        }

        @Override
        public void onData(@NonNull RangingData data) { }

        void triggerUpdate(double value) {
            onHeuristicUpdated(value);
        }
    }

    private interface EventListener{
        void eventOccurred(EngineEvent event);
    }

    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private @Mock EventListener mMockListener;

    private EngineEventFactory mEventFactory;

    @Before
    public void setup() {
        mEventFactory = new EngineEventFactory(MoreExecutors.newDirectExecutorService());
    }

    private boolean always(double unused) {
        return true;
    }

    @Test
    public void heuristicEvent_doesNotOccur_whenHeuristicFailsThreshold() {
        TestHeuristic heuristic = new TestHeuristic();

        EngineEvent event = mEventFactory.when(heuristic.threshold(value -> ((int) value) > 10));
        event.onNextOccurrence(mMockListener::eventOccurred);

        heuristic.triggerUpdate(0);
        verify(mMockListener, never()).eventOccurred(any());
    }

    @Test
    public void heuristicEvent_doesOccur_whenHeuristicMeetsThreshold() {
        TestHeuristic heuristic = new TestHeuristic();

        EngineEvent event = mEventFactory.when(heuristic.threshold(value -> ((int) value) >= 10));
        event.onNextOccurrence(mMockListener::eventOccurred);

        heuristic.triggerUpdate(11);
        verify(mMockListener).eventOccurred(eq(event));
    }

    @Test
    public void heuristicEvent_callsNotifierOnlyOnce() {
        TestHeuristic heuristic = new TestHeuristic();

        EngineEvent event = mEventFactory.when(heuristic.threshold(this::always));
        event.onNextOccurrence(mMockListener::eventOccurred);

        heuristic.triggerUpdate(0);
        heuristic.triggerUpdate(0);
        heuristic.triggerUpdate(0);
        verify(mMockListener, times(1)).eventOccurred(eq(event));
    }

    @Test
    public void heuristicConjunctionEvent_doesNotOccur_whenNotAllHeuristicsMeetThreshold() {
        List<TestHeuristic> heuristics = List.of(
                new TestHeuristic(),
                new TestHeuristic(),
                new TestHeuristic());

        EngineEvent event = mEventFactory.whenAll(
                heuristics.get(0).threshold(value -> value == 0),
                heuristics.get(1).threshold(value -> value == 1),
                heuristics.get(2).threshold(value -> value == 2));
        event.onNextOccurrence(mMockListener::eventOccurred);

        heuristics.get(0).triggerUpdate(0);
        heuristics.get(1).triggerUpdate(1);
        heuristics.get(2).triggerUpdate(100);

        verify(mMockListener, never()).eventOccurred(any());
    }

    @Test
    public void heuristicConjunctionEvent_doesOccur_whenAllHeuristicsMeetThreshold() {
        List<TestHeuristic> heuristics = List.of(
                new TestHeuristic(),
                new TestHeuristic(),
                new TestHeuristic());

        EngineEvent event = mEventFactory.whenAll(
                heuristics.get(0).threshold(this::always),
                heuristics.get(1).threshold(this::always),
                heuristics.get(2).threshold(this::always));
        event.onNextOccurrence(mMockListener::eventOccurred);

        heuristics.forEach(heuristic -> heuristic.triggerUpdate(0));
        verify(mMockListener, times(1)).eventOccurred(eq(event));
    }

    @Test
    public void raceEvent_returnsFirstEventThatMeetsThreshold() {
        List<TestHeuristic> heuristics = List.of(
                new TestHeuristic(),
                new TestHeuristic(),
                new TestHeuristic());

        List<EngineEvent> events = List.of(
                mEventFactory.when(heuristics.get(0).threshold(value -> value == 0)),
                mEventFactory.when(heuristics.get(1).threshold(value -> value == 1)),
                mEventFactory.when(heuristics.get(2).threshold(value -> value == 2)));

        EngineEvent event = mEventFactory.whenAny(events.get(0), events.get(1), events.get(2));
        event.onNextOccurrence(mMockListener::eventOccurred);

        heuristics.get(0).triggerUpdate(1);
        verify(mMockListener, never()).eventOccurred(any());

        heuristics.get(1).triggerUpdate(1);
        heuristics.get(2).triggerUpdate(2);
        verify(mMockListener, times(1)).eventOccurred(eq(events.get(1)));
    }
}
