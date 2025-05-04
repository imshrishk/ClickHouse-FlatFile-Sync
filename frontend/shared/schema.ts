import { pgTable, text, serial, integer, boolean } from "drizzle-orm/pg-core";
import { createInsertSchema } from "drizzle-zod";
import { custom, z } from "zod";

export const users = pgTable("users", {
  id: serial("id").primaryKey(),
  username: text("username").notNull().unique(),
  password: text("password").notNull(),
});

export const insertUserSchema = createInsertSchema(users).pick({
  username: true,
  password: true,
});

export type InsertUser = z.infer<typeof insertUserSchema>;
export type User = typeof users.$inferSelect;

// ClickHouse Connection Schema
export const connectionSchema = z.object({
  protocol: z.enum(["http", "https"]).default("http"),
  host: z.string().default("localhost"),
  port: z.string().default("8123"),
  username: z.string().default("default"),
  database: z.string().default("default"),
  authType: z.enum(["password", "jwt"]).default("password"),
  password: z.string().optional(),
  jwt: z.string().optional(),
});

export type ConnectionConfig = z.infer<typeof connectionSchema>;

// Upload Schema
export const uploadSchema = z.object({
  totalCols: z.number(),
  connection: connectionSchema,
  tableName: z.string().min(1, { message: "Table name is required" }),
  createNewTable: z.boolean().default(false),
  delimiter: z.string().max(3, { message: "Delimiter must be <= 3 character" }).optional(),
  columnTypes: z.record(z.string()).optional(), // For mapping columns to their types
});

export type UploadConfig = z.infer<typeof uploadSchema>;

// Download Schema
export const downloadSchema = z.object({
  connection: connectionSchema,
  delimiter: z.string().max(3, { message: "Delimiter must be <= 3 character" }).optional(),
});

export type DownloadConfig = z.infer<typeof downloadSchema>;

// Tables Schema
export const tablesSchema = z.object({
  connection: connectionSchema,
});

export type TablesConfig = z.infer<typeof tablesSchema>;

// Types Schema (for getting available data types)
export const typesSchema = z.object({
  connection: connectionSchema,
});

export type TypesConfig = z.infer<typeof typesSchema>;

// Columns Schema
export const columnsSchema = z.object({
  connection: connectionSchema,
  tableName: z.string().min(1, { message: "Table name is required" }),
});

export type ColumnsConfig = z.infer<typeof columnsSchema>;

// Selected Columns Query Schema
export const selectedColumnsQuerySchema = z.object({
  connection: connectionSchema,
  tableName: z.string().min(1, { message: "Table name is required" }),
  columns: z.array(z.string()).min(1, { message: "At least one column must be selected" }),
  delimiter: z.string().max(3, { message: "Delimiter must be <= 3 character" }).optional(),
  joinTables: z.array(z.object({
    tableName: z.string(),
    joinType: z.enum([
      "INNER JOIN", 
      "LEFT JOIN", 
      "RIGHT JOIN", 
      "OUTER JOIN", 
      "SEMI JOIN", 
      "ANTI JOIN", 
      "ANY JOIN", 
      "GLOBAL JOIN", 
      "ARRAY JOIN"
    ]),
    joinCondition: z.string(),
  })).optional(),
});

export type SelectedColumnsQueryConfig = z.infer<typeof selectedColumnsQuerySchema>;
