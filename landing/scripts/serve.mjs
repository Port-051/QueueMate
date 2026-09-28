/** Local preview only. Does not deploy or make the site publicly accessible. */
import http from 'node:http';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root = fileURLToPath(new URL('../dist/', import.meta.url));
const port = Number(process.env.PORT || 4173);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('PORT must be 1..65535');
const mime = {'.svg':'image/svg+xml','.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.txt':'text/plain; charset=utf-8','.xml':'application/xml; charset=utf-8','.png':'image/png','.jpg':'image/jpeg','.jpeg':'image/jpeg','.webp':'image/webp'};
const server = http.createServer(async (req, res) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Robots-Tag', 'noindex'); // A local preview server is always noindex.
  res.setHeader('Cache-Control', 'no-store');
  if (!['GET', 'HEAD'].includes(req.method)) {res.writeHead(405, {'Allow':'GET, HEAD'}); res.end(); return;}
  let pathname;
  try {pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);} catch {res.writeHead(400); res.end(); return;}
  const relative = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '');
  const target = path.resolve(root, relative);
  const inside = path.relative(root, target);
  if (inside.startsWith('..') || path.isAbsolute(inside) || pathname.includes('\0') || pathname.includes('\\')) {res.writeHead(403); res.end(); return;}
  try {
    const data = await readFile(target);
    res.writeHead(200, {'Content-Type':mime[path.extname(target)] || 'application/octet-stream'});
    res.end(req.method === 'HEAD' ? undefined : data);
  } catch {
    res.writeHead(404, {'Content-Type':'text/html; charset=utf-8'});
    res.end(req.method === 'HEAD' ? undefined : await readFile(path.join(root, '404.html')));
  }
});
server.on('error', error => {console.error(`Preview server: ${error.message}`); process.exitCode = 1;});
server.listen(port, '127.0.0.1', () => console.log(`Local preview → http://127.0.0.1:${port} (Ctrl+C to stop; rebuild after edits)`));
