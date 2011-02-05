package com.android.inputmethod.latin;

// Kanged from ADW

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

public class DialogSeekBarPreference extends DialogPreference implements
        SeekBar.OnSeekBarChangeListener {
    private static final String androidns = "http://schemas.android.com/apk/res/android";

    private SeekBar mSeekBar;

    private TextView mValueText;

    private String mSuffix;

    private int mMax, mMin, mStep, mValue = 0;

    public DialogSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(true);

        mSuffix = attrs.getAttributeValue(androidns, "text");
        mMin = attrs.getAttributeIntValue(null, "min", 0);
        mMax = attrs.getAttributeIntValue(null, "max", 100);
        mStep = attrs.getAttributeIntValue(null, "step", 1);

        setDialogLayoutResource(R.layout.my_seekbar_preference);
    }

    @Override
    protected void onBindDialogView(View v) {
        super.onBindDialogView(v);
        TextView dialogMessage = (TextView)v.findViewById(R.id.dialogMessage);
        dialogMessage.setText(getDialogMessage());

        mValueText = (TextView)v.findViewById(R.id.actualValue);

        mSeekBar = (SeekBar)v.findViewById(R.id.myBar);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBar.setMax((mMax - mMin) / mStep);
        mSeekBar.setProgress((mValue - mMin) / mStep);

        String t = String.valueOf(mValue);
        mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restore, Object defaultValue) {
        mValue = getPersistedInt(defaultValue == null ? 0 : (Integer)defaultValue);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            int value = mSeekBar.getProgress() * mStep + mMin;
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }

    public void setValue(int value) {
        if (value > mMax) {
            value = mMax;
        } else if (value < mMin) {
            value = mMin;
        }
        mValue = value;
        persistInt(value);
    }

    public void setMax(int max) {
        mMax = max;
        if (mValue > mMax) {
            setValue(mMax);
        }
    }

    public void setMin(int min) {
        if (min < mMax) {
            mMin = min;
        }
    }

    public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
        String t = String.valueOf(value * mStep + mMin);
        mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
    }

    public void onStartTrackingTouch(SeekBar seek) {
    }

    public void onStopTrackingTouch(SeekBar seek) {
    }

}
