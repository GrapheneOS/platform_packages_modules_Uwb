/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb;


import android.content.Context;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;
import android.uwb.UwbManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.server.uwb.util.ArrayUtils;

import java.io.PrintWriter;

/**
 * Interprets and executes 'adb shell cmd uwb [args]'.
 *
 * To add new commands:
 * - onCommand: Add a case "<command>" execute. Return a 0
 *   if command executed successfully.
 * - onHelp: add a description string.
 *
 * Permissions: currently root permission is required for some commands. Others will
 * enforce the corresponding API permissions.
 */
public class UwbShellCommand extends BasicShellCommandHandler {
    @VisibleForTesting
    public static String SHELL_PACKAGE_NAME = "com.android.shell";

    // These don't require root access.
    // However, these do perform permission checks in the corresponding UwbService methods.
    private static final String[] NON_PRIVILEGED_COMMANDS = {
            "status",
            "get-country-code"
    };

    private final UwbServiceImpl mUwbService;
    private final UwbCountryCode mUwbCountryCode;
    private final Context mContext;

    UwbShellCommand(UwbInjector uwbInjector, UwbServiceImpl uwbService, Context context) {
        mUwbService = uwbService;
        mContext = context;
        mUwbCountryCode = uwbInjector.getUwbCountryCode();
    }

    @Override
    public int onCommand(String cmd) {
        // Treat no command as help command.
        if (cmd == null || cmd.equals("")) {
            cmd = "help";
        }
        // Explicit exclusion from root permission
        if (ArrayUtils.indexOf(NON_PRIVILEGED_COMMANDS, cmd) == -1) {
            final int uid = Binder.getCallingUid();
            if (uid != Process.ROOT_UID) {
                throw new SecurityException(
                        "Uid " + uid + " does not have access to " + cmd + " uwb command "
                                + "(or such command doesn't exist)");
            }
        }

        final PrintWriter pw = getOutPrintWriter();
        try {
            switch (cmd) {
                case "force-country-code": {
                    boolean enabled = getNextArgRequiredTrueOrFalse("enabled", "disabled");
                    if (enabled) {
                        String countryCode = getNextArgRequired();
                        if (!UwbCountryCode.isValid(countryCode)) {
                            pw.println("Invalid argument: Country code must be a 2-Character"
                                    + " alphanumeric code. But got countryCode " + countryCode
                                    + " instead");
                            return -1;
                        }
                        mUwbCountryCode.setOverrideCountryCode(countryCode);
                        return 0;
                    } else {
                        mUwbCountryCode.clearOverrideCountryCode();
                        return 0;
                    }
                }
                case "get-country-code": {
                    pw.println("Uwb Country Code = "
                            + mUwbCountryCode.getCountryCode());
                    return 0;
                }
                case "status":
                    printStatus(pw);
                    return 0;
                default:
                    return handleDefaultCommands(cmd);
            }
        } catch (IllegalArgumentException e) {
            pw.println("Invalid args for " + cmd + ": " + e);
            return -1;
        } catch (Exception e) {
            pw.println("Exception while executing UwbShellCommand: ");
            e.printStackTrace(pw);
            return -1;
        }
    }

    private boolean getNextArgRequiredTrueOrFalse(String trueString, String falseString)
            throws IllegalArgumentException {
        String nextArg = getNextArgRequired();
        if (trueString.equals(nextArg)) {
            return true;
        } else if (falseString.equals(nextArg)) {
            return false;
        } else {
            throw new IllegalArgumentException("Expected '" + trueString + "' or '" + falseString
                    + "' as next arg but got '" + nextArg + "'");
        }
    }

    private void printStatus(PrintWriter pw) throws RemoteException {
        boolean uwbEnabled =
                mUwbService.getAdapterState() != UwbManager.AdapterStateCallback.STATE_DISABLED;
        pw.println("Uwb is " + (uwbEnabled ? "enabled" : "disabled"));
    }

    private void onHelpNonPrivileged(PrintWriter pw) {
        pw.println("  status");
        pw.println("    Gets status of UWB stack");
        pw.println("  get-country-code");
        pw.println("    Gets country code as a two-letter string");
    }

    private void onHelpPrivileged(PrintWriter pw) {
        pw.println("  force-country-code enabled <two-letter code> | disabled ");
        pw.println("    Sets country code to <two-letter code> or left for normal value");
    }

    @Override
    public void onHelp() {
        final PrintWriter pw = getOutPrintWriter();
        pw.println("UWB (ultra wide-band) commands:");
        pw.println("  help or -h");
        pw.println("    Print this help text.");
        onHelpNonPrivileged(pw);
        if (Binder.getCallingUid() == Process.ROOT_UID) {
            onHelpPrivileged(pw);
        }
        pw.println();
    }
}
