package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * When a dictation's cleanup model starts loading. Not as the microphone opens: a cold GPU load
 * of the cleanup model held the GPU for seconds, and the launcher's frames, waveform included,
 * waited on it (pong, E4B, 2026-10-14). The load starts at the first moment the user is not
 * mid-speech: the first phrase pause an early pass would start at ({@link Trigger#PAUSE}), or
 * the session's end ({@link Trigger#END}), whichever comes first, and never while the speech
 * model is still loading. A pause that comes before the speech model is ready waits for it; the
 * end does not, since the microphone has closed and nothing more is heard. Starts once. Pure and
 * main-thread only; {@link VoiceSessionCleanup} runs the load it answers.
 */
final class VoiceCleanupWarmUp {

    /** What started the load, for the log. */
    enum Trigger {
        /** A phrase pause an early pass would start at. */
        PAUSE("the first pause"),
        /** The session ended before any such pause. */
        END("the session's end"),
        /** A pause came while the speech model loaded, and that load is now done. */
        SPEECH_READY("the speech model being ready after a pause");

        @NonNull final String why;

        Trigger(@NonNull String why) {
            this.why = why;
        }
    }

    private boolean speechReady;
    private boolean started;
    /** A pause came while the speech model loaded. */
    private boolean pauseWaiting;

    /** Whether the load has started (or the caller loaded at once). */
    boolean started() {
        return started;
    }

    /** The load was started directly, not through this: nothing more starts it. */
    void markStarted() {
        started = true;
    }

    /** The speech model has loaded. The trigger to start the load now, or {@code null}. */
    @Nullable
    Trigger onSpeechReady() {
        speechReady = true;
        if (started || !pauseWaiting) return null;
        started = true;
        return Trigger.SPEECH_READY;
    }

    /** A pause an early pass would start at. The trigger to start the load now, or {@code null}. */
    @Nullable
    Trigger onPause() {
        if (started) return null;
        if (!speechReady) {
            pauseWaiting = true;
            return null;
        }
        started = true;
        return Trigger.PAUSE;
    }

    /** The session has ended with text to clean. The trigger to start the load now, or {@code null}. */
    @Nullable
    Trigger onEnd() {
        if (started) return null;
        started = true;
        return Trigger.END;
    }

    /** Whether a pause is waiting on the speech model, for the log. */
    boolean isPauseWaiting() {
        return pauseWaiting && !started;
    }
}
