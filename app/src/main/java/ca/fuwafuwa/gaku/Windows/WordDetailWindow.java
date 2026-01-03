package ca.fuwafuwa.gaku.Windows;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.List;

import ca.fuwafuwa.gaku.Analysis.ParsedWord;
import ca.fuwafuwa.gaku.Database.User.UserDatabaseHelper;
import ca.fuwafuwa.gaku.Database.User.UserWord;
import ca.fuwafuwa.gaku.R;
import ca.fuwafuwa.gaku.Windows.Views.PitchAccentGraphView;
import android.widget.Button;
import android.widget.Toast;
import android.util.Log;
import java.sql.SQLException;

public class WordDetailWindow extends Window {

    public interface OnStatusChangeListener {
        void onStatusChanged(ParsedWord word);
    }

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
        // For Jiten/Anki, these just open the browser. Local status update might need
        // verification?
        // For now, assume user handles status manually (via sync) OR we update loosely.
        // User request: "jpd-breader buttons".
        // In jpd-breader, clicking a grade sends review.
        // Since we can't send review to JPDB easily (due to API limit), and Anki opens
        // a browser window...
        // We will just proxy the call.

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
            // No local status update for now as we don't know the result.
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
            // Hide or clear pitch graph if no data
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

    public void showAt(int x, int y) {
        params.x = x;
        params.y = y;
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
    protected WindowManager.LayoutParams getDefaultParams() {
        WindowManager.LayoutParams params = super.getDefaultParams();
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.TOP | Gravity.LEFT; // Must be TOP|LEFT for showAt(x, y) to work correctly
        params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE; // Make it focusable to capture touches
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL; // Allow touches outside to go to other windows
        params.alpha = 1.0f; // Full opacity for the detail window
        return params;
    }
}
