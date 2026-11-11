(function () {
  'use strict';
  var doc = document, root = doc.documentElement;
  function lsGet(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function lsSet(k, v) { try { localStorage.setItem(k, v); } catch (e) { /* ignore */ } }
  function $(id) { return doc.getElementById(id); }

  /* ---------- i18n (Spanish text lives in the HTML; this holds English) ---------- */
  var EN = {
    'skip': 'Skip to content', 'nav.aria': 'Main', 'nav.what': 'What it does', 'nav.demo': 'Demo', 'nav.levels': 'Levels',
    'nav.screens': 'Screens', 'nav.tech': 'How it is built', 'lang.aria': 'Switch language', 'theme.aria': 'Switch theme',
    'meta.desc': 'Solfi is a native app to learn music from scratch, written in Kotlin and Compose Multiplatform. Portfolio project.',
    'hero.eyebrow': 'Portfolio project · Kotlin Multiplatform', 'hero.tagline': 'Learn music from scratch',
    'hero.lead': 'A native app for kids and adults: short lessons, solfeges that play, and a staff drawn by hand on Canvas. Android, iOS and desktop from a single Kotlin codebase, offline and with no account.',
    'hero.gh': 'View on GitHub', 'hero.web': 'Try it in the browser', 'nav.app': 'Open the app', 'hero.how': 'How it is built',
    'alt.solfeo': 'Solfege player showing "Ode to Joy" on the staff', 'alt.home': 'Home screen with streak, XP, stars and the current lesson',
    'alt.map': 'Lesson map of level 1, Getting Started',
    'what.eyebrow': 'Features', 'what.title': 'What does it do?',
    'f1.t': '5 levels', 'f1.p': 'From Getting Started to Expert, with sequential unlocking and an exam per level.',
    'f2.t': 'Short lessons, 7 exercise types', 'f2.p': 'Explain, listen, multiple-choice quiz, identify the note, ear training, piano and rhythm (tap in time).',
    'f3.t': 'Solfeges that play', 'f3.p': 'Highlighting synced to the audio, tempo 40–160, metronome, count-in and loop.',
    'f4.t': 'Kid and adult modes', 'f4.p': 'Big buttons, color, a mascot and confetti, or a more sober style.',
    'f5.t': 'es / en / pt', 'f5.p': 'Three languages and your choice of note names: Do Re Mi or C D E.',
    'f6.t': 'Choose your clef', 'f6.p': 'Treble, bass or recommended by instrument; melodies are rewritten in the chosen clef.',
    'f7.t': 'XP, stars, streaks and badges', 'f7.p': '1–3 stars by accuracy, a daily streak and a badge for passing each exam.',
    'f8.t': 'Offline, no account', 'f8.p': 'No backend: progress is stored on the device, with local profiles.',
    'demo.eyebrow': 'Try it here', 'demo.title': 'Interactive mini-demo',
    'demo.lead': 'First phrase of "Ode to Joy" (public domain), with an SVG staff and Web Audio sound. The current note is highlighted in sync with the audio clock.',
    'demo.staffalt': 'Staff with the first phrase of Ode to Joy', 'demo.play': 'Play', 'demo.stop': 'Stop', 'demo.tempo': 'Tempo', 'demo.loop': 'Loop',
    'demo.names': 'Note names', 'demo.piano': 'One-octave piano',
    'demo.note': 'This demo uses Web Audio oscillators. The real app uses its own sample-accurate Kotlin synthesizer, shared by all three platforms.',
    'lv.eyebrow': 'Learning path', 'lv.title': 'Five levels, from zero to expert',
    'lv1.t': 'Getting Started', 'lv1.p': 'Sound and pulse · High and low · The staff and the treble clef · Do, Re, Mi · Fa and Sol · Whole, half and quarter notes · First melodies',
    'lv2.t': 'Basics', 'lv2.p': 'The treble clef · Notes Do to Do · Rests · Measures and bar lines · Ledger lines · The piano keyboard · Reading simple melodies',
    'lv3.t': 'Intermediate', 'lv3.p': 'Eighth notes and rests · Dots and ties · Sharps and flats · Tones and semitones · The C major scale · Simple intervals',
    'lv4.t': 'Advanced', 'lv4.p': 'The bass clef · Major scales and key signatures · Minor scales · Sixteenth notes · Triads · Dynamics · Tempo and character',
    'lv5.t': 'Expert', 'lv5.p': 'Compound meter 6/8 · Syncopation and off-beat · Intervals with quality · Seventh chords · I–IV–V–I functions · Greek modes · Transposition',
    'sc.eyebrow': 'Real screenshots', 'sc.title': 'App screens',
    'sc.lead': 'Real renders of the Compose UI produced by the off-screen tests (sample data; Spanish text).', 'sc.aria': 'Screen gallery',
    'alt.onb': 'Welcome screen with the mascot and language choice', 'cap.onb': 'Welcome',
    'alt.inst': 'Instrument choice in dark mode', 'cap.inst': 'Instrument (dark mode)', 'cap.home': 'Home', 'cap.map': 'Level map',
    'alt.id': 'Exercise: identify the note on the staff', 'cap.id': 'Identify the note',
    'alt.listen': 'Listening exercise with note highlighting', 'cap.listen': 'Listen with highlighting',
    'alt.lp': 'Piano exercise: play Sol, Fa, Mi', 'cap.lp': 'Piano exercise', 'cap.solfeo': 'Solfege player',
    'alt.piano': 'Free piano', 'cap.piano': 'Free piano', 'alt.staff': 'Staff samples: clefs, key signatures, ties and beams', 'cap.staff': 'Staff samples',
    'alt.prog': 'Progress screen', 'cap.prog': 'Progress',
    't.eyebrow': 'Technical deep dive', 't.title': 'How it is built',
    't.lead': 'Almost everything lives in <code>commonMain</code>. Each platform only provides what cannot be shared, through <code>expect</code>/<code>actual</code>.',
    't.dgalt': 'Architecture: commonMain with UI, theory, parser, synth, staff, content, i18n and progress, on top of Android, iOS and desktop actuals',
    'e1.t': 'One shared synthesizer', 'e1.p': 'Piano-like voice with harmonics and ADSR, polyphony and metronome, at 44.1 kHz mono. The sequencer schedules with sample accuracy and the audio clock (frames played) drives both the UI highlight and rhythm scoring.',
    'e2.t': 'Custom Canvas notation', 'e2.p': 'Treble and bass clefs as Bézier curves, beams, ties, ledger lines, accidentals, dots and rests, with no external dependency.',
    'e3.t': 'Compact notation', 'e3.p': 'Melodies are written as text: <code>"C4:q D4:q E4:h | r:q"</code>. A parser turns them into events for the staff and the synth.',
    'e4.t': 'Typed i18n', 'e4.p': 'Strings are a Kotlin interface (<code>Strings</code>) with one implementation per language: a missing key fails compilation.',
    'e5.t': 'Content as data', 'e5.p': 'Each level is a JSON file (kotlinx.serialization) checked by a validator. Adding a level does not touch code.',
    'e6.t': 'Octave-shift re-clefing', 'e6.p': 'When the clef changes, the melody shifts by whole octaves to minimize ledger lines; note names never change. <code>clefLocked</code> pins the clef when a lesson is about staff positions.',
    'e7.t': 'Unit and render tests', 'e7.p': 'Theory, audio, clefs, progress, strings, content and off-screen renders of the screens, all green with <code>desktopTest</code>.',
    'sn1.t': 'Content: a melody in JSON', 'sn2.t': 'expect / actual', 'sn3.t': 'ClefShift: counting ledger lines', 'stack.t': 'Stack',
    'foot.proj': 'Portfolio project'
  };
  var ATTRS = [['data-i18n-alt', 'alt'], ['data-i18n-label', 'aria-label'], ['data-i18n-content', 'content']];
  var lang = 'es';
  function applyLang(l) {
    lang = l; root.lang = l;
    doc.querySelectorAll('[data-i18n]').forEach(function (el) {
      var k = el.getAttribute('data-i18n');
      if (el.__es === undefined) el.__es = el.innerHTML;
      el.innerHTML = (l === 'en' && EN[k] !== undefined) ? EN[k] : el.__es;
    });
    ATTRS.forEach(function (p) {
      doc.querySelectorAll('[' + p[0] + ']').forEach(function (el) {
        var k = el.getAttribute(p[0]);
        if (el.__esa === undefined) el.__esa = {};
        if (el.__esa[p[1]] === undefined) el.__esa[p[1]] = el.getAttribute(p[1]);
        el.setAttribute(p[1], (l === 'en' && EN[k] !== undefined) ? EN[k] : el.__esa[p[1]]);
      });
    });
    var t = l === 'en' ? 'Solfi · Learn music from scratch' : 'Solfi · Aprende música desde cero';
    doc.title = t;
    ['og:title', 'twitter:title'].forEach(function (n) {
      var m = doc.querySelector('meta[property="' + n + '"],meta[name="' + n + '"]'); if (m) m.setAttribute('content', t);
    });
    $('lang').textContent = l === 'en' ? 'ES' : 'EN';
    if (!playing) $('play').setAttribute('aria-label', l === 'en' ? 'Play' : 'Reproducir');
    else $('play').setAttribute('aria-label', l === 'en' ? 'Stop' : 'Detener');
  }
  var saved = lsGet('solfi-lang');
  var initial = saved === 'en' || saved === 'es' ? saved : ((navigator.language || 'es').toLowerCase().indexOf('es') === 0 ? 'es' : ((navigator.language || '').toLowerCase().indexOf('pt') === 0 ? 'es' : 'en'));
  $('lang').addEventListener('click', function () {
    var n = lang === 'es' ? 'en' : 'es'; lsSet('solfi-lang', n); applyLang(n);
    if (!namesChosen) setNames(n === 'en' ? 'let' : 'sol', false);
  });

  /* ---------- theme ---------- */
  var st = lsGet('solfi-theme');
  if (st === 'light' || st === 'dark') root.setAttribute('data-theme', st);
  $('theme').addEventListener('click', function () {
    var cur = root.getAttribute('data-theme') || (window.matchMedia && matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark');
    var n = cur === 'dark' ? 'light' : 'dark'; root.setAttribute('data-theme', n); lsSet('solfi-theme', n);
  });

  /* ---------- scroll reveal ---------- */
  var rv = doc.querySelectorAll('.rv');
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } });
    }, { threshold: 0.12 });
    rv.forEach(function (el) { io.observe(el); });
  } else rv.forEach(function (el) { el.classList.add('in'); });

  /* ---------- demo: staff ---------- */
  var NS = 'http://www.w3.org/2000/svg';
  var SOL = ['Do', 'Re', 'Mi', 'Fa', 'Sol', 'La', 'Si'], LET = ['C', 'D', 'E', 'F', 'G', 'A', 'B'];
  var FREQ = { C4: 261.63, 'C#4': 277.18, D4: 293.66, 'D#4': 311.13, E4: 329.63, F4: 349.23, 'F#4': 369.99, G4: 392.0, 'G#4': 415.3, A4: 440.0, 'A#4': 466.16, B4: 493.88, C5: 523.25 };
  // Ode to Joy, first phrase: [diatonic step from C4, beats, key]
  var M = [[2, 1, 'E4'], [2, 1, 'E4'], [3, 1, 'F4'], [4, 1, 'G4'], [4, 1, 'G4'], [3, 1, 'F4'], [2, 1, 'E4'], [1, 1, 'D4'],
    [0, 1, 'C4'], [0, 1, 'C4'], [1, 1, 'D4'], [2, 1, 'E4'], [2, 1.5, 'E4'], [1, 0.5, 'D4'], [1, 2, 'D4']];
  var names = 'sol', namesChosen = false;
  function nm(step) { return (names === 'sol' ? SOL : LET)[step % 7]; }
  function el(tag, attrs, parent) {
    var e = doc.createElementNS(NS, tag);
    for (var k in attrs) e.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(e); return e;
  }
  var svg = $('staff'), heads = [], labels = [], S = 12;
  function drawStaff() {
    var INK = '#1b1642';
    var measures = [[0, 4], [4, 8], [8, 12], [12, 15]], tops = [50, 182], X0 = 96, MW = 330;
    [0, 1].forEach(function (si) {
      var top = tops[si], bot = top + 4 * S;
      for (var i = 0; i < 5; i++) el('line', { x1: 14, x2: 748, y1: top + i * S, y2: top + i * S, stroke: INK, 'stroke-width': 1.2 }, svg);
      el('line', { x1: 14, x2: 14, y1: top, y2: bot, stroke: INK, 'stroke-width': 1.6 }, svg);
      // treble clef (stroked bezier)
      var g = el('g', { transform: 'translate(36,' + (bot - S) + ')' }, svg);
      el('path', { d: 'M2 34C2 42-9 42-9 33C-9 24 14 8 14-16C14-34 4-40 0-30C-6-18 10 0 14 8C18 16 6 28-4 16C-10 8 2-1 9 6C13 11 8 17 4 14', fill: 'none', stroke: INK, 'stroke-width': 2.6, 'stroke-linecap': 'round' }, g);
      if (si === 0) {
        var tt = el('text', { x: 66, y: top + 20, 'font-size': 26, 'font-weight': 800, 'font-family': 'Nunito,sans-serif', fill: INK }, svg); tt.textContent = '4';
        var tb = el('text', { x: 66, y: top + 44, 'font-size': 26, 'font-weight': 800, 'font-family': 'Nunito,sans-serif', fill: INK }, svg); tb.textContent = '4';
      }
      [0, 1].forEach(function (mi) {
        var ms = measures[si * 2 + mi], mx = X0 + mi * MW, unit = (MW - 40) / 4, beat = 0;
        for (var n = ms[0]; n < ms[1]; n++) {
          var d = M[n][0], b = M[n][1], x = mx + 22 + beat * unit + 0, y = bot - (d - 2) * (S / 2);
          if (d === 0) el('line', { x1: x - 11, x2: x + 11, y1: y, y2: y, stroke: INK, 'stroke-width': 1.3 }, svg);
          var halo = el('circle', { cx: x, cy: y, r: 15, fill: '#6C5CE7', opacity: 0 }, svg);
          var open = b === 2;
          var head = el('ellipse', { cx: x, cy: y, rx: 6.6, ry: 4.8, transform: 'rotate(-20 ' + x + ' ' + y + ')', fill: open ? '#fbfaff' : INK, stroke: INK, 'stroke-width': open ? 1.8 : 1 }, svg);
          el('line', { x1: x + 6, x2: x + 6, y1: y - 1, y2: y - 36, stroke: INK, 'stroke-width': 1.5 }, svg);
          if (b === 0.5) el('path', { d: 'M' + (x + 6) + ' ' + (y - 36) + 'c2 12 12 12 9 24', fill: 'none', stroke: INK, 'stroke-width': 1.8 }, svg);
          if (b === 1.5) el('circle', { cx: x + 13, cy: y - (d % 2 === 0 ? 3 : 0), r: 2, fill: INK }, svg);
          var lb = el('text', { x: x, y: bot + 40, 'text-anchor': 'middle', 'font-size': 13, 'font-weight': 700, 'font-family': 'Nunito,sans-serif', fill: '#4a4575' }, svg);
          heads[n] = { head: head, halo: halo, label: lb, step: d };
          lb.textContent = nm(d);
          beat += b;
        }
        var bx = X0 + (mi + 1) * MW - 8;
        el('line', { x1: bx, x2: bx, y1: top, y2: bot, stroke: INK, 'stroke-width': mi === 1 && si === 1 ? 3 : 1.5 }, svg);
      });
    });
  }
  drawStaff();
  function setNames(v, chosen) {
    names = v; if (chosen) namesChosen = true;
    heads.forEach(function (h) { h.label.textContent = nm(h.step); });
    $('nSol').setAttribute('aria-pressed', v === 'sol'); $('nLet').setAttribute('aria-pressed', v === 'let');
    refreshPiano();
  }
  $('nSol').addEventListener('click', function () { setNames('sol', true); });
  $('nLet').addEventListener('click', function () { setNames('let', true); });

  /* ---------- demo: piano ---------- */
  var WHITE = ['C4', 'D4', 'E4', 'F4', 'G4', 'A4', 'B4', 'C5'];
  var BLACK = [['C#4', 1], ['D#4', 2], ['F#4', 4], ['G#4', 5], ['A#4', 6]];
  var piano = $('piano'), keyEls = {};
  function keyLabel(k) { return /#/.test(k) ? '' : (names === 'sol' ? SOL : LET)[(k.charCodeAt(0) - 67 + 7) % 7]; }
  WHITE.forEach(function (k) {
    var b = doc.createElement('button'); b.type = 'button'; b.className = 'w'; b.setAttribute('data-k', k);
    b.setAttribute('aria-label', k); piano.appendChild(b); keyEls[k] = b;
  });
  BLACK.forEach(function (p) {
    var b = doc.createElement('button'); b.type = 'button'; b.className = 'b'; b.style.left = (p[1] * 12.5) + '%';
    b.setAttribute('data-k', p[0]); b.setAttribute('aria-label', p[0].replace('#', ' sharp ')); piano.appendChild(b); keyEls[p[0]] = b;
  });
  function refreshPiano() { WHITE.forEach(function (k) { keyEls[k].textContent = keyLabel(k); }); }
  refreshPiano();

  /* ---------- demo: Web Audio lookahead scheduler ---------- */
  var ctx = null, playing = false, timer = 0, raf = 0, idx = 0, nextTime = 0, queue = [], finishAt = 0, ending = false;
  var LOOKAHEAD = 0.12, TICK_MS = 25;
  function getCtx() {
    if (!ctx) { var AC = window.AudioContext || window.webkitAudioContext; if (!AC) return null; ctx = new AC(); }
    if (ctx.state === 'suspended') ctx.resume();
    return ctx;
  }
  function voice(freq, t, dur) {
    var master = ctx.createGain(), end = t + Math.max(dur, 0.25);
    master.gain.setValueAtTime(0.0001, t);
    master.gain.exponentialRampToValueAtTime(0.5, t + 0.012);
    master.gain.exponentialRampToValueAtTime(0.18, t + 0.12);
    master.gain.exponentialRampToValueAtTime(0.0001, end);
    master.connect(ctx.destination);
    [[1, 'triangle', 1], [2, 'sine', 0.3], [3, 'sine', 0.1]].forEach(function (h) {
      var o = ctx.createOscillator(), g = ctx.createGain();
      o.type = h[1]; o.frequency.value = freq * h[0]; g.gain.value = h[2];
      o.connect(g); g.connect(master); o.start(t); o.stop(end + 0.05);
    });
  }
  function schedule() {
    while (!ending && nextTime < ctx.currentTime + LOOKAHEAD) {
      var n = M[idx], dur = n[1] * 60 / +$('tempo').value;
      voice(FREQ[n[2]], nextTime, dur * 0.97);
      queue.push({ t: nextTime, e: nextTime + dur, i: idx, k: n[2] });
      nextTime += dur; idx++;
      if (idx >= M.length) {
        if ($('loop').checked) idx = 0; else { ending = true; finishAt = nextTime; clearInterval(timer); }
      }
    }
  }
  var lastActive = -1, lastKey = null;
  function clearHi() {
    if (lastActive >= 0) { heads[lastActive].halo.setAttribute('opacity', 0); heads[lastActive].label.setAttribute('fill', '#4a4575'); }
    if (lastKey) keyEls[lastKey].classList.remove('on');
    lastActive = -1; lastKey = null;
  }
  function frame() {
    if (!playing) return;
    var now = ctx.currentTime, cur = null;
    while (queue.length && queue[0].e <= now) queue.shift();
    if (queue.length && queue[0].t <= now) cur = queue[0];
    var ci = cur ? cur.i : -1;
    if (ci !== lastActive) {
      clearHi();
      if (cur) { heads[ci].halo.setAttribute('opacity', 0.28); heads[ci].label.setAttribute('fill', '#5444d1'); lastActive = ci; lastKey = cur.k; keyEls[cur.k].classList.add('on'); }
    }
    if (ending && now >= finishAt) { stop(); return; }
    raf = requestAnimationFrame(frame);
  }
  function setPlayUi(on) {
    var b = $('play'); b.textContent = on ? '■' : '▶';
    b.setAttribute('aria-label', on ? (lang === 'en' ? 'Stop' : 'Detener') : (lang === 'en' ? 'Play' : 'Reproducir'));
  }
  function start() {
    if (!getCtx()) return;
    playing = true; ending = false; idx = 0; queue = []; nextTime = ctx.currentTime + 0.08;
    setPlayUi(true); schedule(); timer = setInterval(schedule, TICK_MS); raf = requestAnimationFrame(frame);
  }
  function stop() {
    playing = false; clearInterval(timer); cancelAnimationFrame(raf); queue = []; clearHi(); setPlayUi(false);
    if (ctx) { // cut off anything already scheduled
      try { ctx.close(); } catch (e) { /* ignore */ } ctx = null;
    }
  }
  $('play').addEventListener('click', function () { playing ? stop() : start(); });
  $('tempo').addEventListener('input', function () { $('tempoOut').textContent = this.value; });
  var down = {};
  function pressKey(k) {
    if (!getCtx()) return;
    voice(FREQ[k], ctx.currentTime, 0.9);
    var e = keyEls[k]; e.classList.add('on'); clearTimeout(down[k]);
    down[k] = setTimeout(function () { if (k !== lastKey) e.classList.remove('on'); }, 260);
  }
  piano.addEventListener('click', function (ev) {
    var b = ev.target.closest('button[data-k]'); if (b) pressKey(b.getAttribute('data-k'));
  });

  applyLang(initial);
  if (initial === 'en') setNames('let', false);
})();
