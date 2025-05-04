import { useState, useEffect, useCallback } from 'react';
import { useLocation } from 'wouter';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Download, Eye, Loader2, Plus, X, RefreshCw, ArrowLeftRight } from 'lucide-react';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage, FormDescription } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Textarea } from '@/components/ui/textarea';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter
} from '@/components/ui/dialog';
import {
  ConnectionConfig,
  downloadSchema,
  SelectedColumnsQueryConfig
} from '@shared/schema';
import ConnectionForm from '@/components/ConnectionForm';
import ProgressButton from '@/components/ProgressButton';
import {
  downloadData,
  getTables,
  getColumns,
  queryWithSelectedColumns,
  type ColumnInfo
} from '@/lib/clickhouse';
import { useToast } from '@/hooks/use-toast';
import * as z from 'zod';

// Define form schema
const formSchema = z.object({
  delimiter: z.string().max(3, { message: "Delimiter must be <= 3 character" }),
  useMultipleTables: z.boolean().default(false),
  mainTable: z.string().min(1, "Table selection is required"),
});

type FormValues = z.infer<typeof formSchema>;

export default function DownloadPage() {
  const [location, navigate] = useLocation();
  const [connectionConfig, setConnectionConfig] = useState<ConnectionConfig | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingTables, setIsLoadingTables] = useState(false);
  const [isLoadingColumns, setIsLoadingColumns] = useState(false);
  const [queryResult, setQueryResult] = useState<{ headers: string[]; rows: any[] } | null>(null);
  const [previewDialogOpen, setPreviewDialogOpen] = useState(false);
  const [tables, setTables] = useState<string[]>([]);
  const [columns, setColumns] = useState<ColumnInfo[]>([]);
  const [selectedColumns, setSelectedColumns] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [joinTables, setJoinTables] = useState<{
    id: number;
    tableName: string;
    joinType:
    "INNER JOIN" | "LEFT JOIN" | "RIGHT JOIN" | "OUTER JOIN" | "SEMI JOIN"
    | "ANTI JOIN" | "ANY JOIN" | "GLOBAL JOIN" | "ARRAY JOIN";
    joinCondition: string;
    columns: ColumnInfo[];
    selectedColumns: string[];
  }[]>([]);
  const { toast } = useToast();

  // Form setup
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      delimiter: ',',
      useMultipleTables: false,
      mainTable: '',
    },
  });

  // Watch for changes in selected main table
  const mainTable = form.watch('mainTable');
  const useMultipleTables = form.watch('useMultipleTables');

  // Fetch tables when connection is established
  useEffect(() => {
    if (connectionConfig) {
      fetchTables();
    }
  }, [connectionConfig]);

  // Fetch columns when main table changes
  useEffect(() => {
    if (mainTable && connectionConfig) {
      fetchColumns(mainTable);
      // Reset selected columns when table changes
      setSelectedColumns([]);
    }
  }, [mainTable, connectionConfig]);

  // Function to fetch tables from the database
  const fetchTables = async () => {
    if (!connectionConfig) return;

    try {
      setIsLoadingTables(true);
      setError(null);
      const tablesList = await getTables({ connection: connectionConfig });
      setTables(tablesList);
    } catch (error) {
      setError(error instanceof Error ? error.message : "Failed to fetch tables");
      toast({
        title: "Error Fetching Tables",
        description: error instanceof Error ? error.message : "Failed to fetch tables",
        variant: "destructive",
      });
    } finally {
      setIsLoadingTables(false);
    }
  };

  // Function to fetch columns for a specific table
  const fetchColumns = async (tableName: string) => {
    if (!connectionConfig) return;

    try {
      setIsLoadingColumns(true);
      setError(null);
      const columnsData = await getColumns({
        connection: connectionConfig,
        tableName
      });
      setColumns(columnsData);
    } catch (error) {
      setError(error instanceof Error ? error.message : "Failed to fetch columns");
      toast({
        title: "Error Fetching Columns",
        description: error instanceof Error ? error.message : "Failed to fetch columns",
        variant: "destructive",
      });
    } finally {
      setIsLoadingColumns(false);
    }
  };

  // Function to fetch columns for a joined table
  const fetchJoinTableColumns = async (tableName: string, joinIndex: number) => {
    if (!connectionConfig) return;

    try {
      const columnsData = await getColumns({
        connection: connectionConfig,
        tableName
      });

      // Update the join tables array with the columns
      setJoinTables(prevJoinTables => {
        const updatedJoinTables = [...prevJoinTables];
        updatedJoinTables[joinIndex] = {
          ...updatedJoinTables[joinIndex],
          columns: columnsData,
          selectedColumns: []
        };
        return updatedJoinTables;
      });
    } catch (error) {
      toast({
        title: "Error Fetching Columns",
        description: error instanceof Error ? error.message : "Failed to fetch columns for joined table",
        variant: "destructive",
      });
    }
  };

  const handleConnectionSubmit = (data: ConnectionConfig) => {
    setConnectionConfig(data);
    resetForm();
  };

  const resetForm = () => {
    setColumns([]);
    setSelectedColumns([]);
    setJoinTables([]);
    setQueryResult(null);
    form.reset({
      delimiter: ',',
      useMultipleTables: false,
      mainTable: '',
    });
  };

  const selectAllColumns = () => {
    setSelectedColumns(columns.map(col => col.name));
  };

  const deselectAllColumns = () => {
    setSelectedColumns([]);
  };

  const toggleColumnSelection = (columnName: string) => {
    setSelectedColumns(prev => {
      if (prev.includes(columnName)) {
        return prev.filter(col => col !== columnName);
      } else {
        return [...prev, columnName];
      }
    });
  };

  const selectAllJoinTableColumns = (joinIndex: number) => {
    setJoinTables(prevJoinTables => {
      const updatedJoinTables = [...prevJoinTables];
      const currentJoinTable = updatedJoinTables[joinIndex];

      currentJoinTable.selectedColumns = currentJoinTable.columns.map(col => col.name);

      return updatedJoinTables;
    });
  };

  const deselectAllJoinTableColumns = (joinIndex: number) => {
    setJoinTables(prevJoinTables => {
      const updatedJoinTables = [...prevJoinTables];
      updatedJoinTables[joinIndex].selectedColumns = [];
      return updatedJoinTables;
    });
  };

  const toggleJoinTableColumnSelection = (joinIndex: number, columnName: string) => {
    setJoinTables(prevJoinTables => {
      const updatedJoinTables = [...prevJoinTables];
      const currentJoinTable = updatedJoinTables[joinIndex];

      if (currentJoinTable.selectedColumns.includes(columnName)) {
        currentJoinTable.selectedColumns = currentJoinTable.selectedColumns.filter(col => col !== columnName);
      } else {
        currentJoinTable.selectedColumns = [...currentJoinTable.selectedColumns, columnName];
      }

      return updatedJoinTables;
    });
  };

  const addJoinTable = () => {
    const newId = joinTables.length > 0
      ? Math.max(...joinTables.map(jt => jt.id)) + 1
      : 1;

    setJoinTables([
      ...joinTables,
      {
        id: newId,
        tableName: '',
        joinType: 'INNER JOIN',
        joinCondition: '',
        columns: [],
        selectedColumns: []
      }
    ]);
  };

  const removeJoinTable = (id: number) => {
    setJoinTables(joinTables.filter(jt => jt.id !== id));
  };

  const updateJoinTable = (id: number, field: string, value: string) => {
    setJoinTables(prevJoinTables => {
      const updatedJoinTables = [...prevJoinTables];
      const index = updatedJoinTables.findIndex(jt => jt.id === id);

      if (index !== -1) {
        updatedJoinTables[index] = {
          ...updatedJoinTables[index],
          [field]: value
        };

        // If the table name changed, fetch columns for the new table
        if (field === 'tableName' && value && connectionConfig) {
          fetchJoinTableColumns(value, index);
        }
      }

      return updatedJoinTables;
    });
  };

  const handlePreview = async () => {
    if (!connectionConfig || !mainTable) {
      toast({
        title: "Error",
        description: "Missing connection configuration or table selection",
        variant: "destructive",
      });
      return;
    }

    if (selectedColumns.length === 0) {
      toast({
        title: "Error",
        description: "Please select at least one column",
        variant: "destructive",
      });
      return;
    }

    try {
      setIsLoading(true);
      setError(null);

      // Prepare the joined tables data if using multiple tables
      let joinTablesData = undefined;
      if (useMultipleTables && joinTables.length > 0) {
        // Filter out any join table that doesn't have all the required fields
        joinTablesData = joinTables
          .filter(jt => jt.tableName && jt.joinCondition)
          .map(jt => ({
            tableName: jt.tableName,
            joinType: jt.joinType,
            joinCondition: jt.joinCondition,
          }));
      }

      // Get all selected columns (from main table and join tables)
      let allSelectedColumns = [...selectedColumns];
      if (useMultipleTables && joinTables.length > 0) {
        joinTables.forEach(jt => {
          if (jt.selectedColumns.length > 0) {
            // Prefix join table columns with table name to avoid ambiguity
            allSelectedColumns = [
              ...allSelectedColumns,
              ...jt.selectedColumns.map(col => `${jt.tableName}.${col}`)
            ];
          }
        });
      }

      const queryConfig: SelectedColumnsQueryConfig = {
        connection: connectionConfig,
        tableName: mainTable,
        columns: allSelectedColumns,
        delimiter: form.getValues().delimiter,
        joinTables: joinTablesData
      };

      const result = await queryWithSelectedColumns(queryConfig);
      setQueryResult(result);
      setPreviewDialogOpen(true);
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Failed to execute query');
      toast({
        title: "Query Failed",
        description: error instanceof Error ? error.message : 'Failed to execute query',
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  // Helper function to format bytes to human-readable format
  const formatBytes = (bytes: number | undefined, decimals = 1) => {
    if (!bytes) return '0 B';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
  };

  const [downloadedFileSize, setDownloadedFileSize] = useState<number | undefined>(undefined);
  const [downloadedFileName, setDownloadedFileName] = useState<string | undefined>(undefined);
  const [downloadProgress, setDownloadProgress] = useState<{ loaded: number; total: number; percentage: number } | undefined>(undefined);
  const [downloadedLineCount, setDownloadedLineCount] = useState<number | undefined>(undefined);

  const handleDownload = async () => {
    if (!connectionConfig || !mainTable) {
      toast({
        title: "Error",
        description: "Missing connection configuration or table selection",
        variant: "destructive",
      });
      return;
    }

    if (selectedColumns.length === 0) {
      toast({
        title: "Error",
        description: "Please select at least one column",
        variant: "destructive",
      });
      return;
    }

    try {
      setIsLoading(true);
      // Reset file size and progress if a new download is initiated
      setDownloadedFileSize(undefined);
      setDownloadProgress(undefined);
      setDownloadedLineCount(undefined);

      // Prepare the joined tables data if using multiple tables
      let joinTablesData = undefined;
      if (useMultipleTables && joinTables.length > 0) {
        // Filter out any join table that doesn't have all the required fields
        joinTablesData = joinTables
          .filter(jt => jt.tableName && jt.joinCondition)
          .map(jt => ({
            tableName: jt.tableName,
            joinType: jt.joinType,
            joinCondition: jt.joinCondition,
          }));
      }

      // Get all selected columns (from main table and join tables)
      let allSelectedColumns = [...selectedColumns];
      if (useMultipleTables && joinTables.length > 0) {
        joinTables.forEach(jt => {
          if (jt.selectedColumns.length > 0) {
            // Prefix join table columns with table name to avoid ambiguity
            allSelectedColumns = [
              ...allSelectedColumns,
              ...jt.selectedColumns.map(col => `${jt.tableName}.${col}`)
            ];
          }
        });
      }

      const downloadConfig: SelectedColumnsQueryConfig = {
        connection: connectionConfig,
        tableName: mainTable,
        columns: allSelectedColumns,
        delimiter: form.getValues().delimiter || ',',
        joinTables: joinTablesData
      };

      // Track download progress
      const result = await downloadData(
        downloadConfig,
        (progress) => {
          // Update progress state when progress callback is called
          setDownloadProgress(progress);
        },
        setDownloadedFileSize
      );

      // Set file size and name only after download completes
      setDownloadedFileSize(result.size);
      setDownloadedFileName(result.filename);
      setDownloadedLineCount(result.lines);

      toast({
        title: "Download Complete",
        description: `Successfully downloaded ${result.filename}`,
      });
    } catch (error) {
      toast({
        title: "Download Failed",
        description: error instanceof Error ? error.message : 'Failed to download data',
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
      // Clear progress when complete
      setDownloadProgress(undefined);
    }
  };

  if (!connectionConfig) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6 flex items-center">
          <Button variant="ghost" onClick={() => navigate('/')} className="mr-3 text-slate-600 hover:text-slate-900">
            <ArrowLeft className="h-4 w-4 mr-2" /> Back
          </Button>
          <h2 className="text-2xl font-bold text-slate-900">Download from ClickHouse</h2>
        </div>

        <ConnectionForm onSubmit={handleConnectionSubmit} title="Download from ClickHouse" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-6 flex items-center">
        <Button variant="ghost" onClick={() => setConnectionConfig(null)} className="mr-3 text-slate-600 hover:text-slate-900">
          <ArrowLeft className="h-4 w-4 mr-2" /> Back
        </Button>
        <h2 className="text-2xl font-bold text-slate-900">Download from ClickHouse</h2>
      </div>

      <Form {...form}>
        <div className="space-y-6">
          {/* Main Table Selection */}
          <Card className="shadow-md">
            <CardHeader>
              <CardTitle>Table Selection</CardTitle>
              <CardDescription>
                Select the main table from your database
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <FormField
                control={form.control}
                name="mainTable"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Main Table</FormLabel>
                    <div className="flex space-x-2">
                      <Select onValueChange={field.onChange} value={field.value}>
                        <FormControl>
                          <SelectTrigger className="w-[250px]">
                            <SelectValue placeholder="Select a table" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {isLoadingTables ? (
                            <div className="flex items-center justify-center p-2">
                              <Loader2 className="h-4 w-4 animate-spin mr-2" />
                              Loading tables...
                            </div>
                          ) : (
                            tables.map((table) => (
                              <SelectItem key={table} value={table}>
                                {table}
                              </SelectItem>
                            ))
                          )}
                        </SelectContent>
                      </Select>
                      <Button
                        type="button"
                        variant="outline"
                        size="icon"
                        onClick={fetchTables}
                        disabled={isLoadingTables || !connectionConfig}
                      >
                        <RefreshCw className={`h-4 w-4 ${isLoadingTables ? 'animate-spin' : ''}`} />
                      </Button>
                    </div>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </CardContent>
          </Card>

          {/* Columns Selection Area (Only visible after table is selected) */}
          {mainTable && (
            <Card className="shadow-md">
              <CardHeader>
                <div className="flex justify-between items-center">
                  <div>
                    <CardTitle>Column Selection</CardTitle>
                    <CardDescription>
                      Select columns from table "{mainTable}"
                    </CardDescription>
                  </div>
                  <div className="flex space-x-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={selectAllColumns}
                      disabled={isLoadingColumns || columns.length === 0}
                    >
                      Select All
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={deselectAllColumns}
                      disabled={isLoadingColumns || selectedColumns.length === 0}
                    >
                      Deselect All
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                {isLoadingColumns ? (
                  <div className="flex justify-center py-8">
                    <Loader2 className="h-6 w-6 animate-spin text-blue-600" />
                    <span className="ml-2">Loading columns...</span>
                  </div>
                ) : columns.length > 0 ? (
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3 max-h-60 overflow-y-auto p-3 border rounded-md">
                    {columns.map((column) => (
                      <div key={column.name} className="flex items-center space-x-2">
                        <Checkbox
                          id={`column-${column.name}`}
                          checked={selectedColumns.includes(column.name)}
                          onCheckedChange={() => toggleColumnSelection(column.name)}
                        />
                        <label
                          htmlFor={`column-${column.name}`}
                          className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 cursor-pointer"
                        >
                          {column.name}
                          <span className="ml-1 text-xs text-slate-500">({column.type})</span>
                        </label>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="text-center py-8 text-slate-500 border rounded-md">
                    No columns found in table
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Multiple Tables Option (Only visible after main table is selected) */}
          {mainTable && (
            <Card className="shadow-md">
              <CardHeader>
                <CardTitle>Join Options</CardTitle>
                <CardDescription>
                  Configure table joins if needed
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <FormField
                  control={form.control}
                  name="useMultipleTables"
                  render={({ field }) => (
                    <FormItem className="flex flex-row items-start space-x-3 space-y-0 rounded-md border p-4">
                      <FormControl>
                        <Checkbox
                          checked={field.value}
                          onCheckedChange={field.onChange}
                        />
                      </FormControl>
                      <div className="space-y-1 leading-none">
                        <FormLabel>Use Multiple Tables</FormLabel>
                        <FormDescription>
                          Join additional tables to your query
                        </FormDescription>
                      </div>
                    </FormItem>
                  )}
                />

                {/* Join Tables Configuration */}
                {useMultipleTables && (
                  <div className="space-y-4">
                    <div className="flex justify-end">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={addJoinTable}
                      >
                        <Plus className="h-4 w-4 mr-1" /> Add Join
                      </Button>
                    </div>

                    {joinTables.length > 0 ? (
                      <div className="space-y-4">
                        {joinTables.map((joinTable, index) => (
                          <Card key={joinTable.id} className="shadow-sm border">
                            <CardHeader className="pb-2 pt-3 px-4">
                              <div className="flex justify-between items-center">
                                <CardTitle className="text-md">Join Configuration</CardTitle>
                                <Button
                                  type="button"
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => removeJoinTable(joinTable.id)}
                                  className="h-7 w-7 p-0"
                                >
                                  <X className="h-4 w-4" />
                                </Button>
                              </div>
                            </CardHeader>
                            <CardContent className="px-4 py-3 space-y-4">
                              {/* Join Configuration as a single line */}
                              <div className="flex flex-wrap items-center gap-2 bg-slate-50 p-3 rounded-md border">
                                <Select
                                  value={joinTable.joinType}
                                  onValueChange={(value) =>
                                    updateJoinTable(joinTable.id, 'joinType', value as any)
                                  }
                                >
                                  <SelectTrigger className="w-[140px] h-8">
                                    <SelectValue placeholder="Join type" />
                                  </SelectTrigger>
                                  <SelectContent>
                                    <SelectItem value="INNER JOIN">Inner Join</SelectItem>
                                    <SelectItem value="LEFT JOIN">Left Join</SelectItem>
                                    <SelectItem value="RIGHT JOIN">Right Join</SelectItem>
                                    <SelectItem value="OUTER JOIN">Full Outer Join</SelectItem>
                                    <SelectItem value="SEMI JOIN">Semi Join</SelectItem>
                                    <SelectItem value="ANTI JOIN">Anti Join</SelectItem>
                                    <SelectItem value="ANY JOIN">Any Join</SelectItem>
                                    <SelectItem value="GLOBAL JOIN">Global Join</SelectItem>
                                    <SelectItem value="ARRAY JOIN">Array Join</SelectItem>
                                  </SelectContent>
                                </Select>
                                <Select
                                  value={joinTable.tableName}
                                  onValueChange={(value) =>
                                    updateJoinTable(joinTable.id, 'tableName', value)
                                  }
                                >
                                  <SelectTrigger className="w-[140px] h-8">
                                    <SelectValue placeholder="Join table" />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {tables.filter(t => t !== mainTable).map((table) => (
                                      <SelectItem key={table} value={table}>
                                        {table}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                                <span className="text-sm flex items-center">ON</span>
                                <Textarea
                                  spellCheck={false}
                                  autoCorrect="off"
                                  autoComplete="off"
                                  placeholder="Enter ON condition"
                                  value={joinTable.joinCondition}
                                  onChange={(e) => updateJoinTable(joinTable.id, 'joinCondition', e.target.value)}
                                  className="min-h-8 h-auto max-h-64 min-w-[250px] w-[500px] px-2 py-1 resize text-sm overflow-auto font-semibold"
                                  rows={1}
                                />
                              </div>

                              {/* Join Table Columns Selection */}
                              {joinTable.tableName && joinTable.columns.length > 0 && (
                                <div>
                                  <div className="flex justify-between items-center mb-2">
                                    <h5 className="text-sm font-medium">Select Columns from {joinTable.tableName}</h5>
                                    <div className="flex space-x-2">
                                      <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        className="h-7 text-xs"
                                        onClick={() => selectAllJoinTableColumns(index)}
                                      >
                                        Select All
                                      </Button>
                                      <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        className="h-7 text-xs"
                                        onClick={() => deselectAllJoinTableColumns(index)}
                                        disabled={joinTable.selectedColumns.length === 0}
                                      >
                                        Deselect All
                                      </Button>
                                    </div>
                                  </div>
                                  <div className="grid grid-cols-1 md:grid-cols-3 gap-2 max-h-40 overflow-y-auto p-2 border rounded-md">
                                    {joinTable.columns.map((column) => (
                                      <div key={column.name} className="flex items-center space-x-2">
                                        <Checkbox
                                          id={`join-${joinTable.id}-column-${column.name}`}
                                          checked={joinTable.selectedColumns.includes(column.name)}
                                          onCheckedChange={() => toggleJoinTableColumnSelection(index, column.name)}
                                        />
                                        <label
                                          htmlFor={`join-${joinTable.id}-column-${column.name}`}
                                          className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 cursor-pointer"
                                        >
                                          {column.name}
                                          <span className="ml-1 text-xs text-slate-500">({column.type})</span>
                                        </label>
                                      </div>
                                    ))}
                                  </div>
                                </div>
                              )}
                            </CardContent>
                          </Card>
                        ))}
                      </div>
                    ) : (
                      <div className="text-center py-6 border rounded-md text-slate-500">
                        No joins configured. Click "Add Join" to join another table.
                      </div>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          )}

          {/* Delimiter Setting */}
          {mainTable && (
            <Card className="shadow-md">
              <CardHeader>
                <CardTitle>Download Options</CardTitle>
                <CardDescription>
                  Configure additional download settings
                </CardDescription>
              </CardHeader>
              <CardContent>
                <FormField
                  control={form.control}
                  name="delimiter"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Delimiter</FormLabel>
                      <FormControl>
                        <Input
                          placeholder=","
                          maxLength={3}
                          className="w-16"
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>
                        Character used to separate values in the downloaded CSV file
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </CardContent>
              <CardFooter className="flex justify-between border-t px-6 py-4">
                {!isLoading &&
                  (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={handlePreview}
                      disabled={!mainTable || selectedColumns.length === 0}
                      className="bg-white border border-slate-200 hover:bg-slate-100 text-slate-900"
                    >
                      <Eye className="h-4 w-4 mr-2" />
                      <span>Preview Results</span>
                    </Button>
                  )}

                <div className="w-full space-y-1">
                  <ProgressButton
                    type="button"
                    onClick={handleDownload}
                    isLoading={isLoading}
                    disabled={isLoading || !mainTable || selectedColumns.length === 0}
                    loadingText={downloadProgress === downloadedFileSize ? "Processing..." : "Downloading..."}
                    progressDuration={4000}
                    icon={<Download className="h-4 w-4" />}
                    fileSize={downloadedFileSize}
                    showSize={true}
                    actualProgress={downloadProgress}
                    lineCount={downloadedLineCount}
                  >
                    Download Results
                  </ProgressButton>

                  {downloadedFileName && downloadedFileSize && !isLoading && (
                    <div className="flex justify-end items-center text-xs text-slate-500">
                      <span>Last download: {downloadedFileName} ({formatBytes(downloadedFileSize)})</span>
                    </div>
                  )}
                </div>
              </CardFooter>
            </Card>
          )}

          {/* Error Display */}
          {error && (
            <Alert className="bg-red-50 border-red-200">
              <AlertDescription className="text-red-800">
                {error}
              </AlertDescription>
            </Alert>
          )}

          {/* Results Preview Dialog */}
          <Dialog open={previewDialogOpen} onOpenChange={setPreviewDialogOpen}>
            <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>Query Results Preview</DialogTitle>
                <DialogDescription>
                  Showing preview of query results
                </DialogDescription>
              </DialogHeader>

              {queryResult && queryResult.headers.length > 0 && (
                <div className="border rounded-lg overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow className="bg-slate-50">
                        {queryResult.headers.map((header, index) => (
                          <TableHead key={index}>{header}</TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {queryResult.rows.map((row, rowIndex) => (
                        <TableRow key={rowIndex}>
                          {row.map((cell: any, cellIndex: number) => (
                            <TableCell key={cellIndex}>{String(cell)}</TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}

              <DialogFooter className="mt-4">
                <div className="w-full flex justify-between items-center">
                  <span className="text-sm text-slate-600">
                    Showing {queryResult?.rows.length || 0} rows
                  </span>
                  <ProgressButton
                    onClick={handleDownload}
                    isLoading={isLoading}
                    disabled={isLoading}
                    loadingText="Downloading..."
                    progressDuration={4000}
                    icon={<Download className="h-4 w-4" />}
                    fileSize={downloadedFileSize}
                    showSize={downloadedFileSize !== undefined}
                    actualProgress={downloadProgress}
                  >
                    Download All Results
                  </ProgressButton>
                </div>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>
      </Form>
    </div>
  );
}
