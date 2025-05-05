import { Button } from "../components/ui/button";
import { useState } from "react";
import { SPRING_BOOT_URL } from "../lib/queryClient";

export default function TestDownloadPage() {
  const [message, setMessage] = useState<string>("");
  const [error, setError] = useState<string>("");

  const testSimpleDownload = () => {
    setMessage("Starting simple download...");
    setError("");
    
    // Direct window location approach - most compatible
    window.location.href = `${SPRING_BOOT_URL}/clickhouse/simple-download`;
  };

  const testFetchDownload = async () => {
    setMessage("Testing fetch download...");
    setError("");
    
    try {
      const response = await fetch(`${SPRING_BOOT_URL}/clickhouse/simple-download`);
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Error: ${response.status} ${response.statusText} - ${errorText}`);
      }
      
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'test_data.csv';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
      
      setMessage("Fetch download completed!");
    } catch (err) {
      console.error("Fetch download error:", err);
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  return (
    <div className="max-w-7xl mx-auto p-4">
      <h1 className="text-2xl font-bold mb-6">Download Testing Page</h1>
      
      <div className="space-y-8">
        <div className="p-6 border rounded-lg">
          <h2 className="text-xl font-semibold mb-4">Test Simple Download</h2>
          <p className="mb-4">
            This uses a direct link to the backend's simple download endpoint.
          </p>
          <Button onClick={testSimpleDownload} className="mb-4">
            Download Test File (Direct Link)
          </Button>
        </div>
        
        <div className="p-6 border rounded-lg">
          <h2 className="text-xl font-semibold mb-4">Test Fetch API Download</h2>
          <p className="mb-4">
            This uses the Fetch API to download the file.
          </p>
          <Button onClick={testFetchDownload} className="mb-4">
            Download Test File (Fetch API)
          </Button>
        </div>
        
        {message && (
          <div className="p-4 bg-green-100 text-green-800 rounded-lg">
            {message}
          </div>
        )}
        
        {error && (
          <div className="p-4 bg-red-100 text-red-800 rounded-lg">
            {error}
          </div>
        )}
      </div>
    </div>
  );
}
