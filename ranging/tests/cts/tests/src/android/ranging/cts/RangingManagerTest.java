/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.ranging.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.ranging.RangingManager;
import android.ranging.RangingSession;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get RangingManager in instant app mode")
public class RangingManagerTest {
    private static final String TAG = "RangingManagerTest";
    private final Context mContext = InstrumentationRegistry.getContext();
    private RangingManager mRangingManager;
    @Before
    public void setup() throws Exception {
        mRangingManager = mContext.getSystemService(RangingManager.class);
        assertThat(mRangingManager).isNotNull();
    }

    @After
    public void teardown() throws Exception {
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testCreatingRangingSession() {
        RangingSession rangingSession = mRangingManager.createRangingSession();
        assertThat(rangingSession).isNotNull();
    }
}
