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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.widget.Toast;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.utils.DictionaryInfoUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;

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
    public List<ImportDictionary> showFilesFromPackage(final Context context, ApplicationInfo info) {
        ArrayList<ImportDictionary> files = new ArrayList<ImportDictionary>();
        try {
            Context externalContext = context.createPackageContext(info.packageName,
                            Context.CONTEXT_IGNORE_SECURITY);
            AssetManager manager = externalContext.getResources().getAssets();
            String[] list;
            try {
                list = manager.list("");
                for (String filePath : list){
                    // Check the extension
                    if (filePath.endsWith(".dict")) {
                        File file = new File(filePath);
                        files.add(new ImportDictionary(file));
                    }
                }
            } catch (IOException e) {
                //ignore
            }
        } catch (PackageManager.NameNotFoundException e) {
            //Ignore
        }
        return files;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
        ApplicationInfo info = mImportAdapter.getItem(position);
        if (info != null) {
            List<ImportDictionary> files = showFilesFromPackage(getActivity(), info);
            if (!files.isEmpty()) {
                Fragment importDictionariesDetailFragment =
                        ImportFragmentDetail.newInstance(showFilesFromPackage(getActivity(), info));
                FragmentManager fragmentManager = getFragmentManager();
                FragmentTransaction transaction = fragmentManager.beginTransaction();
                transaction.replace(R.id.main_content, importDictionariesDetailFragment).commit();
            } else {
                Toast.makeText(getActivity(), getString(R.string.dictionary_unavailable),
                        Toast.LENGTH_LONG).show();
            }
        }
    }


    public class ImportDictionary {
        private DictionaryHeader header;
        private String languageName;
        private File file;

        public ImportDictionary(File file) {
            this.file = file;
            this.header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file);
            this.languageName = LocaleUtils.constructLocaleFromString(header.getLocaleString())
                    .getDisplayName(Locale.getDefault());
        }

        public DictionaryHeader getHeader() {
            return header;
        }

        public String getLanguageName() {
            return languageName;
        }

        public File getFile() {
            return file;
        }
    }
}
