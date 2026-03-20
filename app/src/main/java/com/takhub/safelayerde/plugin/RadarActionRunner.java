package com.takhub.safelayerde.plugin;

import com.takhub.safelayerde.render.map.RadarRenderException;

final class RadarActionRunner {

    interface Action {
        void run();
    }

    interface RenderAction {
        void run() throws RadarRenderException;
    }

    static final class Outcome {
        private final RadarRenderException renderFailure;
        private final Throwable fatalFailure;

        private Outcome(RadarRenderException renderFailure, Throwable fatalFailure) {
            this.renderFailure = renderFailure;
            this.fatalFailure = fatalFailure;
        }

        static Outcome success() {
            return new Outcome(null, null);
        }

        static Outcome renderFailure(RadarRenderException exception) {
            return new Outcome(exception, null);
        }

        static Outcome fatalFailure(Throwable throwable) {
            return new Outcome(null, throwable);
        }

        boolean isSuccess() {
            return renderFailure == null && fatalFailure == null;
        }

        boolean isRenderFailure() {
            return renderFailure != null;
        }

        RadarRenderException getRenderFailure() {
            return renderFailure;
        }

        Throwable getFatalFailure() {
            return fatalFailure;
        }
    }

    Outcome run(Action action) {
        try {
            action.run();
            return Outcome.success();
        } catch (RuntimeException exception) {
            return Outcome.fatalFailure(exception);
        } catch (LinkageError error) {
            return Outcome.fatalFailure(error);
        }
    }

    Outcome runRender(RenderAction action) {
        try {
            action.run();
            return Outcome.success();
        } catch (RadarRenderException exception) {
            return Outcome.renderFailure(exception);
        } catch (RuntimeException exception) {
            return Outcome.fatalFailure(exception);
        } catch (LinkageError error) {
            return Outcome.fatalFailure(error);
        }
    }
}
