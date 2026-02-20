import { PORT_COLORS } from './wireUtils';

// ─── DXF Generator ──────────────────────────────────────────────────────────
// Produces AutoCAD R2010 DXF with LED_PORT_x layers, wiring, markers, numbers
export function generateDXF(pixels, wiringOrder, settings = {}) {
  const { pixelOdMm = 12, includeWiring = true, includeNumbers = true } = settings;
  const radius = pixelOdMm / 2;
  const pixelMap = {};
  for (const p of pixels) pixelMap[p.id] = p;

  // Determine design height for Y-flip (DXF uses Y-up)
  const ys = pixels.map(p => p.y);
  const maxY = ys.length ? Math.max(...ys) + pixelOdMm : 200;

  const lines = [];

  // Header
  lines.push('0\nSECTION\n2\nHEADER');
  lines.push('9\n$ACADVER\n1\nAC1015');
  lines.push('9\n$INSUNITS\n70\n4'); // 4 = millimeters
  lines.push('0\nENDSEC');

  // Tables (layers)
  lines.push('0\nSECTION\n2\nTABLES');
  lines.push('0\nTABLE\n2\nLAYER\n70\n20');
  const portLayerColors = [1, 2, 3, 4, 5, 6, 7, 8]; // ACI colors
  for (let i = 0; i < 8; i++) {
    lines.push(`0\nLAYER\n2\nLED_PORT_${i+1}\n70\n0\n62\n${portLayerColors[i]}\n6\nCONTINUOUS`);
  }
  lines.push('0\nLAYER\n2\nLED_WIRING\n70\n0\n62\n7\n6\nCONTINUOUS');
  lines.push('0\nLAYER\n2\nLED_MARKERS\n70\n0\n62\n3\n6\nCONTINUOUS');
  lines.push('0\nLAYER\n2\nLED_NUMBERS\n70\n0\n62\n9\n6\nCONTINUOUS');
  lines.push('0\nLAYER\n2\nLED_GUIDE\n70\n0\n62\n8\n6\nCONTINUOUS');
  lines.push('0\nENDTABLE\n0\nENDSEC');

  // Entities
  lines.push('0\nSECTION\n2\nENTITIES');

  // 1. LED Circles
  for (const p of pixels) {
    const portLayer = `LED_PORT_${Math.max(1, (p.portIndex ?? 0) + 1)}`;
    const dxfY = maxY - p.y;
    lines.push(`0\nCIRCLE\n8\n${portLayer}\n10\n${p.x.toFixed(4)}\n20\n${dxfY.toFixed(4)}\n30\n0.0\n40\n${radius.toFixed(4)}`);
  }

  // 2. Wiring polylines
  if (includeWiring && wiringOrder.length > 1) {
    lines.push('0\nPOLYLINE\n8\nLED_WIRING\n66\n1\n70\n0');
    for (const id of wiringOrder) {
      const p = pixelMap[id];
      if (!p) continue;
      const dxfY = maxY - p.y;
      lines.push(`0\nVERTEX\n8\nLED_WIRING\n10\n${p.x.toFixed(4)}\n20\n${dxfY.toFixed(4)}\n30\n0.0`);
    }
    lines.push('0\nSEQEND');
  }

  // 3. First/Last markers
  const seenLetters = new Set();
  for (const id of wiringOrder) {
    const p = pixelMap[id];
    if (!p) continue;
    const lk = `${p.letterIndex}_${p.letter}`;
    if (!seenLetters.has(lk + '_first') && p.isFirst) {
      seenLetters.add(lk + '_first');
      const dxfY = maxY - p.y;
      lines.push(`0\nTEXT\n8\nLED_MARKERS\n10\n${p.x.toFixed(4)}\n20\n${(dxfY + radius + 2).toFixed(4)}\n30\n0.0\n40\n2.0\n1\nFIRST_${p.letter}`);
    }
    if (!seenLetters.has(lk + '_last') && p.isLast) {
      seenLetters.add(lk + '_last');
      const dxfY = maxY - p.y;
      lines.push(`0\nTEXT\n8\nLED_MARKERS\n10\n${p.x.toFixed(4)}\n20\n${(dxfY - radius - 4).toFixed(4)}\n30\n0.0\n40\n2.0\n1\nLAST_${p.letter}`);
    }
  }

  // 4. Pixel numbers
  if (includeNumbers) {
    for (const p of pixels) {
      if (p.wiringOrder < 0) continue;
      const dxfY = maxY - p.y;
      lines.push(`0\nTEXT\n8\nLED_NUMBERS\n10\n${p.x.toFixed(4)}\n20\n${dxfY.toFixed(4)}\n30\n0.0\n40\n1.2\n1\n${p.wiringOrder}`);
    }
  }

  lines.push('0\nENDSEC\n0\nEOF');
  return lines.join('\n');
}

// ─── CJB Generator (LedEdit native XML format) ─────────────────────────────
export function generateCJB(pixels, wiringOrder, settings = {}) {
  const { pixelOdMm = 12, text = '', fontSizeMm = 100 } = settings;
  const pixelMap = {};
  for (const p of pixels) pixelMap[p.id] = p;

  // Group by port
  const ports = {};
  for (const p of pixels) {
    const pi = Math.max(0, p.portIndex ?? 0);
    if (!ports[pi]) ports[pi] = [];
    ports[pi].push(p);
  }

  const now = new Date().toISOString();
  let xml = `<?xml version="1.0" encoding="UTF-8"?>\n`;
  xml += `<!-- LedEdit CJB Layout File - Generated ${now} -->\n`;
  xml += `<LEDEditProject version="2021" controller="T8000" ports="8" pixelsPerPort="1024">\n`;
  xml += `  <Info>\n`;
  xml += `    <Text>${escXml(text)}</Text>\n`;
  xml += `    <FontSizeMM>${fontSizeMm}</FontSizeMM>\n`;
  xml += `    <PixelODMM>${pixelOdMm}</PixelODMM>\n`;
  xml += `    <TotalPixels>${pixels.length}</TotalPixels>\n`;
  xml += `    <Created>${now}</Created>\n`;
  xml += `  </Info>\n`;

  xml += `  <Controller type="T8000">\n`;
  for (let i = 0; i < 8; i++) {
    const portPixels = ports[i] || [];
    xml += `    <Port index="${i+1}" color="${PORT_COLORS[i]}" pixelCount="${portPixels.length}">\n`;
    for (const p of portPixels) {
      xml += `      <Pixel seq="${p.portPixelIndex}" globalSeq="${p.wiringOrder}" `;
      xml += `x="${p.x.toFixed(3)}" y="${p.y.toFixed(3)}" `;
      xml += `type="${p.type}" letter="${escXml(p.letter)}" `;
      xml += `isFirst="${p.isFirst}" isLast="${p.isLast}" mode="${p.isAuto ? 'auto' : 'manual'}"/>\n`;
    }
    xml += `    </Port>\n`;
  }
  xml += `  </Controller>\n`;

  // Wiring path
  xml += `  <WiringPath>\n`;
  let wiringXml = '';
  for (let i = 0; i < wiringOrder.length; i++) {
    const id = wiringOrder[i];
    const p = pixelMap[id];
    if (!p) continue;
    if (i === 0 || p.letter !== pixelMap[wiringOrder[i-1]]?.letter) {
      if (i > 0) wiringXml += `    </Chain>\n`;
      wiringXml += `    <Chain letter="${escXml(p.letter)}" letterIndex="${p.letterIndex}">\n`;
    }
    wiringXml += `      <Node seq="${i+1}" pixelId="${p.id}" x="${p.x.toFixed(3)}" y="${p.y.toFixed(3)}" `;
    wiringXml += `port="${(p.portIndex ?? 0)+1}" portSeq="${p.portPixelIndex}" `;
    wiringXml += `isFirst="${p.isFirst}" isLast="${p.isLast}"/>\n`;
  }
  if (wiringOrder.length > 0) wiringXml += `    </Chain>\n`;
  xml += wiringXml;
  xml += `  </WiringPath>\n`;
  xml += `</LEDEditProject>\n`;

  return xml;
}

function escXml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// Trigger file download in browser
export function downloadFile(content, filename, mimeType) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
