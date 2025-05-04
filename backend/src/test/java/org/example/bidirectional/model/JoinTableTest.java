package org.example.bidirectional.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JoinTable class.
 * Tests all methods and edge cases for the JoinTable model.
 */
class JoinTableTest {

    /**
     * Test the default constructor creates an object with null fields
     */
    @Test
    void testDefaultConstructor() {
        // When
        JoinTable joinTable = new JoinTable();
        
        // Then
        assertNull(joinTable.getTableName(), "Table name should be null");
        assertNull(joinTable.getJoinType(), "Join type should be null");
        assertNull(joinTable.getJoinCondition(), "Join condition should be null");
    }
    
    /**
     * Test the parameterized constructor correctly sets the fields
     */
    @Test
    void testParameterizedConstructor() {
        // Given
        String tableName = "users";
        String joinType = "left join";
        String joinCondition = "users.id = orders.user_id";
        
        // When
        JoinTable joinTable = new JoinTable(tableName, joinType, joinCondition);
        
        // Then
        assertEquals(tableName, joinTable.getTableName(), "Table name should match constructor parameter");
        assertEquals(joinType.toUpperCase(), joinTable.getJoinType(), "Join type should be uppercase");
        assertEquals(joinCondition, joinTable.getJoinCondition(), "Join condition should match constructor parameter");
    }
    
    /**
     * Test the setters correctly update the fields
     */
    @Test
    void testSetters() {
        // Given
        JoinTable joinTable = new JoinTable();
        String tableName = "orders";
        String joinType = "inner join";
        String joinCondition = "orders.id = order_items.order_id";
        
        // When
        joinTable.setTableName(tableName);
        joinTable.setJoinType(joinType);
        joinTable.setJoinCondition(joinCondition);
        
        // Then
        assertEquals(tableName, joinTable.getTableName(), "Table name should be updated");
        assertEquals(joinType.toUpperCase(), joinTable.getJoinType(), "Join type should be uppercase");
        assertEquals(joinCondition, joinTable.getJoinCondition(), "Join condition should be updated");
    }
    
    /**
     * Test the setJoinType method handles null input
     */
    @Test
    void testSetJoinTypeWithNull() {
        // Given
        JoinTable joinTable = new JoinTable();
        
        // When
        joinTable.setJoinType(null);
        
        // Then
        assertNull(joinTable.getJoinType(), "Join type should remain null");
    }
    
    /**
     * Test the isValid method with valid inputs
     */
    @Test
    void testIsValidWithValidInput() {
        // Given
        JoinTable joinTable = new JoinTable("users", "LEFT JOIN", "users.id = orders.user_id");
        
        // When/Then
        assertTrue(joinTable.isValid(), "Valid join table should be valid");
    }
    
    /**
     * Test the isValid method with various invalid inputs
     */
    @ParameterizedTest
    @MethodSource("invalidJoinTableProvider")
    void testIsValidWithInvalidInput(String tableName, String joinType, String joinCondition, String message) {
        // Given
        JoinTable joinTable = new JoinTable();
        joinTable.setTableName(tableName);
        joinTable.setJoinType(joinType);
        joinTable.setJoinCondition(joinCondition);
        
        // When/Then
        assertFalse(joinTable.isValid(), message);
    }
    
    /**
     * Provider for invalid JoinTable test cases
     */
    static Stream<Arguments> invalidJoinTableProvider() {
        return Stream.of(
            Arguments.of(null, "LEFT JOIN", "a.id = b.id", "Null table name should be invalid"),
            Arguments.of("", "LEFT JOIN", "a.id = b.id", "Empty table name should be invalid"),
            Arguments.of("  ", "LEFT JOIN", "a.id = b.id", "Whitespace table name should be invalid"),
            Arguments.of("users", null, "a.id = b.id", "Null join type should be invalid"),
            Arguments.of("users", "", "a.id = b.id", "Empty join type should be invalid"),
            Arguments.of("users", "  ", "a.id = b.id", "Whitespace join type should be invalid"),
            Arguments.of("users", "LEFT JOIN", null, "Null join condition should be invalid"),
            Arguments.of("users", "LEFT JOIN", "", "Empty join condition should be invalid"),
            Arguments.of("users", "LEFT JOIN", "  ", "Whitespace join condition should be invalid")
        );
    }
    
    /**
     * Test toSqlString returns the expected format
     */
    @Test
    void testToSqlString() {
        // Given
        String tableName = "users";
        String joinType = "LEFT JOIN";
        String joinCondition = "users.id = orders.user_id";
        JoinTable joinTable = new JoinTable(tableName, joinType, joinCondition);
        String expected = joinType + " " + tableName + " ON " + joinCondition;
        
        // When
        String result = joinTable.toSqlString();
        
        // Then
        assertEquals(expected, result, "SQL string should match expected format");
    }
    
    /**
     * Test toSqlString handles null values
     */
    @Test
    void testToSqlStringWithNulls() {
        // Given
        JoinTable joinTable = new JoinTable(null, null, null);
        
        // When
        String result = joinTable.toSqlString();
        
        // Then
        assertEquals("JOIN  ON 1=1", result, "SQL string should handle null values");
    }
    
    /**
     * Test toString returns the expected format
     */
    @Test
    void testToString() {
        // Given
        String tableName = "users";
        String joinType = "LEFT JOIN";
        String joinCondition = "users.id = orders.user_id";
        JoinTable joinTable = new JoinTable(tableName, joinType, joinCondition);
        
        // When
        String result = joinTable.toString();
        
        // Then
        assertTrue(result.contains(tableName), "ToString should contain table name");
        assertTrue(result.contains(joinType), "ToString should contain join type");
        assertTrue(result.contains(joinCondition), "ToString should contain join condition");
    }
    
    /**
     * Test equals method with equal objects
     */
    @Test
    void testEqualsWithEqualObjects() {
        // Given
        JoinTable joinTable1 = new JoinTable("users", "LEFT JOIN", "users.id = orders.user_id");
        JoinTable joinTable2 = new JoinTable("users", "LEFT JOIN", "users.id = orders.user_id");
        
        // When/Then
        assertEquals(joinTable1, joinTable2, "Equal objects should be equal");
        assertEquals(joinTable1.hashCode(), joinTable2.hashCode(), "Equal objects should have same hash code");
    }
    
    /**
     * Test equals method with different objects
     */
    @ParameterizedTest
    @MethodSource("differentJoinTableProvider")
    void testEqualsWithDifferentObjects(JoinTable joinTable1, JoinTable joinTable2, String message) {
        // When/Then
        assertNotEquals(joinTable1, joinTable2, message);
    }
    
    /**
     * Provider for different JoinTable test cases
     */
    static Stream<Arguments> differentJoinTableProvider() {
        return Stream.of(
            Arguments.of(
                new JoinTable("users", "LEFT JOIN", "users.id = orders.user_id"),
                new JoinTable("customers", "LEFT JOIN", "users.id = orders.user_id"),
                "Different table names should not be equal"
            ),
            Arguments.of(
                new JoinTable("users", "LEFT JOIN", "users.id = orders.user_id"),
                new JoinTable("users", "INNER JOIN", "users.id = orders.user_id"),
                "Different join types should not be equal"
            ),
            Arguments.of(
                new JoinTable("users", "LEFT JOIN", "users.id = orders.user_id"),
                new JoinTable("users", "LEFT JOIN", "users.id = items.user_id"),
                "Different join conditions should not be equal"
            )
        );
    }
    
    /**
     * Test equals method with null and other object types
     */
    @Test
    void testEqualsWithNullAndOtherTypes() {
        // Given
        JoinTable joinTable = new JoinTable("users", "LEFT JOIN", "users.id = orders.user_id");
        
        // When/Then
        assertNotEquals(null, joinTable, "Join table should not equal null");
        assertNotEquals(joinTable, "users", "Join table should not equal string");
    }
    
    /**
     * Test equals with same instance
     */
    @Test
    void testEqualsWithSameInstance() {
        // Given
        JoinTable joinTable = new JoinTable("users", "LEFT JOIN", "users.id = orders.user_id");
        
        // When/Then
        assertEquals(joinTable, joinTable, "Same instance should be equal");
    }
} 