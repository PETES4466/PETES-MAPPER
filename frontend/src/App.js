import React, { useState, useCallback } from 'react';
import './App.css';
import ToolPanel from './components/ToolPanel';
import LedCanvas from './components/LedCanvas';
import ExportPanel from './components/ExportPanel';
import { parseFont, getGlyphPathMm, getAdvanceWidth } from './utils/fontParser';
import { generatePixelsForText, buildPixelObjects } from './utils/pixelUtils';
import { autoSnakeWiring, assignPorts } from './utils/wireUtils';
import { generateDXF, generateCJB, downloadFile } from './utils/exportUtils';

export default function App() {
  // ── Font & Text ──────────────────────────────────────────────────────────
  const [font, setFont]               = useState(null);
  const [fontName, setFontName]       = useState('');
  const [text, setText]               = useState('HELLO');
  const [fontSizeMm, setFontSizeMm]   = useState(100);
  const [letterSpacingMm, setLetterSpacingMm] = useState(5);

  // ── Mode & Spacing ───────────────────────────────────────────────────────
  const [mode, setMode]                     = useState('both');
  const [fillSpacingMm, setFillSpacingMm]   = useState(15);
  const [borderSpacingMm, setBorderSpacingMm] = useState(12);
  const [borderPixelCount, setBorderPixelCount] = useState('auto');
  const [pixelOdMm, setPixelOdMm]           = useState(12);

  // ── Canvas Data ──────────────────────────────────────────────────────────
  const [pixels, setPixels]           = useState([]);
  const [guideCommands, setGuideCommands] = useState([]); // [{letter, commands}]
  const [wiringOrder, setWiringOrder] = useState([]);     // ordered pixel IDs

  // ── Wiring ───────────────────────────────────────────────────────────────
  const [wiringMode, setWiringMode]         = useState('snake');
  const [wiringDirection, setWiringDirection] = useState('ltr-ttb');
  const [pendingWire, setPendingWire]       = useState([]); // for click-wire mode

  // ── Tools & UI ───────────────────────────────────────────────────────────
  const [activeTool, setActiveTool] = useState('select');
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [clipboard, setClipboard]   = useState([]);
  const [showNumbers, setShowNumbers] = useState(true);
  const [showWiring, setShowWiring]   = useState(true);
  const [showGuide, setShowGuide]     = useState(true);
  const [isGenerating, setIsGenerating] = useState(false);

  // ── Export ───────────────────────────────────────────────────────────────
  const [exportFormat, setExportFormat] = useState('dxf');

  // ── Font Load Handler ────────────────────────────────────────────────────
  const handleFontLoad = useCallback((arrayBuffer, name) => {
    try {
      const parsed = parseFont(arrayBuffer);
      setFont(parsed);
      setFontName(name);
    } catch (e) {
      alert('Failed to parse font: ' + e.message);
    }
  }, []);

  // ── Generate Pixels ──────────────────────────────────────────────────────
  const handleGenerate = useCallback(async () => {
    if (!font || !text.trim()) return;
    setIsGenerating(true);
    try {
      await new Promise(r => setTimeout(r, 10)); // allow UI update

      const settings = { fontSizeMm, letterSpacingMm, mode, borderSpacingMm, borderPixelCount, fillSpacingMm };
      const rawPixels = generatePixelsForText(font, text, settings);
      const basePixels = buildPixelObjects(rawPixels);

      // Guide paths for canvas overlay
      const guides = [];
      let xOff = 0;
      for (const char of text) {
        if (char === ' ') { xOff += fontSizeMm * 0.28; continue; }
        const cmds = getGlyphPathMm(font, char, fontSizeMm, xOff);
        guides.push({ letter: char, commands: cmds });
        xOff += getAdvanceWidth(font, char, fontSizeMm) + letterSpacingMm;
      }
      setGuideCommands(guides);

      // Auto-wire
      const order = autoSnakeWiring(basePixels, wiringDirection);
      const wiredPixels = assignPorts(basePixels, order);
      setPixels(wiredPixels);
      setWiringOrder(order);
      setSelectedIds(new Set());
      setPendingWire([]);
    } catch (e) {
      console.error('Generate error:', e);
      alert('Error generating pixels: ' + e.message);
    } finally {
      setIsGenerating(false);
    }
  }, [font, text, fontSizeMm, letterSpacingMm, mode, borderSpacingMm, borderPixelCount, fillSpacingMm, wiringDirection]);

  // ── Re-wire (apply wiring settings to existing pixels) ───────────────────
  const handleReWire = useCallback(() => {
    if (!pixels.length) return;
    const autoPixels = pixels.filter(p => p.isAuto);
    const manualOrder = wiringOrder.filter(id => {
      const p = pixels.find(px => px.id === id);
      return p && !p.isAuto;
    });
    // For now, re-snake all auto pixels
    const newOrder = autoSnakeWiring(pixels, wiringDirection);
    const rewired = assignPorts(pixels, newOrder);
    setPixels(rewired);
    setWiringOrder(newOrder);
  }, [pixels, wiringOrder, wiringDirection]);

  // ── Apply pending click-wire ─────────────────────────────────────────────
  const handleApplyWire = useCallback(() => {
    if (pendingWire.length < 2) return;
    // Mark pending pixels as manual, fill remaining auto pixels by snake
    const manualSet = new Set(pendingWire);
    const autoPixels = pixels.filter(p => !manualSet.has(p.id));
    const autoOrder  = autoSnakeWiring(autoPixels, wiringDirection);
    const fullOrder  = [...pendingWire, ...autoOrder];
    const updated = pixels.map(p => ({
      ...p, isAuto: !manualSet.has(p.id)
    }));
    const wired = assignPorts(updated, fullOrder);
    setPixels(wired);
    setWiringOrder(fullOrder);
    setPendingWire([]);
  }, [pendingWire, pixels, wiringDirection]);

  // ── Pixel move callback (from canvas drag) ───────────────────────────────
  const handlePixelMove = useCallback((id, newX, newY) => {
    setPixels(prev => prev.map(p => p.id === id ? { ...p, x: newX, y: newY, isAuto: false } : p));
  }, []);

  // ── Pixel selection ──────────────────────────────────────────────────────
  const handlePixelSelect = useCallback((ids, multi = false) => {
    setSelectedIds(prev => {
      const next = multi ? new Set(prev) : new Set();
      for (const id of ids) {
        if (next.has(id)) next.delete(id);
        else next.add(id);
      }
      return next;
    });
  }, []);

  // ── Wire-click: add pixel to pending chain ───────────────────────────────
  const handleWireClick = useCallback((pixelId) => {
    setPendingWire(prev => {
      if (prev.includes(pixelId)) return prev;
      return [...prev, pixelId];
    });
  }, []);

  // ── Cut/Copy/Paste/Delete ────────────────────────────────────────────────
  const handleCopy = useCallback(() => {
    const sel = pixels.filter(p => selectedIds.has(p.id));
    setClipboard(sel);
  }, [pixels, selectedIds]);

  const handlePaste = useCallback(() => {
    if (!clipboard.length) return;
    const offset = 10;
    let counter = Date.now();
    const newPx = clipboard.map(p => ({
      ...p, id: `px_paste_${counter++}`, x: p.x + offset, y: p.y + offset, isAuto: false
    }));
    const allPixels = [...pixels, ...newPx];
    const order = autoSnakeWiring(allPixels, wiringDirection);
    const wired = assignPorts(allPixels, order);
    setPixels(wired);
    setWiringOrder(order);
    setSelectedIds(new Set(newPx.map(p => p.id)));
  }, [clipboard, pixels, wiringDirection]);

  const handleDelete = useCallback(() => {
    if (!selectedIds.size) return;
    const remaining = pixels.filter(p => !selectedIds.has(p.id));
    const newOrder = wiringOrder.filter(id => !selectedIds.has(id));
    const rewired = assignPorts(remaining, newOrder);
    setPixels(rewired);
    setWiringOrder(newOrder);
    setSelectedIds(new Set());
  }, [selectedIds, pixels, wiringOrder]);

  // ── Export ───────────────────────────────────────────────────────────────
  const handleExport = useCallback(() => {
    if (!pixels.length) return;
    const settings = { pixelOdMm, text, fontSizeMm };
    if (exportFormat === 'dxf') {
      const dxf = generateDXF(pixels, wiringOrder, settings);
      downloadFile(dxf, `led_design_${text.replace(/\s+/g, '_')}.dxf`, 'application/dxf');
    } else {
      const cjb = generateCJB(pixels, wiringOrder, settings);
      downloadFile(cjb, `led_design_${text.replace(/\s+/g, '_')}.cjb`, 'application/xml');
    }
  }, [pixels, wiringOrder, exportFormat, pixelOdMm, text, fontSizeMm]);

  return (
    <div className="app-layout">
      <ToolPanel
        font={font}
        fontName={fontName}
        onFontLoad={handleFontLoad}
        text={text}
        onTextChange={setText}
        fontSizeMm={fontSizeMm}
        onFontSizeChange={setFontSizeMm}
        letterSpacingMm={letterSpacingMm}
        onLetterSpacingChange={setLetterSpacingMm}
        mode={mode}
        onModeChange={setMode}
        fillSpacingMm={fillSpacingMm}
        onFillSpacingChange={setFillSpacingMm}
        borderSpacingMm={borderSpacingMm}
        onBorderSpacingChange={setBorderSpacingMm}
        borderPixelCount={borderPixelCount}
        onBorderPixelCountChange={setBorderPixelCount}
        pixelOdMm={pixelOdMm}
        onPixelOdChange={setPixelOdMm}
        activeTool={activeTool}
        onToolChange={setActiveTool}
        showNumbers={showNumbers}
        onShowNumbersChange={setShowNumbers}
        showWiring={showWiring}
        onShowWiringChange={setShowWiring}
        showGuide={showGuide}
        onShowGuideChange={setShowGuide}
        isGenerating={isGenerating}
        onGenerate={handleGenerate}
        onDelete={handleDelete}
        onCopy={handleCopy}
        onPaste={handlePaste}
        clipboardCount={clipboard.length}
        selectedCount={selectedIds.size}
      />

      <div className="canvas-area">
        {/* Toolbar */}
        <div className="canvas-toolbar">
          <span style={{ fontSize: 11, color: 'var(--muted)' }}>
            Scroll = Zoom &nbsp;|&nbsp; Middle-drag or Pan tool = Move
          </span>
          <span style={{ marginLeft: 'auto', fontSize: 11, color: 'var(--muted)' }}>
            {pixels.length > 0 && (
              <span>
                <span style={{ color: 'var(--accent2)' }}>{pixels.length}</span> pixels &nbsp;|&nbsp;
                Wiring: <span style={{ color: 'var(--accent)' }}>{wiringMode}</span>
              </span>
            )}
          </span>
        </div>

        <LedCanvas
          pixels={pixels}
          wiringOrder={wiringOrder}
          guideCommands={guideCommands}
          selectedIds={selectedIds}
          activeTool={activeTool}
          pixelOdMm={pixelOdMm}
          showNumbers={showNumbers}
          showWiring={showWiring}
          showGuide={showGuide}
          wiringMode={wiringMode}
          pendingWire={pendingWire}
          onPixelMove={handlePixelMove}
          onPixelSelect={handlePixelSelect}
          onWireClick={handleWireClick}
        />

        {/* Status bar */}
        <div className="canvas-statusbar">
          <span>Selected: <span className="status-val">{selectedIds.size}</span></span>
          <span>Border: <span className="status-val">{pixels.filter(p=>p.type==='border').length}</span></span>
          <span>Fill: <span className="status-val">{pixels.filter(p=>p.type==='fill').length}</span></span>
          {pendingWire.length > 0 && (
            <span style={{ color: 'var(--accent)' }}>
              Wiring chain: <span className="status-val">{pendingWire.length}</span> px
            </span>
          )}
        </div>
      </div>

      <ExportPanel
        pixels={pixels}
        wiringOrder={wiringOrder}
        pendingWire={pendingWire}
        wiringMode={wiringMode}
        onWiringModeChange={setWiringMode}
        wiringDirection={wiringDirection}
        onWiringDirectionChange={setWiringDirection}
        onReWire={handleReWire}
        onApplyWire={handleApplyWire}
        onClearWire={() => setPendingWire([])}
        exportFormat={exportFormat}
        onExportFormatChange={setExportFormat}
        onExport={handleExport}
      />
    </div>
  );
}
