import React, { createContext, useState, useContext, ReactNode } from 'react';

export interface BenchmarkingContextType {
  isBenchmarking: boolean;
  setIsBenchmarking: React.Dispatch<React.SetStateAction<boolean>>;
}

const BenchmarkingContext = createContext<BenchmarkingContextType | undefined>(undefined);

export const BenchmarkingProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [isBenchmarking, setIsBenchmarking] = useState<boolean>(false);

  return (
    <BenchmarkingContext.Provider value={{ isBenchmarking, setIsBenchmarking }}>
      {children}
    </BenchmarkingContext.Provider>
  );
};

export const useBenchmarkingContext = () => {
  const context = useContext(BenchmarkingContext);
  if (!context) {
    throw new Error('useBenchmarkingContext must be used within a BenchmarkingProvider');
  }
  return context;
};
