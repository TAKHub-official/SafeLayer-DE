package com.takhub.safelayerde.ui.pane;

class PullToRefreshGestureTracker {

    private final float thresholdPx;
    private boolean tracking;
    private boolean triggered;
    private float startY;

    PullToRefreshGestureTracker(float thresholdPx) {
        this.thresholdPx = thresholdPx;
    }

    boolean onTouchEvent(int action, float y, boolean atTop) {
        if (action == 0) {
            tracking = atTop;
            triggered = false;
            startY = y;
            return false;
        }

        if (action == 2) {
            if (triggered) {
                return false;
            }
            if (!tracking) {
                if (!atTop) {
                    return false;
                }
                tracking = true;
                startY = y;
                return false;
            }
            if (!atTop) {
                return false;
            }
            if (y - startY < thresholdPx) {
                return false;
            }
            triggered = true;
            return true;
        }

        if (action == 1 || action == 3) {
            reset();
        }
        return false;
    }

    private void reset() {
        tracking = false;
        triggered = false;
        startY = 0F;
    }
}
