package org.example.bidirectional.model;

import java.util.Objects;

/**
 * Represents information about a database column, including its name and data type.
 * This class is used for database schema operations and data transfer configurations.
 * 
 * It provides information about:
 * - Column name: The identifier used in database tables
 * - Column type: The data type of the column (e.g., String, Int32, DateTime)
 */
public class ColumnInfo {
    private String name;
    private String type;

    /**
     * Default constructor for serialization/deserialization purposes.
     */
    public ColumnInfo() {}

    /**
     * Creates a new ColumnInfo with the specified name and type.
     * 
     * @param name The column name
     * @param type The column data type
     */
    public ColumnInfo(String name, String type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Gets the column name.
     * 
     * @return The column name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the column name.
     * 
     * @param name The column name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the column data type.
     * 
     * @return The column data type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the column data type.
     * 
     * @param type The column data type to set
     */
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Checks if this ColumnInfo is valid (has non-empty name and type)
     * 
     * @return true if both name and type are non-empty, false otherwise
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() && 
               type != null && !type.trim().isEmpty();
    }
    
    /**
     * Returns a string representation of this ColumnInfo.
     * 
     * @return A string in the format "name (type)"
     */
    @Override
    public String toString() {
        return name + " (" + type + ")";
    }
    
    /**
     * Compares this ColumnInfo with another object for equality.
     * Two ColumnInfo objects are equal if they have the same name and type (case-sensitive).
     * 
     * @param obj The object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ColumnInfo other = (ColumnInfo) obj;
        return Objects.equals(name, other.name) && 
               Objects.equals(type, other.type);
    }
    
    /**
     * Computes a hash code for this ColumnInfo.
     * 
     * @return The hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }
}
