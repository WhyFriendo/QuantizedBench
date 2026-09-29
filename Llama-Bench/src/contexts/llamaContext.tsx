import React, { createContext, useState, useContext, ReactNode } from 'react';
import type { LlamaContext as LLlamaContext } from 'llama.rn';

export interface LlamaContextType {
  context: LLlamaContext | undefined;
  setContext: React.Dispatch<React.SetStateAction<LLlamaContext | undefined>>;
}

const LlamaContext = createContext<LlamaContextType | undefined>(undefined);

export const LlamaProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [context, setContext] = useState<LLlamaContext | undefined>(undefined);

  return (
    <LlamaContext.Provider value={{ context, setContext }}>
      {children}
    </LlamaContext.Provider>
  );
};

export const useLlamaContext = () => {
  const context = useContext(LlamaContext);
  if (!context) {
    throw new Error('useLlamaContext must be used within a LlamaProvider');
  }
  return context;
};
