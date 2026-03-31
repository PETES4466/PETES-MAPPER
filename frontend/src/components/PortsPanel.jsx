import React from 'react';
import { Eye, EyeOff, Download } from 'lucide-react';
import { PORT_COLORS, getPortStats, PORT_PIXEL_LIMIT } from '../utils/wireUtils';

export default function PortsPanel({
  pixels,
  visiblePorts,
  selectedPortIndex,
  onSelectPort,
  exportFormat,
  onExportFormatChange,
  onExport,
  isCollapsed,
  onToggleCollapse
}) {
  const portStats = getPortStats(pixels);

  if (isCollapsed) {
    return (
      <div className="ports-panel collapsed" onClick={onToggleCollapse}>
        <div className="collapsed-label vertical">P</div>
      </div>
    );
  }

  return (
    <div className="ports-panel">
      <div className="panel-header">
        <span>Port</span>
        <button className="collapse-btn" onClick={onToggleCollapse}>✕</button>
      </div>

      <div className="panel-scroll">
        {/* Stats */}
        <div className="ports-section compact">
          <div className="stat-mini">T:{portStats.totalPixels}</div>
        </div>

        {/* Ports - compact */}
        <div className="ports-section">
          {portStats.stats.map((s, i) => {
            const isVisible = visiblePorts?.has(i) || false;
            const isSelected = selectedPortIndex === i;
            return (
              <button
                key={i}
                className={`port-btn-mini ${isVisible ? 'vis' : ''} ${isSelected ? 'sel' : ''}`}
                style={{ borderColor: isVisible ? PORT_COLORS[i] : 'transparent' }}
                onClick={() => onSelectPort?.(i)}
                data-testid={`port-${i+1}`}
              >
                <span className="port-dot" style={{ background: PORT_COLORS[i] }} />
                <span>{i+1}</span>
                <span className="cnt">{s.count}</span>
              </button>
            );
          })}
        </div>

        {/* Export - compact */}
        <div className="ports-section">
          <button className="exp-btn" onClick={onExport} disabled={pixels.length === 0}>
            <Download size={10} />
            {exportFormat.toUpperCase()}
          </button>
        </div>
      </div>
    </div>
  );
}
