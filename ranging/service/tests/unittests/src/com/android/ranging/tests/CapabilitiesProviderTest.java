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

package com.android.server.ranging.tests;

import static com.android.server.ranging.CapabilitiesProvider.AvailabilityChangedReason.SYSTEM_POLICY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.RemoteException;
import android.ranging.IRangingCapabilitiesCallback;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;
import com.android.server.ranging.CapabilitiesProvider.TechnologyAvailabilityListener;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.EnumMap;

@RunWith(JUnit4.class)
@SmallTest
public class CapabilitiesProviderTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    @Mock
    private RangingInjector mMockInjector;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IRangingCapabilitiesCallback mMockCallback;

    private final EnumMap<RangingTechnology, CapabilitiesAdapter> mMockAdapters =
            new EnumMap<>(RangingTechnology.class);

    private CapabilitiesProvider mCapabilitiesProvider;

    public void registerInitialCallback() {
        mCapabilitiesProvider.registerCapabilitiesCallback(mMockCallback);

        for (RangingTechnology technology : RangingTechnology.TECHNOLOGIES) {
            ArgumentCaptor<TechnologyAvailabilityListener> captor =
                    ArgumentCaptor.forClass(TechnologyAvailabilityListener.class);

            verify(mMockInjector)
                    .createCapabilitiesAdapter(eq(technology), captor.capture());

            when(mMockAdapters.get(technology).getAvailabilityListener())
                    .thenReturn(captor.getValue());
        }
    }

    @Before
    public void setup() {
        for (RangingTechnology technology : RangingTechnology.TECHNOLOGIES) {
            CapabilitiesAdapter adapter = mock(CapabilitiesAdapter.class);
            mMockAdapters.put(technology, adapter);
            when(mMockInjector.createCapabilitiesAdapter(eq(technology), any()))
                    .thenReturn(adapter);

        }

        mCapabilitiesProvider = new CapabilitiesProvider(mMockInjector);
    }

    @Test
    public void should_sendCapabilitiesAfterRegistration() throws RemoteException {
        registerInitialCallback();

        verify(mMockCallback).onRangingCapabilities(
                argThat((capabilities) -> capabilities
                        .getTechnologyAvailability()
                        .keySet()
                        .containsAll(RangingTechnology.TECHNOLOGIES
                                .stream()
                                .map(RangingTechnology::getValue).toList())
                )
        );

        for (CapabilitiesAdapter adapter : mMockAdapters.values()) {
            Assert.assertNotNull(adapter.getAvailabilityListener());
            verify(adapter).getCapabilities();
            verify(adapter).getAvailability();
        }
    }

    @Test
    public void should_sendCapabilitiesWhenAdapterAvailabilityChanges() throws RemoteException {
        InOrder inOrder = inOrder(mMockCallback);

        registerInitialCallback();
        inOrder.verify(mMockCallback).onRangingCapabilities(any());

        CapabilitiesAdapter adapter = mMockAdapters.get(RangingTechnology.UWB);
        Assert.assertNotNull(adapter.getAvailabilityListener());

        Integer mockAvailability = mock(Integer.class);
        adapter.getAvailabilityListener().onAvailabilityChange(mockAvailability, SYSTEM_POLICY);
        inOrder.verify(mMockCallback).onRangingCapabilities(
                argThat((capabilities) -> capabilities
                        .getTechnologyAvailability()
                        .get(RangingTechnology.UWB.getValue()).equals(mockAvailability)
                )
        );
    }

    @Test
    public void shouldNot_getCapabilitiesFromAdaptersWhenCached() throws RemoteException {
        registerInitialCallback();
        mCapabilitiesProvider.registerCapabilitiesCallback(mMockCallback);

        verify(mMockCallback, times(2)).onRangingCapabilities(any());
        for (CapabilitiesAdapter adapter : mMockAdapters.values()) {
            verify(adapter, times(1)).getCapabilities();
        }
    }

    @Test
    public void
    should_notifyMultipleCallbacksWhenAdapterAvailabilityChanges() throws RemoteException {
        IRangingCapabilitiesCallback anotherMockCallback =
                mock(IRangingCapabilitiesCallback.class, Answers.RETURNS_DEEP_STUBS);

        registerInitialCallback();
        mCapabilitiesProvider.registerCapabilitiesCallback(anotherMockCallback);

        for (CapabilitiesAdapter adapter : mMockAdapters.values()) {
            Assert.assertNotNull(adapter.getAvailabilityListener());
            adapter.getAvailabilityListener()
                    .onAvailabilityChange(mock(Integer.class), mock(Integer.class));
        }

        verify(mMockCallback, times(1 + mMockAdapters.size())).onRangingCapabilities(any());
        verify(anotherMockCallback, times(1 + mMockAdapters.size())).onRangingCapabilities(any());
    }
}
