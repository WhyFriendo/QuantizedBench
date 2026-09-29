import React, { createContext, useContext, ReactNode, useRef } from 'react';
import { stopDownloading } from '../fsUtils';

export interface DownloadingContextType {
  cancelAllDownloads: () => void;
  storeJob: (jobId: number) => void;
  removeJob: (jobId: number) => void;
}

const DownloadingContext = createContext<DownloadingContextType | undefined>(undefined);

export const DownloadingProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const ref = useRef<number[]>([]);

  const cancelAllDownloads = () => {
    ref.current.forEach((id) => {
      try { stopDownloading(id); } catch (error) { }
    });
  };

  const storeJob = (jobId: number) => {
    ref.current.push(jobId);
  };

  const removeJob = (jobId: number) => {
    ref.current = ref.current.filter((id) => id !== jobId);
  };

  return (
    <DownloadingContext.Provider value={{ cancelAllDownloads, storeJob, removeJob }}>
      {children}
    </DownloadingContext.Provider>
  );
};

export const useDownloadingContext = () => {
  const context = useContext(DownloadingContext);
  if (!context) {
    throw new Error('useDownloadingContext must be used within a DownloadingProvider');
  }
  return context;
};
