const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../com_fe');
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8', '.json': 'application/json', '.png': 'image/png',
    '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.ico': 'image/x-icon' };
http.createServer((request, response) => {
    if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405); return response.end(); }
    let filename;
    try {
        const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
        filename = path.resolve(root, '.' + (pathname === '/' ? '/index.html' : pathname));
    } catch { response.writeHead(400); return response.end('Invalid path'); }
    if (!filename.startsWith(root + path.sep)) { response.writeHead(403); return response.end('Forbidden'); }
    fs.realpath(filename, (error, realFilename) => {
        if (error || !realFilename.startsWith(root + path.sep)) { response.writeHead(404); return response.end('Not found'); }
        fs.readFile(realFilename, (readError, data) => {
            if (readError) { response.writeHead(404); return response.end('Not found'); }
            response.writeHead(200, { 'Content-Type': types[path.extname(realFilename)] || 'application/octet-stream',
                'Cache-Control': 'no-store' });
            response.end(request.method === 'HEAD' ? undefined : data);
        });
    });
}).listen(5500, '127.0.0.1', () => console.log('Frontend: http://127.0.0.1:5500'));
