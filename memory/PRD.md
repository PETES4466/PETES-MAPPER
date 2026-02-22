# LED Sign Designer - PRD

## Problem Statement
Professional browser-based LED sign design tool for creating pixel/LED layouts for neon/LED signage. Uploads any TTF font, places 12mm OD LED pixels along borders or as fill, generates wiring paths for the LedEdit T8000 controller, and exports DXF/CJB files for AutoCAD/LedEdit production use.

## Architecture

### Tech Stack
- **Frontend**: React + HTML5 Canvas + opentype.js
- **Backend**: FastAPI (Python)
- **Key Libraries**: opentype.js, file-saver, ezdxf (Python), lucide-react

### File Structure
```
frontend/src/
  App.js                    - Main app with professional layout
  App.css                   - Professional dark theme
  components/
    MenuBar.jsx             - Top menu bar with File/Edit/View/Tools + Port Dropdown
    Toolbar.jsx             - Left vertical toolbar (tools + actions)
    PropertiesPanel.jsx     - Collapsible left properties panel
    LedCanvas.jsx           - Main interactive canvas
    PortsPanel.jsx          - Collapsible right ports panel
    StatusPanel.jsx         - Bottom status bar
  utils/
    fontParser.js           - opentype.js wrapper
    pixelUtils.js           - Pixel placement with strict boundary checking
    wireUtils.js            - Smart wiring with nearest neighbor algorithm
    exportUtils.js          - DXF/CJB export
```

## What's Been Implemented

### v6 - Smart Wiring & Port Dropdown (2026-02-22)
**Port Management:**
- Port dropdown menu in MenuBar showing "P1 (156/1024)" format
- All 8 ports (P1-P8) with color indicators and progress bars
- Selecting port automatically makes it visible on canvas

**Pixel Placement:**
- Stricter boundary checking to prevent pixels bleeding outside font outline
- Edge margin now includes pixel radius for accurate placement
- 24-point ring check for more precise boundary detection

**Smart Wiring:**
- Nearest neighbor wiring algorithm
- Stays within letter boundaries (won't jump across gaps in letters like 'H', 'A')
- Wiring displayed as dotted red lines (recommendation)

**UI Improvements:**
- Font outline rendered as thin white reference line
- Enhanced right-click context menu with:
  - Add Pixels Between (requires 2+ selected)
  - Connect Wire (requires 2+ selected)
  - Disconnect Wire (requires 1+ selected)

### v5 - Professional UI Overhaul (2026-02-22)
**Major UI Redesign:**
- **Menu Bar** with File/Edit/View/Tools dropdown menus
- **File Menu**: Load Font, Fill/Border/Both Mode, Export DXF, Export CJB, Exit
- **Edit Menu**: Undo, Copy, Paste, Delete (with shortcuts)
- **View Menu**: Show Guide, Show Wiring, Show Numbers, Show Ports toggles
- **Tools Menu**: Select (V), Pan (H), Wire Connect (W)
- **Quick Toolbar**: Font name, Text input, Generate button
- **Collapsible Properties Panel**: Letter Size, Pixel Grid, Wiring settings
- **Large Canvas Area**: Maximized workspace
- **Collapsible Ports Panel**: Pixel stats, T8000 ports (P1-P8), Export buttons
- **Status Bar**: Total/Border/Fill counts, Selected, Tool, Port stats

**Functionality Changes:**
- **Paste Logic**: Adds pixels BETWEEN 2+ selected pixels following wiring path
- **Right-click**: Context menu for adding multiple pixels (no direct add)
- **Break Wire**: Disconnects selected pixel from next in sequence
- **Join Wire**: Connects selected pixels and renumbers sequence
- **Removed**: Break Apart feature completely removed
- **Border=Cyan, Fill=Green**: Different colors for visual distinction

### Previous Versions
- v1: Core MVP with font upload, pixel placement, DXF export
- v2: Safety margins, smart wiring, rubber band selection
- v3: 10-step undo, port buttons, draggable port nodes
- v4: Per-letter wiring, BS/BE/FS/FE markers, Wire Connect tool

## Prioritized Backlog

### P0 - Critical
- [x] Port dropdown menu in MenuBar
- [x] Stricter pixel placement (no bleeding outside font outline)
- [x] Smart wiring using nearest neighbor algorithm
- [x] Enhanced right-click context menu (Connect/Disconnect Wire)
- [ ] Fix CJB export compatibility with LedEdit software

### P1 - High Value
- [ ] Per-letter independent export
- [ ] Arrow wiring direction tool

### P2 - Enhancement
- [ ] Save/load project as JSON
- [ ] Desktop app (Electron/Tauri)
- [ ] System fonts access or more default fonts

## Next Tasks
1. Verify CJB export compatibility with LedEdit
2. Per-letter independent export feature
3. Add system fonts or more default fonts
