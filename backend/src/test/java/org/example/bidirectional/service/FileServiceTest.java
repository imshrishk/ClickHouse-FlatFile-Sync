package org.example.bidirectional.service;

import com.opencsv.exceptions.CsvException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the FileService class.
 * Tests file operations including CSV reading and writing with various delimiters
 * and edge cases like special characters, quotes, and newlines.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FileServiceTest {

    private FileService fileService;
    private final String testFilePath = "test_output.csv";
    
    // Test data with various common CSV scenarios
    private static final String CSV_CONTENT = "id,name,email\n1,John Doe,john@example.com\n2,Jane Smith,jane@example.com";
    private static final String TSV_CONTENT = "id\tname\temail\n1\tJohn Doe\tjohn@example.com\n2\tJane Smith\tjane@example.com";
    private static final String PIPE_CONTENT = "id|name|email\n1|John Doe|john@example.com\n2|Jane Smith|jane@example.com";
    
    @TempDir
    Path tempDir;
    
    private Path csvFile;
    private Path tsvFile;
    private Path pipeFile;
    private Path nonExistentFile;

    @BeforeAll
    void setUp() {
        fileService = new FileService();
    }
    
    @BeforeEach
    void setUpTestFiles() throws IOException {
        // Create test files in the temp directory
        csvFile = tempDir.resolve("test.csv");
        tsvFile = tempDir.resolve("test.tsv");
        pipeFile = tempDir.resolve("test.pipe");
        nonExistentFile = tempDir.resolve("nonexistent.csv");
        
        // Prepare test files with different delimiters
        Files.writeString(csvFile, CSV_CONTENT);
        Files.writeString(tsvFile, TSV_CONTENT);
        Files.writeString(pipeFile, PIPE_CONTENT);
    }

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(new File(testFilePath).toPath());
    }

    /**
     * Test reading a file with comma delimiter
     */
    @Test
    void testReadCsvWithCommaDelimiter() throws IOException, CsvException {
        // When
        List<String[]> data = fileService.readCsv(csvFile.toString(), ',');
        
        // Then
        assertEquals(3, data.size(), "CSV should have 3 rows (header + 2 data rows)");
        assertEquals("id", data.get(0)[0], "First column header should be 'id'");
        assertEquals("John Doe", data.get(1)[1], "First row, second column should be 'John Doe'");
        assertEquals("jane@example.com", data.get(2)[2], "Second row, third column should be 'jane@example.com'");
    }
    
    /**
     * Test reading a file with tab delimiter
     */
    @Test
    void testReadCsvWithTabDelimiter() throws IOException, CsvException {
        // When
        List<String[]> data = fileService.readCsv(tsvFile.toString(), '\t');
        
        // Then
        assertEquals(3, data.size(), "TSV should have 3 rows (header + 2 data rows)");
        assertEquals("id", data.get(0)[0], "First column header should be 'id'");
        assertEquals("John Doe", data.get(1)[1], "First row, second column should be 'John Doe'");
        assertEquals("jane@example.com", data.get(2)[2], "Second row, third column should be 'jane@example.com'");
    }
    
    /**
     * Test reading a file with pipe delimiter
     */
    @Test
    void testReadCsvWithPipeDelimiter() throws IOException, CsvException {
        // When
        List<String[]> data = fileService.readCsv(pipeFile.toString(), '|');
        
        // Then
        assertEquals(3, data.size(), "Pipe-delimited file should have 3 rows (header + 2 data rows)");
        assertEquals("id", data.get(0)[0], "First column header should be 'id'");
        assertEquals("John Doe", data.get(1)[1], "First row, second column should be 'John Doe'");
        assertEquals("jane@example.com", data.get(2)[2], "Second row, third column should be 'jane@example.com'");
    }

    /**
     * Test writing and reading CSV with comma delimiter and quoted values
     */
    @Test
    void testWriteAndReadCsvWithCommas() throws IOException, CsvException {
        List<String[]> data = Arrays.asList(
                new String[]{"ID", "Name", "Comment"},
                new String[]{"1", "Alice", "Likes apples, oranges"},
                new String[]{"2", "Bob", "Said, \"Hello!\""}
        );

        fileService.writeCsv(testFilePath, data, ',');
        List<String[]> result = fileService.readCsv(testFilePath, ',');

        assertEquals(3, result.size(), "Should read all 3 rows");
        assertArrayEquals(data.get(1), result.get(1), "Row with commas should be correctly parsed");
        assertEquals("Said, \"Hello!\"", result.get(2)[2], "Quoted text should be preserved");
    }

    /**
     * Test writing and reading CSV with tab delimiter
     */
    @Test
    void testWriteAndReadCsvWithTabDelimiter() throws IOException, CsvException {
        List<String[]> data = List.of(
                new String[]{"ID", "Name"},
                new String[]{"1", "Eve"},
                new String[]{"2", "Frank"}
        );

        fileService.writeCsv(testFilePath, data, '\t');
        List<String[]> result = fileService.readCsv(testFilePath, '\t');

        assertEquals(3, result.size(), "Should read all 3 rows");
        assertArrayEquals(new String[]{"2", "Frank"}, result.get(2), "Data should be preserved with tab delimiter");
    }

    /**
     * Test handling quoted fields with newlines
     */
    @Test
    void testQuotedFieldsAndNewlines() throws IOException, CsvException {
        List<String[]> data = List.of(
                new String[]{"ID", "Bio"},
                new String[]{"1", "Loves reading\nand hiking"},
                new String[]{"2", "Line1\nLine2\nLine3"}
        );

        fileService.writeCsv(testFilePath, data, ',');
        List<String[]> result = fileService.readCsv(testFilePath, ',');

        assertEquals(3, result.size(), "Should read all 3 rows");
        assertTrue(result.get(1)[1].contains("\n"), "Newlines should be preserved");
        assertEquals("Line1\nLine2\nLine3", result.get(2)[1], "Multiple newlines should be preserved");
    }

    /**
     * Test reading an empty CSV file
     */
    @Test
    void testEmptyCsvRead() throws IOException, CsvException {
        fileService.writeCsv(testFilePath, List.of(), ',');
        List<String[]> result = fileService.readCsv(testFilePath, ',');
        assertTrue(result.isEmpty(), "Result should be empty for empty CSV");
    }

    /**
     * Test using pipe delimiter for CSV operations
     */
    @Test
    void testPipeDelimiter() throws IOException, CsvException {
        List<String[]> data = List.of(
                new String[]{"User", "Role"},
                new String[]{"Alice", "Admin"},
                new String[]{"Bob", "User"}
        );

        fileService.writeCsv(testFilePath, data, '|');
        List<String[]> result = fileService.readCsv(testFilePath, '|');

        assertEquals(3, result.size(), "Should read all 3 rows");
        assertArrayEquals(data.get(2), result.get(2), "Data should be preserved with pipe delimiter");
    }

    /**
     * Test reading from a non-existent file
     */
    @Test
    void testReadNonExistentFile() {
        assertThrows(IOException.class, 
            () -> fileService.readCsv(nonExistentFile.toString(), ','),
            "Should throw IOException for non-existent file");
    }
    
    /**
     * Test reading CSV from input stream with different delimiters
     */
    @ParameterizedTest
    @ValueSource(strings = {",", "\t", "|"})
    void testReadCsvRowsFromInputStream(String delimiter) throws IOException {
        // Given
        String content = CSV_CONTENT;
        if (delimiter.equals("\t")) {
            content = TSV_CONTENT;
        } else if (delimiter.equals("|")) {
            content = PIPE_CONTENT;
        }
        
        InputStream inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        
        // When
        List<String[]> data = FileService.readCsvRows(inputStream, delimiter);
        
        // Then
        assertEquals(3, data.size(), "Should have header plus two data rows");
        assertEquals("id", data.get(0)[0], "First column header should be 'id'");
        assertEquals("John Doe", data.get(1)[1], "Name field should be correctly parsed");
    }
    
    /**
     * Test reading CSV rows with null input stream
     */
    @Test
    void testReadCsvRowsWithNullInputStream() {
        assertThrows(IllegalArgumentException.class, 
            () -> FileService.readCsvRows(null, ","),
            "Should throw IllegalArgumentException for null input stream");
    }
    
    /**
     * Test reading CSV rows with null delimiter
     */
    @Test
    void testReadCsvRowsWithNullDelimiter() {
        InputStream inputStream = new ByteArrayInputStream(CSV_CONTENT.getBytes(StandardCharsets.UTF_8));
        
        assertThrows(IllegalArgumentException.class, 
            () -> FileService.readCsvRows(inputStream, null),
            "Should throw IllegalArgumentException for null delimiter");
    }
    
    /**
     * Test writing to a non-existent directory
     */
    @Test
    void testWriteToNestedDirectory() throws IOException, CsvException {
        // Given
        Path nestedDir = tempDir.resolve("nested/dir");
        Path nestedFile = nestedDir.resolve("test.csv");
        List<String[]> data = List.of(
            new String[]{"id", "name"}, 
            new String[]{"1", "test"}
        );
        
        // When
        fileService.writeCsv(nestedFile.toString(), data, ',');
        
        // Then
        assertTrue(Files.exists(nestedFile), "File should be created in nested directory");
        List<String[]> result = fileService.readCsv(nestedFile.toString(), ',');
        assertEquals(2, result.size(), "Should read all written data");
    }
    
    /**
     * Test CSV file with special characters
     */
    @Test
    void testCsvWithSpecialCharacters() throws IOException, CsvException {
        // Given
        List<String[]> data = List.of(
            new String[]{"id", "special"},
            new String[]{"1", "á é í ó ú ñ"},
            new String[]{"2", "漢字 & ₹ € £ ¥"}
        );
        
        Path specialFile = tempDir.resolve("special.csv");
        
        // When
        fileService.writeCsv(specialFile.toString(), data, ',');
        List<String[]> result = fileService.readCsv(specialFile.toString(), ',');
        
        // Then
        assertEquals(3, result.size(), "Should read all rows");
        assertEquals("á é í ó ú ñ", result.get(1)[1], "Accented characters should be preserved");
        assertEquals("漢字 & ₹ € £ ¥", result.get(2)[1], "Unicode and currency symbols should be preserved");
    }
    
    /**
     * Test CSV format validation
     */
    @Test
    void testValidateCsvFormat() {
        // Given
        List<String[]> validData = List.of(
            new String[]{"col1", "col2"},
            new String[]{"val1", "val2"}
        );
        
        List<String[]> invalidData = Arrays.asList(
            new String[]{"col1", "col2", "col3"},
            new String[]{"val1", "val2"} // Missing one column
        );
        
        // When/Then
        assertTrue(fileService.validateCsvFormat(validData), "Valid CSV should pass validation");
        assertFalse(fileService.validateCsvFormat(invalidData), "Invalid CSV should fail validation");
        assertFalse(fileService.validateCsvFormat(null), "Null data should fail validation");
        assertFalse(fileService.validateCsvFormat(List.of()), "Empty data should fail validation");
    }
}
