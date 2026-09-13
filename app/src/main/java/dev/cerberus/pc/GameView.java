package dev.cerberus.pc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** UI only presents frames. CPU emulation and JNI ownership live on one worker. */
final class GameView extends View implements Choreographer.FrameCallback {
    interface Listener { void status(String text); }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Bitmap frame = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888);
    private final int[] pixels = new int[320 * 200];
    private final Paint paint = new Paint();
    private final RectF destination = new RectF();
    private final Listener listener;
    private NativeCore core; // Worker only.
    private boolean running, closed, busy, failed; // Main thread only.
    private int inputX = 160, buttons, direction, budgets;
    private long previousTick, previousReport;

    GameView(Context context, byte[] program, Listener listener) {
        super(context);
        this.listener = listener;
        setFocusableInTouchMode(true);
        setContentDescription("Cerberus Orbit. Arraste para mover a plataforma; teclado e gamepad tambem suportados.");
        paint.setFilterBitmap(false);
        worker.execute(() -> {
            try { core = new NativeCore(program); }
            catch (RuntimeException | LinkageError e) { post(() -> fail("Falha ao iniciar: " + e.getMessage())); }
        });
    }
    void resume() {
        if (closed || running || failed) return;
        running = true;
        setKeepScreenOn(true);
        previousTick = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }
    void pause() {
        running = false;
        setKeepScreenOn(false);
        buttons = direction = 0;
        Choreographer.getInstance().removeFrameCallback(this);
    }
    void close() {
        if (closed) return;
        pause(); closed = true;
        worker.execute(() -> { if (core != null) { core.close(); core = null; } });
        worker.shutdown(); // Queued bounded slice finishes before destroying its machine.
    }
    private void fail(String text) {
        if (closed) return;
        failed = true; pause(); listener.status(text);
    }
    @Override public void doFrame(long now) {
        if (!running || closed) return;
        Choreographer.getInstance().postFrameCallback(this);
        // Cap demo simulation to about 60 Hz on 90/120 Hz panels. No catch-up bursts.
        if (busy || (previousTick != 0 && now - previousTick < 16_000_000L)) return;
        previousTick = now;
        inputX = Math.max(0, Math.min(319, inputX + direction * 5));
        final int x = inputX, keys = buttons;
        final boolean report = now - previousReport > 1_000_000_000L;
        if (report) previousReport = now;
        busy = true;
        worker.execute(() -> {
            if (core == null) { post(() -> busy = false); return; }
            try {
                int state = core.tick(x, keys, pixels);
                String text = report || state >= 3 ? core.status() : null;
                post(() -> {
                    if (closed) return;
                    busy = false;
                    if (state == 1) {
                        budgets = 0;
                        frame.setPixels(pixels, 0, 320, 0, 0, 320, 200);
                        invalidate();
                    } else if (state == 2 && ++budgets >= 120) {
                        fail("Programa suspenso: 1,2 milhao de instrucoes sem apresentar frame.");
                        return;
                    } else if (state >= 3) { fail(text); return; }
                    if (text != null) listener.status(text);
                });
            } catch (RuntimeException | LinkageError e) {
                post(() -> { busy = false; fail("Falha do core: " + e.getMessage()); });
            }
        });
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0xff080c17);
        float scale = Math.min(getWidth() / 320f, getHeight() / 200f);
        float w = 320 * scale, h = 200 * scale;
        destination.set((getWidth() - w) / 2, (getHeight() - h) / 2,
                        (getWidth() + w) / 2, (getHeight() + h) / 2);
        canvas.drawBitmap(frame, null, destination, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (destination.width() <= 0) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            requestFocus(); getParent().requestDisallowInterceptTouchEvent(true);
        }
        inputX = Math.max(0, Math.min(319, Math.round((event.getX() - destination.left) * 320 / destination.width())));
        boolean up = event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL;
        buttons = up ? 0 : 1;
        if (up) { getParent().requestDisallowInterceptTouchEvent(false); performClick(); }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_A) { direction = -1; return true; }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_D) { direction = 1; return true; }
        return super.onKeyDown(keyCode, event);
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_A ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_D) { direction = 0; return true; }
        return super.onKeyUp(keyCode, event);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK) {
            float axis = event.getAxisValue(MotionEvent.AXIS_X);
            if (Math.abs(axis) < 0.2f) axis = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            direction = axis < -0.2f ? -1 : axis > 0.2f ? 1 : 0;
            return true;
        }
        return super.onGenericMotionEvent(event);
    }
}
