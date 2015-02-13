/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.android.inputmethod.latin.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import com.android.inputmethod.dictionarypack.LocaleUtils;
import com.android.inputmethod.latin.R;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class ImportDictionariesArrayAdapter extends ArrayAdapter<File> {

    private static class ViewHolder {
        TextView dictionaryTitle;
        TextView dictionaryLocale;
    }

    public ImportDictionariesArrayAdapter(Context context,
                                          List<File> objects) {
        super(context, 0, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        File dictionary = getItem(position);
        ViewHolder viewHolder;
        if (convertView == null) {
            viewHolder = new ViewHolder();
            convertView = LayoutInflater.from(
                    getContext()).inflate(R.layout.import_dict_view, parent, false);
            viewHolder.dictionaryTitle = (TextView) convertView
                    .findViewById(R.id.dictionary_language);
            viewHolder.dictionaryLocale = (TextView) convertView
                    .findViewById(R.id.dictionary_locale);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        String possibleLocale =
                dictionary.getName().substring(0, dictionary.getName().lastIndexOf('.'));
        Locale locale = LocaleUtils.constructLocaleFromString(possibleLocale);
        viewHolder.dictionaryTitle.setText(dictionary.getName());
        viewHolder.dictionaryLocale.setText(locale.getDisplayCountry());
        return convertView;
    }
}
