import React, { useMemo } from 'react';
import { Download, RotateCcw, CheckCircle, AlertTriangle, Plug } from 'lucide-react';
import { PORT_COLORS, getPortStats, PORT_PIXEL_LIMIT } from '../utils/wireUtils';

const WIRE_DIRECTIONS = [
  { id: 'ltr-ttb', label: 'Left→Right, Top→Bottom' },
  { id: 'rtl-ttb', label: 'Right→Left, Top→Bottom' },
  { id: 'ltr-btt', label: 'Left→Right, Bottom→Top' },
  { id: 'rtl-btt', label: 'Right→Left, Bottom→Top' },
];

export default function ExportPanel({
  pixels, wiringOrder, pendingWire,
  wiringMode, onWiringModeChange,
  wiringDirection, onWiringDirectionChange,
  onReWire, onApplyWire, onClearWire,
  exportFormat, onExportFormatChange, onExport,
  portNodes, letterPortMap, selectedPortIndex, onSelectPort, text
}) {
  const portStats = useMemo(() => getPortStats(pixels), [pixels]);
  const fillPixels   = pixels.filter(p => p.type === 'fill').length;
  const borderPixels = pixels.filter(p => p.type === 'border').length;
  const manualPixels = pixels.filter(p => !p.isAuto).length;

  const overflowWarning = portStats.totalPixels > 8 * PORT_PIXEL_LIMIT;

  return (
    <div className="right-panel">
      {/* ── Pixel Stats ─────────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Pixel Count</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--muted)' }}>Total</span>
            <span style={{ color: 'var(--accent)', fontWeight: 700 }}
              data-testid="total-pixel-count">{portStats.totalPixels}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--muted)' }}>Border</span>
            <span style={{ color: '#00d4ff' }} data-testid="border-pixel-count">{borderPixels}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--muted)' }}>Fill</span>
            <span style={{ color: '#00ff88' }} data-testid="fill-pixel-count">{fillPixels}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between' }}>
            <span style={{ color: 'var(--muted)' }}>Manual</span>
            <span style={{ color: '#ff9f43' }}>{manualPixels}</span>
          </div>
        </div>
        {overflowWarning && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 8,
            color: 'var(--warning)', fontSize: 11, background: 'rgba(255,217,61,0.08)',
            padding: '5px 8px', borderRadius: 4 }}>
            <AlertTriangle size={12}/>
            Exceeds T8000 capacity (8 × 1024)
          </div>
        )}
      </div>

      {/* ── T8000 Port Assignment ────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">T8000 Ports (8 × 1024)</div>
        {portStats.stats.map((s, i) => (
          <div key={i} className="port-bar-row" data-testid={`port-bar-${i+1}`}>
            <span className="port-label" style={{ color: PORT_COLORS[i] }}>P{i+1}</span>
            <div className="port-bar-track">
              <div className="port-bar-fill"
                style={{
                  width: `${Math.min(100, (s.count / PORT_PIXEL_LIMIT) * 100)}%`,
                  background: s.overflow ? '#ff6b6b' : PORT_COLORS[i]
                }}
              />
            </div>
            <span className="port-count"
              style={{ color: s.overflow ? 'var(--danger)' : s.count > 0 ? PORT_COLORS[i] : 'var(--muted)' }}>
              {s.count}/{PORT_PIXEL_LIMIT}
            </span>
          </div>
        ))}
      </div>

      {/* ── Wiring Mode ─────────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Wiring Mode</div>
        <div className="wire-pills">
          <div className={`wire-pill ${wiringMode === 'snake' ? 'active' : ''}`}
            onClick={() => onWiringModeChange('snake')}
            data-testid="wire-mode-snake">
            <span className="wire-pill-dot"/>
            <div>
              <div style={{ fontWeight: 500 }}>Auto Snake</div>
              <div style={{ fontSize: 10, color: 'var(--muted)' }}>Row-by-row serpentine</div>
            </div>
          </div>
          <div className={`wire-pill ${wiringMode === 'click' ? 'active' : ''}`}
            onClick={() => onWiringModeChange('click')}
            data-testid="wire-mode-click">
            <span className="wire-pill-dot"/>
            <div>
              <div style={{ fontWeight: 500 }}>Click-to-Connect</div>
              <div style={{ fontSize: 10, color: 'var(--muted)' }}>Click pixels in order</div>
            </div>
          </div>
        </div>
      </div>

      {/* ── Wiring Direction ─────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Wiring Direction</div>
        <select value={wiringDirection} onChange={e => onWiringDirectionChange(e.target.value)}
          data-testid="wiring-direction-select">
          {WIRE_DIRECTIONS.map(d => (
            <option key={d.id} value={d.id}>{d.label}</option>
          ))}
        </select>
        <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
          <button className="btn btn-secondary" style={{ flex: 1, fontSize: 11 }}
            onClick={onReWire} disabled={!pixels.length}
            data-testid="btn-rewire">
            <RotateCcw size={12}/> Re-Wire
          </button>
        </div>

        {/* Click-wire controls */}
        {wiringMode === 'click' && (
          <div style={{ marginTop: 8, padding: 8, background: 'var(--panel2)',
            borderRadius: 6, border: '1px solid var(--border)' }}>
            <div style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 6 }}>
              Click-wire mode: click pixels on canvas in sequence
            </div>
            <div style={{ fontSize: 12, color: 'var(--accent)', marginBottom: 8 }}>
              {pendingWire.length} pixel{pendingWire.length !== 1 ? 's' : ''} connected
            </div>
            <div style={{ display: 'flex', gap: 4 }}>
              <button className="btn btn-success" style={{ flex: 1, fontSize: 11 }}
                onClick={onApplyWire} disabled={pendingWire.length < 2}
                data-testid="btn-apply-wire">
                <CheckCircle size={12}/> Apply
              </button>
              <button className="btn btn-danger" style={{ flex: 1, fontSize: 11 }}
                onClick={onClearWire} disabled={!pendingWire.length}
                data-testid="btn-clear-wire">
                Clear
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ── Export ──────────────────────────────────────────── */}
      <div className="panel-section">
        <div className="section-title">Export</div>
        <div className="tabs" style={{ marginBottom: 10 }}>
          {[
            { id: 'dxf', label: 'DXF' },
            { id: 'cjb', label: 'CJB' },
          ].map(f => (
            <button key={f.id}
              className={`tab ${exportFormat === f.id ? 'active' : ''}`}
              onClick={() => onExportFormatChange(f.id)}
              data-testid={`export-fmt-${f.id}`}>
              {f.label}
            </button>
          ))}
        </div>

        <div style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 8, lineHeight: 1.5 }}>
          {exportFormat === 'dxf'
            ? 'AutoCAD R2010 DXF — Layers per port, wiring polyline, first/last node text markers, pixel numbers'
            : 'LedEdit CJB — XML layout with port assignments, wiring chain, first/last nodes for T8000'}
        </div>

        <div style={{ display: 'flex', gap: 4, marginBottom: 6 }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: 'var(--muted)', cursor: 'pointer' }}>
            Include wiring
          </label>
        </div>

        <button className="btn btn-success btn-full"
          onClick={onExport} disabled={!pixels.length}
          data-testid="btn-export">
          <Download size={14}/>
          Export {exportFormat.toUpperCase()}
          {pixels.length > 0 && ` (${pixels.length} px)`}
        </button>

        {pixels.length === 0 && (
          <div className="tooltip-hint" style={{ marginTop: 6, textAlign: 'center' }}>
            Generate pixels first
          </div>
        )}
      </div>

      {/* ── Footer info ─────────────────────────────────────── */}
      <div className="panel-section" style={{ marginTop: 'auto' }}>
        <div style={{ fontSize: 10, color: 'var(--muted)', lineHeight: 1.6 }}>
          <div><span style={{ color: 'var(--accent2)' }}>F</span> = First node per letter</div>
          <div><span style={{ color: 'var(--danger)' }}>L</span> = Last node per letter</div>
          <div><span style={{ color: '#ff9f43' }}>●</span> = Manually positioned pixel</div>
          <div style={{ marginTop: 4 }}>T8000: 8 ports × 1024 pixels = 8192 max</div>
        </div>
      </div>
    </div>
  );
}
