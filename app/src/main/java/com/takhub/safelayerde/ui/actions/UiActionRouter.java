package com.takhub.safelayerde.ui.actions;

import android.content.Context;

import com.takhub.safelayerde.ui.pane.SafeLayerPaneController;

import gov.tak.api.ui.IHostUIService;

public class UiActionRouter {

    private final SafeLayerPaneController paneController;
    private final Context pluginContext;
    private final IHostUIService uiService;
    private final DetailFocusListener detailFocusListener;

    public UiActionRouter(
            SafeLayerPaneController paneController,
            Context pluginContext,
            IHostUIService uiService,
            DetailFocusListener detailFocusListener) {
        this.paneController = paneController;
        this.pluginContext = pluginContext;
        this.uiService = uiService;
        this.detailFocusListener = detailFocusListener;
    }

    protected UiActionRouter(
            SafeLayerPaneController paneController,
            DetailFocusListener detailFocusListener) {
        this(paneController, null, null, detailFocusListener);
    }

    public void openDetail(String stableId) {
        openDetailInternal(stableId, false);
    }

    public void openDetailAndFocus(String stableId) {
        openDetailInternal(stableId, true);
    }

    public void openList() {
        if (paneController != null) {
            paneController.showList();
        }
    }

    void showPane() {
        paneController.showPane(pluginContext, uiService);
    }

    private void openDetailInternal(String stableId, boolean focusRequested) {
        if (paneController == null) {
            return;
        }
        showPane();
        paneController.openDetail(stableId);
        if (focusRequested && detailFocusListener != null) {
            detailFocusListener.onDetailFocusRequested(stableId);
        }
    }

    public interface DetailFocusListener {
        void onDetailFocusRequested(String stableId);
    }
}
