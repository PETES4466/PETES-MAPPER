import React, { useState, useRef, useEffect } from 'react';
import { ChevronDown, X } from 'lucide-react';
import { PORT_COLORS, PORT_PIXEL_LIMIT } from '../utils/wireUtils';

// Default fonts available in the app
export const DEFAULT_FONTS = [
  { name: 'Arial', family: 'Arial, sans-serif' },
  { name: 'Helvetica', family: 'Helvetica, Arial, sans-serif' },
  { name: 'Times New Roman', family: '"Times New Roman", Times, serif' },
  { name: 'Georgia', family: 'Georgia, serif' },
  { name: 'Verdana', family: 'Verdana, sans-serif' },
  { name: 'Courier New', family: '"Courier New", Courier, monospace' },
  { name: 'Impact', family: 'Impact, sans-serif' },
  { name: 'Comic Sans MS', family: '"Comic Sans MS", cursive' },
];

export default function MenuBar({
  // File menu
  onLoadFont,
  mode, onModeChange,
  onExportDXF, onExportCJB,
  // Edit menu
  canUndo, onUndo,
  canCopy, onCopy,
  canPaste, onPaste,
  canDelete, onDelete,
  // View menu
  showGuide, onShowGuideChange,
  showWiring, onShowWiringChange,
  showNumbers, onShowNumbersChange,
  showPorts, onShowPortsChange,
  // Tools menu
  activeTool, onToolChange,
  // Font
  fontName,
  // Text
  text, onTextChange,
  // Generate
  canGenerate, onGenerate, isGenerating,
  // Ports (new)
  activePort, onActivePortChange, portStats
}) {
  const [openMenu, setOpenMenu] = useState(null);
  const [showFontDialog, setShowFontDialog] = useState(false);
  const [showPortDropdown, setShowPortDropdown] = useState(false);
  const menuRef = useRef(null);
  const fileInputRef = useRef(null);
  const portDropdownRef = useRef(null);

  // Close menu when clicking outside
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setOpenMenu(null);
      }
      if (portDropdownRef.current && !portDropdownRef.current.contains(e.target)) {
        setShowPortDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleMenuClick = (menu) => {
    setOpenMenu(openMenu === menu ? null : menu);
  };

  const handleMenuItemClick = (action) => {
    action();
    setOpenMenu(null);
  };

  const handleLoadFontClick = () => {
    fileInputRef.current?.click();
    setOpenMenu(null);
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (ev) => onLoadFont(ev.target.result, file.name.replace(/\.[^.]+$/, ''));
      reader.readAsArrayBuffer(file);
    }
    e.target.value = '';
  };

  const menus = [
    {
      label: 'File',
      items: [
        { label: 'Load Font...', action: handleLoadFontClick, shortcut: 'Ctrl+O' },
        { divider: true },
        { label: 'Fill Mode', action: () => onModeChange('fill'), checked: mode === 'fill' },
        { label: 'Border Mode', action: () => onModeChange('border'), checked: mode === 'border' },
        { label: 'Both Mode', action: () => onModeChange('both'), checked: mode === 'both' },
        { divider: true },
        { label: 'Export DXF...', action: onExportDXF, shortcut: 'Ctrl+E' },
        { label: 'Export CJB...', action: onExportCJB, shortcut: 'Ctrl+Shift+E' },
        { divider: true },
        { label: 'Exit', action: () => window.close() },
      ]
    },
    {
      label: 'Edit',
      items: [
        { label: 'Undo', action: onUndo, disabled: !canUndo, shortcut: 'Ctrl+Z' },
        { divider: true },
        { label: 'Copy', action: onCopy, disabled: !canCopy, shortcut: 'Ctrl+C' },
        { label: 'Paste', action: onPaste, disabled: !canPaste, shortcut: 'Ctrl+V' },
        { label: 'Delete', action: onDelete, disabled: !canDelete, shortcut: 'Del' },
      ]
    },
    {
      label: 'View',
      items: [
        { label: 'Show Guide', action: () => onShowGuideChange(!showGuide), checked: showGuide },
        { label: 'Show Wiring', action: () => onShowWiringChange(!showWiring), checked: showWiring },
        { label: 'Show Numbers', action: () => onShowNumbersChange(!showNumbers), checked: showNumbers },
        { label: 'Show Ports', action: () => onShowPortsChange(!showPorts), checked: showPorts },
      ]
    },
    {
      label: 'Tools',
      items: [
        { label: 'Select', action: () => onToolChange('select'), checked: activeTool === 'select', shortcut: 'V' },
        { label: 'Pan', action: () => onToolChange('pan'), checked: activeTool === 'pan', shortcut: 'H' },
        { label: 'Wire Connect', action: () => onToolChange('wireconnect'), checked: activeTool === 'wireconnect', shortcut: 'W' },
      ]
    },
  ];

  return (
    <div className="menu-bar" ref={menuRef}>
      {/* Hidden file input */}
      <input
        ref={fileInputRef}
        type="file"
        accept=".ttf,.otf"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />

      {/* App title */}
      <div className="app-title">
        <span className="app-icon">◈</span>
        LED Sign Designer
      </div>

      {/* Menus */}
      <div className="menu-items">
        {menus.map((menu) => (
          <div key={menu.label} className="menu-item-container">
            <button
              className={`menu-button ${openMenu === menu.label ? 'active' : ''}`}
              onClick={() => handleMenuClick(menu.label)}
            >
              {menu.label}
              <ChevronDown size={12} />
            </button>
            {openMenu === menu.label && (
              <div className="menu-dropdown">
                {menu.items.map((item, idx) => 
                  item.divider ? (
                    <div key={idx} className="menu-divider" />
                  ) : (
                    <button
                      key={idx}
                      className={`menu-dropdown-item ${item.disabled ? 'disabled' : ''}`}
                      onClick={() => !item.disabled && handleMenuItemClick(item.action)}
                      disabled={item.disabled}
                    >
                      <span className="menu-check">{item.checked ? '✓' : ''}</span>
                      <span className="menu-label">{item.label}</span>
                      {item.shortcut && <span className="menu-shortcut">{item.shortcut}</span>}
                    </button>
                  )
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Quick toolbar */}
      <div className="quick-toolbar">
        {/* Port Dropdown */}
        <div className="toolbar-group port-dropdown-container" ref={portDropdownRef}>
          <button 
            className="port-dropdown-btn"
            onClick={() => setShowPortDropdown(!showPortDropdown)}
            data-testid="port-dropdown-btn"
          >
            <span 
              className="port-indicator" 
              style={{ backgroundColor: activePort !== null ? PORT_COLORS[activePort] : 'var(--text-muted)' }}
            />
            <span className="port-label">
              {activePort !== null ? `P${activePort + 1}` : 'Port'}
            </span>
            <span className="port-count-display">
              ({portStats?.stats?.[activePort]?.count ?? 0}/{PORT_PIXEL_LIMIT})
            </span>
            <ChevronDown size={12} />
          </button>
          {showPortDropdown && (
            <div className="port-dropdown-menu">
              {Array.from({ length: 8 }, (_, i) => {
                const count = portStats?.stats?.[i]?.count ?? 0;
                const isActive = activePort === i;
                return (
                  <button
                    key={i}
                    className={`port-dropdown-item ${isActive ? 'active' : ''}`}
                    onClick={() => {
                      onActivePortChange?.(i);
                      setShowPortDropdown(false);
                    }}
                    data-testid={`port-select-${i+1}`}
                  >
                    <span 
                      className="port-color-dot" 
                      style={{ backgroundColor: PORT_COLORS[i] }}
                    />
                    <span className="port-name">P{i + 1}</span>
                    <span className="port-stats">
                      {count}/{PORT_PIXEL_LIMIT}
                    </span>
                    <div className="port-progress">
                      <div 
                        className="port-progress-fill"
                        style={{ 
                          width: `${Math.min(100, (count / PORT_PIXEL_LIMIT) * 100)}%`,
                          backgroundColor: count > PORT_PIXEL_LIMIT ? '#ff4444' : PORT_COLORS[i]
                        }}
                      />
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
        <div className="toolbar-divider" />
        <div className="toolbar-group">
          <label className="toolbar-label">Font:</label>
          <span className="toolbar-value">{fontName || 'None'}</span>
        </div>
        <div className="toolbar-divider" />
        <div className="toolbar-group">
          <label className="toolbar-label">Text:</label>
          <input
            type="text"
            className="toolbar-input"
            value={text}
            onChange={(e) => onTextChange(e.target.value)}
            placeholder="Enter text"
          />
        </div>
        <div className="toolbar-divider" />
        <button
          className="toolbar-generate-btn"
          onClick={onGenerate}
          disabled={!canGenerate || isGenerating}
        >
          {isGenerating ? 'Generating...' : '⚡ Generate'}
        </button>
      </div>

      {/* Window controls placeholder */}
      <div className="window-controls">
        <span className="font-status">{fontName ? `✓ ${fontName}` : ''}</span>
      </div>
    </div>
  );
}
