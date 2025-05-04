import { 
  AlertCircle, 
  CheckCircle2, 
  Clock, 
  Database, 
  FileWarning,
  Info
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";

export type StatusType = "success" | "error" | "warning" | "info" | "pending" | "connecting";

type StatusCardProps = {
  type: StatusType;
  title: string;
  message?: string;
  progress?: number;
  className?: string;
};

export function StatusCard({ type, title, message, progress, className }: StatusCardProps) {
  const icons = {
    success: CheckCircle2,
    error: AlertCircle,
    warning: FileWarning,
    info: Info,
    pending: Clock,
    connecting: Database
  };

  const Icon = icons[type];
  
  const statusStyles = {
    success: "border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-900/20",
    error: "border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-900/20",
    warning: "border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-900/20",
    info: "border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-900/20",
    pending: "border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/20",
    connecting: "border-violet-200 bg-violet-50 dark:border-violet-800 dark:bg-violet-900/20",
  };
  
  const iconStyles = {
    success: "text-green-600 dark:text-green-400",
    error: "text-red-600 dark:text-red-400",
    warning: "text-amber-600 dark:text-amber-400",
    info: "text-blue-600 dark:text-blue-400",
    pending: "text-slate-600 dark:text-slate-400",
    connecting: "text-violet-600 dark:text-violet-400",
  };
  
  const progressStyles = {
    success: "bg-green-600 dark:bg-green-400",
    error: "bg-red-600 dark:bg-red-400",
    warning: "bg-amber-600 dark:bg-amber-400",
    info: "bg-blue-600 dark:bg-blue-400",
    pending: "bg-slate-600 dark:bg-slate-400",
    connecting: "bg-violet-600 dark:bg-violet-400",
  };

  return (
    <Card className={cn("border-2 overflow-hidden", statusStyles[type], className)}>
      {typeof progress === "number" && (
        <div className="h-1 w-full bg-background">
          <div 
            className={cn("h-full transition-all duration-300", progressStyles[type])} 
            style={{ width: `${progress}%` }}
          />
        </div>
      )}
      <CardContent className="p-4 flex items-start gap-3">
        <div className={cn("mt-1 flex-shrink-0", iconStyles[type])}>
          <Icon className="h-5 w-5" />
        </div>
        <div className="flex-grow">
          <h4 className="text-sm font-semibold text-foreground mb-1">{title}</h4>
          {message && <p className="text-sm text-muted-foreground">{message}</p>}
          
          {type === "connecting" && (
            <div className="mt-3">
              <Progress value={progress} className="h-1.5" />
              <p className="text-xs text-muted-foreground mt-1">
                Connection in progress...
              </p>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

export function ConnectionStatus({ 
  isConnected, 
  isConnecting, 
  error, 
  serverInfo, 
  className 
}: { 
  isConnected: boolean; 
  isConnecting: boolean; 
  error?: string; 
  serverInfo?: string;
  className?: string;
}) {
  if (isConnecting) {
    return (
      <StatusCard
        type="connecting"
        title="Connecting to ClickHouse"
        message="Please wait while we establish a connection..."
        progress={65}
        className={className}
      />
    );
  }

  if (error) {
    return (
      <StatusCard
        type="error"
        title="Connection failed"
        message={error}
        className={className}
      />
    );
  }

  if (isConnected) {
    return (
      <StatusCard
        type="success"
        title="Connected to ClickHouse"
        message={serverInfo || "Connection established successfully"}
        className={className}
      />
    );
  }

  return (
    <StatusCard
      type="info"
      title="Connection required"
      message="Please enter your ClickHouse connection details"
      className={className}
    />
  );
}

export function ProgressStatus({ 
  progress, 
  processingLabel = "Processing", 
  completedLabel = "Completed", 
  className 
}: { 
  progress: number; 
  processingLabel?: string;
  completedLabel?: string;
  className?: string;
}) {
  const isComplete = progress >= 100;
  
  return (
    <Card className={cn(
      "border overflow-hidden", 
      isComplete 
        ? "border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-900/20" 
        : "border-blue-200 bg-blue-50 dark:border-blue-900 dark:bg-blue-900/20",
      className
    )}>
      <CardContent className="p-4">
        <div className="flex justify-between mb-2">
          <h4 className="text-sm font-medium">
            {isComplete ? completedLabel : processingLabel}
          </h4>
          <span className="text-sm font-medium">{Math.round(progress)}%</span>
        </div>
        <Progress value={progress} className="h-2" />
      </CardContent>
    </Card>
  );
}
