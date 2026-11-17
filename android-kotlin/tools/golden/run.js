// Genera los vectores dorados ejecutando la lógica TypeScript ORIGINAL de la app con Node.
//
//   node run.js            (desde android-kotlin/tools/golden)
//
// Requiere TypeScript: usa <repo>/node_modules (tras `npm install` en la raíz) o la ruta indicada en la
// variable de entorno RN_NODE_MODULES. Escribe app/src/test/resources/golden/<escenario>.json.
'use strict';

const path = require('path');
const fs = require('fs');
const cp = require('child_process');

// Zona horaria sin horario de verano para que las fechas locales sean deterministas.
if (process.env.GOLDEN_CHILD !== '1') {
  const r = cp.spawnSync(process.execPath, [__filename, ...process.argv.slice(2)], {
    stdio: 'inherit',
    env: { ...process.env, GOLDEN_CHILD: '1', TZ: 'America/Guayaquil' },
  });
  process.exit(r.status === null ? 1 : r.status);
}

const Module = require('module');
const stubs = require('./stubs');

const repoRoot = path.resolve(__dirname, '..', '..', '..');
const nodeModules = process.env.RN_NODE_MODULES || path.join(repoRoot, 'node_modules');
const tsc = path.join(nodeModules, 'typescript', 'bin', 'tsc');
const build = path.join(__dirname, '.build');
const outDir = path.resolve(__dirname, '..', '..', 'app', 'src', 'test', 'resources', 'golden');

if (!fs.existsSync(tsc)) {
  console.error(`No se encontró TypeScript en ${tsc}. Ejecuta "npm install" en la raíz del repo o define RN_NODE_MODULES.`);
  process.exit(1);
}

// ---------- 1. Compilar la lógica original a CommonJS ----------
fs.rmSync(build, { recursive: true, force: true });
const srcFiles = ['types/index.ts', 'utils/formatters.ts', 'utils/cardPurchases.ts', 'utils/paymentReminders.ts',
  'db/storage.ts', 'utils/reportGenerator.ts', 'utils/currency.ts']
  .map((f) => path.join(repoRoot, 'src', f));
const compile = cp.spawnSync(process.execPath, [tsc, '--outDir', build, '--rootDir', path.join(repoRoot, 'src'),
  '--module', 'commonjs', '--target', 'es2020', '--moduleResolution', 'node', '--skipLibCheck', '--esModuleInterop',
  '--lib', 'es2022', ...srcFiles], { encoding: 'utf8' });
if (!fs.existsSync(path.join(build, 'db', 'storage.js'))) {
  console.error(compile.stdout || compile.stderr);
  process.exit(1);
}

// ---------- 2. Stubs de módulos nativos ----------
const originalLoad = Module._load;
Module._load = function (request, parent, isMain) {
  if (Object.prototype.hasOwnProperty.call(stubs.modules, request)) return stubs.modules[request];
  return originalLoad.apply(this, arguments);
};

// ---------- 3. Reloj y aleatoriedad deterministas ----------
const RealDate = Date;
let frozenMs = null;
class FakeDate extends RealDate {
  constructor(...args) {
    if (args.length === 0 && frozenMs !== null) super(frozenMs);
    else super(...args);
  }
  static now() { return frozenMs !== null ? frozenMs : RealDate.now(); }
}
global.Date = FakeDate;
const setClock = (iso) => { frozenMs = new RealDate(iso).getTime(); }; // 'YYYY-MM-DDTHH:mm:ss' = hora local

let seed = 123456789;
Math.random = () => { // mulberry32
  seed |= 0; seed = (seed + 0x6D2B79F5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};

const load = (rel) => require(path.join(build, rel));
const ctx = {
  storage: load('db/storage.js'),
  formatters: load('utils/formatters.js'),
  cardPurchases: load('utils/cardPurchases.js'),
  paymentReminders: load('utils/paymentReminders.js'),
  reportGenerator: load('utils/reportGenerator.js'),
  captured: stubs.captured,
  refs: {},
};
const { ops } = require('./ops');
const { scenarios } = require('./scenarios');

// ---------- 4. Normalización de ids generados (misma regla que GoldenRunner.kt) ----------
const PREFIX = '@tracker_gastos_';
const STORE_ORDER = ['salary', 'expenses', 'income', 'loans', 'creditCards', 'reminders', 'accounts', 'categories', 'investments', 'settings'];

function readStores() {
  const out = {};
  for (const s of STORE_ORDER) {
    const raw = stubs.kv.get(PREFIX + s);
    out[s] = raw ? JSON.parse(raw) : [];
  }
  return out;
}

class IdNormalizer {
  constructor(seedIds) { this.seedIds = seedIds; this.map = new Map(); this.next = 1; }
  scan(stores) {
    for (const s of STORE_ORDER) {
      for (const rec of stores[s]) {
        if (rec && typeof rec.id === 'string' && !this.seedIds.has(rec.id) && !this.map.has(rec.id)) {
          this.map.set(rec.id, `gen-${this.next++}`);
        }
      }
    }
  }
  apply(value) {
    if (typeof value === 'string') return this.map.has(value) ? this.map.get(value) : value;
    if (Array.isArray(value)) return value.map((v) => this.apply(v));
    if (value && typeof value === 'object') {
      return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, this.apply(v)]));
    }
    return value;
  }
  snapshot(stores) {
    const out = {};
    for (const s of STORE_ORDER) {
      out[s] = stores[s].map((rec) => {
        const r = this.apply(rec);
        if (rec && typeof rec.id === 'string' && !this.seedIds.has(rec.id) && 'createdAt' in r) r.createdAt = '<createdAt>';
        return r;
      });
    }
    return out;
  }
}

const resolveRefs = (value) => {
  if (typeof value === 'string' && value.startsWith('$') && Object.prototype.hasOwnProperty.call(ctx.refs, value.slice(1))) {
    return ctx.refs[value.slice(1)];
  }
  if (Array.isArray(value)) return value.map(resolveRefs);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, resolveRefs(v)]));
  return value;
};

// ---------- 5. Ejecutar escenarios ----------
(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const only = process.argv[2];
  let count = 0;
  for (const sc of scenarios) {
    if (only && sc.name !== only) continue;
    stubs.kv.clear();
    ctx.refs = {};
    stubs.captured.files.length = 0;
    stubs.captured.html = null;
    for (const s of STORE_ORDER) {
      if (sc.seed && sc.seed[s]) stubs.kv.set(PREFIX + s, JSON.stringify(sc.seed[s]));
    }
    setClock(sc.clock || '2026-11-17T12:00:00');

    const seedIds = new Set();
    for (const s of STORE_ORDER) for (const rec of (sc.seed && sc.seed[s]) || []) if (rec && rec.id) seedIds.add(rec.id);
    const norm = new IdNormalizer(seedIds);

    const steps = [];
    for (const step of sc.steps) {
      if (step.op === 'setClock') {
        setClock(step.args.at);
        steps.push({ op: step.op, args: step.args, result: null });
        continue;
      }
      const fn = ops[step.op];
      if (!fn) throw new Error(`Op desconocida: ${step.op} (${sc.name})`);
      const args = resolveRefs(step.args || {});
      const raw = await fn(ctx, args);
      norm.scan(readStores());
      const result = JSON.parse(JSON.stringify(raw === undefined ? null : raw));
      steps.push({ op: step.op, args: step.args || {}, result: norm.apply(result) });
    }
    const final = norm.snapshot(readStores());
    const doc = { name: sc.name, description: sc.description || '', clock: sc.clock || '2026-11-17T12:00:00', seed: sc.seed || {}, steps, final };
    fs.writeFileSync(path.join(outDir, `${sc.name}.json`), JSON.stringify(doc, null, 2) + '\n', 'utf8');
    count++;
    console.log(`ok  ${sc.name}  (${steps.length} pasos)`);
  }
  console.log(`${count} escenarios escritos en ${outDir}`);
})().catch((e) => { console.error(e); process.exit(1); });
