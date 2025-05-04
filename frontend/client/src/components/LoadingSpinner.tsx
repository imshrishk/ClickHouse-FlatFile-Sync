import { cn } from "@/lib/utils";

type LoadingSpinnerProps = {
  size?: "sm" | "md" | "lg";
  className?: string;
  text?: string;
};

export function LoadingSpinner({ 
  size = "md", 
  className,
  text
}: LoadingSpinnerProps) {
  const sizeClass = {
    sm: "h-4 w-4 border-2",
    md: "h-8 w-8 border-3",
    lg: "h-12 w-12 border-4",
  };

  return (
    <div className="flex flex-col items-center justify-center gap-3">
      <div
        className={cn(
          "animate-spin rounded-full border-t-transparent border-primary", 
          sizeClass[size],
          className
        )}
      />
      {text && (
        <p className="text-sm text-muted-foreground animate-pulse">{text}</p>
      )}
    </div>
  );
}

export function FullPageLoader({ message = "Loading..." }) {
  return (
    <div className="fixed inset-0 flex flex-col items-center justify-center bg-background/80 backdrop-blur-sm z-50">
      <div className="relative">
        <div className="h-24 w-24 rounded-full border-t-4 border-b-4 border-primary animate-spin"></div>
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="h-16 w-16 rounded-full border-t-4 border-b-4 border-primary/30 animate-spin animation-delay-150"></div>
        </div>
        <div className="absolute inset-0 flex items-center justify-center">
          <div className="h-8 w-8 rounded-full border-t-4 border-b-4 border-primary/70 animate-spin animation-delay-300"></div>
        </div>
      </div>
      <p className="mt-6 text-lg font-medium text-foreground">{message}</p>
    </div>
  );
}

export function DataLoader({ className }: { className?: string }) {
  return (
    <div className={cn("flex flex-col items-center justify-center p-8", className)}>
      <div className="flex space-x-2 mb-4">
        <div className="w-3 h-10 bg-blue-500 rounded animate-wave-1"></div>
        <div className="w-3 h-10 bg-blue-500 rounded animate-wave-2"></div>
        <div className="w-3 h-10 bg-blue-500 rounded animate-wave-3"></div>
        <div className="w-3 h-10 bg-blue-500 rounded animate-wave-4"></div>
        <div className="w-3 h-10 bg-blue-500 rounded animate-wave-5"></div>
      </div>
      <p className="text-sm text-muted-foreground">Processing data...</p>
    </div>
  );
}
