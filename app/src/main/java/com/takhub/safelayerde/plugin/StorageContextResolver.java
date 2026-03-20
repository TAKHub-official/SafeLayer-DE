package com.takhub.safelayerde.plugin;

import android.content.Context;
import android.content.pm.PackageManager;

import com.takhub.safelayerde.BuildConfig;

import java.util.ArrayList;
import java.util.List;

final class StorageContextResolver {

    private StorageContextResolver() {
    }

    static Context resolve(Context pluginContext, Context mapContext) {
        List<Context> candidates = collectCandidates(pluginContext, mapContext);

        for (Context candidate : candidates) {
            Context hostContext = resolveHostContext(candidate);
            if (hostContext != null) {
                return hostContext;
            }
        }

        for (Context candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Context> collectCandidates(Context pluginContext, Context mapContext) {
        List<Context> candidates = new ArrayList<>();
        addCandidate(candidates, mapContext == null ? null : mapContext.getApplicationContext());
        addCandidate(candidates, mapContext);
        addCandidate(candidates, pluginContext == null ? null : pluginContext.getApplicationContext());
        addCandidate(candidates, pluginContext);
        return candidates;
    }

    private static void addCandidate(List<Context> candidates, Context candidate) {
        if (candidate == null || candidates.contains(candidate)) {
            return;
        }
        candidates.add(candidate);
    }

    private static Context resolveHostContext(Context candidate) {
        if (candidate == null) {
            return null;
        }

        if (BuildConfig.ATAK_PACKAGE_NAME.equals(candidate.getPackageName())) {
            return candidate;
        }

        try {
            Context hostContext = candidate.createPackageContext(BuildConfig.ATAK_PACKAGE_NAME, 0);
            if (hostContext != null && BuildConfig.ATAK_PACKAGE_NAME.equals(hostContext.getPackageName())) {
                return hostContext;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        } catch (RuntimeException ignored) {
        }
        return null;
    }
}
