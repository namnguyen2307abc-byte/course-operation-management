import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// com_fe is the editable source. Keep the existing launch locations compatible.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.join(root, 'com_fe');
const destinations = ['.', 'com_be/src/main/resources/static', 'com_be/src/main/webapp', 'src/main/webapp'];
const check = process.argv.includes('--check');
function files(directory) {
    return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        const full = path.join(directory, entry.name);
        return entry.isDirectory() ? files(full) : /\.(html|css|js)$/.test(entry.name) ? [full] : [];
    });
}
let differences = 0;
for (const file of files(source)) {
    const relative = path.relative(source, file);
    const content = fs.readFileSync(file, 'utf8').replace(/\r\n/g, '\n');
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
