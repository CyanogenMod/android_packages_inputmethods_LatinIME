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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.debug.ExternalDictionaryGetterForDebug;

import java.io.File;
import java.util.ArrayList;

public class ImportFragmentDetailActivity extends Activity
        implements AdapterView.OnItemClickListener {
    public static final String DICTIONARY_LIST_FOR_PACKAGE = "import_dictionary_list";
    public static final String DICTIONARY_PACKAGE_NAME = "import_dictionary_package_name";

    private ImportDictionariesArrayAdapter mDictionariesArrayAdapter;
    private ArrayList<File> mDictionaries;
    private String mPackageName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(DICTIONARY_LIST_FOR_PACKAGE)) {
                mDictionaries = (ArrayList<File>)
                        extras.get(DICTIONARY_LIST_FOR_PACKAGE);
            }
            if (extras.containsKey(DICTIONARY_PACKAGE_NAME)) {
                mPackageName = extras.getString(DICTIONARY_PACKAGE_NAME);
            }
        }

        if (mDictionaries == null || TextUtils.isEmpty(mPackageName)) {
            finish();
        }

        mDictionariesArrayAdapter =
                new ImportDictionariesArrayAdapter(this, mDictionaries);
        setContentView(R.layout.import_view);
        ListView listView = (ListView) findViewById(android.R.id.list);
        ViewStub emptyView = (ViewStub) findViewById(android.R.id.empty);
        emptyView.setLayoutResource(R.layout.empty_dicts);
        listView.setOnItemClickListener(this);
        listView.setAdapter(mDictionariesArrayAdapter);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        File importDictionary = mDictionariesArrayAdapter.getItem(i);
        if (importDictionary != null) {
            ExternalDictionaryGetterForDebug.askInstallFileButCopyFirst(this,
                    mPackageName, importDictionary.getName(), null);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
