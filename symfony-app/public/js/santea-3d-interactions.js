/**
 * SANTÉA — 3D Interactions & UX Layer
 * - Scroll progress bar
 * - Scroll reveal
 * - Card tilt (mouse 3D)
 * - Page transitions
 * - Hero 2D canvas particles (always-on fallback)
 * - Parallax elements
 * - Counter animations
 */
(function () {
  'use strict';

  /* ═══════════════════════════════════════════════════════════════════
     1. SCROLL PROGRESS BAR
  ═══════════════════════════════════════════════════════════════════ */
  (function initScrollProgress() {
    const bar = document.getElementById('s3d-scroll-progress');
    if (!bar) return;
    function update() {
      const scrolled = window.scrollY;
      const total = document.documentElement.scrollHeight - window.innerHeight;
      bar.style.width = total > 0 ? ((scrolled / total) * 100) + '%' : '0%';
    }
    window.addEventListener('scroll', update, { passive: true });
    update();
  })();

  /* ═══════════════════════════════════════════════════════════════════
     2. SCROLL REVEAL
  ═══════════════════════════════════════════════════════════════════ */
  (function initScrollReveal() {
    const els = document.querySelectorAll(
      '.feature-card, .stat-card-hero, .doctor-card, .s3d-reveal, .s3d-reveal-left, .s3d-reveal-right'
    );
    if (!els.length) return;

    // Add defaults
    els.forEach((el, i) => {
      if (!el.classList.contains('s3d-reveal') &&
          !el.classList.contains('s3d-reveal-left') &&
          !el.classList.contains('s3d-reveal-right')) {
        el.classList.add('s3d-reveal');
        // stagger siblings inside same parent
        const siblings = el.parentElement ? el.parentElement.children : [];
        const idx = Array.from(siblings).indexOf(el);
        if (idx > 0 && idx <= 5) el.classList.add(`s3d-delay-${idx}`);
      }
    });

    const IO = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          IO.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -40px 0px' });

    document.querySelectorAll(
      '.feature-card, .stat-card-hero, .doctor-card, .s3d-reveal, .s3d-reveal-left, .s3d-reveal-right'
    ).forEach(el => IO.observe(el));
  })();

  /* ═══════════════════════════════════════════════════════════════════
     3. CARD 3D TILT (mouse perspective)
  ═══════════════════════════════════════════════════════════════════ */
  (function initCardTilt() {
    const MAX_TILT = 8; // degrees
    const SCALE = 1.02;
    const GLARE = true;

    function createGlare(card) {
      if (!GLARE) return null;
      const glare = document.createElement('div');
      glare.style.cssText = `
        position:absolute;inset:0;border-radius:inherit;
        background:radial-gradient(circle at 50% 50%, rgba(255,255,255,.35) 0%, transparent 60%);
        pointer-events:none;opacity:0;transition:opacity .3s;z-index:2;
      `;
      card.style.position = 'relative';
      card.style.overflow = 'hidden';
      card.appendChild(glare);
      return glare;
    }

    function applyTilt(el) {
      const glare = createGlare(el);

      el.addEventListener('mousemove', (e) => {
        const rect = el.getBoundingClientRect();
        const cx = rect.left + rect.width / 2;
        const cy = rect.top + rect.height / 2;
        const dx = (e.clientX - cx) / (rect.width / 2);
        const dy = (e.clientY - cy) / (rect.height / 2);

        const rotX = -dy * MAX_TILT;
        const rotY =  dx * MAX_TILT;

        el.style.transform = `perspective(800px) rotateX(${rotX}deg) rotateY(${rotY}deg) scale(${SCALE})`;
        el.style.transition = 'transform .05s linear';

        if (glare) {
          const angle = Math.atan2(dy, dx) * (180 / Math.PI) + 90;
          glare.style.background = `radial-gradient(circle at ${50 + dx*35}% ${50 + dy*35}%, rgba(255,255,255,.28) 0%, transparent 60%)`;
          glare.style.opacity = '1';
        }
      });

      el.addEventListener('mouseleave', () => {
        el.style.transition = 'transform .45s cubic-bezier(.4,0,.2,1)';
        el.style.transform  = 'perspective(800px) rotateX(0deg) rotateY(0deg) scale(1)';
        if (glare) glare.style.opacity = '0';
      });
    }

    // Only on non-touch devices
    if (!('ontouchstart' in window)) {
      document.querySelectorAll('.feature-card, .stat-card-hero, .metric-card, .dashboard-card').forEach(applyTilt);
    }
  })();

  /* ═══════════════════════════════════════════════════════════════════
     4. PAGE TRANSITION
  ═══════════════════════════════════════════════════════════════════ */
  (function initPageTransition() {
    const overlay = document.getElementById('s3d-page-transition');
    if (!overlay) return;

    // Ensure overlay is cleared on every page show (bfcache support)
    const clearOverlay = () => {
      overlay.classList.remove('entering');
      overlay.classList.add('leaving');
      setTimeout(() => overlay.classList.remove('leaving'), 500);
    };

    // Animate in when clicked (only for real cross-page navigations)
    document.querySelectorAll('a[href]').forEach(link => {
      const href = link.getAttribute('href');
      if (!href || href.startsWith('#') || href.startsWith('javascript:') || link.target === '_blank') return;
      link.addEventListener('click', () => {
        // Skip same-page hash navigation (e.g. /dashboard#panel)
        try {
          const dest = new URL(link.href, location.href);
          if (dest.pathname === location.pathname) return;
        } catch (_) {}
        overlay.classList.add('entering');
      });
    });

    window.addEventListener('load', clearOverlay);
    // bfcache: browser restores page from cache without firing 'load'
    window.addEventListener('pageshow', (e) => { if (e.persisted) clearOverlay(); });
  })();

  /* ═══════════════════════════════════════════════════════════════════
     5. HERO CANVAS PARTICLES (2D — always-on complement)
  ═══════════════════════════════════════════════════════════════════ */
  (function initHeroCanvas() {
    const hero = document.querySelector('.hero');
    if (!hero) return;

    const canvas = document.createElement('canvas');
    canvas.id = 'hero-particle-canvas';
    canvas.setAttribute('aria-hidden', 'true');
    hero.insertBefore(canvas, hero.firstChild);

    const ctx = canvas.getContext('2d');
    let W, H, particles;

    const COLORS = ['#0D8ABC', '#00c9a7', '#22c55e', '#3b82f6'];
    const SHAPES = ['circle', 'cross', 'diamond'];

    function resize() {
      W = canvas.width  = hero.offsetWidth;
      H = canvas.height = hero.offsetHeight;
    }

    function createParticles() {
      const count = Math.min(40, Math.floor(W / 24));
      particles = Array.from({ length: count }, () => ({
        x: Math.random() * W,
        y: Math.random() * H,
        r: 2 + Math.random() * 4,
        color: COLORS[Math.floor(Math.random() * COLORS.length)],
        shape: SHAPES[Math.floor(Math.random() * SHAPES.length)],
        vx: (Math.random() - 0.5) * 0.4,
        vy: (Math.random() - 0.5) * 0.4,
        alpha: 0.2 + Math.random() * 0.5,
        pulse: Math.random() * Math.PI * 2,
      }));
    }

    function drawParticle(p, t) {
      const alpha = p.alpha * (0.7 + 0.3 * Math.sin(p.pulse + t));
      ctx.globalAlpha = alpha;
      ctx.fillStyle = p.color;
      ctx.strokeStyle = p.color;
      ctx.lineWidth = 1.5;

      if (p.shape === 'circle') {
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fill();
      } else if (p.shape === 'cross') {
        const s = p.r * 1.4;
        ctx.beginPath();
        ctx.moveTo(p.x - s, p.y); ctx.lineTo(p.x + s, p.y);
        ctx.moveTo(p.x, p.y - s); ctx.lineTo(p.x, p.y + s);
        ctx.stroke();
      } else {
        ctx.beginPath();
        ctx.moveTo(p.x, p.y - p.r * 1.5);
        ctx.lineTo(p.x + p.r, p.y);
        ctx.lineTo(p.x, p.y + p.r * 1.5);
        ctx.lineTo(p.x - p.r, p.y);
        ctx.closePath();
        ctx.fill();
      }
      ctx.globalAlpha = 1;
    }

    let rafId;
    function loop(ts) {
      const t = ts / 1000;
      ctx.clearRect(0, 0, W, H);

      particles.forEach(p => {
        p.x += p.vx;
        p.y += p.vy;
        if (p.x < -10) p.x = W + 10;
        if (p.x > W + 10) p.x = -10;
        if (p.y < -10) p.y = H + 10;
        if (p.y > H + 10) p.y = -10;
        drawParticle(p, t);
      });

      rafId = requestAnimationFrame(loop);
    }

    resize();
    createParticles();
    rafId = requestAnimationFrame(loop);

    let resizeTimer;
    window.addEventListener('resize', () => {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(() => { resize(); createParticles(); }, 250);
    }, { passive: true });
  })();

  /* ═══════════════════════════════════════════════════════════════════
     6. COUNTER ANIMATION (stats)
  ═══════════════════════════════════════════════════════════════════ */
  (function initCounters() {
    const els = document.querySelectorAll('.stat-value-hero');
    if (!els.length) return;

    const IO = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        const el = entry.target;
        const text = el.textContent.trim();
        const match = text.match(/[\d\s,]+/);
        if (!match) return;
        const target = parseInt(match[0].replace(/\s|,/g, ''));
        const prefix = text.slice(0, text.search(/\d/));
        const suffix = text.slice(text.search(/\d/) + match[0].length);
        let current = 0;
        const duration = 2000;
        const steps = 60;
        const increment = target / steps;
        const timer = setInterval(() => {
          current += increment;
          if (current >= target) {
            current = target;
            clearInterval(timer);
          }
          el.textContent = prefix + Math.floor(current).toLocaleString('fr') + suffix;
        }, duration / steps);
        IO.unobserve(el);
      });
    }, { threshold: 0.5 });

    els.forEach(el => IO.observe(el));
  })();

  /* ═══════════════════════════════════════════════════════════════════
     7. PARALLAX SECTIONS
  ═══════════════════════════════════════════════════════════════════ */
  (function initParallax() {
    const els = document.querySelectorAll('[data-parallax]');
    if (!els.length) return;
    if ('ontouchstart' in window) return; // skip on touch

    function update() {
      const scrollY = window.scrollY;
      els.forEach(el => {
        const speed  = parseFloat(el.dataset.parallax) || 0.15;
        const rect   = el.getBoundingClientRect();
        const center = rect.top + rect.height / 2 + scrollY;
        const off    = (scrollY + window.innerHeight / 2 - center) * speed;
        el.style.transform = `translateY(${off}px)`;
      });
    }
    window.addEventListener('scroll', update, { passive: true });
    update();
  })();

  /* ═══════════════════════════════════════════════════════════════════
     8. MAGNETIC BUTTONS
  ═══════════════════════════════════════════════════════════════════ */
  (function initMagneticButtons() {
    if ('ontouchstart' in window) return;
    document.querySelectorAll('.btn-primary, .btn-appointment').forEach(btn => {
      btn.addEventListener('mousemove', e => {
        const rect = btn.getBoundingClientRect();
        const dx = e.clientX - (rect.left + rect.width  / 2);
        const dy = e.clientY - (rect.top  + rect.height / 2);
        btn.style.transform = `translate(${dx * 0.18}px, ${dy * 0.18}px) scale(1.04)`;
      });
      btn.addEventListener('mouseleave', () => {
        btn.style.transform = '';
      });
    });
  })();

  /* ═══════════════════════════════════════════════════════════════════
     9. NAVBAR SCROLL BEHAVIOR
  ═══════════════════════════════════════════════════════════════════ */
  (function initNavbarScroll() {
    const header = document.querySelector('header');
    if (!header) return;
    let lastY = 0;
    window.addEventListener('scroll', () => {
      const y = window.scrollY;
      if (y > 80) {
        header.style.boxShadow = '0 8px 40px rgba(13,138,188,.2)';
        header.style.backdropFilter = 'blur(28px) saturate(200%)';
      } else {
        header.style.boxShadow = '';
        header.style.backdropFilter = '';
      }
      lastY = y;
    }, { passive: true });
  })();

  /* ═══════════════════════════════════════════════════════════════════
     10. TILT FOR NEW DYNAMIC CARDS (MutationObserver)
  ═══════════════════════════════════════════════════════════════════ */
  if (!('ontouchstart' in window)) {
    const tiltObserver = new MutationObserver(mutations => {
      mutations.forEach(m => {
        m.addedNodes.forEach(node => {
          if (node.nodeType !== 1) return;
          node.querySelectorAll && node.querySelectorAll('.metric-card, .dashboard-card').forEach(el => {
            // avoid double-init
            if (!el.dataset.tilt) {
              el.dataset.tilt = '1';
              /* minimal tilt for dynamic cards */
            }
          });
        });
      });
    });
    tiltObserver.observe(document.body, { childList: true, subtree: true });
  }

})();
