package com.termux.terminal;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes and placements reach the placement layer in stream order while a display decode is on
 * the worker: a delete that arrives behind a decode lands with it, in the same posted update, and
 * no longer cancels it. With nothing in flight a delete still lands at once.
 *
 * <p>The worker is stepped by hand and the decode is stood in for, since the JVM's stub
 * {@link Bitmap} decodes nothing; each stand-in's identity is what tells the frames apart.</p>
 */
public class KittyGraphicsLayerOrderTest extends TerminalTestCase {

    private final List<Runnable> mWorker = new ArrayList<>();
    private Bitmap mNextDecode;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        withTerminalSized(20, 10);
        KittyGraphicsProtocol kitty = mTerminal.kittyGraphics();
        kitty.decoder = mWorker::add;
        kitty.decodeForTests = () -> mNextDecode;
    }

    public void testADeleteWithNothingInFlightStillLandsAtOnce() throws Exception {
        Bitmap first = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(first);
        assertSame(first, placedBitmap(1));

        assertEnteringStringGivesResponse("\033_Ga=d,d=i,i=1\033\\", "\033_Gi=1;OK\033\\");
        assertTrue("nothing was on the worker, so nothing to wait for",
            mTerminal.kittyPlacementsFor(1).isEmpty());
    }

    /** Pattern B: a same-id transmission replaces its own placement when it lands, and not before. */
    public void testASameIdTransmissionReplacesThePictureWhenItLands() throws Exception {
        Bitmap first = KittyPlacementLayerTest.standInBitmap();
        Bitmap second = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(first);

        transmit(1);
        assertSame("the old picture stays while the new one decodes", first, placedBitmap(1));
        landNext(second);
        assertSame(second, placedBitmap(1));
        assertEquals(1, mTerminal.kittyPlacementsFor(1).size());
    }

    /**
     * Pattern C, double buffering: the new picture goes up under a second id, then the old id is
     * deleted. The delete used to land at once, leaving nothing on screen until the decode finished.
     */
    public void testDoubleBufferingKeepsTheOldPictureUntilTheNewOneLands() throws Exception {
        Bitmap first = KittyPlacementLayerTest.standInBitmap();
        Bitmap second = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(first);

        transmit(2);
        assertEnteringStringGivesResponse("\033_Ga=d,d=i,i=1\033\\", "\033_Gi=1;OK\033\\");
        assertSame("the delete waits for the decode ahead of it", first, placedBitmap(1));
        assertTrue(mTerminal.kittyPlacementsFor(2).isEmpty());

        landNext(second);
        assertTrue("and lands in the same update as it", mTerminal.kittyPlacementsFor(1).isEmpty());
        assertSame(second, placedBitmap(2));
    }

    /** A delete used to move the generation on, which threw away every decode still in flight. */
    public void testADeleteOfOneImageNoLongerDropsAnotherImagesDecode() throws Exception {
        Bitmap pixels = KittyPlacementLayerTest.standInBitmap();
        transmit(2);
        enterString("\033_Ga=d,d=i,i=1,q=2\033\\");
        landNext(pixels);
        assertSame(pixels, placedBitmap(2));
    }

    /**
     * Pattern A, delete then retransmit under the same id, with the previous frame still decoding:
     * the delete waits behind that frame instead of landing first, and the end state is the one the
     * commands give in order. Freeing ({@code d=I}) must not take the new frame's reservation.
     */
    public void testADeleteBeforeASameIdFrameWaitsForTheFrameAheadOfIt() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap previous = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        transmit(1);
        enterString("\033[?2026h\033_Ga=d,d=I,i=1,q=2\033\\");
        transmit(1);
        enterString("\033[?2026l");
        assertSame("the delete did not land ahead of the frame still decoding", shown, placedBitmap(1));

        landNext(previous);
        landNext(next);
        assertSame(next, placedBitmap(1));
        assertEquals(1, mTerminal.kittyPlacementsFor(1).size());
        assertNotNull("the frame after the delete keeps its stored image",
            mTerminal.kittyGraphics().store().get(1));
    }

    /** Pattern D: a delete of everything between frames waits for the frame ahead of it. */
    public void testADeleteOfEverythingBetweenFramesWaitsForTheFrameAheadOfIt() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        Bitmap previous = KittyPlacementLayerTest.standInBitmap();
        Bitmap next = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        transmit(2);
        enterString("\033_Ga=d,d=A,q=2\033\\");
        transmit(3);
        assertSame("the delete did not land ahead of the frame still decoding", shown, placedBitmap(1));

        landNext(previous);
        landNext(next);
        assertTrue(mTerminal.kittyPlacementsFor(1).isEmpty());
        assertTrue(mTerminal.kittyPlacementsFor(2).isEmpty());
        assertSame(next, placedBitmap(3));
        KittyImageStore store = mTerminal.kittyGraphics().store();
        assertNull(store.get(1));
        assertNull(store.get(2));
        assertNotNull("transmitted after the delete, so not freed by it", store.get(3));
    }

    /**
     * Leaving the screen a queued delete was addressed to lands it first, on that screen: the decode
     * ahead of it is dropped by the switch, so waiting would leave the old picture behind.
     */
    public void testAScreenSwitchLandsAQueuedDeleteOnTheScreenItWasFor() throws Exception {
        Bitmap shown = KittyPlacementLayerTest.standInBitmap();
        transmit(1);
        landNext(shown);

        transmit(2);
        enterString("\033_Ga=d,d=i,i=1,q=2\033\\");
        enterString("\033[?1049h");
        assertTrue(mTerminal.kittyPlacementsFor(1).isEmpty());
        enterString("\033[?1049l");
        landNext(KittyPlacementLayerTest.standInBitmap());
        assertTrue("a decode from before the switch is still dropped",
            mTerminal.kittyPlacementsFor(2).isEmpty());
    }

    /** A display transmission of one 1x1 raw pixel at the cursor, leaving the cursor where it is. */
    private void transmit(long id) {
        // o=z defers the payload check to the worker, where the stand-in decode replaces it.
        enterString("\033_Ga=T,f=32,o=z,s=1,v=1,C=1,q=2,i=" + id + ";AAAA\033\\");
    }

    /** Run the oldest decode on the worker with {@code pixels} as its result, then post it back. */
    private void landNext(Bitmap pixels) {
        mNextDecode = pixels;
        mWorker.remove(0).run();
        mOutput.drainTerminalUpdates();
    }

    private Bitmap placedBitmap(long id) {
        List<KittyPlacement> placements = mTerminal.kittyPlacementsFor(id);
        assertEquals("one placement of image " + id, 1, placements.size());
        return placements.get(0).getBitmap();
    }
}
