package com.termux.app.terminal.inappkeyboard.voice;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * A dictation session's cleanup (spec D5): the model warms on its own {@code voice-cleanup} thread
 * at the first moment the user is not mid-speech ({@link #warmAtFirstPause}, {@link VoiceCleanupWarmUp}),
 * every phrase collects in the panel as heard meanwhile, and the text goes
 * through the {@link VoiceTextPolisher} in the parts {@link VoiceCleanupPlan} hands out: early
 * passes at phrase pauses while the user speaks ({@link #onPhrase}), and once the session has ended
 * the rest ({@link #run}), before ✓ or Copy uses it. Each part goes through the gate
 * ({@link VoiceGatedCleanup}): fillers out, and only its sentences with a sign of trouble sent to the
 * model, in the dictation's language. The panel shows only the text as heard until
 * the whole cleaned text is delivered.
 * The warm-up is queued ahead of the passes on the same thread, so a pass waits for the load
 * instead of racing it. Callbacks come back on the main thread and never after {@link #cancel}.
 */
public final class VoiceSessionCleanup {

    private static final String LOG_TAG = "VoiceSessionCleanup";

    /** Called on the main thread with the raw text and what came of it. */
    public interface Callback {
        void onCleaned(@NonNull String raw, @NonNull VoiceTextPolisher.Result result);
    }

    private final VoiceTextPolisher polisher;
    @NonNull private final String level;
    private final ExecutorService worker;
    private final Executor main;
    private volatile boolean cancelled;
    /** The speech model's language for the dictation; {@code null} reads it from the text. */
    @Nullable private volatile String languageHint;
    /** The keyboard's or the phone's language, taken only when the text itself is unclear. */
    @Nullable private volatile String languageFallback;
    /** Main-thread state: a warm-up asked for as the mic opened ({@link #warm}) is loading. */
    private boolean warming;
    /** Main-thread state: when a deferred warm-up starts. */
    private final VoiceCleanupWarmUp warmUp = new VoiceCleanupWarmUp();
    /** Set by {@link #warmAtFirstPause}: the load waits for a pause or the end, and this runs after it. */
    @Nullable private Runnable deferredOnWarmed;
    /** Main-thread state: the last phrase {@link #onPhrase} saw, for the pass a deferred load let through. */
    @Nullable private String lastRaw;
    private boolean lastPending;
    private boolean lastByHand;
    private final VoiceCleanupPlan plan;
    /** Set by {@link #run}: the session has ended on this text, and this is waiting for it. */
    @Nullable private String finishingRaw;
    @Nullable private Callback finishing;
    private long finishStartNs;
    /** Main-thread state: the session has said once that it cuts parts at phrases. */
    private boolean loggedUnpunctuated;

    /** @param level the "Cleanup level" setting, for how the parts join */
    public VoiceSessionCleanup(@NonNull VoiceTextPolisher polisher, @Nullable String level) {
        this(polisher, level, Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "voice-cleanup")),
            new Handler(Looper.getMainLooper())::post);
    }

    /** For tests: the worker and the main thread as plain executors. */
    VoiceSessionCleanup(@NonNull VoiceTextPolisher polisher, @Nullable String level, @NonNull ExecutorService worker,
                        @NonNull Executor main) {
        this.polisher = polisher;
        this.level = VoicePolishRules.normalizeLevel(level);
        this.plan = new VoiceCleanupPlan(level);
        this.worker = worker;
        this.main = main;
    }

    /**
     * The dictation carries on from {@code base}, the text already in the panel: as it is when it is
     * final (a cleanup or the user's edit), else with what {@code previous}, the cleanup it replaces,
     * had cleaned of it. Call before the first phrase.
     */
    public void carryOn(@NonNull String base, boolean baseFinal, @Nullable VoiceSessionCleanup previous) {
        plan.carryOn(base, baseFinal, previous == null ? null : previous.plan);
    }

    /**
     * Loads the model in the background now; {@code onWarmed} runs on the main thread once it is
     * done, loaded or not. Early passes start only after it, and only when the model allows them.
     * {@link #isWarming} holds until then.
     */
    public void warm(@NonNull Runnable onWarmed) {
        warmUp.markStarted();
        warming = true;
        load(onWarmed, false);
    }

    /**
     * Loads the model in the background, but not yet: at the first phrase pause an early pass would
     * start at, or at the session's end ({@link #run}), whichever comes first, and never while the
     * speech model is still loading ({@link #onSpeechReady}). A cold load of a GPU model held the
     * GPU for seconds, and the launcher's frames waited on it. {@code onWarmed} runs on the main
     * thread once the load is done, loaded or not; the pause that started it gets its early pass
     * then, when the model allows one. A model already resident loads at once at that pause, so
     * early passes start where they would have. Call once, before the first phrase.
     */
    public void warmAtFirstPause(@NonNull Runnable onWarmed) {
        deferredOnWarmed = onWarmed;
        Logger.logInfo(LOG_TAG, "cleanup warm deferred: loads at the first pause or the session's end");
    }

    /** The speech model has loaded: a pause that came while it loaded starts the deferred warm-up now. Main thread. */
    public void onSpeechReady() {
        if (cancelled) return;
        VoiceCleanupWarmUp.Trigger trigger = warmUp.onSpeechReady();
        if (trigger != null) startDeferred(trigger);
    }

    private void startDeferred(@NonNull VoiceCleanupWarmUp.Trigger trigger) {
        Runnable onWarmed = deferredOnWarmed;
        if (onWarmed == null) return;
        Logger.logInfo(LOG_TAG, "cleanup warm start: at " + trigger.why
            + " (deferred from the mic opening, where a cold load held the GPU and froze the screen)");
        load(onWarmed, true);
    }

    /**
     * Queues the load on the cleanup thread, ahead of any pass. {@code deferred}: once it is done,
     * the last phrase is offered an early pass, since the pause that started it had none.
     */
    private void load(@NonNull Runnable onWarmed, boolean deferred) {
        try {
            worker.execute(() -> {
                boolean early = false;
                if (!cancelled) {
                    try {
                        polisher.warm();
                        early = polisher.cleansWhileListening();
                    } catch (RuntimeException e) {
                        Logger.logWarn(LOG_TAG, "cleanup warm failed: " + e.getMessage());
                    }
                }
                boolean allowed = early;
                main.execute(() -> {
                    warming = false;
                    if (cancelled) return;
                    plan.allowEarlyPasses(allowed);
                    onWarmed.run();
                    String raw = lastRaw;
                    if (deferred && allowed && raw != null) onPhrase(raw, lastPending, lastByHand);
                });
            });
        } catch (RejectedExecutionException e) {
            warming = false;
        }
    }

    /**
     * The language the speech model heard, as an ISO 639 code ("es", "en-US"), or {@code null} to read
     * it from the text ({@link VoiceTextLanguage#guess}). Passes that start after this use it to name
     * the language in the prompt and to pick the fillers they strip. Any thread.
     */
    public void setLanguageHint(@Nullable String iso639) {
        languageHint = VoiceTextLanguage.normalize(iso639);
    }

    /**
     * The language to assume when there is no hint and the text does not say ({@link
     * VoiceTextLanguage#guess} is unclear): the keyboard layout's or the phone's. Without it, a
     * short part that reads as no language in particular gets its fillers stripped and nothing
     * else. Any thread.
     */
    public void setLanguageFallback(@Nullable String iso639) {
        languageFallback = VoiceTextLanguage.normalize(iso639);
    }

    /**
     * Whether a warm-up asked for as the mic opened ({@link #warm}) is still loading the model. A
     * deferred one ({@link #warmAtFirstPause}) loads behind the speech and never counts. Main thread.
     */
    public boolean isWarming() {
        return warming;
    }

    /** Why {@code raw} would be left as heard, or {@code null} when {@link #run} would clean it. */
    @Nullable
    public static String skipReason(@NonNull String raw) {
        return VoicePolishRules.skipReason(raw);
    }

    /**
     * A phrase has landed and the dictation's text is now {@code raw}: starts an early pass when one
     * is due ({@link VoiceCleanupPlan#nextEarlyPass}). Main thread; no-op once the session has ended.
     */
    public void onPhrase(@NonNull String raw, boolean transcriptionPending, boolean byHand) {
        if (cancelled || finishing != null) return;
        lastRaw = raw;
        lastPending = transcriptionPending;
        lastByHand = byHand;
        if (deferredOnWarmed != null && !warmUp.started()
                && plan.wouldStartEarlyPass(raw, transcriptionPending, byHand)) {
            boolean waiting = warmUp.isPauseWaiting();
            VoiceCleanupWarmUp.Trigger trigger = warmUp.onPause();
            if (trigger != null) {
                startDeferred(trigger);
            } else if (!waiting && warmUp.isPauseWaiting()) {
                Logger.logInfo(LOG_TAG, "cleanup warm waits: a pause came while the speech model loads");
            }
        }
        VoiceCleanupPlan.Pass pass = plan.nextEarlyPass(raw, transcriptionPending, byHand);
        if (pass == null) return;
        if (pass.unpunctuated && !loggedUnpunctuated) {
            loggedUnpunctuated = true;
            Logger.logInfo(LOG_TAG, "cleanup early: no sentence ends from the recogniser; parts cut at phrases");
        }
        submit(pass);
    }

    /**
     * The session has ended on {@code raw}: cleans what the early passes have not, after the one in
     * flight, and hands the whole text back once. A session {@link #skipReason} rules out is handed
     * back at once as a fallback. The callback does not run once {@link #cancel} is called. Main
     * thread; no work is taken after this.
     */
    public void run(@NonNull String raw, @NonNull Callback callback) {
        String skip = skipReason(raw);
        if (skip != null) {
            log("skipped", 0L, 0, raw.length(), raw.length(), "skipped:" + skip);
            worker.shutdown();
            deliver(raw, VoiceTextPolisher.Result.fallback(raw, "skipped:" + skip), callback);
            return;
        }
        if (cancelled) return;
        // No pause started the deferred load: it starts now, queued ahead of the passes.
        if (deferredOnWarmed != null) {
            VoiceCleanupWarmUp.Trigger trigger = warmUp.onEnd();
            if (trigger != null) startDeferred(trigger);
        }
        finishingRaw = raw;
        finishing = callback;
        finishStartNs = System.nanoTime();
        advance();
    }

    /** Drops every pass (and a warm-up still queued); the model is left to the runtime's idle unload. */
    public void cancel() {
        cancelled = true;
        worker.shutdown();
    }

    /** The end's next step: wait for the pass in flight, send the next, or deliver the whole text. */
    private void advance() {
        String raw = finishingRaw;
        Callback callback = finishing;
        if (raw == null || callback == null || cancelled) return;
        while (true) {
            VoiceCleanupPlan.Step step = plan.finish(raw);
            if (step.waits) return;
            VoiceCleanupPlan.Pass pass = step.pass;
            if (pass == null) break;
            String skip = VoiceCleanupPlan.skipReason(pass);
            if (skip != null) {
                plan.onPassDone(pass, VoiceTextPolisher.Result.fallback(pass.raw, "skipped:" + skip));
                continue;
            }
            if (submit(pass)) return;
        }
        finishing = null;
        finishingRaw = null;
        worker.shutdown();
        VoiceTextPolisher.Result result = plan.result(raw);
        log("session", (System.nanoTime() - finishStartNs) / 1_000_000L, plan.partCount(), raw.length(),
            result.text.length(), result.outcome);
        deliver(raw, result, callback);
    }

    /**
     * Sends {@code pass} to the cleanup thread; its answer comes back to the plan on the main thread,
     * and moves the end on when the session has ended. False when the thread took no more work, the
     * pass then kept as heard.
     */
    private boolean submit(@NonNull VoiceCleanupPlan.Pass pass) {
        try {
            worker.execute(() -> {
                if (cancelled) return;
                long start = System.nanoTime();
                VoiceTextPolisher.Result result;
                String gated = "";
                try {
                    VoiceGatedCleanup.Outcome outcome =
                        VoiceGatedCleanup.clean(polisher, pass.raw, pass.context, level, languageHint, languageFallback);
                    result = outcome.result(pass.raw, pass.early);
                    gated = " lang=" + (outcome.language == null ? "unclear" : outcome.language)
                        + " sent=" + outcome.calls + "/" + outcome.groups;
                } catch (RuntimeException e) {
                    result = VoiceTextPolisher.Result.fallback(pass.raw, "exception");
                }
                log(pass.early ? "early" : "final", (System.nanoTime() - start) / 1_000_000L,
                    VoicePolishRules.wordCount(pass.context), pass.raw.length(), result.text.length(),
                    result.outcome + gated);
                VoiceTextPolisher.Result done = result;
                main.execute(() -> {
                    if (cancelled) return;
                    plan.onPassDone(pass, done);
                    advance();
                });
            });
            return true;
        } catch (RejectedExecutionException e) {
            // cancel() got there first, or the end already delivered: nobody waits for this pass.
            plan.onPassDone(pass, VoiceTextPolisher.Result.fallback(pass.raw, "rejected"));
            return false;
        }
    }

    private void deliver(@NonNull String raw, @NonNull VoiceTextPolisher.Result result, @NonNull Callback callback) {
        main.execute(() -> {
            if (!cancelled) callback.onCleaned(raw, result);
        });
    }

    /** One line per pass and one per session: timing, sizes and outcome, never the text. */
    private static void log(@NonNull String what, long cleanupMs, int count, int inLength, int outLength,
                            @NonNull String outcome) {
        String counted = "session".equals(what) ? " passes=" + count : " contextWords=" + count;
        Logger.logInfo(LOG_TAG, "cleanup " + what + ": cleanupMs=" + cleanupMs + counted + " inLength=" + inLength
            + " outLength=" + outLength + " outcome=" + outcome);
    }
}
