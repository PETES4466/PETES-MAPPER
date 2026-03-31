import React from 'react';
import { 
  MousePointer, Move, Cable, Copy, Clipboard, Trash2, 
  Undo2, Scissors, Link2, RotateCcw, PenTool
} from 'lucide-react';

export default function Toolbar({
  activeTool, onToolChange,
  canUndo, onUndo,
  canCopy, onCopy,
  canPaste, onPaste,
  canDelete, onDelete,
  canBreakWire, onBreakWire,
  canJoinWire, onJoinWire,
  onReWire,
  wireConnectStart,
  pathPoints
}) {
  const tools = [
    { id: 'select', icon: MousePointer, label: 'Select (V)', shortcut: 'V' },
    { id: 'pan', icon: Move, label: 'Pan (H)', shortcut: 'H' },
    { id: 'path', icon: PenTool, label: 'Path Tool (P) - Draw wiring path', shortcut: 'P' },
    { id: 'wireconnect', icon: Cable, label: 'Wire Connect (W)', shortcut: 'W' },
  ];

  const actions = [
    { id: 'undo', icon: Undo2, label: 'Undo (Ctrl+Z)', action: onUndo, disabled: !canUndo },
    { divider: true },
    { id: 'copy', icon: Copy, label: 'Copy (Ctrl+C)', action: onCopy, disabled: !canCopy },
    { id: 'paste', icon: Clipboard, label: 'Paste (Ctrl+V)', action: onPaste, disabled: !canPaste },
    { id: 'delete', icon: Trash2, label: 'Delete (Del)', action: onDelete, disabled: !canDelete, danger: true },
    { divider: true },
    { id: 'breakwire', icon: Scissors, label: 'Break Wire', action: onBreakWire, disabled: !canBreakWire, warning: true },
    { id: 'joinwire', icon: Link2, label: 'Join Wire', action: onJoinWire, disabled: !canJoinWire },
    { divider: true },
    { id: 'rewire', icon: RotateCcw, label: 'Re-Wire All', action: onReWire },
  ];

  return (
    <div className="toolbar-vertical">
      {/* Tools */}
      <div className="toolbar-section">
        <div className="toolbar-section-label">Tools</div>
        {tools.map(tool => (
          <button
            key={tool.id}
            className={`toolbar-btn ${activeTool === tool.id ? 'active' : ''} ${tool.id === 'wireconnect' && wireConnectStart ? 'pending' : ''} ${tool.id === 'path' && pathPoints?.length > 0 ? 'pending' : ''}`}
            onClick={() => onToolChange(tool.id)}
            title={tool.label}
            data-testid={`tool-${tool.id}`}
          >
            <tool.icon size={18} />
          </button>
        ))}
      </div>

      <div className="toolbar-spacer" />

      {/* Actions */}
      <div className="toolbar-section">
        <div className="toolbar-section-label">Actions</div>
        {actions.map((action, idx) => 
          action.divider ? (
            <div key={idx} className="toolbar-divider-h" />
          ) : (
            <button
              key={action.id}
              className={`toolbar-btn ${action.danger ? 'danger' : ''} ${action.warning ? 'warning' : ''}`}
              onClick={action.action}
              disabled={action.disabled}
              title={action.label}
              data-testid={`action-${action.id}`}
            >
              <action.icon size={18} />
            </button>
          )
        )}
      </div>
    </div>
  );
}
