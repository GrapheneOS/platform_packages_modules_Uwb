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

package android.ranging;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MotionStateTest {
    @Test
    public void testGetters() {
        MotionState motion = new MotionState(MotionState.MOTION_SLIGHT);
        assertThat(motion.getMotionState()).isEqualTo(MotionState.MOTION_SLIGHT);
    }

    @Test
    public void testToString() {
        MotionState motion = new MotionState(MotionState.MOTION_SLIGHT);
        assertTrue(motion.toString().contains("SLIGHT"));
    }

    @Test
    public void testParcel() {
        MotionState originalmotion = new MotionState(MotionState.MOTION_MODERATE);
        Parcel parcel = Parcel.obtain();
        originalmotion.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MotionState newmotion = MotionState.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        assertThat(newmotion).isEqualTo(originalmotion);
    }

    @Test
    public void testEquals() {
        MotionState motion1 = new MotionState(MotionState.MOTION_LARGE);
        MotionState motion2 = new MotionState(MotionState.MOTION_LARGE);
        MotionState motion3 = new MotionState(MotionState.MOTION_NOT_DETECTED);
        assertTrue(motion1.equals(motion2));
        assertFalse(motion1.equals(motion3));
        assertFalse(motion1.equals(null));
        assertFalse(motion1.equals(new Object()));
    }

    @Test
    public void testHashCode() {
        MotionState motion1 = new MotionState(MotionState.MOTION_SLIGHT);
        MotionState motion2 = new MotionState(MotionState.MOTION_SLIGHT);
        assertThat(motion1.hashCode()).isEqualTo(motion2.hashCode());
    }
}
