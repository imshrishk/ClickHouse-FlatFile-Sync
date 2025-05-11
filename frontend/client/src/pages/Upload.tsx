import { useState, useEffect, useRef } from 'react';
import { useLocation } from 'wouter';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Upload as UploadIcon, PlusCircle, Loader2, Eye, RefreshCw, X, ArrowLeftRight } from 'lucide-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage, FormDescription } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter
} from '@/components/ui/dialog';
import {
  ConnectionConfig,
  uploadSchema,
  TablesConfig
} from '@shared/schema';
import { cn } from '@/lib/utils';
import ConnectionForm from '@/components/ConnectionForm';
import ProgressButton from '@/components/ProgressButton';
import {
  testConnection,
  uploadFile,
  getTables,
  previewCsvFile,
  getTypes,
  getColumns,
  queryWithSelectedColumns,
  serverCheck,
  type ColumnInfo
} from '@/lib/clickhouse';
import { useToast } from '@/hooks/use-toast';
import * as z from 'zod';

// Define form schema
const formSchema = z.object({
  tableName: z.string().min(1, { message: "Enter tablename" }),
  createNewTable: z.boolean().default(false),
  delimiter: z.string().default(','),
  hasHeader: z.boolean().default(true),
  streamingMode: z.boolean().default(false),
  file: z
    .instanceof(File, { message: "File is required" })
    .refine((file) => file.size <= 10 * 1024 * 1024 * 1024, {
      message: "File size must be less than 10GB",
    })
    .refine((file) => {
      const acceptedTypes = [
        "text/csv",
        "application/vnd.ms-excel",
        "text/plain"
      ];
      return acceptedTypes.includes(file.type) || file.name.endsWith('.csv');
    }, {
      message: "File must be a CSV",
    }),
});

// Type for the form values
type FormValues = z.infer<typeof formSchema>;

export default function UploadPage() {
  const [location, navigate] = useLocation();
  const [connectionConfig, setConnectionConfig] = useState<ConnectionConfig | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isLoadingTables, setIsLoadingTables] = useState(false);
  const [isPreviewingCsv, setIsPreviewingCsv] = useState(false);
  const [isLoadingTypes, setIsLoadingTypes] = useState(false);
  const [csvPreview, setCsvPreview] = useState<{ headers: string[]; rows: any[] } | null>(null);
  const [previewDialogOpen, setPreviewDialogOpen] = useState(false);
  const [tables, setTables] = useState<string[]>([]);
  const [dataTypes, setDataTypes] = useState<string[]>([]);
  const [selectedColumnTypes, setSelectedColumnTypes] = useState<Record<string, string>>({});
  const [selectedColumns, setSelectedColumns] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState<{ loaded: number; total: number; percentage: number } | undefined>(undefined);
  const [uploadedLineCount, setUploadedLineCount] = useState<number | undefined>(undefined);
  const [fileSize, setFileSize] = useState<string | null>(null);
  const [isLoadingColumns, setIsLoadingColumns] = useState(false);
  const [tableColumns, setTableColumns] = useState<ColumnInfo[]>([]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();

  // Form setup
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      tableName: '',
      createNewTable: false,
      delimiter: ',',
      hasHeader: true,
      streamingMode: false,
    },
  });

  // Watch if it's a new table
  const createNewTable = form.watch('createNewTable');
  const selectedFile = form.watch('file');
  const delimiter = form.watch('delimiter');
  const streamingMode = form.watch('streamingMode');
  const hasHeader = form.watch('hasHeader');

  // Fetch tables when connection is established
  useEffect(() => {
    if (connectionConfig) {
      fetchTables();
      fetchDataTypes();
    }
  }, [connectionConfig]);

  // Update useEffect for file changes to handle the conflict with table selection
  useEffect(() => {
    if (selectedFile && connectionConfig) {
      // Reset table selection if we're uploading a new file
      if (!createNewTable) {
        form.setValue('createNewTable', true);
        setTableColumns([]);
      }
      handlePreviewCsv(selectedFile, delimiter);
    }
  }, [selectedFile, delimiter]);

  // Add a new effect to handle table selection changes
  useEffect(() => {
    const tableName = form.getValues().tableName;
    if (tableName && !createNewTable && connectionConfig) {
      fetchTableColumns(tableName);
    }
  }, [form.watch('tableName'), createNewTable]);

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

  // Function to fetch data types
  const fetchDataTypes = async () => {
    if (!connectionConfig) return;

    try {
      setIsLoadingTypes(true);
      setError(null);
      const types = await getTypes({ connection: connectionConfig });
      setDataTypes(types);
    } catch (error) {
      setError(error instanceof Error ? error.message : "Failed to fetch data types");
      toast({
        title: "Error Fetching Data Types",
        description: error instanceof Error ? error.message : "Failed to fetch data types",
        variant: "destructive",
      });
    } finally {
      setIsLoadingTypes(false);
    }
  };

  // Add new function to fetch table preview data
  const fetchTablePreview = async (tableName: string, tableColumns: ColumnInfo[]) => {
    if (!connectionConfig || !tableName || tableColumns.length === 0) return;

    try {
      setIsPreviewingCsv(true);
      setError(null);
      
      // Create the list of column names to query
      const columnNames = tableColumns.map(col => col.name);
      
      console.log(`Fetching preview for table: ${tableName} with ${columnNames.length} columns`);
      
      // Use queryWithSelectedColumns to get actual data from the table with a limit of 100 rows
      try {
        const result = await queryWithSelectedColumns({
          connection: connectionConfig,
          tableName,
          columns: columnNames,
          delimiter: delimiter || ',',
          limit: 100 // Limit to 100 rows for preview
        });
        
        console.log(`Received preview with ${result.rows.length} rows`);
        setCsvPreview(result);
        
        // If we got any rows, show a success message
        if (result.rows.length > 0) {
          toast({
            title: "Preview Loaded",
            description: `Showing ${result.rows.length} rows from table ${tableName}`,
            variant: "default",
          });
        } else {
          toast({
            title: "Empty Preview",
            description: "The table appears to be empty",
            variant: "default",
          });
        }
      } catch (error) {
        console.error("Error fetching table preview:", error);
        let errorMessage = "Failed to fetch preview data";
        
        if (error instanceof Error) {
          errorMessage = error.message;
        }
        
        // Set error and show toast
        setError(`Preview Failed: ${errorMessage}`);
        toast({
          title: "Preview Failed",
          description: errorMessage,
          variant: "destructive",
        });
      }
    } catch (error) {
      console.error("Error in fetchTablePreview:", error);
      let errorMessage = "An unexpected error occurred";
      
      if (error instanceof Error) {
        errorMessage = error.message;
      }
      
      setError(`Preview Failed: ${errorMessage}`);
    } finally {
      setIsPreviewingCsv(false);
    }
  };

  // Modify fetchTableColumns to handle both column fetching and preview
  const fetchTableColumns = async (tableName: string) => {
    if (!connectionConfig || !tableName) return;

    try {
      setIsLoadingColumns(true);
      setError(null);
      
      // Clear any file selection when selecting a table
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      // Use null to reset the file state
      form.setValue('file', null as unknown as File);
      setCsvPreview(null);
      
      const columnsData = await getColumns({
        connection: connectionConfig,
        tableName
      });
      
      setTableColumns(columnsData);
      
      // After getting columns, fetch a preview of the table data
      if (columnsData.length > 0) {
        await fetchTablePreview(tableName, columnsData);
      }
      
      return columnsData;
    } catch (error) {
      console.error('Error fetching table columns:', error);
      setError(error instanceof Error ? error.message : 'Failed to fetch table columns');
      toast({
        title: "Error",
        description: error instanceof Error ? error.message : 'Failed to fetch table columns',
        variant: "destructive",
      });
      return [];
    } finally {
      setIsLoadingColumns(false);
    }
  };

  const handleConnectionSubmit = (data: ConnectionConfig) => {
    setConnectionConfig(data);
    resetForm();
  };

  const resetForm = () => {
    setCsvPreview(null);
    setSelectedColumnTypes({});
    setSelectedColumns([]);
    form.reset({
      tableName: '',
      createNewTable: false,
      delimiter: ',',
      hasHeader: true,
      streamingMode: false,
    });

    // Clear file input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handlePreviewCsv = async (csvFile: File, delimiter: string) => {
    if (!csvFile) return;

    try {
      setIsPreviewingCsv(true);
      setError(null);
      
      console.log(`Previewing CSV file: ${csvFile.name}, size: ${csvFile.size} bytes`);
      
      // Show toast for large file warning
      if (csvFile.size > 50 * 1024 * 1024) { // > 50MB
        toast({
          title: "Large File Detected",
          description: "The file is large, preview might take longer than usual or fail. You can still upload if preview fails.",
          variant: "default",
        });
      }
      
      // Convert 'tab' to '\t' for backend processing
      const actualDelimiter = delimiter === 'tab' ? '\t' : delimiter;
      console.log(`Using delimiter: '${actualDelimiter === '\t' ? '\\t (tab)' : actualDelimiter}'`);

      try {
        // Pass the hasHeader parameter to the preview function
        // @ts-ignore - temporarily ignore type errors until we update the interface
        const fileData = await previewCsvFile(csvFile, actualDelimiter, hasHeader);
        console.log(`Preview successful, received ${fileData.headers.length} headers and ${fileData.rows.length} rows`);
        
        setCsvPreview(fileData);
        
        // Automatically select all columns
        setSelectedColumns(fileData.headers);
        
        // If there are headers, get data types from the ClickHouse service
        if (fileData.headers.length > 0 && connectionConfig) {
          try {
            const types = await getTypes({ connection: connectionConfig });
            
            // Default all to String if can't determine
            const columnTypes: Record<string, string> = {};
            fileData.headers.forEach(header => {
              columnTypes[header] = 'String';
            });
            
            setSelectedColumnTypes(columnTypes);
          } catch (typesError) {
            console.error('Failed to fetch column types:', typesError);
            // Create default column types
            const columnTypes: Record<string, string> = {};
            fileData.headers.forEach(header => {
              columnTypes[header] = 'String';
            });
            setSelectedColumnTypes(columnTypes);
          }
        }
        
        // Show success toast
        toast({
          title: "Preview Generated",
          description: `Showing ${fileData.rows.length} rows from the file`,
          variant: "default",
        });
      } catch (previewError) {
        console.error('CSV preview error:', previewError);
        
        // Extract meaningful error message
        const errorMessage = previewError instanceof Error 
          ? previewError.message 
          : "Failed to generate preview";
        
        setError(`CSV Preview Failed: ${errorMessage}`);
        
        toast({
          title: "Preview Failed",
          description: errorMessage,
          variant: "destructive",
        });
        
        // Extract headers from file as fallback
        extractHeadersFromFile(csvFile, actualDelimiter);
      }
    } finally {
      setIsPreviewingCsv(false);
    }
  };

  // Add helper function to extract headers directly from file
  const extractHeadersFromFile = (csvFile: File, delimiter: string) => {
    try {
      const reader = new FileReader();
      reader.onload = (e) => {
        const text = e.target?.result as string;
        if (text) {
          // Handle tab delimiter properly
          const actualDelimiter = delimiter === 'tab' ? '\t' : delimiter;
          
          const lines = text.split(/\r?\n/);
          const firstLine = lines[0];
          
          if (firstLine) {
            const headers = firstLine.split(actualDelimiter).map(h => h.trim());
            
            // Extract data rows from the file content
            const dataRows = [];
            // Increase to display up to 100 rows (but still starting from index 1 to skip header)
            const maxRows = hasHeader ? 101 : 100; // If no header, we can show all rows
            const startIndex = hasHeader ? 1 : 0; // Start from first line if not using header
            
            for (let i = startIndex; i < Math.min(lines.length, maxRows); i++) {
              if (lines[i] && lines[i].trim()) {
                // Handle tab-delimited files with quotes more carefully
                if (actualDelimiter === '\t') {
                  try {
                    // Parse line with proper quote handling
                    let row = [];
                    let currentField = '';
                    let inQuotes = false;
                    
                    for (let j = 0; j < lines[i].length; j++) {
                      const char = lines[i][j];
                      
                      if (char === '"') {
                        inQuotes = !inQuotes;
                        currentField += char; // Keep quotes for tab-delimited files
                      } else if (char === actualDelimiter && !inQuotes) {
                        row.push(currentField.trim());
                        currentField = '';
                      } else {
                        currentField += char;
                      }
                    }
                    
                    // Add the last field
                    row.push(currentField.trim());
                    dataRows.push(row);
                  } catch (e) {
                    // Fallback to simple split
                    dataRows.push(lines[i].split(actualDelimiter));
                  }
                } else {
                  dataRows.push(lines[i].split(actualDelimiter).map(cell => cell.trim()));
                }
              }
            }
            
            // Generate headers if not using first row as header
            const finalHeaders = hasHeader ? headers : headers.map((_, index) => `Column${index + 1}`);
            
            // Create a preview structure with headers and available data
            setCsvPreview({
              headers: finalHeaders,
              rows: dataRows.length > 0 ? dataRows : [finalHeaders.map(() => "(Preview not available - using file headers)")]
            });
            
            // Initialize columns and types
            setSelectedColumns(finalHeaders);
            const types: Record<string, string> = {};
            finalHeaders.forEach(h => types[h] = 'String');
            setSelectedColumnTypes(types);
            
            toast({
              title: "Headers Extracted",
              description: `${hasHeader ? 'Using headers from file.' : 'Generated column names.'} ${dataRows.length > 0 ? `Showing ${dataRows.length} rows.` : 'Full preview not available.'}`,
              variant: "default",
            });
          }
        }
      };
      reader.onerror = (error) => {
        console.error("Error reading file:", error);
      };
      reader.readAsText(csvFile.slice(0, 500000)); // Read the first 500KB to get header and more data rows (up to 100)
    } catch (headerError) {
      console.error("Failed to extract header:", headerError);
    }
  };

  const handleUpload = async (formValues: FormValues) => {
    if (!connectionConfig) {
      toast({
        title: "Error",
        description: "Missing connection configuration",
        variant: "destructive",
      });
      return;
    }

    try {
      setIsUploading(true);
      setError(null);
      // Reset upload progress
      setUploadProgress(undefined);
      setUploadedLineCount(undefined)

      // For existing tables, make sure we have selected columns
      if (!formValues.createNewTable && selectedColumns.length === 0) {
        throw new Error("Please select at least one column to upload");
      }

      // Filtering only selected columns
      const filteredTypes: Record<string, string> = Object.fromEntries(
        Object.entries(selectedColumnTypes).filter(([key]) => selectedColumns.includes(key))
      );
      
      // Handle tab delimiter correctly for upload
      const actualDelimiter = formValues.delimiter === 'tab' ? '\t' : formValues.delimiter;

      // Prepare upload configuration
      const uploadConfig = {
        // Only include totalCols if we have selected columns
        totalCols: selectedColumns.length > 0 ? selectedColumns.length : undefined,
        connection: connectionConfig,
        tableName: formValues.tableName,
        createNewTable: formValues.createNewTable,
        hasHeader: formValues.hasHeader,
        delimiter: actualDelimiter,
        // Only include columnTypes if we have selected columns
        columnTypes: Object.keys(filteredTypes).length > 0 ? filteredTypes : undefined
      };

      // Call uploadFile with progress tracking
      const result = await uploadFile(
        uploadConfig,
        formValues.file,
        (progress) => {
          // Update progress state when progress callback is called
          setUploadProgress(progress);
        }
      );

      if (result.success) {
        // Set the line count based on the lines in result
        if (result.lines)
          setUploadedLineCount(result.lines);

        toast({
          title: "Upload Successful",
          description: result.message || "Data uploaded successfully",
        });
      } else {
        throw new Error(result.message || "Upload failed");
      }
    } catch (error) {
      setError(error instanceof Error ? error.message : "Failed to upload file");
      toast({
        title: "Upload Failed",
        description: error instanceof Error ? error.message : "Failed to upload file",
        variant: "destructive",
      });
    } finally {
      setIsUploading(false);
      // Clear progress when complete
      setUploadProgress(undefined);
    }
  };

  const selectAllColumns = () => {
    if (csvPreview) {
      setSelectedColumns([...csvPreview.headers]);
    }
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

  const updateColumnType = (columnName: string, type: string) => {
    setSelectedColumnTypes(prev => ({
      ...prev,
      [columnName]: type
    }));
  };

  const showPreviewDialog = () => {
    setPreviewDialogOpen(true);
  };

  const updateFileSize = (file: File | null) => {
    if (!file) {
      setFileSize(null);
      return;
    }
    
    const bytes = file.size;
    if (bytes === 0) {
      setFileSize('0 Bytes');
      return;
    }
    
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    setFileSize(parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]);
    
    // Auto-enable streaming mode for files > 1GB
    if (bytes > 1024 * 1024 * 1024) {
      form.setValue('streamingMode', true);
    }
  };

  const handleTestConnection = async () => {
    try {
      toast({
        title: "Testing Backend Connection",
        description: "Checking connectivity to backend server...",
      });
      
      const serverStatus = await serverCheck(true); // Pass true to bypass errors
      
      if (serverStatus.status === 'online') {
        toast({
          title: "Server Online",
          description: "Backend server is accessible",
          variant: "default",
        });
        console.log("Server check details:", serverStatus.details);
      } else if (serverStatus.status === 'partial') {
        toast({
          title: "Partial Connectivity",
          description: "Some backend endpoints are not responding correctly, but uploads may still work.",
          variant: "default",
        });
        console.warn("Server check details:", serverStatus.details);
      } else {
        toast({
          title: "Connection Warning",
          description: "Backend server connectivity is limited, but uploads may still work.",
          variant: "default",
        });
        console.error("Server check details:", serverStatus.details);
      }
    } catch (error) {
      console.error("Error testing connection:", error);
      toast({
        title: "Connection Test Error",
        description: error instanceof Error ? error.message : "Unknown error testing connection",
        variant: "destructive",
      });
    }
  };

  if (!connectionConfig) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6 flex items-center">
          <Button variant="ghost" onClick={() => navigate('/')} className="mr-3 text-slate-600 hover:text-slate-900">
            <ArrowLeft className="h-4 w-4 mr-2" /> Back
          </Button>
          <h2 className="text-2xl font-bold text-foreground">Upload to ClickHouse</h2>
        </div>

        <ConnectionForm onSubmit={handleConnectionSubmit} title="Upload to ClickHouse" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-6 flex items-center">
        <Button variant="ghost" onClick={() => setConnectionConfig(null)} className="mr-3 text-slate-600 hover:text-slate-900">
          <ArrowLeft className="h-4 w-4 mr-2" /> Back
        </Button>
        <h2 className="text-2xl font-bold text-foreground">Upload to ClickHouse</h2>
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(handleUpload)} className="space-y-6">
          {/* Table Selection and File Upload */}
          <Card className="shadow-md">
            <CardHeader>
              <CardTitle>Upload Configuration</CardTitle>
              <CardDescription>
                Select a table and upload a CSV file
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              {/* File Upload Area */}
              <FormField
                control={form.control}
                name="file"
                render={({ field: { onChange, value, ...rest } }) => (
                  <FormItem>
                    <FormLabel>CSV File</FormLabel>
                    <div className="border border-dashed border-gray-300 rounded-md px-6 py-8 flex flex-col items-center">
                      <div className="mb-4 text-center">
                        <div className="flex justify-center mb-2">
                          <UploadIcon className="h-10 w-10 text-blue-500" />
                        </div>
                        <h3 className="text-sm font-medium text-gray-900">
                          {value ? value.name : 'Drag and drop file here, or click to select'}
                        </h3>
                        <p className="text-xs text-gray-500">
                          CSV files only, maximum 10GB
                        </p>
                        {fileSize && (
                          <p className="text-xs font-medium mt-1">
                            File size: {fileSize}
                          </p>
                        )}
                      </div>

                      <input
                        type="file"
                        ref={fileInputRef}
                        accept=".csv,text/csv,application/vnd.ms-excel,text/plain"
                        className="block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100"
                        onChange={(e) => {
                          const file = e.target.files?.[0];
                          if (file) {
                            onChange(file);
                            updateFileSize(file);
                          }
                        }}
                      />
                    </div>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Create New Table Option - Moved up as requested */}
              <FormField
                control={form.control}
                name="createNewTable"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-start space-x-3 space-y-0 rounded-md border p-4">
                    <FormControl>
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                    <div className="space-y-1 leading-none">
                      <FormLabel>Create New Table</FormLabel>
                      <FormDescription>
                        Create a new table instead of uploading to an existing one.
                        {field.value && 
                          " Column names will be automatically detected from your CSV file."}
                      </FormDescription>
                    </div>
                  </FormItem>
                )}
              />

              {/* Add Streaming Mode Option */}
              <FormField
                control={form.control}
                name="streamingMode"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-start space-x-3 space-y-0">
                    <FormControl>
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                    <div className="space-y-1 leading-none">
                      <FormLabel>
                        Use streaming mode for large files
                      </FormLabel>
                      <FormDescription>
                        Recommended for files over 1GB. Helps prevent timeouts and memory issues.
                      </FormDescription>
                    </div>
                  </FormItem>
                )}
              />

              {/* Add hasHeader Option */}
              <FormField
                control={form.control}
                name="hasHeader"
                render={({ field }) => (
                  <FormItem className="flex flex-row items-start space-x-3 space-y-0 rounded-md border p-4">
                    <FormControl>
                      <Checkbox
                        checked={field.value}
                        onCheckedChange={field.onChange}
                      />
                    </FormControl>
                    <div className="space-y-1 leading-none">
                      <FormLabel>Use First Row As Header</FormLabel>
                      <FormDescription>
                        If unchecked, the first row will be treated as data and column names will be auto-generated
                      </FormDescription>
                    </div>
                  </FormItem>
                )}
              />

              <div className="grid md:grid-cols-2 gap-4">
                {/* Table Name Selection */}
                <FormField
                  control={form.control}
                  name="tableName"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Table Name</FormLabel>
                      {createNewTable ? (
                        <FormControl>
                          <Input placeholder="Enter new table name" {...field} />
                        </FormControl>
                      ) : (
                        <div className="flex space-x-2">
                          <Select 
                            onValueChange={(value) => {
                              field.onChange(value);
                              // The useEffect will handle fetching columns
                            }} 
                            value={field.value}
                          >
                            <FormControl>
                              <SelectTrigger>
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
                      )}
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {/* Delimiter Option */}
                <FormField
                  control={form.control}
                  name="delimiter"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Delimiter</FormLabel>
                      <Select 
                        onValueChange={field.onChange} 
                        value={field.value}
                      >
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder="Select delimiter" />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value=",">Comma (,)</SelectItem>
                          <SelectItem value="tab">Tab</SelectItem>
                          <SelectItem value=";">Semicolon (;)</SelectItem>
                          <SelectItem value="|">Pipe (|)</SelectItem>
                          <SelectItem value=" ">Space</SelectItem>
                        </SelectContent>
                      </Select>
                      <FormDescription>
                        Character used as a delimiter in your CSV file
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>


            </CardContent>
          </Card>

          {/* CSV Preview and Column Selection - Only shown after a file is selected */}
          {csvPreview && (
            <Card className="shadow-md">
              <CardHeader>
                <div className="flex justify-between items-center">
                  <div>
                    <CardTitle>Column Configuration</CardTitle>
                    <CardDescription>
                      {createNewTable ? 
                        "Select columns to upload and specify data types for each column" :
                        "These are the columns from the selected table"}
                    </CardDescription>
                  </div>
                  <div className="flex space-x-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={selectAllColumns}
                      disabled={!csvPreview || csvPreview.headers.length === 0}
                    >
                      Select All
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={deselectAllColumns}
                      disabled={!csvPreview || selectedColumns.length === 0}
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
                    <span className="ml-2">Loading table columns...</span>
                  </div>
                ) : (
                  <div className="overflow-x-auto border rounded-md">
                    <Table>
                      <TableHeader>
                        <TableRow className="bg-slate-50">
                          <TableHead className="w-10">
                            Include
                          </TableHead>
                          <TableHead>
                            Column Name
                          </TableHead>
                          <TableHead>
                            {createNewTable ? "Data Type" : "Type (From Table)"}
                          </TableHead>
                          <TableHead>
                            Sample Data
                          </TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {csvPreview.headers.map((header, index) => (
                          <TableRow key={header}>
                            <TableCell>
                              <Checkbox
                                checked={selectedColumns.includes(header)}
                                onCheckedChange={() => toggleColumnSelection(header)}
                              />
                            </TableCell>
                            <TableCell className="font-medium">
                              {header}
                            </TableCell>
                            <TableCell>
                              {createNewTable ? (
                                <Select
                                  value={selectedColumnTypes[header] || 'String'}
                                  onValueChange={(value) => updateColumnType(header, value)}
                                  disabled={!selectedColumns.includes(header)}
                                >
                                  <SelectTrigger className="w-[180px]">
                                    <SelectValue />
                                  </SelectTrigger>
                                  <SelectContent>
                                    {isLoadingTypes ? (
                                      <div className="flex items-center justify-center p-2">
                                        <Loader2 className="h-4 w-4 animate-spin mr-2" />
                                        Loading types...
                                      </div>
                                    ) : (
                                      dataTypes.map((type) => (
                                        <SelectItem key={type} value={type}>
                                          {type}
                                        </SelectItem>
                                      ))
                                    )}
                                  </SelectContent>
                                </Select>
                              ) : (
                                <span className="text-slate-500">Auto-detected</span>
                              )}
                            </TableCell>
                            <TableCell className="max-w-[200px] truncate">
                              {csvPreview.rows.length > 0 && csvPreview.rows[0][index] !== undefined
                                ? String(csvPreview.rows[0][index])
                                : ''}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )}
              </CardContent>

              <CardFooter className="flex justify-between border-t px-6 py-4">
                {!isUploading && (
                  <>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={showPreviewDialog}
                      disabled={isUploading}
                      className="bg-white border border-slate-200 hover:bg-slate-100 text-slate-900"
                    >
                      <Eye className="h-4 w-4" />
                      <span>Preview Data</span>
                    </Button>

                    <Button
                      type="button"
                      variant="outline"
                      onClick={handleTestConnection}
                      disabled={isUploading}
                      className="bg-white border border-slate-200 hover:bg-slate-100 text-slate-900 ml-2"
                    >
                      <RefreshCw className="h-4 w-4 mr-1" />
                      <span>Test Server</span>
                    </Button>
                  </>
                )}

                <div className="w-full space-y-1">
                  <ProgressButton
                    type="submit"
                    isLoading={isUploading}
                    disabled={isUploading || selectedColumns.length === 0}
                    loadingText={uploadProgress ?
                      (uploadProgress.loaded < uploadProgress.total ? "Uploading..." : "Processing...")
                      : "Uploading..."}
                    progressDuration={3000}
                    icon={<UploadIcon className="h-4 w-4" />}
                    fileSize={form.getValues().file?.size}
                    showSize={true}
                    actualProgress={uploadProgress}
                    lineCount={uploadedLineCount}
                  >
                    Upload to ClickHouse
                  </ProgressButton>
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

          {/* Submit Button -- Just for Show */}
          {!csvPreview && (
            <div className="flex justify-end">
              <Button
                type="submit"
                disabled={true}
                className="bg-primary text-white hover:bg-primary/90 flex items-center gap-2"
              >
                <UploadIcon className="h-4 w-4" />
                Upload to ClickHouse
              </Button>
            </div>
          )}
        </form>
      </Form>

      {/* CSV Preview Dialog */}
      <Dialog open={previewDialogOpen} onOpenChange={setPreviewDialogOpen}>
        <DialogContent className="max-w-4xl max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>CSV Preview</DialogTitle>
            <DialogDescription>
              Preview of the CSV data to be uploaded
            </DialogDescription>
          </DialogHeader>

          {csvPreview ? (
            <div className="border rounded-lg overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow className="bg-slate-50">
                    {csvPreview.headers.map((header, index) => (
                      <TableHead
                        key={index}
                        className={cn({
                          "bg-blue-50": selectedColumns.includes(header),
                          "opacity-50": !selectedColumns.includes(header)
                        })}
                      >
                        {header}
                        {!selectedColumns.includes(header) && (
                          <div className="text-xs text-red-500">(excluded)</div>
                        )}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {csvPreview.rows.length > 0 ? (
                    csvPreview.rows.map((row, rowIndex) => (
                      <TableRow key={rowIndex}>
                        {row.map((cell: any, cellIndex: number) => (
                          <TableCell
                            key={cellIndex}
                            className={cn({
                              "bg-blue-50": selectedColumns.includes(csvPreview.headers[cellIndex]),
                              "opacity-50": !selectedColumns.includes(csvPreview.headers[cellIndex])
                            })}
                          >
                            {String(cell || '')}
                          </TableCell>
                        ))}
                      </TableRow>
                    ))
                  ) : (
                    <TableRow>
                      <TableCell colSpan={csvPreview.headers.length} className="text-center py-4 text-slate-500">
                        No data available for preview, but column headers were detected.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </div>
          ) : (
            <div className="text-center p-8 border rounded-md">
              <p className="text-red-500">
                {error || "Failed to generate preview. You can still proceed with the upload."}
              </p>
            </div>
          )}

          <DialogFooter className="mt-4">
            <div className="w-full flex justify-between items-center">
              <span className="text-sm text-slate-600">
                {csvPreview ? (
                  <>Showing {csvPreview.rows.length} rows • Selected {selectedColumns.length} of {csvPreview.headers.length} columns</>
                ) : (
                  "Preview unavailable"
                )}
              </span>
              <Button onClick={() => setPreviewDialogOpen(false)}>Close</Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
