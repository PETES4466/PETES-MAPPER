import React, { useState } from 'react';
import { ChevronRight, ChevronDown, Settings, Ruler, Grid3X3, Zap } from 'lucide-react';

// Font size options in cm (30-300cm)
const FONT_SIZE_OPTIONS = [
  30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150,
  160, 170, 180, 190, 200, 220, 240, 260, 280, 300
];

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
                <option key={v} value={v}>{v} cm ({v*10} mm)</option>
              ))}
            </select>
          </div>
          <div className="prop-row">
            <label>Spacing</label>
            <div className="prop-slider-row">
              <input
                type="range"
                min="0"
                max="5"
                step="0.1"
                value={letterSpacingCm}
                onChange={(e) => onLetterSpacingCmChange(Number(e.target.value))}
              />
              <span className="prop-value">{letterSpacingCm} cm</span>
            </div>
          </div>
        </CollapsibleSection>

        <CollapsibleSection title="Pixel Grid" icon={Grid3X3}>
          <div className="prop-row">
            <label>Pixel OD</label>
            <div className="prop-slider-row">
              <input
                type="range"
                min="8"
                max="20"
                step="1"
                value={pixelOdMm}
                onChange={(e) => onPixelOdChange(Number(e.target.value))}
              />
              <span className="prop-value">{pixelOdMm} mm</span>
            </div>
          </div>
          <div className="prop-row">
            <label>Fill Spacing</label>
            <div className="prop-slider-row">
              <input
                type="range"
                min="10"
                max="30"
                step="1"
                value={fillSpacingMm}
                onChange={(e) => onFillSpacingChange(Number(e.target.value))}
              />
              <span className="prop-value">{fillSpacingMm} mm</span>
            </div>
          </div>
          <div className="prop-row">
            <label>Border Spacing</label>
            <div className="prop-slider-row">
              <input
                type="range"
                min="8"
                max="25"
                step="1"
                value={borderSpacingMm}
                onChange={(e) => onBorderSpacingChange(Number(e.target.value))}
              />
              <span className="prop-value">{borderSpacingMm} mm</span>
            </div>
          </div>
          <div className="prop-row">
            <label>Edge Margin</label>
            <div className="prop-slider-row">
              <input
                type="range"
                min="0"
                max="10"
                step="0.5"
                value={edgeMarginMm}
                onChange={(e) => onEdgeMarginChange(Number(e.target.value))}
              />
              <span className="prop-value">{edgeMarginMm} mm</span>
            </div>
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
              <option value="ltr-ttb">Left→Right, Top→Bottom</option>
              <option value="rtl-ttb">Right→Left, Top→Bottom</option>
              <option value="ltr-btt">Left→Right, Bottom→Top</option>
              <option value="rtl-btt">Right→Left, Bottom→Top</option>
            </select>
          </div>
        </CollapsibleSection>
      </div>
    </div>
  );
}
