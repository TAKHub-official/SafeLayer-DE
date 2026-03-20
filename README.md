# SafeLayer DE

SafeLayer DE is an ATAK plugin focused on official German warning and weather data.

This public repository contains a reduced source snapshot that shows how the plugin is structured and how the core flows work inside the app.

## What The Plugin Does

- Ingests BBK/NINA warning data
- Ingests DWD warning and radar data
- Merges source states into a single operational view
- Renders warnings and radar overlays inside ATAK
- Exposes ATAK-native controls, status, and detail views

## Source Layout

- `app/src/main/java/com/takhub/safelayerde/source`: feed adapters, fetchers, parsers, and normalization
- `app/src/main/java/com/takhub/safelayerde/domain`: warning models, policies, and orchestration
- `app/src/main/java/com/takhub/safelayerde/render`: map overlays, markers, shapes, and radar rendering
- `app/src/main/java/com/takhub/safelayerde/ui`: pane state, binding, and view models
- `app/src/main/java/com/takhub/safelayerde/plugin`: plugin runtime and ATAK integration points
- `app/src/main/res`: layouts, drawables, strings, and styles

## Data Sources

- BBK / NINA
- Deutscher Wetterdienst (DWD)

## Notes

- This repository is intentionally limited to the application-facing source snapshot.
- Internal development material, build environment files, and private working assets are not included.

## License

See `LICENSE` and `NOTICE`.
