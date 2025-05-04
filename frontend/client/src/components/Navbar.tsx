import React, { useState } from 'react';
import { Link, useLocation } from 'wouter';
import { 
  Database, 
  Menu, 
  X, 
  Upload, 
  Download, 
  Home, 
  Github, 
  Settings
} from 'lucide-react';
import { Button } from './ui/button';
import { cn } from '@/lib/utils';
import { ThemeSwitcher, useThemeContext } from './ui/theme-provider';
import { motion } from 'framer-motion';
import {
  Sheet,
  SheetContent,
  SheetTrigger,
} from '@/components/ui/sheet';

export default function Navbar() {
  const [isOpen, setIsOpen] = useState(false);
  const [currentLocation, setLocation] = useLocation();
  const { theme } = useThemeContext();
  
  const links = [
    { href: '/', label: 'Home', icon: Home },
    { href: '/upload', label: 'Upload', icon: Upload },
    { href: '/download', label: 'Download', icon: Download },
  ];

  const isActive = (path: string) => currentLocation === path;

  return (
    <header className="sticky top-0 z-50 w-full border-b border-slate-200 dark:border-slate-700 bg-white/80 backdrop-blur-md dark:bg-slate-900/80">
      <div className="container mx-auto px-4 h-16 flex items-center justify-between">
        {/* Logo & Branding */}
        <div className="flex items-center gap-2">
          <Link href="/">
            <motion.div 
              className="flex items-center gap-2 cursor-pointer"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
            >
              <div className="flex items-center justify-center h-9 w-9 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 text-white shadow-md">
                <Database className="h-5 w-5" />
              </div>
              <span className="font-bold text-xl bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400 hidden sm:inline-block">
                ClickSync
              </span>
            </motion.div>
          </Link>
        </div>

        {/* Desktop Navigation */}
        <nav className="hidden md:flex items-center gap-1">
          {links.map((link) => {
            const Icon = link.icon;
            return (
              <Link key={link.href} href={link.href}>
                <Button
                  variant={isActive(link.href) ? "default" : "ghost"}
                  size="sm"
                  className={cn(
                    "gap-2",
                    isActive(link.href) 
                      ? "bg-blue-50 text-blue-600 hover:bg-blue-100 hover:text-blue-700 dark:bg-slate-800 dark:text-blue-400" 
                      : "text-slate-600 dark:text-slate-300"
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {link.label}
                </Button>
              </Link>
            );
          })}
        </nav>

        {/* Theme Switcher & Mobile Menu */}
        <div className="flex items-center gap-4">
          <div className="hidden md:block">
            <ThemeSwitcher />
          </div>
          
          <div className="flex md:hidden">
            <Sheet open={isOpen} onOpenChange={setIsOpen}>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" className="md:hidden">
                  <Menu className="h-5 w-5" />
                  <span className="sr-only">Toggle menu</span>
                </Button>
              </SheetTrigger>
              <SheetContent side="right" className="w-[300px] sm:w-[400px]">
                <div className="px-2">
                  <div className="flex items-center justify-between mb-8 mt-4">
                    <div className="flex items-center gap-2">
                      <div className="flex items-center justify-center h-8 w-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 text-white shadow-sm">
                        <Database className="h-4 w-4" />
                      </div>
                      <span className="font-semibold text-lg bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-indigo-600 dark:from-blue-400 dark:to-indigo-400">
                        ClickSync
                      </span>
                    </div>
                    <ThemeSwitcher />
                  </div>
                  
                  <nav className="flex flex-col space-y-4">
                    {links.map((link) => {
                      const Icon = link.icon;
                      return (
                        <Link key={link.href} href={link.href}>
                          <Button
                            variant={isActive(link.href) ? "default" : "ghost"}
                            size="lg"
                            className={cn(
                              "w-full justify-start gap-3 px-4",
                              isActive(link.href) 
                                ? "bg-blue-50 text-blue-600 hover:bg-blue-100 hover:text-blue-700 dark:bg-slate-800 dark:text-blue-400" 
                                : "text-slate-600 dark:text-slate-300"
                            )}
                            onClick={() => setIsOpen(false)}
                          >
                            <Icon className="h-5 w-5" />
                            {link.label}
                          </Button>
                        </Link>
                      );
                    })}
                  </nav>
                  
                  <div className="mt-8 pt-8 border-t border-slate-200 dark:border-slate-700">
                    <div className="flex flex-col space-y-3">
                      <Button variant="outline" size="sm" className="justify-start gap-3">
                        <Github className="h-4 w-4" />
                        View on GitHub
                      </Button>
                      <Button variant="outline" size="sm" className="justify-start gap-3">
                        <Settings className="h-4 w-4" />
                        Settings
                      </Button>
                    </div>
                  </div>
                </div>
              </SheetContent>
            </Sheet>
          </div>
        </div>
      </div>
    </header>
  );
}
