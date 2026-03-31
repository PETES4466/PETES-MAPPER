# LED Sign Designer - PRD

## Problem Statement
Professional browser-based LED sign design tool for creating pixel/LED layouts for neon/LED signage.

## Architecture
- **Frontend**: React + HTML5 Canvas + opentype.js
- **Backend**: FastAPI (Python)

## What's Been Implemented

### Latest Changes (2025-03-31)
**User Control Fixes:**
- **NO AUTO-REWIRING** on paste/delete/join - User controls the flow
- Paste/Add Pixels now inherits port from parent pixels
- Delete preserves user's wiring order
- Join Wire just clears broken flag without re-ordering

**UI Improvements:**
- **Pixel Grid uses DROPDOWNS** instead of sliders - saves space
- **Arial Bold auto-loaded** as default font - one less operation
- "Connect Solid Wire" in right-click menu (green solid line)
- "Disconnect Wire" in right-click menu

**DXF Export Fixed:**
- Works WITHOUT requiring port selection
- Uses LED_BORDER/LED_FILL layers for unassigned pixels
- Wiring grouped by letter to avoid cross-letter jumps

**Port Management:**
- Port dropdown in MenuBar showing "P1 (count/1024)"
- Connecting port updates ALL pixels of that letter/type
- Port count now updates correctly after connection

### Visual Changes
- Dotted red = wiring recommendation
- Solid green = user-confirmed wiring (Connect Solid Wire)
- Thin white = font outline guide

## Prioritized Backlog

### P0 - Done
- [x] Remove auto-rewiring on user actions
- [x] Pixel Grid dropdowns instead of sliders
- [x] Connect Solid Wire in right-click menu
- [x] Arial Bold default font
- [x] Fix DXF export without port selection
- [x] Fix port count display

### P1 - High Value
- [ ] Fix CJB export for LedEdit compatibility
- [ ] Per-letter independent export

### P2 - Enhancement
- [ ] Save/load project as JSON
- [ ] Desktop app (Electron/Tauri)
