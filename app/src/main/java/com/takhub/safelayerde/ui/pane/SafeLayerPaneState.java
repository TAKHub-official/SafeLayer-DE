package com.takhub.safelayerde.ui.pane;

import com.takhub.safelayerde.util.StringUtils;

import java.util.Objects;

public class SafeLayerPaneState {

    public enum Mode {
        LIST,
        DETAIL,
        HELP
    }

    public enum Tab {
        NINA,
        DWD,
        RADAR
    }

    private final Mode mode;
    private final Tab activeTab;
    private final String selectedStableId;

    private SafeLayerPaneState(Mode mode, Tab activeTab, String selectedStableId) {
        this.mode = mode == null ? Mode.LIST : mode;
        this.activeTab = activeTab == null ? Tab.NINA : activeTab;
        this.selectedStableId = this.mode == Mode.DETAIL
                ? StringUtils.trimToNull(selectedStableId)
                : null;
    }

    public static SafeLayerPaneState list() {
        return list(Tab.NINA);
    }

    public static SafeLayerPaneState list(Tab activeTab) {
        return new SafeLayerPaneState(Mode.LIST, activeTab, null);
    }

    public static SafeLayerPaneState detail(String stableId) {
        return detail(stableId, Tab.NINA);
    }

    public static SafeLayerPaneState detail(String stableId, Tab activeTab) {
        return new SafeLayerPaneState(Mode.DETAIL, activeTab, stableId);
    }

    public static SafeLayerPaneState help() {
        return help(Tab.NINA);
    }

    public static SafeLayerPaneState help(Tab activeTab) {
        return new SafeLayerPaneState(Mode.HELP, activeTab, null);
    }

    public Mode getMode() {
        return mode;
    }

    public Tab getActiveTab() {
        return activeTab;
    }

    public String getSelectedStableId() {
        return selectedStableId;
    }

    public SafeLayerPaneState withActiveTab(Tab tab) {
        return new SafeLayerPaneState(mode, tab, selectedStableId);
    }

    public SafeLayerPaneState asList() {
        return new SafeLayerPaneState(Mode.LIST, activeTab, null);
    }

    public SafeLayerPaneState asDetail(String stableId) {
        return new SafeLayerPaneState(Mode.DETAIL, activeTab, stableId);
    }

    public SafeLayerPaneState asHelp() {
        return new SafeLayerPaneState(Mode.HELP, activeTab, null);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SafeLayerPaneState)) {
            return false;
        }
        SafeLayerPaneState that = (SafeLayerPaneState) other;
        return mode == that.mode
                && activeTab == that.activeTab
                && Objects.equals(selectedStableId, that.selectedStableId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, activeTab, selectedStableId);
    }

    @Override
    public String toString() {
        return "SafeLayerPaneState{"
                + "mode=" + mode
                + ", activeTab=" + activeTab
                + ", selectedStableId='" + selectedStableId + '\''
                + '}';
    }
}
