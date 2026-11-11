import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// com_fe is the editable source. Keep the existing launch locations compatible.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.join(root, 'com_fe');
const destinations = ['.', 'com_be/src/main/resources/static', 'com_be/src/main/webapp', 'src/main/webapp'];
const check = process.argv.includes('--check');
const navbar = fs.readFileSync(path.join(source, 'components/navbar.html'), 'utf8').replace(/\r\n/g, '\n').trim();

// Bake the shared shell into source HTML so it is present before first paint.
// The runtime only personalizes links; navigation never waits for a navbar fetch.
function prepareWorkspace(file, content) {
    if (!file.endsWith('.html') || !content.includes('id="navbar-container"')) return content;
    const prefix = path.dirname(file) === source ? './' : '../';
    const shell = `<div id="navbar-container"><!-- workspace-shell:start -->\n${navbar}\n<!-- workspace-shell:end --></div>\n<script src="${prefix}js/workspace-shell.js"></script>`;
    content = content.replace(/<script\b[^>]*src=["'][^"']*\/auth\.js["'][^>]*>\s*<\/script>/g, '');
    content = content.replace(/<script\b[^>]*src=["'][^"']*\/workspace-shell\.js["'][^>]*>\s*<\/script>/g, '');
    content = content.replace(/<div id="navbar-container">(?:<!-- workspace-shell:start -->[\s\S]*?<!-- workspace-shell:end -->|\s*)<\/div>/, shell);
    content = content.replace(/\s*<\/head>/, `\n<script src="${prefix}js/auth.js"></script>\n</head>`);
    content = content.replace(/<body\b([^>]*)>/, (tag, attributes) => {
        if (/\bclass=/.test(attributes)) return tag.replace(/class="([^"]*)"/, (_, names) => `class="${[...new Set([...names.split(/\s+/), 'has-workspace-sidebar'])].filter(Boolean).join(' ')}"`);
        return `<body${attributes} class="has-workspace-sidebar">`;
    });
    // Keep repeated syncs byte-stable after moving the shell/auth script.
    return content.replace(/(<script src="[^"']*js\/workspace-shell\.js"><\/script>)\s*/, '$1\n').replace(/^[ \t]+$/gm, '');
}
function files(directory) {
    return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        const full = path.join(directory, entry.name);
        return entry.isDirectory() ? files(full) : /\.(html|css|js)$/.test(entry.name) ? [full] : [];
    });
}
let differences = 0;
for (const file of files(source)) {
    const relative = path.relative(source, file);
    const original = fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
    const content = prepareWorkspace(file, original);
    if (content !== original) {
        differences++;
        if (check) console.error(`Workspace shell out of sync: ${path.relative(root, file)}`);
        else fs.writeFileSync(file, content);
    }
    for (const destination of destinations) {
        const target = path.join(root, destination, relative);
        if (fs.existsSync(target) && fs.readFileSync(target, 'utf8').replace(/\r\n/g, '\n') === content) continue;
        differences++;
        if (check) console.error(`Out of sync: ${path.relative(root, target)}`);
        else {
            fs.mkdirSync(path.dirname(target), { recursive: true });
            fs.writeFileSync(target, content);
        }
    }
}
console.log(check ? `${differences} frontend differences.` : `Synced ${differences} frontend files from com_fe.`);
if (check && differences) process.exitCode = 1;
