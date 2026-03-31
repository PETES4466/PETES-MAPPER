import React, { useEffect, useRef, useCallback, forwardRef } from 'react';
import { PORT_COLORS, BORDER_COLOR, FILL_COLOR } from '../utils/wireUtils';

const WIRING_COLOR = 'rgba(255,77,77,0.8)';  // Red dotted for smart wiring recommendation
const PENDING_COLOR = 'rgba(0,212,255,0.9)';
const GUIDE_FILL = 'rgba(15,40,100,0.08)';  // Very subtle fill
const GUIDE_STROKE = 'rgba(255,255,255,0.7)';  // Thin white outline
const PORT_NODE_SIZE = 18;
const WIRE_CONNECT_COLOR = '#FFD700';

const LedCanvas = forwardRef(function LedCanvas({
  pixels, wiringOrder, guideCommands,
  selectedIds, activeTool, pixelOdMm,
  showNumbers, showWiring, showGuide,
  onPixelMove, onPixelSelect, onWireConnectClick, onContextMenu,
  wireConnectStart,
  portNodes, onPortNodeMove, letterPortMap,
  visiblePorts, selectedPortIndex, onConnectPortToLetter,
  manualWires
}, ref) {
  const containerRef = useRef(null);
  const canvasRef = useRef(null);
  const scaleRef = useRef(2);
  const offsetRef = useRef({ x: 40, y: 40 });
  const dprRef = useRef(1);

  // Interaction refs
  const isDraggingRef = useRef(false);
  const dragPixelRef = useRef(null);
  const dragStartRef = useRef({});
  const isPanningRef = useRef(false);
  const panStartRef = useRef({});
  const isRubberBandRef = useRef(false);
  const rubberStartRef = useRef({ mx: 0, my: 0 });
  const rubberCurRef = useRef({ mx: 0, my: 0 });
  const livePixelRef = useRef({});
  const isDraggingPortRef = useRef(false);
  const dragPortIdxRef = useRef(null);
  const livePortRef = useRef({});

  // Props refs for event handlers
  const pixelsRef = useRef(pixels);
  const wiringRef = useRef(wiringOrder);
  const guidesRef = useRef(guideCommands);
  const selRef = useRef(selectedIds);
  const toolRef = useRef(activeTool);
  const showNumRef = useRef(showNumbers);
  const showWireRef = useRef(showWiring);
  const showGuideRef = useRef(showGuide);
  const odRef = useRef(pixelOdMm);
  const portNodesRef = useRef(portNodes);
  const letterPortMapRef = useRef(letterPortMap);
  const visiblePortsRef = useRef(visiblePorts);
  const selPortIdxRef = useRef(selectedPortIndex);
  const manualWiresRef = useRef(manualWires);
  const wireConnectStartRef = useRef(wireConnectStart);

  // Update refs
  pixelsRef.current = pixels;
  wiringRef.current = wiringOrder;
  guidesRef.current = guideCommands;
  selRef.current = selectedIds;
  toolRef.current = activeTool;
  showNumRef.current = showNumbers;
  showWireRef.current = showWiring;
  showGuideRef.current = showGuide;
  odRef.current = pixelOdMm;
  portNodesRef.current = portNodes;
  letterPortMapRef.current = letterPortMap;
  visiblePortsRef.current = visiblePorts;
  selPortIdxRef.current = selectedPortIndex;
  manualWiresRef.current = manualWires;
  wireConnectStartRef.current = wireConnectStart;

  const toScreen = (x, y) => ({
    sx: x * scaleRef.current + offsetRef.current.x,
    sy: y * scaleRef.current + offsetRef.current.y
  });
  
  const toMm = (sx, sy) => ({
    x: (sx - offsetRef.current.x) / scaleRef.current,
    y: (sy - offsetRef.current.y) / scaleRef.current
  });

  function hitPixel(mx, my) {
    const { x, y } = toMm(mx, my);
    const r = odRef.current / 2 + 1.5;
    for (const p of pixelsRef.current) {
      const px = livePixelRef.current[p.id]?.x ?? p.x;
      const py = livePixelRef.current[p.id]?.y ?? p.y;
      if ((px - x) ** 2 + (py - y) ** 2 <= r * r) return p;
    }
    return null;
  }

  function hitPortNode(mx, my) {
    const ports = portNodesRef.current || [];
    const visibleSet = visiblePortsRef.current || new Set();
    const portR = PORT_NODE_SIZE / 2 / scaleRef.current;
    for (const port of ports) {
      if (!visibleSet.has(port.portIndex)) continue;
      const px = livePortRef.current[port.portIndex]?.x ?? port.x;
      const py = livePortRef.current[port.portIndex]?.y ?? port.y;
      const { x, y } = toMm(mx, my);
      if ((px - x) ** 2 + (py - y) ** 2 <= (portR * 1.5) ** 2) return port;
    }
    return null;
  }

  function pixelsInRect(x1, y1, x2, y2) {
    const minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
    const minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
    return pixelsRef.current
      .filter(p => p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY)
      .map(p => p.id);
  }

  function buildMap(arr) {
    const m = {};
    for (const p of arr) m[p.id] = p;
    return m;
  }

  // ── Render ───────────────────────────────────────────────────────────────
  const render = useCallback(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const { width: W, height: H } = container.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    dprRef.current = dpr;
    canvas.width = W * dpr;
    canvas.height = H * dpr;
    canvas.style.width = W + 'px';
    canvas.style.height = H + 'px';

    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.fillStyle = '#0d1117';
    ctx.fillRect(0, 0, W, H);

    const pixels = pixelsRef.current;
    const guides = guidesRef.current;
    const selIds = selRef.current;
    const r = odRef.current / 2 * scaleRef.current;

    // ── Grid ───────────────────────────────────────────────────────────────
    ctx.save();
    ctx.strokeStyle = 'rgba(48,54,61,0.5)';
    ctx.lineWidth = 0.5;
    const gridSize = 50 * scaleRef.current;
    const ox = offsetRef.current.x % gridSize;
    const oy = offsetRef.current.y % gridSize;
    for (let x = ox; x < W; x += gridSize) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
    }
    for (let y = oy; y < H; y += gridSize) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }
    ctx.restore();

    // ── Guide paths (thin white outline) ────────────────────────────────────
    if (showGuideRef.current && guides.length) {
      ctx.save();
      for (const g of guides) {
        ctx.beginPath();
        for (const cmd of g.commands) {
          const { sx, sy } = toScreen(cmd.x, cmd.y);
          if (cmd.type === 'M') ctx.moveTo(sx, sy);
          else ctx.lineTo(sx, sy);
        }
        ctx.closePath();
        ctx.fillStyle = GUIDE_FILL;
        ctx.fill();
        // Thin white reference line
        ctx.strokeStyle = GUIDE_STROKE;
        ctx.lineWidth = 1.5;
        ctx.stroke();
      }
      ctx.restore();
    }

    // ── Wiring (dotted red = recommendation, solid = user confirmed) ────────
    if (showWireRef.current && pixels.length > 1) {
      const wOrder = wiringRef.current;
      const pmap = buildMap(pixels);
      ctx.save();
      
      let prevBroken = false;
      let prevSolid = false;
      
      // Draw in segments - solid for confirmed, dotted for recommendation
      for (let i = 0; i < wOrder.length; i++) {
        const id = wOrder[i];
        const p = pmap[id];
        if (!p) continue;
        
        const px = livePixelRef.current[p.id]?.x ?? p.x;
        const py = livePixelRef.current[p.id]?.y ?? p.y;
        const { sx, sy } = toScreen(px, py);
        
        if (i === 0 || prevBroken) {
          ctx.beginPath();
          ctx.moveTo(sx, sy);
        } else {
          // Determine line style based on solidWire flag
          const isSolid = p.solidWire || false;
          
          if (isSolid !== prevSolid && i > 0) {
            // Style changed, stroke current path and start new
            ctx.stroke();
            ctx.beginPath();
            // Get previous point
            const prevP = pmap[wOrder[i-1]];
            if (prevP) {
              const prevPx = livePixelRef.current[prevP.id]?.x ?? prevP.x;
              const prevPy = livePixelRef.current[prevP.id]?.y ?? prevP.y;
              const { sx: psx, sy: psy } = toScreen(prevPx, prevPy);
              ctx.moveTo(psx, psy);
            }
          }
          
          ctx.strokeStyle = isSolid ? '#00ff88' : WIRING_COLOR; // Green for solid, red for dotted
          ctx.lineWidth = isSolid ? 2.5 : 2;
          ctx.setLineDash(isSolid ? [] : [6, 4]);
          ctx.lineTo(sx, sy);
          prevSolid = isSolid;
        }
        
        prevBroken = p.wiringBroken ?? false;
        
        if (prevBroken || i === wOrder.length - 1) {
          ctx.stroke();
        }
      }
      
      ctx.setLineDash([]);
      ctx.restore();
    }

    // ── Manual wires ───────────────────────────────────────────────────────
    const mWires = manualWiresRef.current || [];
    if (mWires.length > 0) {
      const pmap = buildMap(pixels);
      ctx.save();
      ctx.strokeStyle = WIRE_CONNECT_COLOR;
      ctx.lineWidth = 2.5;
      ctx.shadowColor = WIRE_CONNECT_COLOR;
      ctx.shadowBlur = 6;
      for (const wire of mWires) {
        const fromP = pmap[wire.from];
        const toP = pmap[wire.to];
        if (!fromP || !toP) continue;
        const { sx: fx, sy: fy } = toScreen(fromP.x, fromP.y);
        const { sx: tx, sy: ty } = toScreen(toP.x, toP.y);
        ctx.beginPath();
        ctx.moveTo(fx, fy);
        ctx.lineTo(tx, ty);
        ctx.stroke();
      }
      ctx.shadowBlur = 0;
      ctx.restore();
    }

    // ── Pixels ─────────────────────────────────────────────────────────────
    for (const p of pixels) {
      const px = livePixelRef.current[p.id]?.x ?? p.x;
      const py = livePixelRef.current[p.id]?.y ?? p.y;
      const { sx, sy } = toScreen(px, py);
      const isSel = selIds.has(p.id);
      const isBroken = p.wiringBroken ?? false;
      const pixelColor = p.type === 'border' ? BORDER_COLOR : FILL_COLOR;
      const isWireStart = wireConnectStartRef.current === p.id;

      ctx.save();
      if (isSel) { ctx.shadowColor = '#ffffff'; ctx.shadowBlur = 10; }
      else if (isWireStart) { ctx.shadowColor = WIRE_CONNECT_COLOR; ctx.shadowBlur = 14; }
      else if (p.isBorderFirst || p.isFillFirst) { ctx.shadowColor = '#00ff88'; ctx.shadowBlur = 12; }
      else if (p.isBorderLast || p.isFillLast) { ctx.shadowColor = '#ff6b6b'; ctx.shadowBlur = 12; }

      ctx.beginPath();
      ctx.arc(sx, sy, r, 0, Math.PI * 2);
      let fill = isSel ? 'rgba(255,255,255,0.85)'
               : isWireStart ? WIRE_CONNECT_COLOR + 'dd'
               : pixelColor + 'cc';
      if (isBroken) fill = 'rgba(80,20,20,0.8)';
      ctx.fillStyle = fill;
      ctx.fill();
      ctx.strokeStyle = isSel ? '#ffffff' : isWireStart ? WIRE_CONNECT_COLOR
                      : isBroken ? '#ff6b6b' : pixelColor;
      ctx.lineWidth = isSel ? 2.5 : 1.5;
      ctx.stroke();
      ctx.shadowBlur = 0;
      ctx.restore();

      // Break mark
      if (isBroken && r > 4) {
        ctx.save();
        ctx.strokeStyle = '#ff6b6b';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(sx - r * 0.5, sy - r * 0.5);
        ctx.lineTo(sx + r * 0.5, sy + r * 0.5);
        ctx.moveTo(sx + r * 0.5, sy - r * 0.5);
        ctx.lineTo(sx - r * 0.5, sy + r * 0.5);
        ctx.stroke();
        ctx.restore();
      }
    }

    // ── Start/End markers ──────────────────────────────────────────────────
    for (const p of pixels) {
      const isStart = p.isBorderFirst || p.isFillFirst;
      const isEnd = p.isBorderLast || p.isFillLast;
      if (!isStart && !isEnd) continue;

      const px = livePixelRef.current[p.id]?.x ?? p.x;
      const py = livePixelRef.current[p.id]?.y ?? p.y;
      const { sx, sy } = toScreen(px, py);
      const color = isStart ? '#00ff88' : '#ff4444';
      const label = p.type === 'border' 
        ? (isStart ? 'BS' : 'BE')
        : (isStart ? 'FS' : 'FE');

      ctx.save();
      ctx.strokeStyle = color;
      ctx.lineWidth = 2.5;
      ctx.shadowColor = color;
      ctx.shadowBlur = 12;
      ctx.beginPath();
      ctx.arc(sx, sy, r * 1.7, 0, Math.PI * 2);
      ctx.stroke();
      ctx.shadowBlur = 0;
      ctx.fillStyle = color;
      ctx.font = `bold ${Math.max(7, r * 0.8)}px monospace`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(label, sx, sy);
      ctx.restore();
    }

    // ── Pixel numbers ──────────────────────────────────────────────────────
    if (showNumRef.current && r > 5) {
      ctx.save();
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.font = `${Math.max(7, Math.min(11, r * 0.8))}px monospace`;
      for (const p of pixels) {
        if (p.isBorderFirst || p.isBorderLast || p.isFillFirst || p.isFillLast) continue;
        const order = p.type === 'border' ? p.borderOrder : p.fillOrder;
        if (order < 0) continue;
        const px = livePixelRef.current[p.id]?.x ?? p.x;
        const py = livePixelRef.current[p.id]?.y ?? p.y;
        const { sx, sy } = toScreen(px, py);
        ctx.fillStyle = '#ffffff';
        ctx.fillText(order, sx, sy);
      }
      ctx.restore();
    }

    // ── Rubber band ────────────────────────────────────────────────────────
    if (isRubberBandRef.current) {
      const rs = rubberStartRef.current;
      const rc = rubberCurRef.current;
      ctx.save();
      ctx.strokeStyle = 'rgba(0,212,255,0.9)';
      ctx.fillStyle = 'rgba(0,212,255,0.07)';
      ctx.lineWidth = 1.5;
      ctx.setLineDash([4, 4]);
      const rx = Math.min(rs.mx, rc.mx), ry = Math.min(rs.my, rc.my);
      const rw = Math.abs(rc.mx - rs.mx), rh = Math.abs(rc.my - rs.my);
      ctx.fillRect(rx, ry, rw, rh);
      ctx.strokeRect(rx, ry, rw, rh);
      ctx.setLineDash([]);
      ctx.restore();
    }

    // ── Port nodes ─────────────────────────────────────────────────────────
    const ports = portNodesRef.current || [];
    const visibleSet = visiblePortsRef.current || new Set();
    const lpMap = letterPortMapRef.current || {};
    const selPortIdx = selPortIdxRef.current;

    if (visibleSet.size > 0) {
      // Port connection lines
      ctx.save();
      for (const [key, portIdx] of Object.entries(lpMap)) {
        if (!visibleSet.has(portIdx)) continue;
        const [letterIdxStr, pixelType] = key.split('_');
        const letterIdx = parseInt(letterIdxStr);
        let startPixel;
        if (pixelType === 'border') {
          startPixel = pixels.find(p => p.letterIndex === letterIdx && p.isBorderFirst);
        } else {
          startPixel = pixels.find(p => p.letterIndex === letterIdx && p.isFillFirst);
        }
        if (!startPixel) continue;
        const port = ports.find(p => p.portIndex === portIdx);
        if (!port) continue;
        const portX = livePortRef.current[port.portIndex]?.x ?? port.x;
        const portY = livePortRef.current[port.portIndex]?.y ?? port.y;
        const { sx: psx, sy: psy } = toScreen(portX, portY);
        const { sx: lsx, sy: lsy } = toScreen(startPixel.x, startPixel.y);
        ctx.strokeStyle = PORT_COLORS[portIdx] + 'aa';
        ctx.lineWidth = 2;
        ctx.setLineDash([6, 3]);
        ctx.beginPath();
        ctx.moveTo(psx, psy);
        ctx.lineTo(lsx, lsy);
        ctx.stroke();
        ctx.setLineDash([]);
      }
      ctx.restore();

      // Port nodes
      ctx.save();
      for (const port of ports) {
        if (!visibleSet.has(port.portIndex)) continue;
        const portX = livePortRef.current[port.portIndex]?.x ?? port.x;
        const portY = livePortRef.current[port.portIndex]?.y ?? port.y;
        const { sx, sy } = toScreen(portX, portY);
        const portColor = PORT_COLORS[port.portIndex];
        const isSelected = selPortIdx === port.portIndex;
        ctx.beginPath();
        ctx.arc(sx, sy, PORT_NODE_SIZE / 2, 0, Math.PI * 2);
        ctx.fillStyle = isSelected ? portColor : portColor + '66';
        ctx.fill();
        ctx.strokeStyle = isSelected ? '#ffffff' : portColor;
        ctx.lineWidth = isSelected ? 3 : 2;
        ctx.stroke();
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 10px monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(port.label, sx, sy);
      }
      ctx.restore();
    }

    // Placeholder
    if (!pixels.length && !guides.length) {
      ctx.save();
      ctx.fillStyle = 'rgba(107,114,128,0.45)';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.font = '15px system-ui';
      ctx.fillText('Load a font, enter text, and click Generate', W / 2, H / 2 - 14);
      ctx.font = '11px system-ui';
      ctx.fillText('Scroll=Zoom · Middle-drag=Pan', W / 2, H / 2 + 10);
      ctx.restore();
    }
  }, []);

  // Setup and resize
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const observer = new ResizeObserver(() => render());
    observer.observe(container);
    render();
    return () => observer.disconnect();
  }, [render]);

  // Re-render on props change
  useEffect(() => { render(); }, [
    pixels, wiringOrder, guideCommands, selectedIds,
    showNumbers, showWiring, showGuide, pixelOdMm, render,
    portNodes, letterPortMap, visiblePorts, selectedPortIndex,
    manualWires, wireConnectStart
  ]);

  // ── Event handlers ───────────────────────────────────────────────────────
  const getPos = (e) => {
    const r = canvasRef.current.getBoundingClientRect();
    return { mx: e.clientX - r.left, my: e.clientY - r.top };
  };

  const handleMouseDown = (e) => {
    e.preventDefault();
    const { mx, my } = getPos(e);
    const tool = toolRef.current;

    // Middle / Pan tool
    if (e.button === 1 || tool === 'pan') {
      isPanningRef.current = true;
      panStartRef.current = { mx, my, ox: offsetRef.current.x, oy: offsetRef.current.y };
      return;
    }

    // Right-click = context menu (handled by parent)
    if (e.button === 2) return;

    // Check port node
    const hitPort = hitPortNode(mx, my);
    if (hitPort) {
      isDraggingPortRef.current = true;
      dragPortIdxRef.current = hitPort.portIndex;
      dragStartRef.current = { mx, my };
      return;
    }

    // Port connection
    const selPort = selPortIdxRef.current;
    if (selPort !== null) {
      const hit = hitPixel(mx, my);
      if (hit && (hit.isBorderFirst || hit.isFillFirst)) {
        onConnectPortToLetter?.(selPort, hit.letterIndex ?? 0, hit.type);
        return;
      }
    }

    // Wire connect tool
    if (tool === 'wireconnect') {
      const hit = hitPixel(mx, my);
      if (hit) {
        onWireConnectClick?.(hit.id);
        render();
      }
      return;
    }

    // Select tool
    if (tool === 'select') {
      const hit = hitPixel(mx, my);
      if (hit) {
        onPixelSelect([hit.id], e.shiftKey);
        isDraggingRef.current = true;
        dragStartRef.current = { mx, my };
        dragPixelRef.current = hit;
      } else {
        if (!e.shiftKey) onPixelSelect([], false);
        isRubberBandRef.current = true;
        rubberStartRef.current = { mx, my };
        rubberCurRef.current = { mx, my };
      }
    }
  };

  const handleMouseMove = (e) => {
    const { mx, my } = getPos(e);

    if (isPanningRef.current) {
      const ps = panStartRef.current;
      offsetRef.current = { x: ps.ox + (mx - ps.mx), y: ps.oy + (my - ps.my) };
      render();
      return;
    }

    if (isDraggingPortRef.current && dragPortIdxRef.current !== null) {
      const ds = dragStartRef.current;
      const port = portNodesRef.current?.find(p => p.portIndex === dragPortIdxRef.current);
      if (port) {
        const newX = port.x + (mx - ds.mx) / scaleRef.current;
        const newY = port.y + (my - ds.my) / scaleRef.current;
        livePortRef.current = { ...livePortRef.current, [dragPortIdxRef.current]: { x: newX, y: newY } };
        render();
      }
      return;
    }

    if (isDraggingRef.current && dragPixelRef.current) {
      const dp = dragPixelRef.current;
      const ds = dragStartRef.current;
      const newX = dp.x + (mx - ds.mx) / scaleRef.current;
      const newY = dp.y + (my - ds.my) / scaleRef.current;
      livePixelRef.current = { ...livePixelRef.current, [dp.id]: { x: newX, y: newY } };
      render();
      return;
    }

    if (isRubberBandRef.current) {
      rubberCurRef.current = { mx, my };
      render();
      return;
    }

    // Cursor
    const canvas = canvasRef.current;
    if (!canvas) return;
    if (toolRef.current === 'pan') { canvas.style.cursor = 'grab'; return; }
    if (toolRef.current === 'wireconnect') { canvas.style.cursor = 'crosshair'; return; }
    if (hitPortNode(mx, my)) { canvas.style.cursor = 'grab'; return; }
    canvas.style.cursor = hitPixel(mx, my) ? 'grab' : 'default';
  };

  const handleMouseUp = () => {
    if (isPanningRef.current) { isPanningRef.current = false; return; }

    if (isDraggingPortRef.current && dragPortIdxRef.current !== null) {
      const portIdx = dragPortIdxRef.current;
      const live = livePortRef.current[portIdx];
      if (live) {
        onPortNodeMove?.(portIdx, live.x, live.y);
        livePortRef.current = {};
      }
      isDraggingPortRef.current = false;
      dragPortIdxRef.current = null;
      return;
    }

    if (isDraggingRef.current && dragPixelRef.current) {
      const dp = dragPixelRef.current;
      const live = livePixelRef.current[dp.id];
      if (live) {
        onPixelMove(dp.id, live.x, live.y);
        livePixelRef.current = {};
      }
      isDraggingRef.current = false;
      dragPixelRef.current = null;
    }

    if (isRubberBandRef.current) {
      isRubberBandRef.current = false;
      const rs = rubberStartRef.current, rc = rubberCurRef.current;
      const mmA = toMm(rs.mx, rs.my), mmB = toMm(rc.mx, rc.my);
      const ids = pixelsInRect(mmA.x, mmA.y, mmB.x, mmB.y);
      if (ids.length) onPixelSelect(ids, false);
      render();
    }
  };

  const handleWheel = (e) => {
    e.preventDefault();
    const { mx, my } = getPos(e);
    const { x: ox, y: oy } = offsetRef.current;
    const delta = e.deltaY < 0 ? 1.15 : 0.87;
    const ns = Math.max(0.2, Math.min(20, scaleRef.current * delta));
    offsetRef.current = {
      x: mx - (mx - ox) * (ns / scaleRef.current),
      y: my - (my - oy) * (ns / scaleRef.current)
    };
    scaleRef.current = ns;
    render();
  };

  const handleContextMenu = (e) => {
    e.preventDefault();
    onContextMenu?.(e);
  };

  return (
    <div ref={containerRef} style={{ width: '100%', height: '100%', position: 'relative' }}>
      <canvas
        ref={canvasRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        onContextMenu={handleContextMenu}
        style={{ display: 'block' }}
      />
    </div>
  );
});

export default LedCanvas;
