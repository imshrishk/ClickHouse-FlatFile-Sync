import { useState, useEffect, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Loader2, Info } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';

interface ProgressButtonProps {
  isLoading: boolean;
  onClick?: () => void;
  type?: "button" | "submit" | "reset";
  disabled?: boolean;
  className?: string;
  progressDuration?: number; // in milliseconds
  loadingText?: string;
  icon?: React.ReactNode;
  children: React.ReactNode;
  fileSize?: number; // size in bytes
  totalSize?: number; // total size in bytes for download progress
  showSize?: boolean; // whether to show size information
  actualProgress?: {
    loaded: number;
    total: number;
    percentage: number
  }; // real progress from XHR request
  lineCount?: number; // number of lines/rows processed
}

export default function ProgressButton({
  isLoading,
  onClick,
  type = "button",
  disabled = false,
  className = "",
  progressDuration = 5000, // default 5 seconds
  loadingText = "Processing...",
  icon,
  children,
  fileSize,
  totalSize,
  showSize = false,
  actualProgress,
  lineCount
}: ProgressButtonProps) {
  const [progress, setProgress] = useState(0);
  const [currentSize, setCurrentSize] = useState(0);
  const [completed, setCompleted] = useState(false);
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  // Format file size to human-readable format
  const formatFileSize = (sizeInBytes: number | undefined) => {
    if (!sizeInBytes) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let size = sizeInBytes;
    let unitIndex = 0;

    while (size >= 1024 && unitIndex < units.length - 1) {
      size /= 1024;
      unitIndex++;
    }

    return `${size.toFixed(1)} ${units[unitIndex]}`;
  };

  // Update progress when actual progress changes
  useEffect(() => {
    if (actualProgress) {
      // Clear any simulated progress intervals
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }

      // Set the real progress
      setProgress(actualProgress.percentage);
      setCurrentSize(actualProgress.loaded);
    }
  }, [actualProgress]);

  // Reset progress when loading state changes
  useEffect(() => {
    if (isLoading) {
      // Only use simulated progress if actualProgress is not provided
      if (!actualProgress) {
        setProgress(0);
        setCurrentSize(0);
        setCompleted(false);

        intervalRef.current = setInterval(() => {
          setProgress(prev => {
            // Only increase to 95% max during automatic progression
            // The final 5% will be filled when the operation actually completes
            if (prev >= 95) {
              if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
              }
              return 95;
            }
            return prev + 1;
          });

          // Simulate progress for file size if totalSize is provided
          if (totalSize && fileSize) {
            setCurrentSize(prev => {
              const progressRatio = progress / 100;
              const targetSize = Math.min(fileSize * progressRatio, fileSize);
              return targetSize;
            });
          }
        }, progressDuration / 100);
      }

      return () => {
        if (intervalRef.current) {
          clearInterval(intervalRef.current);
          intervalRef.current = null;
        }
      };
    } else {
      // When loading completes, set to 100%
      if (progress > 0) {
        setProgress(100);
        if (fileSize) setCurrentSize(fileSize);
        setCompleted(true);

        // Reset after animation completes
        const timeout = setTimeout(() => {
          setProgress(0);
          // Don't reset currentSize to keep showing the final size
        }, 2000);
        return () => clearTimeout(timeout);
      }
    }
  }, [isLoading, progressDuration, fileSize, totalSize, progress, actualProgress]);

  // Create gradient color for progress bar
  const progressBarColor = isLoading
    ? "bg-gradient-to-r from-blue-200 via-blue-400 to-blue-600"
    : completed ? "bg-green-500" : "bg-blue-100";

  return (
    <div className="flex flex-col space-y-2">
      {/* Show progress section when loading or immediately after completion */}
      {isLoading && (
        <div className="mb-2 p-3 border rounded-md bg-slate-50">
          <div className="mb-2 flex justify-between items-center text-xs text-slate-600">
            <span className="font-medium">{formatFileSize(currentSize)} / {formatFileSize(fileSize)}</span>
            <span className="font-semibold">{(progress).toFixed(0)}%</span>
          </div>

          <div className="h-3 w-full bg-slate-200 rounded-full overflow-hidden">
            <div
              className="h-3 transition-all duration-300 bg-blue-500 rounded-full"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      )}

      {/* Show line count if available and completed */}
      {completed && lineCount !== undefined && (
        <div className="mt-2 flex items-center text-xs text-slate-600">
          <Info className="h-3.5 w-3.5 mr-1 text-blue-500" />
          <span className="font-medium">{lineCount.toLocaleString()} lines ingested</span>
        </div>
      )}

      <Button
        type={type}
        onClick={onClick}
        disabled={disabled || isLoading}
        className={cn(
          "bg-blue-600 hover:bg-blue-700 relative overflow-hidden",
          className
        )}
      >
        {isLoading ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            {loadingText}
          </>
        ) : (
          <>
            {icon && <span className="mr-2">{icon}</span>}
            {children}
          </>
        )}
      </Button>
    </div>
  );
}
