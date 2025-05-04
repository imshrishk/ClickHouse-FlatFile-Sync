/**
 * Home.tsx
 * 
 * Main landing page for the ClickHouse-FlatFile-Sync application.
 * Provides a user-friendly interface for selecting between the two primary operations:
 * 1. Upload (CSV to ClickHouse)
 * 2. Download (ClickHouse to CSV)
 * 
 * Features:
 * - Interactive card selection with visual feedback
 * - Tabbed interface for operation selection and feature highlights
 * - Responsive design with mobile and desktop layouts
 * - Smooth animations using Framer Motion
 * - Dark mode support with theme-aware styling
 */

import { useState, useEffect } from 'react';
import { useLocation } from 'wouter';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardFooter } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { 
  Upload, 
  Download, 
  ArrowRight, 
  Database, 
  FileText, 
  ChevronRight,
  BarChart4,
  RefreshCw,
  ArrowLeftRight
} from 'lucide-react';
import { motion } from 'framer-motion';
import { cn } from '@/lib/utils';

export default function Home() {
  // Navigation state using wouter
  const [location, setLocation] = useLocation();
  
  // Track which operation card is selected (upload, download, or none)
  const [selectedOption, setSelectedOption] = useState<'upload' | 'download' | null>(null);
  
  // Used to trigger entrance animations after component mounts
  const [mounted, setMounted] = useState(false);

  /**
   * Trigger entrance animations once component is mounted
   * This creates a smoother initial load experience
   */
  useEffect(() => {
    setMounted(true);
  }, []);

  /**
   * Navigate to the appropriate page based on the selected option
   * Called when the Continue button is clicked
   */
  const handleContinue = () => {
    if (selectedOption === 'upload') {
      setLocation('/upload');
    } else if (selectedOption === 'download') {
      setLocation('/download');
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-50 to-slate-100 dark:from-slate-950 dark:to-slate-900">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
        {/* Hero section with animated entrance */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: mounted ? 1 : 0, y: mounted ? 0 : 20 }}
          transition={{ duration: 0.5 }}
          className="text-center mb-16"
        >
          {/* App badge/logo */}
          <div className="mb-4 inline-flex items-center justify-center p-2 bg-white dark:bg-slate-800 rounded-full shadow-sm">
            <Database className="h-6 w-6 text-blue-500 mr-2" />
            <span className="text-sm font-medium text-slate-600 dark:text-slate-300">ClickHouse Sync Tool</span>
          </div>
          
          {/* Main title with gradient text */}
          <h1 className="text-5xl font-extrabold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400 mb-4">
            ClickHouse ⟷ FlatFile
          </h1>
          
          {/* App description */}
          <p className="text-slate-600 dark:text-slate-400 text-xl max-w-2xl mx-auto leading-relaxed">
            Seamlessly transfer data between ClickHouse databases and flat files with advanced control and real-time feedback.
          </p>
        </motion.div>

        {/* Main content area */}
        <div className="mb-12">
          {/* Tabs for "Select Operation" and "Key Features" */}
          <Tabs defaultValue="select" className="w-full max-w-4xl mx-auto">
            <TabsList className="grid w-full grid-cols-2 mb-8">
              <TabsTrigger value="select">Select Operation</TabsTrigger>
              <TabsTrigger value="features">Key Features</TabsTrigger>
            </TabsList>
            
            {/* Select Operation tab content */}
            <TabsContent value="select" className="space-y-4">
              <div className="grid md:grid-cols-2 gap-8">
                {/* Upload card with hover and selection animations */}
                <motion.div
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  <Card 
                    className={cn(
                      "overflow-hidden border-2 transition cursor-pointer h-full",
                      // Change border and shadow when selected
                      selectedOption === 'upload' 
                        ? "border-blue-500 shadow-xl dark:border-blue-400" 
                        : "border-slate-200 dark:border-slate-700 hover:shadow-lg"
                    )}
                    onClick={() => setSelectedOption('upload')}
                  >
                    {/* Colored indicator bar at top */}
                    <div className={cn(
                      "h-1.5 w-full",
                      selectedOption === 'upload' ? "bg-gradient-to-r from-blue-500 to-indigo-500" : "bg-transparent"
                    )} />
                    <CardContent className="p-6 pt-8">
                      {/* Card header with icon and title */}
                      <div className="flex items-center mb-6">
                        <div className={cn(
                          "flex items-center justify-center h-14 w-14 rounded-full",
                          selectedOption === 'upload' ? "bg-blue-100 dark:bg-blue-900" : "bg-slate-100 dark:bg-slate-800"
                        )}>
                          <Upload className={cn(
                            "h-7 w-7", 
                            selectedOption === 'upload' ? "text-blue-600 dark:text-blue-400" : "text-slate-500 dark:text-slate-400"
                          )} />
                        </div>
                        <div className="ml-4">
                          <h3 className="text-xl font-semibold">Upload to ClickHouse</h3>
                          <p className="text-slate-500 dark:text-slate-400">CSV → Database</p>
                        </div>
                      </div>
                      {/* Feature list */}
                      <div className="space-y-3">
                        <div className="flex items-center text-sm text-slate-600 dark:text-slate-300">
                          <ChevronRight className="h-4 w-4 mr-2 text-green-500" />
                          <span>Import CSV data with column mapping</span>
                        </div>
                        <div className="flex items-center text-sm text-slate-600 dark:text-slate-300">
                          <ChevronRight className="h-4 w-4 mr-2 text-green-500" />
                          <span>Create new tables or append to existing</span>
                        </div>
                        <div className="flex items-center text-sm text-slate-600 dark:text-slate-300">
                          <ChevronRight className="h-4 w-4 mr-2 text-green-500" />
                          <span>Smart data type detection</span>
                        </div>
                      </div>
                    </CardContent>
                    {/* Footer with selected indicator */}
                    <CardFooter className="pb-6 px-6 pt-0 justify-end">
                      {selectedOption === 'upload' && (
                        <div className="flex items-center text-sm font-medium text-blue-600 dark:text-blue-400">
                          Selected <ChevronRight className="h-4 w-4 ml-1" />
                        </div>
                      )}
                    </CardFooter>
                  </Card>
                </motion.div>

                {/* Download card with hover and selection animations */}
                <motion.div
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  <Card 
                    className={cn(
                      "overflow-hidden border-2 transition cursor-pointer h-full",
                      // Change border and shadow when selected
                      selectedOption === 'download' 
                        ? "border-blue-500 shadow-xl dark:border-blue-400" 
                        : "border-slate-200 dark:border-slate-700 hover:shadow-lg"
                    )}
                    onClick={() => setSelectedOption('download')}
                  >
                    {/* Colored indicator bar at top */}
                    <div className={cn(
                      "h-1.5 w-full",
                      selectedOption === 'download' ? "bg-gradient-to-r from-blue-500 to-indigo-500" : "bg-transparent"
                    )} />
                    <CardContent className="p-6 pt-8">
                      {/* Card header with icon and title */}
                      <div className="flex items-center mb-6">
                        <div className={cn(
                          "flex items-center justify-center h-14 w-14 rounded-full",
                          selectedOption === 'download' ? "bg-blue-100 dark:bg-blue-900" : "bg-slate-100 dark:bg-slate-800"
                        )}>
                          <Download className={cn(
                            "h-7 w-7", 
                            selectedOption === 'download' ? "text-blue-600 dark:text-blue-400" : "text-slate-500 dark:text-slate-400"
                          )} />
                        </div>
                        <div className="ml-4">
                          <h3 className="text-xl font-semibold">Download from ClickHouse</h3>
                          <p className="text-slate-500 dark:text-slate-400">Database → CSV</p>
                        </div>
                      </div>
                      {/* Feature list */}
                      <div className="space-y-3">
                        <div className="flex items-center text-sm text-slate-600 dark:text-slate-300">
                          <ChevronRight className="h-4 w-4 mr-2 text-green-500" />
                          <span>Export selected tables and columns</span>
                        </div>
                        <div className="flex items-center text-sm text-slate-600 dark:text-slate-300">
                          <ChevronRight className="h-4 w-4 mr-2 text-green-500" />
                          <span>Support for multi-table JOINs</span>
                        </div>
                        <div className="flex items-center text-sm text-slate-600 dark:text-slate-300">
                          <ChevronRight className="h-4 w-4 mr-2 text-green-500" />
                          <span>Efficient streaming for large datasets</span>
                        </div>
                      </div>
                    </CardContent>
                    {/* Footer with selected indicator */}
                    <CardFooter className="pb-6 px-6 pt-0 justify-end">
                      {selectedOption === 'download' && (
                        <div className="flex items-center text-sm font-medium text-blue-600 dark:text-blue-400">
                          Selected <ChevronRight className="h-4 w-4 ml-1" />
                        </div>
                      )}
                    </CardFooter>
                  </Card>
                </motion.div>
              </div>
            </TabsContent>
            
            {/* Key Features tab content */}
            <TabsContent value="features" className="space-y-4">
              <Card>
                <CardContent className="p-6 pt-8">
                  {/* Features grid layout - 3 columns on desktop, 1 on mobile */}
                  <div className="grid md:grid-cols-3 gap-8">
                    {/* Feature 1: Bidirectional Flow */}
                    <div className="space-y-3">
                      <div className="flex items-center mb-3">
                        <div className="flex items-center justify-center h-10 w-10 rounded-full bg-blue-100 dark:bg-blue-900">
                          <RefreshCw className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div className="ml-3">
                          <h3 className="text-md font-semibold">Bidirectional Flow</h3>
                        </div>
                      </div>
                      <p className="text-sm text-slate-600 dark:text-slate-300">
                        Seamless transfer in both directions between ClickHouse databases and CSV files.
                      </p>
                    </div>
                    
                    {/* Feature 2: JWT Authentication */}
                    <div className="space-y-3">
                      <div className="flex items-center mb-3">
                        <div className="flex items-center justify-center h-10 w-10 rounded-full bg-blue-100 dark:bg-blue-900">
                          <Database className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div className="ml-3">
                          <h3 className="text-md font-semibold">JWT Authentication</h3>
                        </div>
                      </div>
                      <p className="text-sm text-slate-600 dark:text-slate-300">
                        Secure connection with JWT token support for ClickHouse authentication.
                      </p>
                    </div>
                    
                    {/* Feature 3: Schema Discovery */}
                    <div className="space-y-3">
                      <div className="flex items-center mb-3">
                        <div className="flex items-center justify-center h-10 w-10 rounded-full bg-blue-100 dark:bg-blue-900">
                          <FileText className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div className="ml-3">
                          <h3 className="text-md font-semibold">Schema Discovery</h3>
                        </div>
                      </div>
                      <p className="text-sm text-slate-600 dark:text-slate-300">
                        Automatic detection and mapping of table schemas and column types.
                      </p>
                    </div>
                    
                    {/* Feature 4: JOIN Support */}
                    <div className="space-y-3">
                      <div className="flex items-center mb-3">
                        <div className="flex items-center justify-center h-10 w-10 rounded-full bg-blue-100 dark:bg-blue-900">
                          <ArrowLeftRight className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div className="ml-3">
                          <h3 className="text-md font-semibold">JOIN Support</h3>
                        </div>
                      </div>
                      <p className="text-sm text-slate-600 dark:text-slate-300">
                        Create complex queries with multi-table joins for advanced data exports.
                      </p>
                    </div>
                    
                    {/* Feature 5: Performance Analytics */}
                    <div className="space-y-3">
                      <div className="flex items-center mb-3">
                        <div className="flex items-center justify-center h-10 w-10 rounded-full bg-blue-100 dark:bg-blue-900">
                          <BarChart4 className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div className="ml-3">
                          <h3 className="text-md font-semibold">Performance Analytics</h3>
                        </div>
                      </div>
                      <p className="text-sm text-slate-600 dark:text-slate-300">
                        Monitor transfer speed and progress with real-time visualizations.
                      </p>
                    </div>
                    
                    {/* Additional features continue... */}
                    <div className="space-y-3">
                      <div className="flex items-center mb-3">
                        <div className="flex items-center justify-center h-10 w-10 rounded-full bg-blue-100 dark:bg-blue-900">
                          <Upload className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div className="ml-3">
                          <h3 className="text-md font-semibold">Streaming Upload</h3>
                        </div>
                      </div>
                      <p className="text-sm text-slate-600 dark:text-slate-300">
                        Efficient handling of large datasets with optimized memory usage.
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>
        
        {/* Continue button - only enabled when an option is selected */}
        <div className="flex justify-center">
          <Button 
            size="lg"
            disabled={!selectedOption}
            onClick={handleContinue}
            className={cn(
              "transition-all duration-300 flex items-center gap-2 px-8",
              selectedOption ? "opacity-100" : "opacity-50 cursor-not-allowed"
            )}
          >
            Continue
            <ArrowRight className="h-4 w-4 ml-1" />
          </Button>
        </div>
        
        {/* Information text below button */}
        {!selectedOption && (
          <p className="text-sm text-center mt-4 text-slate-500 dark:text-slate-400">
            Select an operation to continue
          </p>
        )}
      </div>
    </div>
  );
}
