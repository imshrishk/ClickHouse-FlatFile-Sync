import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { ConnectionConfig, connectionSchema } from '@shared/schema';
import { testConnection } from '@/lib/clickhouse';
import { useToast } from '@/hooks/use-toast';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { CheckCircle, XCircle, Loader2 } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

interface ConnectionFormProps {
  onSubmit: (data: ConnectionConfig) => void;
  title: string;
}

export default function ConnectionForm({ onSubmit, title }: ConnectionFormProps) {
  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const { toast } = useToast();
  
  const form = useForm<ConnectionConfig>({
    resolver: zodResolver(connectionSchema),
    defaultValues: {
      protocol: 'http',
      host: '',
      port: '8123',
      username: '',
      database: '',
      authType: 'password',
      password: '',
      jwt: '',
    },
  });
  
  const authType = form.watch('authType');
  const onTestConnection = async () => {
    try {
      const values = form.getValues();
      setIsTesting(true);
      setTestResult(null);
      
      const isConnected = await testConnection(values);
      
      if (isConnected) {
        setTestResult({ success: true, message: 'Connection successful! You can now continue.' });
      } else {
        setTestResult({ success: false, message: 'Connection failed. Please check your credentials and try again.' });
      }
    } catch (error) {
      setTestResult({ success: false, message: error instanceof Error ? error.message : 'Connection test failed' });
    } finally {
      setIsTesting(false);
    }
  };

  const handleSubmit = (data: ConnectionConfig) => {
    onSubmit(data);
  };

  return (
    <div className="max-w-4xl mx-auto">
      <div className="bg-white shadow-md rounded-lg border border-slate-200 p-6 mb-8">
        <h3 className="text-lg font-semibold mb-4 text-slate-800">Connection Configuration</h3>
        
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <div className="grid md:grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="protocol"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Protocol</FormLabel>
                    <Select onValueChange={field.onChange} defaultValue={field.value}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="Select protocol" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="http">HTTP</SelectItem>
                        <SelectItem value="https">HTTPS</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
              
              <FormField
                control={form.control}
                name="port"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Port</FormLabel>
                    <FormControl>
                      <Input placeholder="8123" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            
            <FormField
              control={form.control}
              name="host"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Host</FormLabel>
                  <FormControl>
                    <Input placeholder="localhost" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            
            <FormField
              control={form.control}
              name="username"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Username</FormLabel>
                  <FormControl>
                    <Input placeholder="default" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="database"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Database</FormLabel>
                  <FormControl>
                    <Input placeholder="default" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            
            <div className="space-y-2">
              <FormLabel>Authentication Type</FormLabel>
              <div className="flex space-x-2">
                <Button
                  type="button"
                  variant={authType === "password" ? "default" : "outline"}
                  className={authType === "password" ? "bg-blue-600 hover:bg-blue-700" : ""}
                  onClick={() => form.setValue("authType", "password")}
                >
                  Password
                </Button>
                <Button
                  type="button"
                  variant={authType === "jwt" ? "default" : "outline"}
                  className={authType === "jwt" ? "bg-blue-600 hover:bg-blue-700" : ""}
                  onClick={() => form.setValue("authType", "jwt")}
                >
                  JWT Token
                </Button>
              </div>
            </div>
            
            {authType === "password" ? (
              <FormField
                key="password"
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input type="password" placeholder="Enter your Password" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            ) : (
              <FormField
                key="jwt"
                control={form.control}
                name="jwt"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>JWT Token</FormLabel>
                    <FormControl>
                      <Input type="text" placeholder="Enter your JWT token" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}
            
            {testResult && (
              <Alert className={testResult.success ? "bg-green-50 border-green-200" : "bg-red-50 border-red-200"}>
                <div className="flex items-center">
                  {testResult.success ? (
                    <CheckCircle className="h-4 w-4 text-green-500 mr-2" />
                  ) : (
                    <XCircle className="h-4 w-4 text-red-500 mr-2" />
                  )}
                  <AlertDescription className={testResult.success ? "text-green-800" : "text-red-800"}>
                    {testResult.message}
                  </AlertDescription>
                </div>
              </Alert>
            )}
            
            <div className="pt-2 flex flex-wrap gap-2">
              <Button 
                type="button" 
                variant="outline" 
                onClick={onTestConnection}
                disabled={isTesting}
              >
                {isTesting ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Testing
                  </>
                ) : (
                  'Test Connection'
                )}
              </Button>
              <Button type="submit" className="bg-blue-600 hover:bg-blue-700">Continue</Button>
            </div>
          </form>
        </Form>
      </div>
    </div>
  );
}
