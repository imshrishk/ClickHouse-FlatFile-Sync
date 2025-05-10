import { QueryClient, QueryFunction } from "@tanstack/react-query";

export const SPRING_BOOT_URL = import.meta.env.VITE_SPRING_BOOT_URL || "/api";

type UnauthorizedBehavior = "throw" | "returnNull";

async function throwIfResNotOk(res: Response) {
  if (!res.ok) {
    let errorMessage = `Server error: ${res.status} ${res.statusText}`;
    try {
      const errorText = await res.text();
      if (errorText) {
        errorMessage = errorText;
      }
    } catch (e) {
      console.error("Error reading error response:", e);
    }
    throw new Error(errorMessage);
  }
}

export async function apiRequest(
  method: string,
  url: string,
  data?: unknown | undefined,
  timeoutMs: number = 30000 // Increased default timeout
): Promise<Response> {
  // Make sure URL has the correct format
  url = url.startsWith('/') ? url : `${SPRING_BOOT_URL}/${url}`;
  // Remove any double slashes that might occur when combining URLs
  url = url.replace(/([^:]\/)\/+/g, "$1");
  
  console.log(`Making ${method} request to: ${url}`);
  
  try {
    // Create AbortController for the timeout
    const controller = new AbortController();
    const timeoutId = setTimeout(() => {
      controller.abort();
      console.warn(`Request to ${url} timed out after ${timeoutMs}ms`);
    }, timeoutMs);
    
    // Prepare headers with content type
    const headers: HeadersInit = {};
    if (data) {
      headers["Content-Type"] = "application/json";
    }
    
    // Add CORS related headers
    headers["Accept"] = "application/json, text/plain, */*";
    
    const requestOptions: RequestInit = {
      method,
      headers,
      body: data ? JSON.stringify(data) : undefined,
      credentials: "include", // Include cookies for CORS requests
      signal: controller.signal,
      mode: "cors", // Explicitly set CORS mode
    };
    
    console.log(`Request options:`, {
      method,
      dataSize: data ? JSON.stringify(data).length : 0,
      timeout: timeoutMs
    });
    
    const res = await fetch(url, requestOptions);
    
    // Clear the timeout since we got a response
    clearTimeout(timeoutId);
    
    // Log response details for debugging
    console.log(`Response status for ${url}: ${res.status} ${res.statusText}`);
    
    return res;
  } catch (error) {
    // Handle specific error types with clear messages
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error(`Request to ${url} timed out after ${timeoutMs}ms. The server may be overloaded or the request too large.`);
    }
    
    // Handle network connectivity issues
    if (error instanceof TypeError && error.message.includes('Failed to fetch')) {
      throw new Error(`Network error when connecting to ${url}. Please check your connection and ensure the backend is running.`);
    }
    
    // Log and rethrow
    console.error(`API request error for ${url}:`, error);
    throw error;
  }
}

export const getQueryFn: <T>(options: {
  on401: UnauthorizedBehavior;
}) => QueryFunction<T> =
  ({ on401: unauthorizedBehavior }) =>
  async ({ queryKey }) => {
    const res = await fetch(queryKey[0] as string, {
      credentials: "include",
    });

    if (unauthorizedBehavior === "returnNull" && res.status === 401) {
      return null;
    }

    await throwIfResNotOk(res);
    return await res.json();
  };

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      queryFn: getQueryFn({ on401: "throw" }),
      refetchInterval: false,
      refetchOnWindowFocus: false,
      staleTime: Infinity,
      retry: false,
    },
    mutations: {
      retry: false,
    },
  },
});
