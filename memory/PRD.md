# LED Sign Design Tool - PRD

## Problem Statement
Professional browser-based LED sign design tool for creating pixel/LED layouts for neon/LED signage. Uploads any TTF font, places 12mm OD LED pixels along borders or as fill, generates wiring paths for the LedEdit T8000 controller, and exports DXF/CJB files for AutoCAD/LedEdit production use.

## Architecture

### Tech Stack
- **Frontend**: React + HTML5 Canvas (raw, no library) + opentype.js (font parsing)
- **Backend**: FastAPI + Python (served at /api)
- **Key Libraries**: opentype.js, file-saver, ezdxf (Python), lucide-react

### File Structure
```
frontend/src/
  App.js                    - Main state, handlers, 3-panel layout
  App.css                   - Dark LED theme
  components/
    ToolPanel.jsx           - Left sidebar: font, text, mode, tools, undo
    LedCanvas.jsx           - Main interactive canvas with port nodes
    ExportPanel.jsx         - Right sidebar: ports, wiring, export
  utils/
    fontParser.js           - opentype.js wrapper, bezier flatten, contour sampling
    pixelUtils.js           - Border/fill pixel placement with edge margin
    wireUtils.js            - Smart snake wiring, port assignment, letter zoom
    exportUtils.js          - DXF (AutoCAD R2010) + CJB (LedEdit XML)
```

## User Personas
- LED sign manufacturers
- Sign shop designers
- Professional lighting designers using LedEdit T8000 controller

## Core Requirements (Static)
1. Upload TTF/OTF font from local machine
2. Support all characters (uppercase, lowercase, symbols)
3. Three modes: Fill (grid), Border (outline), Both
4. 12mm OD pixel circles, configurable spacing
5. Edge margin 3-5mm to prevent pixel bleed / mechanical obstruction
6. T8000 controller support: 8 ports × 1024 pixels = 8192 max
7. Export as DXF (AutoCAD R2010) or CJB (LedEdit XML)
8. Smart wiring with first/last node marking per letter

## What's Been Implemented

### v1 - Core MVP (2026-02-20)
- Font upload (TTF/OTF) with opentype.js parsing
- Text input with font size (mm) and letter spacing
- Fill + Border + Both pixel placement modes
- HTML5 Canvas with zoom/pan (scroll to zoom, middle-drag to pan)
- Pixel selection (click), drag to move
- Auto-snake wiring (row-by-row serpentine)
- Click-to-connect manual wiring mode
- T8000 port assignment (8 × 1024) with color-coded visualization
- First/Last node markers per letter
- DXF export (AutoCAD R2010): PORT layers, wiring polyline, markers, numbers
- CJB export (LedEdit XML): port chains, wiring, first/last markers
- Copy/Paste/Delete pixels
- Display toggles (guide, wiring, numbers)
- Status bar with pixel counts

### v2 - User Feedback Improvements (2026-02-20)
- **Font size in CM**: dropdown 30–300cm (30 options) → stores as mm internally
- **Letter spacing in CM**: slider with cm display
- **Edge margin**: 0–10mm slider (default 3mm), prevents pixel bleeding and mechanical obstruction
- **Thick blue guide border**: constant 3px screen width, #1E7FFF with glow shadow
- **Prominent S/E markers**: outer pulsing ring, center letter label, text label above
- **Smart wiring**: nearest-neighbor row transitions minimize hollow-area jumps
- **Break Apart mode**: toggle per-letter highlight (unique colors), click letter → zoom to it
- **Rubber band selection**: drag on empty canvas to select multiple pixels
- **Break Wiring**: button disconnects selected pixel from chain (shown as red X)
- **Restore Wiring**: re-connects broken pixels
- **Right-click**: Add new pixel at clicked position
- **Adaptive canvas scale**: font canvas capped at 1200px to support 3000mm+ fonts

### v3 - Advanced Wiring & Controls (2026-02-20)
- **10-Step Undo**: Undo button in Tools section, reverts pixel/wiring changes
- **8 Selectable Port Buttons (P1-P8)**: Click to select, then click letter start to connect
- **Draggable Port Nodes**: Port markers on canvas can be dragged for positioning
- **Port-to-Letter Connection Lines**: Dashed lines show port connections visually
- **Start/End Nodes per Letter**: Clear S (green) and E (red) markers on each letter
- **Disconnect After Letter**: In Break Apart mode, disconnect wiring between letters (X marker)
- **Right-click Escape**: First right-click adds pixel, second right-click within 500ms cancels

## Prioritized Backlog

### P0 - Critical for production
- [ ] Verify CJB format compatibility with actual LedEdit 2021 software
- [ ] Test with complex letters: O, G, B, Q (hollow inner areas) for wiring quality
- [ ] Performance optimization for >5000 pixel designs

### P1 - High value
- [ ] Arrow wiring direction tool (drag arrow to set wiring flow direction)
- [ ] Per-letter independent export (nested export mode)
- [ ] Corner pixel detection and auto-fill for tight corners
- [ ] Wire join tool: click pixel A then B to bridge them

### P2 - Enhancement
- [ ] Real-time pixel count preview before Generate
- [ ] Background image import (trace over a reference)
- [ ] Pixel count per letter breakdown panel
- [ ] Save/load project as JSON
- [ ] Convert to desktop app (Electron/Tauri)
- [ ] Scale ruler overlay on canvas

## Next Tasks List
1. Test with actual LedEdit software to validate DXF/CJB import
2. Arrow wiring direction tool
3. Per-letter independent editing/export (nesting mode)
4. Performance test with full JOHN (6190 pixels) - optimize if needed
