import React, { useState, useCallback, useRef } from 'react';
import './App.css';
import ToolPanel from './components/ToolPanel';
import LedCanvas from './components/LedCanvas';
import ExportPanel from './components/ExportPanel';
import { parseFont, getGlyphPathMm, getAdvanceWidth } from './utils/fontParser';
import { generatePixelsForText, buildPixelObjects } from './utils/pixelUtils';
import { autoSnakeWiringPerLetter, assignPortsWithLetterMap, computeLetterZoom, buildInitialPortNodes } from './utils/wireUtils';
import { generateDXF, generateCJB, downloadFile } from './utils/exportUtils';

// Maximum undo history steps
const MAX_HISTORY = 10;

export default function App() {
  // ── Font & Text ──────────────────────────────────────────────────────────
  const [font, setFont]           = useState(null);
  const [fontName, setFontName]   = useState('');
  const [text, setText]           = useState('HELLO');
  const [fontSizeCm, setFontSizeCm] = useState(100);     // in cm (=mm*10)
  const [letterSpacingCm, setLetterSpacingCm] = useState(0.5); // in cm

  // ── Mode & Spacing ───────────────────────────────────────────────────────
  const [mode, setMode]                         = useState('both');
  const [fillSpacingMm, setFillSpacingMm]       = useState(15);
  const [borderSpacingMm, setBorderSpacingMm]   = useState(12);
  const [borderPixelCount, setBorderPixelCount] = useState('auto');
  const [pixelOdMm, setPixelOdMm]               = useState(12);
  const [edgeMarginMm, setEdgeMarginMm]         = useState(3);

  // ── Canvas Data ──────────────────────────────────────────────────────────
  const [pixels, setPixels]               = useState([]);
  const [guideCommands, setGuideCommands] = useState([]);
  const [wiringOrder, setWiringOrder]     = useState([]);

  // ── Wiring ───────────────────────────────────────────────────────────────
  const [wiringMode, setWiringMode]           = useState('snake');
  const [wiringDirection, setWiringDirection] = useState('ltr-ttb');
  const [pendingWire, setPendingWire]         = useState([]);

  // ── Tools & UI ───────────────────────────────────────────────────────────
  const [activeTool, setActiveTool]     = useState('select');
  const [selectedIds, setSelectedIds]   = useState(new Set());
  const [clipboard, setClipboard]       = useState([]);
  const [showNumbers, setShowNumbers]   = useState(true);
  const [showWiring, setShowWiring]     = useState(true);
  const [showGuide, setShowGuide]       = useState(true);
  const [isGenerating, setIsGenerating] = useState(false);

  // ── Break Apart ──────────────────────────────────────────────────────────
  const [isBreakApart, setIsBreakApart]           = useState(false);
  const [selectedLetterIndex, setSelectedLetterIndex] = useState(null);

  // ── Export ───────────────────────────────────────────────────────────────
  const [exportFormat, setExportFormat] = useState('dxf');

  // ── Port Nodes (T8000 controller ports) ──────────────────────────────────
  const [portNodes, setPortNodes] = useState(buildInitialPortNodes());
  const [letterPortMap, setLetterPortMap] = useState({}); // { letterIndex: portIndex }
  const [selectedPortIndex, setSelectedPortIndex] = useState(null); // Currently selected port for connection
  const [visiblePorts, setVisiblePorts] = useState(new Set()); // Which ports are visible on canvas

  // ── Letter Wiring Approval (per-letter two-stage wiring) ─────────────────
  const [approvedLetters, setApprovedLetters] = useState(new Set()); // Set<letterIndex> - letters with approved wiring

  // ── Wire Connect Tool State ──────────────────────────────────────────────
  const [wireConnectStart, setWireConnectStart] = useState(null); // First pixel in wire connect

  // ── Manual Wire Connections (user-drawn lines between pixels) ────────────
  const [manualWires, setManualWires] = useState([]); // Array of { from: pixelId, to: pixelId }

  // ── Undo History ─────────────────────────────────────────────────────────
  const [history, setHistory] = useState([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  // ── Right-click Escape State ─────────────────────────────────────────────
  const [lastAction, setLastAction] = useState(null); // Track last action for escape

  // Canvas zoom ref (to allow App to trigger zoom)
  const canvasZoomRef = useRef(null);

  // Convert cm → mm for internal use
  const fontSizeMm     = fontSizeCm * 10;
  const letterSpacingMm = letterSpacingCm * 10;

  // ── Save state to history ────────────────────────────────────────────────
  const saveToHistory = useCallback((newPixels, newWiringOrder, newPortNodes, newLetterPortMap, newApprovedLetters, newManualWires) => {
    const snapshot = {
      pixels: JSON.parse(JSON.stringify(newPixels)),
      wiringOrder: [...newWiringOrder],
      portNodes: JSON.parse(JSON.stringify(newPortNodes)),
      letterPortMap: { ...newLetterPortMap },
      approvedLetters: new Set(newApprovedLetters || []),
      manualWires: [...(newManualWires || [])],
      selectedIds: new Set()
    };
    
    setHistory(prev => {
      const newHistory = prev.slice(0, historyIndex + 1);
      newHistory.push(snapshot);
      if (newHistory.length > MAX_HISTORY) {
        newHistory.shift();
        return newHistory;
      }
      return newHistory;
    });
    setHistoryIndex(prev => Math.min(prev + 1, MAX_HISTORY - 1));
  }, [historyIndex]);

  // ── Undo function ────────────────────────────────────────────────────────
  const handleUndo = useCallback(() => {
    if (historyIndex <= 0 || history.length === 0) return;
    
    const newIndex = historyIndex - 1;
    const snapshot = history[newIndex];
    if (!snapshot) return;
    
    setPixels(snapshot.pixels);
    setWiringOrder(snapshot.wiringOrder);
    setPortNodes(snapshot.portNodes);
    setLetterPortMap(snapshot.letterPortMap);
    setApprovedLetters(snapshot.approvedLetters || new Set());
    setManualWires(snapshot.manualWires || []);
    setSelectedIds(snapshot.selectedIds);
    setHistoryIndex(newIndex);
  }, [history, historyIndex]);

  // ── Font Load ────────────────────────────────────────────────────────────
  const handleFontLoad = useCallback((arrayBuffer, name) => {
    try { setFont(parseFont(arrayBuffer)); setFontName(name); }
    catch (e) { alert('Font parse error: ' + e.message); }
  }, []);

  // ── Generate ─────────────────────────────────────────────────────────────
  const handleGenerate = useCallback(async () => {
    if (!font || !text.trim()) return;
    setIsGenerating(true);
    try {
      await new Promise(r => setTimeout(r, 10));
      const settings = { fontSizeMm, letterSpacingMm, mode, borderSpacingMm, borderPixelCount, fillSpacingMm, edgeMarginMm };
      const raw  = generatePixelsForText(font, text, settings);
      const base = buildPixelObjects(raw);

      // Build guide paths
      const guides = [];
      let xOff = 0;
      for (const char of text) {
        if (char === ' ') { xOff += fontSizeMm * 0.28; continue; }
        guides.push({ letter: char, commands: getGlyphPathMm(font, char, fontSizeMm, xOff) });
        xOff += getAdvanceWidth(font, char, fontSizeMm) + letterSpacingMm;
      }
      setGuideCommands(guides);

      const order  = autoSnakeWiring(base, wiringDirection);
      const wired  = assignPortsWithLetterMap(base, order, letterPortMap, disconnectedAfter);
      setPixels(wired);
      setWiringOrder(order);
      setSelectedIds(new Set());
      setPendingWire([]);
      setSelectedLetterIndex(null);
      setLastAction(null);
      
      // Reset port nodes positions based on pixel bounds
      const resetPorts = buildInitialPortNodes();
      setPortNodes(resetPorts);
      
      // Save to history
      saveToHistory(wired, order, resetPorts, letterPortMap, disconnectedAfter);
    } catch (e) {
      console.error(e);
      alert('Generate error: ' + e.message);
    } finally { setIsGenerating(false); }
  }, [font, text, fontSizeMm, letterSpacingMm, mode, borderSpacingMm, borderPixelCount, fillSpacingMm, edgeMarginMm, wiringDirection, letterPortMap, disconnectedAfter, saveToHistory]);

  // ── Re-Wire ──────────────────────────────────────────────────────────────
  const handleReWire = useCallback(() => {
    if (!pixels.length) return;
    const order = autoSnakeWiring(pixels, wiringDirection);
    const wired = assignPortsWithLetterMap(pixels, order, letterPortMap, disconnectedAfter);
    setPixels(wired);
    setWiringOrder(order);
    saveToHistory(wired, order, portNodes, letterPortMap, disconnectedAfter);
  }, [pixels, wiringDirection, letterPortMap, disconnectedAfter, portNodes, saveToHistory]);

  // ── Apply pending click-wire ──────────────────────────────────────────────
  const handleApplyWire = useCallback(() => {
    if (pendingWire.length < 2) return;
    const manualSet = new Set(pendingWire);
    const rest      = pixels.filter(p => !manualSet.has(p.id));
    const autoOrder = autoSnakeWiring(rest, wiringDirection);
    const fullOrder = [...pendingWire, ...autoOrder];
    const updated   = pixels.map(p => ({ ...p, isAuto: !manualSet.has(p.id) }));
    const wired = assignPortsWithLetterMap(updated, fullOrder, letterPortMap, disconnectedAfter);
    setPixels(wired);
    setWiringOrder(fullOrder);
    setPendingWire([]);
    saveToHistory(wired, fullOrder, portNodes, letterPortMap, disconnectedAfter);
  }, [pendingWire, pixels, wiringDirection, letterPortMap, disconnectedAfter, portNodes, saveToHistory]);

  // ── Pixel move ────────────────────────────────────────────────────────────
  const handlePixelMove = useCallback((id, newX, newY) => {
    setPixels(prev => {
      const next = prev.map(p => p.id === id ? { ...p, x: newX, y: newY, isAuto: false } : p);
      return assignPortsWithLetterMap(next, wiringOrder, letterPortMap, disconnectedAfter);
    });
    setLastAction({ type: 'move', id, x: newX, y: newY });
  }, [wiringOrder, letterPortMap, disconnectedAfter]);

  // ── Pixel select ──────────────────────────────────────────────────────────
  const handlePixelSelect = useCallback((ids, multi = false) => {
    setSelectedIds(prev => {
      const next = multi ? new Set(prev) : new Set();
      for (const id of ids) { if (next.has(id)) next.delete(id); else next.add(id); }
      return next;
    });
  }, []);

  // ── Break-apart letter select ─────────────────────────────────────────────
  const handleLetterSelect = useCallback((letterIndex) => {
    setSelectedLetterIndex(prev => prev === letterIndex ? null : letterIndex);
    // Trigger canvas zoom to this letter
    if (canvasZoomRef.current) {
      canvasZoomRef.current(letterIndex);
    }
  }, []);

  // ── Wire click ────────────────────────────────────────────────────────────
  const handleWireClick = useCallback((pixelId) => {
    setPendingWire(prev => prev.includes(pixelId) ? prev : [...prev, pixelId]);
    setLastAction({ type: 'wire', pixelId });
  }, []);

  // ── Break wiring ──────────────────────────────────────────────────────────
  const handleBreakWiring = useCallback(() => {
    if (!selectedIds.size) return;
    const newPixels = pixels.map(p => selectedIds.has(p.id) ? { ...p, wiringBroken: true } : p);
    setPixels(newPixels);
    saveToHistory(newPixels, wiringOrder, portNodes, letterPortMap, disconnectedAfter);
  }, [selectedIds, pixels, wiringOrder, portNodes, letterPortMap, disconnectedAfter, saveToHistory]);

  // ── Restore wiring ────────────────────────────────────────────────────────
  const handleRestoreWiring = useCallback(() => {
    if (!selectedIds.size) return;
    const newPixels = pixels.map(p => selectedIds.has(p.id) ? { ...p, wiringBroken: false } : p);
    setPixels(newPixels);
    saveToHistory(newPixels, wiringOrder, portNodes, letterPortMap, disconnectedAfter);
  }, [selectedIds, pixels, wiringOrder, portNodes, letterPortMap, disconnectedAfter, saveToHistory]);

  // ── Disconnect wiring between letters ─────────────────────────────────────
  const handleDisconnectLetterWiring = useCallback(() => {
    if (selectedLetterIndex === null) return;
    const newDisconnected = new Set(disconnectedAfter);
    if (newDisconnected.has(selectedLetterIndex)) {
      newDisconnected.delete(selectedLetterIndex);
    } else {
      newDisconnected.add(selectedLetterIndex);
    }
    setDisconnectedAfter(newDisconnected);
    
    // Re-assign ports with new disconnection
    const wired = assignPortsWithLetterMap(pixels, wiringOrder, letterPortMap, newDisconnected);
    setPixels(wired);
    saveToHistory(wired, wiringOrder, portNodes, letterPortMap, newDisconnected);
  }, [selectedLetterIndex, disconnectedAfter, pixels, wiringOrder, letterPortMap, portNodes, saveToHistory]);

  // ── Add pixel at position ─────────────────────────────────────────────────
  const handleAddPixel = useCallback((xMm, yMm) => {
    let counter = Date.now();
    const newPx = {
      id: `px_add_${counter++}`,
      x: xMm, y: yMm,
      type: 'fill',
      letter: '?', letterIndex: 0,
      portIndex: -1, portPixelIndex: -1,
      wiringOrder: -1,
      isFirst: false, isLast: false,
      isAuto: false, wiringBroken: false, selected: false
    };
    const allPx = [...pixels, newPx];
    const order = [...wiringOrder, newPx.id];
    const wired = assignPortsWithLetterMap(allPx, order, letterPortMap, disconnectedAfter);
    setPixels(wired);
    setWiringOrder(order);
    setSelectedIds(new Set([newPx.id]));
    setLastAction({ type: 'addPixel', id: newPx.id });
    saveToHistory(wired, order, portNodes, letterPortMap, disconnectedAfter);
  }, [pixels, wiringOrder, letterPortMap, disconnectedAfter, portNodes, saveToHistory]);

  // ── Right-click escape (cancel last operation) ────────────────────────────
  const handleEscape = useCallback(() => {
    if (lastAction) {
      if (lastAction.type === 'addPixel') {
        // Remove the last added pixel
        const newPixels = pixels.filter(p => p.id !== lastAction.id);
        const newOrder = wiringOrder.filter(id => id !== lastAction.id);
        setPixels(assignPortsWithLetterMap(newPixels, newOrder, letterPortMap, disconnectedAfter));
        setWiringOrder(newOrder);
        setSelectedIds(new Set());
      } else if (lastAction.type === 'wire') {
        // Remove last pixel from pending wire
        setPendingWire(prev => prev.slice(0, -1));
      }
      setLastAction(null);
    } else {
      // Clear selection or pending operations
      setSelectedIds(new Set());
      setPendingWire([]);
      setSelectedPortIndex(null);
      setActivePortTool(false);
    }
  }, [lastAction, pixels, wiringOrder, letterPortMap, disconnectedAfter]);

  // ── Port node drag ────────────────────────────────────────────────────────
  const handlePortNodeMove = useCallback((portIndex, newX, newY) => {
    setPortNodes(prev => prev.map(p => 
      p.portIndex === portIndex ? { ...p, x: newX, y: newY } : p
    ));
  }, []);

  // ── Connect port to letter ────────────────────────────────────────────────
  const handleConnectPortToLetter = useCallback((portIndex, letterIndex) => {
    const newMap = { ...letterPortMap, [letterIndex]: portIndex };
    setLetterPortMap(newMap);
    
    // Re-assign ports with new mapping
    const wired = assignPortsWithLetterMap(pixels, wiringOrder, newMap, disconnectedAfter);
    setPixels(wired);
    setSelectedPortIndex(null);
    saveToHistory(wired, wiringOrder, portNodes, newMap, disconnectedAfter);
  }, [letterPortMap, pixels, wiringOrder, disconnectedAfter, portNodes, saveToHistory]);

  // ── Select port for connection ────────────────────────────────────────────
  const handleSelectPort = useCallback((portIndex) => {
    setSelectedPortIndex(prev => prev === portIndex ? null : portIndex);
    setActivePortTool(true);
  }, []);

  // ── Copy / Paste / Delete ─────────────────────────────────────────────────
  const handleCopy   = useCallback(() => setClipboard(pixels.filter(p => selectedIds.has(p.id))), [pixels, selectedIds]);
  const handlePaste  = useCallback(() => {
    if (!clipboard.length) return;
    let c = Date.now();
    const newPx = clipboard.map(p => ({ ...p, id: `px_paste_${c++}`, x: p.x + 10, y: p.y + 10, isAuto: false }));
    const all   = [...pixels, ...newPx];
    const order = autoSnakeWiring(all, wiringDirection);
    const wired = assignPortsWithLetterMap(all, order, letterPortMap, disconnectedAfter);
    setPixels(wired);
    setWiringOrder(order);
    setSelectedIds(new Set(newPx.map(p => p.id)));
    saveToHistory(wired, order, portNodes, letterPortMap, disconnectedAfter);
  }, [clipboard, pixels, wiringDirection, letterPortMap, disconnectedAfter, portNodes, saveToHistory]);
  
  const handleDelete = useCallback(() => {
    if (!selectedIds.size) return;
    const rem = pixels.filter(p => !selectedIds.has(p.id));
    const ord = wiringOrder.filter(id => !selectedIds.has(id));
    const wired = assignPortsWithLetterMap(rem, ord, letterPortMap, disconnectedAfter);
    setPixels(wired);
    setWiringOrder(ord);
    setSelectedIds(new Set());
    saveToHistory(wired, ord, portNodes, letterPortMap, disconnectedAfter);
  }, [selectedIds, pixels, wiringOrder, letterPortMap, disconnectedAfter, portNodes, saveToHistory]);

  // ── Export ────────────────────────────────────────────────────────────────
  const handleExport = useCallback(() => {
    if (!pixels.length) return;
    const s = { pixelOdMm, text, fontSizeMm };
    if (exportFormat === 'dxf') {
      downloadFile(generateDXF(pixels, wiringOrder, s), `led_${text.replace(/\s+/g,'_')}.dxf`, 'application/dxf');
    } else {
      downloadFile(generateCJB(pixels, wiringOrder, s), `led_${text.replace(/\s+/g,'_')}.cjb`, 'application/xml');
    }
  }, [pixels, wiringOrder, exportFormat, pixelOdMm, text, fontSizeMm]);

  return (
    <div className="app-layout">
      <ToolPanel
        font={font} fontName={fontName} onFontLoad={handleFontLoad}
        text={text} onTextChange={setText}
        fontSizeCm={fontSizeCm} onFontSizeCmChange={setFontSizeCm}
        letterSpacingCm={letterSpacingCm} onLetterSpacingCmChange={setLetterSpacingCm}
        mode={mode} onModeChange={setMode}
        fillSpacingMm={fillSpacingMm} onFillSpacingChange={setFillSpacingMm}
        borderSpacingMm={borderSpacingMm} onBorderSpacingChange={setBorderSpacingMm}
        borderPixelCount={borderPixelCount} onBorderPixelCountChange={setBorderPixelCount}
        pixelOdMm={pixelOdMm} onPixelOdChange={setPixelOdMm}
        edgeMarginMm={edgeMarginMm} onEdgeMarginChange={setEdgeMarginMm}
        activeTool={activeTool} onToolChange={setActiveTool}
        showNumbers={showNumbers} onShowNumbersChange={setShowNumbers}
        showWiring={showWiring} onShowWiringChange={setShowWiring}
        showGuide={showGuide} onShowGuideChange={setShowGuide}
        isGenerating={isGenerating} onGenerate={handleGenerate}
        onDelete={handleDelete} onCopy={handleCopy} onPaste={handlePaste}
        onBreakWiring={handleBreakWiring} onRestoreWiring={handleRestoreWiring}
        isBreakApart={isBreakApart} onBreakApartToggle={() => setIsBreakApart(v => !v)}
        clipboardCount={clipboard.length} selectedCount={selectedIds.size}
        pixelCount={pixels.length}
        onUndo={handleUndo}
        canUndo={historyIndex > 0}
        onDisconnectLetterWiring={handleDisconnectLetterWiring}
        selectedLetterIndex={selectedLetterIndex}
        disconnectedAfter={disconnectedAfter}
      />

      <div className="canvas-area">
        <div className="canvas-toolbar">
          <span style={{ fontSize: 11, color: 'var(--muted)' }}>
            Scroll=Zoom &nbsp;·&nbsp; Middle-drag/Pan=Move &nbsp;·&nbsp; Right-click=Add pixel (2nd click=Escape)
          </span>
          <span style={{ marginLeft: 'auto', fontSize: 11 }}>
            {pixels.length > 0 && (
              <span style={{ color: 'var(--muted)' }}>
                <span style={{ color: 'var(--accent2)' }}>{pixels.length}</span> px &nbsp;·&nbsp;
                Font: <span style={{ color: 'var(--accent)' }}>{fontSizeCm}cm</span> &nbsp;·&nbsp;
                Margin: <span style={{ color: 'var(--accent)' }}>{edgeMarginMm}mm</span>
              </span>
            )}
          </span>
        </div>

        <LedCanvas
          pixels={pixels} wiringOrder={wiringOrder} guideCommands={guideCommands}
          selectedIds={selectedIds} activeTool={activeTool}
          pixelOdMm={pixelOdMm}
          showNumbers={showNumbers} showWiring={showWiring} showGuide={showGuide}
          wiringMode={wiringMode} pendingWire={pendingWire}
          isBreakApart={isBreakApart} selectedLetterIndex={selectedLetterIndex}
          onPixelMove={handlePixelMove} onPixelSelect={handlePixelSelect}
          onWireClick={handleWireClick} onLetterSelect={handleLetterSelect}
          onAddPixel={handleAddPixel}
          onEscape={handleEscape}
          lastAction={lastAction}
          zoomRef={canvasZoomRef}
          portNodes={portNodes}
          onPortNodeMove={handlePortNodeMove}
          letterPortMap={letterPortMap}
          disconnectedAfter={disconnectedAfter}
          selectedPortIndex={selectedPortIndex}
          onConnectPortToLetter={handleConnectPortToLetter}
          activePortTool={activePortTool}
        />

        <div className="canvas-statusbar">
          <span>Selected: <span className="status-val">{selectedIds.size}</span></span>
          <span>Border: <span className="status-val">{pixels.filter(p=>p.type==='border').length}</span></span>
          <span>Fill: <span className="status-val">{pixels.filter(p=>p.type==='fill').length}</span></span>
          <span>Broken: <span className="status-val" style={{ color: pixels.some(p=>p.wiringBroken)?'var(--danger)':'inherit' }}>
            {pixels.filter(p=>p.wiringBroken).length}
          </span></span>
          {pendingWire.length > 0 && <span style={{ color: 'var(--accent)' }}>Wire chain: <span className="status-val">{pendingWire.length}</span></span>}
          {isBreakApart && selectedLetterIndex !== null && (
            <span style={{ color: 'var(--accent2)' }}>Letter selected: index {selectedLetterIndex}</span>
          )}
          {historyIndex > 0 && (
            <span style={{ color: 'var(--muted)' }}>Undo: <span className="status-val">{historyIndex}</span></span>
          )}
        </div>
      </div>

      <ExportPanel
        pixels={pixels} wiringOrder={wiringOrder} pendingWire={pendingWire}
        wiringMode={wiringMode} onWiringModeChange={setWiringMode}
        wiringDirection={wiringDirection} onWiringDirectionChange={setWiringDirection}
        onReWire={handleReWire} onApplyWire={handleApplyWire}
        onClearWire={() => setPendingWire([])}
        exportFormat={exportFormat} onExportFormatChange={setExportFormat}
        onExport={handleExport}
        portNodes={portNodes}
        letterPortMap={letterPortMap}
        selectedPortIndex={selectedPortIndex}
        onSelectPort={handleSelectPort}
        text={text}
      />
    </div>
  );
}
