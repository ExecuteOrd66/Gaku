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
    private Button btnKnown;
    private Button btnMature;
    private Button btnAnki;
    private android.widget.ImageButton btnClose;
    private ParsedWord currentWord;
    private OnStatusChangeListener statusChangeListener;

    public WordDetailWindow(Context context, WindowCoordinator windowCoordinator) {
        super(context, windowCoordinator, R.layout.view_popup_word);

        wordText = window.findViewById(R.id.popup_word);
        tagText = window.findViewById(R.id.popup_tag);
        pitchGraph = window.findViewById(R.id.popup_pitch_graph);
        defText = window.findViewById(R.id.popup_def);
        freqText = window.findViewById(R.id.popup_freq);

        btnKnown = window.findViewById(R.id.btn_mark_known);
        btnMature = window.findViewById(R.id.btn_mark_mature);
        btnAnki = window.findViewById(R.id.btn_add_anki);
        btnClose = window.findViewById(R.id.btn_close_popup);

        btnKnown.setOnClickListener(v -> updateStatus(UserWord.STATUS_KNOWN));
        btnMature.setOnClickListener(v -> updateStatus(UserWord.STATUS_MATURE));
        btnAnki.setOnClickListener(v -> {
            Toast.makeText(context, "Anki integration coming soon!", Toast.LENGTH_SHORT).show();
        });
        btnClose.setOnClickListener(v -> hide());
    }

    public void setWord(ParsedWord word) {
        this.currentWord = word;
        wordText.setText(word.getSurface());

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
            case UserWord.STATUS_DISMISSED:
                tagText.setText("DISMISSED");
                tagText.setBackgroundColor(Color.parseColor("#95a5a6"));
                break;
            default:
                tagText.setText("UNKNOWN");
                tagText.setBackgroundColor(Color.parseColor("#3498db"));
                break;
        }

        if (word.getPitchPattern() != null && word.getReading() != null) {
            pitchGraph.setData(word.getReading(), word.getPitchPattern());
        } else {
            // Hide or clear pitch graph if no data
            pitchGraph.setData("", "");
        }

        if (word.getMeanings() != null && !word.getMeanings().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            List<String> meanings = word.getMeanings();
            for (int i = 0; i < meanings.size(); i++) {
                String m = meanings.get(i);
                if (m.startsWith("[") && m.contains("] ")) {
                    // This is a Yomitan result: [reading] ["meaning1", "meaning2"]
                    int bracketIndex = m.indexOf("] ");
                    String readingPref = m.substring(0, bracketIndex + 1);
                    String jsonMeanings = m.substring(bracketIndex + 2);
                    sb.append(readingPref).append(" ");
                    try {
                        com.google.gson.JsonElement je = com.google.gson.JsonParser.parseString(jsonMeanings);
                        if (je.isJsonArray()) {
                            com.google.gson.JsonArray ja = je.getAsJsonArray();
                            for (int j = 0; j < ja.size(); j++) {
                                sb.append(ja.get(j).getAsString());
                                if (j < ja.size() - 1)
                                    sb.append(", ");
                            }
                        } else {
                            sb.append(jsonMeanings);
                        }
                    } catch (Exception e) {
                        sb.append(jsonMeanings);
                    }
                } else {
                    sb.append(m);
                }
                if (i < meanings.size() - 1) {
                    sb.append("\n\n");
                }
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
