/**
 * SANTÉA 3D Engine — Three.js WebGL Background
 * Immersive medical-themed particle & geometry layer
 * Uses Three.js from CDN (r158 — module build)
 */
(function initSanteaEngine() {
  'use strict';

  /* ── Config ─────────────────────────────────────────────────────── */
  const CFG = {
    particleCount:    180,    // total background particles
    dnaCount:         3,      // double-helix DNA strands
    pulseCount:       5,      // pulsing sphere orbs
    connectionDist:   120,    // max distance for particle connections
    mouseInfluence:   0.0015, // mouse parallax strength
    autoRotateSpeed:  0.0003, // base scene rotation speed
    reducedOnMobile:  true,   // halve particles on small screens
  };

  /* ── WebGL check ────────────────────────────────────────────────── */
  function hasWebGL() {
    try {
      const c = document.createElement('canvas');
      return !!(
        c.getContext('webgl2') ||
        c.getContext('webgl') ||
        c.getContext('experimental-webgl')
      );
    } catch (_) { return false; }
  }

  if (!hasWebGL()) {
    document.documentElement.classList.add('no-webgl');
    console.info('[Santea 3D] WebGL not available — CSS fallback active.');
    return;
  }

  /* ── Load Three.js asynchronously ───────────────────────────────── */
  const THREEJS_URL = 'https://cdn.jsdelivr.net/npm/three@0.158.0/build/three.module.js';

  async function loadThree() {
    try {
      return await import(THREEJS_URL);
    } catch (e) {
      console.warn('[Santea 3D] Three.js failed to load:', e);
      document.documentElement.classList.add('no-webgl');
      return null;
    }
  }

  /* ── Color helpers ──────────────────────────────────────────────── */
  const PALETTE = {
    primary: 0x0D8ABC,
    teal:    0x00c9a7,
    green:   0x22c55e,
    blue:    0x3b82f6,
    white:   0xffffff,
  };

  function isDark() {
    return document.body.getAttribute('data-theme') === 'dark';
  }

  /* ── Main boot ──────────────────────────────────────────────────── */
  async function boot() {
    const THREE = await loadThree();
    if (!THREE) return;

    /* Canvas */
    const canvas = document.createElement('canvas');
    canvas.id = 'santea-canvas-bg';
    document.body.prepend(canvas);

    /* Renderer */
    const renderer = new THREE.WebGLRenderer({
      canvas,
      alpha: true,
      antialias: false, // kept off for perf
      powerPreference: 'low-power',
    });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5));
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setClearColor(0x000000, 0);

    /* Scene & Camera */
    const scene  = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(70, window.innerWidth / window.innerHeight, 0.1, 2000);
    camera.position.z = 500;

    /* ── Particle System ──────────────────────────────────────────── */
    const isMobile = window.innerWidth < 768;
    const pCount = isMobile && CFG.reducedOnMobile ? Math.floor(CFG.particleCount / 2) : CFG.particleCount;

    const pPositions = new Float32Array(pCount * 3);
    const pVelocities = [];
    const pColors = new Float32Array(pCount * 3);

    const colorPool = [
      new THREE.Color(PALETTE.primary),
      new THREE.Color(PALETTE.teal),
      new THREE.Color(PALETTE.blue),
      new THREE.Color(PALETTE.green),
    ];

    for (let i = 0; i < pCount; i++) {
      const i3 = i * 3;
      pPositions[i3]   = (Math.random() - 0.5) * window.innerWidth * 1.4;
      pPositions[i3+1] = (Math.random() - 0.5) * window.innerHeight * 1.4;
      pPositions[i3+2] = (Math.random() - 0.5) * 300;

      pVelocities.push({
        x: (Math.random() - 0.5) * 0.25,
        y: (Math.random() - 0.5) * 0.25,
        z: (Math.random() - 0.5) * 0.08,
      });

      const c = colorPool[Math.floor(Math.random() * colorPool.length)];
      pColors[i3]   = c.r;
      pColors[i3+1] = c.g;
      pColors[i3+2] = c.b;
    }

    const pGeo = new THREE.BufferGeometry();
    pGeo.setAttribute('position', new THREE.BufferAttribute(pPositions, 3));
    pGeo.setAttribute('color', new THREE.BufferAttribute(pColors, 3));

    const pMat = new THREE.PointsMaterial({
      size: 2.5,
      vertexColors: true,
      transparent: true,
      opacity: isDark() ? 0.6 : 0.4,
      sizeAttenuation: true,
      depthWrite: false,
    });

    const particles = new THREE.Points(pGeo, pMat);
    scene.add(particles);

    /* ── Line Connections (shared geometry, updated per frame) ──── */
    const maxLines = pCount * 2;
    const linePositions = new Float32Array(maxLines * 2 * 3);
    const lineColors    = new Float32Array(maxLines * 2 * 3);

    const lineGeo = new THREE.BufferGeometry();
    const linePosBuf = new THREE.BufferAttribute(linePositions, 3);
    const lineColBuf = new THREE.BufferAttribute(lineColors, 3);
    linePosBuf.setUsage(THREE.DynamicDrawUsage);
    lineColBuf.setUsage(THREE.DynamicDrawUsage);
    lineGeo.setAttribute('position', linePosBuf);
    lineGeo.setAttribute('color', lineColBuf);
    lineGeo.setDrawRange(0, 0);

    const lineMat = new THREE.LineSegments(lineGeo, new THREE.LineBasicMaterial({
      vertexColors: true,
      transparent: true,
      opacity: isDark() ? 0.25 : 0.15,
      depthWrite: false,
    }));
    scene.add(lineMat);

    /* ── DNA Double Helix ─────────────────────────────────────────── */
    if (!isMobile) {
      const DNAGroup = new THREE.Group();
      DNAGroup.position.set(window.innerWidth * 0.38, 0, -80);
      scene.add(DNAGroup);

      const helixSteps = 60;
      const helixRadius = 28;
      const helixHeight = 280;
      const helixTurns = 3;

      const strandMat1 = new THREE.MeshBasicMaterial({ color: PALETTE.primary, transparent: true, opacity: 0.55 });
      const strandMat2 = new THREE.MeshBasicMaterial({ color: PALETTE.teal,    transparent: true, opacity: 0.55 });
      const bridgeMat  = new THREE.MeshBasicMaterial({ color: PALETTE.white,   transparent: true, opacity: 0.3 });
      const sphereGeo  = new THREE.SphereGeometry(2.5, 6, 6);

      for (let i = 0; i <= helixSteps; i++) {
        const t = i / helixSteps;
        const angle = t * Math.PI * 2 * helixTurns;
        const y = (t - 0.5) * helixHeight;

        const x1 = Math.cos(angle) * helixRadius;
        const z1 = Math.sin(angle) * helixRadius;
        const x2 = Math.cos(angle + Math.PI) * helixRadius;
        const z2 = Math.sin(angle + Math.PI) * helixRadius;

        const s1 = new THREE.Mesh(sphereGeo, strandMat1);
        s1.position.set(x1, y, z1);
        DNAGroup.add(s1);

        const s2 = new THREE.Mesh(sphereGeo, strandMat2);
        s2.position.set(x2, y, z2);
        DNAGroup.add(s2);

        if (i % 6 === 0 && i < helixSteps) {
          const bGeo = new THREE.CylinderGeometry(0.8, 0.8, helixRadius * 2, 4);
          const bridge = new THREE.Mesh(bGeo, bridgeMat);
          bridge.position.set((x1+x2)/2, y, (z1+z2)/2);
          bridge.lookAt(new THREE.Vector3(x1, y, z1));
          bridge.rotateX(Math.PI / 2);
          DNAGroup.add(bridge);
        }
      }
    }

    /* ── Pulsing Orbs ─────────────────────────────────────────────── */
    const orbs = [];
    for (let i = 0; i < CFG.pulseCount; i++) {
      const radius = 15 + Math.random() * 20;
      const geo = new THREE.SphereGeometry(radius, 20, 20);
      const mat = new THREE.MeshBasicMaterial({
        color: colorPool[i % colorPool.length],
        transparent: true,
        opacity: 0.0,
        wireframe: true,
      });
      const orb = new THREE.Mesh(geo, mat);
      orb.position.set(
        (Math.random() - 0.5) * window.innerWidth * 0.8,
        (Math.random() - 0.5) * window.innerHeight * 0.8,
        (Math.random() - 0.5) * 100
      );
      orb.userData.phase = Math.random() * Math.PI * 2;
      scene.add(orb);
      orbs.push(orb);
    }

    /* ── Mouse parallax ───────────────────────────────────────────── */
    const mouse = { x: 0, y: 0, targetX: 0, targetY: 0 };
    window.addEventListener('mousemove', (e) => {
      mouse.targetX = (e.clientX / window.innerWidth  - 0.5) * 2;
      mouse.targetY = (e.clientY / window.innerHeight - 0.5) * 2;
    }, { passive: true });

    /* ── Resize ───────────────────────────────────────────────────── */
    let resizeRaf;
    window.addEventListener('resize', () => {
      clearTimeout(resizeRaf);
      resizeRaf = setTimeout(() => {
        renderer.setSize(window.innerWidth, window.innerHeight);
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
      }, 200);
    });

    /* ── Dark-mode watcher ────────────────────────────────────────── */
    const themeObs = new MutationObserver(() => {
      const dark = isDark();
      pMat.opacity = dark ? 0.6 : 0.4;
      lineMat.material.opacity = dark ? 0.25 : 0.15;
    });
    themeObs.observe(document.body, { attributes: true, attributeFilter: ['data-theme'] });

    /* ── Animation Loop ───────────────────────────────────────────── */
    let frame = 0;
    const clock = new THREE.Clock();

    function animate() {
      requestAnimationFrame(animate);
      frame++;
      const t = clock.getElapsedTime();

      /* Mouse lerp */
      mouse.x += (mouse.targetX - mouse.x) * 0.04;
      mouse.y += (mouse.targetY - mouse.y) * 0.04;
      scene.rotation.y = mouse.x * 0.08;
      scene.rotation.x = -mouse.y * 0.05;

      /* Move particles */
      const pos = pGeo.attributes.position.array;
      const hw = window.innerWidth * 0.7;
      const hh = window.innerHeight * 0.7;
      for (let i = 0; i < pCount; i++) {
        const i3 = i * 3;
        const v = pVelocities[i];
        pos[i3]   += v.x;
        pos[i3+1] += v.y;
        pos[i3+2] += v.z;
        if (pos[i3]   >  hw) pos[i3]   = -hw;
        if (pos[i3]   < -hw) pos[i3]   =  hw;
        if (pos[i3+1] >  hh) pos[i3+1] = -hh;
        if (pos[i3+1] < -hh) pos[i3+1] =  hh;
      }
      pGeo.attributes.position.needsUpdate = true;

      /* Connection lines (every 3rd frame for perf) */
      if (frame % 3 === 0) {
        let lineIdx = 0;
        for (let i = 0; i < pCount && lineIdx < maxLines - 1; i++) {
          for (let j = i + 1; j < pCount && lineIdx < maxLines - 1; j++) {
            const dx = pos[i*3] - pos[j*3];
            const dy = pos[i*3+1] - pos[j*3+1];
            const dz = pos[i*3+2] - pos[j*3+2];
            const dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (dist < CFG.connectionDist) {
              const li2 = lineIdx * 6;
              linePositions[li2]   = pos[i*3];   linePositions[li2+1] = pos[i*3+1]; linePositions[li2+2] = pos[i*3+2];
              linePositions[li2+3] = pos[j*3];   linePositions[li2+4] = pos[j*3+1]; linePositions[li2+5] = pos[j*3+2];
              const alpha = 1 - dist / CFG.connectionDist;
              lineColors[li2]   = pColors[i*3];   lineColors[li2+1] = pColors[i*3+1]; lineColors[li2+2] = pColors[i*3+2];
              lineColors[li2+3] = pColors[j*3];   lineColors[li2+4] = pColors[j*3+1]; lineColors[li2+5] = pColors[j*3+2];
              lineIdx++;
            }
          }
        }
        lineGeo.setDrawRange(0, lineIdx * 2);
        linePosBuf.needsUpdate = true;
        lineColBuf.needsUpdate = true;
      }

      /* Pulsing orbs */
      orbs.forEach((orb, i) => {
        const phase = orb.userData.phase + t * 0.6;
        orb.material.opacity = (Math.sin(phase) * 0.5 + 0.5) * 0.08;
        orb.scale.setScalar(1 + Math.sin(phase + i) * 0.15);
        orb.rotation.y = t * 0.2 * (i % 2 === 0 ? 1 : -1);
      });

      /* DNA helix rotation */
      scene.children.forEach(child => {
        if (child.isGroup) {
          child.rotation.y = t * 0.4;
        }
      });

      renderer.render(scene, camera);
    }

    /* Show canvas after first render */
    setTimeout(() => canvas.classList.add('ready'), 100);
    animate();
  }

  /* Defer until page is idle */
  if ('requestIdleCallback' in window) {
    requestIdleCallback(boot, { timeout: 2000 });
  } else {
    setTimeout(boot, 500);
  }
})();
