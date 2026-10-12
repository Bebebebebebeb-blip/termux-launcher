package com.termux.app.statusbar;

import com.termux.app.statusbar.WindowActivityRing.Style;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TEMP ring-style A/B: the timing of the two cheaper rings on trial against the smooth arc. */
public class WindowActivityRingStyleTest {

    private static final long STEP = WindowActivityRing.CHASER_STEP_MS;
    private static final long PASS = WindowActivityRing.HEARTBEAT_PASS_MS;
    private static final long CYCLE = WindowActivityRing.HEARTBEAT_CYCLE_MS;

    @Test
    public void anUnknownOrMissingValueIsTheSmoothArc() {
        assertEquals(Style.SMOOTH, Style.parse(null));
        assertEquals(Style.SMOOTH, Style.parse(""));
        assertEquals(Style.SMOOTH, Style.parse("smooth"));
        assertEquals(Style.SMOOTH, Style.parse("spinner"));
        assertEquals(Style.CHASER, Style.parse("chaser"));
        assertEquals(Style.HEARTBEAT, Style.parse("heartbeat"));
        assertEquals(Style.BREATHE, Style.parse("breathe"));
    }

    @Test
    public void theBreathTicksAtFourHertzAndNeverGoesDark() {
        long step = WindowActivityRing.BREATHE_STEP_MS;
        for (long ms = 0; ms < 5000; ms += 7) {
            long delay = WindowActivityRing.nextTickDelayMs(Style.BREATHE, ms);
            assertTrue(delay > 0 && delay <= step);
            assertEquals(0, (ms + delay) % step);
            float level = WindowActivityRing.breatheLevel(ms);
            assertTrue(level >= WindowActivityRing.BREATHE_FLOOR - 1e-6f && level <= 1f + 1e-6f);
        }
        assertEquals(WindowActivityRing.BREATHE_FLOOR, WindowActivityRing.breatheLevel(0), 1e-6f);
        assertEquals(1f, WindowActivityRing.breatheLevel(WindowActivityRing.BREATHE_PERIOD_MS / 2),
            1e-6f);
    }

    @Test
    public void theSmoothArcKeepsItsTick() {
        for (long ms = 0; ms < 5000; ms += 7) {
            assertEquals(WindowActivityRing.SMOOTH_TICK_MS,
                WindowActivityRing.nextTickDelayMs(Style.SMOOTH, ms));
        }
    }

    @Test
    public void theChaserLapsInTenStepsOfAnEighthOfASecond() {
        assertEquals(10, WindowActivityRing.CHASER_DASHES);
        assertEquals(0, WindowActivityRing.chaserLitDash(0L));
        assertEquals(0, WindowActivityRing.chaserLitDash(STEP - 1));
        assertEquals(1, WindowActivityRing.chaserLitDash(STEP));
        assertEquals(9, WindowActivityRing.chaserLitDash(STEP * 10 - 1));
        assertEquals(0, WindowActivityRing.chaserLitDash(STEP * 10));
        // A lap lands close to the smooth arc's turn, so the chase reads at the same speed.
        assertEquals(1250L, STEP * WindowActivityRing.CHASER_DASHES);
    }

    @Test
    public void theChaserTicksOnTheNextStepBoundary() {
        assertEquals(STEP, WindowActivityRing.nextTickDelayMs(Style.CHASER, 0L));
        assertEquals(STEP, WindowActivityRing.nextTickDelayMs(Style.CHASER, STEP * 7));
        assertEquals(1L, WindowActivityRing.nextTickDelayMs(Style.CHASER, STEP - 1));
        assertEquals(STEP - 40, WindowActivityRing.nextTickDelayMs(Style.CHASER, STEP * 3 + 40));
        for (long ms = 0; ms < 5000; ms += 3) {
            long delay = WindowActivityRing.nextTickDelayMs(Style.CHASER, ms);
            assertTrue(delay >= 1 && delay <= STEP);
            // The tick lands exactly where the lit dash changes.
            assertEquals(0L, (ms + delay) % STEP);
            assertEquals((WindowActivityRing.chaserLitDash(ms) + 1) % 10,
                WindowActivityRing.chaserLitDash(ms + delay));
        }
    }

    @Test
    public void theChaserTrailFadesBehindTheLitDash() {
        assertEquals(1f, WindowActivityRing.chaserTrailAlpha(0), 0f);
        assertEquals(0.55f, WindowActivityRing.chaserTrailAlpha(1), 0f);
        assertEquals(0.25f, WindowActivityRing.chaserTrailAlpha(2), 0f);
        assertEquals(0f, WindowActivityRing.chaserTrailAlpha(3), 0f);
        assertEquals(0f, WindowActivityRing.chaserTrailAlpha(-1), 0f);
    }

    @Test
    public void chaserDashesShareTheOutlineWithAGapAfterEach() {
        float slot = 1f / WindowActivityRing.CHASER_DASHES;
        float dash = WindowActivityRing.chaserDashLengthFraction();
        assertEquals(slot, dash * (1f + WindowActivityRing.CHASER_GAP_OF_DASH), 1e-6f);
        assertEquals(0f, WindowActivityRing.chaserDashStartFraction(0), 0f);
        assertEquals(0.3f, WindowActivityRing.chaserDashStartFraction(3), 1e-6f);
        // The trail behind dash 0 wraps to the end of the outline rather than going negative.
        assertEquals(0.9f, WindowActivityRing.chaserDashStartFraction(-1), 1e-6f);
        assertEquals(0.8f, WindowActivityRing.chaserDashStartFraction(-2), 1e-6f);
        // The last dash ends short of the seam, so no dash has to be drawn in two pieces.
        assertTrue(WindowActivityRing.chaserDashStartFraction(9) + dash < 1f);
    }

    @Test
    public void theHeartbeatLapsOnceThenRests() {
        assertEquals(0f, WindowActivityRing.heartbeatPassPhase(0L), 0f);
        assertEquals(0.5f, WindowActivityRing.heartbeatPassPhase(PASS / 2), 1e-6f);
        assertTrue(WindowActivityRing.heartbeatPassPhase(PASS - 1) < 1f);
        assertEquals(WindowActivityRing.HEARTBEAT_RESTING,
            WindowActivityRing.heartbeatPassPhase(PASS), 0f);
        assertEquals(WindowActivityRing.HEARTBEAT_RESTING,
            WindowActivityRing.heartbeatPassPhase(CYCLE - 1), 0f);
        assertEquals(0f, WindowActivityRing.heartbeatPassPhase(CYCLE), 0f);
        assertEquals(0.25f, WindowActivityRing.heartbeatPassPhase(CYCLE * 4 + PASS / 4), 1e-6f);
    }

    @Test
    public void theHeartbeatTicksThroughTheLapAndOnceAcrossTheRest() {
        long tick = WindowActivityRing.SMOOTH_TICK_MS;
        assertEquals(tick, WindowActivityRing.nextTickDelayMs(Style.HEARTBEAT, 0L));
        // The last tick of the lap lands on the rest, so the arc is cleared on time.
        assertEquals(6L, WindowActivityRing.nextTickDelayMs(Style.HEARTBEAT, PASS - 6));
        // The rest is one wait, to the start of the next lap.
        assertEquals(CYCLE - PASS, WindowActivityRing.nextTickDelayMs(Style.HEARTBEAT, PASS));
        assertEquals(1L, WindowActivityRing.nextTickDelayMs(Style.HEARTBEAT, CYCLE - 1));
    }

    /** Walks the clock the way the bar does: a cycle of the heartbeat is a short lap of ticks. */
    @Test
    public void theHeartbeatTicksFarLessOftenThanTheSmoothArc() {
        long now = 0L;
        int ticks = 0;
        int restingTicks = 0;
        while (now < CYCLE * 10) {
            long delay = WindowActivityRing.nextTickDelayMs(Style.HEARTBEAT, now);
            assertTrue(delay >= 1);
            now += delay;
            ticks++;
            if (WindowActivityRing.heartbeatPassPhase(now) == WindowActivityRing.HEARTBEAT_RESTING) {
                restingTicks++;
            }
        }
        // One tick into each rest, none inside it.
        assertEquals(10, restingTicks);
        long smoothTicks = CYCLE * 10 / WindowActivityRing.SMOOTH_TICK_MS;
        assertTrue("heartbeat " + ticks + " vs smooth " + smoothTicks, ticks * 4 < smoothTicks);
    }

    @Test
    public void bothStylesTakeTheirPhaseFromTheClockAlone() {
        // The same instant gives the same answer however it is reached: every chip stays in step.
        long now = 987_654_321L;
        assertEquals(WindowActivityRing.chaserLitDash(now), WindowActivityRing.chaserLitDash(now));
        assertEquals(WindowActivityRing.heartbeatPassPhase(now),
            WindowActivityRing.heartbeatPassPhase(now), 0f);
        assertEquals(WindowActivityRing.chaserLitDash(now % (STEP * 10)),
            WindowActivityRing.chaserLitDash(now));
        assertEquals(WindowActivityRing.heartbeatPassPhase(now % CYCLE),
            WindowActivityRing.heartbeatPassPhase(now), 0f);
    }
}
