# LED Sign Design Tool - PRD

## Problem Statement
Professional browser-based LED sign design tool for creating pixel/LED layouts for neon/LED signage. Uploads any TTF font, places 12mm OD LED pixels along borders or as fill, generates wiring paths for the LedEdit T8000 controller, and exports DXF/CJB files for AutoCAD/LedEdit production use.

## Architecture

### Tech Stack
- **Frontend**: React + HTML5 Canvas (raw, no library) + opentype.js (font parsing)
- **Backend**: FastAPI (Python) for file handling and exports
- **Key Libraries**: opentype.js, file-saver, ezdxf (Python), lucide-react

### File Structure
```
frontend/src/
  App.js                    - Main state, handlers, 3-panel layout
  App.css                   - Dark LED theme
  components/
    ToolPanel.jsx           - Left sidebar: font, text, mode, tools, undo, approve
    LedCanvas.jsx           - Main interactive canvas with port nodes
    ExportPanel.jsx         - Right sidebar: ports, wiring, export
  utils/
    fontParser.js           - opentype.js wrapper, bezier flatten, contour sampling
    pixelUtils.js           - Border/fill pixel placement with edge margin
    wireUtils.js            - Per-letter wiring, port assignment, zoom
    exportUtils.js          - DXF (AutoCAD R2010) + CJB (LedEdit XML)
```

## Core Requirements
1. Upload TTF/OTF font from local machine
2. Three modes: Fill (green), Border (cyan), Both
3. 12mm OD pixel circles, configurable spacing
4. Edge margin 3-5mm to prevent pixel bleed
5. T8000 controller support: 8 ports × 1024 pixels
6. Export as DXF or CJB

## What's Been Implemented

### v1 - Core MVP (2026-02-20)
- Font upload, text input, pixel placement
- Fill/Border/Both modes
- HTML5 Canvas with zoom/pan
- Auto-snake wiring, DXF/CJB export

### v2 - User Feedback (2026-02-20)
- Font size in CM, edge margin slider
- Thick blue guide border
- Smart wiring within letter bounds
- Break Apart mode, rubber band selection
- Break/Restore wiring buttons

### v3 - Advanced Wiring (2026-02-20)
- 10-step Undo button
- 8 selectable port buttons P1-P8
- Draggable port nodes on canvas
- Right-click escape functionality

### v4 - Per-Letter Wiring Control (2026-02-22)
- **Border=Cyan (#00d4ff), Fill=Green (#6bcb77)** - Different colors for border vs fill pixels
- **Separate S/E for Border AND Fill** - Each letter has BS/BE (Border Start/End) and FS/FE (Fill Start/End) markers
- **S/E cannot overlap** - Minimum spacing enforced between start/end pixels
- **Two-Stage Wiring per Letter**:
  - Stage 1 (Draft): Dashed preview wiring
  - Stage 2 (Approved): Solid blue wiring after clicking "Approve Letter Wiring"
- **Manual Port Connection** - No auto port assignment; user clicks port then letter's start node
- **Wire Connect Tool** - New tool button (Cable icon) to draw straight lines between two pixels
- **Ports Hidden by Default** - Eye/EyeOff icons on port buttons; ports only appear when clicked
- **Status bar** - Shows "Approved: X/Y" to track letter wiring approval progress

## Prioritized Backlog

### P0 - Critical
- [ ] Test CJB export with actual LedEdit software
- [ ] Test with complex letters (O, G, B, Q) for hollow area wiring

### P1 - High Value
- [ ] Arrow wiring direction tool
- [ ] Per-letter independent export (nesting mode)

### P2 - Enhancement
- [ ] Save/load project as JSON
- [ ] Convert to desktop app (Electron/Tauri)
- [ ] Background image import for tracing

## Next Tasks
1. Test with actual LedEdit T8000 software
2. Arrow direction tool for wiring flow
3. Per-letter independent editing/export
