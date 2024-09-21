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

package android.ranging;

import android.annotation.FlaggedApi;
import android.annotation.Hide;
import android.annotation.SystemApi;
import android.app.SystemServiceRegistry;
import android.content.Context;


/**
 * Class for performing registration for Ranging service.
 *
 * @hide
 */
@FlaggedApi("com.android.ranging.flags.ranging_stack_enabled")
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public class RangingFrameworkInitializer {
    private RangingFrameworkInitializer() {}

    /**
     * @hide
     */
    @Hide
    @FlaggedApi("com.android.ranging.flags.ranging_stack_enabled")
    public static void registerServiceWrappers() {
        SystemServiceRegistry.registerContextAwareService(
                Context.RANGING_SERVICE,
                RangingManager.class,
                (context, serviceBinder) -> {
                    IRangingAdapter adapter = IRangingAdapter.Stub.asInterface(serviceBinder);
                    return new RangingManager(context, adapter);
                }
        );
    }
}
