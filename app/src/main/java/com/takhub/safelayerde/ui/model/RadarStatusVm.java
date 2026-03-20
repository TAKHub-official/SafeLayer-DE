package com.takhub.safelayerde.ui.model;

public class RadarStatusVm {

    private final String statusLabel;
    private final String dataAgeLabel;
    private final String productLabel;
    private final boolean inactive;
    private final String errorMessage;

    public RadarStatusVm(
            String statusLabel,
            String dataAgeLabel,
            String productLabel,
            boolean inactive,
            String errorMessage) {
        this.statusLabel = statusLabel;
        this.dataAgeLabel = dataAgeLabel;
        this.productLabel = productLabel;
        this.inactive = inactive;
        this.errorMessage = errorMessage;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getDataAgeLabel() {
        return dataAgeLabel;
    }

    public String getProductLabel() {
        return productLabel;
    }

    public boolean isInactive() {
        return inactive;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
