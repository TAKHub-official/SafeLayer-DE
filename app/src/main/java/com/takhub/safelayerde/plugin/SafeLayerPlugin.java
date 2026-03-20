package com.takhub.safelayerde.plugin;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.takhub.safelayerde.R;
import com.takhub.safelayerde.debug.SafeLayerDebugLog;

import gov.tak.api.commons.graphics.Bitmap;
import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

public class SafeLayerPlugin implements IPlugin {

    private static final String TAG = "SafeLayerPlugin";

    private final SafeLayerDependencies dependencies;
    private SafeLayerRuntime runtime;
    private final IHostUIService uiService;
    private final ToolbarItem toolbarItem;
    private boolean runtimeStarted;
    private boolean toolbarRegistered;
    private boolean toolbarClicksEnabled;

    public SafeLayerPlugin(IServiceController serviceController) {
        SafeLayerDebugLog.i(TAG, "plugin-bootstrap serviceController=" + (serviceController != null));
        this.dependencies = new SafeLayerDependencies(serviceController);
        this.dependencies.logResolvedServices();
        this.runtime = new SafeLayerRuntime();
        this.uiService = dependencies.getUiService();
        this.toolbarItem = createToolbarItem(dependencies.getPluginContext());
    }

    @Override
    public synchronized void onStart() {
        SafeLayerDebugLog.i(TAG, "plugin-onStart uiService=" + (dependencies.getUiService() != null)
                + ", mapView=" + (dependencies.getMapViewProvider().get() != null));
        if (runtimeStarted) {
            return;
        }

        try {
            runtime.start(
                    dependencies.getPluginContext(),
                    dependencies.getUiService(),
                    dependencies.getMapViewProvider());
        } catch (RuntimeException exception) {
            runtime = new SafeLayerRuntime();
            toolbarClicksEnabled = false;
            throw exception;
        } catch (Error error) {
            runtime = new SafeLayerRuntime();
            toolbarClicksEnabled = false;
            throw error;
        }
        runtimeStarted = true;
        toolbarClicksEnabled = true;
        if (uiService != null && toolbarItem != null && !toolbarRegistered) {
            uiService.addToolbarItem(toolbarItem);
            toolbarRegistered = true;
            return;
        }
        Log.w(TAG, "Toolbar registration skipped. uiService=" + (uiService != null)
                + ", toolbarItem=" + (toolbarItem != null));
    }

    @Override
    public synchronized void onStop() {
        SafeLayerDebugLog.i(TAG, "plugin-onStop");
        toolbarClicksEnabled = false;
        if (uiService != null && toolbarItem != null && toolbarRegistered) {
            uiService.removeToolbarItem(toolbarItem);
            toolbarRegistered = false;
        }
        if (!runtimeStarted) {
            return;
        }

        runtimeStarted = false;
        runtime.stop();
    }

    private ToolbarItem createToolbarItem(Context pluginContext) {
        if (pluginContext == null) {
            return null;
        }

        return new ToolbarItem.Builder(
                "SafeLayer DE",
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        Drawable.class,
                        Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        if (toolbarClicksEnabled) {
                            runtime.onToolbarClick();
                        }
                    }
                })
                .setIdentifier(pluginContext.getPackageName())
                .build();
    }
}
