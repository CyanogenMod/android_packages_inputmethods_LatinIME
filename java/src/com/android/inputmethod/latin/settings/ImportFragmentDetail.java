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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.debug.ExternalDictionaryGetterForDebug;

import java.util.List;

public class ImportFragmentDetail extends SubScreenFragment
        implements AdapterView.OnItemClickListener {
    private ImportDictionariesArrayAdapter mDictionariesArrayAdapter;
    private List<ImportFragment.ImportDictionary> mDictionaries;

    public ImportFragmentDetail(List<ImportFragment.ImportDictionary> dictionaries) {
        mDictionaries = dictionaries;
    }

    public static ImportFragmentDetail newInstance(List<ImportFragment.ImportDictionary>
                                                           dictionaries) {
        ImportFragmentDetail importFragmentDetail = new ImportFragmentDetail(dictionaries);
        return importFragmentDetail;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDictionariesArrayAdapter =
                new ImportDictionariesArrayAdapter(getActivity(), mDictionaries);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View hostView = inflater.inflate(R.layout.import_view, container, false);
        ListView listView = (ListView) hostView.findViewById(android.R.id.list);
        listView.setOnItemClickListener(this);
        listView.setAdapter(mDictionariesArrayAdapter);
        return hostView;
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        ImportFragment.ImportDictionary importDictionary = mDictionariesArrayAdapter.getItem(i);
        if (importDictionary != null) {
            ExternalDictionaryGetterForDebug.askInstallFile(getActivity(), importDictionary);
        }
    }
}
