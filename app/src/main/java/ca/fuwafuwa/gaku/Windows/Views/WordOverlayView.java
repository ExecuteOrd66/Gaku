package ca.fuwafuwa.gaku.Windows.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import ca.fuwafuwa.gaku.Analysis.ParsedWord;
import ca.fuwafuwa.gaku.Database.User.UserWord;
import ca.fuwafuwa.gaku.R;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import ca.fuwafuwa.gaku.Analysis.ParsedLine;
import ca.fuwafuwa.gaku.Analysis.ParsedResult;
import android.text.SpannableString;
import android.text.style.ClickableSpan;
import android.text.method.LinkMovementMethod;

public class WordOverlayView extends RelativeLayout {

    private static final String TAG = "WordOverlayView";
    private List<ParsedWord> words = new ArrayList<>();
    private List<ParsedLine> lines = new ArrayList<>();
    private Paint paintUnknown;
    private Paint paintLearning;
    private Paint paintKnown;
    private Paint paintMature;
    private Paint paintMastered;
    private Paint paintDue;
    private Paint paintDismissed;
    private Paint paintTouch;
    private ca.fuwafuwa.gaku.TextDirection textDirection = ca.fuwafuwa.gaku.TextDirection.AUTO;
    private OnWordClickListener listener;

    public interface OnWordClickListener {
        void onWordClicked(ParsedWord word);
    }

    public WordOverlayView(Context context) {
        super(context);
        init();
    }

    public WordOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WordOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        paintUnknown = new Paint();
        paintUnknown.setColor(ContextCompat.getColor(getContext(), R.color.status_unknown));
        paintUnknown.setStrokeWidth(6);
        paintUnknown.setStyle(Paint.Style.STROKE);

        paintLearning = new Paint();
        paintLearning.setColor(ContextCompat.getColor(getContext(), R.color.status_learning));
        paintLearning.setStrokeWidth(6);
        paintLearning.setStyle(Paint.Style.STROKE);

        paintKnown = new Paint();
        paintKnown.setColor(ContextCompat.getColor(getContext(), R.color.status_known));
        paintKnown.setStrokeWidth(6);
        paintKnown.setStyle(Paint.Style.STROKE);

        paintMature = new Paint();
        paintMature.setColor(ContextCompat.getColor(getContext(), R.color.status_mature));
        paintMature.setStrokeWidth(6);
        paintMature.setStyle(Paint.Style.STROKE);

        paintMastered = new Paint();
        paintMastered.setColor(ContextCompat.getColor(getContext(), R.color.status_mastered));
        paintMastered.setStrokeWidth(6);
        paintMastered.setStyle(Paint.Style.STROKE);

        paintDue = new Paint();
        paintDue.setColor(ContextCompat.getColor(getContext(), R.color.status_due));
        paintDue.setStrokeWidth(6);
        paintDue.setStyle(Paint.Style.STROKE);

        paintDismissed = new Paint();
        paintDismissed.setColor(ContextCompat.getColor(getContext(), R.color.status_dismissed));
        paintDismissed.setStrokeWidth(0); // Invisible? Or just do not draw.
        paintDismissed.setStyle(Paint.Style.STROKE);
        paintDismissed.setAlpha(0); // Fully transparent
        paintDismissed.setAlpha(128);

        paintTouch = new Paint();
        paintTouch.setColor(0x33000000); // Semi-transparent black for touch feedback
        paintTouch.setStyle(Paint.Style.FILL);
    }

    public void setTextDirection(ca.fuwafuwa.gaku.TextDirection direction) {
        this.textDirection = direction;
        invalidate();
    }

    public void setParsedResult(ParsedResult result) {
        this.words = result.getWords();
        this.lines = result.getLines();
        updateTextViews();
        invalidate(); // Redraw underlines
    }

    private void updateTextViews() {
        removeAllViews();
        int offset = getOffset();
        for (final ParsedLine line : lines) {
            Rect rect = line.getBoundingBox();
            final TextView tv = new TextView(getContext());
            tv.setText(line.getText());
            tv.setTextColor(Color.TRANSPARENT);
            tv.setTextIsSelectable(true);
            tv.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Critical for alignment: Use a monospaced font or ensure fits
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);

            // Calculate font size to fit rect height
            float height = rect.height();
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, height * 0.95f);
            tv.setPadding(0, 0, 0, 0);
            tv.setIncludeFontPadding(false);

            LayoutParams lp = new LayoutParams(rect.width(), rect.height());
            lp.leftMargin = rect.left + offset;
            lp.topMargin = rect.top + offset;
            tv.setLayoutParams(lp);

            // Custom touch listener to handle both Selection (Long Press) and Word Taps
            tv.setOnTouchListener(new OnTouchListener() {
                private long downTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        downTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - downTime < 300) {
                            // It's a short tap, find the word under the tap
                            handleTap(event.getRawX(), event.getRawY());
                            return true;
                        }
                    }
                    return false; // Let TextView handle Long Press for selection
                }
            });

            addView(tv);
        }
    }

    private void handleTap(float rawX, float rawY) {
        // Convert screen coordinates to view coordinates
        int[] location = new int[2];
        getLocationOnScreen(location);
        int localX = (int) (rawX - location[0] - getOffset());
        int localY = (int) (rawY - location[1] - getOffset());

        for (ParsedWord word : words) {
            if (word.getBoundingBox().contains(localX, localY)) {
                if (listener != null) {
                    listener.onWordClicked(word);
                }
                return;
            }
        }
    }

    public void setOnWordClickListener(OnWordClickListener listener) {
        this.listener = listener;
    }

    private int getOffset() {
        return ca.fuwafuwa.gaku.GakuTools.dpToPx(getContext(), 1) + 1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (words == null || words.isEmpty()) {
            return;
        }

        int offset = getOffset();
        int gap = ca.fuwafuwa.gaku.GakuTools.dpToPx(getContext(), 1);
        boolean isVertical = textDirection == ca.fuwafuwa.gaku.TextDirection.VERTICAL;

        // Group segments by status
        java.util.Map<Integer, List<Float>> segmentsByStatus = new java.util.HashMap<>();

        for (ParsedWord word : words) {
            int status = word.getStatus();
            if (status == UserWord.STATUS_DISMISSED) {
                continue;
            }

            if (!segmentsByStatus.containsKey(status)) {
                segmentsByStatus.put(status, new ArrayList<>());
            }

            Rect rect = word.getBoundingBox();
            List<Float> segments = segmentsByStatus.get(status);

            if (isVertical) {
                // x0, y0, x1, y1
                segments.add((float) (rect.right + offset));
                segments.add((float) (rect.top + offset + gap));
                segments.add((float) (rect.right + offset));
                segments.add((float) (rect.bottom + offset - gap));
            } else {
                segments.add((float) (rect.left + offset + gap));
                segments.add((float) (rect.bottom + offset - 2));
                segments.add((float) (rect.right + offset - gap));
                segments.add((float) (rect.bottom + offset - 2));
            }
        }

        // Draw batched segments
        long startTime = System.nanoTime();
        for (java.util.Map.Entry<Integer, List<Float>> entry : segmentsByStatus.entrySet()) {
            List<Float> floatList = entry.getValue();
            if (floatList.isEmpty())
                continue;

            float[] pts = new float[floatList.size()];
            for (int i = 0; i < floatList.size(); i++) {
                pts[i] = floatList.get(i);
            }

            Paint paint = getPaintForStatus(entry.getKey());
            canvas.drawLines(pts, paint);
        }
        long endTime = System.nanoTime();
        Log.d(TAG, String.format("onDraw batched: %d segments in %.3f ms", words.size(), (endTime - startTime) / 1e6));
    }

    private Paint getPaintForStatus(int status) {
        switch (status) {
            case UserWord.STATUS_LEARNING:
                return paintLearning;
            case UserWord.STATUS_KNOWN:
                return paintKnown;
            case UserWord.STATUS_MATURE:
                return paintMature;
            case UserWord.STATUS_MASTERED:
                return paintMastered;
            case UserWord.STATUS_DUE:
                return paintDue;
            case UserWord.STATUS_DISMISSED:
                return paintDismissed;
            default:
                return paintUnknown;
        }
    }

    // Touch event handling is now mostly handled by the TextViews for clicks.
    // However, we can keep the custom drawing in onDraw.
    // The TextViews sit on top and will consume touches.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We let the TextViews handle the touch first
        return false;
    }
}
