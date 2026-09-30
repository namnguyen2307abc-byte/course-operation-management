USE course_operation_management;
GO

DECLARE @tbl TABLE (table_name sysname, column_name sysname, bad_count int);
DECLARE @t sysname, @c sysname, @q NVARCHAR(MAX);

DECLARE cur CURSOR FOR
SELECT t.name, c.name
FROM sys.tables t
JOIN sys.columns c ON t.object_id = c.object_id
JOIN sys.types ty ON c.user_type_id = ty.user_type_id
WHERE ty.name IN ('nvarchar', 'varchar', 'text', 'ntext')
  AND t.is_ms_shipped = 0;

OPEN cur;
FETCH NEXT FROM cur INTO @t, @c;

WHILE @@FETCH_STATUS = 0
BEGIN
    SET @q = N'SELECT @cnt = COUNT(*) FROM dbo.' + QUOTENAME(@t) + N' WHERE ' + QUOTENAME(@c) + N' LIKE ''%á»%'' OR ' + QUOTENAME(@c) + N' LIKE ''%Ä%'' OR ' + QUOTENAME(@c) + N' LIKE ''%Ã%'' OR ' + QUOTENAME(@c) + N' LIKE ''%Æ%'';';
    DECLARE @cnt INT = 0;
    BEGIN TRY
        EXEC sp_executesql @q, N'@cnt INT OUTPUT', @cnt OUTPUT;
        IF @cnt > 0
            INSERT INTO @tbl VALUES (@t, @c, @cnt);
    END TRY
    BEGIN CATCH
    END CATCH
    FETCH NEXT FROM cur INTO @t, @c;
END;

CLOSE cur;
DEALLOCATE cur;

SELECT * FROM @tbl;
GO
