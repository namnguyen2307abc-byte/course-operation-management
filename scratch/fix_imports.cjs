const fs = require('fs');
const file = 'com_be/src/main/java/com/talent/management/features/attendance_makeup/service/impl/AbsenceMakeupServiceImpl.java';
let content = fs.readFileSync(file, 'utf8');

content = content.replace('import java.time.LocalTime;', 'import java.time.LocalTime;\r\nimport java.time.format.DateTimeFormatter;');
content = content.replace('public class AbsenceMakeupServiceImpl implements AbsenceMakeupService {', 'public class AbsenceMakeupServiceImpl implements AbsenceMakeupService {\r\n\r\n    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");');

fs.writeFileSync(file, content, 'utf8');
console.log('Added imports to AbsenceMakeupServiceImpl.java');
