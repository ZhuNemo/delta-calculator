#!/usr/bin/env node
/**
 * 把 MathLive 的 dist 下载到 app/src/main/assets/mathlive/vendor。
 * 目的是让 App 完全离线渲染公式 —— 不依赖 CDN，也不需要 INTERNET 权限。
 *
 * 用法：node tools/fetch-mathlive.mjs [--force]
 * 说明：优先走 npmmirror（国内更快），失败再回退到 registry.npmjs.org。
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const vendorDir = path.join(projectRoot, 'app', 'src', 'main', 'assets', 'mathlive', 'vendor');
const marker = path.join(vendorDir, 'mathlive.min.js');
const force = process.argv.includes('--force');

if (fs.existsSync(marker) && !force) {
  console.log('[mathlive] vendor 已存在，跳过下载，只重建派生文件。');
  buildDerived();
  process.exit(0);
}

const registries = [
  'https://registry.npmmirror.com/mathlive/latest',
  'https://registry.npmjs.org/mathlive/latest',
];

async function fetchMeta() {
  const errors = [];
  for (const url of registries) {
    try {
      const res = await fetch(url, { redirect: 'follow' });
      if (!res.ok) {
        errors.push(`${url} -> HTTP ${res.status}`);
        continue;
      }
      return await res.json();
    } catch (e) {
      errors.push(`${url} -> ${e.message}`);
    }
  }
  throw new Error(`无法获取 mathlive 元数据:\n${errors.join('\n')}`);
}

const meta = await fetchMeta();
console.log(`[mathlive] 版本 ${meta.version}`);

const tarballUrl = meta.dist?.tarball;
if (!tarballUrl) throw new Error('元数据里没有 dist.tarball');

const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'mathlive-'));
const tgz = path.join(tmpDir, 'mathlive.tgz');
const pkgDir = path.join(tmpDir, 'pkg');
fs.mkdirSync(pkgDir, { recursive: true });

console.log(`[mathlive] 下载 ${tarballUrl}`);
const res = await fetch(tarballUrl, { redirect: 'follow' });
if (!res.ok) throw new Error(`下载失败：HTTP ${res.status}`);
fs.writeFileSync(tgz, Buffer.from(await res.arrayBuffer()));

// 注意：在 Git Bash 的 GNU tar 下，带盘符的绝对路径会被当成「远程主机:路径」，
// 所以这里统一用 cwd + 相对路径解压。
execFileSync('tar', ['-xzf', 'mathlive.tgz', '-C', 'pkg'], { cwd: tmpDir, stdio: 'inherit' });

const pkgRoot = path.join(pkgDir, 'package');
// 旧版本把产物放在 dist/，0.111+ 直接放在包根目录，这里都兼容
const source = [path.join(pkgRoot, 'dist'), pkgRoot]
    .find((dir) => fs.existsSync(path.join(dir, 'mathlive.min.js')));
if (!source) throw new Error('压缩包里找不到 mathlive.min.js');

// 只复制渲染必需的文件，避免把 package.json / types / vue 封装塞进 APK
const FILES = ['mathlive.min.js', 'mathlive-static.css', 'mathlive-fonts.css'];
const DIRS = ['fonts'];

fs.rmSync(vendorDir, { recursive: true, force: true });
fs.mkdirSync(vendorDir, { recursive: true });
for (const name of FILES) {
    const from = path.join(source, name);
    if (fs.existsSync(from)) fs.copyFileSync(from, path.join(vendorDir, name));
}
for (const name of DIRS) {
    const from = path.join(source, name);
    if (fs.existsSync(from)) fs.cpSync(from, path.join(vendorDir, name), { recursive: true });
}
fs.rmSync(tmpDir, { recursive: true, force: true });

const files = fs.readdirSync(vendorDir);
console.log(`[mathlive] 已写入 ${path.relative(projectRoot, vendorDir)}：${files.join(', ')}`);
if (!fs.existsSync(marker)) throw new Error('vendor 里没有 mathlive.min.js，请检查 dist 结构');
if (!fs.existsSync(path.join(vendorDir, 'fonts'))) throw new Error('vendor 里没有 fonts 目录，公式会缺字');

function buildDerived() {
// ---------------------------------------------------------------------------
// 1) 把字体内联成 data URI
// Android WebView 在 file:// 下会以 CORS 为由拒绝 @font-face 请求，
// 字体加载不到 MathLive 就不排版，表现为「公式区一片空白」。
// ---------------------------------------------------------------------------
const cssPath = path.join(vendorDir, 'mathlive-static.css');
if (fs.existsSync(cssPath)) {
  const css = fs.readFileSync(cssPath, 'utf8');
  let inlined = 0;
  const embedded = css.replace(/url\((fonts\/[^)]+)\)/g, (match, rel) => {
    const fontPath = path.join(vendorDir, decodeURIComponent(rel));
    if (!fs.existsSync(fontPath)) return match;
    inlined++;
    return `url(data:font/woff2;base64,${fs.readFileSync(fontPath).toString('base64')})`;
  });
  fs.writeFileSync(path.join(vendorDir, 'mathlive-embedded.css'), embedded);
  console.log(`[mathlive] 内联 ${inlined} 个字体 -> mathlive-embedded.css (${Math.round(embedded.length / 1024)} KB)`);
}

// ---------------------------------------------------------------------------
// 2) 生成单文件 bundle.html
// JS / CSS / 字体全内联，WebView 不再发任何子资源请求，绕开 file:// 的各种限制。
// ---------------------------------------------------------------------------
const assetDir = path.join(projectRoot, 'app', 'src', 'main', 'assets', 'mathlive');
const indexPath = path.join(assetDir, 'index.html');
const embeddedCss = path.join(vendorDir, 'mathlive-embedded.css');
if (fs.existsSync(indexPath) && fs.existsSync(embeddedCss)) {
  let html = fs.readFileSync(indexPath, 'utf8');
  const css = fs.readFileSync(embeddedCss, 'utf8');
  const js = fs.readFileSync(path.join(vendorDir, 'mathlive.min.js'), 'utf8');
  html = html.replace(
    /<link[^>]*href="vendor\/mathlive-embedded\.css"[^>]*>/,
    () => `<style>\n${css}\n</style>`,
  );
  html = html.replace(
    /<script[^>]*src="vendor\/mathlive\.min\.js"[^>]*><\/script>/,
    () => `<script>\n${js.replace(/<\/script/gi, '<\\/script')}\n</script>`,
  );
  fs.writeFileSync(path.join(assetDir, 'bundle.html'), html);
  console.log(`[mathlive] 生成单文件 bundle.html (${Math.round(html.length / 1024)} KB)`);
}
}

buildDerived();
