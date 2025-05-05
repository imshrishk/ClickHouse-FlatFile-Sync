import { createBrowserRouter, RouterProvider } from "react-router-dom";
import HomePage from "./pages/Home";
import UploadPage from "./pages/Upload";
import DownloadPage from "./pages/Download";
import TestDownloadPage from "./pages/TestDownload";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "./components/ui/theme-provider";
import { Toaster } from "./components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import Navbar from "@/components/Navbar";
import NotFound from "@/pages/not-found";

const queryClient = new QueryClient();

const router = createBrowserRouter([
  {
    path: "/",
    element: <HomePage />,
  },
  {
    path: "/upload",
    element: <UploadPage />,
  },
  {
    path: "/download",
    element: <DownloadPage />,
  },
  {
    path: "/test-download",
    element: <TestDownloadPage />,
  },
]);

function App() {
  return (
    <ThemeProvider defaultTheme="light" storageKey="vite-ui-theme">
      <QueryClientProvider client={queryClient}>
        <TooltipProvider>
          <div className="min-h-screen flex flex-col bg-slate-50 dark:bg-slate-950 font-sans text-slate-900 dark:text-slate-100">
            <Navbar />
            
            <main className="flex-grow">
              <RouterProvider router={router} />
            </main>

            <footer className="border-t border-slate-200 dark:border-slate-800 py-6 mt-12">
              <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                  <div>
                    <div className="flex items-center gap-2 mb-4">
                      <div className="flex items-center justify-center h-8 w-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 text-white">
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M12 2a10 10 0 1 0 10 10H2A10 10 0 0 0 12 2Z" />
                        </svg>
                      </div>
                      <span className="font-bold text-lg bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400">
                        ClickSync
                      </span>
                    </div>
                    <p className="text-sm text-slate-600 dark:text-slate-400 mb-4">
                      Seamless bidirectional data transfer between ClickHouse and flat files.
                    </p>
                  </div>
                  
                  <div>
                    <h3 className="font-medium mb-3 text-slate-900 dark:text-slate-200">Quick Links</h3>
                    <ul className="space-y-2 text-sm">
                      <li><a href="/" className="text-slate-600 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400">Home</a></li>
                      <li><a href="/upload" className="text-slate-600 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400">Upload to ClickHouse</a></li>
                      <li><a href="/download" className="text-slate-600 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400">Download from ClickHouse</a></li>
                    </ul>
                  </div>
                  
                  <div>
                    <h3 className="font-medium mb-3 text-slate-900 dark:text-slate-200">Resources</h3>
                    <ul className="space-y-2 text-sm">
                      <li><a href="https://clickhouse.com/docs" target="_blank" rel="noopener" className="text-slate-600 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400">ClickHouse Docs</a></li>
                      <li><a href="https://github.com/ClickHouse/clickhouse-java" target="_blank" rel="noopener" className="text-slate-600 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400">ClickHouse Java Client</a></li>
                      <li><a href="https://clickhouse.com/docs/en/getting-started/example-datasets" target="_blank" rel="noopener" className="text-slate-600 dark:text-slate-400 hover:text-blue-600 dark:hover:text-blue-400">Example Datasets</a></li>
                    </ul>
                  </div>
                </div>
                
                <div className="mt-8 pt-6 border-t border-slate-200 dark:border-slate-800 text-center text-sm text-slate-500 dark:text-slate-500">
                  <p>&copy; {new Date().getFullYear()} ClickSync. All rights reserved.</p>
                </div>
              </div>
            </footer>
          </div>
          <Toaster />
        </TooltipProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}

export default App;
