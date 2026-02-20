import React, { useCallback } from 'react';
import { MousePointer, Move, Zap, Layers, Settings, Copy, Trash2, Clipboard, Scissors, RefreshCw, Unlink, Link } from 'lucide-react';

// Font size options in cm (30-300cm)
const FONT_SIZE_OPTIONS = [
  30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150,
  160, 170, 180, 190, 200, 220, 240, 260, 280, 300
];

export default function ToolPanel({
  font, fontName, onFontLoad,
  text, onTextChange,
  fontSizeCm, onFontSizeCmChange,
  letterSpacingCm, onLetterSpacingCmChange,
  mode, onModeChange,
  fillSpacingMm, onFillSpacingChange,
  borderSpacingMm, onBorderSpacingChange,
  borderPixelCount, onBorderPixelCountChange,
  pixelOdMm, onPixelOdChange,
  edgeMarginMm, onEdgeMarginChange,
  activeTool, onToolChange,
  showNumbers, onShowNumbersChange,
  showWiring, onShowWiringChange,
  showGuide, onShowGuideChange,
  isGenerating, onGenerate,
  onDelete, onCopy, onPaste, onBreakWiring, onRestoreWiring,
  isBreakApart, onBreakApartToggle,
  clipboardCount, selectedCount, pixelCount
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
    if (file && (file.name.endsWith('.ttf') || file.name.endsWith('.otf'))) loadFontFile(file);
  }, [loadFontFile]);

  return (
    <div className="left-panel">
      {/* ── Font Upload ────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Font</div>
        <div className="upload-zone"
          onClick={() => document.getElementById('font-input').click()}
          onDrop={handleDrop}
          onDragOver={e => { e.preventDefault(); e.currentTarget.classList.add('dragover'); }}
          onDragLeave={e => e.currentTarget.classList.remove('dragover')}
          data-testid="font-upload-zone">
          {font ? (
            <><div style={{ color: 'var(--accent2)', fontSize: 18 }}>✓</div>
              <div className="font-name">{fontName}</div>
              <small style={{ fontSize: 10, color: 'var(--muted)' }}>Click to change</small></>
          ) : (
            <><Layers size={22} style={{ margin: '0 auto 4px' }} />
              <div>Drop .ttf / .otf</div><small>or click to browse</small></>
          )}
        </div>
        <input id="font-input" type="file" accept=".ttf,.otf" style={{ display: 'none' }}
          onChange={e => { const f = e.target.files[0]; if (f) loadFontFile(f); }}
          data-testid="font-file-input" />
      </div>

      {/* ── Text ─────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Text</div>
        <input type="text" value={text} onChange={e => onTextChange(e.target.value)}
          placeholder="Enter text…" data-testid="text-input"
          style={{ fontWeight: 600, fontSize: 14, letterSpacing: 2, marginBottom: 10 }} />

        {/* Font size dropdown */}
        <div className="form-row">
          <span className="form-label">Letter height</span>
        </div>
        <select value={fontSizeCm} onChange={e => onFontSizeCmChange(+e.target.value)}
          data-testid="font-size-select" style={{ marginBottom: 8 }}>
          {FONT_SIZE_OPTIONS.map(s => (
            <option key={s} value={s}>{s} cm ({s * 10} mm)</option>
          ))}
        </select>

        {/* Letter spacing */}
        <div className="form-row">
          <span className="form-label">Letter spacing (cm)</span>
          <span className="form-value">{letterSpacingCm} cm</span>
        </div>
        <input type="range" min={0} max={30} step={0.5}
          value={letterSpacingCm} onChange={e => onLetterSpacingCmChange(+e.target.value)}
          data-testid="letter-spacing-slider" />
      </div>

      {/* ── Mode ─────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Mode</div>
        <div className="tabs" style={{ marginBottom: 10 }}>
          {['fill', 'border', 'both'].map(m => (
            <button key={m} className={`tab ${mode === m ? 'active' : ''}`}
              onClick={() => onModeChange(m)} data-testid={`mode-tab-${m}`}>
              {m.charAt(0).toUpperCase() + m.slice(1)}
            </button>
          ))}
        </div>

        {(mode === 'fill' || mode === 'both') && (
          <>
            <div className="form-row">
              <span className="form-label">Fill spacing (mm)</span>
              <span className="form-value">{fillSpacingMm}</span>
            </div>
            <input type="range" min={5} max={100} step={1}
              value={fillSpacingMm} onChange={e => onFillSpacingChange(+e.target.value)}
              data-testid="fill-spacing-slider" style={{ marginBottom: 8 }} />
          </>
        )}

        {(mode === 'border' || mode === 'both') && (
          <>
            <div className="form-row">
              <span className="form-label">Border spacing (mm)</span>
              <span className="form-value">{borderSpacingMm}</span>
            </div>
            <input type="range" min={3} max={100} step={1}
              value={borderSpacingMm} onChange={e => onBorderSpacingChange(+e.target.value)}
              data-testid="border-spacing-slider" style={{ marginBottom: 6 }} />
            <div className="form-row" style={{ marginTop: 4 }}>
              <span className="form-label">Border count</span>
              <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                <button className="btn btn-secondary"
                  style={{ padding: '3px 8px', fontSize: 11,
                    ...(borderPixelCount === 'auto' ? { borderColor: 'var(--accent)', color: 'var(--accent)' } : {}) }}
                  onClick={() => onBorderPixelCountChange('auto')} data-testid="border-count-auto">
                  Auto
                </button>
                <input type="number" min={4} max={9999} step={1}
                  value={borderPixelCount === 'auto' ? '' : borderPixelCount}
                  placeholder="Count" onChange={e => onBorderPixelCountChange(e.target.value || 'auto')}
                  data-testid="border-count-input" style={{ width: 60, fontSize: 11 }} />
              </div>
            </div>
          </>
        )}
      </div>

      {/* ── Pixel & Margin ───────────────────── */}
      <div className="panel-section">
        <div className="section-title">Pixel & Safety</div>
        <div className="form-row">
          <span className="form-label">Pixel OD (mm)</span>
          <span className="form-value">{pixelOdMm}</span>
        </div>
        <input type="range" min={5} max={30} step={1}
          value={pixelOdMm} onChange={e => onPixelOdChange(+e.target.value)}
          data-testid="pixel-od-slider" style={{ marginBottom: 8 }} />

        <div className="form-row">
          <span className="form-label">Edge margin (mm)</span>
          <span className="form-value" style={{ color: 'var(--warning)' }}>{edgeMarginMm}</span>
        </div>
        <input type="range" min={0} max={10} step={0.5}
          value={edgeMarginMm} onChange={e => onEdgeMarginChange(+e.target.value)}
          data-testid="edge-margin-slider" />
        <div className="tooltip-hint">3–5 mm recommended to prevent pixel bleed &amp; allow mechanical fitting</div>
      </div>

      {/* ── Tools ────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Tools</div>
        <div style={{ display: 'flex', gap: 4, marginBottom: 6 }}>
          {[
            { id: 'select', icon: <MousePointer size={13}/>, label: 'Select / Drag' },
            { id: 'pan',    icon: <Move size={13}/>,         label: 'Pan canvas' },
            { id: 'wire',   icon: <Zap size={13}/>,          label: 'Click-wire' },
          ].map(t => (
            <button key={t.id}
              className={`btn btn-icon ${activeTool === t.id ? 'active' : ''}`}
              style={{ flex: 1, width: 'auto' }}
              onClick={() => onToolChange(t.id)} title={t.label}
              data-testid={`tool-${t.id}`}>
              {t.icon}
            </button>
          ))}
        </div>

        {/* Edit actions */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 3, marginBottom: 4 }}>
          <button className="btn btn-secondary" style={{ fontSize: 11, padding: '5px 4px' }}
            onClick={onCopy} disabled={!selectedCount} title="Copy selected" data-testid="btn-copy">
            <Copy size={11}/> Copy
          </button>
          <button className="btn btn-secondary" style={{ fontSize: 11, padding: '5px 4px' }}
            onClick={onPaste} disabled={!clipboardCount} title="Paste" data-testid="btn-paste">
            <Clipboard size={11}/> Paste
          </button>
          <button className="btn btn-danger" style={{ fontSize: 11, padding: '5px 4px' }}
            onClick={onDelete} disabled={!selectedCount} title="Delete" data-testid="btn-delete">
            <Trash2 size={11}/> Del
          </button>
        </div>

        {/* Break / Restore wiring */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 3 }}>
          <button className="btn btn-danger" style={{ fontSize: 11, padding: '5px 4px' }}
            onClick={onBreakWiring} disabled={!selectedCount} title="Break wiring connection"
            data-testid="btn-break-wiring">
            <Unlink size={11}/> Break Wire
          </button>
          <button className="btn btn-secondary" style={{ fontSize: 11, padding: '5px 4px' }}
            onClick={onRestoreWiring} disabled={!selectedCount} title="Restore wiring"
            data-testid="btn-restore-wiring">
            <Link size={11}/> Restore
          </button>
        </div>
      </div>

      {/* ── Break Apart ──────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Letter Layout</div>
        <button
          className={`btn btn-full ${isBreakApart ? 'btn-primary' : 'btn-secondary'}`}
          onClick={onBreakApartToggle}
          disabled={!pixelCount || text.length < 2}
          data-testid="btn-break-apart"
          style={{ marginBottom: 6, fontSize: 12 }}>
          <Scissors size={13}/>
          {isBreakApart ? 'Exit Break-Apart' : 'Break Apart Letters'}
        </button>
        {isBreakApart && (
          <div className="tooltip-hint">
            Click a letter's pixels to select & zoom it independently
          </div>
        )}
      </div>

      {/* ── Display ──────────────────────────── */}
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

      {/* ── Generate ─────────────────────────── */}
      <div className="panel-section">
        <button className="btn-generate" disabled={!font || !text.trim() || isGenerating}
          onClick={onGenerate} data-testid="btn-generate">
          {isGenerating
            ? <><Settings size={13} className="spin" /> Generating…</>
            : <>Generate Pixels &nbsp; {font && text ? `(${fontSizeCm}cm)` : ''}</>}
        </button>
        {!font && <div className="tooltip-hint" style={{ marginTop: 6, textAlign: 'center' }}>Upload a font to begin</div>}
      </div>
    </div>
  );
}
