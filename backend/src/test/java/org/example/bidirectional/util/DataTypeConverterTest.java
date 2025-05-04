package org.example.bidirectional.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClickHouse data type conversion utilities.
 * Validates proper conversion between CSV string values and ClickHouse data types.
 */
class DataTypeConverterTest {

    /**
     * Tests string value conversion to ClickHouse Int32 type.
     */
    @ParameterizedTest
    @CsvSource({
        "123, 123",
        "0, 0",
        "-456, -456",
        "2147483647, 2147483647",
        "-2147483648, -2147483648"
    })
    void testConvertToInt32(String input, int expected) {
        assertEquals(expected, DataTypeConverter.convertToInt32(input));
    }
    
    /**
     * Tests invalid values for Int32 conversion.
     */
    @ParameterizedTest
    @CsvSource({
        "abc",
        "2147483648", // Max Int32 + 1
        "-2147483649", // Min Int32 - 1
        "123.45",
        "1,234"
    })
    void testConvertToInt32WithInvalidInput(String input) {
        assertThrows(NumberFormatException.class, 
                () -> DataTypeConverter.convertToInt32(input));
    }
    
    /**
     * Tests string value conversion to ClickHouse Int64 type.
     */
    @ParameterizedTest
    @CsvSource({
        "123, 123",
        "0, 0",
        "-456, -456",
        "9223372036854775807, 9223372036854775807", // Max Long value
        "-9223372036854775808, -9223372036854775808" // Min Long value
    })
    void testConvertToInt64(String input, long expected) {
        assertEquals(expected, DataTypeConverter.convertToInt64(input));
    }
    
    /**
     * Tests invalid values for Int64 conversion.
     */
    @ParameterizedTest
    @CsvSource({
        "abc",
        "9223372036854775808", // Max Long + 1
        "-9223372036854775809", // Min Long - 1
        "123.45"
    })
    void testConvertToInt64WithInvalidInput(String input) {
        assertThrows(NumberFormatException.class, 
                () -> DataTypeConverter.convertToInt64(input));
    }
    
    /**
     * Tests string value conversion to ClickHouse Float64 type.
     */
    @ParameterizedTest
    @CsvSource({
        "123.45, 123.45",
        "0.0, 0.0",
        "-456.789, -456.789",
        "1.7976931348623157E308, 1.7976931348623157E308", // Max Double value
        "4.9E-324, 4.9E-324" // Min positive Double value
    })
    void testConvertToFloat64(String input, double expected) {
        assertEquals(expected, DataTypeConverter.convertToFloat64(input));
    }
    
    /**
     * Tests invalid values for Float64 conversion.
     */
    @ParameterizedTest
    @CsvSource({
        "abc",
        "123,456.78"
    })
    void testConvertToFloat64WithInvalidInput(String input) {
        assertThrows(NumberFormatException.class, 
                () -> DataTypeConverter.convertToFloat64(input));
    }
    
    /**
     * Tests string value conversion to ClickHouse UUID type.
     */
    @ParameterizedTest
    @CsvSource({
        "123e4567-e89b-12d3-a456-426614174000",
        "00000000-0000-0000-0000-000000000000",
        "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"
    })
    void testConvertToUUID(String input) {
        UUID result = DataTypeConverter.convertToUUID(input);
        assertEquals(input.toLowerCase(), result.toString());
    }
    
    /**
     * Tests invalid values for UUID conversion.
     */
    @ParameterizedTest
    @CsvSource({
        "123",
        "123e4567e89b12d3a456426614174000", // No hyphens
        "123e4567-e89b-12d3-a456-42661417400X" // Invalid character
    })
    @NullAndEmptySource
    void testConvertToUUIDWithInvalidInput(String input) {
        assertThrows(IllegalArgumentException.class, 
                () -> DataTypeConverter.convertToUUID(input));
    }
    
    /**
     * Tests string value conversion to ClickHouse Date type.
     */
    @ParameterizedTest
    @CsvSource({
        "2023-01-15, 2023-01-15",
        "1970-01-01, 1970-01-01",
        "2100-12-31, 2100-12-31"
    })
    void testConvertToDate(String input, String expected) {
        LocalDate result = DataTypeConverter.convertToDate(input);
        assertEquals(LocalDate.parse(expected), result);
    }
    
    /**
     * Tests invalid values for Date conversion.
     */
    @ParameterizedTest
    @CsvSource({
        "abc",
        "01/15/2023", // Wrong format
        "2023-13-01", // Invalid month
        "2023-01-32" // Invalid day
    })
    @NullAndEmptySource
    void testConvertToDateWithInvalidInput(String input) {
        assertThrows(DateTimeParseException.class, 
                () -> DataTypeConverter.convertToDate(input));
    }
    
    /**
     * Tests string value conversion to ClickHouse DateTime type.
     */
    @ParameterizedTest
    @CsvSource({
        "2023-01-15T12:30:45, 2023-01-15T12:30:45",
        "1970-01-01T00:00:00, 1970-01-01T00:00:00",
        "2100-12-31T23:59:59, 2100-12-31T23:59:59"
    })
    void testConvertToDateTime(String input, String expected) {
        LocalDateTime result = DataTypeConverter.convertToDateTime(input);
        assertEquals(LocalDateTime.parse(expected), result);
    }
    
    /**
     * Tests invalid values for DateTime conversion.
     */
    @ParameterizedTest
    @CsvSource({
        "abc",
        "01/15/2023 12:30:45", // Wrong format
        "2023-13-01T12:30:45", // Invalid month
        "2023-01-32T12:30:45", // Invalid day
        "2023-01-15T25:30:45" // Invalid hour
    })
    @NullAndEmptySource
    void testConvertToDateTimeWithInvalidInput(String input) {
        assertThrows(DateTimeParseException.class, 
                () -> DataTypeConverter.convertToDateTime(input));
    }
    
    /**
     * Tests string value conversion to ClickHouse Boolean type.
     */
    @ParameterizedTest
    @CsvSource({
        "true, true",
        "True, true",
        "TRUE, true",
        "false, false",
        "False, false",
        "FALSE, false",
        "1, true",
        "0, false",
        "yes, true",
        "no, false"
    })
    void testConvertToBoolean(String input, boolean expected) {
        assertEquals(expected, DataTypeConverter.convertToBoolean(input));
    }
    
    /**
     * Tests invalid values for Boolean conversion.
     */
    @ParameterizedTest
    @CsvSource({
        "abc",
        "2",
        "-1"
    })
    @NullAndEmptySource
    void testConvertToBooleanWithInvalidInput(String input) {
        assertThrows(IllegalArgumentException.class, 
                () -> DataTypeConverter.convertToBoolean(input));
    }
    
    /**
     * Tests conversion of string values to appropriate types based on ClickHouse type name.
     */
    @ParameterizedTest
    @MethodSource("typeConversionProvider")
    void testConvertValueByType(String value, String clickHouseType, Object expected) {
        Object result = DataTypeConverter.convertValueByType(value, clickHouseType);
        assertEquals(expected, result);
    }
    
    /**
     * Provider for type conversion test cases.
     */
    static Stream<Arguments> typeConversionProvider() {
        return Stream.of(
            Arguments.of("123", "Int32", 123),
            Arguments.of("123456789", "Int64", 123456789L),
            Arguments.of("123.45", "Float64", 123.45),
            Arguments.of("2023-01-15", "Date", LocalDate.parse("2023-01-15")),
            Arguments.of("2023-01-15T12:30:45", "DateTime", LocalDateTime.parse("2023-01-15T12:30:45")),
            Arguments.of("true", "Bool", true),
            Arguments.of("123e4567-e89b-12d3-a456-426614174000", "UUID", 
                        UUID.fromString("123e4567-e89b-12d3-a456-426614174000")),
            Arguments.of("test string", "String", "test string")
        );
    }
    
    /**
     * Tests detecting ClickHouse type from sample values.
     */
    @Test
    void testDetectType() {
        assertEquals("Int32", DataTypeConverter.detectType("123", "456", "789"));
        assertEquals("Int64", DataTypeConverter.detectType("9223372036854775807", "1", "2"));
        assertEquals("Float64", DataTypeConverter.detectType("123.45", "67.89", "0.0"));
        assertEquals("String", DataTypeConverter.detectType("abc", "def", "ghi"));
        assertEquals("Date", DataTypeConverter.detectType("2023-01-15", "2022-12-31", "2024-06-30"));
        assertEquals("DateTime", DataTypeConverter.detectType(
                "2023-01-15T12:30:45", "2022-12-31T23:59:59", "2024-06-30T00:00:00"));
        assertEquals("Bool", DataTypeConverter.detectType("true", "false", "true"));
        assertEquals("UUID", DataTypeConverter.detectType(
                "123e4567-e89b-12d3-a456-426614174000", 
                "00000000-0000-0000-0000-000000000000", 
                "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"));
    }
    
    /**
     * Tests mixed types defaulting to String.
     */
    @Test
    void testMixedTypesDefaultToString() {
        assertEquals("String", DataTypeConverter.detectType("123", "abc", "456"));
        assertEquals("String", DataTypeConverter.detectType("123.45", "abc", "true"));
        assertEquals("String", DataTypeConverter.detectType("2023-01-15", "not a date", "2022-12-31"));
    }
    
    /**
     * Tests null and empty values handling in type detection.
     */
    @Test
    void testNullAndEmptyHandling() {
        assertEquals("Int32", DataTypeConverter.detectType("123", null, ""));
        assertEquals("String", DataTypeConverter.detectType(null, null, null));
        assertEquals("String", DataTypeConverter.detectType("", "", ""));
    }
    
    /**
     * Tests creating a map of suggested column types from headers and values.
     */
    @Test
    void testSuggestColumnTypes() {
        String[] headers = {"id", "name", "age", "price", "birthdate", "created_at", "is_active", "external_id"};
        String[][] rows = {
            {"1", "John", "30", "99.99", "1993-05-20", "2023-01-15T12:30:45", "true", "123e4567-e89b-12d3-a456-426614174000"},
            {"2", "Jane", "25", "149.99", "1998-10-15", "2023-02-20T10:15:30", "false", "00000000-0000-0000-0000-000000000000"}
        };
        
        Map<String, String> expected = new HashMap<>();
        expected.put("id", "Int32");
        expected.put("name", "String");
        expected.put("age", "Int32");
        expected.put("price", "Float64");
        expected.put("birthdate", "Date");
        expected.put("created_at", "DateTime");
        expected.put("is_active", "Bool");
        expected.put("external_id", "UUID");
        
        Map<String, String> result = DataTypeConverter.suggestColumnTypes(headers, rows);
        assertEquals(expected, result);
    }
    
    /**
     * Tests handling of empty dataset in type suggestion.
     */
    @Test
    void testSuggestColumnTypesWithEmptyData() {
        String[] headers = {"id", "name", "age"};
        String[][] rows = {};
        
        Map<String, String> expected = new HashMap<>();
        expected.put("id", "String");
        expected.put("name", "String");
        expected.put("age", "String");
        
        Map<String, String> result = DataTypeConverter.suggestColumnTypes(headers, rows);
        assertEquals(expected, result);
    }
}

/**
 * Utility class for converting between CSV string values and ClickHouse data types.
 */
class DataTypeConverter {
    /**
     * Converts a string value to the appropriate Java type based on ClickHouse type.
     */
    public static Object convertValueByType(String value, String clickHouseType) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        return switch (clickHouseType) {
            case "Int32" -> convertToInt32(value);
            case "Int64" -> convertToInt64(value);
            case "Float64" -> convertToFloat64(value);
            case "Date" -> convertToDate(value);
            case "DateTime" -> convertToDateTime(value);
            case "Bool" -> convertToBoolean(value);
            case "UUID" -> convertToUUID(value);
            default -> value; // String and other types
        };
    }
    
    /**
     * Converts string to Integer.
     */
    public static int convertToInt32(String value) {
        return Integer.parseInt(value);
    }
    
    /**
     * Converts string to Long.
     */
    public static long convertToInt64(String value) {
        return Long.parseLong(value);
    }
    
    /**
     * Converts string to Double.
     */
    public static double convertToFloat64(String value) {
        return Double.parseDouble(value);
    }
    
    /**
     * Converts string to LocalDate.
     */
    public static LocalDate convertToDate(String value) {
        return LocalDate.parse(value);
    }
    
    /**
     * Converts string to LocalDateTime.
     */
    public static LocalDateTime convertToDateTime(String value) {
        return LocalDateTime.parse(value);
    }
    
    /**
     * Converts string to Boolean.
     */
    public static boolean convertToBoolean(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Cannot convert null or empty value to boolean");
        }
        
        String lowerValue = value.toLowerCase();
        if (lowerValue.equals("true") || lowerValue.equals("1") || lowerValue.equals("yes")) {
            return true;
        } else if (lowerValue.equals("false") || lowerValue.equals("0") || lowerValue.equals("no")) {
            return false;
        } else {
            throw new IllegalArgumentException("Cannot convert '" + value + "' to boolean");
        }
    }
    
    /**
     * Converts string to UUID.
     */
    public static UUID convertToUUID(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Cannot convert null or empty value to UUID");
        }
        
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + value);
        }
    }
    
    /**
     * Detects the most appropriate ClickHouse type from sample values.
     */
    public static String detectType(String... values) {
        // Check for all null or empty values
        boolean allNullOrEmpty = true;
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                allNullOrEmpty = false;
                break;
            }
        }
        
        if (allNullOrEmpty) {
            return "String";
        }
        
        boolean couldBeInt32 = true;
        boolean couldBeInt64 = true;
        boolean couldBeFloat64 = true;
        boolean couldBeDate = true;
        boolean couldBeDateTime = true;
        boolean couldBeBoolean = true;
        boolean couldBeUUID = true;
        
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            
            // Try Int32
            if (couldBeInt32) {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    couldBeInt32 = false;
                }
            }
            
            // Try Int64
            if (couldBeInt64) {
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    couldBeInt64 = false;
                }
            }
            
            // Try Float64
            if (couldBeFloat64) {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    couldBeFloat64 = false;
                }
            }
            
            // Try Date
            if (couldBeDate) {
                try {
                    LocalDate.parse(value);
                } catch (Exception e) {
                    couldBeDate = false;
                }
            }
            
            // Try DateTime
            if (couldBeDateTime) {
                try {
                    LocalDateTime.parse(value);
                } catch (Exception e) {
                    couldBeDateTime = false;
                }
            }
            
            // Try Boolean
            if (couldBeBoolean) {
                String lowerValue = value.toLowerCase();
                if (!lowerValue.equals("true") && !lowerValue.equals("false") && 
                    !lowerValue.equals("1") && !lowerValue.equals("0") &&
                    !lowerValue.equals("yes") && !lowerValue.equals("no")) {
                    couldBeBoolean = false;
                }
            }
            
            // Try UUID
            if (couldBeUUID) {
                try {
                    UUID.fromString(value);
                } catch (Exception e) {
                    couldBeUUID = false;
                }
            }
        }
        
        // Return the most specific type that fits all values
        if (couldBeInt32) return "Int32";
        if (couldBeInt64) return "Int64";
        if (couldBeFloat64) return "Float64";
        if (couldBeDate) return "Date";
        if (couldBeDateTime) return "DateTime";
        if (couldBeBoolean) return "Bool";
        if (couldBeUUID) return "UUID";
        
        return "String";
    }
    
    /**
     * Suggests column types based on headers and sample data rows.
     */
    public static Map<String, String> suggestColumnTypes(String[] headers, String[][] rows) {
        Map<String, String> columnTypes = new HashMap<>();
        
        // Default to String if no data
        if (rows.length == 0) {
            for (String header : headers) {
                columnTypes.put(header, "String");
            }
            return columnTypes;
        }
        
        // Detect types for each column
        for (int i = 0; i < headers.length; i++) {
            String[] columnValues = new String[rows.length];
            for (int j = 0; j < rows.length; j++) {
                if (i < rows[j].length) {
                    columnValues[j] = rows[j][i];
                } else {
                    columnValues[j] = null; // Handle missing values
                }
            }
            
            columnTypes.put(headers[i], detectType(columnValues));
        }
        
        return columnTypes;
    }
} 