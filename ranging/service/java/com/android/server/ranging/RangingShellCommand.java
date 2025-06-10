/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.ranging;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;

import java.io.PrintWriter;

/**
 * Interprets and executes 'adb shell cmd ranging [args]'.
 *
 * To add new commands:
 * - onCommand: Add a case "<command>" execute. Return a 0
 * if command executed successfully.
 * - onHelp: add a description string.
 *
 * Permissions: currently root permission is required for some commands. Others will
 * enforce the corresponding API permissions.
 */
public class RangingShellCommand extends BasicShellCommandHandler {
    @VisibleForTesting
    public static String SHELL_PACKAGE_NAME = "com.android.shell";

    private final RangingInjector mRangingInjector;
    private final RangingServiceImpl mRangingService;
    private final Context mContext;

    public RangingShellCommand(RangingInjector injector, RangingServiceImpl rangingService,
            Context context) {
        mRangingInjector = injector;
        mRangingService = rangingService;
        mContext = context;
    }

    @Override
    public int onCommand(String cmd) {
        // Treat no command as help command.
        if (cmd == null || cmd.equals("")) {
            cmd = "help";
        }
        final PrintWriter pw = getOutPrintWriter();
        try {
            switch (cmd) {
                case "simulate-app-state-change": {
                    String appPackageName = getNextArgRequired();
                    String nextArg = getNextArg();
                    if (nextArg != null) {
                        boolean isFg = argTrueOrFalse(nextArg, "foreground", "background");
                        int importance = isFg ? IMPORTANCE_FOREGROUND : IMPORTANCE_BACKGROUND;
                        int uid = 0;
                        try {
                            uid = mContext.getPackageManager().getApplicationInfo(
                                    appPackageName, 0).uid;
                        } catch (PackageManager.NameNotFoundException e) {
                            pw.println("Unable to find package name: " + appPackageName);
                            return -1;
                        }
                        mRangingInjector.setOverridePackageImportance(appPackageName, importance);
                        mRangingInjector.getRangingServiceManager().onUidImportance(uid,
                                importance);
                    } else {
                        mRangingInjector.resetOverridePackageImportance(appPackageName);
                    }
                    return 0;
                }
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (IllegalArgumentException e) {
            pw.println("Invalid args for " + cmd + ": ");
            e.printStackTrace(pw);
            return -1;
        } catch (Exception e) {
            pw.println("Exception while executing RangingShellCommand" + cmd + ": ");
            e.printStackTrace(pw);
            return -1;
        }
    }

    private static boolean argTrueOrFalse(String arg, String trueString, String falseString) {
        if (trueString.equals(arg)) {
            return true;
        } else if (falseString.equals(arg)) {
            return false;
        } else {
            throw new IllegalArgumentException("Expected '" + trueString + "' or '" + falseString
                    + "' as next arg but got '" + arg + "'");
        }

    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("Ranging commands:");
        pw.println("  help or -h");
        pw.println("    Print this help text.");
        pw.println("  simulate-app-state-change <package-name> foreground|background");
        pw.println("    Simulate app moving to foreground/background to test stack handling");
        pw.println();
    }
}
