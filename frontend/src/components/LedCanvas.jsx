import React, { useEffect, useRef, useCallback } from 'react';
import { PORT_COLORS, LETTER_COLORS, computeLetterZoom, getLetterStartPixel } from '../utils/wireUtils';

const WIRING_COLOR   = 'rgba(255,200,30,0.7)';
const PENDING_COLOR  = 'rgba(0,212,255,0.9)';
const GUIDE_FILL     = 'rgba(15,40,100,0.35)';
const GUIDE_STROKE   = '#1E7FFF';        // thick blue guide border
const SEL_RING_COLOR = 'rgba(255,255,255,0.9)';
const PORT_NODE_SIZE = 18; // px on screen
const DISCONNECTED_COLOR = 'rgba(255,107,107,0.8)';

export default function LedCanvas({
  pixels, wiringOrder, guideCommands,
  selectedIds, activeTool, pixelOdMm,
  showNumbers, showWiring, showGuide,
  wiringMode, pendingWire,
  isBreakApart, selectedLetterIndex,
  onPixelMove, onPixelSelect, onWireClick,
  onLetterSelect, onAddPixel,
  onEscape, lastAction,
  zoomRef,
  portNodes, onPortNodeMove, letterPortMap, disconnectedAfter,
  selectedPortIndex, onConnectPortToLetter, activePortTool
}) {
  const containerRef = useRef(null);
  const canvasRef    = useRef(null);
  const scaleRef     = useRef(2);
  const offsetRef    = useRef({ x: 40, y: 40 });
  const dprRef       = useRef(1);

  // Interaction refs
  const isDraggingRef    = useRef(false);
  const dragPixelRef     = useRef(null);
  const dragStartRef     = useRef({});
  const isPanningRef     = useRef(false);
  const panStartRef      = useRef({});
  const isRubberBandRef  = useRef(false);
  const rubberStartRef   = useRef({ mx: 0, my: 0 });
  const rubberCurRef     = useRef({ mx: 0, my: 0 });
  const livePixelRef     = useRef({});
  
  // Port dragging refs
  const isDraggingPortRef = useRef(false);
  const dragPortIdxRef    = useRef(null);
  const livePortRef       = useRef({});
  
  // Right-click tracking for escape
  const lastRightClickRef = useRef(0);

  // Props accessible in event handlers via refs
  const pixelsRef       = useRef(pixels);
  const wiringRef       = useRef(wiringOrder);
  const guidesRef       = useRef(guideCommands);
  const selRef          = useRef(selectedIds);
  const toolRef         = useRef(activeTool);
  const pendingRef      = useRef(pendingWire);
  const showNumRef      = useRef(showNumbers);
  const showWireRef     = useRef(showWiring);
  const showGuideRef    = useRef(showGuide);
  const odRef           = useRef(pixelOdMm);
  const breakApartRef   = useRef(isBreakApart);
  const selLetterRef    = useRef(selectedLetterIndex);
  const portNodesRef    = useRef(portNodes);
  const letterPortMapRef = useRef(letterPortMap);
  const disconnectedRef  = useRef(disconnectedAfter);
  const selPortIdxRef    = useRef(selectedPortIndex);

  pixelsRef.current     = pixels;
  wiringRef.current     = wiringOrder;
  guidesRef.current     = guideCommands;
  selRef.current        = selectedIds;
  toolRef.current       = activeTool;
  pendingRef.current    = pendingWire;
  showNumRef.current    = showNumbers;
  showWireRef.current   = showWiring;
  showGuideRef.current  = showGuide;
  odRef.current         = pixelOdMm;
  breakApartRef.current = isBreakApart;
  selLetterRef.current  = selectedLetterIndex;
  portNodesRef.current  = portNodes;
  letterPortMapRef.current = letterPortMap;
  disconnectedRef.current = disconnectedAfter;
  selPortIdxRef.current = selectedPortIndex;

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
    const portR = PORT_NODE_SIZE / 2 / scaleRef.current; // Convert screen px to mm
    for (const port of ports) {
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
      .filter(p => {
        const px = livePixelRef.current[p.id]?.x ?? p.x;
        const py = livePixelRef.current[p.id]?.y ?? p.y;
        return px >= minX && px <= maxX && py >= minY && py <= maxY;
      })
      .map(p => p.id);
  }

  // ── Main render ─────────────────────────────────────────────────────────
  const render = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const dpr = dprRef.current;
    const W = canvas.width / dpr;
    const H = canvas.height / dpr;
    const ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    ctx.fillStyle = '#0d0e1a';
    ctx.fillRect(0, 0, W, H);
    drawGrid(ctx, W, H);

    const scale   = scaleRef.current;
    const offset  = offsetRef.current;
    const pixels  = pixelsRef.current;
    const guides  = guidesRef.current;
    const wOrder  = wiringRef.current;
    const selIds  = selRef.current;
    const pending = pendingRef.current;
    const od      = odRef.current;
    const r       = (od / 2) * scale;
    const breakApart   = breakApartRef.current;
    const selLetter    = selLetterRef.current;

    // ── Letter guide paths ────────────────────────────────────────────────
    if (showGuideRef.current && guides.length) {
      // Fill
      ctx.save();
      ctx.fillStyle = GUIDE_FILL;
      for (const g of guides) {
        ctx.beginPath();
        drawPathCmds(ctx, g.commands, scale, offset);
        ctx.fill('evenodd');
      }
      ctx.restore();

      // Thick blue stroke – constant 3px regardless of zoom
      ctx.save();
      ctx.strokeStyle = GUIDE_STROKE;
      ctx.lineWidth = 3;
      ctx.shadowColor = 'rgba(30,127,255,0.5)';
      ctx.shadowBlur = 8;
      for (const g of guides) {
        ctx.beginPath();
        drawPathCmds(ctx, g.commands, scale, offset);
        ctx.stroke();
      }
      ctx.shadowBlur = 0;
      ctx.restore();
    }

    // ── Wiring ────────────────────────────────────────────────────────────
    if (showWireRef.current && wOrder.length > 1) {
      const pmap = buildMap(pixels);
      ctx.save();
      ctx.strokeStyle = WIRING_COLOR;
      ctx.lineWidth = Math.max(1, scale * 0.35);
      ctx.setLineDash([scale * 0.5, scale * 0.5]);
      ctx.beginPath();
      let prevBroken = false;
      wOrder.forEach((id, i) => {
        const p = pmap[id]; if (!p) return;
        const px = livePixelRef.current[id]?.x ?? p.x;
        const py = livePixelRef.current[id]?.y ?? p.y;
        const { sx, sy } = toScreen(px, py);
        if (i === 0 || prevBroken || p.wiringBroken) ctx.moveTo(sx, sy);
        else ctx.lineTo(sx, sy);
        prevBroken = p.wiringBroken ?? false;
      });
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.restore();
    }

    // ── Pending wire ──────────────────────────────────────────────────────
    if (pending.length > 1) {
      const pmap = buildMap(pixels);
      ctx.save();
      ctx.strokeStyle = PENDING_COLOR;
      ctx.lineWidth = 2.5;
      ctx.shadowColor = PENDING_COLOR; ctx.shadowBlur = 6;
      ctx.beginPath();
      let fp = true;
      for (const id of pending) {
        const p = pmap[id]; if (!p) continue;
        const { sx, sy } = toScreen(p.x, p.y);
        if (fp) { ctx.moveTo(sx, sy); fp = false; } else ctx.lineTo(sx, sy);
      }
      ctx.stroke();
      ctx.shadowBlur = 0;
      ctx.restore();
    }

    // ── Pixels ────────────────────────────────────────────────────────────
    for (const p of pixels) {
      const px = livePixelRef.current[p.id]?.x ?? p.x;
      const py = livePixelRef.current[p.id]?.y ?? p.y;
      const { sx, sy } = toScreen(px, py);
      const isSel     = selIds.has(p.id);
      const isPend    = pending.includes(p.id);
      const portColor = PORT_COLORS[Math.max(0, Math.min(7, p.portIndex ?? 0))];
      const isBroken  = p.wiringBroken ?? false;
      const letterColor = LETTER_COLORS[(p.letterIndex ?? 0) % LETTER_COLORS.length];
      const isSelLetter = breakApart && selLetter !== null && p.letterIndex === selLetter;

      ctx.save();

      // Glow effects
      if (isSel)           { ctx.shadowColor = '#ffffff'; ctx.shadowBlur = 10; }
      else if (isPend)     { ctx.shadowColor = PENDING_COLOR; ctx.shadowBlur = 12; }
      else if (p.isFirst)  { ctx.shadowColor = '#00ff88'; ctx.shadowBlur = 12; }
      else if (p.isLast)   { ctx.shadowColor = '#ff6b6b'; ctx.shadowBlur = 12; }
      else if (isSelLetter){ ctx.shadowColor = letterColor; ctx.shadowBlur = 8; }

      // Main circle
      ctx.beginPath();
      ctx.arc(sx, sy, r, 0, Math.PI * 2);
      let fill = isSel ? 'rgba(255,255,255,0.85)'
               : isPend ? 'rgba(0,212,255,0.85)'
               : portColor + 'cc';
      if (isBroken) fill = 'rgba(80,20,20,0.8)';
      ctx.fillStyle = fill;
      ctx.fill();

      // Stroke ring
      ctx.strokeStyle = isSel ? '#ffffff' : isPend ? '#00d4ff'
                      : isBroken ? '#ff6b6b' : p.isAuto ? portColor : '#ff9f43';
      ctx.lineWidth = isSel ? 2.5 : isBroken ? 2 : 1.5;
      ctx.stroke();
      ctx.shadowBlur = 0;
      ctx.restore();

      // ── Break-apart letter ring ─────────────────────────────────────────
      if (breakApart) {
        ctx.save();
        ctx.strokeStyle = letterColor;
        ctx.lineWidth = isSelLetter ? 3 : 1.5;
        ctx.globalAlpha = isSelLetter ? 1 : 0.5;
        ctx.beginPath();
        ctx.arc(sx, sy, r + (isSelLetter ? 4 : 2), 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }

      // ── Broken wiring mark ──────────────────────────────────────────────
      if (isBroken && r > 4) {
        ctx.save();
        ctx.strokeStyle = '#ff6b6b';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(sx - r * 0.5, sy - r * 0.5); ctx.lineTo(sx + r * 0.5, sy + r * 0.5);
        ctx.moveTo(sx + r * 0.5, sy - r * 0.5); ctx.lineTo(sx - r * 0.5, sy + r * 0.5);
        ctx.stroke();
        ctx.restore();
      }

      // ── Manual dot ─────────────────────────────────────────────────────
      if (!p.isAuto && !isBroken && r > 5) {
        ctx.save();
        ctx.fillStyle = '#ff9f43';
        ctx.beginPath();
        ctx.arc(sx + r * 0.6, sy - r * 0.6, 2.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.restore();
      }
    }

    // ── Prominent FIRST / LAST markers ───────────────────────────────────
    for (const p of pixels) {
      if (!p.isFirst && !p.isLast) continue;
      const px = livePixelRef.current[p.id]?.x ?? p.x;
      const py = livePixelRef.current[p.id]?.y ?? p.y;
      const { sx, sy } = toScreen(px, py);
      const color = p.isFirst ? '#00ff88' : '#ff4444';
      const label = p.isFirst ? 'S' : 'E';

      ctx.save();
      // Outer pulsing ring
      ctx.strokeStyle = color;
      ctx.lineWidth = 2.5;
      ctx.shadowColor = color;
      ctx.shadowBlur = 12;
      ctx.beginPath();
      ctx.arc(sx, sy, r * 1.7, 0, Math.PI * 2);
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Fill center triangle / arrow
      ctx.fillStyle = color;
      const fs = Math.max(9, r * 1.0);
      ctx.font = `bold ${fs}px monospace`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(label, sx, sy);

      // Label above
      if (r > 6) {
        ctx.font = `bold ${Math.max(8, r * 0.7)}px sans-serif`;
        ctx.fillStyle = color;
        ctx.fillText(p.isFirst ? `▶ ${p.letter}` : `${p.letter} ◀`, sx, sy - r * 2.3);
      }
      ctx.restore();
    }

    // ── Pixel numbers ─────────────────────────────────────────────────────
    if (showNumRef.current && r > 5) {
      ctx.save();
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.font = `${Math.max(7, Math.min(11, r * 0.8))}px monospace`;
      for (const p of pixels) {
        if (p.wiringOrder < 0 || p.isFirst || p.isLast) continue;
        const px = livePixelRef.current[p.id]?.x ?? p.x;
        const py = livePixelRef.current[p.id]?.y ?? p.y;
        const { sx, sy } = toScreen(px, py);
        ctx.fillStyle = (p.portIndex ?? 0) < 3 ? '#ffffff' : '#ffffff';
        ctx.fillText(p.wiringOrder, sx, sy);
      }
      ctx.restore();
    }

    // ── Rubber band ───────────────────────────────────────────────────────
    if (isRubberBandRef.current) {
      const rs = rubberStartRef.current;
      const rc = rubberCurRef.current;
      ctx.save();
      ctx.strokeStyle = 'rgba(0,212,255,0.9)';
      ctx.fillStyle   = 'rgba(0,212,255,0.07)';
      ctx.lineWidth   = 1.5;
      ctx.setLineDash([4, 4]);
      const rx = Math.min(rs.mx, rc.mx), ry = Math.min(rs.my, rc.my);
      const rw = Math.abs(rc.mx - rs.mx), rh = Math.abs(rc.my - rs.my);
      ctx.fillRect(rx, ry, rw, rh);
      ctx.strokeRect(rx, ry, rw, rh);
      ctx.setLineDash([]);
      ctx.restore();
    }

    // ── Port nodes and connections ─────────────────────────────────────────
    const ports = portNodesRef.current || [];
    const lpMap = letterPortMapRef.current || {};
    const disconnected = disconnectedRef.current || new Set();
    const selPortIdx = selPortIdxRef.current;
    
    if (pixels.length > 0 && ports.length > 0) {
      // Draw port-to-letter connection lines
      ctx.save();
      for (const [letterIdxStr, portIdx] of Object.entries(lpMap)) {
        const letterIdx = parseInt(letterIdxStr);
        const startPixel = getLetterStartPixel(pixels, letterIdx);
        if (!startPixel) continue;
        
        const port = ports.find(p => p.portIndex === portIdx);
        if (!port) continue;
        
        const portX = livePortRef.current[port.portIndex]?.x ?? port.x;
        const portY = livePortRef.current[port.portIndex]?.y ?? port.y;
        const { sx: psx, sy: psy } = toScreen(portX, portY);
        const { sx: lsx, sy: lsy } = toScreen(startPixel.x, startPixel.y);
        
        // Draw connection line
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
      
      // Draw disconnection markers between letters
      if (disconnected.size > 0) {
        ctx.save();
        for (const letterIdx of disconnected) {
          const endPixel = pixels.find(p => p.letterIndex === letterIdx && p.isLast);
          const nextLetterIdx = letterIdx + 1;
          const nextStartPixel = pixels.find(p => p.letterIndex === nextLetterIdx && p.isFirst);
          
          if (endPixel && nextStartPixel) {
            const { sx: ex, sy: ey } = toScreen(endPixel.x, endPixel.y);
            const { sx: nx, sy: ny } = toScreen(nextStartPixel.x, nextStartPixel.y);
            
            // Draw disconnection X mark in the middle
            const mx = (ex + nx) / 2;
            const my = (ey + ny) / 2;
            
            ctx.strokeStyle = DISCONNECTED_COLOR;
            ctx.lineWidth = 3;
            ctx.beginPath();
            ctx.moveTo(mx - 8, my - 8);
            ctx.lineTo(mx + 8, my + 8);
            ctx.moveTo(mx + 8, my - 8);
            ctx.lineTo(mx - 8, my + 8);
            ctx.stroke();
          }
        }
        ctx.restore();
      }
      
      // Draw port nodes
      ctx.save();
      for (const port of ports) {
        const portX = livePortRef.current[port.portIndex]?.x ?? port.x;
        const portY = livePortRef.current[port.portIndex]?.y ?? port.y;
        const { sx, sy } = toScreen(portX, portY);
        const portColor = PORT_COLORS[port.portIndex];
        const isSelected = selPortIdx === port.portIndex;
        const hasConnection = Object.values(lpMap).includes(port.portIndex);
        
        // Port node background
        ctx.beginPath();
        ctx.arc(sx, sy, PORT_NODE_SIZE / 2, 0, Math.PI * 2);
        ctx.fillStyle = isSelected ? portColor : hasConnection ? portColor + 'cc' : portColor + '66';
        ctx.fill();
        
        // Port node border
        ctx.strokeStyle = isSelected ? '#ffffff' : portColor;
        ctx.lineWidth = isSelected ? 3 : 2;
        ctx.stroke();
        
        // Port label
        ctx.fillStyle = '#ffffff';
        ctx.font = 'bold 10px monospace';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(port.label, sx, sy);
        
        // Glow for selected port
        if (isSelected) {
          ctx.shadowColor = portColor;
          ctx.shadowBlur = 12;
          ctx.beginPath();
          ctx.arc(sx, sy, PORT_NODE_SIZE / 2 + 4, 0, Math.PI * 2);
          ctx.strokeStyle = portColor;
          ctx.lineWidth = 2;
          ctx.stroke();
          ctx.shadowBlur = 0;
        }
      }
      ctx.restore();
    }

    // Placeholder
    if (!pixels.length && !guides.length) {
      ctx.save();
      ctx.fillStyle = 'rgba(107,114,128,0.45)';
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.font = '15px system-ui';
      ctx.fillText('Upload a font, enter text, and click Generate', W / 2, H / 2 - 14);
      ctx.font = '11px system-ui';
      ctx.fillText('Scroll=Zoom · Drag=Pan · Right-click=Add pixel', W / 2, H / 2 + 10);
      ctx.restore();
    }
  }, []);

  // ── Helpers ──────────────────────────────────────────────────────────────
  function drawGrid(ctx, W, H) {
    const scale = scaleRef.current;
    const { x: ox, y: oy } = offsetRef.current;
    const gridMm = scale >= 4 ? 10 : scale >= 1 ? 20 : 50;
    const gridPx = gridMm * scale;
    ctx.save();
    ctx.strokeStyle = 'rgba(42,45,74,0.55)';
    ctx.lineWidth = 0.5;
    for (let x = ((ox % gridPx) + gridPx) % gridPx; x <= W; x += gridPx) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
    }
    for (let y = ((oy % gridPx) + gridPx) % gridPx; y <= H; y += gridPx) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }
    ctx.restore();
  }

  function drawPathCmds(ctx, cmds, scale, offset) {
    for (const c of cmds) {
      const sx = c.x * scale + offset.x;
      const sy = c.y * scale + offset.y;
      switch (c.type) {
        case 'M': ctx.moveTo(sx, sy); break;
        case 'L': ctx.lineTo(sx, sy); break;
        case 'C':
          ctx.bezierCurveTo(c.x1*scale+offset.x, c.y1*scale+offset.y,
            c.x2*scale+offset.x, c.y2*scale+offset.y, sx, sy); break;
        case 'Q':
          ctx.quadraticCurveTo(c.x1*scale+offset.x, c.y1*scale+offset.y, sx, sy); break;
        case 'Z': ctx.closePath(); break;
        default: break;
      }
    }
  }

  function buildMap(arr) {
    const m = {}; for (const p of arr) m[p.id] = p; return m;
  }

  // ── Resize observer ──────────────────────────────────────────────────────
  useEffect(() => {
    const container = containerRef.current;
    const canvas = canvasRef.current;
    if (!container || !canvas) return;
    const ro = new ResizeObserver(entries => {
      for (const e of entries) {
        const { width, height } = e.contentRect;
        const dpr = window.devicePixelRatio || 1;
        dprRef.current = dpr;
        canvas.width = width * dpr; canvas.height = height * dpr;
        canvas.style.width = width + 'px'; canvas.style.height = height + 'px';
        render();
      }
    });
    ro.observe(container);
    return () => ro.disconnect();
  }, [render]);

  // Re-render on props change
  useEffect(() => { render(); }, [
    pixels, wiringOrder, guideCommands, selectedIds,
    showNumbers, showWiring, showGuide, pendingWire, pixelOdMm,
    isBreakApart, selectedLetterIndex, render
  ]);

  // ── Zoom-to-letter callback ──────────────────────────────────────────────
  useEffect(() => {
    if (!zoomRef) return;
    zoomRef.current = (letterIndex) => {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const { width: W, height: H } = canvas.getBoundingClientRect();
      const result = computeLetterZoom(letterIndex, pixelsRef.current, W, H, 60);
      if (result) {
        scaleRef.current = result.scale;
        offsetRef.current = result.offset;
        render();
      }
    };
  }, [zoomRef, render]);

  // ── Event handlers ───────────────────────────────────────────────────────
  const getPos = (e) => {
    const r = canvasRef.current.getBoundingClientRect();
    return { mx: e.clientX - r.left, my: e.clientY - r.top };
  };

  const handleMouseDown = (e) => {
    e.preventDefault();
    const { mx, my } = getPos(e);
    const tool = toolRef.current;

    // Right-click → add pixel
    if (e.button === 2) {
      const { x, y } = toMm(mx, my);
      onAddPixel(x, y);
      return;
    }
    // Middle / Pan tool
    if (e.button === 1 || tool === 'pan') {
      isPanningRef.current = true;
      panStartRef.current = { mx, my, ox: offsetRef.current.x, oy: offsetRef.current.y };
      return;
    }

    if (tool === 'wire') {
      const hit = hitPixel(mx, my);
      if (hit) { onWireClick(hit.id); render(); }
      return;
    }

    if (tool === 'select') {
      const hit = hitPixel(mx, my);
      if (hit) {
        // Break-apart: clicking a pixel selects its letter
        if (breakApartRef.current) {
          onLetterSelect(hit.letterIndex ?? 0);
          return;
        }
        onPixelSelect([hit.id], e.shiftKey);
        isDraggingRef.current = true;
        dragStartRef.current  = { mx, my };
        dragPixelRef.current  = hit;
      } else {
        // Start rubber-band selection
        if (!e.shiftKey) onPixelSelect([], false);
        isRubberBandRef.current = true;
        rubberStartRef.current  = { mx, my };
        rubberCurRef.current    = { mx, my };
      }
    }
  };

  const handleMouseMove = (e) => {
    const { mx, my } = getPos(e);

    if (isPanningRef.current) {
      const ps = panStartRef.current;
      offsetRef.current = { x: ps.ox + (mx - ps.mx), y: ps.oy + (my - ps.my) };
      render(); return;
    }
    if (isDraggingRef.current && dragPixelRef.current) {
      const dp = dragPixelRef.current;
      const ds = dragStartRef.current;
      const newX = dp.x + (mx - ds.mx) / scaleRef.current;
      const newY = dp.y + (my - ds.my) / scaleRef.current;
      livePixelRef.current = { ...livePixelRef.current, [dp.id]: { x: newX, y: newY } };
      render(); return;
    }
    if (isRubberBandRef.current) {
      rubberCurRef.current = { mx, my };
      render(); return;
    }

    // Cursor
    const canvas = canvasRef.current;
    if (!canvas) return;
    if (toolRef.current === 'pan') { canvas.style.cursor = 'grab'; return; }
    if (toolRef.current === 'wire') { canvas.style.cursor = 'crosshair'; return; }
    canvas.style.cursor = hitPixel(mx, my) ? 'grab' : 'default';
  };

  const handleMouseUp = (e) => {
    if (isPanningRef.current) { isPanningRef.current = false; return; }
    if (isDraggingRef.current && dragPixelRef.current) {
      const dp = dragPixelRef.current;
      const live = livePixelRef.current[dp.id];
      if (live) { onPixelMove(dp.id, live.x, live.y); livePixelRef.current = {}; }
      isDraggingRef.current = false; dragPixelRef.current = null;
    }
    if (isRubberBandRef.current) {
      isRubberBandRef.current = false;
      const rs = rubberStartRef.current, rc = rubberCurRef.current;
      const mmA = toMm(rs.mx, rs.my), mmB = toMm(rc.mx, rc.my);
      const ids = pixelsInRect(mmA.x, mmA.y, mmB.x, mmB.y);
      if (ids.length) onPixelSelect(ids, e.shiftKey);
      render();
    }
  };

  const handleWheel = (e) => {
    e.preventDefault();
    const { mx, my } = getPos(e);
    const factor = e.deltaY < 0 ? 1.12 : 0.89;
    const ns = Math.min(25, Math.max(0.15, scaleRef.current * factor));
    const ox = offsetRef.current.x, oy = offsetRef.current.y;
    offsetRef.current = {
      x: mx - (mx - ox) * (ns / scaleRef.current),
      y: my - (my - oy) * (ns / scaleRef.current)
    };
    scaleRef.current = ns;
    render();
  };

  return (
    <div ref={containerRef} className="canvas-container" data-testid="led-canvas-container">
      <canvas ref={canvasRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        onContextMenu={e => e.preventDefault()}
        data-testid="led-canvas"
      />
    </div>
  );
}
