package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the cleanup model starts loading: never as the mic opens, nor while the speech model loads. */
public class VoiceCleanupWarmUpTest {

    @Test
    public void nothingStartsAsTheMicOpensOrWhenTheSpeechModelIsReady() {
        VoiceCleanupWarmUp warmUp = new VoiceCleanupWarmUp();
        assertFalse(warmUp.started());
        assertNull(warmUp.onSpeechReady());
        assertFalse(warmUp.started());
    }

    @Test
    public void theFirstPauseStartsItOnce() {
        VoiceCleanupWarmUp warmUp = new VoiceCleanupWarmUp();
        warmUp.onSpeechReady();
        assertEquals(VoiceCleanupWarmUp.Trigger.PAUSE, warmUp.onPause());
        assertTrue(warmUp.started());
        assertNull(warmUp.onPause());
        assertNull(warmUp.onEnd());
    }

    @Test
    public void aPauseWhileTheSpeechModelLoadsWaitsForIt() {
        VoiceCleanupWarmUp warmUp = new VoiceCleanupWarmUp();
        assertNull(warmUp.onPause());
        assertTrue(warmUp.isPauseWaiting());
        assertFalse(warmUp.started());
        assertEquals(VoiceCleanupWarmUp.Trigger.SPEECH_READY, warmUp.onSpeechReady());
        assertFalse(warmUp.isPauseWaiting());
        assertNull(warmUp.onEnd());
    }

    @Test
    public void theEndStartsItWhenNoPauseDid() {
        VoiceCleanupWarmUp warmUp = new VoiceCleanupWarmUp();
        warmUp.onSpeechReady();
        assertEquals(VoiceCleanupWarmUp.Trigger.END, warmUp.onEnd());
        assertNull(warmUp.onPause());
    }

    @Test
    public void theEndDoesNotWaitForTheSpeechModel() {
        // The mic has closed: nothing more is heard, and the speech model's ready call never comes.
        VoiceCleanupWarmUp warmUp = new VoiceCleanupWarmUp();
        assertNull(warmUp.onPause());
        assertEquals(VoiceCleanupWarmUp.Trigger.END, warmUp.onEnd());
        assertNull(warmUp.onSpeechReady());
    }

    @Test
    public void aLoadStartedDirectlyIsNotStartedAgain() {
        VoiceCleanupWarmUp warmUp = new VoiceCleanupWarmUp();
        warmUp.markStarted();
        warmUp.onSpeechReady();
        assertNull(warmUp.onPause());
        assertNull(warmUp.onEnd());
    }
}
