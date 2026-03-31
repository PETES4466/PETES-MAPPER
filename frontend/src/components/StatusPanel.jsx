import React from 'react';
import { PORTS, getPortStats, PORT_PIXEL_LIMIT, BORDER_COLOR, FILL_COLOR } from '../utils/wireUtils';

export default function StatusPanel({
  pixels,
  selectedCount,
  activeTool,
  wireConnectStart,
  portStats,
  activePortId,
  activeController
}) {
  const borderCount = pixels.filter(p => p.type === 'border').length;
  const fillCount = pixels.filter(p => p.type === 'fill').length;
  const stats = portStats || getPortStats(pixels);

  // Get first 8 ports stats for display
  const first8Ports = PORTS.slice(0, 8);

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
        {activeController && (
          <span className="status-item">
            <span className="status-label">Ctrl:</span>
            <span className="status-value accent">{activeController}</span>
          </span>
        )}
        {wireConnectStart && (
          <span className="status-item warning">
            Wire Connect: click 2nd pixel
          </span>
        )}
      </div>

      <div className="status-divider" />

      <div className="status-section ports">
        {first8Ports.map(port => {
          const count = stats.stats[port.id]?.count || 0;
          return (
            <span 
              key={port.id} 
              className={`port-mini ${activePortId === port.id ? 'active' : ''}`}
              style={{ 
                color: count > 0 ? port.color : 'var(--muted)',
                opacity: count > 0 ? 1 : 0.4
              }}
              title={`${port.name}: ${count}/${PORT_PIXEL_LIMIT}`}
            >
              {port.name}:{count}
            </span>
          );
        })}
      </div>

      <div className="status-spacer" />
      
      <div className="status-section right">
        <span className="status-hint">
          Q/W/E/R=Ctrl · 1-8=Port · V=Select · H=Pan
        </span>
      </div>
    </div>
  );
}
