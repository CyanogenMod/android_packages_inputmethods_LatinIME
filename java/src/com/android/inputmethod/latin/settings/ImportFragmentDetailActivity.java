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
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.ListView;

import android.widget.TextView;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.debug.ExternalDictionaryGetterForDebug;

import java.util.ArrayList;
import java.util.List;

public class ImportFragmentDetailActivity extends Activity
        implements AdapterView.OnItemClickListener {
    public static final String DICTIONARY_LIST_FOR_PACKAGE = "import_dictionary_list";
    private ImportDictionariesArrayAdapter mDictionariesArrayAdapter;
    private ArrayList<ImportFragment.ImportDictionary> mDictionaries;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(DICTIONARY_LIST_FOR_PACKAGE)) {
                mDictionaries = (ArrayList<ImportFragment.ImportDictionary>)
                        extras.get(DICTIONARY_LIST_FOR_PACKAGE);
            }
        }

        if (mDictionaries == null) {
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
        ImportFragment.ImportDictionary importDictionary = mDictionariesArrayAdapter.getItem(i);
        if (importDictionary != null) {
            ExternalDictionaryGetterForDebug.askInstallFile(this, importDictionary);
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
