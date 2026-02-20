export const PORT_COLORS = [
  '#FF6B6B', '#FFD93D', '#6BCB77', '#4D96FF',
  '#FF9F43', '#A29BFE', '#FD79A8', '#00CEC9'
];

export const PORT_PIXEL_LIMIT = 1024;
export const PORT_COUNT = 8;

// Auto snake wiring: letters L→R, within letter border-first then fill snake
export function autoSnakeWiring(pixels, direction = 'ltr-ttb') {
  if (!pixels.length) return [];

  // Group by letter index, preserving order
  const byLetter = {};
  for (const p of pixels) {
    const k = p.letterIndex ?? 0;
    if (!byLetter[k]) byLetter[k] = { border: [], fill: [] };
    byLetter[k][p.type === 'fill' ? 'fill' : 'border'].push(p);
  }

  const ordered = [];
  const letterKeys = Object.keys(byLetter).map(Number).sort((a, b) => a - b);

  for (const lk of letterKeys) {
    const { border, fill } = byLetter[lk];

    // Border pixels: already in contour order from pixel placement
    ordered.push(...border);

    // Fill pixels: snake pattern
    const snaked = snakeFill(fill, direction);
    ordered.push(...snaked);
  }

  return ordered.map(p => p.id);
}

function snakeFill(pixels, direction) {
  if (!pixels.length) return [];
  // Round y to nearest 0.5mm for row grouping
  const rows = {};
  for (const p of pixels) {
    const rowKey = (Math.round(p.y * 2) / 2).toFixed(1);
    if (!rows[rowKey]) rows[rowKey] = [];
    rows[rowKey].push(p);
  }
  const sortedKeys = Object.keys(rows).map(Number).sort((a, b) => a - b);
  const result = [];
  sortedKeys.forEach((rk, idx) => {
    const row = [...rows[rk.toFixed(1)]];
    if (direction === 'rtl-ttb') {
      row.sort((a, b) => idx % 2 === 0 ? b.x - a.x : a.x - b.x);
    } else {
      row.sort((a, b) => idx % 2 === 0 ? a.x - b.x : b.x - a.x);
    }
    result.push(...row);
  });
  return result;
}

// Assign pixels to T8000 ports based on wiring order
export function assignPorts(pixels, wiringOrder, portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT) {
  const pixelMap = {};
  for (const p of pixels) pixelMap[p.id] = p;

  // Track first/last per letter
  const letterFirstLast = {};
  wiringOrder.forEach((id, idx) => {
    const p = pixelMap[id];
    if (!p) return;
    const lk = p.letterIndex ?? 0;
    if (!letterFirstLast[lk]) letterFirstLast[lk] = { first: idx, last: idx };
    else letterFirstLast[lk].last = idx;
  });

  const updatedPixels = pixels.map(p => ({ ...p }));
  const updatedMap = {};
  for (const p of updatedPixels) updatedMap[p.id] = p;

  wiringOrder.forEach((id, seqIdx) => {
    const p = updatedMap[id];
    if (!p) return;
    p.wiringOrder = seqIdx + 1;
    p.portIndex = Math.floor(seqIdx / pixelsPerPort);
    p.portPixelIndex = (seqIdx % pixelsPerPort) + 1;
    const lk = p.letterIndex ?? 0;
    p.isFirst = letterFirstLast[lk]?.first === seqIdx;
    p.isLast  = letterFirstLast[lk]?.last  === seqIdx;
  });

  return updatedPixels;
}

// Check port overflow
export function getPortStats(pixels, portCount = PORT_COUNT, pixelsPerPort = PORT_PIXEL_LIMIT) {
  const stats = Array.from({ length: portCount }, (_, i) => ({ port: i + 1, count: 0, overflow: false }));
  for (const p of pixels) {
    const pi = p.portIndex ?? 0;
    if (pi >= 0 && pi < portCount) stats[pi].count++;
  }
  stats.forEach(s => { s.overflow = s.count > pixelsPerPort; });
  const totalOverflow = pixels.length > portCount * pixelsPerPort;
  return { stats, totalOverflow, totalPixels: pixels.length };
}
