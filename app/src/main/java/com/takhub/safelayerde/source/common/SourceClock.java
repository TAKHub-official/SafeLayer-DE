package com.takhub.safelayerde.source.common;

import com.takhub.safelayerde.util.TimeUtils;

public class SourceClock {

    public long nowMs() {
        return TimeUtils.nowEpochMs();
    }
}
