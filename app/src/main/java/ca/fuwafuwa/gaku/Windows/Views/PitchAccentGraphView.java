package ca.fuwafuwa.gaku.Windows.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import ca.fuwafuwa.gaku.R;

public class PitchAccentGraphView extends View {

    private String reading = "";
    private String pattern = ""; // "0100"
    private Paint linePaint;
    private Paint circlePaint;
    private Paint textPaint;

    // Constants to match mockup style
    private float charWidth;
    private float graphHeight = 5; // Vertical space for lines
    private float highY = 8;
    private float textY = 35;

    public PitchAccentGraphView(Context context) {
        super(context);
        init();
    }

    public PitchAccentGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setColor(Color.LTGRAY);
        linePaint.setStrokeWidth(4);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        circlePaint = new Paint();
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(36); // Approx 14sp
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setAntiAlias(true);
        // Load fonts if needed
    }

    public void setData(String reading, String pattern) {
        this.reading = reading;
        this.pattern = pattern;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (reading.isEmpty()) {
            setMeasuredDimension(0, 0);
            return;
        }
        float totalWidth = textPaint.measureText(reading);
        charWidth = totalWidth / reading.length();
        int width = (int) totalWidth + 20;
        int height = (int) textY + 10;
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (reading.isEmpty())
            return;

        float currentX = 5;
        canvas.drawText(reading, currentX, textY, textPaint);

        if (pattern.isEmpty())
            return;

        // Draw Overline
        float lineY = highY;
        boolean inHigh = false;

        for (int i = 0; i < pattern.length() && i < reading.length(); i++) {
            boolean isHigh = pattern.charAt(i) == '1';
            float charW = textPaint.measureText(reading.substring(i, i + 1));

            if (isHigh) {
                if (!inHigh) {
                    // Start high
                    inHigh = true;
                    // Draw vertical start if not the first character or if first character is low
                    // (standard Japanese pitch)
                    // Actually usually just a horizontal line. The drop is more important.
                }
                // Draw horizontal line over this char
                canvas.drawLine(currentX, lineY, currentX + charW, lineY, linePaint);

                // If next is low or end, draw drop
                if (i + 1 == pattern.length() || pattern.charAt(i + 1) == '0') {
                    canvas.drawLine(currentX + charW, lineY, currentX + charW, textY - 5, linePaint);
                    inHigh = false;
                }
            } else {
                inHigh = false;
            }
            currentX += charW;
        }
    }
}
