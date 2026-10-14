USE course_operation_management;
GO

SELECT id, note FROM placement_schedules WHERE note LIKE '%á»%' OR note LIKE '%Ä%' OR note LIKE '%Ã%' OR note LIKE '%Æ%';
SELECT id, discount_reason FROM invoices WHERE discount_reason LIKE '%á»%' OR discount_reason LIKE '%Ä%' OR discount_reason LIKE '%Ã%' OR discount_reason LIKE '%Æ%';
SELECT id, full_name, notes FROM students WHERE notes LIKE '%á»%' OR notes LIKE '%Ä%' OR notes LIKE '%Ã%' OR notes LIKE '%Æ%';
SELECT id, description FROM programs WHERE description LIKE '%á»%' OR description LIKE '%Ä%' OR description LIKE '%Ã%' OR description LIKE '%Æ%';
SELECT id, notes FROM enrollments WHERE notes LIKE '%á»%' OR notes LIKE '%Ä%' OR notes LIKE '%Ã%' OR notes LIKE '%Æ%';
SELECT id, note, review_note FROM enrollment_requests WHERE note LIKE '%á»%' OR review_note LIKE '%á»%' OR note LIKE '%Ä%' OR review_note LIKE '%Ä%';
SELECT id, lesson_note FROM lessons WHERE lesson_note LIKE '%á»%' OR lesson_note LIKE '%Ä%';
SELECT id, review_note FROM absence_requests WHERE review_note LIKE '%á»%' OR review_note LIKE '%Ä%';
SELECT id, note FROM makeup_registrations WHERE note LIKE '%á»%' OR note LIKE '%Ä%';
SELECT id, teacher_notes FROM placement_tests WHERE teacher_notes LIKE '%á»%' OR teacher_notes LIKE '%Ä%';
GO
