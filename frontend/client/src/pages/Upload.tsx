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
  type ColumnInfo
} from '@/lib/clickhouse';
import { useToast } from '@/hooks/use-toast';
import * as z from 'zod';

// Define form schema
const formSchema = z.object({
  tableName: z.string().min(1, { message: "Enter tablename" }),
  createNewTable: z.boolean().default(false),
  delimiter: z.string().default(','),
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
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();

  // Form setup
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      tableName: '',
      createNewTable: false,
      delimiter: ',',
    },
  });

  // Watch if it's a new table
  const createNewTable = form.watch('createNewTable');
  const selectedFile = form.watch('file');
  const delimiter = form.watch('delimiter');

  // Fetch tables when connection is established
  useEffect(() => {
    if (connectionConfig) {
      fetchTables();
      fetchDataTypes();
    }
  }, [connectionConfig]);

  // Preview CSV when file changes
  useEffect(() => {
    if (selectedFile && connectionConfig) {
      handlePreviewCsv(selectedFile, delimiter);
    }
  }, [selectedFile, delimiter]);

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
    });

    // Clear file input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handlePreviewCsv = async (csvFile: File, delimiter: string) => {
    try {
      setIsPreviewingCsv(true);
      setError(null);
      const result = await previewCsvFile(csvFile, delimiter);
      setCsvPreview(result);

      // Initialize selected columns with all headers by default
      setSelectedColumns(result.headers);

      // Initialize column types to String by default
      const initialColumnTypes: Record<string, string> = {};
      result.headers.forEach(header => {
        initialColumnTypes[header] = 'String';
      });
      setSelectedColumnTypes(initialColumnTypes);
    } catch (error) {
      setError(error instanceof Error ? error.message : "Failed to preview CSV file");
      toast({
        title: "CSV Preview Failed",
        description: error instanceof Error ? error.message : "Failed to preview CSV file",
        variant: "destructive",
      });
    } finally {
      setIsPreviewingCsv(false);
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

      // Make sure we have selected columns
      if (selectedColumns.length === 0) {
        throw new Error("Please select at least one column to upload");
      }

      // Filtering only selected columns
      const filteredTypes: Record<string, string> = Object.fromEntries(
        Object.entries(selectedColumnTypes).filter(([key]) => selectedColumns.includes(key))
      );

      // Prepare upload configuration
      const uploadConfig = {
        totalCols: Object.keys(selectedColumnTypes).length,
        connection: connectionConfig,
        tableName: formValues.tableName,
        createNewTable: formValues.createNewTable,
        delimiter: formValues.delimiter,
        columnTypes: filteredTypes
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

  if (!connectionConfig) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="mb-6 flex items-center">
          <Button variant="ghost" onClick={() => navigate('/')} className="mr-3 text-slate-600 hover:text-slate-900">
            <ArrowLeft className="h-4 w-4 mr-2" /> Back
          </Button>
          <h2 className="text-2xl font-bold text-slate-900">Upload to ClickHouse</h2>
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
        <h2 className="text-2xl font-bold text-slate-900">Upload to ClickHouse</h2>
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
                        Create a new table instead of uploading to an existing one
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
                          <Select onValueChange={field.onChange} value={field.value}>
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
                      <FormControl>
                        <Input
                          placeholder=","
                          maxLength={3}
                          className="w-16"
                          {...field}
                        />
                      </FormControl>
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
                      Select columns to upload and specify data types for each column
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
                          {createNewTable ? "Data Type" : "Type (Auto-detected)"}
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
              </CardContent>

              <CardFooter className="flex justify-between border-t px-6 py-4">
                {!isUploading &&
                  (
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

          {csvPreview && (
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
                  {csvPreview.rows.map((row, rowIndex) => (
                    <TableRow key={rowIndex}>
                      {row.map((cell: any, cellIndex: number) => (
                        <TableCell
                          key={cellIndex}
                          className={cn({
                            "bg-blue-50": selectedColumns.includes(csvPreview.headers[cellIndex]),
                            "opacity-50": !selectedColumns.includes(csvPreview.headers[cellIndex])
                          })}
                        >
                          {String(cell)}
                        </TableCell>
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
                Showing {csvPreview?.rows.length || 0} rows • Selected {selectedColumns.length} of {csvPreview?.headers.length || 0} columns
              </span>
              <Button onClick={() => setPreviewDialogOpen(false)}>Close</Button>
            </div>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
