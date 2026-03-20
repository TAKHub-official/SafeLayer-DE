package com.takhub.safelayerde.platform;

import android.os.Handler;
import android.os.Looper;

public class UiThreadRunner {

    private final Handler handler;

    public UiThreadRunner() {
        this(new Handler(Looper.getMainLooper()));
    }

    UiThreadRunner(Handler handler) {
        this.handler = handler;
    }

    public void run(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (isMainThread()) {
            runnable.run();
            return;
        }
        handler.post(runnable);
    }

    public void post(Runnable runnable) {
        if (runnable != null) {
            handler.post(runnable);
        }
    }

    public void postDelayed(Runnable runnable, long delayMs) {
        if (runnable != null) {
            handler.postDelayed(runnable, delayMs);
        }
    }

    public void removeCallbacks(Runnable runnable) {
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    boolean isMainThread() {
        return Looper.myLooper() == handler.getLooper();
    }
}
