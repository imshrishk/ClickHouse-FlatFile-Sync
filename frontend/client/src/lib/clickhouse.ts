import { apiRequest, SPRING_BOOT_URL } from './queryClient';
import axios, { AxiosProgressEvent } from "axios"
import { 
  ConnectionConfig, 
  UploadConfig, 
  DownloadConfig, 
  TablesConfig,
  TypesConfig,
  ColumnsConfig,
  SelectedColumnsQueryConfig
} from '@shared/schema';

// Column information interface
export interface ColumnInfo {
  name: string;
  type: string;
}

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
    // Copy config to avoid mutating the original
    const configCopy = { ...config };

    // Clear sensitive unused auth field
    if (configCopy.connection.authType === 'jwt') {
      configCopy.connection.password = undefined;
    } else {
      configCopy.connection.jwt = undefined;
    }

    // Prepare form data
    const formData = new FormData();
    formData.append('file', file);
    formData.append('config', new Blob([JSON.stringify(configCopy)], { type: 'application/json' }));

    // Upload using axios
    const response = await axios.post(`${SPRING_BOOT_URL}/api/clickhouse/upload`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      withCredentials: true,
      onUploadProgress: (event) => {
        if (event.lengthComputable && onProgress && event.total) {
          const percentage = Math.round((event.loaded / event.total) * 100);
          onProgress({
            loaded: event.loaded,
            total: event.total,
            percentage,
          });
        }
      },
    });

    if (response.data && typeof response.data === 'object') {
      return response.data;
    } else {
      return {
        success: true,
        lines: 0,
        message: 'File uploaded successfully (no response data)',
      };
    }
  } catch (error) {
    return {
      success: false,
      lines: 0,
      message: error instanceof Error ? error.message : 'Failed to upload file',
    };
  }
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
        const fallbackUrl = browserBaseUrl + downloadPath;
        
        console.log('Using absolute fallback URL:', fallbackUrl);
        window.location.href = fallbackUrl;
        
        return {
          size: 0,
          filename: 'clickhouse_data.csv',
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

export const getTrimmedCsvFile = (
  originalFile: File,
  lineLimit: number = 100
): Promise<File> => {
  return new Promise((resolve, reject) => {
    const CHUNK_SIZE = 64 * 1024; // 64KB
    const reader = new FileReader();
    let offset = 0;
    let textBuffer = '';
    const lines: string[] = [];

    const processLines = () => {
      const allLines = textBuffer.split(/\r?\n/);
      textBuffer = allLines.pop() || '';
      lines.push(...allLines);

      if (lines.length >= lineLimit + 1 || offset >= originalFile.size) {
        const finalLines = lines.slice(0, lineLimit + 1);
        if (textBuffer && offset >= originalFile.size) {
          finalLines.push(textBuffer);
        }
        const resultText = finalLines.join('\n');
        const blob = new Blob([resultText], { type: "text/csv" });
        const previewFile = new File([blob], "preview_" + originalFile.name, {
          type: "text/csv"
        });
        resolve(previewFile);
      } else {
        readNextChunk();
      }
    };

    const readNextChunk = () => {
      const slice = originalFile.slice(offset, offset + CHUNK_SIZE);
      reader.readAsText(slice);
    };

    reader.onload = () => {
      offset += CHUNK_SIZE;
      textBuffer += reader.result as string;
      processLines();
    };

    reader.onerror = () => reject(new Error("❌ Error reading file."));

    readNextChunk();
  });
};

// Function to preview a CSV file
export async function previewCsvFile(file: File, delimiter: string = ','): Promise<{ headers: string[]; rows: any[] }> {
  try {
    const previewFile = await getTrimmedCsvFile(file);
    const formData = new FormData();

    formData.append('file', previewFile);
    formData.append('delimiter', delimiter);

    const response = await fetch(SPRING_BOOT_URL + '/api/clickhouse/preview-csv', {
      method: 'POST',
      body: formData,
      credentials: 'include',
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(errorText || response.statusText);
    }

    return await response.json();
  } catch (error) {
    throw error;
  }
}

// Function to query with selected columns
export async function queryWithSelectedColumns(config: SelectedColumnsQueryConfig): Promise<{ headers: string[]; rows: any[] }> {
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
    
    const response = await apiRequest('POST', '/api/clickhouse/query-selected-columns', configCopy);
    return await response.json();
  } catch (error) {
    throw error;
  }
}
