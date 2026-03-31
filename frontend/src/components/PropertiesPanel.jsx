import React, { useState } from 'react';
import { ChevronRight, ChevronDown } from 'lucide-react';

const FONT_SIZE_OPTIONS = [30, 40, 50, 60, 80, 100, 120, 150, 200, 250, 300];
const PIXEL_OD_OPTIONS = [8, 10, 12, 14, 16, 18, 20];
const SPACING_OPTIONS = [10, 12, 14, 16, 18, 20, 24, 28];
const MARGIN_OPTIONS = [0, 2, 3, 4, 5, 6, 8, 10];
const LETTER_SP_OPTIONS = [0, 1, 2, 3, 4, 5];

function Section({ title, children, defaultOpen = true }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="collapsible-section">
      <button className="section-header" onClick={() => setOpen(!open)}>
        {open ? <ChevronDown size={10} /> : <ChevronRight size={10} />}
        <span>{title}</span>
      </button>
      {open && <div className="section-content">{children}</div>}
    </div>
  );
}

export default function PropertiesPanel({
  fontSizeCm, onFontSizeCmChange,
  letterSpacingCm, onLetterSpacingCmChange,
  fillSpacingMm, onFillSpacingChange,
  borderSpacingMm, onBorderSpacingChange,
  pixelOdMm, onPixelOdChange,
  edgeMarginMm, onEdgeMarginChange,
  wiringMode, onWiringModeChange,
  isCollapsed, onToggleCollapse
}) {
  if (isCollapsed) {
    return (
      <div className="properties-panel collapsed" onClick={onToggleCollapse}>
        <div className="collapsed-label">P</div>
      </div>
    );
  }

  return (
    <div className="properties-panel">
      <div className="panel-header">
        <span>Props</span>
        <button className="collapse-btn" onClick={onToggleCollapse}>✕</button>
      </div>

      <div className="panel-scroll">
        <Section title="Size">
          <div className="prop-row">
            <label>Ht(cm)</label>
            <select value={fontSizeCm} onChange={e => onFontSizeCmChange(Number(e.target.value))} className="prop-select">
              {FONT_SIZE_OPTIONS.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
          <div className="prop-row">
            <label>Spc(cm)</label>
            <select value={letterSpacingCm} onChange={e => onLetterSpacingCmChange(Number(e.target.value))} className="prop-select">
              {LETTER_SP_OPTIONS.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
        </Section>

        <Section title="Pixel">
          <div className="prop-row">
            <label>OD(mm)</label>
            <select value={pixelOdMm} onChange={e => onPixelOdChange(Number(e.target.value))} className="prop-select">
              {PIXEL_OD_OPTIONS.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
          <div className="prop-row">
            <label>Fill</label>
            <select value={fillSpacingMm} onChange={e => onFillSpacingChange(Number(e.target.value))} className="prop-select">
              {SPACING_OPTIONS.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
          <div className="prop-row">
            <label>Bord</label>
            <select value={borderSpacingMm} onChange={e => onBorderSpacingChange(Number(e.target.value))} className="prop-select">
              {SPACING_OPTIONS.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
          <div className="prop-row">
            <label>Mrgn</label>
            <select value={edgeMarginMm} onChange={e => onEdgeMarginChange(Number(e.target.value))} className="prop-select">
              {MARGIN_OPTIONS.map(v => <option key={v} value={v}>{v}</option>)}
            </select>
          </div>
        </Section>

        <Section title="Wire" defaultOpen={false}>
          <div className="prop-row">
            <label>Mode</label>
            <select value={wiringMode} onChange={e => onWiringModeChange(e.target.value)} className="prop-select">
              <option value="auto">Auto</option>
              <option value="layout">Layout</option>
            </select>
          </div>
        </Section>
      </div>
    </div>
  );
}
