#!/usr/bin/env node
/*
 * Takes the screenshots for the documentation on rocketshow.net/docs.
 *
 * It runs the real, built web app in a headless Chromium, but without a
 * device behind it: every /api request and every WebSocket the app opens is
 * answered from the demo data in ./demo-data. That keeps the pictures the same
 * from one run to the next, and anybody can retake them on a laptop, with no
 * Raspberry Pi, no audio interface and no DMX on the desk.
 *
 * Usage, from the repository root:
 *
 *   (cd src/main/webapp && npm run prod)       # builds into src/main/resources/static
 *   (cd docs/screenshots && npm install && npx playwright install chromium)
 *   node docs/screenshots/capture.js
 *
 * Options:
 *   --app <dir>     the built web app (default: src/main/resources/static)
 *   --out <dir>     where the images go (default: ../spark/website/assets/images/docs,
 *                   the website in a checkout next to this one)
 *   --only a,b      take only the shots with these names
 *   --list          print the shot names and exit
 *
 * The shot list is at the bottom of this file. Every entry is one image,
 * written as <name>.webp; the docs page refers to them by that name, so keep a
 * name once the page uses it.
 */

'use strict';

const fs = require('fs');
const http = require('http');
const path = require('path');
const { chromium } = require('playwright');

const ROOT = path.resolve(__dirname, '..', '..');
const DATA = path.join(__dirname, 'demo-data');

// ------------------------------------------------------------------ arguments

function arg(name, fallback) {
  const i = process.argv.indexOf('--' + name);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

const APP_DIR = path.resolve(arg('app', path.join(ROOT, 'src/main/resources/static')));
const OUT_DIR = path.resolve(arg('out', path.join(ROOT, '..', 'spark/website/assets/images/docs')));
const ONLY = arg('only', '') ? arg('only', '').split(',') : null;

// ------------------------------------------------------------------ demo data

function load(file) {
  return JSON.parse(fs.readFileSync(path.join(DATA, file), 'utf8'));
}

const demo = {
  settings: load('settings.json'),
  system: load('system.json'),
  compositions: load('compositions.json'),
  sets: load('sets.json'),
  mediaFiles: load('media-files.json'),
  leadSheets: load('lead-sheets.json'),
  leadSheetImage: fs.readFileSync(path.join(DATA, 'lead-sheet.svg')),
  designer: {
    fixtures: load('designer/fixtures.json'),
    projects: load('designer/projects.json'),
    project: load('designer/project-summer-tour-2026.json'),
    profiles: {
      'generic/rgbw-par': load('designer/profile-generic-rgbw-par.json'),
      'stairville/mh-100': load('designer/profile-stairville-mh-100.json'),
    },
  },
};

/*
 * A stand-in for the audio files, so the designer's timeline has a waveform to
 * draw. Generated rather than stored: a few seconds of maths instead of
 * megabytes in the repository. The loudness follows the song structure of the
 * demo project (quiet verses, loud choruses), so the scenes drawn on the
 * timeline line up with something you can see.
 */
function demoAudio(durationSeconds) {
  const rate = 8000;
  const samples = durationSeconds * rate;
  const sections = [[0, 15, 0.35], [15, 45, 0.45], [45, 75, 0.9], [75, 105, 0.45], [105, 135, 0.9],
    [135, 165, 0.55], [165, 167, 1.0], [167, 205, 0.95], [205, 243, 0.4], [243, 9999, 0.05]];
  const buffer = Buffer.alloc(44 + samples);
  buffer.write('RIFF', 0); buffer.writeUInt32LE(36 + samples, 4); buffer.write('WAVE', 8);
  buffer.write('fmt ', 12); buffer.writeUInt32LE(16, 16); buffer.writeUInt16LE(1, 20); buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(rate, 24); buffer.writeUInt32LE(rate, 28); buffer.writeUInt16LE(1, 32); buffer.writeUInt16LE(8, 34);
  buffer.write('data', 36); buffer.writeUInt32LE(samples, 40);
  let seed = 7;
  const noise = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff) * 2 - 1;
  const beat = rate / 2; // 120 BPM
  for (let i = 0; i < samples; i++) {
    const t = i / rate;
    const level = sections.find(([a, b]) => t >= a && t < b)[2];
    const inBeat = (i % beat) / beat;
    const kick = Math.exp(-inBeat * 18) * Math.sin(2 * Math.PI * 55 * t);
    const hat = (Math.floor(i / (beat / 2)) % 2 ? 0.25 : 0.12) * Math.exp(-((i % (beat / 2)) / (beat / 2)) * 30) * noise();
    const pad = 0.25 * Math.sin(2 * Math.PI * 110 * t) * (0.6 + 0.4 * Math.sin(2 * Math.PI * t / 8));
    const value = Math.max(-1, Math.min(1, level * (0.7 * kick + hat + pad)));
    buffer[44 + i] = Math.round(128 + value * 120);
  }
  return buffer;
}

// ------------------------------------------------------------------ the fake device

/*
 * One per shot: what the device "is" while that picture is taken. A shot can
 * start before setup, logged out, as a community edition or with another
 * composition playing, without touching the shared demo data.
 */
class FakeDevice {
  constructor(options = {}) {
    this.edition = options.edition || 'spark';
    this.auth = { ...demo.system.auth, ...(options.auth || {}) };
    this.state = { ...demo.system.state, ...(options.state || {}) };
    this.session = { ...demo.system.session, ...(options.session || {}) };
    this.settings = JSON.parse(JSON.stringify(demo.settings));
    this.olaOutputPorts = demo.system.olaOutputPorts;
    if (this.edition === 'community') {
      // A Raspberry Pi with USB interfaces instead of SPARK's own outputs
      Object.assign(this.settings, demo.system.community.settings);
      this.olaOutputPorts = demo.system.community.olaOutputPorts;
    }
    this.sockets = { state: [] };
  }

  currentSet() {
    const set = demo.sets.find((s) => s.name === this.state.currentSetName);
    if (!set) {
      // "All compositions": the app fills the list from composition/list itself
      return { name: null, notes: null, compositionList: [], currentCompositionIndex: 0 };
    }
    return { ...set, currentCompositionIndex: this.state.currentCompositionIndex };
  }

  pushState() {
    for (const ws of this.sockets.state) ws.send(JSON.stringify(this.state));
  }

  api(method, route, query, body) {
    const s = demo.system;
    const json = (data, status = 200) => ({ status, contentType: 'application/json', body: JSON.stringify(data) });
    const ok = () => ({ status: 200, contentType: 'application/json', body: '' });
    const notFound = () => ({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'not in the demo data' }) });

    const get = {
      'auth/me': () => json(this.auth),
      'system/state': () => json(this.state),
      'system/settings': () => json(this.settings),
      'system/device-information': () => json(s.deviceInformation[this.edition]),
      'system/operating-system-information': () => json(s.operatingSystemInformation),
      'system/current-version': () => json(s.currentVersion),
      'system/remote-version': () => json(s.remoteVersion),
      'system/update-state': () => json(s.updateState),
      'system/build-info': () => json(s.buildInfo),
      'system/disk-space': () => json(s.diskSpace),
      'system/lan-info': () => json(s.lanInfo),
      'system/health': () => json({ ...s.health, deviceInformation: s.deviceInformation[this.edition] }),
      'session': () => json(this.session),
      'composition/list': () => json(demo.compositions),
      'composition': () => {
        const c = demo.compositions.find((x) => x.name === query.get('name'));
        return c ? json(c) : notFound();
      },
      'set': () => json(this.currentSet()),
      'set/list': () => json(demo.sets),
      'set/details': () => {
        const set = demo.sets.find((x) => x.name === query.get('name'));
        return set ? json(set) : notFound();
      },
      'file/list': () => json(demo.mediaFiles),
      'file/get': () => ({ status: 200, contentType: 'audio/wav', body: demoAudio(245) }),
      'lead-sheet/list': () => json(demo.leadSheets),
      'lead-sheet/image': () => ({ status: 200, contentType: 'image/svg+xml', body: demo.leadSheetImage }),
      'audio/devices': () => json(s.audioDevices),
      'audio/max-channels': () => json(s.maxAudioChannels),
      'midi/in-devices': () => json(s.midiInDevices),
      'midi/out-devices': () => json(s.midiOutDevices),
      'lighting/ola-plugins': () => json(s.olaPlugins),
      'lighting/ola-output-ports': () => json(this.olaOutputPorts),
      'fixtures': () => {
        const uuid = query.get('uuid');
        return json(uuid ? demo.designer.fixtures.filter((f) => f.uuid === uuid) : demo.designer.fixtures);
      },
      'fixture': () => {
        const profile = demo.designer.profiles[query.get('uuid')];
        return profile ? json(profile) : notFound();
      },
      'projects': () => json(demo.designer.projects),
      'project': () => json(demo.designer.project),
    };

    const post = {
      'auth/login': () => json({ ...this.auth, authenticated: true }),
      'auth/setup': () => json({ authenticated: true, passwordConfigured: true }),
      'set/load': () => {
        this.state.currentSetName = query.get('name') || null;
        this.state.currentCompositionIndex = 0;
        this.pushState();
        return ok();
      },
      'session/set-auto-select-next-composition': () => {
        this.session.autoSelectNextComposition = query.get('value') === 'true';
        return ok();
      },
      'session/set-play-view-mode': () => {
        this.session.playViewMode = query.get('value');
        return ok();
      },
      'composition/test-play': () => json({ durationMillis: JSON.parse(body || '{}').durationMillis || 0 }),
      'system/enable-ssh': () => ({ status: 200, contentType: 'text/plain', body: 'k7-rocket-42' }),
    };

    const handler = (method === 'GET' ? get : post)[route];
    if (handler) return handler();
    if (method !== 'GET') return ok(); // transport, preview, saving: accepted and forgotten
    console.warn('  no demo data for GET /api/' + route);
    return notFound();
  }
}

// ------------------------------------------------------------------ plumbing

function serveApp() {
  const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json',
    '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
    '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.eot': 'application/vnd.ms-fontobject' };
  const server = http.createServer((req, res) => {
    let file = path.join(APP_DIR, decodeURIComponent(new URL(req.url, 'http://x').pathname));
    if (!file.startsWith(APP_DIR)) { res.writeHead(403); return res.end(); }
    if (fs.existsSync(file) && fs.statSync(file).isDirectory()) file = path.join(file, 'index.html');
    if (!fs.existsSync(file)) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  });
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server)));
}

async function wire(page, device) {
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const apiPath = url.pathname.replace(/^\/api\//, '').replace(/\/$/, '');
    await route.fulfill(device.api(request.method(), apiPath, url.searchParams, request.postData()));
  });

  // The sockets the app keeps open. Answered here with no server behind them.
  await page.routeWebSocket(/\/api\/state$/, (ws) => {
    device.sockets.state.push(ws);
  });
  await page.routeWebSocket(/\/api\/update\/state$/, () => {});
  await page.routeWebSocket(/\/api\/activity\/(audio|midi|lighting)$/, (ws) => {
    const kind = ws.url().split('/').pop();
    let tick = 0;
    const timer = setInterval(() => {
      tick++;
      if (kind === 'audio') {
        const level = (bus, ch) => -10 - 6 * Math.abs(Math.sin(tick / 7 + bus * 2 + ch));
        ws.send(JSON.stringify({ activityAudioBusList: device.settings.audioBusList.map((bus, b) => ({
          name: bus.name,
          activityAudioChannelList: Array.from({ length: bus.channels }, (_, c) => ({ index: c, volumeDb: level(b, c) })),
        })) }));
      } else if (kind === 'midi') {
        ws.send(JSON.stringify({ midiSignal: { command: 144, channel: 0, data1: 60, data2: 100 },
          midiDirection: 'OUT', midiSources: ['FILE'], midiDestinations: ['OUT_DEVICE'] }));
      } else {
        ws.send(JSON.stringify({}));
      }
    }, 20);
    ws.onClose(() => clearInterval(timer));
  });
}

async function shoot(browser, baseUrl, shot) {
  const device = new FakeDevice(shot.device);
  const context = await browser.newContext({
    // A little narrower than a laptop screen, so the interface is not shrunk
    // too far on the page, and twice the pixels, so it stays sharp there.
    viewport: shot.viewport || { width: 1100, height: 760 },
    deviceScaleFactor: shot.scale || 2,
    isMobile: !!shot.mobile,
    hasTouch: !!shot.mobile,
    locale: 'en-GB',
    timezoneId: 'Europe/Zurich',
    colorScheme: 'dark',
  });
  // Values the app and the designer keep in the browser, e.g. the instrument
  // chosen under Settings → Personal, or the designer's last project.
  await context.addInitScript((storage) => {
    for (const [k, v] of Object.entries(storage)) localStorage.setItem(k, v);
    // The same "random" numbers on every run: the particles behind the setup
    // wizard and the uuids the app makes up land where they did last time.
    let seed = 42;
    Math.random = () => ((seed = (seed * 16807) % 2147483647) - 1) / 2147483646;
  }, {
    settings: JSON.stringify({ instrumentUuid: '5f1c2a9e-3b7d-4e21-9a6f-1c0d8e2b7a02' }),
    projectName: demo.designer.project.name,
    showIntro: 'false',
    language: 'en',
    ...(shot.localStorage || {}),
  });
  // A shot that cannot find its button fails fast instead of holding up the rest
  context.setDefaultTimeout(10000);
  try {
    return await capture(context, baseUrl, shot, device);
  } finally {
    await context.close();
  }
}

async function capture(context, baseUrl, shot, device) {
  const page = await context.newPage();
  // A fixed clock: the play position and every "time since" in the app read the
  // same on every run. Timers still run, so the app does not stall.
  await page.clock.setFixedTime(new Date('2026-07-18T21:14:00+02:00'));
  page.on('pageerror', (e) => console.warn('  page error:', e.message));
  await wire(page, device);

  await page.goto(baseUrl + '/#' + (shot.path || '/play'));
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(500);
  if (shot.run) await shot.run(page, device);
  await page.waitForTimeout(shot.settle || 400);
  // No blinking caret or tooltip in the picture, and no sticky menu bar
  // stitched over the top of an element taller than the window.
  await page.mouse.move(0, 0);
  await page.addStyleTag({ content: '*{caret-color:transparent!important}'
    + (shot.element ? '.navbar.sticky-top{position:static!important}' : '') });
  await page.waitForTimeout(200);

  const options = { animations: 'disabled', timeout: 60000 };
  if (shot.element && shot.pad) {
    // the element with some of the page around it
    const box = await page.locator(shot.element).first().boundingBox();
    const view = page.viewportSize();
    const x = Math.max(0, box.x - shot.pad);
    const y = Math.max(0, box.y - shot.pad);
    options.clip = { x, y, width: Math.min(view.width, box.x + box.width + shot.pad) - x,
      height: Math.min(view.height, box.y + box.height + shot.pad) - y };
    return page.screenshot(options);
  }
  if (shot.element) return page.locator(shot.element).first().screenshot(options);
  return page.screenshot({ ...options, fullPage: !!shot.fullPage });
}

// PNG in, WebP out, encoded by the browser so there is nothing else to install.
async function toWebp(browser, png, quality) {
  const page = await browser.newPage();
  const dataUrl = await page.evaluate(async ({ b64, q }) => {
    const img = new Image();
    img.src = 'data:image/png;base64,' + b64;
    await img.decode();
    const canvas = document.createElement('canvas');
    canvas.width = img.naturalWidth;
    canvas.height = img.naturalHeight;
    canvas.getContext('2d').drawImage(img, 0, 0);
    return canvas.toDataURL('image/webp', q);
  }, { b64: png.toString('base64'), q: quality });
  await page.close();
  return Buffer.from(dataUrl.split(',')[1], 'base64');
}

// ------------------------------------------------------------------ helpers for the shots

const settingsTab = (id) => async (page) => {
  await page.click(`a.nav-link[href="#${id}"]`);
  await page.waitForTimeout(300);
};

const openComposition = (name) => async (page) => {
  await page.locator('app-editor-composition .list-group-item', { hasText: name }).first().click();
  await page.waitForSelector('#compositionName');
};

// ------------------------------------------------------------------ the shots

// For the designer crops that do not show the 3D stage: hidden, it is not
// rendered, and rendering it without a graphics card is what makes them slow.
const NO_STAGE = { previewHidden: 'true' };

const SHOTS = [
  // Getting started
  {
    name: 'setup-language',
    device: { auth: { authenticated: false, passwordConfigured: false } },
    path: '/intro',
  },
  {
    name: 'setup-password',
    device: { auth: { authenticated: false, passwordConfigured: false } },
    path: '/intro',
    run: async (page) => {
      await page.getByRole('button', { name: 'Next' }).click();
      await page.fill('#deviceNameInput', 'Main Stage');
      await page.getByRole('button', { name: 'Next' }).click();
      await page.fill('input[name=password1]', 'my-secret');
      await page.fill('input[name=password2]', 'my-secret');
    },
    element: '.col-xl > .d-flex.flex-column',
    pad: 40,
  },
  {
    name: 'setup-access-point',
    device: { auth: { authenticated: false, passwordConfigured: false } },
    path: '/intro',
    run: async (page) => {
      await page.getByRole('button', { name: 'Next' }).click();
      await page.getByRole('button', { name: 'Next' }).click();
      await page.fill('input[name=password1]', 'my-secret');
      await page.fill('input[name=password2]', 'my-secret');
      await page.getByRole('button', { name: 'Next' }).click();
      await page.locator('label', { hasText: 'Activated' }).first().click();
    },
    element: '.col-xl > .d-flex.flex-column',
    pad: 40,
  },
  {
    name: 'login',
    device: { auth: { authenticated: false, passwordConfigured: true } },
  },

  // Start
  { name: 'start-setlist' },
  {
    name: 'start-cue-grid',
    device: { session: { playViewMode: 'grid' } },
  },
  {
    name: 'start-set-menu',
    device: { state: { playState: 'STOPPED', positionMillis: 0 } },
    run: async (page) => {
      await page.locator('#button-container:visible').click();
    },
  },
  {
    name: 'start-phone',
    viewport: { width: 390, height: 780 },
    scale: 2,
    mobile: true,
  },
  {
    // a tablet on a music stand
    name: 'lead-sheet',
    viewport: { width: 834, height: 1194 },
    scale: 1.5,
    run: async (page) => {
      await page.locator('.navbar-actions .btn').click();
      await page.waitForTimeout(500);
    },
  },

  // Compositions
  {
    name: 'editor-composition',
    path: '/editor',
    run: openComposition('Neon Skyline'),
    fullPage: true,
  },
  {
    name: 'editor-add-menu',
    path: '/editor',
    run: async (page) => {
      await openComposition('Neon Skyline')(page);
      await page.click('#dropdownMenuButton');
    },
    viewport: { width: 1100, height: 900 },
  },
  {
    name: 'editor-audio-file',
    path: '/editor',
    run: async (page) => {
      await openComposition('Neon Skyline')(page);
      await page.locator('.list-group-item', { hasText: 'neon_skyline_click.wav' }).locator('a.btn', { hasText: '...' }).click();
      await page.waitForSelector('.card-modal');
    },
    element: '.modal-content',
    viewport: { width: 1100, height: 900 },
  },
  {
    name: 'editor-media-library',
    path: '/editor',
    run: async (page) => {
      await openComposition('Neon Skyline')(page);
      await page.locator('.list-group-item', { hasText: 'neon_skyline_backing.wav' }).locator('a.btn', { hasText: '...' }).click();
      await page.waitForSelector('.card-modal');
      await page.click('.modal-content #media-library-tab');
    },
    element: '.modal-content',
    viewport: { width: 1100, height: 900 },
  },
  {
    name: 'editor-midi-file',
    path: '/editor',
    run: async (page) => {
      await openComposition('Midnight Run')(page);
      await page.locator('.list-group-item', { hasText: 'midnight_run_lights.mid' }).locator('a.btn', { hasText: '...' }).click();
      await page.waitForSelector('.card-modal');
    },
    element: '.modal-content',
    viewport: { width: 1100, height: 900 },
  },
  {
    name: 'editor-action-http',
    path: '/editor',
    run: async (page) => {
      await openComposition('Intro – Countdown')(page);
      await page.locator('.list-group-item', { hasText: 'HTTP' }).locator('a.btn', { hasText: '...' }).click();
      await page.waitForSelector('.card-modal');
    },
    element: '.modal-content',
    viewport: { width: 1100, height: 900 },
  },
  {
    name: 'editor-action-lighting',
    path: '/editor',
    run: async (page) => {
      await openComposition('Neon Skyline')(page);
      await page.locator('.list-group-item', { hasText: 'Lighting' }).locator('a.btn', { hasText: '...' }).click();
      await page.waitForSelector('.card-modal');
    },
    element: '.modal-content',
    viewport: { width: 1100, height: 900 },
  },
  {
    name: 'editor-lead-sheet',
    path: '/editor',
    run: async (page) => {
      await openComposition('Neon Skyline')(page);
      await page.locator('.list-group-item', { hasText: 'neon-skyline-chords.png' }).locator('a.btn', { hasText: '...' }).click();
      await page.waitForSelector('.card-modal');
      await page.click('.modal-content #media-library-tab');
    },
    element: '.modal-content',
    viewport: { width: 1100, height: 900 },
  },
  {
    name: 'editor-set',
    path: '/editor',
    run: async (page) => {
      await page.click('#sets-tab');
      await page.locator('app-editor-set .list-group-item', { hasText: 'Summer Tour 2026' }).first().click();
      await page.waitForSelector('#setName');
    },
    viewport: { width: 1100, height: 900 },
  },
  {
    name: 'editor-media',
    path: '/editor',
    run: async (page) => {
      await page.click('#media-tab');
    },
  },

  // Designer
  {
    name: 'designer',
    path: '/designer',
    viewport: { width: 1440, height: 1000 },
    scale: 1.5,
    settle: 2500,
  },
  {
    name: 'designer-timeline',
    path: '/designer',
    viewport: { width: 1440, height: 1000 },
    settle: 2500,
    element: '#row3',
    localStorage: NO_STAGE,
  },
  {
    name: 'designer-effects',
    path: '/designer',
    viewport: { width: 1440, height: 1000 },
    run: async (page) => {
      await page.waitForTimeout(1500);
      await page.click('#v-pills-effects-tab');
    },
    settle: 1500,
    element: '#row2',
    localStorage: NO_STAGE,
  },
  {
    name: 'designer-fixture-pool',
    path: '/designer',
    viewport: { width: 1440, height: 1000 },
    run: async (page) => {
      await page.waitForTimeout(1500);
      await page.locator('#fixtures a.btn:has(i.fa-cog)').first().click();
    },
    settle: 1500,
    element: '.modal-content',
    localStorage: NO_STAGE,
  },

  // Settings
  { name: 'settings-system', path: '/settings', fullPage: true },
  {
    name: 'settings-update',
    path: '/settings',
    run: async (page) => {
      await page.getByText('Search for updates...').click();
      await page.waitForSelector('app-update-dialog');
      await page.waitForTimeout(400);
    },
    element: '.modal-content',
  },
  { name: 'settings-security', path: '/settings', run: settingsTab('security'), fullPage: true },
  {
    name: 'settings-api-key',
    path: '/settings',
    run: async (page) => {
      await settingsTab('security')(page);
      await page.getByText('Add API Key').click();
      await page.waitForSelector('app-new-api-key-dialog');
      await page.fill('app-new-api-key-dialog #description', 'Show control desk');
    },
    element: '.modal-content',
  },
  { name: 'settings-network', path: '/settings', run: settingsTab('network'), fullPage: true },
  { name: 'settings-midi', path: '/settings', run: settingsTab('midi'), viewport: { width: 1100, height: 900 } },
  {
    name: 'settings-midi-control',
    path: '/settings',
    run: settingsTab('midi'),
    element: '#midi .form-group.row:has(app-action-trigger)',
  },
  { name: 'settings-audio', path: '/settings', run: settingsTab('audio'), fullPage: true },
  { name: 'settings-video', path: '/settings', run: settingsTab('video') },
  { name: 'settings-lighting', path: '/settings', run: settingsTab('lighting'), fullPage: true },
  {
    name: 'settings-lighting-community',
    device: { edition: 'community' },
    path: '/settings',
    run: settingsTab('lighting'),
    fullPage: true,
  },
  { name: 'settings-external-control', path: '/settings', run: settingsTab('external-control'), fullPage: true },
  { name: 'settings-scheduler', path: '/settings', run: settingsTab('scheduler'), fullPage: true },
  { name: 'settings-band', path: '/settings', run: settingsTab('band'), element: '#band' },
  { name: 'settings-advanced', path: '/settings', run: settingsTab('advanced') },
  { name: 'settings-info', path: '/settings', run: settingsTab('info'), fullPage: true },
];

// ------------------------------------------------------------------ main

(async () => {
  if (process.argv.includes('--list')) {
    SHOTS.forEach((s) => console.log(s.name));
    return;
  }
  if (!fs.existsSync(path.join(APP_DIR, 'index.html'))) {
    console.error(`No built web app in ${APP_DIR}. Build it first: cd src/main/webapp && npm run prod`);
    process.exit(1);
  }
  fs.mkdirSync(OUT_DIR, { recursive: true });

  const server = await serveApp();
  const baseUrl = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

  let failed = 0;
  for (const shot of SHOTS) {
    if (ONLY && !ONLY.includes(shot.name)) continue;
    process.stdout.write(shot.name + ' ... ');
    try {
      const png = await shoot(browser, baseUrl, shot);
      const webp = await toWebp(browser, png, 0.82);
      fs.writeFileSync(path.join(OUT_DIR, shot.name + '.webp'), webp);
      console.log(Math.round(webp.length / 1024) + ' KB');
    } catch (e) {
      failed++;
      console.log('FAILED\n  ' + e.message.split('\n')[0]);
    }
  }

  await browser.close();
  server.close();
  console.log(failed ? `\n${failed} shot(s) failed` : `\nall written to ${OUT_DIR}`);
  process.exit(failed ? 1 : 0);
})();
