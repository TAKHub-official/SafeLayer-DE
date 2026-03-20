package com.takhub.safelayerde.domain.service;

import android.util.Log;

import com.takhub.safelayerde.debug.SafeLayerDebugLog;
import com.takhub.safelayerde.domain.model.SourceIdentity;
import com.takhub.safelayerde.domain.model.SourceState;
import com.takhub.safelayerde.plugin.SafeLayerConstants;
import com.takhub.safelayerde.source.common.SourceAdapter;
import com.takhub.safelayerde.source.common.SourceRefreshResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RefreshCoordinator {

    private static final String TAG = "SafeLayerRefresh";
    private final Object lock = new Object();
    private final ScheduledExecutorService executorService;
    private final boolean periodicSchedulingEnabled;
    private final List<RegisteredSource> sources = new ArrayList<>();
    private boolean started;
    private boolean refreshRunning;
    private boolean refreshWorkerActive;
    private RefreshRequest pendingRefreshRequest;
    private ScheduledFuture<?> scheduledPeriodicRefresh;
    private volatile RefreshListener listener;

    public RefreshCoordinator() {
        this(Executors.newSingleThreadScheduledExecutor(), false);
    }

    RefreshCoordinator(ScheduledExecutorService executorService, boolean periodicSchedulingEnabled) {
        this.executorService = executorService;
        this.periodicSchedulingEnabled = periodicSchedulingEnabled;
    }

    public void start() {
        synchronized (lock) {
            if (started) {
                return;
            }
            started = true;
            if (periodicSchedulingEnabled) {
                scheduleNextPeriodicRefreshLocked(0L);
            }
        }
    }

    public boolean requestImmediateRefresh() {
        return requestRefresh(RefreshRequest.all(RefreshTrigger.MANUAL));
    }

    public boolean requestRefreshAll() {
        return requestRefresh(RefreshRequest.all(RefreshTrigger.ON_DEMAND));
    }

    public boolean requestSourceRefresh(SourceIdentity sourceIdentity) {
        if (sourceIdentity == null) {
            return false;
        }
        return requestRefresh(RefreshRequest.single(RefreshTrigger.ON_DEMAND, sourceIdentity));
    }

    public boolean requestDueRefreshNow() {
        synchronized (lock) {
            if (!started || executorService.isShutdown()) {
                return false;
            }
            cancelPeriodicRefreshLocked();
        }
        return requestRefresh(RefreshRequest.all(RefreshTrigger.PERIODIC));
    }

    public void registerSource(SourceAdapter source) {
        registerSource(source, SafeLayerConstants.WARNING_REFRESH_INTERVAL_MS);
    }

    public void registerSource(SourceIdentity sourceIdentity, SourceAdapter source) {
        registerSource(sourceIdentity, source, SafeLayerConstants.WARNING_REFRESH_INTERVAL_MS);
    }

    public void registerSource(SourceAdapter source, long minimumIntervalMs) {
        registerSource(null, source, minimumIntervalMs);
    }

    public void registerSource(SourceIdentity sourceIdentity, SourceAdapter source, long minimumIntervalMs) {
        if (source == null) {
            return;
        }

        RegisteredSource registeredSource = new RegisteredSource(sourceIdentity, source, minimumIntervalMs);
        synchronized (lock) {
            if (isDuplicateRegistrationLocked(registeredSource)) {
                SafeLayerDebugLog.w(TAG, "source-register-ignored identity=" + sourceIdentity
                        + ", type=" + source.getClass().getSimpleName());
                return;
            }
            sources.add(registeredSource);
            if (started
                    && periodicSchedulingEnabled
                    && registeredSource.lastRunEpochMs <= 0L
                    && !executorService.isShutdown()) {
                scheduleNextPeriodicRefreshLocked(0L);
            }
        }
        SafeLayerDebugLog.i(TAG, "source-registered type=" + source.getClass().getSimpleName()
                + ", identity=" + sourceIdentity
                + ", minIntervalMs=" + Math.max(1L, minimumIntervalMs));
    }

    public void setListener(RefreshListener listener) {
        this.listener = listener;
    }

    public void stop() {
        synchronized (lock) {
            cancelPeriodicRefreshLocked();
            refreshRunning = false;
            refreshWorkerActive = false;
            pendingRefreshRequest = null;
            started = false;
        }
        executorService.shutdownNow();
        SafeLayerDebugLog.i(TAG, "refresh-coordinator-stop");
    }

    boolean requestRefreshForTest(RefreshTrigger trigger) {
        return requestRefresh(RefreshRequest.all(trigger));
    }

    private boolean requestRefresh(final RefreshRequest request) {
        synchronized (lock) {
            if (!started || executorService.isShutdown()) {
                return false;
            }
            if (request != null && request.trigger == RefreshTrigger.MANUAL) {
                cancelPeriodicRefreshLocked();
            }
            pendingRefreshRequest = RefreshRequest.merge(pendingRefreshRequest, request);
            if (refreshWorkerActive) {
                return true;
            }
            return submitRefreshWorkerLocked();
        }
    }

    private void runScheduledPeriodicRefresh() {
        synchronized (lock) {
            scheduledPeriodicRefresh = null;
            if (!started || executorService.isShutdown()) {
                return;
            }
        }
        if (!requestRefresh(RefreshRequest.all(RefreshTrigger.PERIODIC))) {
            synchronized (lock) {
                if (started && periodicSchedulingEnabled && !executorService.isShutdown()) {
                    scheduleNextPeriodicRefreshLocked(nextTickDelayMsLocked());
                }
            }
        }
    }

    private boolean submitRefreshWorkerLocked() {
        refreshWorkerActive = true;
        try {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    executePendingRefreshes();
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            refreshWorkerActive = false;
            pendingRefreshRequest = null;
            if (started && periodicSchedulingEnabled && !executorService.isShutdown()) {
                scheduleNextPeriodicRefreshLocked(nextTickDelayMsLocked());
            }
            SafeLayerDebugLog.w(TAG, "refresh-submit-rejected");
            return false;
        }
    }

    private void executePendingRefreshes() {
        while (true) {
            RefreshRequest request;
            synchronized (lock) {
                if (!started || executorService.isShutdown()) {
                    pendingRefreshRequest = null;
                    refreshRunning = false;
                    refreshWorkerActive = false;
                    return;
                }
                request = pendingRefreshRequest;
                pendingRefreshRequest = null;
                if (request == null) {
                    refreshRunning = false;
                    refreshWorkerActive = false;
                    if (started && periodicSchedulingEnabled && !executorService.isShutdown()) {
                        scheduleNextPeriodicRefreshLocked(nextTickDelayMsLocked());
                    }
                    return;
                }
                refreshRunning = true;
            }

            executeRefreshCycle(request);

            boolean manualRefreshChainActive;
            boolean shouldReturn;
            synchronized (lock) {
                refreshRunning = false;
                manualRefreshChainActive = hasManualRefreshChainLocked();
                if (started && periodicSchedulingEnabled && !executorService.isShutdown()) {
                    scheduleNextPeriodicRefreshLocked(nextTickDelayMsLocked());
                }
                shouldReturn = pendingRefreshRequest == null;
                if (shouldReturn) {
                    refreshWorkerActive = false;
                }
            }
            notifyRefreshCycleFinished(
                    request == null ? RefreshTrigger.ON_DEMAND : request.trigger,
                    manualRefreshChainActive);
            if (shouldReturn) {
                return;
            }
        }
    }

    private void executeRefreshCycle(RefreshRequest request) {
        RefreshTrigger trigger = request == null ? RefreshTrigger.ON_DEMAND : request.trigger;
        try {
            List<RegisteredSource> dueSources = resolveDueSources(request);
            SafeLayerDebugLog.i(TAG, "refresh-cycle-start trigger=" + trigger + ", dueSources=" + dueSources.size());
            long runStartedAtMs = currentTimeMs();
            for (RegisteredSource registeredSource : dueSources) {
                if (registeredSource == null || registeredSource.source == null) {
                    continue;
                }

                try {
                    SourceRefreshResult result = normalizeResult(
                            registeredSource,
                            registeredSource.source.refresh());
                    logRefreshResult(result);
                    notifyRefreshResult(result);
                } catch (RuntimeException exception) {
                    logError("Unhandled source refresh failure.", exception);
                    SafeLayerDebugLog.e(TAG, "refresh-cycle-runtime-failure source="
                            + registeredSource.identity, exception);
                } finally {
                    registeredSource.lastRunEpochMs = runStartedAtMs;
                }
            }
        } catch (RuntimeException exception) {
            logError("Unhandled refresh cycle failure.", exception);
            SafeLayerDebugLog.e(TAG, "refresh-cycle-unexpected-failure", exception);
        }
    }

    private List<RegisteredSource> resolveDueSources(RefreshRequest request) {
        RefreshTrigger trigger = request == null ? RefreshTrigger.ON_DEMAND : request.trigger;
        List<RegisteredSource> dueSources = new ArrayList<>();
        List<RegisteredSource> registeredSources;
        synchronized (lock) {
            registeredSources = new ArrayList<>(sources);
        }
        long now = currentTimeMs();
        for (RegisteredSource registeredSource : registeredSources) {
            if (registeredSource == null || registeredSource.source == null) {
                continue;
            }
            if (request != null && !request.targetsAll() && !request.matches(registeredSource.identity)) {
                continue;
            }
            if (trigger == RefreshTrigger.MANUAL
                    || trigger == RefreshTrigger.ON_DEMAND
                    || registeredSource.lastRunEpochMs <= 0L
                    || now - registeredSource.lastRunEpochMs >= registeredSource.minimumIntervalMs) {
                dueSources.add(registeredSource);
            }
        }
        return dueSources;
    }

    private void notifyRefreshResult(SourceRefreshResult result) {
        RefreshListener currentListener = listener;
        if (currentListener == null || result == null) {
            return;
        }
        try {
            currentListener.onRefreshResult(result);
        } catch (RuntimeException exception) {
            logError("Refresh listener failed while handling result.", exception);
            SafeLayerDebugLog.e(TAG, "refresh-listener-result-failure", exception);
        }
    }

    private void notifyRefreshCycleFinished(RefreshTrigger trigger, boolean manualRefreshChainActive) {
        RefreshListener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        try {
            currentListener.onRefreshCycleFinished(trigger, manualRefreshChainActive);
        } catch (RuntimeException exception) {
            logError("Refresh listener failed while handling cycle end.", exception);
            SafeLayerDebugLog.e(TAG, "refresh-listener-cycle-failure", exception);
        }
    }

    private boolean hasManualRefreshChainLocked() {
        return pendingRefreshRequest != null && pendingRefreshRequest.trigger == RefreshTrigger.MANUAL;
    }

    private SourceRefreshResult normalizeResult(RegisteredSource registeredSource, SourceRefreshResult result) {
        if (result == null) {
            return buildSourceFailure(registeredSource, "Source returned null refresh result.");
        }
        if (result.getSourceState() == null) {
            return buildSourceFailure(registeredSource, "Source returned refresh result without source state.");
        }
        if (result.getSourceState().getSourceIdentity() == null && registeredSource != null) {
            result.getSourceState().setSourceIdentity(registeredSource.identity);
        }
        return result;
    }

    private SourceRefreshResult buildSourceFailure(RegisteredSource registeredSource, String message) {
        SourceIdentity sourceIdentity = registeredSource == null ? null : registeredSource.identity;
        SafeLayerDebugLog.e(TAG, "refresh-cycle-invalid-result source=" + sourceIdentity
                + ", reason=" + message, null);
        SourceState sourceState = SourceState.forSource(sourceIdentity);
        sourceState.setStatus(SourceState.Status.ERROR_NO_CACHE);
        sourceState.setLastErrorMessage(message);
        return SourceRefreshResult.failure(sourceState, message);
    }

    private void logRefreshResult(SourceRefreshResult result) {
        if (result == null) {
            return;
        }
        SourceState sourceState = result.getSourceState();
        SafeLayerDebugLog.i(TAG, "refresh-cycle-result source="
                + (sourceState == null ? null : sourceState.getSourceIdentity())
                + ", success=" + result.isSuccess()
                + ", status=" + (sourceState == null ? null : sourceState.getStatus())
                + ", error=" + result.getErrorMessage());
    }

    private void scheduleNextPeriodicRefreshLocked(long delayMs) {
        cancelPeriodicRefreshLocked();
        scheduledPeriodicRefresh = executorService.schedule(new Runnable() {
            @Override
            public void run() {
                runScheduledPeriodicRefresh();
            }
        }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
    }

    private void cancelPeriodicRefreshLocked() {
        if (scheduledPeriodicRefresh == null) {
            return;
        }
        scheduledPeriodicRefresh.cancel(false);
        scheduledPeriodicRefresh = null;
    }

    public enum RefreshTrigger {
        ON_DEMAND,
        PERIODIC,
        MANUAL
    }

    public interface RefreshListener {
        void onRefreshResult(SourceRefreshResult result);
        void onRefreshCycleFinished(RefreshTrigger trigger, boolean manualRefreshChainActive);
    }

    private long nextTickDelayMsLocked() {
        long now = currentTimeMs();
        long nextDelayMs = Long.MAX_VALUE;
        for (RegisteredSource registeredSource : sources) {
            if (registeredSource == null || registeredSource.source == null) {
                continue;
            }
            if (registeredSource.lastRunEpochMs <= 0L) {
                return 0L;
            }

            long dueAtEpochMs = registeredSource.lastRunEpochMs + registeredSource.minimumIntervalMs;
            nextDelayMs = Math.min(nextDelayMs, dueAtEpochMs - now);
        }
        if (nextDelayMs == Long.MAX_VALUE) {
            return SafeLayerConstants.WARNING_REFRESH_INTERVAL_MS;
        }
        return Math.max(0L, nextDelayMs);
    }

    protected long currentTimeMs() {
        return System.currentTimeMillis();
    }

    private boolean isDuplicateRegistrationLocked(RegisteredSource registeredSource) {
        if (registeredSource == null) {
            return false;
        }
        for (RegisteredSource existingSource : sources) {
            if (existingSource == null) {
                continue;
            }
            if (registeredSource.identity != null && registeredSource.identity.equals(existingSource.identity)) {
                return true;
            }
            if (registeredSource.source == existingSource.source) {
                return true;
            }
        }
        return false;
    }

    private void logError(String message, RuntimeException exception) {
        try {
            Log.e(TAG, message, exception);
        } catch (RuntimeException ignored) {
            // Host-side unit tests may not provide a full Android logging runtime.
        }
    }

    private static final class RegisteredSource {

        private final SourceIdentity identity;
        private final SourceAdapter source;
        private final long minimumIntervalMs;
        private long lastRunEpochMs;

        private RegisteredSource(SourceIdentity identity, SourceAdapter source, long minimumIntervalMs) {
            this.identity = identity;
            this.source = source;
            this.minimumIntervalMs = Math.max(1L, minimumIntervalMs);
        }
    }

    private static final class RefreshRequest {

        private final RefreshTrigger trigger;
        private final List<SourceIdentity> sourceIdentities;

        private RefreshRequest(RefreshTrigger trigger, List<SourceIdentity> sourceIdentities) {
            this.trigger = trigger == null ? RefreshTrigger.ON_DEMAND : trigger;
            this.sourceIdentities = sourceIdentities == null
                    ? Collections.<SourceIdentity>emptyList()
                    : sourceIdentities;
        }

        private static RefreshRequest all(RefreshTrigger trigger) {
            return new RefreshRequest(trigger, null);
        }

        private static RefreshRequest single(RefreshTrigger trigger, SourceIdentity sourceIdentity) {
            return new RefreshRequest(trigger, Collections.singletonList(sourceIdentity));
        }

        private static RefreshRequest merge(RefreshRequest left, RefreshRequest right) {
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }

            RefreshTrigger mergedTrigger = priority(left.trigger) >= priority(right.trigger)
                    ? left.trigger
                    : right.trigger;
            if (left.targetsAll() || right.targetsAll()) {
                return RefreshRequest.all(mergedTrigger);
            }

            List<SourceIdentity> mergedIdentities = new ArrayList<>(left.sourceIdentities);
            for (SourceIdentity sourceIdentity : right.sourceIdentities) {
                if (!mergedIdentities.contains(sourceIdentity)) {
                    mergedIdentities.add(sourceIdentity);
                }
            }
            return new RefreshRequest(mergedTrigger, mergedIdentities);
        }

        private static int priority(RefreshTrigger trigger) {
            if (trigger == RefreshTrigger.MANUAL) {
                return 3;
            }
            if (trigger == RefreshTrigger.ON_DEMAND) {
                return 2;
            }
            return 1;
        }

        private boolean targetsAll() {
            return sourceIdentities.isEmpty();
        }

        private boolean matches(SourceIdentity sourceIdentity) {
            return sourceIdentities.contains(sourceIdentity);
        }
    }
}
