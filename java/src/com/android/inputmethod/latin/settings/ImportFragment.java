/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.inputmethod.latin.settings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.android.inputmethod.latin.R;

/**
 * Show all the possible external packages that have dictionaries that we can import
 * and install
 */
public class ImportFragment extends SubScreenFragment implements AdapterView.OnItemClickListener {
    private static final String EXTERNAL_LATIN_IME_DICT = "com.android.inputmethod.latin.extension";

    private List<ApplicationInfo> mExternalPackages;
    private ImportPackagesArrayAdapter mImportAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExternalPackages = findExternalDictionaries(getActivity());
        mImportAdapter = new ImportPackagesArrayAdapter(getActivity(),
                mExternalPackages);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View hostView = inflater.inflate(R.layout.import_view, container, false);
        ListView listView = (ListView) hostView.findViewById(android.R.id.list);
        ViewStub emptyView = (ViewStub) hostView.findViewById(android.R.id.empty);
        emptyView.setLayoutResource(R.layout.empty_packages);
        listView.setOnItemClickListener(this);
        listView.setAdapter(mImportAdapter);
        return hostView;
    }

    /**
     * List all possible external dictionary packages
     * @param context
     * @return all possible external dictionary packages
     */
    public static List<ApplicationInfo> findExternalDictionaries(final Context context) {
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packages = packageManager.getInstalledPackages(0);
        List<ApplicationInfo> elgiblePackages = new ArrayList<ApplicationInfo>();
        for (PackageInfo info : packages) {
            try {
                ApplicationInfo ai = packageManager.getApplicationInfo(info.packageName,
                        PackageManager.GET_META_DATA);
                Bundle metaData = ai.metaData;
                if (metaData != null) {
                    if (metaData.containsKey(EXTERNAL_LATIN_IME_DICT)) {
                        elgiblePackages.add(ai);
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                // This is odd?
            }
        }
        return elgiblePackages;
    }

    /**
     * Find all possible dictionaries within the given package
     * @param context
     */
    public ArrayList<File> showFilesFromPackage(final Context context, ApplicationInfo info) {
        ArrayList<File> files = new ArrayList<File>();
        try {
            Context externalContext = context.createPackageContext(info.packageName,
                    Context.CONTEXT_IGNORE_SECURITY);
            AssetManager manager = externalContext.getResources().getAssets();
            String[] list;
            list = manager.list("");
            for (String filePath : list) {
                // Check the extension
                File file = new File(filePath);
                if (file.getName().endsWith(".dict")) {
                    files.add(file);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            //
        } catch (IOException e) {
            //
        }
        return files;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        ApplicationInfo info = mImportAdapter.getItem(position);
        if (info != null) {
            ArrayList<File> files = showFilesFromPackage(getActivity(), info);
            Intent intent = new Intent(getActivity(), ImportFragmentDetailActivity.class);
            intent.putExtra(ImportFragmentDetailActivity.DICTIONARY_PACKAGE_NAME, info.packageName);
            intent.putExtra(ImportFragmentDetailActivity.DICTIONARY_LIST_FOR_PACKAGE, files);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }
}
