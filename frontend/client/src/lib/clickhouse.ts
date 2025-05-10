import { apiRequest, SPRING_BOOT_URL } from './queryClient';
import { 
  ConnectionConfig, 
  UploadConfig, 
  DownloadConfig, 
  TablesConfig,
  TypesConfig,
  ColumnsConfig
} from '@shared/schema';
import { z } from 'zod';

// Extended SelectedColumnsQueryConfig with optional limit parameter
export interface SelectedColumnsQueryConfig {
  connection: ConnectionConfig;
  tableName: string;
  columns: string[];
  delimiter?: string;
  limit?: number; // Optional limit parameter for preview
  joinTables?: {
    joinTable: string;
    joinColumn: string;
    localColumn: string;
    joinType: 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';
  }[];
}

// Column information interface
export interface ColumnInfo {
  name: string;
  type: string;
}

// ClickHouse Connection Schema
export const connectionSchema = z.object({
  protocol: z.enum(["http", "https"]).default("http"),
  host: z.string().default("host.docker.internal"),
  port: z.string().default("8123"),
  username: z.string().default("default"),
  database: z.string().default("uk"),
  authType: z.enum(["password", "jwt"]).default("password"),
  password: z.string().optional(),
  jwt: z.string().optional(),
});

// Function to test connection to ClickHouse
export async function testConnection(config: ConnectionConfig): Promise<boolean> {
  try {
    // Create a copy of the config
    const connectionData = { ...config };
    
    // If using JWT, make sure to clean up unused fields
    if (connectionData.authType === 'jwt') {
      // Clear password if JWT is being used
      connectionData.password = undefined;
    } else {
      // Clear JWT if password is being used
      connectionData.jwt = undefined;
    }
    
    const response = await apiRequest('POST', '/api/clickhouse/test-connection', connectionData);
    const data = await response.json();
    return data.success;
  } catch (error) {
    return false;
  }
}

// Function to upload file to ClickHouse with progress tracking
export async function uploadFile(
  config: UploadConfig,
  file: File,
  onProgress?: (progress: { loaded: number; total: number; percentage: number }) => void
): Promise<{ success: boolean; message: string; lines: number }> {
  try {
    // Log file details for diagnostics
    console.log(`File details: name=${file.name}, size=${formatFileSize(file.size)}, type=${file.type}`);
    
    // Copy config to avoid mutating the original
    const configCopy = { ...config };

    // Clear sensitive unused auth field
    if (configCopy.connection.authType === 'jwt') {
      configCopy.connection.password = undefined;
    } else {
      configCopy.connection.jwt = undefined;
    }
    
    // Log connection details (excluding sensitive info)
    console.log(`Connection details: host=${configCopy.connection.host}, port=${configCopy.connection.port}, database=${configCopy.connection.database}`);

    // Determine if we should use streaming mode for large files
    const useStreaming = file.size > 1024 * 1024 * 1024; // 1GB
    console.log(`Upload size: ${formatFileSize(file.size)}, using ${useStreaming ? 'streaming' : 'standard'} mode`);

    // Prepare form data
    const formData = new FormData();
    formData.append('file', file);
    formData.append('config', new Blob([JSON.stringify(configCopy)], { type: 'application/json' }));
    
    // Prepare the upload URL with streaming parameter if needed
    // Since SPRING_BOOT_URL already includes '/api', we should not add it again
    const uploadUrl = `${SPRING_BOOT_URL}/clickhouse/upload${useStreaming ? '?streaming=true' : ''}`;
    console.log(`Starting file upload to ${uploadUrl}`);

    // Try each method in sequence until one works
    try {
      console.log("Attempting upload with XHR...");
      return await tryUploadWithXHR(uploadUrl, formData, file, configCopy, useStreaming, onProgress);
    } catch (xhrError) {
      console.error("XHR upload failed:", xhrError);
      console.log("Trying fallback fetch upload method...");
      
      try {
        console.log("Attempting upload with fetch API...");
        return await tryUploadWithFetch(uploadUrl, formData);
      } catch (fetchError) {
        console.error("Fetch upload failed:", fetchError);
        throw new Error(`All upload methods failed. ${xhrError instanceof Error ? xhrError.message : String(xhrError)}`);
      }
    }
  } catch (error) {
    console.error('Upload error:', error);
    return {
      success: false,
      lines: 0,
      message: error instanceof Error ? error.message : 'Failed to upload file',
    };
  }
}

// Helper function for XHR upload
async function tryUploadWithXHR(
  uploadUrl: string, 
  formData: FormData, 
  file: File,
  config: any,
  useStreaming: boolean,
  onProgress?: (progress: { loaded: number; total: number; percentage: number }) => void
): Promise<{ success: boolean; message: string; lines: number }> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    
    xhr.open('POST', uploadUrl, true);
    xhr.responseType = 'json';
    
    // Log when request opens
    console.log(`XHR request opened: ${uploadUrl}`);
    
    // Track when request actually starts
    const startTime = Date.now();
    let lastProgressTime = startTime;
    
    // Add debugging listeners
    xhr.addEventListener('loadstart', () => console.log('XHR upload started'));
    xhr.addEventListener('timeout', () => console.error('XHR upload timed out'));
    xhr.addEventListener('abort', () => console.error('XHR upload aborted'));
    xhr.addEventListener('error', (e) => console.error('XHR network error:', e));
    
    // Progress tracking
    xhr.upload.onprogress = (event) => {
      const now = Date.now();
      
      if (event.lengthComputable && onProgress && event.total) {
        const percentage = Math.round((event.loaded / event.total) * 100);
        onProgress({
          loaded: event.loaded,
          total: event.total,
          percentage
        });
        
        // Log every 2 seconds or every 20% progress
        if (now - lastProgressTime > 2000 || percentage % 20 === 0) {
          console.log(`Upload progress: ${percentage}% (${formatFileSize(event.loaded)}/${formatFileSize(event.total)})`);
          lastProgressTime = now;
        }
      }
    };
    
    xhr.onload = async () => {
      const elapsed = Date.now() - startTime;
      console.log(`Upload completed in ${elapsed}ms, status: ${xhr.status}`);
      
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const result = JSON.parse(xhr.responseText || '{}');
          console.log(`Upload completed successfully:`, result);
          resolve(result);
        } catch (e) {
          console.error('Failed to parse response:', e);
          console.log('Raw response:', xhr.responseText);
          resolve({
            success: true,
            lines: 0,
            message: 'Upload completed but failed to parse server response'
          });
        }
      } else {
        // Try to extract error message from response
        let errorMessage = `Server error: ${xhr.status}`;
        try {
          if (xhr.responseText) {
            const responseData = JSON.parse(xhr.responseText);
            if (responseData.message) {
              errorMessage = responseData.message;
            }
          }
        } catch (e) {
          console.error('Failed to parse error response:', e);
          if (xhr.responseText) {
            errorMessage += ` - ${xhr.responseText.substring(0, 100)}`;
          }
        }
        
        console.error(`Upload failed with status ${xhr.status}:`, errorMessage);
        reject(new Error(errorMessage));
      }
    };
    
    xhr.onerror = (event) => {
      console.error('XHR upload error:', event);
      
      // Try to determine if CORS is the issue
      const corsError = !xhr.responseText && xhr.status === 0;
      
      if (corsError) {
        reject(new Error('Network error (possible CORS issue). Check network tab for details.'));
      } else {
        reject(new Error('Network error during upload. Check console for details.'));
      }
    };
    
    // Set a reasonable timeout for large files
    xhr.timeout = 600000; // Increasing from 300000 (5 min) to 600000 (10 min)
    
    // Add event handler for timeouts with better user-friendly message
    xhr.ontimeout = () => {
      console.error('XHR upload timed out after 10 minutes');
      reject(new Error('Upload timed out. The server might be under heavy load or the file is too large for direct processing.'));
    };
    
    try {
      xhr.send(formData);
      console.log('XHR request sent, waiting for response...');
    } catch (e) {
      console.error('Error sending XHR request:', e);
      reject(new Error(`Failed to send upload request: ${e instanceof Error ? e.message : String(e)}`));
    }
  });
}

// Helper function for fetch-based upload
async function tryUploadWithFetch(
  uploadUrl: string, 
  formData: FormData
): Promise<{ success: boolean; message: string; lines: number }> {
  console.log(`Starting fetch-based upload to ${uploadUrl}`);
  const startTime = Date.now();
  
  try {
    console.log(`Initializing fetch request with timeout`);
    
    // Create an abort controller for timeout
    const controller = new AbortController();
    const timeoutId = setTimeout(() => {
      controller.abort();
      console.error(`Fetch upload timed out after ${600000}ms`);
    }, 600000); // 10 minute timeout (increased from 5 minutes)
    
    const response = await fetch(uploadUrl, {
      method: 'POST',
      body: formData,
      credentials: 'include',
      signal: controller.signal,
      // Do not set Content-Type header - browser sets it with proper boundary
    });
    
    // Clear the timeout since we got a response
    clearTimeout(timeoutId);
    
    const elapsed = Date.now() - startTime;
    console.log(`Fetch upload completed in ${elapsed}ms, status: ${response.status}`);
    
    if (!response.ok) {
      console.error(`Fetch upload failed with status ${response.status}: ${response.statusText}`);
      
      // Try to get more detailed error from the response
      try {
        const errorText = await response.text();
        console.error(`Error response: ${errorText}`);
        throw new Error(`Server error ${response.status}: ${errorText || response.statusText}`);
      } catch (textError) {
        throw new Error(`Server error ${response.status}: ${response.statusText}`);
      }
    }
    
    try {
      const result = await response.json();
      console.log(`Fetch upload success, result:`, result);
      return result;
    } catch (jsonError) {
      console.error(`Failed to parse response as JSON:`, jsonError);
      // If we can't parse JSON but got a 200 OK, assume success
      if (response.ok) {
        return { success: true, lines: 0, message: 'Upload successful, but could not parse response' };
      }
      throw new Error(`Failed to parse server response: ${jsonError}`);
    }
  } catch (error) {
    console.error(`Fetch upload error:`, error);
    
    // Check if it was an abort error (timeout)
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error(`Upload timed out after ${600000 / 1000} seconds. The file may be too large or the server is slow to respond.`);
    }
    
    // Re-throw with more context
    throw new Error(`Fetch upload failed: ${error instanceof Error ? error.message : String(error)}`);
  }
}

// Helper function to format file size
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Function to query ClickHouse and get preview results
export async function queryClickHouse(config: DownloadConfig): Promise<{ headers: string[]; rows: any[] }> {
  try {
    // Create a copy of the config
    const configCopy = { ...config };
    
    // Handle authentication type properly
    if (configCopy.connection.authType === 'jwt') {
      // Clear password if JWT is being used
      configCopy.connection.password = undefined;
    } else {
      // Clear JWT if password is being used
      configCopy.connection.jwt = undefined;
    }
    
    const response = await apiRequest('POST', '/api/clickhouse/query', configCopy);
    return await response.json();
  } catch (error) {
    throw error;
  }
}

// Function to download data from ClickHouse with progress tracking
export async function downloadData(
  config: SelectedColumnsQueryConfig,
  onProgress?: (progress: { loaded: number; total: number; percentage: number }) => void,
  setFileSize?: (size: number) => void
): Promise<{ size: number; filename: string; lines: number }> {
  try {
    const configCopy: SelectedColumnsQueryConfig = { ...config };

    // Clean up authentication values
    if (configCopy.connection.authType === 'jwt') {
      configCopy.connection.password = undefined;
    } else {
      configCopy.connection.jwt = undefined;
    }

    // Log the request being made
    console.log('Sending download request to:', `${SPRING_BOOT_URL}/clickhouse/download`);
    console.log('Request configuration:', JSON.stringify(configCopy));

    // Use fetch API for better handling of binary data
    // SPRING_BOOT_URL already contains '/api' if needed
    const requestUrl = `${SPRING_BOOT_URL}/clickhouse/download`;
    console.log('Full URL for fetch request:', requestUrl);
    
    try {
      const response = await fetch(requestUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': '*/*',
        },
        body: JSON.stringify(configCopy),
        credentials: 'include',
      });
  
      console.log('Response status:', response.status);
      
      // Log headers for debugging
      const responseHeaders: Record<string, string> = {};
      response.headers.forEach((value, key) => {
        responseHeaders[key] = value;
      });
      console.log('Response headers:', responseHeaders);
  
      if (!response.ok) {
        const errorText = await response.text();
        console.error('Error response body:', errorText);
        throw new Error(`Server error: ${response.status} ${response.statusText}. Details: ${errorText || 'No details available'}`);
      }
  
      // Get headers
      const contentDisposition = response.headers.get('content-disposition');
      const contentType = response.headers.get('content-type') || 'application/octet-stream';
      const contentLength = response.headers.get('content-length');
      const lineCount = response.headers.get('x-line-count');
  
      console.log('Download headers:', {
        contentDisposition,
        contentType,
        contentLength,
        lineCount
      });
  
      // Parse filename from Content-Disposition header
      const filename = contentDisposition
        ? contentDisposition.split('filename=')[1]?.replace(/"/g, '')
        : `clickhouse_data_${new Date().toISOString()}.csv`;
  
      // Get response data as blob
      const blob = await response.blob();
      const fileSize = blob.size;
  
      console.log(`Downloaded blob size: ${fileSize} bytes, type: ${blob.type}`);
  
      if (fileSize === 0) {
        throw new Error('Downloaded file is empty');
      }
      
      // If setFileSize callback is provided, use it
      if (setFileSize) {
        setFileSize(fileSize);
      }
  
      // Create download link and trigger download
      const downloadUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
  
      // Cleanup
      document.body.removeChild(link);
      window.URL.revokeObjectURL(downloadUrl);
  
      return { 
        size: fileSize, 
        filename: filename, 
        lines: lineCount ? parseInt(lineCount, 10) : 0 
      };
    } catch (fetchError) {
      console.error('Fetch error details:', fetchError);
      
      // Fall back to direct download approach if fetch fails
      try {
        console.log('Attempting fallback direct download method...');
        
        // Use an absolute URL with the browser's hostname, not the internal backend URL
        // This ensures the download URL is accessible from the browser
        const browserBaseUrl = window.location.origin;
        // Direct path to the API endpoint (we know the API is at /api/...)
        const downloadPath = '/api/direct-download';
        
        // Add table parameter from the config
        let fallbackUrl = `${browserBaseUrl}${downloadPath}?table=${configCopy.tableName}`;
        
        // Add optional limit parameter - use 10000 as default for the fallback method
        const limit = configCopy.limit || 10000;
        fallbackUrl += `&limit=${limit}`;
        
        console.log('Using absolute fallback URL:', fallbackUrl);
        window.location.href = fallbackUrl;
        
        return {
          size: 0,
          filename: `${configCopy.tableName}_data.csv`,
          lines: 0
        };
      } catch (fallbackError) {
        console.error('Fallback download also failed:', fallbackError);
        throw new Error('Download failed with both methods. Please try again later.');
      }
    }
  } catch (error) {
    console.error('Download function error:', error);
    throw error;
  }
}

// Function to get tables from ClickHouse
export async function getTables(config: TablesConfig): Promise<string[]> {
  try {
    // Create a copy of the config
    const configCopy = { ...config };
    
    // Handle authentication type properly
    if (configCopy.connection.authType === 'jwt') {
      // Clear password if JWT is being used
      configCopy.connection.password = undefined;
    } else {
      // Clear JWT if password is being used
      configCopy.connection.jwt = undefined;
    }
    
    const response = await apiRequest('POST', '/api/clickhouse/tables', configCopy);
    const data = await response.json();
    return data.tables;
  } catch (error) {
    throw error;
  }
}

// Function to get columns for a specific table
export async function getColumns(config: ColumnsConfig): Promise<ColumnInfo[]> {
  try {
    // Create a copy of the config
    const configCopy = { ...config };
    
    // Handle authentication type properly
    if (configCopy.connection.authType === 'jwt') {
      // Clear password if JWT is being used
      configCopy.connection.password = undefined;
    } else {
      // Clear JWT if password is being used
      configCopy.connection.jwt = undefined;
    }
    
    const response = await apiRequest('POST', '/api/clickhouse/columns', configCopy);
    const data = await response.json();
    return data.columns;
  } catch (error) {
    throw error;
  }
}

// Function to get data types from ClickHouse
export async function getTypes(config: TypesConfig): Promise<string[]> {
  try {
    // Create a copy of the config
    const configCopy = { ...config };
    
    // Handle authentication type properly
    if (configCopy.connection.authType === 'jwt') {
      // Clear password if JWT is being used
      configCopy.connection.password = undefined;
    } else {
      // Clear JWT if password is being used
      configCopy.connection.jwt = undefined;
    }
    
    const response = await apiRequest('POST', '/api/clickhouse/types', configCopy);
    const data = await response.json();
    return data.types;
  } catch (error) {
    throw error;
  }
}

// Enhanced getTrimmedCsvFile function with better chunking for large files
export const getTrimmedCsvFile = (
  originalFile: File,
  lineLimit: number = 100
): Promise<File> => {
  return new Promise((resolve, reject) => {
    const CHUNK_SIZE = 1024 * 1024; // 1MB chunks for better performance
    const reader = new FileReader();
    let offset = 0;
    let textBuffer = '';
    const lines: string[] = [];
    const maxSizeForPreview = 10 * 1024 * 1024; // Only process up to 10MB for preview
    
    console.log(`Processing file for preview. Size: ${formatFileSize(originalFile.size)}`);
    
    // For extremely large files, just read the start of the file
    if (originalFile.size > 100 * 1024 * 1024) { // 100MB+
      console.log(`Large file detected (${formatFileSize(originalFile.size)}). Using optimized preview.`);
      const previewSlice = originalFile.slice(0, maxSizeForPreview);
      // Create a new smaller file for preview
      const previewFile = new File([previewSlice], "preview_" + originalFile.name, {
        type: "text/csv"
      });
      console.log(`Created preview file of size: ${formatFileSize(previewFile.size)}`);
      resolve(previewFile);
      return;
    }

    const processLines = () => {
      try {
        const allLines = textBuffer.split(/\r?\n/);
        // Keep the last partial line in the buffer
        textBuffer = allLines.pop() || '';
        lines.push(...allLines);

        if (lines.length >= lineLimit + 1 || offset >= originalFile.size || offset >= maxSizeForPreview) {
          const finalLines = lines.slice(0, lineLimit + 1);
          if (textBuffer && (offset >= originalFile.size || offset >= maxSizeForPreview)) {
            finalLines.push(textBuffer);
          }
          const resultText = finalLines.join('\n');
          const blob = new Blob([resultText], { type: "text/csv" });
          const previewFile = new File([blob], "preview_" + originalFile.name, {
            type: "text/csv"
          });
          console.log(`Created preview with ${finalLines.length} lines, size: ${formatFileSize(previewFile.size)}`);
          resolve(previewFile);
        } else {
          readNextChunk();
        }
      } catch (error) {
        console.error("Error processing CSV lines:", error);
        // If we have some lines, return what we have
        if (lines.length > 0) {
          const resultText = lines.slice(0, lineLimit + 1).join('\n');
          const blob = new Blob([resultText], { type: "text/csv" });
          const previewFile = new File([blob], "preview_" + originalFile.name, {
            type: "text/csv"
          });
          console.log(`Created partial preview with ${lines.length} lines due to error`);
          resolve(previewFile);
        } else {
          reject(new Error("Failed to process file for preview"));
        }
      }
    };

    const readNextChunk = () => {
      try {
        if (offset >= maxSizeForPreview) {
          console.log(`Reached preview size limit (${formatFileSize(maxSizeForPreview)})`);
          processLines(); // Process what we have
          return;
        }
        
        const endOffset = Math.min(offset + CHUNK_SIZE, originalFile.size, maxSizeForPreview);
        const slice = originalFile.slice(offset, endOffset);
        reader.readAsText(slice);
        console.log(`Reading chunk ${offset}-${endOffset} of ${originalFile.size}`);
      } catch (error) {
        console.error("Error reading file chunk:", error);
        // If we have read some data, try to process what we have
        if (textBuffer.length > 0 || lines.length > 0) {
          processLines();
        } else {
          reject(new Error("Failed to read file chunk"));
        }
      }
    };

    reader.onload = () => {
      offset += CHUNK_SIZE;
      textBuffer += reader.result as string;
      processLines();
    };

    reader.onerror = (error) => {
      console.error("File reader error:", error);
      // If we have some data, return what we have
      if (lines.length > 0) {
        const resultText = lines.slice(0, lineLimit + 1).join('\n');
        const blob = new Blob([resultText], { type: "text/csv" });
        const previewFile = new File([blob], "preview_" + originalFile.name, {
          type: "text/csv"
        });
        console.log(`Created partial preview due to reader error`);
        resolve(previewFile);
      } else {
        reject(new Error("File reader error"));
      }
    };
    
    // Add timeout for safety
    const timeout = setTimeout(() => {
      if (lines.length > 0) {
        console.warn("Preview timeout - returning partial data");
        const resultText = lines.slice(0, lineLimit + 1).join('\n');
        const blob = new Blob([resultText], { type: "text/csv" });
        const previewFile = new File([blob], "preview_" + originalFile.name, {
          type: "text/csv"
        });
        resolve(previewFile);
      } else {
        reject(new Error("Preview timed out"));
      }
    }, 10000); // 10 second timeout

    // Start the reading process
    readNextChunk();
    
    return () => clearTimeout(timeout);
  });
};

// Function to preview a CSV file with enhanced error handling
export async function previewCsvFile(file: File, delimiter: string = ',', hasHeader: boolean = true): Promise<{ headers: string[]; rows: any[] }> {
  try {
    console.log(`Starting preview for file: ${file.name}, size: ${formatFileSize(file.size)}, hasHeader: ${hasHeader}`);
    const startTime = performance.now();
    
    // Prepare file for preview
    const previewFile = await getTrimmedCsvFile(file, 100); // Limit to 100 rows for preview
    console.log(`Preview file prepared in ${Math.round(performance.now() - startTime)}ms`);
    
    const formData = new FormData();
    formData.append('file', previewFile);
    
    // Ensure we handle tab delimiter properly
    const actualDelimiter = delimiter === '\\t' || delimiter === 'tab' ? '\t' : delimiter;
    console.log(`Using delimiter: '${actualDelimiter === '\t' ? '\\t (tab)' : actualDelimiter}'`);
    formData.append('delimiter', actualDelimiter);
    
    // Add hasHeader parameter
    formData.append('hasHeader', hasHeader.toString());

    // Add timeout handling
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 60000); // 60 second timeout (increased from 30)
    
    try {
      // IMPORTANT: Fixed URL path - ensure it matches the backend controller mapping
      // Since SPRING_BOOT_URL already includes '/api', we should not add it again
      const fullUrl = `${SPRING_BOOT_URL}/clickhouse/preview-csv`;
      console.log(`Sending preview request to ${fullUrl}`);
      
      const response = await fetch(fullUrl, {
        method: 'POST',
        body: formData,
        credentials: 'include',
        signal: controller.signal,
        // Do NOT set Content-Type - browser will set it correctly with proper boundary
      });
      
      clearTimeout(timeoutId);

      console.log(`Preview response status: ${response.status}`);
      
      if (!response.ok) {
        const errorText = await response.text();
        console.error(`Preview error response: ${errorText}`);
        throw new Error(errorText || response.statusText || `HTTP error ${response.status}`);
      }

      const result = await response.json();
      console.log(`Preview completed successfully in ${Math.round(performance.now() - startTime)}ms, rows: ${result.rows?.length || 0}`);
      return result;
    } catch (error) {
      clearTimeout(timeoutId);
      
      // Better network error handling
      if (error instanceof TypeError && error.message.includes('Failed to fetch')) {
        console.error('Network error during preview:', error);
        throw new Error('Network error: Please check that the backend server is running and accessible');
      }
      
      // If we have a timeout/abort error, provide a more specific message
      if (error instanceof DOMException && error.name === 'AbortError') {
        throw new Error("CSV preview timed out. The file may be too large or complex.");
      }
      
      console.error('Preview request error:', error);
      throw error;
    }
  } catch (error) {
    console.error('CSV preview error:', error);
    
    // Try to use the file header as a fallback
    try {
      const reader = new FileReader();
      const headerPromise = new Promise<string>((resolve, reject) => {
        reader.onload = (e) => resolve((e.target?.result as string) || '');
        reader.onerror = reject;
        reader.readAsText(file.slice(0, 100000)); // Read first 100KB to get more rows
      });
      
      const headerData = await headerPromise;
      if (headerData) {
        // Ensure we handle tab delimiter properly for fallback parsing
        const actualDelimiter = delimiter === '\\t' || delimiter === 'tab' ? '\t' : delimiter;
        
        const lines = headerData.split(/\r?\n/);
        const firstLine = lines[0];
        if (firstLine) {
          let headers: string[];
          
          // Generate column headers if not using first row as header
          if (!hasHeader) {
            const columnCount = firstLine.split(actualDelimiter).length;
            headers = Array.from({ length: columnCount }, (_, i) => `Column${i + 1}`);
          } else {
            headers = firstLine.split(actualDelimiter).map(h => h.trim());
          }
          
          console.log('Using headers:', headers, 'hasHeader:', hasHeader);
          
          // Create data rows from the file content instead of placeholder text
          const dataRows = [];
          // Start from index 0 or 1 depending on hasHeader setting
          const startIndex = hasHeader ? 1 : 0;
          
          // Try to get up to 100 data rows
          for (let i = startIndex; i < Math.min(lines.length, startIndex + 100); i++) {
            if (lines[i] && lines[i].trim()) {
              // For tab-delimited files with quoted values, use a more careful parsing approach
              if (actualDelimiter === '\t') {
                try {
                  // Handle quoted fields more carefully
                  const row = parseDelimitedLine(lines[i], actualDelimiter);
                  dataRows.push(row);
                } catch (e) {
                  // Fallback to simple split if parsing fails
                  dataRows.push(lines[i].split(actualDelimiter));
                }
              } else {
                dataRows.push(lines[i].split(actualDelimiter).map(cell => cell.trim()));
              }
            }
          }
          
          return {
            headers: headers,
            rows: dataRows.length > 0 ? dataRows : [headers.map(() => "(Preview failed - used headers from file)")]
          };
        }
      }
    } catch (headerError) {
      console.error("Failed to extract header as fallback:", headerError);
    }
    
    // If we already have a formatted error message, pass it through
    if (error instanceof Error) {
      throw new Error(`CSV Preview Failed: ${error.message}`);
    }
    
    // Otherwise, provide a generic message
    throw new Error("Failed to generate CSV preview. The file may be too large or in an incorrect format.");
  }
}

// Helper function to parse a delimited line with quoted fields
function parseDelimitedLine(line: string, delimiter: string): string[] {
  const fields: string[] = [];
  let currentField = '';
  let inQuotes = false;
  
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    
    if (char === '"') {
      inQuotes = !inQuotes;
    } else if (char === delimiter && !inQuotes) {
      fields.push(cleanField(currentField));
      currentField = '';
    } else {
      currentField += char;
    }
  }
  
  // Add the last field
  fields.push(cleanField(currentField));
  return fields;
}

// Helper function to clean a field, removing quotes
function cleanField(field: string): string {
  field = field.trim();
  // Remove quotes at beginning and end of field
  if (field.startsWith('"') && field.endsWith('"') && field.length >= 2) {
    field = field.substring(1, field.length - 1);
  }
  return field;
}

// Function to query with selected columns
export async function queryWithSelectedColumns(config: SelectedColumnsQueryConfig): Promise<{ headers: string[]; rows: any[] }> {
  try {
    console.log(`Querying selected columns from table: ${config.tableName}, columns:`, config.columns);
    
    // Create a copy of the config
    const configCopy = { ...config };
    
    // Handle authentication type properly
    if (configCopy.connection.authType === 'jwt') {
      // Clear password if JWT is being used
      configCopy.connection.password = undefined;
    } else {
      // Clear JWT if password is being used
      configCopy.connection.jwt = undefined;
    }
    
    // Add better error handling with timeout
    try {
      // Use a longer timeout for this request since it might involve a lot of data
      const response = await apiRequest('POST', '/api/clickhouse/query-selected-columns', configCopy, 60000);
      
      // Check if response is OK before trying to parse JSON
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Server returned error: ${response.status} ${response.statusText}. Details: ${errorText || 'No details available'}`);
      }
      
      const data = await response.json();
      console.log(`Query completed successfully, received ${data.rows?.length || 0} rows`);
      
      // If the response doesn't have the expected format, create a meaningful error
      if (!data.headers || !data.rows) {
        throw new Error('Invalid response format from server. Missing headers or rows.');
      }
      
      return data;
    } catch (requestError) {
      console.error('Error during API request:', requestError);
      
      // Add more specific error handling
      if (requestError instanceof TypeError && requestError.message.includes('Failed to fetch')) {
        throw new Error('Network error while querying the database. Please check your connection and make sure the backend is running.');
      }
      
      throw requestError;
    }
  } catch (error) {
    console.error('Error in queryWithSelectedColumns:', error);
    if (error instanceof Error) {
      throw new Error(`Query failed: ${error.message}`);
    }
    throw new Error('Failed to query database for unknown reasons');
  }
}

// Function to check if the backend server is reachable
export async function serverCheck(bypassErrors: boolean = false): Promise<{
  status: 'online' | 'offline' | 'partial';
  details: string;
}> {
  const checkEndpoints = [
    { name: 'Health Check', url: `${SPRING_BOOT_URL}/health-test` },
    { name: 'Upload Endpoint', url: `${SPRING_BOOT_URL}/health-test` },
    { name: 'Clickhouse API', url: `${SPRING_BOOT_URL}/test-connection` }
  ];
  
  const results = [];
  
  try {
    console.log('Starting server connectivity check');
    
    for (const endpoint of checkEndpoints) {
      try {
        // Use AbortController for timeouts
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 15000); // 15 second timeout
        
        const start = Date.now();
        console.log(`Checking endpoint: ${endpoint.name} (${endpoint.url})`);
        
        const response = await fetch(endpoint.url, {
          method: endpoint.name === 'Clickhouse API' ? 'POST' : 'GET',
          headers: endpoint.name === 'Clickhouse API' ? { 'Content-Type': 'application/json' } : {},
          body: endpoint.name === 'Clickhouse API' ? JSON.stringify({
            host: 'localhost',
            port: '8123',
            protocol: 'http',
            database: 'default',
            username: 'default',
            password: '',
            authType: 'password'
          }) : undefined,
          cache: 'no-cache',
          credentials: 'include',
          signal: controller.signal
        });
        
        clearTimeout(timeoutId);
        const time = Date.now() - start;
        
        const ok = response.ok;
        results.push({ 
          name: endpoint.name, 
          ok, 
          status: response.status,
          time
        });
        
        console.log(`Endpoint ${endpoint.name}: ${ok ? 'OK' : 'Failed'} in ${time}ms (${response.status})`);
      } catch (error) {
        let errorMessage = 'Unknown error';
        
        if (error instanceof DOMException && error.name === 'AbortError') {
          errorMessage = 'Request timed out after 15 seconds';
        } else if (error instanceof Error) {
          errorMessage = error.message;
        }
        
        console.error(`Error checking ${endpoint.name}:`, errorMessage);
        results.push({ 
          name: endpoint.name, 
          ok: false, 
          status: 0, 
          time: 0,
          error: errorMessage
        });
      }
    }
    
    // Determine overall status
    const endpointsAvailable = results.filter(r => r.ok).length;
    const status = endpointsAvailable === checkEndpoints.length ? 'online' : 
                  endpointsAvailable > 0 ? 'partial' : 'offline';
    
    console.log('Server check complete:', {
      status,
      endpointsAvailable,
      results
    });
    
    // If bypassErrors is true, consider the server online even with partial connectivity
    return {
      status: bypassErrors && endpointsAvailable > 0 ? 'online' : status,
      details: results.map(r => `${r.name}: ${r.ok ? 'OK' : 'Failed'} in ${r.time}ms (${r.status})`).join('\n'),
    };
  } catch (e) {
    console.error('Fatal error in server check:', e);
    return {
      status: 'offline',
      details: `Fatal error running connectivity tests: ${e instanceof Error ? e.message : String(e)}`,
    };
  }
}
