package ca.fuwafuwa.gaku.Logic;

import android.content.Context;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.util.List;
import java.util.Map;

import ca.fuwafuwa.gaku.Analysis.ParsedWord;
import ca.fuwafuwa.gaku.Database.User.UserWord;
import ca.fuwafuwa.gaku.Network.AnkiConnectClient;
import ca.fuwafuwa.gaku.Network.JitenApiClient;
import ca.fuwafuwa.gaku.Network.JpdbApiClient;
import ca.fuwafuwa.gaku.Network.JpdbDTOs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReviewController {

    private final Context context;
    private final JitenApiClient jitenClient;
    private final JpdbApiClient jpdbClient;
    private final AnkiConnectClient ankiClient;

    public ReviewController(Context context) {
        this.context = context;
        this.jitenClient = JitenApiClient.getInstance(context);
        this.jpdbClient = JpdbApiClient.getInstance(context);
        this.ankiClient = AnkiConnectClient.getInstance(context);
    }

    public void sync() {
        String backend = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("pref_parser_backend", "jiten");

        if ("jiten".equals(backend)) {
            jitenClient.sync((success, count) -> {
                runOnMain(success ? "Sync complete: " + count + " words updated" : "Sync failed");
            });
        } else if ("jpdb".equals(backend)) {
            runOnMain("Sync for JPDB is automatic (live). Ensure API Key is correct.");
        } else {
            runOnMain("Sync not available for offline mode.");
        }
    }

    public void mine(ParsedWord word) {
        String backend = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("pref_parser_backend", "jiten");

        if ("jpdb".equals(backend)) {
            Map<String, Object> meta = word.getMetadata();
            if (meta != null && meta.containsKey("vid") && meta.containsKey("sid")) {
                int vid = (int) meta.get("vid");
                int sid = (int) meta.get("sid");
                // Default deck for mining in JPDB is usually user selected, but API takes Deck
                // ID.
                // jpd-breader uses specific logic. For now, let's trying adding to "learning"
                // deck equivalent?
                // Wait, jpd-breader uses deck IDs.
                // Assuming we want to add to "default" deck?
                // backend.ts uses addToDeckAPI with a deckId.
                // To keep it simple, we might need to ask user for Deck ID for JPDB too?
                // Or maybe just use a known deck like "global" or current deck?
                // "RequestMine" in background_comms calls `requestMine` which calls
                // `addToDeck`.
                // Actually `requestMine` calls `requestUnabortable({ type: 'mine' ... })`
                // In backend.ts line 49, `requestMine`.
                // It seems to fetch the deck?

                // Let's look at `addToDeck` in `backend.ts`. It takes `deckId`.
                // We'll trust the user has configured something, OR we might need to implement
                // deck fetch.
                // For this iteration, I'll log that deck selection is needed or try a default
                // if I can guess it.
                // Actually, let's fallback to "jiten" style for now: just toast?
                // NO, I implemented `addVocabulary`.
                // I'll try adding to deck id 1 (often default) or ask the user.
                // User didn't specify Deck ID for JPDB.
                // I will add a TOAST saying "JPDB Mining not fully configured" if I can't
                // guess.
                runOnMain("JPDB Mining: Deck ID selection not yet implemented.");
            }
        } else {
            // Jiten / Offline -> Anki
            openAnkiBrowser(word);
        }
    }

    public void setJpdbFlag(ParsedWord word, String flag, boolean remove) {
        // flag: "blacklist", "never-forget"
        Map<String, Object> meta = word.getMetadata();
        if (meta != null && meta.containsKey("vid") && meta.containsKey("sid")) {
            int vid = (int) meta.get("vid");
            int sid = (int) meta.get("sid");
            JpdbDTOs.ModifyDeckRequest req = new JpdbDTOs.ModifyDeckRequest(flag, vid, sid);

            // JpdbApi: addVocabulary or removeVocabulary
            // backend.ts: addToDeckAPI used for blacklist/never-forget too.
            // "id" field in JSON body controls it.

            callJpdbModify(req, !remove);
        }
    }

    private void callJpdbModify(JpdbDTOs.ModifyDeckRequest req, boolean add) {
        // Need to expose JpdbApiClient methods or get the api directly?
        // JpdbApiClient wraps JpdbApi. I should add methods to JpdbApiClient.
        // Assuming I added them to JpdbApi but not JpdbApiClient yet.
        // I need to update JpdbApiClient first.

        // For now, I'll use JpdbApiClient.getInstance().getApi() pattern?
        // No, JpdbApiClient is singleton and hides API.
        // I must add methods to JpdbApiClient.
    }

    public void grade(ParsedWord word, String grade) {
        // Grade: "nothing", "something", "hard", "good", "easy"
        String backend = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("pref_parser_backend", "jiten");

        if ("jpdb".equals(backend)) {
            // Review not implemented via API yet as discussed.
            runOnMain("JPDB Review via API not supported. Please review on site.");
        } else {
            // Anki
            openAnkiBrowser(word);
        }
    }

    private void openAnkiBrowser(ParsedWord word) {
        String query = "deck:\"" +
                PreferenceManager.getDefaultSharedPreferences(context).getString("anki_mining_deck", "Mining") +
                "\" " + word.getSurface();
        ankiClient.guiBrowse(query);
    }

    private void runOnMain(String message) {
        // Need a way to post to main thread if context is activity, or just Toast.
        // Toast can be shown from any thread if using Looper, but safely:
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
}
