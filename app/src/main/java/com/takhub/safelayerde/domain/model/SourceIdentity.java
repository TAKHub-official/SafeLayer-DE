package com.takhub.safelayerde.domain.model;

public enum SourceIdentity {
    BBK,
    DWD,
    RADAR;

    public static SourceIdentity fromWarningSourceType(WarningSourceType sourceType) {
        if (sourceType == WarningSourceType.BBK) {
            return BBK;
        }
        if (sourceType == WarningSourceType.DWD) {
            return DWD;
        }
        return null;
    }

    public WarningSourceType toWarningSourceType() {
        if (this == BBK) {
            return WarningSourceType.BBK;
        }
        if (this == DWD) {
            return WarningSourceType.DWD;
        }
        return null;
    }
}
