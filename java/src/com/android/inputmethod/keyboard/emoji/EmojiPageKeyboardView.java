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

    private static final String TAG = EmojiPageKeyboardView.class.getSimpleName();

    private static final long KEY_PRESS_DELAY_TIME = 250;  // msec
    private static final long KEY_RELEASE_DELAY_TIME = 30;  // msec
    private final DrawingPreviewPlacerView mDrawingPreviewPlacerView;
    private MoreKeysPanel mMoreKeysPanel;
    private boolean mNeedsToDimEntireKeyboard;
    private final int[] mOriginCoords = CoordinateUtils.newInstance();
    Context mContext;
    final LayoutInflater inflater;
    private final View mMoreKeysKeyboardContainer;

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

    private void installPreviewPlacerView() {
        final View rootView = getRootView();
        if (rootView == null) {
            Log.w(TAG, "Cannot find root view");
            return;
        }
        final ViewGroup windowContentView = (ViewGroup)rootView.findViewById(android.R.id.content);
        // Note: It'd be very weird if we get null by android.R.id.content.
        if (windowContentView == null) {
            Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView");
            return;
        }
        windowContentView.addView(mDrawingPreviewPlacerView);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        installPreviewPlacerView();
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
        mMoreKeysKeyboardContainer = inflater.inflate(moreKeysKeyboardLayoutId, null);

        mMoreKeysKeyboardForActionContainer = inflater.inflate(
                moreKeysKeyboardForActionLayoutId, null);
    }

    public void setOnKeyEventListener(final OnKeyEventListener listener) {
        mListener = listener;
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        PointerTracker.setKeyboardActionListener(listener);
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
        onDismissMoreKeysPanel();
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
        openMoreKeysPanel(key, new PointerTracker(0));
    }

    private void openMoreKeysPanel(final Key key, final PointerTracker tracker) {
        final MoreKeysPanel moreKeysPanel = onCreateMoreKeysPanel(key, getContext());
        if (moreKeysPanel == null) {
            return;
        }

        final int[] lastCoords = CoordinateUtils.newInstance();
        tracker.getLastCoordinates(lastCoords);
        final boolean keyPreviewEnabled = mKeyPreviewDrawParams.isPopupEnabled()
                && !key.noKeyPreview();
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = key.getX() + key.getWidth() / 2;
        // The more keys keyboard is usually vertically aligned with the top edge of the parent key
        // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
        // aligned with the bottom edge of the visible part of the key preview.
        // {@code mPreviewVisibleOffset} has been set appropriately in
        // {@link KeyboardView#showKeyPreview(PointerTracker)}.
        final int pointY = key.getY() + mKeyPreviewDrawParams.getVisibleOffset();
        moreKeysPanel.showMoreKeysPanel(this, this, pointX, pointY, PointerTracker.getKeyboardActionListener());
        tracker.onShowMoreKeysPanel(moreKeysPanel);
        // TODO: Implement zoom in animation of more keys panel.
        dismissKeyPreviewWithoutDelay(key);
    }

    // Implements {@link TimerHandler.Callbacks} method.
    public void dismissKeyPreviewWithoutDelay(final Key key) {
        mKeyPreviewChoreographer.dismissKeyPreview(key, false /* withAnimation */);
        // To redraw key top letter.
        invalidateKey(key);
    }

    private MoreKeysPanel onCreateMoreKeysPanel(final Key key, final Context context) {
        final MoreKeySpec[] moreKeys = key.getMoreKeys();
        if (moreKeys == null) {
            return null;
        }
        Keyboard moreKeysKeyboard = mMoreKeysKeyboardCache.get(key);
        if (moreKeysKeyboard == null) {
            // {@link KeyPreviewDrawParams#mPreviewVisibleWidth} should have been set at
            // {@link KeyPreviewChoreographer#placeKeyPreview(Key,TextView,KeyboardIconsSet,KeyDrawParams,int,int[]},
            // though there may be some chances that the value is zero. <code>width == 0</code>
            // will cause zero-division error at
            // {@link MoreKeysKeyboardParams#setParameters(int,int,int,int,int,int,boolean,int)}.
            final boolean isSingleMoreKeyWithPreview = mKeyPreviewDrawParams.isPopupEnabled()
                    && !key.noKeyPreview() && moreKeys.length == 1
                    && mKeyPreviewDrawParams.getVisibleWidth() > 0;
            final MoreKeysKeyboard.Builder builder = new MoreKeysKeyboard.Builder(
                    context, key, getKeyboard(), isSingleMoreKeyWithPreview,
                    mKeyPreviewDrawParams.getVisibleWidth(),
                    mKeyPreviewDrawParams.getVisibleHeight(), newLabelPaint(key));
            moreKeysKeyboard = builder.build();
            mMoreKeysKeyboardCache.put(key, moreKeysKeyboard);
        }

        final View container = key.isActionKey() ? mMoreKeysKeyboardForActionContainer
                : mMoreKeysKeyboardContainer;
        final MoreKeysKeyboardView moreKeysKeyboardView =
                (MoreKeysKeyboardView)container.findViewById(R.id.more_keys_keyboard_view);
        moreKeysKeyboardView.setKeyboard(moreKeysKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return moreKeysKeyboardView;
    }

}
