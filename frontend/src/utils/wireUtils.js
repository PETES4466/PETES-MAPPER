// Wire utilities for LED sign design tool
// Supports 4 Controllers (A, B, C, D) with 8 Ports each (32 total ports)

// Controller definitions - 4 Controllers with 8 ports each
export const CONTROLLERS = [
  { id: 'A', name: 'Controller A', key: 'Q', ports: [1, 2, 3, 4, 5, 6, 7, 8] },
  { id: 'B', name: 'Controller B', key: 'W', ports: [9, 10, 11, 12, 13, 14, 15, 16] },
  { id: 'C', name: 'Controller C', key: 'E', ports: [17, 18, 19, 20, 21, 22, 23, 24] },
  { id: 'D', name: 'Controller D', key: 'R', ports: [25, 26, 27, 28, 29, 30, 31, 32] }
];

// All 32 ports with colors
export const PORTS = [
  // Controller A - Red/Pink shades
  { id: 1, name: 'A1', controller: 'A', color: '#ef4444', maxLeds: 1024 },
  { id: 2, name: 'A2', controller: 'A', color: '#f87171', maxLeds: 1024 },
  { id: 3, name: 'A3', controller: 'A', color: '#dc2626', maxLeds: 1024 },
  { id: 4, name: 'A4', controller: 'A', color: '#fca5a5', maxLeds: 1024 },
  { id: 5, name: 'A5', controller: 'A', color: '#ec4899', maxLeds: 1024 },
  { id: 6, name: 'A6', controller: 'A', color: '#f472b6', maxLeds: 1024 },
  { id: 7, name: 'A7', controller: 'A', color: '#db2777', maxLeds: 1024 },
  { id: 8, name: 'A8', controller: 'A', color: '#f9a8d4', maxLeds: 1024 },
  
  // Controller B - Green shades
  { id: 9, name: 'B1', controller: 'B', color: '#22c55e', maxLeds: 1024 },
  { id: 10, name: 'B2', controller: 'B', color: '#4ade80', maxLeds: 1024 },
  { id: 11, name: 'B3', controller: 'B', color: '#16a34a', maxLeds: 1024 },
  { id: 12, name: 'B4', controller: 'B', color: '#86efac', maxLeds: 1024 },
  { id: 13, name: 'B5', controller: 'B', color: '#10b981', maxLeds: 1024 },
  { id: 14, name: 'B6', controller: 'B', color: '#34d399', maxLeds: 1024 },
  { id: 15, name: 'B7', controller: 'B', color: '#059669', maxLeds: 1024 },
  { id: 16, name: 'B8', controller: 'B', color: '#6ee7b7', maxLeds: 1024 },
  
  // Controller C - Blue/Cyan shades
  { id: 17, name: 'C1', controller: 'C', color: '#3b82f6', maxLeds: 1024 },
  { id: 18, name: 'C2', controller: 'C', color: '#60a5fa', maxLeds: 1024 },
  { id: 19, name: 'C3', controller: 'C', color: '#2563eb', maxLeds: 1024 },
  { id: 20, name: 'C4', controller: 'C', color: '#93c5fd', maxLeds: 1024 },
  { id: 21, name: 'C5', controller: 'C', color: '#06b6d4', maxLeds: 1024 },
  { id: 22, name: 'C6', controller: 'C', color: '#22d3ee', maxLeds: 1024 },
  { id: 23, name: 'C7', controller: 'C', color: '#0891b2', maxLeds: 1024 },
  { id: 24, name: 'C8', controller: 'C', color: '#67e8f9', maxLeds: 1024 },
  
  // Controller D - Amber/Orange shades
  { id: 25, name: 'D1', controller: 'D', color: '#f59e0b', maxLeds: 1024 },
  { id: 26, name: 'D2', controller: 'D', color: '#fbbf24', maxLeds: 1024 },
  { id: 27, name: 'D3', controller: 'D', color: '#d97706', maxLeds: 1024 },
  { id: 28, name: 'D4', controller: 'D', color: '#fcd34d', maxLeds: 1024 },
  { id: 29, name: 'D5', controller: 'D', color: '#ea580c', maxLeds: 1024 },
  { id: 30, name: 'D6', controller: 'D', color: '#fb923c', maxLeds: 1024 },
  { id: 31, name: 'D7', controller: 'D', color: '#c2410c', maxLeds: 1024 },
  { id: 32, name: 'D8', controller: 'D', color: '#fdba74', maxLeds: 1024 }
];

// Legacy support
export const PORT_COLORS = PORTS.slice(0, 8).map(p => p.color);
export const BORDER_COLOR = '#00d4ff';
export const FILL_COLOR = '#6bcb77';
export const PORT_PIXEL_LIMIT = 1024;
export const PORT_COUNT = 32;

// Get port by ID
export function getPortById(portId) {
  return PORTS.find(p => p.id === portId) || PORTS[0];
}

// Get ports for a controller
export function getPortsForController(controllerId) {
  return PORTS.filter(p => p.controller === controllerId);
}

// Simple row-by-row snake wiring (fast and predictable)
function snakeWire(pixels, direction = 'ltr-ttb') {
  if (pixels.length <= 1) return [...pixels];
  
  const rowHeight = 15;
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
          portId: p.portId || 1,
          portPixelIndex: -1
        };
        wiredPixels.push(wiredP);
        wiringOrder.push(wiredP.id);
      });
    }
    
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
          portId: p.portId || 1,
          portPixelIndex: -1
        };
        wiredPixels.push(wiredP);
        wiringOrder.push(wiredP.id);
      });
    }
  }

  return { wiredPixels, wiringOrder };
}

// Re-index nodes for a specific port - ensures contiguous order
export function reindexNodesForPort(nodeList, portId) {
  const portNodes = nodeList
    .filter(n => n.portId === portId)
    .sort((a, b) => {
      const d = (a.order || 0) - (b.order || 0);
      return d !== 0 ? d : a.id.localeCompare(b.id);
    });
  
  const otherNodes = nodeList.filter(n => n.portId !== portId);
  
  const reindexed = portNodes.map((n, i) => ({
    ...n,
    order: i,
    portPixelIndex: i + 1
  }));
  
  return [...otherNodes, ...reindexed];
}

// Ensure proper sequential order across all ports
export function ensureProperOrder(nodeList) {
  const portGroups = {};
  
  nodeList.forEach(n => {
    const pid = n.portId || 1;
    if (!portGroups[pid]) portGroups[pid] = [];
    portGroups[pid].push(n);
  });
  
  const result = [];
  const portIds = Object.keys(portGroups).sort((a, b) => Number(a) - Number(b));
  
  portIds.forEach(portIdKey => {
    const sorted = portGroups[portIdKey].sort((a, b) => {
      // Sort by order first, then by id
      const orderDiff = (a.order || 0) - (b.order || 0);
      return orderDiff !== 0 ? orderDiff : a.id.localeCompare(b.id);
    });
    
    sorted.forEach((n, idx) => {
      result.push({
        ...n,
        order: idx,
        portPixelIndex: idx + 1
      });
    });
  });
  
  return result;
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

// Get port stats for all 32 ports
export function getPortStats(pixels) {
  const stats = {};
  PORTS.forEach(p => {
    stats[p.id] = { count: 0, overflow: false, port: p };
  });
  
  let total = 0;
  for (const p of pixels) {
    total++;
    const pid = p.portId || 1;
    if (stats[pid]) {
      stats[pid].count++;
      if (stats[pid].count > PORT_PIXEL_LIMIT) stats[pid].overflow = true;
    }
  }
  
  return { totalPixels: total, stats };
}

// Build initial port nodes (legacy)
export function buildInitialPortNodes() {
  return PORTS.slice(0, 8).map((p, i) => ({
    portIndex: i,
    portId: p.id,
    label: p.name,
    x: -50,
    y: 30 + i * 35
  }));
}
