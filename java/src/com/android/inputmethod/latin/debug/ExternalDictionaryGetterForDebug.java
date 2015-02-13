/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.inputmethod.latin.debug;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Environment;

import android.widget.Toast;
import com.android.inputmethod.latin.BinaryDictionaryFileDumper;
import com.android.inputmethod.latin.BinaryDictionaryGetter;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.makedict.DictionaryHeader;
import com.android.inputmethod.latin.settings.ImportFragment;
import com.android.inputmethod.latin.utils.DialogUtils;
import com.android.inputmethod.latin.utils.DictionaryInfoUtils;
import com.android.inputmethod.latin.utils.LocaleUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

/**
 * A class to read a local file as a dictionary for debugging purposes.
 */
public class ExternalDictionaryGetterForDebug {
    private static final String SOURCE_FOLDER = Environment.getExternalStorageDirectory().getPath()
            + "/Download";
    private static final String INTERNAL_DICT_PATH =
            "/data/data/com.android.inputmethod.latin/files";

    private static String[] findDictionariesInTheDownloadedFolder() {
        final File[] files = new File(SOURCE_FOLDER).listFiles();
        final ArrayList<String> eligibleList = new ArrayList<>();
        for (File f : files) {
            final DictionaryHeader header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(f);
            if (null == header) continue;
            eligibleList.add(f.getName());
        }
        return eligibleList.toArray(new String[0]);
    }

    public static void chooseAndInstallDictionary(final Context context) {
        final String[] fileNames = findDictionariesInTheDownloadedFolder();
        if (0 == fileNames.length) {
            showNoFileDialog(context);
        } else if (1 == fileNames.length) {
            askInstallFile(context, SOURCE_FOLDER, fileNames[0], null /* completeRunnable */);
        } else {
            showChooseFileDialog(context, fileNames);
        }
    }

    private static void showNoFileDialog(final Context context) {
        new AlertDialog.Builder(DialogUtils.getPlatformDialogThemeContext(context))
                .setMessage(R.string.read_external_dictionary_no_files_message)
                .setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                }).create().show();
    }

    private static void showChooseFileDialog(final Context context, final String[] fileNames) {
        new AlertDialog.Builder(DialogUtils.getPlatformDialogThemeContext(context))
                .setTitle(R.string.read_external_dictionary_multiple_files_title)
                .setItems(fileNames, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        askInstallFile(context, SOURCE_FOLDER, fileNames[which],
                                null /* completeRunnable */);
                    }
                })
                .create().show();
    }


    public static void askInstallFileButCopyFirst(final Context context, final String externalPackage,
            final String fileName, final Runnable completeRunnable) {
        Context otherContext;
        try {
            otherContext =
                    context.createPackageContext(externalPackage, Context.CONTEXT_IGNORE_SECURITY);

            // We need to copy the file to LatinIME's data/ directory, so the rest of the
            // stack can interpret the absolute path correctly.
            AssetFileDescriptor assetFileDescriptor = otherContext.getAssets().openFd(fileName);
            File fileCopied =
                    copyAsset(assetFileDescriptor, INTERNAL_DICT_PATH + "/" + fileName);
            askInstallFile(context, fileCopied.getAbsolutePath(), fileCopied.getName(), null);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(context, R.string.unable_to_create_package_context,
                    Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            // Should really never happen
        } catch (NullPointerException e) {
            Toast.makeText(context, R.string.unable_to_load_native_library,
                    Toast.LENGTH_SHORT).show();
        }

    }

    private static File copyAsset(AssetFileDescriptor aFd, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        File outFile = new File(toPath);
        try {
            in = aFd.createInputStream();
            outFile.createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return outFile;
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    /**
     * Shows a dialog which offers the user to install the external dictionary.
     */
    public static void askInstallFile(final Context context, final String dirPath,
            final String fileName, final Runnable completeRunnable) {
        final File file = new File(dirPath, fileName.toString());
        final DictionaryHeader header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file);
        final StringBuilder message = new StringBuilder();
        final String locale = header.getLocaleString();
        for (String key : header.mDictionaryOptions.mAttributes.keySet()) {
            message.append(key + " = " + header.mDictionaryOptions.mAttributes.get(key));
            message.append("\n");
        }
        final String languageName = LocaleUtils.constructLocaleFromString(locale)
                .getDisplayName(Locale.getDefault());
        final String title = String.format(
                context.getString(R.string.read_external_dictionary_confirm_install_message),
                languageName);
        new AlertDialog.Builder(DialogUtils.getPlatformDialogThemeContext(context))
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                        if (completeRunnable != null) {
                            completeRunnable.run();
                        }
                    }
                }).setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        installFile(context, file, header);
                        dialog.dismiss();
                        if (completeRunnable != null) {
                            completeRunnable.run();
                        }
                    }
                }).setOnCancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        // Canceled by the user by hitting the back key
                        if (completeRunnable != null) {
                            completeRunnable.run();
                        }
                    }
                }).create().show();
    }

    private static void installFile(final Context context, final File file,
            final DictionaryHeader header) {
        BufferedOutputStream outputStream = null;
        File tempFile = null;
        try {
            final String locale = header.getLocaleString();
            // Create the id for a main dictionary for this locale
            final String id = BinaryDictionaryGetter.MAIN_DICTIONARY_CATEGORY
                    + BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR + locale;
            final String finalFileName = DictionaryInfoUtils.getCacheFileName(id, locale, context);
            final String tempFileName = BinaryDictionaryGetter.getTempFileName(id, context);
            tempFile = new File(tempFileName);
            tempFile.delete();
            outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
            final BufferedInputStream bufferedStream = new BufferedInputStream(
                    new FileInputStream(file));
            BinaryDictionaryFileDumper.checkMagicAndCopyFileTo(bufferedStream, outputStream);
            outputStream.flush();
            final File finalFile = new File(finalFileName);
            finalFile.delete();
            if (!tempFile.renameTo(finalFile)) {
                throw new IOException("Can't move the file to its final name");
            }
        } catch (IOException e) {
            // There was an error: show a dialog
            new AlertDialog.Builder(DialogUtils.getPlatformDialogThemeContext(context))
                    .setTitle(R.string.read_external_dictionary_error)
                    .setMessage(e.toString())
                    .setPositiveButton(android.R.string.ok, new OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, final int which) {
                            dialog.dismiss();
                        }
                    }).create().show();
            return;
        } finally {
            try {
                if (null != outputStream) outputStream.close();
                if (null != tempFile) tempFile.delete();
            } catch (IOException e) {
                // Don't do anything
            }
        }
    }
}
