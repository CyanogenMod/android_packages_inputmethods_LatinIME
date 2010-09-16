/*
 * Copyright (C) 2008-2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.text.*;  // For Workaround in Bidi
import android.text.Layout.Alignment;


public class CandidateView extends View {

    private static final int OUT_OF_BOUNDS = -1;
    private static final List<CharSequence> EMPTY_LIST = new ArrayList<CharSequence>();

    private LatinIME mService;
    private List<CharSequence> mSuggestions = EMPTY_LIST;
    private boolean mShowingCompletions;
    private CharSequence mSelectedString;
    private int mSelectedIndex;
    private int mTouchX = OUT_OF_BOUNDS;
    private Drawable mSelectionHighlight;
    private boolean mTypedWordValid;

    private boolean mHaveMinimalSuggestion;

    private Rect mBgPadding;

    private TextView mPreviewText;
    private PopupWindow mPreviewPopup;
    private int mCurrentWordIndex;
    private Drawable mDivider;

    private static final int MAX_SUGGESTIONS = 32;
    private static final int SCROLL_PIXELS = 20;

    private static final int MSG_REMOVE_PREVIEW = 1;
    private static final int MSG_REMOVE_THROUGH_PREVIEW = 2;

    private int[] mWordWidth = new int[MAX_SUGGESTIONS];
    private int[] mWordX = new int[MAX_SUGGESTIONS];
    private int mPopupPreviewX;
    private int mPopupPreviewY;

    private static final int X_GAP = 10;

    private int mColorNormal;
    private int mColorRecommended;
    private int mColorOther;
    private Paint mPaint;
    private int mDescent;
    private boolean mScrolled;
    private boolean mShowingAddToDictionary;
    private CharSequence mWordToAddToDictionary;
    private CharSequence mAddToDictionaryHint;

    private int mTargetScrollX;

    private int mMinTouchableWidth;

    private int mTotalWidth;

    private GestureDetector mGestureDetector;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REMOVE_PREVIEW:
                    mPreviewText.setVisibility(GONE);
                    break;
                case MSG_REMOVE_THROUGH_PREVIEW:
                    mPreviewText.setVisibility(GONE);
                    if (mTouchX != OUT_OF_BOUNDS) {
                        removeHighlight();
                    }
                    break;
            }

        }
    };

	private int mScrollX; //Global for calculating new Xs in scrolling

    /**
     * Construct a CandidateView for showing suggested words for completion.
     * @param context
     * @param attrs
     */

    public CandidateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSelectionHighlight = context.getResources().getDrawable(
                R.drawable.highlight_pressed); //For Bidi workaround

        LayoutInflater inflate =
            (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Resources res = context.getResources();
        mPreviewPopup = new PopupWindow(context);
        mPreviewText = (TextView) inflate.inflate(R.layout.candidate_preview, null);
        mPreviewPopup.setWindowLayoutMode(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mPreviewPopup.setContentView(mPreviewText);
        mPreviewPopup.setBackgroundDrawable(null);
        mColorNormal = res.getColor(R.color.candidate_normal);
        mColorRecommended = res.getColor(R.color.candidate_recommended);
        mColorOther = res.getColor(R.color.candidate_other);
        mDivider = res.getDrawable(R.drawable.keyboard_suggest_strip_divider);
       /* mAddToDictionaryHint = res.getString(R.string.hint_add_to_dictionary);   */ //Not needed

        mPaint = new Paint();
        mPaint.setColor(mColorNormal);
        mPaint.setAntiAlias(true);
        mPaint.setTextSize(mPreviewText.getTextSize());
        mPaint.setStrokeWidth(0);
       /* mPaint.setTextAlign(Align.CENTER);*/ //Taking off for Bidi fixing
        mDescent = (int) mPaint.descent();
        // 80 pixels for a 160dpi device would mean half an inch
      /*  mMinTouchableWidth = (int) (getResources().getDisplayMetrics().density * 50);*/ //Taking off for Bidi fixing calculation will be done later

        mGestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent me) {
                if (mSuggestions.size() > 0) {
                    if (me.getX() + mScrollX() < mWordWidth[0] && mScrollX() < 10) {  //For Bidi support
                        longPressFirstWord();
                    }
                }
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2,
                    float distanceX, float distanceY) {
                final int width = getWidth();
                mScrolled = true;
                //int scrollX = getScrollX();   Will be using mScrollx instead
                mScrollX += (int) distanceX;
                if (mScrollX < 0) {
                    mScrollX = 0;
                }
                if (distanceX > 0 && mScrollX + width > mTotalWidth) {
                    mScrollX -= (int) distanceX;
                }
                mTargetScrollX = mScrollX;
                //scrollTo(scrollX, getScrollY()); //Taking care for scrolling later on
                mTouchX += mScrollX;
                hidePreview();
                invalidate();
                return true;
            }
        });
        setHorizontalFadingEdgeEnabled(true);
        setWillNotDraw(false);
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);
        //scrollTo(0, getScrollY()); //Taking care for scrolling later on
        mScrollX = 0;

    }

    /**
     * A connection back to the service to communicate with the text field
     * @param listener
     */
    public void setService(LatinIME listener) {
        mService = listener;
    }

    @Override
    public int computeHorizontalScrollRange() {
        return mTotalWidth;
    }

    /**
     * If the canvas is null, then only touch calculations are performed to pick the target
     * candidate.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        if (canvas != null) {
            super.onDraw(canvas);
        }
        mTotalWidth = 0;
        if (mSuggestions == null) return;

        final int height = getHeight();
        if (mBgPadding == null) {
            mBgPadding = new Rect(0, 0, 0, 0);
            if (getBackground() != null) {
                getBackground().getPadding(mBgPadding);
            }
            mDivider.setBounds(0, mBgPadding.top, mDivider.getIntrinsicWidth(), //Fixing for Bidi
                    mDivider.getIntrinsicHeight());
        }
        int x = 0;
        final int count = mSuggestions.size();
        //final int width = getWidth(); //not needed here
        final Rect bgPadding = mBgPadding;
        final Paint paint = mPaint;
        final int touchX = mTouchX;
        final int scrollX = mScrollX;  //Making sure calculation for Bidi works
        final boolean scrolled = mScrolled;
        final boolean typedWordValid = mTypedWordValid;
        final int y = (int) (mPaint.getTextSize() - (2*mDescent)); // Recalculating for Bidi

        for (int i = 0; i < count; i++) {
            CharSequence suggestion = mSuggestions.get(i);
            if (suggestion == null) || (suggestion.length() == 0)) continue;
            paint.setColor(mColorNormal);
            if (mHaveMinimalSuggestion
                    && ((i == 1 && !typedWordValid) || (i == 0 && typedWordValid))) {
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setColor(mColorRecommended);
            } else if (i != 0) {
				paint.setTypeface(Typeface.DEFAULT); //for Bidi Support
                paint.setColor(mColorOther);
            }
            final int wordWidth;
            if (mWordWidth[i] != 0) {
                wordWidth = mWordWidth[i];
            } else {
                float textWidth =  paint.measureText(suggestion, 0, suggestion.length());
                wordWidth =  (int) (textWidth + (X_GAP * 1.8)); //Calculatin word length for Bidi Support
                mWordWidth[i] = wordWidth;
            }

            mWordX[i] = x;

            if ((touchX >= x) && (touchX < x + wordWidth) && (!scrolled) &&
            	(touchX != OUT_OF_BOUNDS)) {
                if (canvas != null ) {   //!mShowingAddToDictionary not needed here
                    canvas.translate(x, 0);
                    mSelectionHighlight.setBounds(0, bgPadding.top, wordWidth, height);
                    mSelectionHighlight.draw(canvas);
                    canvas.translate(-x, 0);
                    showPreview(i, null);
                }
                mSelectedString = suggestion;
                mSelectedIndex = i;
            }
//canvas.drawText doesn't have the Bidi fixes needed we will be using workaround
            if (canvas != null) {
//                canvas.drawText(suggestion, 0, suggestion.length(), x + wordWidth / 2, y, paint); //Using workaround for Bidi

				TextPaint suggestionPaint = new TextPaint(paint);
				StaticLayout suggestionText = new StaticLayout(suggestion, suggestionPaint, wordWidth, Alignment.ALIGN_CENTER,(float)0.0,(float)0.0,false);
				if (suggestionText != null) {
					canvas.translate(x , y);
					suggestionText.draw(canvas);
					canvas.translate(-x , -y);
				}
                paint.setColor(mColorOther);
                canvas.translate(x + wordWidth, 0);
                // Draw a divider unless it's after the hint
              //  if (!(mShowingAddToDictionary && i == 1)) {  //not using mShowingAddToDictionary
                    mDivider.draw(canvas);
              //  }
                canvas.translate(-x - wordWidth, 0);
            }
            paint.setTypeface(Typeface.DEFAULT);
            x += wordWidth;
        }
        mTotalWidth = x + mScrollX;  //for Bidi Support
        if (mTargetScrollX != mScrollX) {
            scrollToTarget();
        }
    }

    private void scrollToTarget() {
      //  int scrollX = getScrollX();  Using mScrollx instead
        if (mTargetScrollX > mScrollX) {
            scrollX += SCROLL_PIXELS;
            if (mScrollX >= mTargetScrollX) {
                scrollX = mTargetScrollX;
                //scrollTo(scrollX, getScrollY());  Not needed
                requestLayout();
            }
           /* } else {
                scrollTo(scrollX, getScrollY());*/

        } else {
            mScrollX -= SCROLL_PIXELS;
            if (mScrollX <= mTargetScrollX) {
                mScrollX = mTargetScrollX;
               // scrollTo(scrollX, getScrollY());  Not Needed
                requestLayout();
            }
            /*} else {
                scrollTo(scrollX, getScrollY());*/

        }
        invalidate();
    }

    public void setSuggestions(List<CharSequence> suggestions, boolean completions,
            boolean typedWordValid, boolean haveMinimalSuggestion) {
        clear();
        if (suggestions != null) {
            mSuggestions = new ArrayList<CharSequence>(suggestions);
        }
        mShowingCompletions = completions;
        mTypedWordValid = typedWordValid;
        //scrollTo(0, getScrollY());  Not needed using mScrollx
        mScrollX = 0;
        mTargetScrollX = 0;
        mHaveMinimalSuggestion = haveMinimalSuggestion;
        // Compute the total width
        onDraw(null);
        invalidate();
        requestLayout();
    }

    public void showAddToDictionaryHint(CharSequence word) {
        mWordToAddToDictionary = word;
        ArrayList<CharSequence> suggestions = new ArrayList<CharSequence>();
        suggestions.add(word);
        suggestions.add(mAddToDictionaryHint);
        setSuggestions(suggestions, false, false, false);
        mShowingAddToDictionary = true;
    }

    public boolean dismissAddToDictionaryHint() {
        if (!mShowingAddToDictionary) return false;
        clear();
        return true;
    }
//Now calculating the scrolling for Bidi Support too.
    public void scrollPrev() {
      /*  int i = 0;
        final int count = mSuggestions.size();
        int firstItem = 0; // Actually just before the first item, if at the boundary
        while (i < count) {
            if (mWordX[i] < getScrollX()
                    && mWordX[i] + mWordWidth[i] >= getScrollX() - 1) {
                firstItem = i;
                break;
            }
            i++;
        }
        int leftEdge = mWordX[firstItem] + mWordWidth[firstItem] - getWidth();
        if (leftEdge < 0) leftEdge = 0;
        updateScrollPosition(leftEdge);*/
        //0.75 to the left
        updateScrollPosition(mScrollX - (int)(0.75 * getWidth()));
    }

    public void scrollNext() {
      /*  int i = 0;
        int scrollX = getScrollX();
        int targetX = scrollX;
        final int count = mSuggestions.size();
        int rightEdge = scrollX + getWidth();
        while (i < count) {
            if (mWordX[i] <= rightEdge &&
                    mWordX[i] + mWordWidth[i] >= rightEdge) {
                targetX = Math.min(mWordX[i], mTotalWidth - getWidth());
                break;
            }
            i++;
        }
        updateScrollPosition(targetX);*/
        //0.75 to the right
        updateScrollPosition(mScrollX + (int)(0.75 * getWidth()));

    }

    private void updateScrollPosition(int targetX) {
		if (targetX < 0)
			targetX = 0;
		if (targetX > (mTotalWidth - 50))
			targetX = (mTotalWidth - 50);

        if (targetX != mScrollX) {
            // TODO: Animate
            mTargetScrollX = targetX;
            requestLayout();
            invalidate();
            mScrolled = true;
        }
    }

    public void clear() {
        mSuggestions = EMPTY_LIST;
        mTouchX = OUT_OF_BOUNDS;
        mSelectedString = null;
        mSelectedIndex = OUT_OF_BOUNDS;
        mShowingAddToDictionary = false;
        invalidate();
        Arrays.fill(mWordWidth, 0);
        Arrays.fill(mWordX, 0);
        if (mPreviewPopup.isShowing()) {
            mPreviewPopup.dismiss();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent me) {

        if (mGestureDetector.onTouchEvent(me)) {
            return true;
        }

        int action = me.getAction();
        int x = (int) me.getX();
        int y = (int) me.getY();
        mTouchX = x;

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mScrolled = false;
            invalidate();
            break;
        case MotionEvent.ACTION_MOVE:
            if (y <= 0) {
                // Fling up!?
                if (mSelectedString != null) {
                    if (!mShowingCompletions) {
                        TextEntryState.acceptedSuggestion(mSuggestions.get(0),
                                mSelectedString);
                    }
                    mService.pickSuggestionManually(mSelectedIndex, mSelectedString);
                    mSelectedString = null;
                    mSelectedIndex = OUT_OF_BOUNDS;
                }
            }
            invalidate();
            break;
        case MotionEvent.ACTION_UP:
            if (!mScrolled) {
                if (mSelectedString != null) {
                  /*  if (mShowingAddToDictionary) {
                        longPressFirstWord();
                        clear();
                    } else {*/
                        if (!mShowingCompletions) {
                            TextEntryState.acceptedSuggestion(mSuggestions.get(0),
                                    mSelectedString);
                        }
                        mService.pickSuggestionManually(mSelectedIndex, mSelectedString);
                   // }
                }
            }
            mSelectedString = null;
            mSelectedIndex = OUT_OF_BOUNDS;
            removeHighlight();
            hidePreview();
            requestLayout();
            mScrolled = false;

            break;
        }
        return true;
    }

    /**
     * For flick through from keyboard, call this method with the x coordinate of the flick
     * gesture.
     * @param x
     */
    public void takeSuggestionAt(float x) {
        mTouchX = (int) x;
        // To detect candidate
        onDraw(null);
        if (mSelectedString != null) {
            if (!mShowingCompletions) {
                TextEntryState.acceptedSuggestion(mSuggestions.get(0), mSelectedString);
            }
            mService.pickSuggestionManually(mSelectedIndex, mSelectedString);
        }
        invalidate();
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_REMOVE_THROUGH_PREVIEW), 200);
    }

    private void hidePreview() {
        mCurrentWordIndex = OUT_OF_BOUNDS;
        if (mPreviewPopup.isShowing()) {
            mHandler.sendMessageDelayed(mHandler
                    .obtainMessage(MSG_REMOVE_PREVIEW), 60);
        }
    }

    private void showPreview(int wordIndex, String altText) {
        int oldWordIndex = mCurrentWordIndex;
        mCurrentWordIndex = wordIndex;
        // If index changed or changing text
        if (oldWordIndex != mCurrentWordIndex || altText != null) {
            if (wordIndex == OUT_OF_BOUNDS) {
                hidePreview();
            } else {
                CharSequence word = altText != null? altText : mSuggestions.get(wordIndex);
                mPreviewText.setText(word);
                mPreviewText.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int wordWidth = (int) (mPaint.measureText(word, 0, word.length()) + X_GAP * 2);
                final int popupWidth = wordWidth
                        + mPreviewText.getPaddingLeft() + mPreviewText.getPaddingRight();
                final int popupHeight = mPreviewText.getMeasuredHeight();
                //mPreviewText.setVisibility(INVISIBLE);
                mPopupPreviewX = mWordX[wordIndex] - mPreviewText.getPaddingLeft() - mScrollX; //Recalculating for Bidi
                        //+ (mWordWidth[wordIndex] - wordWidth) / 2;
                mPopupPreviewY = - popupHeight;
                mHandler.removeMessages(MSG_REMOVE_PREVIEW);
                int [] offsetInWindow = new int[2];
                getLocationInWindow(offsetInWindow);
                if (mPreviewPopup.isShowing()) {
                    mPreviewPopup.update(mPopupPreviewX, mPopupPreviewY + offsetInWindow[1],
                            popupWidth, popupHeight);
                } else {
                    mPreviewPopup.setWidth(popupWidth);
                    mPreviewPopup.setHeight(popupHeight);
                    mPreviewPopup.showAtLocation(this, Gravity.NO_GRAVITY, mPopupPreviewX,
                            mPopupPreviewY + offsetInWindow[1]);
                }
                mPreviewText.setVisibility(VISIBLE);
            }
        }
    }

    private void removeHighlight() {
        mTouchX = OUT_OF_BOUNDS;
        invalidate();
    }

    private void longPressFirstWord() {
        CharSequence word = mSuggestions.get(0);
        if (word.length() < 2) return;
        if (mService.addWordToDictionary(word.toString())) {
            showPreview(0, getContext().getResources().getString(R.string.added_word, word));
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        hidePreview();
    }
        public int getmScrollX() {
			return mScrollX;
		}

}
