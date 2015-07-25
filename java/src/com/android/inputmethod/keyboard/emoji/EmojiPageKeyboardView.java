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

package com.android.inputmethod.keyboard.emoji;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;

import android.widget.Toast;
import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.accessibility.KeyboardAccessibilityDelegate;
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardActionListener;
import com.android.inputmethod.keyboard.KeyboardView;
import com.android.inputmethod.keyboard.MoreKeysKeyboard;
import com.android.inputmethod.keyboard.MoreKeysKeyboardView;
import com.android.inputmethod.keyboard.MoreKeysPanel;
import com.android.inputmethod.keyboard.PointerTracker;
import com.android.inputmethod.keyboard.internal.DrawingPreviewPlacerView;
import com.android.inputmethod.keyboard.internal.KeyPreviewChoreographer;
import com.android.inputmethod.keyboard.internal.KeyPreviewDrawParams;
import com.android.inputmethod.keyboard.internal.MoreKeySpec;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.utils.CoordinateUtils;

import java.util.WeakHashMap;

/**
 * This is an extended {@link KeyboardView} class that hosts an emoji page keyboard.
 * Multi-touch unsupported. No gesture support.
 */
// TODO: Implement key popup preview.
final class EmojiPageKeyboardView extends KeyboardView implements
        GestureDetector.OnGestureListener,
        MoreKeysPanel.Controller {
    private static final long KEY_PRESS_DELAY_TIME = 250;  // msec
    private static final long KEY_RELEASE_DELAY_TIME = 30;  // msec
    private final DrawingPreviewPlacerView mDrawingPreviewPlacerView;
    private MoreKeysPanel mMoreKeysPanel;
    private boolean mNeedsToDimEntireKeyboard;
    private final int[] mOriginCoords = CoordinateUtils.newInstance();
    Context mContext;
    final LayoutInflater inflater;

    @Override
    public void onShowMoreKeysPanel(MoreKeysPanel panel) {
        locatePreviewPlacerView();
        panel.showInParent(mDrawingPreviewPlacerView);
        mMoreKeysPanel = panel;
        dimEntireKeyboard(true /* dimmed */);
    }

    @Override
    public void onDismissMoreKeysPanel() {
        dimEntireKeyboard(false /* dimmed */);
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.removeFromParent();
            mMoreKeysPanel = null;
        }
    }

    @Override
    public void onCancelMoreKeysPanel() {
        PointerTracker.dismissAllMoreKeysPanels();
    }

    private void dimEntireKeyboard(final boolean dimmed) {
        final boolean needsRedrawing = mNeedsToDimEntireKeyboard != dimmed;
        mNeedsToDimEntireKeyboard = dimmed;
        if (needsRedrawing) {
            invalidateAllKeys();
        }
    }

    public boolean isShowingMoreKeysPanel() {
        return mMoreKeysPanel != null && mMoreKeysPanel.isShowingInParent();
    }

    private void locatePreviewPlacerView() {
        getLocationInWindow(mOriginCoords);
        mDrawingPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords, getWidth(), getHeight());
    }

    public interface OnKeyEventListener {
        public void onPressKey(Key key);
        public void onReleaseKey(Key key);
    }

    private static final OnKeyEventListener EMPTY_LISTENER = new OnKeyEventListener() {
        @Override
        public void onPressKey(final Key key) {}
        @Override
        public void onReleaseKey(final Key key) {}
    };

    private OnKeyEventListener mListener = EMPTY_LISTENER;
    private final KeyDetector mKeyDetector = new KeyDetector();
    private final GestureDetector mGestureDetector;
    private KeyboardAccessibilityDelegate<EmojiPageKeyboardView> mAccessibilityDelegate;
    private KeyboardActionListener mKeyboardActionListener;
    private final KeyPreviewDrawParams mKeyPreviewDrawParams;
    private final KeyPreviewChoreographer mKeyPreviewChoreographer;
    private final WeakHashMap<Key, Keyboard> mMoreKeysKeyboardCache = new WeakHashMap<>();
    private View mMoreKeysKeyboardForActionContainer;


    public EmojiPageKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public EmojiPageKeyboardView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setIsLongpressEnabled(true /* isLongpressEnabled */);
        mHandler = new Handler();
        mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;
        final TypedArray mainKeyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        mDrawingPreviewPlacerView = new DrawingPreviewPlacerView(context, attrs);

        mKeyPreviewDrawParams = new KeyPreviewDrawParams(mainKeyboardViewAttr);
        mKeyPreviewChoreographer = new KeyPreviewChoreographer(mKeyPreviewDrawParams);

        final int moreKeysKeyboardLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardLayout, 0);
        final int moreKeysKeyboardForActionLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardForActionLayout,
                moreKeysKeyboardLayoutId);

        inflater = LayoutInflater.from(getContext());

        mMoreKeysKeyboardForActionContainer = inflater.inflate(
                moreKeysKeyboardForActionLayoutId, null);
    }

    public void setOnKeyEventListener(final OnKeyEventListener listener) {
        mListener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKeyboard(final Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(keyboard, 0 /* correctionX */, 0 /* correctionY */);
        if (AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            if (mAccessibilityDelegate == null) {
                mAccessibilityDelegate = new KeyboardAccessibilityDelegate<>(this, mKeyDetector);
            }
            mAccessibilityDelegate.setKeyboard(keyboard);
        } else {
            mAccessibilityDelegate = null;
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with all Emoji keys.
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onHoverEvent(final MotionEvent event) {
        final KeyboardAccessibilityDelegate<EmojiPageKeyboardView> accessibilityDelegate =
                mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            return accessibilityDelegate.onHoverEvent(event);
        }
        return super.onHoverEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(final MotionEvent e) {
        if (mGestureDetector.onTouchEvent(e)) {
            return true;
        }
        final Key key = getKey(e);
        if (key != null && key != mCurrentKey) {
            releaseCurrentKey(false /* withKeyRegistering */);
        }
        return true;
    }

    // {@link GestureEnabler#OnGestureListener} methods.
    private Key mCurrentKey;
    private Runnable mPendingKeyDown;
    private final Handler mHandler;

    private Key getKey(final MotionEvent e) {
        final int index = e.getActionIndex();
        final int x = (int)e.getX(index);
        final int y = (int)e.getY(index);
        return mKeyDetector.detectHitKey(x, y);
    }

    public void releaseCurrentKey(final boolean withKeyRegistering) {
        mHandler.removeCallbacks(mPendingKeyDown);
        mPendingKeyDown = null;
        final Key currentKey = mCurrentKey;
        if (currentKey == null) {
            return;
        }
        currentKey.onReleased();
        invalidateKey(currentKey);
        if (withKeyRegistering) {
            mListener.onReleaseKey(currentKey);
        }
        mCurrentKey = null;
    }

    @Override
    public boolean onDown(final MotionEvent e) {
        final Key key = getKey(e);
        releaseCurrentKey(false /* withKeyRegistering */);
        mCurrentKey = key;
        if (key == null) {
            return false;
        }
        // Do not trigger key-down effect right now in case this is actually a fling action.
        mPendingKeyDown = new Runnable() {
            @Override
            public void run() {
                mPendingKeyDown = null;
                key.onPressed();
                invalidateKey(key);
                mListener.onPressKey(key);
            }
        };
        mHandler.postDelayed(mPendingKeyDown, KEY_PRESS_DELAY_TIME);
        return false;
    }

    @Override
    public void onShowPress(final MotionEvent e) {
        // User feedback is done at {@link #onDown(MotionEvent)}.
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent e) {
        final Key key = getKey(e);
        final Runnable pendingKeyDown = mPendingKeyDown;
        final Key currentKey = mCurrentKey;
        releaseCurrentKey(false /* withKeyRegistering */);
        if (key == null) {
            return false;
        }
        if (key == currentKey && pendingKeyDown != null) {
            pendingKeyDown.run();
            // Trigger key-release event a little later so that a user can see visual feedback.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    key.onReleased();
                    invalidateKey(key);
                    mListener.onReleaseKey(key);
                }
            }, KEY_RELEASE_DELAY_TIME);
        } else {
            key.onReleased();
            invalidateKey(key);
            mListener.onReleaseKey(key);
        }
        return true;
    }

    @Override
    public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX,
           final float distanceY) {
        releaseCurrentKey(false /* withKeyRegistering */);
        return false;
    }

    @Override
    public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX,
            final float velocityY) {
        releaseCurrentKey(false /* withKeyRegistering */);
        return false;
    }

    @Override
    public void onLongPress(final MotionEvent e) {
        final Key key = getKey(e);
        buildEmojiDialog(key.getMoreKeys());
    }

    public void buildEmojiDialog(MoreKeySpec[] more) {
        String[] items = new String[more.length];

        for (int i = 0; i < more.length; i++) {
            items[i] = more[i].mOutputText;
            Toast.makeText(getContext(), items[i], Toast.LENGTH_SHORT).show();
        }



    }

}
