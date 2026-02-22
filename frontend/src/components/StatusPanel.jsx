import React from 'react';
import { PORT_COLORS, getPortStats, PORT_PIXEL_LIMIT, BORDER_COLOR, FILL_COLOR } from '../utils/wireUtils';

export default function StatusPanel({
  pixels,
  selectedCount,
  activeTool,
  wireConnectStart,
  portStats
}) {
  const borderCount = pixels.filter(p => p.type === 'border').length;
  const fillCount = pixels.filter(p => p.type === 'fill').length;
  const stats = portStats || getPortStats(pixels);

  return (
    <div className="status-panel">
      <div className="status-section">
        <span className="status-item">
          <span className="status-label">Total:</span>
          <span className="status-value primary">{pixels.length}</span>
        </span>
        <span className="status-item">
          <span className="status-label">Border:</span>
          <span className="status-value" style={{ color: BORDER_COLOR }}>{borderCount}</span>
        </span>
        <span className="status-item">
          <span className="status-label">Fill:</span>
          <span className="status-value" style={{ color: FILL_COLOR }}>{fillCount}</span>
        </span>
      </div>
      
      <div className="status-divider" />
      
      <div className="status-section">
        <span className="status-item">
          <span className="status-label">Selected:</span>
          <span className="status-value">{selectedCount}</span>
        </span>
        <span className="status-item">
          <span className="status-label">Tool:</span>
          <span className="status-value accent">{activeTool}</span>
        </span>
        {wireConnectStart && (
          <span className="status-item warning">
            Wire Connect: click 2nd pixel
          </span>
        )}
      </div>

      <div className="status-divider" />

      <div className="status-section ports">
        {stats.stats.slice(0, 8).map((s, i) => (
          <span 
            key={i} 
            className="port-mini"
            style={{ 
              color: s.count > 0 ? PORT_COLORS[i] : 'var(--muted)',
              opacity: s.count > 0 ? 1 : 0.4
            }}
            title={`Port ${i+1}: ${s.count}/${PORT_PIXEL_LIMIT}`}
          >
            P{i+1}:{s.count}
          </span>
        ))}
      </div>

      <div className="status-spacer" />
      
      <div className="status-section right">
        <span className="status-hint">
          Scroll=Zoom · Middle-drag=Pan
        </span>
      </div>
    </div>
  );
}
