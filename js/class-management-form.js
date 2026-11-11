/** Parse the schedule formats already stored in the center database. */
function parseClassSchedule(description) {
    const text = String(description || '');
    const days = new Set();
    for (const match of text.matchAll(/(?:Thứ\s*|T)([2-7])|\bCN\b|Chủ\s*Nhật/giu)) {
        days.add(match[1] ? Number(match[1]) - 1 : 7);
    }
    const times = [...text.matchAll(/\b(\d{1,2}:\d{2})(?::\d{2})?\b/g)].map(match => match[1].padStart(5, '0'));
    return { days: [...days].sort((a, b) => a - b), startTime: times[0] || '', endTime: times[1] || '' };
}
