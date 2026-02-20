import React, { useCallback } from 'react';
import { MousePointer, Move, Zap, Layers, Settings, Copy, Trash2, Clipboard } from 'lucide-react';

export default function ToolPanel({
  font, fontName, onFontLoad,
  text, onTextChange,
  fontSizeMm, onFontSizeChange,
  letterSpacingMm, onLetterSpacingChange,
  mode, onModeChange,
  fillSpacingMm, onFillSpacingChange,
  borderSpacingMm, onBorderSpacingChange,
  borderPixelCount, onBorderPixelCountChange,
  pixelOdMm, onPixelOdChange,
  activeTool, onToolChange,
  showNumbers, onShowNumbersChange,
  showWiring, onShowWiringChange,
  showGuide, onShowGuideChange,
  isGenerating, onGenerate,
  onDelete, onCopy, onPaste,
  clipboardCount, selectedCount
}) {

  const loadFontFile = useCallback((file) => {
    const reader = new FileReader();
    reader.onload = (e) => onFontLoad(e.target.result, file.name.replace(/\.[^.]+$/, ''));
    reader.readAsArrayBuffer(file);
  }, [onFontLoad]);

  const handleDrop = useCallback((e) => {
    e.preventDefault();
    e.currentTarget.classList.remove('dragover');
    const file = e.dataTransfer.files[0];
    if (file && (file.name.endsWith('.ttf') || file.name.endsWith('.otf'))) {
      loadFontFile(file);
    }
  }, [loadFontFile]);

  const handleFileInput = (e) => {
    const file = e.target.files[0];
    if (file) loadFontFile(file);
  };

  return (
    <div className="left-panel">
      {/* ── Font Upload ─────────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Font</div>
        <div
          className={`upload-zone ${font ? 'loaded' : ''}`}
          onClick={() => document.getElementById('font-input').click()}
          onDrop={handleDrop}
          onDragOver={(e) => { e.preventDefault(); e.currentTarget.classList.add('dragover'); }}
          onDragLeave={(e) => e.currentTarget.classList.remove('dragover')}
          data-testid="font-upload-zone"
        >
          {font ? (
            <>
              <div style={{ fontSize: 20 }}>✓</div>
              <div className="font-name">{fontName}</div>
              <small style={{ fontSize: 10, color: 'var(--muted)' }}>Click to change</small>
            </>
          ) : (
            <>
              <Layers size={24} style={{ margin: '0 auto 4px' }} />
              <div style={{ fontSize: 12 }}>Drop .ttf / .otf here</div>
              <small>or click to browse</small>
            </>
          )}
        </div>
        <input
          id="font-input"
          type="file"
          accept=".ttf,.otf"
          style={{ display: 'none' }}
          onChange={handleFileInput}
          data-testid="font-file-input"
        />
      </div>

      {/* ── Text & Size ─────────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Text</div>
        <div className="form-row" style={{ marginBottom: 8 }}>
          <input
            type="text"
            value={text}
            onChange={e => onTextChange(e.target.value)}
            placeholder="Enter text..."
            data-testid="text-input"
            style={{ fontWeight: 600, fontSize: 14, letterSpacing: 2 }}
          />
        </div>
        <div className="form-row">
          <span className="form-label">Size (mm)</span>
          <span className="form-value">{fontSizeMm}</span>
        </div>
        <input type="range" min={20} max={500} step={5}
          value={fontSizeMm} onChange={e => onFontSizeChange(+e.target.value)}
          data-testid="font-size-slider"
        />
        <div className="form-row" style={{ marginTop: 8 }}>
          <span className="form-label">Letter spacing (mm)</span>
          <span className="form-value">{letterSpacingMm}</span>
        </div>
        <input type="range" min={0} max={50} step={1}
          value={letterSpacingMm} onChange={e => onLetterSpacingChange(+e.target.value)}
          data-testid="letter-spacing-slider"
        />
      </div>

      {/* ── Mode ─────────────────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Mode</div>
        <div className="tabs" style={{ marginBottom: 10 }}>
          {['fill', 'border', 'both'].map(m => (
            <button key={m} className={`tab ${mode === m ? 'active' : ''}`}
              onClick={() => onModeChange(m)}
              data-testid={`mode-tab-${m}`}>
              {m.charAt(0).toUpperCase() + m.slice(1)}
            </button>
          ))}
        </div>

        {/* Fill spacing */}
        {(mode === 'fill' || mode === 'both') && (
          <>
            <div className="form-row">
              <span className="form-label">Fill spacing (mm)</span>
              <span className="form-value">{fillSpacingMm}</span>
            </div>
            <input type="range" min={5} max={60} step={1}
              value={fillSpacingMm} onChange={e => onFillSpacingChange(+e.target.value)}
              data-testid="fill-spacing-slider"
              style={{ marginBottom: 8 }}
            />
          </>
        )}

        {/* Border spacing */}
        {(mode === 'border' || mode === 'both') && (
          <>
            <div className="form-row">
              <span className="form-label">Border spacing (mm)</span>
              <span className="form-value">{borderSpacingMm}</span>
            </div>
            <input type="range" min={3} max={60} step={1}
              value={borderSpacingMm} onChange={e => onBorderSpacingChange(+e.target.value)}
              data-testid="border-spacing-slider"
              style={{ marginBottom: 8 }}
            />
            <div className="form-row" style={{ marginTop: 4 }}>
              <span className="form-label">Border pixel count</span>
              <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                <button className={`btn btn-secondary`}
                  style={{ padding: '3px 8px', fontSize: 11,
                    ...(borderPixelCount === 'auto' ? { borderColor: 'var(--accent)', color: 'var(--accent)' } : {}) }}
                  onClick={() => onBorderPixelCountChange('auto')}
                  data-testid="border-count-auto">
                  Auto
                </button>
                <input type="number" min={4} max={5000} step={1}
                  value={borderPixelCount === 'auto' ? '' : borderPixelCount}
                  placeholder="Count"
                  onChange={e => onBorderPixelCountChange(e.target.value || 'auto')}
                  data-testid="border-count-input"
                  style={{ width: 64, fontSize: 11 }}
                />
              </div>
            </div>
          </>
        )}
      </div>

      {/* ── Pixel OD ─────────────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Pixel</div>
        <div className="form-row">
          <span className="form-label">OD (mm)</span>
          <span className="form-value">{pixelOdMm}</span>
        </div>
        <input type="range" min={5} max={30} step={1}
          value={pixelOdMm} onChange={e => onPixelOdChange(+e.target.value)}
          data-testid="pixel-od-slider"
        />
      </div>

      {/* ── Tools ─────────────────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Tools</div>
        <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
          {[
            { id: 'select', icon: <MousePointer size={14}/>, label: 'Select' },
            { id: 'pan',    icon: <Move size={14}/>,         label: 'Pan' },
            { id: 'wire',   icon: <Zap size={14}/>,          label: 'Wire' },
          ].map(t => (
            <button key={t.id}
              className={`btn btn-secondary ${activeTool === t.id ? 'btn-icon active' : 'btn-icon'}`}
              onClick={() => onToolChange(t.id)}
              title={t.label}
              data-testid={`tool-${t.id}`}
              style={{ flex: 1, width: 'auto', height: 30 }}>
              {t.icon}
            </button>
          ))}
        </div>
        {/* Edit actions */}
        <div style={{ display: 'flex', gap: 4 }}>
          <button className="btn btn-secondary" style={{ flex: 1, fontSize: 11, padding: '5px 6px' }}
            onClick={onCopy} disabled={!selectedCount} title="Copy selected"
            data-testid="btn-copy">
            <Copy size={12}/> Copy{selectedCount > 0 ? ` (${selectedCount})` : ''}
          </button>
          <button className="btn btn-secondary" style={{ flex: 1, fontSize: 11, padding: '5px 6px' }}
            onClick={onPaste} disabled={!clipboardCount} title="Paste"
            data-testid="btn-paste">
            <Clipboard size={12}/> Paste
          </button>
          <button className="btn btn-danger" style={{ flex: 1, fontSize: 11, padding: '5px 6px' }}
            onClick={onDelete} disabled={!selectedCount} title="Delete selected"
            data-testid="btn-delete">
            <Trash2 size={12}/>
          </button>
        </div>
      </div>

      {/* ── Display Toggles ───────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Display</div>
        {[
          { label: 'Letter guide', val: showGuide, fn: onShowGuideChange, id: 'toggle-guide' },
          { label: 'Wiring path',  val: showWiring, fn: onShowWiringChange, id: 'toggle-wiring' },
          { label: 'Pixel numbers',val: showNumbers, fn: onShowNumbersChange, id: 'toggle-numbers' },
        ].map(({ label, val, fn, id }) => (
          <div key={id} className="toggle-row">
            <span className="toggle-label">{label}</span>
            <label className="toggle" data-testid={id}>
              <input type="checkbox" checked={val} onChange={e => fn(e.target.checked)} />
              <span className="toggle-track" />
            </label>
          </div>
        ))}
      </div>

      {/* ── Generate ─────────────────────────────────────────── */}
      <div className="panel-section">
        <button className="btn-generate" disabled={!font || !text.trim() || isGenerating}
          onClick={onGenerate} data-testid="btn-generate">
          {isGenerating ? (
            <><Settings size={14} className="spin" /> Generating…</>
          ) : 'Generate Pixels'}
        </button>
        {!font && <div className="tooltip-hint" style={{ marginTop: 6, textAlign: 'center' }}>Upload a font to begin</div>}
      </div>
    </div>
  );
}
