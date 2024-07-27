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

package com.android.ranging;

/**
 * Utilities for {@link com.android.ranging}.
 */
public class RangingUtils {
    /**
     * A basic synchronized state machine.
     * @param <E> enum representing the different states of the machine.
     */
    public static class StateMachine<E extends Enum<E>> {
        private E mState;

        public StateMachine(E start) {
            mState = start;
        }

        /** Gets the current state */
        public synchronized E getState() {
            return mState;
        }

        /** Sets the current state */
        public synchronized void setState(E state) {
            mState = state;
        }

        /**
         * Sets the current state.
         * @return true if the state was successfully changed, false if the current state is
         * already {@code state}.
         */
        public synchronized boolean changeStateTo(E state) {
            if (mState == state) {
                return false;
            }
            setState(state);
            return true;
        }

        /**
         * If the current state is {@code from}, sets it to {@code to}.
         * @return true if the current state is {@code from}, false otherwise.
         */
        public synchronized boolean transition(E from, E to) {
            if (mState != from) {
                return false;
            }
            mState = to;
            return true;
        }
    }

}
