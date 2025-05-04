package org.example.bidirectional.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ColumnInfo class.
 * Tests all methods and edge cases for the ColumnInfo model.
 */
class ColumnInfoTest {

    /**
     * Test the default constructor creates an object with null fields
     */
    @Test
    void testDefaultConstructor() {
        // When
        ColumnInfo columnInfo = new ColumnInfo();
        
        // Then
        assertNull(columnInfo.getName(), "Name should be null");
        assertNull(columnInfo.getType(), "Type should be null");
    }
    
    /**
     * Test the parameterized constructor correctly sets the fields
     */
    @Test
    void testParameterizedConstructor() {
        // Given
        String name = "user_id";
        String type = "Int32";
        
        // When
        ColumnInfo columnInfo = new ColumnInfo(name, type);
        
        // Then
        assertEquals(name, columnInfo.getName(), "Name should match constructor parameter");
        assertEquals(type, columnInfo.getType(), "Type should match constructor parameter");
    }
    
    /**
     * Test the setters correctly update the fields
     */
    @Test
    void testSetters() {
        // Given
        ColumnInfo columnInfo = new ColumnInfo();
        String name = "timestamp";
        String type = "DateTime";
        
        // When
        columnInfo.setName(name);
        columnInfo.setType(type);
        
        // Then
        assertEquals(name, columnInfo.getName(), "Name should be updated");
        assertEquals(type, columnInfo.getType(), "Type should be updated");
    }
    
    /**
     * Test the isValid method with valid inputs
     */
    @Test
    void testIsValidWithValidInput() {
        // Given
        ColumnInfo columnInfo = new ColumnInfo("column1", "String");
        
        // When/Then
        assertTrue(columnInfo.isValid(), "Column with non-empty name and type should be valid");
    }
    
    /**
     * Test the isValid method with various invalid inputs
     */
    @ParameterizedTest
    @MethodSource("invalidColumnInfoProvider")
    void testIsValidWithInvalidInput(String name, String type, String message) {
        // Given
        ColumnInfo columnInfo = new ColumnInfo(name, type);
        
        // When/Then
        assertFalse(columnInfo.isValid(), message);
    }
    
    /**
     * Provider for invalid ColumnInfo test cases
     */
    static Stream<Arguments> invalidColumnInfoProvider() {
        return Stream.of(
            Arguments.of(null, "String", "Null name should be invalid"),
            Arguments.of("", "String", "Empty name should be invalid"),
            Arguments.of("  ", "String", "Whitespace name should be invalid"),
            Arguments.of("column1", null, "Null type should be invalid"),
            Arguments.of("column1", "", "Empty type should be invalid"),
            Arguments.of("column1", "  ", "Whitespace type should be invalid"),
            Arguments.of(null, null, "Null name and type should be invalid")
        );
    }
    
    /**
     * Test toString returns the expected format
     */
    @Test
    void testToString() {
        // Given
        String name = "price";
        String type = "Float64";
        ColumnInfo columnInfo = new ColumnInfo(name, type);
        String expected = name + " (" + type + ")";
        
        // When
        String result = columnInfo.toString();
        
        // Then
        assertEquals(expected, result, "ToString should match expected format");
    }
    
    /**
     * Test equals method with equal objects
     */
    @Test
    void testEqualsWithEqualObjects() {
        // Given
        ColumnInfo columnInfo1 = new ColumnInfo("id", "UInt32");
        ColumnInfo columnInfo2 = new ColumnInfo("id", "UInt32");
        
        // When/Then
        assertEquals(columnInfo1, columnInfo2, "Equal objects should be equal");
        assertEquals(columnInfo1.hashCode(), columnInfo2.hashCode(), "Equal objects should have same hash code");
    }
    
    /**
     * Test equals method with different objects
     */
    @ParameterizedTest
    @MethodSource("differentColumnInfoProvider")
    void testEqualsWithDifferentObjects(ColumnInfo columnInfo1, ColumnInfo columnInfo2, String message) {
        // When/Then
        assertNotEquals(columnInfo1, columnInfo2, message);
    }
    
    /**
     * Provider for different ColumnInfo test cases
     */
    static Stream<Arguments> differentColumnInfoProvider() {
        return Stream.of(
            Arguments.of(
                new ColumnInfo("column1", "String"),
                new ColumnInfo("column2", "String"),
                "Different names should not be equal"
            ),
            Arguments.of(
                new ColumnInfo("column1", "String"),
                new ColumnInfo("column1", "Int32"),
                "Different types should not be equal"
            ),
            Arguments.of(
                new ColumnInfo("column1", "String"),
                new ColumnInfo("COLUMN1", "String"),
                "Names are case-sensitive"
            ),
            Arguments.of(
                new ColumnInfo("column1", "string"),
                new ColumnInfo("column1", "String"),
                "Types are case-sensitive"
            )
        );
    }
    
    /**
     * Test equals method with null and other object types
     */
    @Test
    void testEqualsWithNullAndOtherTypes() {
        // Given
        ColumnInfo columnInfo = new ColumnInfo("column1", "String");
        
        // When/Then
        assertNotEquals(null, columnInfo, "Column should not equal null");
        assertNotEquals(columnInfo, "column1", "Column should not equal string");
    }
    
    /**
     * Test equals with same instance
     */
    @Test
    void testEqualsWithSameInstance() {
        // Given
        ColumnInfo columnInfo = new ColumnInfo("column1", "String");
        
        // When/Then
        assertEquals(columnInfo, columnInfo, "Same instance should be equal");
    }
} 