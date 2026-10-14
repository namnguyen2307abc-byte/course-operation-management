const fs = require('fs');
const path = require('path');

const jsPath = path.join(__dirname, '..', 'com_fe', 'js', 'weekly-schedule.js');
let code = fs.readFileSync(jsPath, 'utf8');

// Cắt bỏ phần modal cũ: từ "// MODAL ĐIỂM DANH BUỔI HỌC" cho đến trước "// MODAL TẠO LỚP HỌC MỚI"
const modalBlockRegex = /\/\/ =+\s*\/\/ MODAL ĐIỂM DANH BUỔI HỌC[\s\S]*?(?=\/\/ =+\s*\/\/ MODAL TẠO LỚP HỌC MỚI)/;
if (modalBlockRegex.test(code)) {
    code = code.replace(modalBlockRegex, '');
    fs.writeFileSync(jsPath, code, 'utf8');
    console.log('SUCCESS: cleaned up legacy attendance modal code from weekly-schedule.js');
} else {
    console.log('Pattern not found, skipping cleanup');
}
