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

package com.android.server.ranging.common;

/**
 * A basic synchronized state machine.
 *
 * @param <E> enum representing the different states of the machine.
 */
public class StateMachine<E extends Enum<E>> {
    private final Object mLock;
    private E mState;

    public StateMachine(E start) {
        mLock = this;
        mState = start;
    }

    public StateMachine(E start, Object lock) {
        mLock = lock;
        mState = start;
    }

    /** Gets the current state */
    public synchronized E getState() {
        synchronized (mLock) {
            return mState;
        }
    }

    /** Sets the current state */
    public  void setState(E state) {
        synchronized (mLock) {
            mState = state;
        }
    }

    /**
     * Sets the current state.
     *
     * @return true if the state was successfully changed, false if the current state is
     * already {@code state}.
     */
    public boolean changeStateTo(E state) {
        synchronized (mLock) {
            if (mState == state) {
                return false;
            }
            setState(state);
            return true;
        }
    }

    /**
     * If the current state is {@code from}, sets it to {@code to}.
     *
     * @return true if the current state is {@code from}, false otherwise.
     */
    public boolean transition(E from, E to) {
        synchronized (mLock) {
            if (mState != from) {
                return false;
            }
            mState = to;
            return true;
        }
    }

    /**
     * Atomically get the current state before setting it to a new one.
     *
     * @return the previous state before it was set to the provided one.
     */
    public E getAndSet(E state) {
        synchronized (mLock) {
            E previousState = mState;
            mState = state;
            return previousState;
        }
    }

    @Override
    public String toString() {
        return "StateMachine{ "
                + mState
                + "}";
    }
}
