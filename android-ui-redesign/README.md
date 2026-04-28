# PETES Mapper Android UI Redesign (Compose)

This package provides a UI-only redesign blueprint for a professional industrial-grade LED signage workflow app.

## 1) UI Architecture

- **Pattern:** MVVM-ready, UI state separated in lightweight ViewModels.
- **Layers:**
  - `theme/` — industrial dark Material 3 system.
  - `components/` — reusable technical widgets/panels.
  - `screens/` — feature screen composables.
  - `navigation/` — route contracts and graph.
  - `model/` — UI models only.
  - `viewmodel/` — screen-level state holders.
- **Non-goal:** no backend/export algorithm changes; buttons include integration hooks only.

## 2) Navigation Structure

Routes:
- `dashboard`
- `letter_builder`
- `wiring_intelligence`
- `layer_editor`
- `pixel_mapping`
- `export`

`PetesMapperApp()` hosts a bottom navigation scaffold and injects a single `NavHost`.

## 3) Reusable Components

- `IndustrialPanel` — standardized bordered control panel.
- `MetricRow` — quick numeric technical metrics.
- `GridCanvasPlaceholder` — precision-grid preview surface slot.
- `SegmentedToggle` — low-tap tab/action switcher.
- `StateIndicator` — wiring legend chips.

## 4) Theme System

- Dark industrial palette with high-contrast cyan/amber signaling.
- Monospace-weighted headings for technical character.
- Material 3 `darkColorScheme` used for consistency and future dynamic extension.

## 5) Screen Coverage

- **Dashboard**: New Project, Open Project, Templates, Wiring Library.
- **Letter Builder**: text, font, height, width, stroke thickness, pixel spacing.
- **Wiring Intelligence**: shape/wiring preview + options list + technical legend.
- **Layer Editor**: Border / Fill / Inner islands tabs.
- **Pixel Mapping Preview**: pixel index, data flow, strip IDs, power injection points.
- **Export**: DXF, controller mapping, PDF cards.

## 6) Responsiveness and Smoothness

- LazyColumn-first layouts for small/large devices.
- Animated segmented toggle for active-state feedback.
- Grid-preview slots provide clear insertion points for Canvas-driven renderers.
