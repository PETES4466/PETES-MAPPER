// Wire utilities for LED sign design tool
// Handles per-letter wiring with separate border/fill sequences

export const PORT_COLORS = [
  '#FF6B6B', '#FFD93D', '#6BCB77', '#4D96FF',
  '#FF9F43', '#A29BFE', '#FD79A8', '#00CEC9'
];

export const BORDER_COLOR = '#00d4ff';
export const FILL_COLOR = '#6bcb77';

export const LETTER_COLORS = [
  '#00d4ff', '#00ff88', '#ffd93d', '#ff6b6b',
  '#ff9f43', '#a29bfe', '#fd79a8', '#00cec9',
  '#6bcb77', '#ff6b6b'
];

export const PORT_PIXEL_LIMIT = 1024;
export const PORT_COUNT = 8;

// Simple row-by-row snake wiring (fast and predictable)
function snakeWire(pixels, direction = 'ltr-ttb') {
  if (pixels.length <= 1) return [...pixels];
  
  const rowHeight = 15; // Group by approximate rows
  const rows = {};
  
  for (const p of pixels) {
    const rowKey = Math.floor(p.y / rowHeight);
    if (!rows[rowKey]) rows[rowKey] = [];
    rows[rowKey].push(p);
  }
  
  const rowKeys = Object.keys(rows).map(Number).sort((a, b) => 
    direction.includes('ttb') ? a - b : b - a
  );
  
  const result = [];
  let leftToRight = direction.includes('ltr');
  
  for (const key of rowKeys) {
    const row = rows[key].sort((a, b) => leftToRight ? a.x - b.x : b.x - a.x);
    result.push(...row);
    leftToRight = !leftToRight;
  }
  
  return result;
}

// Simple spacing check
function ensureStartEndSpacing(arr, minSpacing) {
  if (arr.length < 2 || minSpacing <= 0) return arr;
  return arr;
}

// Per-letter wiring with separate border/fill sequences
export function autoSnakeWiringPerLetter(pixels, direction = 'ltr-ttb', minSpacing = 12) {
  if (!pixels.length) return { wiredPixels: [], wiringOrder: [] };

  const byLetter = {};
  for (const p of pixels) {
    const k = p.letterIndex ?? 0;
    if (!byLetter[k]) byLetter[k] = { border: [], fill: [] };
    byLetter[k][p.type === 'fill' ? 'fill' : 'border'].push(p);
  }

  const wiredPixels = [];
  const wiringOrder = [];
  const letterKeys = Object.keys(byLetter).map(Number).sort((a, b) => a - b);

  for (const letterIdx of letterKeys) {
    const { border, fill } = byLetter[letterIdx];
    
    // Border pixels
    if (border.length > 0) {
      const orderedBorder = snakeWire(border, direction);
      orderedBorder.forEach((p, idx) => {
        const wiredP = {
          ...p,
          borderOrder: idx + 1,
          fillOrder: -1,
          isBorderFirst: idx === 0,
          isBorderLast: idx === orderedBorder.length - 1,
          isFillFirst: false,
          isFillLast: false,
          portIndex: -1,
          portPixelIndex: -1
        };
        wiredPixels.push(wiredP);
        wiringOrder.push(wiredP.id);
      });
    }
    
    // Fill pixels
    if (fill.length > 0) {
      const orderedFill = snakeWire(fill, direction);
      orderedFill.forEach((p, idx) => {
        const wiredP = {
          ...p,
          borderOrder: -1,
          fillOrder: idx + 1,
          isBorderFirst: false,
          isBorderLast: false,
          isFillFirst: idx === 0,
          isFillLast: idx === orderedFill.length - 1,
          portIndex: -1,
          portPixelIndex: -1
        };
        wiredPixels.push(wiredP);
        wiringOrder.push(wiredP.id);
      });
    }
  }

  return { wiredPixels, wiringOrder };
}

// Renumber pixels based on user's wiring order
export function renumberPixelsInOrder(pixels, wiringOrder) {
  const pixelMap = {};
  for (const p of pixels) pixelMap[p.id] = { ...p };
  
  const letterGroups = {};
  for (const id of wiringOrder) {
    const p = pixelMap[id];
    if (!p) continue;
    const key = `${p.letterIndex ?? 0}_${p.type}`;
    if (!letterGroups[key]) letterGroups[key] = [];
    letterGroups[key].push(id);
  }
  
  for (const [key, ids] of Object.entries(letterGroups)) {
    const [, type] = key.split('_');
    ids.forEach((id, idx) => {
      const p = pixelMap[id];
      if (!p) return;
      
      if (type === 'border') {
        p.borderOrder = idx + 1;
        p.fillOrder = -1;
        p.isBorderFirst = idx === 0;
        p.isBorderLast = idx === ids.length - 1;
        p.isFillFirst = false;
        p.isFillLast = false;
      } else {
        p.borderOrder = -1;
        p.fillOrder = idx + 1;
        p.isBorderFirst = false;
        p.isBorderLast = false;
        p.isFillFirst = idx === 0;
        p.isFillLast = idx === ids.length - 1;
      }
    });
  }
  
  return Object.values(pixelMap);
}

// Port stats
export function getPortStats(pixels) {
  const stats = Array.from({ length: PORT_COUNT }, () => ({ count: 0, overflow: false }));
  let total = 0;
  
  for (const p of pixels) {
    total++;
    const pi = p.portIndex;
    if (pi >= 0 && pi < PORT_COUNT) {
      stats[pi].count++;
      if (stats[pi].count > PORT_PIXEL_LIMIT) stats[pi].overflow = true;
    }
  }
  
  return { totalPixels: total, stats };
}

// Build initial port nodes
export function buildInitialPortNodes() {
  return Array.from({ length: PORT_COUNT }, (_, i) => ({
    portIndex: i,
    label: `P${i + 1}`,
    x: -50,
    y: 30 + i * 35
  }));
}
