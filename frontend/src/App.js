import React, { useState, useCallback, useRef, useMemo } from 'react';
import './App.css';
import MenuBar from './components/MenuBar';
import Toolbar from './components/Toolbar';
import PropertiesPanel from './components/PropertiesPanel';
import LedCanvas from './components/LedCanvas';
import PortsPanel from './components/PortsPanel';
import StatusPanel from './components/StatusPanel';
import { parseFont, getGlyphPathMm, getAdvanceWidth } from './utils/fontParser';
import { generatePixelsForText, buildPixelObjects } from './utils/pixelUtils';
import { autoSnakeWiringPerLetter, getPortStats, buildInitialPortNodes } from './utils/wireUtils';
import { generateDXF, generateCJB, downloadFile } from './utils/exportUtils';
import { verifyLedEditLayout } from './utils/verificationUtils';

const MAX_HISTORY = 10;

export default function App() {
  // ── Font & Text ──────────────────────────────────────────────────────────
  const [font, setFont] = useState(null);
  const [fontName, setFontName] = useState('');
  const [text, setText] = useState('HELLO');
  const [fontSizeCm, setFontSizeCm] = useState(100);
  const [letterSpacingCm, setLetterSpacingCm] = useState(0.5);

  // ── Mode & Spacing ───────────────────────────────────────────────────────
  const [mode, setMode] = useState('both');
  const [fillSpacingMm, setFillSpacingMm] = useState(15);
  const [borderSpacingMm, setBorderSpacingMm] = useState(12);
  const [borderPixelCount, setBorderPixelCount] = useState('auto');
  const [pixelOdMm, setPixelOdMm] = useState(12);
  const [edgeMarginMm, setEdgeMarginMm] = useState(3);

  // ── Canvas Data ──────────────────────────────────────────────────────────
  const [pixels, setPixels] = useState([]);
  const [guideCommands, setGuideCommands] = useState([]);
  const [wiringOrder, setWiringOrder] = useState([]);

  // ── Wiring ───────────────────────────────────────────────────────────────
  const [wiringDirection, setWiringDirection] = useState('ltr-ttb');

  // ── Tools & UI ───────────────────────────────────────────────────────────
  const [activeTool, setActiveTool] = useState('select');
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [clipboard, setClipboard] = useState([]);
  const [showNumbers, setShowNumbers] = useState(true);
  const [showWiring, setShowWiring] = useState(true);
  const [showGuide, setShowGuide] = useState(true);
  const [showPorts, setShowPorts] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);

  // ── Port Nodes ───────────────────────────────────────────────────────────
  const [portNodes, setPortNodes] = useState(buildInitialPortNodes());
  const [letterPortMap, setLetterPortMap] = useState({});
  const [visiblePorts, setVisiblePorts] = useState(new Set());
  const [selectedPortIndex, setSelectedPortIndex] = useState(null);
  const [activePort, setActivePort] = useState(0); // Currently active port in dropdown

  // ── Wire Connect Tool ────────────────────────────────────────────────────
  const [wireConnectStart, setWireConnectStart] = useState(null);
  const [manualWires, setManualWires] = useState([]);

  // ── Undo History ─────────────────────────────────────────────────────────
  const [history, setHistory] = useState([]);
  const [historyIndex, setHistoryIndex] = useState(-1);

  // ── Context Menu ─────────────────────────────────────────────────────────
  const [contextMenu, setContextMenu] = useState(null);

  // ── Panel States ─────────────────────────────────────────────────────────
  const [propertiesCollapsed, setPropertiesCollapsed] = useState(false);
  const [portsCollapsed, setPortsCollapsed] = useState(false);

  // ── Export ───────────────────────────────────────────────────────────────
  const [exportFormat, setExportFormat] = useState('dxf');
  const [verifyStatus, setVerifyStatus] = useState(null);

  const canvasRef = useRef(null);

  // Convert cm → mm
  const fontSizeMm = fontSizeCm * 10;
  const letterSpacingMm = letterSpacingCm * 10;

  // Computed values
  const portStats = useMemo(() => getPortStats(pixels), [pixels]);
  const selectedCount = selectedIds.size;
  const canCopy = selectedCount > 0;
  const canPaste = selectedCount >= 2; // Need 2+ pixels to paste between
  const canDelete = selectedCount > 0;
  const canBreakWire = selectedCount === 1;
  const canJoinWire = selectedCount >= 2;
  const canGenerate = font && text.trim().length > 0;

  // ── Save to History ──────────────────────────────────────────────────────
  const saveToHistory = useCallback((newPixels, newWiringOrder) => {
    const snapshot = {
      pixels: JSON.parse(JSON.stringify(newPixels)),
      wiringOrder: [...newWiringOrder],
      selectedIds: new Set()
    };
    setHistory(prev => {
      const newHistory = prev.slice(0, historyIndex + 1);
      newHistory.push(snapshot);
      if (newHistory.length > MAX_HISTORY) newHistory.shift();
      return newHistory;
    });
    setHistoryIndex(prev => Math.min(prev + 1, MAX_HISTORY - 1));
  }, [historyIndex]);

  // ── Undo ─────────────────────────────────────────────────────────────────
  const handleUndo = useCallback(() => {
    if (historyIndex <= 0) return;
    const newIndex = historyIndex - 1;
    const snapshot = history[newIndex];
    if (!snapshot) return;
    setPixels(snapshot.pixels);
    setWiringOrder(snapshot.wiringOrder);
    setSelectedIds(new Set());
    setHistoryIndex(newIndex);
  }, [history, historyIndex]);

  // ── Font Load ────────────────────────────────────────────────────────────
  const handleFontLoad = useCallback((arrayBuffer, name) => {
    try {
      setFont(parseFont(arrayBuffer));
      setFontName(name);
    } catch (e) {
      alert('Font parse error: ' + e.message);
    }
  }, []);

  // ── Generate ─────────────────────────────────────────────────────────────
  const handleGenerate = useCallback(async () => {
    if (!font || !text.trim()) return;
    setIsGenerating(true);
    try {
      await new Promise(r => setTimeout(r, 10));
      const settings = { fontSizeMm, letterSpacingMm, mode, borderSpacingMm, borderPixelCount, fillSpacingMm, edgeMarginMm, pixelOdMm };
      const raw = generatePixelsForText(font, text, settings);
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

      const { wiredPixels, wiringOrder: order } = autoSnakeWiringPerLetter(base, wiringDirection, edgeMarginMm);
      setPixels(wiredPixels);
      setWiringOrder(order);
      setSelectedIds(new Set());
      setManualWires([]);
      setPortNodes(buildInitialPortNodes());
      saveToHistory(wiredPixels, order);
    } catch (e) {
      console.error(e);
      alert('Generate error: ' + e.message);
    } finally {
      setIsGenerating(false);
    }
  }, [font, text, fontSizeMm, letterSpacingMm, mode, borderSpacingMm, borderPixelCount, fillSpacingMm, edgeMarginMm, pixelOdMm, wiringDirection, saveToHistory]);

  // ── Re-Wire ──────────────────────────────────────────────────────────────
  const handleReWire = useCallback(() => {
    if (!pixels.length) return;
    const { wiredPixels, wiringOrder: order } = autoSnakeWiringPerLetter(pixels, wiringDirection, edgeMarginMm);
    setPixels(wiredPixels);
    setWiringOrder(order);
    saveToHistory(wiredPixels, order);
  }, [pixels, wiringDirection, edgeMarginMm, saveToHistory]);

  // ── Pixel Select ─────────────────────────────────────────────────────────
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

  // ── Pixel Move ───────────────────────────────────────────────────────────
  const handlePixelMove = useCallback((id, newX, newY) => {
    setPixels(prev => prev.map(p => p.id === id ? { ...p, x: newX, y: newY } : p));
  }, []);

  // ── Copy ─────────────────────────────────────────────────────────────────
  const handleCopy = useCallback(() => {
    setClipboard(pixels.filter(p => selectedIds.has(p.id)));
  }, [pixels, selectedIds]);

  // ── Paste (between selected pixels following wiring path) ────────────────
  const handlePaste = useCallback(() => {
    if (selectedIds.size < 2) return;
    
    // Get selected pixels in wiring order
    const selectedInOrder = wiringOrder
      .filter(id => selectedIds.has(id))
      .map(id => pixels.find(p => p.id === id))
      .filter(Boolean);
    
    if (selectedInOrder.length < 2) return;
    
    // Add one pixel between each consecutive pair
    let counter = Date.now();
    const newPixels = [];
    
    for (let i = 0; i < selectedInOrder.length - 1; i++) {
      const p1 = selectedInOrder[i];
      const p2 = selectedInOrder[i + 1];
      
      // Calculate midpoint
      const midX = (p1.x + p2.x) / 2;
      const midY = (p1.y + p2.y) / 2;
      
      const newPx = {
        id: `px_paste_${counter++}`,
        x: midX, y: midY,
        type: p1.type, // Same type as first pixel
        letter: p1.letter,
        letterIndex: p1.letterIndex,
        portIndex: -1,
        portPixelIndex: -1,
        borderOrder: -1,
        fillOrder: -1,
        isBorderFirst: false, isBorderLast: false,
        isFillFirst: false, isFillLast: false
      };
      newPixels.push(newPx);
    }
    
    if (newPixels.length === 0) return;
    
    // Insert new pixels into the array and rewire
    const allPixels = [...pixels, ...newPixels];
    const { wiredPixels, wiringOrder: order } = autoSnakeWiringPerLetter(allPixels, wiringDirection, edgeMarginMm);
    setPixels(wiredPixels);
    setWiringOrder(order);
    setSelectedIds(new Set(newPixels.map(p => p.id)));
    saveToHistory(wiredPixels, order);
  }, [selectedIds, wiringOrder, pixels, wiringDirection, edgeMarginMm, saveToHistory]);

  // ── Delete ───────────────────────────────────────────────────────────────
  const handleDelete = useCallback(() => {
    if (!selectedIds.size) return;
    const remaining = pixels.filter(p => !selectedIds.has(p.id));
    const { wiredPixels, wiringOrder: order } = autoSnakeWiringPerLetter(remaining, wiringDirection, edgeMarginMm);
    setPixels(wiredPixels);
    setWiringOrder(order);
    setSelectedIds(new Set());
    saveToHistory(wiredPixels, order);
  }, [selectedIds, pixels, wiringDirection, edgeMarginMm, saveToHistory]);

  // ── Break Wire (disconnect selected pixel from next) ─────────────────────
  const handleBreakWire = useCallback(() => {
    if (selectedIds.size !== 1) return;
    const selectedId = [...selectedIds][0];
    setPixels(prev => prev.map(p => 
      p.id === selectedId ? { ...p, wiringBroken: true } : p
    ));
  }, [selectedIds]);

  // ── Join Wire (connect selected pixels, renumber) ────────────────────────
  const handleJoinWire = useCallback(() => {
    if (selectedIds.size < 2) return;
    
    // Get selected pixels and add connections between them
    const selectedPixels = wiringOrder
      .filter(id => selectedIds.has(id))
      .map(id => pixels.find(p => p.id === id))
      .filter(Boolean);
    
    if (selectedPixels.length < 2) return;
    
    // Clear broken wiring for selected pixels
    const updatedPixels = pixels.map(p => 
      selectedIds.has(p.id) ? { ...p, wiringBroken: false } : p
    );
    
    // Rewire to renumber
    const { wiredPixels, wiringOrder: order } = autoSnakeWiringPerLetter(updatedPixels, wiringDirection, edgeMarginMm);
    setPixels(wiredPixels);
    setWiringOrder(order);
    saveToHistory(wiredPixels, order);
  }, [selectedIds, wiringOrder, pixels, wiringDirection, edgeMarginMm, saveToHistory]);

  // ── Wire Connect Tool ────────────────────────────────────────────────────
  const handleWireConnectClick = useCallback((pixelId) => {
    if (wireConnectStart === null) {
      setWireConnectStart(pixelId);
    } else {
      if (wireConnectStart !== pixelId) {
        setManualWires(prev => [...prev, { from: wireConnectStart, to: pixelId }]);
      }
      setWireConnectStart(null);
    }
  }, [wireConnectStart]);

  // ── Context Menu (Right-click) ───────────────────────────────────────────
  const handleContextMenu = useCallback((e, pixelId) => {
    e.preventDefault();
    const menuOptions = [];
    
    // Add Pixels option (requires 2+ selected)
    if (selectedIds.size >= 2) {
      menuOptions.push({ type: 'addPixels' });
    }
    
    // Connect Wire option (requires 2+ selected)
    if (selectedIds.size >= 2) {
      menuOptions.push({ type: 'connectWire' });
    }
    
    // Disconnect Wire option (requires 1+ selected)
    if (selectedIds.size >= 1) {
      menuOptions.push({ type: 'disconnectWire' });
    }
    
    if (menuOptions.length > 0) {
      setContextMenu({
        x: e.clientX,
        y: e.clientY,
        options: menuOptions
      });
    }
  }, [selectedIds]);

  // ── Connect Wire (from context menu) ──────────────────────────────────────
  const handleConnectWire = useCallback(() => {
    if (selectedIds.size < 2) return;
    
    // Clear broken wiring for selected pixels and rewire
    const updatedPixels = pixels.map(p => 
      selectedIds.has(p.id) ? { ...p, wiringBroken: false } : p
    );
    
    const { wiredPixels, wiringOrder: order } = autoSnakeWiringPerLetter(updatedPixels, wiringDirection, edgeMarginMm);
    setPixels(wiredPixels);
    setWiringOrder(order);
    saveToHistory(wiredPixels, order);
    setContextMenu(null);
  }, [selectedIds, pixels, wiringDirection, edgeMarginMm, saveToHistory]);

  // ── Disconnect Wire (from context menu) ───────────────────────────────────
  const handleDisconnectWire = useCallback(() => {
    if (selectedIds.size < 1) return;
    
    // Mark selected pixels as having broken wiring
    setPixels(prev => prev.map(p => 
      selectedIds.has(p.id) ? { ...p, wiringBroken: true } : p
    ));
    setContextMenu(null);
  }, [selectedIds]);

  // ── Add Multiple Pixels (from context menu) ──────────────────────────────
  const handleAddMultiplePixels = useCallback((count) => {
    if (selectedIds.size < 2 || count < 1) return;
    
    const selectedInOrder = wiringOrder
      .filter(id => selectedIds.has(id))
      .map(id => pixels.find(p => p.id === id))
      .filter(Boolean);
    
    if (selectedInOrder.length < 2) return;
    
    let counter = Date.now();
    const newPixels = [];
    
    // Add 'count' pixels between each consecutive pair
    for (let i = 0; i < selectedInOrder.length - 1; i++) {
      const p1 = selectedInOrder[i];
      const p2 = selectedInOrder[i + 1];
      
      for (let j = 1; j <= count; j++) {
        const t = j / (count + 1);
        const x = p1.x + (p2.x - p1.x) * t;
        const y = p1.y + (p2.y - p1.y) * t;
        
        newPixels.push({
          id: `px_add_${counter++}`,
          x, y,
          type: p1.type,
          letter: p1.letter,
          letterIndex: p1.letterIndex,
          portIndex: -1,
          portPixelIndex: -1,
          borderOrder: -1,
          fillOrder: -1,
          isBorderFirst: false, isBorderLast: false,
          isFillFirst: false, isFillLast: false
        });
      }
    }
    
    const allPixels = [...pixels, ...newPixels];
    const { wiredPixels, wiringOrder: order } = autoSnakeWiringPerLetter(allPixels, wiringDirection, edgeMarginMm);
    setPixels(wiredPixels);
    setWiringOrder(order);
    setSelectedIds(new Set(newPixels.map(p => p.id)));
    saveToHistory(wiredPixels, order);
    setContextMenu(null);
  }, [selectedIds, wiringOrder, pixels, wiringDirection, edgeMarginMm, saveToHistory]);

  // ── Port Selection (from dropdown) ─────────────────────────────────────────
  const handleActivePortChange = useCallback((portIndex) => {
    setActivePort(portIndex);
    // Automatically make port visible when selected
    setVisiblePorts(prev => {
      const next = new Set(prev);
      next.add(portIndex);
      return next;
    });
    setSelectedPortIndex(portIndex);
  }, []);

  // ── Port Selection (from panel - toggle visibility) ───────────────────────
  const handleSelectPort = useCallback((portIndex) => {
    setVisiblePorts(prev => {
      const next = new Set(prev);
      if (next.has(portIndex)) {
        next.delete(portIndex);
        setSelectedPortIndex(null);
      } else {
        next.add(portIndex);
        setSelectedPortIndex(portIndex);
      }
      return next;
    });
  }, []);

  // ── Port Node Move ───────────────────────────────────────────────────────
  const handlePortNodeMove = useCallback((portIndex, x, y) => {
    setPortNodes(prev => prev.map(p => 
      p.portIndex === portIndex ? { ...p, x, y } : p
    ));
  }, []);

  // ── Connect Port to Letter ───────────────────────────────────────────────
  const handleConnectPortToLetter = useCallback((portIndex, letterIndex, pixelType) => {
    const key = `${letterIndex}_${pixelType}`;
    setLetterPortMap(prev => ({ ...prev, [key]: portIndex }));
    setSelectedPortIndex(null);
  }, []);

  // ── Export ───────────────────────────────────────────────────────────────
  const handleVerifyLedEdit = useCallback(() => {
    const result = verifyLedEditLayout(pixels, wiringOrder, letterPortMap);
    setVerifyStatus(result);

    if (result.ok) {
      alert(`✅ ${result.summary}`);
    } else {
      const topErrors = result.errors.slice(0, 6).map((e, i) => `${i + 1}. ${e}`).join('\n');
      alert(`❌ ${result.summary}\n\n${topErrors}`);
    }
    return result;
  }, [pixels, wiringOrder, letterPortMap]);

  const handleExport = useCallback((formatOverride = null) => {
    if (!pixels.length) return;
    const verifyResult = verifyLedEditLayout(pixels, wiringOrder, letterPortMap);
    setVerifyStatus(verifyResult);
    if (!verifyResult.ok) {
      const topErrors = verifyResult.errors.slice(0, 6).map((e, i) => `${i + 1}. ${e}`).join('\n');
      alert(`Cannot export until verification passes.\n\n${topErrors}`);
      return;
    }

    const targetFormat = formatOverride || exportFormat;
    const exportPixels = verifyResult.exportPixels || pixels;
    const s = { pixelOdMm, text, fontSizeMm };
    if (targetFormat === 'dxf') {
      downloadFile(generateDXF(exportPixels, wiringOrder, s), `led_${text.replace(/\s+/g, '_')}.dxf`, 'application/dxf');
    } else {
      downloadFile(generateCJB(exportPixels, wiringOrder, s), `led_${text.replace(/\s+/g, '_')}.cjb`, 'application/xml');
    }
  }, [pixels, wiringOrder, letterPortMap, exportFormat, pixelOdMm, text, fontSizeMm]);

  // Close context menu on click outside
  const handleCloseContextMenu = useCallback(() => {
    setContextMenu(null);
  }, []);

  return (
    <div className="app-container" onClick={handleCloseContextMenu}>
      {/* Menu Bar */}
      <MenuBar
        onLoadFont={handleFontLoad}
        mode={mode}
        onModeChange={setMode}
        onExportDXF={() => { setExportFormat('dxf'); handleExport('dxf'); }}
        onExportCJB={() => { setExportFormat('cjb'); handleExport('cjb'); }}
        canUndo={historyIndex > 0}
        onUndo={handleUndo}
        canCopy={canCopy}
        onCopy={handleCopy}
        canPaste={canPaste}
        onPaste={handlePaste}
        canDelete={canDelete}
        onDelete={handleDelete}
        showGuide={showGuide}
        onShowGuideChange={setShowGuide}
        showWiring={showWiring}
        onShowWiringChange={setShowWiring}
        showNumbers={showNumbers}
        onShowNumbersChange={setShowNumbers}
        showPorts={showPorts}
        onShowPortsChange={setShowPorts}
        activeTool={activeTool}
        onToolChange={setActiveTool}
        fontName={fontName}
        text={text}
        onTextChange={setText}
        canGenerate={canGenerate}
        onGenerate={handleGenerate}
        isGenerating={isGenerating}
        activePort={activePort}
        onActivePortChange={handleActivePortChange}
        portStats={portStats}
      />

      {/* Main Content */}
      <div className="main-content">
        {/* Left Toolbar */}
        <Toolbar
          activeTool={activeTool}
          onToolChange={setActiveTool}
          canUndo={historyIndex > 0}
          onUndo={handleUndo}
          canCopy={canCopy}
          onCopy={handleCopy}
          canPaste={canPaste}
          onPaste={handlePaste}
          canDelete={canDelete}
          onDelete={handleDelete}
          canBreakWire={canBreakWire}
          onBreakWire={handleBreakWire}
          canJoinWire={canJoinWire}
          onJoinWire={handleJoinWire}
          onReWire={handleReWire}
          wireConnectStart={wireConnectStart}
        />

        {/* Properties Panel */}
        <PropertiesPanel
          fontSizeCm={fontSizeCm}
          onFontSizeCmChange={setFontSizeCm}
          letterSpacingCm={letterSpacingCm}
          onLetterSpacingCmChange={setLetterSpacingCm}
          fillSpacingMm={fillSpacingMm}
          onFillSpacingChange={setFillSpacingMm}
          borderSpacingMm={borderSpacingMm}
          onBorderSpacingChange={setBorderSpacingMm}
          borderPixelCount={borderPixelCount}
          onBorderPixelCountChange={setBorderPixelCount}
          pixelOdMm={pixelOdMm}
          onPixelOdChange={setPixelOdMm}
          edgeMarginMm={edgeMarginMm}
          onEdgeMarginChange={setEdgeMarginMm}
          wiringDirection={wiringDirection}
          onWiringDirectionChange={setWiringDirection}
          isCollapsed={propertiesCollapsed}
          onToggleCollapse={() => setPropertiesCollapsed(!propertiesCollapsed)}
        />

        {/* Canvas */}
        <div className="canvas-wrapper">
          <div className="canvas-container">
            <LedCanvas
              ref={canvasRef}
              pixels={pixels}
              wiringOrder={wiringOrder}
              guideCommands={guideCommands}
              selectedIds={selectedIds}
              activeTool={activeTool}
              pixelOdMm={pixelOdMm}
              showNumbers={showNumbers}
              showWiring={showWiring}
              showGuide={showGuide}
              onPixelMove={handlePixelMove}
              onPixelSelect={handlePixelSelect}
              onWireConnectClick={handleWireConnectClick}
              onContextMenu={handleContextMenu}
              wireConnectStart={wireConnectStart}
              portNodes={portNodes}
              onPortNodeMove={handlePortNodeMove}
              letterPortMap={letterPortMap}
              visiblePorts={visiblePorts}
              selectedPortIndex={selectedPortIndex}
              onConnectPortToLetter={handleConnectPortToLetter}
              manualWires={manualWires}
            />
          </div>
        </div>

        {/* Ports Panel */}
        <PortsPanel
          pixels={pixels}
          portNodes={portNodes}
          letterPortMap={letterPortMap}
          visiblePorts={visiblePorts}
          selectedPortIndex={selectedPortIndex}
          onSelectPort={handleSelectPort}
          text={text}
          exportFormat={exportFormat}
          onExportFormatChange={setExportFormat}
          onExport={handleExport}
          onVerify={handleVerifyLedEdit}
          verifyStatus={verifyStatus}
          isCollapsed={portsCollapsed}
          onToggleCollapse={() => setPortsCollapsed(!portsCollapsed)}
        />
      </div>

      {/* Status Panel */}
      <StatusPanel
        pixels={pixels}
        selectedCount={selectedCount}
        activeTool={activeTool}
        wireConnectStart={wireConnectStart}
        portStats={portStats}
      />

      {/* Context Menu */}
      {contextMenu && (
        <ContextMenuPopup
          x={contextMenu.x}
          y={contextMenu.y}
          options={contextMenu.options || [{ type: 'addPixels' }]}
          onAddPixels={handleAddMultiplePixels}
          onConnectWire={handleConnectWire}
          onDisconnectWire={handleDisconnectWire}
          onClose={() => setContextMenu(null)}
        />
      )}
    </div>
  );
}

// Context Menu Component
function ContextMenuPopup({ x, y, options, onAddPixels, onConnectWire, onDisconnectWire, onClose }) {
  const [count, setCount] = useState(1);
  const [showAddInput, setShowAddInput] = useState(false);
  
  const handleAddPixels = (e) => {
    e.preventDefault();
    e.stopPropagation();
    onAddPixels(count);
  };

  const hasOption = (type) => options.some(opt => opt.type === type);

  return (
    <div 
      className="context-menu" 
      style={{ left: x, top: y }}
      onClick={e => e.stopPropagation()}
    >
      {hasOption('addPixels') && !showAddInput && (
        <button 
          className="context-menu-item"
          onClick={() => setShowAddInput(true)}
          data-testid="ctx-add-pixels"
        >
          <span className="ctx-icon">+</span>
          Add Pixels Between...
        </button>
      )}
      
      {showAddInput && (
        <form onSubmit={handleAddPixels} className="context-menu-input">
          <label>Count:</label>
          <input
            type="number"
            min="1"
            max="100"
            value={count}
            onChange={(e) => setCount(Number(e.target.value))}
            autoFocus
          />
          <button type="submit">Add</button>
        </form>
      )}
      
      {hasOption('connectWire') && (
        <button 
          className="context-menu-item"
          onClick={onConnectWire}
          data-testid="ctx-connect-wire"
        >
          <span className="ctx-icon">⎯</span>
          Connect Wire
        </button>
      )}
      
      {hasOption('disconnectWire') && (
        <button 
          className="context-menu-item"
          onClick={onDisconnectWire}
          data-testid="ctx-disconnect-wire"
        >
          <span className="ctx-icon">✂</span>
          Disconnect Wire
        </button>
      )}
    </div>
  );
}
