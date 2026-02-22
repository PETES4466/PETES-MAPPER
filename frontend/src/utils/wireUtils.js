// Wire utilities for LED sign design tool
// Handles per-letter wiring with separate border/fill sequences

export const PORT_COLORS = [
  '#FF6B6B', '#FFD93D', '#6BCB77', '#4D96FF',
  '#FF9F43', '#A29BFE', '#FD79A8', '#00CEC9'
];

// Border = cyan, Fill = green (distinct colors)
export const BORDER_COLOR = '#00d4ff';
export const FILL_COLOR = '#6bcb77';

export const LETTER_COLORS = [
  '#00d4ff', '#00ff88', '#ffd93d', '#ff6b6b',
  '#ff9f43', '#a29bfe', '#fd79a8', '#00cec9',
  '#6bcb77', '#ff6b6b'
];

export const PORT_PIXEL_LIMIT = 1024;
export const PORT_COUNT = 8;

function dist(a, b) {
  return Math.sqrt((a.x - b.x) ** 2 + (a.y - b.y) ** 2);
}

// Check if a line segment intersects with a "boundary" - used for smart wiring
function lineIntersectsEmptySpace(p1, p2, allPixels, letterIndex, threshold = 30) {
  // Simple heuristic: check if the midpoint of the line is too far from any pixel
  // This prevents wiring across empty gaps in letters like 'H', 'A', 'O'
  const midX = (p1.x + p2.x) / 2;
  const midY = (p1.y + p2.y) / 2;
  const distance = dist(p1, p2);
  
  // Short distances are always OK
  if (distance < threshold) return false;
  
  // Check if midpoint is close to any pixel of this letter
  const letterPixels = allPixels.filter(p => (p.letterIndex ?? 0) === letterIndex);
  for (const p of letterPixels) {
    const d = dist({ x: midX, y: midY }, p);
    if (d < threshold * 0.7) return false;
  }
  
  // Check along the line at intervals
  const steps = Math.ceil(distance / (threshold * 0.5));
  for (let i = 1; i < steps; i++) {
    const t = i / steps;
    const checkX = p1.x + (p2.x - p1.x) * t;
    const checkY = p1.y + (p2.y - p1.y) * t;
    
    let nearestDist = Infinity;
    for (const p of letterPixels) {
      nearestDist = Math.min(nearestDist, dist({ x: checkX, y: checkY }, p));
    }
    
    // If any point along the line is too far from pixels, it crosses empty space
    if (nearestDist > threshold * 0.8) return true;
  }
  
  return false;
}

// Nearest neighbor wiring algorithm - stays within letter boundaries
function nearestNeighborWiring(pixels, letterIndex, pixelType) {
  if (pixels.length === 0) return [];
  if (pixels.length === 1) return [...pixels];
  
  const letterPixels = pixels.filter(p => 
    (p.letterIndex ?? 0) === letterIndex && p.type === pixelType
  );
  
  if (letterPixels.length <= 1) return letterPixels;
  
  // Start from top-left most pixel
  const sorted = [...letterPixels].sort((a, b) => {
    const aScore = a.x + a.y * 0.5;
    const bScore = b.x + b.y * 0.5;
    return aScore - bScore;
  });
  
  const result = [];
  const remaining = new Set(sorted.map(p => p.id));
  let current = sorted[0];
  
  result.push(current);
  remaining.delete(current.id);
  
  // Greedy nearest neighbor with boundary check
  while (remaining.size > 0) {
    let nearest = null;
    let nearestDist = Infinity;
    
    for (const p of letterPixels) {
      if (!remaining.has(p.id)) continue;
      
      const d = dist(current, p);
      
      // Check if this path crosses empty space
      const crossesEmpty = lineIntersectsEmptySpace(current, p, letterPixels, letterIndex, 25);
      
      // Penalize paths that cross empty space
      const effectiveDist = crossesEmpty ? d * 3 : d;
      
      if (effectiveDist < nearestDist) {
        nearestDist = effectiveDist;
        nearest = p;
      }
    }
    
    if (nearest) {
      result.push(nearest);
      remaining.delete(nearest.id);
      current = nearest;
    } else {
      break;
    }
  }
  
  return result;
}

function getTypicalSpacing(pixels) {
  if (pixels.length < 2) return 15;
  let total = 0, count = 0;
  const sample = pixels.slice(0, Math.min(30, pixels.length));
  for (let i = 0; i < sample.length; i++) {
    let minD = Infinity;
    for (let j = 0; j < sample.length; j++) {
      if (i !== j) minD = Math.min(minD, dist(sample[i], sample[j]));
    }
    if (isFinite(minD)) { total += minD; count++; }
  }
  return count > 0 ? total / count : 15;
}

function clusterRows(pixels, threshold) {
  const rows = new Map();
  for (const p of pixels) {
    let assigned = false;
    for (const [ry] of rows) {
      if (Math.abs(p.y - ry) < threshold) { rows.get(ry).push(p); assigned = true; break; }
    }
    if (!assigned) rows.set(p.y, [p]);
  }
  return rows;
}

// Smart snake: nearest-neighbor row transitions to minimize hollow jumps
function smartSnakeFill(pixels, direction = 'ltr-ttb') {
  if (!pixels.length) return [];
  const spacing = getTypicalSpacing(pixels);
  const rows = clusterRows(pixels, spacing * 0.75);
  const rowKeys = [...rows.keys()].sort((a, b) => a - b);

  const result = [];
  let lastPx = null;

  for (let ri = 0; ri < rowKeys.length; ri++) {
    const row = [...rows.get(rowKeys[ri])];
    if (!row.length) continue;

    if (lastPx) {
      const rowLR = [...row].sort((a, b) => a.x - b.x);
      const rowRL = [...row].sort((a, b) => b.x - a.x);
      const dLR = dist(lastPx, rowLR[0]);
      const dRL = dist(lastPx, rowRL[0]);
      result.push(...(dLR <= dRL ? rowLR : rowRL));
    } else {
      row.sort((a, b) => direction === 'rtl-ttb' ? b.x - a.x : a.x - b.x);
      result.push(...row);
    }
    lastPx = result[result.length - 1];
  }
  return result;
}

// Ensure start and end pixels are not overlapping (minimum spacing)
function ensureStartEndSpacing(orderedPixels, minSpacing) {
  if (orderedPixels.length < 2) return orderedPixels;
  
  const first = orderedPixels[0];
  const last = orderedPixels[orderedPixels.length - 1];
  
  // If start and end are too close, try to find a better end point
  if (dist(first, last) < minSpacing && orderedPixels.length > 2) {
    // Find the pixel that is farthest from the first pixel
    let maxDist = 0;
    let bestEndIdx = orderedPixels.length - 1;
    for (let i = 1; i < orderedPixels.length; i++) {
      const d = dist(first, orderedPixels[i]);
      if (d > maxDist) {
        maxDist = d;
        bestEndIdx = i;
      }
    }
    
    // Reorder: move best end to the end
    if (bestEndIdx !== orderedPixels.length - 1) {
      const reordered = [...orderedPixels];
      const [endPixel] = reordered.splice(bestEndIdx, 1);
      reordered.push(endPixel);
      return reordered;
    }
  }
  
  return orderedPixels;
}

// ── Per-letter wiring with separate border/fill sequences ────────────────────
// Each letter has:
//   - Border pixels: numbered 1 to N with borderFirst/borderLast markers
//   - Fill pixels: numbered 1 to M with fillFirst/fillLast markers
// NO auto-connection between letters
export function autoSnakeWiringPerLetter(pixels, direction = 'ltr-ttb', minSpacing = 12) {
  if (!pixels.length) return { wiredPixels: [], wiringOrder: [] };

  // Group by letter
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
    
    // Process border pixels for this letter
    let orderedBorder = border.length > 0 ? [...border] : [];
    if (orderedBorder.length > 0) {
      // Border follows contour - already in order from placement, just apply snake
      orderedBorder = ensureStartEndSpacing(orderedBorder, minSpacing);
      
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
    
    // Process fill pixels for this letter
    let orderedFill = fill.length > 0 ? smartSnakeFill(fill, direction) : [];
    if (orderedFill.length > 0) {
      orderedFill = ensureStartEndSpacing(orderedFill, minSpacing);
      
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

// ── Port assignment with letter-to-port mapping ───────────────────────────────
// letterPortMap: { "letterIndex_type": portIndex } e.g., { "0_border": 0, "0_fill": 1 }
export function assignPortsWithLetterMap(
  pixels, wiringOrder,
  letterPortMap = {},
  portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT
) {
  const pixelMap = {};
  for (const p of pixels) pixelMap[p.id] = p;

  // Track per-port running counts
  const portCounts = Object.fromEntries(Array.from({length: portCount}, (_, i) => [i, 0]));

  const updated = pixels.map(p => {
    const newP = { ...p };
    const key = `${p.letterIndex ?? 0}_${p.type}`;
    const portIdx = letterPortMap[key];
    
    if (portIdx !== undefined) {
      portCounts[portIdx]++;
      newP.portIndex = portIdx;
      newP.portPixelIndex = portCounts[portIdx];
    } else {
      newP.portIndex = -1;
      newP.portPixelIndex = -1;
    }
    
    return newP;
  });

  return updated;
}

// ── Port stats ────────────────────────────────────────────────────────────────
export function getPortStats(pixels, portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT) {
  const stats = Array.from({ length: portCount }, (_, i) => ({ port: i + 1, count: 0, overflow: false }));
  for (const p of pixels) {
    const pi = p.portIndex ?? -1;
    if (pi >= 0 && pi < portCount) stats[pi].count++;
  }
  stats.forEach(s => { s.overflow = s.count > pixelsPerPort; });
  return { stats, totalPixels: pixels.length, totalOverflow: pixels.length > portCount * pixelsPerPort };
}

// ── Zoom to letter bounds ─────────────────────────────────────────────────────
export function computeLetterZoom(letterIndex, pixels, canvasW, canvasH, pad = 60) {
  const lp = pixels.filter(p => (p.letterIndex ?? 0) === letterIndex);
  if (!lp.length) return null;
  const xs = lp.map(p => p.x), ys = lp.map(p => p.y);
  const minX = Math.min(...xs), maxX = Math.max(...xs);
  const minY = Math.min(...ys), maxY = Math.max(...ys);
  const lw = maxX - minX || 10, lh = maxY - minY || 10;
  const scale = Math.min((canvasW - 2 * pad) / lw, (canvasH - 2 * pad) / lh, 8);
  const ox = (canvasW - lw * scale) / 2 - minX * scale;
  const oy = (canvasH - lh * scale) / 2 - minY * scale;
  return { scale, offset: { x: ox, y: oy } };
}

// ── Initial port nodes for draggable port markers ─────────────────────────────
export function buildInitialPortNodes() {
  return Array.from({ length: 8 }, (_, i) => ({
    id: `port_${i}`,
    portIndex: i,
    x: -60,           // mm – left of design area (will be positioned on canvas)
    y: i * 25 + 20,   // mm – evenly spaced vertically
    connectedLetterIndex: null,
    label: `P${i + 1}`
  }));
}

// Get the first pixel of a letter's border (for port connection lines)
export function getLetterBorderStartPixel(pixels, letterIndex) {
  return pixels.find(p => (p.letterIndex ?? 0) === letterIndex && p.type === 'border' && p.isBorderFirst) || null;
}

// Get the first pixel of a letter's fill (for port connection lines)
export function getLetterFillStartPixel(pixels, letterIndex) {
  return pixels.find(p => (p.letterIndex ?? 0) === letterIndex && p.type === 'fill' && p.isFillFirst) || null;
}

// Get the last pixel of a letter's border
export function getLetterBorderEndPixel(pixels, letterIndex) {
  return pixels.find(p => (p.letterIndex ?? 0) === letterIndex && p.type === 'border' && p.isBorderLast) || null;
}

// Get the last pixel of a letter's fill
export function getLetterFillEndPixel(pixels, letterIndex) {
  return pixels.find(p => (p.letterIndex ?? 0) === letterIndex && p.type === 'fill' && p.isFillLast) || null;
}

// Get unique letter indices from pixels
export function getUniqueLetterIndices(pixels) {
  const indices = new Set();
  for (const p of pixels) {
    indices.add(p.letterIndex ?? 0);
  }
  return [...indices].sort((a, b) => a - b);
}

// Get letter stats: border count, fill count, approved status
export function getLetterStats(pixels, approvedLetters = new Set()) {
  const stats = {};
  for (const p of pixels) {
    const li = p.letterIndex ?? 0;
    if (!stats[li]) {
      stats[li] = { letterIndex: li, borderCount: 0, fillCount: 0, approved: approvedLetters.has(li) };
    }
    if (p.type === 'border') stats[li].borderCount++;
    else stats[li].fillCount++;
  }
  return Object.values(stats);
}
