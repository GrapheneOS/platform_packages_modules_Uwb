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

package com.android.server.uwb.profile;

import static com.android.server.uwb.data.UwbConfig.CONTROLEE_AND_RESPONDER;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.UwbServiceCore;
import com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo;
import com.android.server.uwb.data.UwbConfig;
import com.android.server.uwb.pm.PacsControleeSession;
import com.android.server.uwb.pm.RangingSessionController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
// TODO : Add unit tests for states during implementation
public class PacsControleeSessionTest {
    @Mock
    private AttributionSource mAttributionSource;
    @Mock
    private Context mContext;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private ServiceProfileInfo mServiceProfileInfo;
    @Mock
    private IUwbRangingCallbacks mIUwbRangingCallbacks;
    @Mock
    private Handler mHandler;
    @Mock
    private UwbServiceCore mUwbServiceCore;
    @Mock
    private Looper mLooper;

    private RangingSessionController mRangingSessionController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mHandler.getLooper()).thenReturn(mLooper);
        when(mUwbInjector.getUwbServiceCore()).thenReturn(mUwbServiceCore);
        SessionHandle sessionHandle = new SessionHandle(10);
        mRangingSessionController = new PacsControleeSession(sessionHandle,
                mAttributionSource,
                mContext,
                mUwbInjector,
                mServiceProfileInfo,
                mIUwbRangingCallbacks,
                mHandler);
    }

    @Test
    public void testStartRangingSession() throws RemoteException {

        UwbConfig uwbConfig = mRangingSessionController.getUwbConfig();
        mRangingSessionController.mSessionInfo.setSessionId(1);

        mRangingSessionController.mSessionInfo
                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x0A, 0x01}));
        mRangingSessionController.mSessionInfo.mDestAddressList
                .add(UwbAddress.fromBytes(new byte[]{0x0B, 0x01}));

        mRangingSessionController.startRangingSession();

        assertEquals(uwbConfig.mUwbRole, CONTROLEE_AND_RESPONDER);

        verify(mUwbServiceCore).openRanging(eq(mAttributionSource),
                eq(mRangingSessionController.mSessionInfo.mSessionHandle),
                eq(mIUwbRangingCallbacks),
                any());
    }

}
