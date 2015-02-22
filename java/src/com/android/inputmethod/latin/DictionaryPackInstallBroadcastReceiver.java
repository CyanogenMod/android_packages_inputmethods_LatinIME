/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.inputmethod.latin;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import com.android.inputmethod.dictionarypack.DictionaryPackConstants;
import com.android.inputmethod.latin.utils.TargetPackageInfoGetterTask;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.util.Log;

/**
 * Receives broadcasts pertaining to dictionary management and takes the appropriate action.
 *
 * This object receives three types of broadcasts.
 * - Package installed/added. When a dictionary provider application is added or removed, we
 * need to query the dictionaries.
 * - New dictionary broadcast. The dictionary provider broadcasts new dictionary availability. When
 * this happens, we need to re-query the dictionaries.
 * - Unknown client. If the dictionary provider is in urgent need of data about some client that
 * it does not know, it sends this broadcast. When we receive this, we need to tell the dictionary
 * provider about ourselves. This happens when the settings for the dictionary pack are accessed,
 * but Latin IME never got a chance to register itself.
 */
public final class DictionaryPackInstallBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = DictionaryPackInstallBroadcastReceiver.class.getSimpleName();

    final LatinIME mService;

    public DictionaryPackInstallBroadcastReceiver() {
        // This empty constructor is necessary for the system to instantiate this receiver.
        // This happens when the dictionary pack says it can't find a record for our client,
        // which happens when the dictionary pack settings are called before the keyboard
        // was ever started once.
        Log.i(TAG, "Latin IME dictionary broadcast receiver instantiated from the framework.");
        mService = null;
    }

    public DictionaryPackInstallBroadcastReceiver(final LatinIME service) {
        mService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final PackageManager manager = context.getPackageManager();

        // We need to reread the dictionary if a new dictionary package is installed.
        if (action.equals(Intent.ACTION_PACKAGE_ADDED)) {
            if (null == mService) {
                Log.e(TAG, "Called with intent " + action + " but we don't know the service: this "
                        + "should never happen");
                return;
            }
            final Uri packageUri = intent.getData();
            if (null == packageUri) return; // No package name : we can't do anything
            final String packageName = packageUri.getSchemeSpecificPart();
            if (null == packageName) return;
            // TODO: do this in a more appropriate place
            TargetPackageInfoGetterTask.removeCachedPackageInfo(packageName);
            final PackageInfo packageInfo;
            try {
                packageInfo = manager.getPackageInfo(packageName, PackageManager.GET_PROVIDERS);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return; // No package info : we can't do anything
            }
            final ProviderInfo[] providers = packageInfo.providers;
            if (null == providers) {
                // If the dictionary package doesn't have a provider, we can try to load its binary
                // dictionaries from its assets if it has the correct metadata
                try {
                    ApplicationInfo ai = manager.getApplicationInfo(packageName,
                            PackageManager.GET_META_DATA);
                    Bundle metaData = ai.metaData;
                    if (metaData != null) {
                        if (metaData.containsKey(DictionaryPackConstants.EXTERNAL_LATIN_IME_DICT)) {
                            mService.handlePossibleDictionaryUpdateOrPromptInstall(ai);
                        }
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    return ;
                }
                return;
            }

            // Search for some dictionary pack in the just-installed package. If found, reread.
            for (ProviderInfo info : providers) {
                if (DictionaryPackConstants.AUTHORITY.equals(info.authority)) {
                    mService.resetSuggestMainDict();
                    return;
                }
            }

            // If we come here none of the authorities matched the one we searched for.
            // We can exit safely.
            return;
        } else if (action.equals(Intent.ACTION_PACKAGE_REMOVED)
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            if (null == mService) {
                Log.e(TAG, "Called with intent " + action + " but we don't know the service: this "
                        + "should never happen");
                return;
            }
            // When the dictionary package is removed, we need to reread dictionary (to use the
            // next-priority one, or stop using a dictionary at all if this was the only one,
            // since this is the user request).
            // If we are replacing the package, we will receive ADDED right away so no need to
            // remove the dictionary at the moment, since we will do it when we receive the
            // ADDED broadcast.

            // TODO: Only reload dictionary on REMOVED when the removed package is the one we
            // read dictionary from?
            mService.resetSuggestMainDict();
        } else if (action.equals(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)) {
            if (null == mService) {
                Log.e(TAG, "Called with intent " + action + " but we don't know the service: this "
                        + "should never happen");
                return;
            }
            mService.resetSuggestMainDict();
        } else if (action.equals(DictionaryPackConstants.UNKNOWN_DICTIONARY_PROVIDER_CLIENT)) {
            if (null != mService) {
                // Careful! This is returning if the service is NOT null. This is because we
                // should come here instantiated by the framework in reaction to a broadcast of
                // the above action, so we should gave gone through the no-args constructor.
                Log.e(TAG, "Called with intent " + action + " but we have a reference to the "
                        + "service: this should never happen");
                return;
            }
            // The dictionary provider does not know about some client. We check that it's really
            // us that it needs to know about, and if it's the case, we register with the provider.
            final String wantedClientId =
                    intent.getStringExtra(DictionaryPackConstants.DICTIONARY_PROVIDER_CLIENT_EXTRA);
            final String myClientId = context.getString(R.string.dictionary_pack_client_id);
            if (!wantedClientId.equals(myClientId)) return; // Not for us
            BinaryDictionaryFileDumper.initializeClientRecordHelper(context, myClientId);
        }
    }
}
