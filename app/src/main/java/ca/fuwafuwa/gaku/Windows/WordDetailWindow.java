package ca.fuwafuwa.gaku.Windows;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

import java.sql.SQLException;
import java.util.List;

import ca.fuwafuwa.gaku.Analysis.ParsedWord;
import ca.fuwafuwa.gaku.Database.User.UserDatabaseHelper;
import ca.fuwafuwa.gaku.Database.User.UserWord;
import ca.fuwafuwa.gaku.GakuTools;
import ca.fuwafuwa.gaku.R;
import ca.fuwafuwa.gaku.Windows.Views.PitchAccentGraphView;

public class WordDetailWindow extends Window {

    public interface OnStatusChangeListener {
        void onStatusChanged(ParsedWord word);
    }

    private View popupContainer;
    private TextView wordText;
    private TextView tagText;
    private PitchAccentGraphView pitchGraph;
    private TextView defText;
    private TextView freqText;

    private android.widget.LinearLayout layoutMining;
    private android.widget.LinearLayout layoutGrading;

    // Mining
    private Button btnMineAdd;
    private Button btnMineBlacklist;
    private Button btnMineNeverForget;

    // Grading
    private Button btnGradeNothing;
    private Button btnGradeSomething;
    private Button btnGradeHard;
    private Button btnGradeGood;
    private Button btnGradeEasy;

    private android.widget.ImageButton btnClose;
    private ParsedWord currentWord;
    private OnStatusChangeListener statusChangeListener;
    private ca.fuwafuwa.gaku.Logic.ReviewController reviewController;

    public WordDetailWindow(Context context, WindowCoordinator windowCoordinator) {
        super(context, windowCoordinator, R.layout.view_popup_word);
        this.reviewController = new ca.fuwafuwa.gaku.Logic.ReviewController(context);

        ViewGroup windowContent = window.findViewById(R.id.content_view);
        if (windowContent.getChildCount() > 0) {
            popupContainer = windowContent.getChildAt(0);
        }

        wordText = window.findViewById(R.id.popup_word);
        tagText = window.findViewById(R.id.popup_tag);
        pitchGraph = window.findViewById(R.id.popup_pitch_graph);
        defText = window.findViewById(R.id.popup_def);
        freqText = window.findViewById(R.id.popup_freq);
        btnClose = window.findViewById(R.id.btn_close_popup);

        layoutMining = window.findViewById(R.id.layout_mining_buttons);
        layoutGrading = window.findViewById(R.id.layout_grading_buttons);

        btnMineAdd = window.findViewById(R.id.btn_mine_add);
        btnMineBlacklist = window.findViewById(R.id.btn_mine_blacklist);
        btnMineNeverForget = window.findViewById(R.id.btn_mine_never_forget);

        btnGradeNothing = window.findViewById(R.id.btn_grade_nothing);
        btnGradeSomething = window.findViewById(R.id.btn_grade_something);
        btnGradeHard = window.findViewById(R.id.btn_grade_hard);
        btnGradeGood = window.findViewById(R.id.btn_grade_good);
        btnGradeEasy = window.findViewById(R.id.btn_grade_easy);

        // Mining Listeners
        btnMineAdd.setOnClickListener(v -> {
            reviewController.mine(currentWord);
            updateStatus(UserWord.STATUS_LEARNING);
        });
        btnMineBlacklist.setOnClickListener(v -> {
            reviewController.setJpdbFlag(currentWord, "blacklist", false);
            updateStatus(UserWord.STATUS_DISMISSED);
        });
        btnMineNeverForget.setOnClickListener(v -> {
            reviewController.setJpdbFlag(currentWord, "never-forget", false);
            updateStatus(UserWord.STATUS_MASTERED);
        });

        // Grading Listeners
        android.view.View.OnClickListener gradeListener = v -> {
            String grade = "good";
            if (v == btnGradeNothing)
                grade = "nothing";
            if (v == btnGradeSomething)
                grade = "something";
            if (v == btnGradeHard)
                grade = "hard";
            if (v == btnGradeGood)
                grade = "good";
            if (v == btnGradeEasy)
                grade = "easy";
            reviewController.grade(currentWord, grade);
        };

        btnGradeNothing.setOnClickListener(gradeListener);
        btnGradeSomething.setOnClickListener(gradeListener);
        btnGradeHard.setOnClickListener(gradeListener);
        btnGradeGood.setOnClickListener(gradeListener);
        btnGradeEasy.setOnClickListener(gradeListener);

        btnClose.setOnClickListener(v -> hide());
    }

    public void setWord(ParsedWord word) {
        this.currentWord = word;
        wordText.setText(word.getSurface());

        boolean showMining = false;

        switch (word.getStatus()) {
            case UserWord.STATUS_LEARNING:
                tagText.setText("LEARNING");
                tagText.setBackgroundColor(Color.parseColor("#e67e22"));
                break;
            case UserWord.STATUS_KNOWN:
                tagText.setText("KNOWN");
                tagText.setBackgroundColor(Color.parseColor("#2ecc71"));
                break;
            case UserWord.STATUS_MATURE:
                tagText.setText("MATURE");
                tagText.setBackgroundColor(Color.parseColor("#9b59b6"));
                break;
            case UserWord.STATUS_MASTERED:
                tagText.setText("MASTERED");
                tagText.setBackgroundColor(Color.parseColor("#27ae60"));
                break;
            case UserWord.STATUS_DUE:
                tagText.setText("DUE"); // Red?
                tagText.setBackgroundColor(Color.parseColor("#e74c3c"));
                break;
            case UserWord.STATUS_DISMISSED:
                tagText.setText("DISMISSED");
                tagText.setBackgroundColor(Color.parseColor("#95a5a6"));
                showMining = true;
                break;
            default:
                tagText.setText("UNKNOWN"); // New
                tagText.setBackgroundColor(Color.parseColor("#3498db"));
                showMining = true;
                break;
        }

        layoutMining.setVisibility(showMining ? android.view.View.VISIBLE : android.view.View.GONE);
        layoutGrading.setVisibility(!showMining ? android.view.View.VISIBLE : android.view.View.GONE);

        if (word.getPitchPattern() != null && word.getReading() != null) {
            pitchGraph.setData(word.getReading(), word.getPitchPattern());
        } else {
            pitchGraph.setData("", "");
        }

        if (word.getMeanings() != null && !word.getMeanings().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            List<String> meanings = word.getMeanings();
            List<String> meaningPos = word.getMeaningPos();
            String dictionary = word.getDictionary();

            for (int i = 0; i < meanings.size(); i++) {
                if (i != 0) {
                    sb.append("\n\n");
                }
                sb.append(ca.fuwafuwa.gaku.LangUtils.Companion.ConvertIntToCircledNum(i + 1));
                sb.append(" ");

                if (ca.fuwafuwa.gaku.Constants.JMDICT_DATABASE_NAME.equals(dictionary) &&
                        meaningPos != null && i < meaningPos.size() && !meaningPos.get(i).isEmpty()) {
                    sb.append(String.format("(%s) ", meaningPos.get(i)));
                }

                sb.append(meanings.get(i));
            }
            defText.setText(sb.toString());
        } else {
            defText.setText("No definitions found.");
        }

        freqText.setText("POS: " + (word.getPos() != null ? word.getPos() : "Unknown"));
    }

    /**
     * Calculates the best position for the popup based on the side of the capture
     * window
     * that has the most available screen real estate.
     */
    public void showForWordBounds(Rect globalWordRect, Rect captureWindowRect) {
        if (popupContainer == null)
            return;

        params.x = 0;
        params.y = 0;

        popupContainer.post(() -> {
            Point displaySize = getRealDisplaySize();
            int popupWidth = popupContainer.getWidth();
            int popupHeight = popupContainer.getHeight();
            int padding = GakuTools.dpToPx(context, 10);

            // 1. Calculate gaps on all 4 sides of the Capture Window
            int topGap = captureWindowRect.top;
            int bottomGap = displaySize.y - captureWindowRect.bottom;
            int leftGap = captureWindowRect.left;
            int rightGap = displaySize.x - captureWindowRect.right;

            // 2. Find the largest gap
            int maxGap = Math.max(Math.max(topGap, bottomGap), Math.max(leftGap, rightGap));

            int finalX;
            int finalY;

            // 3. Position based on the largest gap
            if (maxGap == topGap) {
                // Place Above
                finalY = captureWindowRect.top - popupHeight - padding;
                // Align X with word center
                finalX = globalWordRect.centerX() - (popupWidth / 2);
            } else if (maxGap == bottomGap) {
                // Place Below
                finalY = captureWindowRect.bottom + padding;
                // Align X with word center
                finalX = globalWordRect.centerX() - (popupWidth / 2);
            } else if (maxGap == leftGap) {
                // Place Left
                finalX = captureWindowRect.left - popupWidth - padding;
                // Align Y with word center
                finalY = globalWordRect.centerY() - (popupHeight / 2);
            } else {
                // Place Right (Default if all equal)
                finalX = captureWindowRect.right + padding;
                // Align Y with word center
                finalY = globalWordRect.centerY() - (popupHeight / 2);
            }

            // 4. Clamping (Ensure popup stays entirely on screen)
            // Even if we picked the "best" side, the word might be near the edge of that
            // side
            if (finalX < 0)
                finalX = 0;
            if (finalX + popupWidth > displaySize.x)
                finalX = displaySize.x - popupWidth;

            if (finalY < 0)
                finalY = 0;
            if (finalY + popupHeight > displaySize.y)
                finalY = displaySize.y - popupHeight;

            popupContainer.setX(finalX);
            popupContainer.setY(finalY);
        });

        if (!addedToWindowManager) {
            show();
        } else {
            windowManager.updateViewLayout(window, params);
            bringToFront();
        }
    }

    public void setOnStatusChangeListener(OnStatusChangeListener listener) {
        this.statusChangeListener = listener;
    }

    private void updateStatus(int status) {
        if (currentWord == null)
            return;
        currentWord.setStatus(status);
        setWord(currentWord);

        if (statusChangeListener != null) {
            statusChangeListener.onStatusChanged(currentWord);
        }

        new Thread(() -> {
            try {
                UserDatabaseHelper db = UserDatabaseHelper.instance(context);
                UserWord uw = db.getUserWordDao().queryBuilder()
                        .where().eq("text", currentWord.getSurface()).queryForFirst();
                if (uw == null) {
                    uw = new UserWord(currentWord.getSurface(),
                            currentWord.getReading() != null ? currentWord.getReading() : "", status);
                    db.getUserWordDao().create(uw);
                } else {
                    uw.setStatus(status);
                    db.getUserWordDao().update(uw);
                }
            } catch (SQLException e) {
                Log.e("WordDetail", "Failed to update status", e);
            }
        }).start();
    }

    @Override
    public boolean onTouch(MotionEvent e) {
        // We only care about the release of the tap
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (popupContainer != null) {

                // 1. Get the screen bounds of the visible popup UI box
                int[] location = new int[2];
                popupContainer.getLocationOnScreen(location);
                Rect popupRect = new Rect(location[0], location[1],
                        location[0] + popupContainer.getWidth(),
                        location[1] + popupContainer.getHeight());

                float rawX = e.getRawX();
                float rawY = e.getRawY();

                // 2. If the user clicks INSIDE the popup UI (buttons/text),
                // return false. This allows the OS to pass the touch to child
                // views like your "Mine" or "Close" buttons.
                if (popupRect.contains((int) rawX, (int) rawY)) {
                    return false;
                }

                // 3. If they clicked OUTSIDE the popup, let's see if there's
                // a word underneath in the CaptureWindow.
                CaptureWindow capWin = (CaptureWindow) windowCoordinator.getWindow("WINDOW_CAPTURE");

                if (capWin != null) {
                    ParsedWord hitWord = capWin.getWordAtScreenCoords(rawX, rawY);
                    if (hitWord != null) {
                        // We found a new word!
                        // Tell CaptureWindow to trigger its click logic, which
                        // calls setWord() and showForWordBounds() on THIS instance.
                        capWin.onWordHandleExternal(hitWord);
                        return true; // Return true to keep the window open/blocking
                    }
                }

                // 4. Fallback: They clicked truly blank space. Hide the window.
                hide();
                return true;
            }
        }

        // Return true for all other actions (DOWN, MOVE) to ensure
        // this window blocks all touch input to the game/app underneath.
        return true;
    }

    @Override
    protected WindowManager.LayoutParams getDefaultParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT > 25 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.x = 0;
        params.y = 0;
        params.gravity = Gravity.TOP | Gravity.LEFT;
        return params;
    }
}