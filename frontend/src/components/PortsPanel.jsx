import React from 'react';
import { Eye, EyeOff, Download } from 'lucide-react';
import { PORT_COLORS, getPortStats, PORT_PIXEL_LIMIT, BORDER_COLOR, FILL_COLOR } from '../utils/wireUtils';

export default function PortsPanel({
  pixels,
  portNodes,
  letterPortMap,
  visiblePorts,
  selectedPortIndex,
  onSelectPort,
  text,
  exportFormat,
  onExportFormatChange,
  onExport,
  onVerify,
  verifyStatus,
  isCollapsed,
  onToggleCollapse
}) {
  const portStats = getPortStats(pixels);
  const borderCount = pixels.filter(p => p.type === 'border').length;
  const fillCount = pixels.filter(p => p.type === 'fill').length;

  if (isCollapsed) {
    return (
      <div className="ports-panel collapsed" onClick={onToggleCollapse}>
        <div className="collapsed-label vertical">
          <span>Ports & Export</span>
        </div>
      </div>
    );
  }

  return (
    <div className="ports-panel">
      <div className="panel-header">
        <span>Ports & Export</span>
        <button className="collapse-btn" onClick={onToggleCollapse} title="Collapse">
          ✕
        </button>
      </div>

      <div className="panel-scroll">
        {/* Pixel Stats */}
        <div className="ports-section">
          <div className="section-title-small">Pixel Count</div>
          <div className="pixel-stats">
            <div className="stat-row">
              <span>Total</span>
              <span className="stat-value primary">{portStats.totalPixels}</span>
            </div>
            <div className="stat-row">
              <span style={{ color: BORDER_COLOR }}>Border</span>
              <span className="stat-value" style={{ color: BORDER_COLOR }}>{borderCount}</span>
            </div>
            <div className="stat-row">
              <span style={{ color: FILL_COLOR }}>Fill</span>
              <span className="stat-value" style={{ color: FILL_COLOR }}>{fillCount}</span>
            </div>
          </div>
        </div>

        {/* T8000 Ports */}
        <div className="ports-section">
          <div className="section-title-small">T8000 Ports (8 × 1024)</div>
          <div className="port-hint">Click port to show on canvas, then click letter start</div>
          <div className="port-list">
            {portStats.stats.map((s, i) => {
              const isVisible = visiblePorts?.has(i) || false;
              const isSelected = selectedPortIndex === i;
              const connectedItems = Object.entries(letterPortMap || {})
                .filter(([_, pIdx]) => pIdx === i)
                .map(([key]) => {
                  const [lIdx, pType] = key.split('_');
                  return `${text?.[parseInt(lIdx)] || '?'}(${pType?.[0]?.toUpperCase() || '?'})`;
                })
                .join(', ');

              return (
                <div key={i} className="port-row" data-testid={`port-row-${i+1}`}>
                  <button
                    className={`port-toggle ${isVisible ? 'visible' : ''} ${isSelected ? 'selected' : ''}`}
                    style={{ 
                      '--port-color': PORT_COLORS[i],
                      borderColor: isVisible ? PORT_COLORS[i] : 'var(--border)'
                    }}
                    onClick={() => onSelectPort?.(i)}
                    title={connectedItems ? `Connected: ${connectedItems}` : 'Click to show'}
                  >
                    {isVisible ? <Eye size={12} /> : <EyeOff size={12} />}
                    <span style={{ color: PORT_COLORS[i] }}>P{i + 1}</span>
                  </button>
                  <div className="port-bar">
                    <div 
                      className="port-bar-fill"
                      style={{
                        width: `${Math.min(100, (s.count / PORT_PIXEL_LIMIT) * 100)}%`,
                        background: s.overflow ? '#ff6b6b' : PORT_COLORS[i]
                      }}
                    />
                  </div>
                  <span 
                    className="port-count"
                    style={{ color: s.count > 0 ? PORT_COLORS[i] : 'var(--muted)' }}
                  >
                    {s.count}
                  </span>
                </div>
              );
            })}
          </div>
        </div>

        {/* Export */}
        <div className="ports-section">
          <div className="section-title-small">Export</div>
          <button
            className="export-main-btn"
            onClick={onVerify}
            disabled={pixels.length === 0}
            style={{
              marginBottom: 8,
              background: verifyStatus?.ok ? '#1f7a45' : undefined
            }}
            title="Validate ports, string counts, and wiring before export"
          >
            Verify LedEdit
          </button>
          {verifyStatus && (
            <div
              style={{
                fontSize: 11,
                marginBottom: 8,
                color: verifyStatus.ok ? '#6bcb77' : '#ff8a80'
              }}
            >
              {verifyStatus.ok ? 'PASS' : 'FAIL'} · {verifyStatus.summary}
            </div>
          )}
          <div className="export-buttons">
            <button
              className={`export-btn ${exportFormat === 'dxf' ? 'active' : ''}`}
              onClick={() => onExportFormatChange('dxf')}
            >
              DXF
            </button>
            <button
              className={`export-btn ${exportFormat === 'cjb' ? 'active' : ''}`}
              onClick={() => onExportFormatChange('cjb')}
            >
              CJB
            </button>
          </div>
          <button
            className="export-main-btn"
            onClick={onExport}
            disabled={pixels.length === 0}
          >
            <Download size={14} />
            Export {exportFormat.toUpperCase()}
          </button>
        </div>
      </div>
    </div>
  );
}
