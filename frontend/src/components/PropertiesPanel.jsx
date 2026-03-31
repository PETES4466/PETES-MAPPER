import React, { useState } from 'react';
import { ChevronRight, ChevronDown, Settings, Ruler, Grid3X3, Zap } from 'lucide-react';

// Font size options in cm (30-300cm)
const FONT_SIZE_OPTIONS = [
  30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150,
  160, 170, 180, 190, 200, 220, 240, 260, 280, 300
];

// Pixel OD options (mm)
const PIXEL_OD_OPTIONS = [8, 10, 12, 14, 16, 18, 20];

// Fill spacing options (mm)
const FILL_SPACING_OPTIONS = [10, 12, 14, 15, 16, 18, 20, 22, 24, 26, 28, 30];

// Border spacing options (mm)
const BORDER_SPACING_OPTIONS = [8, 10, 12, 14, 16, 18, 20, 22, 24, 25];

// Edge margin options (mm)
const EDGE_MARGIN_OPTIONS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

// Letter spacing options (cm)
const LETTER_SPACING_OPTIONS = [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, 5];

function CollapsibleSection({ title, icon: Icon, children, defaultOpen = true }) {
  const [isOpen, setIsOpen] = useState(defaultOpen);
  
  return (
    <div className="collapsible-section">
      <button className="section-header" onClick={() => setIsOpen(!isOpen)}>
        {isOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        {Icon && <Icon size={14} />}
        <span>{title}</span>
      </button>
      {isOpen && <div className="section-content">{children}</div>}
    </div>
  );
}

export default function PropertiesPanel({
  fontSizeCm, onFontSizeCmChange,
  letterSpacingCm, onLetterSpacingCmChange,
  fillSpacingMm, onFillSpacingChange,
  borderSpacingMm, onBorderSpacingChange,
  borderPixelCount, onBorderPixelCountChange,
  pixelOdMm, onPixelOdChange,
  edgeMarginMm, onEdgeMarginChange,
  wiringDirection, onWiringDirectionChange,
  isCollapsed, onToggleCollapse
}) {
  if (isCollapsed) {
    return (
      <div className="properties-panel collapsed" onClick={onToggleCollapse}>
        <div className="collapsed-label">
          <Settings size={14} />
          <span>Properties</span>
        </div>
      </div>
    );
  }

  return (
    <div className="properties-panel">
      <div className="panel-header">
        <span>Properties</span>
        <button className="collapse-btn" onClick={onToggleCollapse} title="Collapse">
          <ChevronRight size={14} />
        </button>
      </div>

      <div className="panel-scroll">
        <CollapsibleSection title="Letter Size" icon={Ruler}>
          <div className="prop-row">
            <label>Height</label>
            <select
              value={fontSizeCm}
              onChange={(e) => onFontSizeCmChange(Number(e.target.value))}
              className="prop-select"
            >
              {FONT_SIZE_OPTIONS.map(v => (
                <option key={v} value={v}>{v} cm</option>
              ))}
            </select>
          </div>
          <div className="prop-row">
            <label>Spacing</label>
            <select
              value={letterSpacingCm}
              onChange={(e) => onLetterSpacingCmChange(Number(e.target.value))}
              className="prop-select"
            >
              {LETTER_SPACING_OPTIONS.map(v => (
                <option key={v} value={v}>{v} cm</option>
              ))}
            </select>
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Pixel Grid" icon={Grid3X3}>
          <div className="prop-row">
            <label>Pixel OD</label>
            <select
              value={pixelOdMm}
              onChange={(e) => onPixelOdChange(Number(e.target.value))}
              className="prop-select"
            >
              {PIXEL_OD_OPTIONS.map(v => (
                <option key={v} value={v}>{v} mm</option>
              ))}
            </select>
          </div>
          <div className="prop-row">
            <label>Fill Spacing</label>
            <select
              value={fillSpacingMm}
              onChange={(e) => onFillSpacingChange(Number(e.target.value))}
              className="prop-select"
            >
              {FILL_SPACING_OPTIONS.map(v => (
                <option key={v} value={v}>{v} mm</option>
              ))}
            </select>
          </div>
          <div className="prop-row">
            <label>Border Spacing</label>
            <select
              value={borderSpacingMm}
              onChange={(e) => onBorderSpacingChange(Number(e.target.value))}
              className="prop-select"
            >
              {BORDER_SPACING_OPTIONS.map(v => (
                <option key={v} value={v}>{v} mm</option>
              ))}
            </select>
          </div>
          <div className="prop-row">
            <label>Edge Margin</label>
            <select
              value={edgeMarginMm}
              onChange={(e) => onEdgeMarginChange(Number(e.target.value))}
              className="prop-select"
            >
              {EDGE_MARGIN_OPTIONS.map(v => (
                <option key={v} value={v}>{v} mm</option>
              ))}
            </select>
          </div>
          <div className="prop-row">
            <label>Border Count</label>
            <div className="prop-toggle-row">
              <button
                className={`prop-toggle ${borderPixelCount === 'auto' ? 'active' : ''}`}
                onClick={() => onBorderPixelCountChange('auto')}
              >
                Auto
              </button>
              <button
                className={`prop-toggle ${borderPixelCount !== 'auto' ? 'active' : ''}`}
                onClick={() => onBorderPixelCountChange(50)}
              >
                Fixed
              </button>
            </div>
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Wiring" icon={Zap} defaultOpen={false}>
          <div className="prop-row">
            <label>Direction</label>
            <select
              value={wiringDirection}
              onChange={(e) => onWiringDirectionChange(e.target.value)}
              className="prop-select"
            >
              <option value="ltr-ttb">L→R, T→B</option>
              <option value="rtl-ttb">R→L, T→B</option>
              <option value="ltr-btt">L→R, B→T</option>
              <option value="rtl-btt">R→L, B→T</option>
            </select>
          </div>
        </CollapsibleSection>
      </div>
    </div>
  );
}
