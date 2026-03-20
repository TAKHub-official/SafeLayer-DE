package com.takhub.safelayerde.domain.policy;

import com.takhub.safelayerde.domain.model.WarningSeverity;

public class SeverityColorPolicy {

    public int colorForSeverity(WarningSeverity severity) {
        switch (severity == null ? WarningSeverity.UNKNOWN : severity) {
            case EXTREME:
                return 0xFFCC0000;
            case SEVERE:
                return 0xFFFF4500;
            case MODERATE:
                return 0xFFFF8C00;
            case MINOR:
                return 0xFFFFD700;
            case UNKNOWN:
            default:
                return 0xFF808080;
        }
    }

    public int fillColorForSeverity(WarningSeverity severity) {
        return (colorForSeverity(severity) & 0x00FFFFFF) | 0x66000000;
    }
}
