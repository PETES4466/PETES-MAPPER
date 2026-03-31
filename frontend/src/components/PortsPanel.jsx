import React, { useState } from 'react';
import { Download, ChevronDown, ChevronRight } from 'lucide-react';
import { CONTROLLERS, PORTS, getPortsForController, PORT_PIXEL_LIMIT } from '../utils/wireUtils';

export default function PortsPanel({
  pixels,
  activePortId,
  onSelectPort,
  activeController,
  onSelectController,
  exportFormat,
  onExport,
  isCollapsed,
  onToggleCollapse
}) {
  const [expandedControllers, setExpandedControllers] = useState({ A: true, B: false, C: false, D: false });

  // Count pixels per port
  const portCounts = {};
  PORTS.forEach(p => { portCounts[p.id] = 0; });
  pixels.forEach(p => {
    const pid = p.portId || 1;
    if (portCounts[pid] !== undefined) portCounts[pid]++;
  });

  const toggleController = (cid) => {
    setExpandedControllers(prev => ({ ...prev, [cid]: !prev[cid] }));
  };

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
        <span>Ports</span>
        <button className="collapse-btn" onClick={onToggleCollapse}>✕</button>
      </div>

      <div className="panel-scroll">
        {/* Total */}
        <div className="ports-section compact">
          <div className="stat-mini">Total: {pixels.length}</div>
        </div>

        {/* Controllers */}
        {CONTROLLERS.map(ctrl => {
          const ctrlPorts = getPortsForController(ctrl.id);
          const isExpanded = expandedControllers[ctrl.id];
          const ctrlTotal = ctrlPorts.reduce((sum, p) => sum + (portCounts[p.id] || 0), 0);
          
          return (
            <div key={ctrl.id} className="controller-section">
              <button 
                className={`controller-header ${activeController === ctrl.id ? 'active' : ''}`}
                onClick={() => {
                  toggleController(ctrl.id);
                  onSelectController?.(ctrl.id);
                }}
              >
                {isExpanded ? <ChevronDown size={10} /> : <ChevronRight size={10} />}
                <span className="ctrl-key">{ctrl.key}</span>
                <span className="ctrl-name">{ctrl.id}</span>
                <span className="ctrl-count">{ctrlTotal}</span>
              </button>
              
              {isExpanded && (
                <div className="controller-ports">
                  {ctrlPorts.map(port => {
                    const count = portCounts[port.id] || 0;
                    const isActive = activePortId === port.id;
                    const isOverflow = count > PORT_PIXEL_LIMIT;
                    
                    return (
                      <button
                        key={port.id}
                        className={`port-btn-mini ${isActive ? 'active' : ''} ${isOverflow ? 'overflow' : ''}`}
                        onClick={() => onSelectPort?.(port.id)}
                        title={`${port.name}: ${count}/${PORT_PIXEL_LIMIT}`}
                      >
                        <span className="port-dot" style={{ background: port.color }} />
                        <span className="port-num">{port.id % 8 || 8}</span>
                        <span className="port-cnt">{count}</span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}

        {/* Export */}
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
