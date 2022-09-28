/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.uwb;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/**
 * Test of {@link UwbOemExtensionCallbackListener}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class UwbOemExtensionCallbackListenerTest {

    @Mock
    private IUwbAdapter mIUwbAdapter;
    @Mock
    private UwbManager.UwbOemExtensionCallback mUwbOemExtensionCallback;
    @Mock
    private UwbManager.UwbOemExtensionCallback mUwbOemExtensionCallback2;
    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();

    private UwbOemExtensionCallbackListener mUwbOemExtensionCallbackListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mUwbOemExtensionCallbackListener = new UwbOemExtensionCallbackListener(mIUwbAdapter);
    }

    @Test
    public void testRegisterUnregister() throws Exception {
        // Register callback
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        verify(mIUwbAdapter, times(1))
                .registerOemExtensionCallback(mUwbOemExtensionCallbackListener);
        // Unregister first callback
        mUwbOemExtensionCallbackListener.unregister(mUwbOemExtensionCallback);
        verify(mIUwbAdapter, times(1))
                .unregisterOemExtensionCallback(mUwbOemExtensionCallbackListener);

        // Register second callback
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback2);
        verify(mIUwbAdapter, times(2))
                .registerOemExtensionCallback(mUwbOemExtensionCallbackListener);
        // Unregister second callback
        mUwbOemExtensionCallbackListener.unregister(mUwbOemExtensionCallback2);
        verify(mIUwbAdapter, times(2))
                .unregisterOemExtensionCallback(mUwbOemExtensionCallbackListener);
    }
}
