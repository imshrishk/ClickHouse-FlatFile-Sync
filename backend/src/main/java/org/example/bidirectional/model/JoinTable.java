package org.example.bidirectional.model;

import java.util.Objects;

/**
 * Represents a table join configuration for SQL queries.
 * This class encapsulates the information needed to construct JOIN clauses
 * in SQL queries, including the table name, join type, and join condition.
 * 
 * Supported join types include:
 * - INNER JOIN
 * - LEFT JOIN
 * - RIGHT JOIN
 * - FULL JOIN
 * - CROSS JOIN
 */
public class JoinTable {
    private String tableName;
    private String joinType;
    private String joinCondition;
    
    /**
     * Default constructor for serialization/deserialization purposes.
     */
    public JoinTable() {}
    
    /**
     * Creates a new JoinTable with the specified configuration.
     * 
     * @param tableName The name of the table to join
     * @param joinType The type of join (e.g., LEFT JOIN, INNER JOIN)
     * @param joinCondition The condition for the join (e.g., "t1.id = t2.id")
     */
    public JoinTable(String tableName, String joinType, String joinCondition) {
        this.tableName = tableName;
        setJoinType(joinType); // Uses the setter to ensure uppercase conversion
        this.joinCondition = joinCondition;
    }

    /**
     * Gets the table name for the join.
     * 
     * @return The table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Sets the table name for the join.
     * 
     * @param tableName The table name to set
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * Gets the join type (e.g., LEFT JOIN, INNER JOIN).
     * 
     * @return The join type
     */
    public String getJoinType() {
        return joinType;
    }

    /**
     * Sets the join type, converting it to uppercase for SQL syntax consistency.
     * 
     * @param joinType The join type to set
     */
    public void setJoinType(String joinType) {
        this.joinType = joinType != null ? joinType.toUpperCase() : null;
    }

    /**
     * Gets the join condition (the ON clause).
     * 
     * @return The join condition
     */
    public String getJoinCondition() {
        return joinCondition;
    }

    /**
     * Sets the join condition (the ON clause).
     * 
     * @param joinCondition The join condition to set
     */
    public void setJoinCondition(String joinCondition) {
        this.joinCondition = joinCondition;
    }
    
    /**
     * Checks if this JoinTable configuration is valid.
     * A valid JoinTable must have a non-empty tableName, a non-empty joinType,
     * and a non-empty joinCondition.
     * 
     * @return true if all required fields are present, false otherwise
     */
    public boolean isValid() {
        return tableName != null && !tableName.trim().isEmpty() &&
               joinType != null && !joinType.trim().isEmpty() &&
               joinCondition != null && !joinCondition.trim().isEmpty();
    }
    
    /**
     * Returns a SQL representation of this join configuration.
     * 
     * @return A string in the format "JOIN_TYPE table_name ON join_condition"
     */
    public String toSqlString() {
        return (joinType != null ? joinType : "JOIN") + " " + 
               (tableName != null ? tableName : "") + " ON " + 
               (joinCondition != null ? joinCondition : "1=1");
    }
    
    /**
     * Returns a string representation of this JoinTable.
     * 
     * @return A string representation
     */
    @Override
    public String toString() {
        return "JoinTable{" +
                "tableName='" + tableName + '\'' +
                ", joinType='" + joinType + '\'' +
                ", joinCondition='" + joinCondition + '\'' +
                '}';
    }
    
    /**
     * Compares this JoinTable with another object for equality.
     * Two JoinTable objects are equal if they have the same tableName, joinType, and joinCondition.
     * 
     * @param obj The object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        JoinTable other = (JoinTable) obj;
        return Objects.equals(tableName, other.tableName) &&
               Objects.equals(joinType, other.joinType) &&
               Objects.equals(joinCondition, other.joinCondition);
    }
    
    /**
     * Computes a hash code for this JoinTable.
     * 
     * @return The hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(tableName, joinType, joinCondition);
    }
}
