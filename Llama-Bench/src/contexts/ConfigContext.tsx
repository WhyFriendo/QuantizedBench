import React, { createContext, useContext, useState, useEffect, ReactNode, useCallback } from 'react';
import { useSQLiteContext } from './SQLiteContext';
import baseConfig from '../config';
import { Config } from '../types/Config';

interface ConfigContextType {
  config: Config;
  reloadConfig: () => Promise<void>;
}

const ConfigContext = createContext<ConfigContextType | null>(null);

export const ConfigProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const sqlite = useSQLiteContext();
  const [config, setConfig] = useState<Config>(baseConfig);

  const reloadConfig = useCallback(async () => {
    try {
      if (!sqlite) {
        console.warn('SQLite context not available yet, using base config');
        setConfig(baseConfig);
        return;
      }

      const selectedRunIndex = await sqlite.getSelectedRun();
      const selectedRun = baseConfig.BENCHMARK_SETTINGS.RUNS[selectedRunIndex];
      
      if (!selectedRun) {
        console.warn('Invalid selected run index, using base config');
        setConfig({...baseConfig, BENCHMARK_SETTINGS: { ...baseConfig.BENCHMARK_SETTINGS, RUNS: [baseConfig.BENCHMARK_SETTINGS.RUNS[0]] }});
        return;
      }

      const autoDeleteModels = await sqlite.getAutoDeleteModels();

      const updatedConfig: Config = {
        ...baseConfig,
        BENCHMARK_SETTINGS: {
          ...baseConfig.BENCHMARK_SETTINGS,
          RUNS: [selectedRun],
          AUTO_DELETE_MODELS: autoDeleteModels,
        },
      };
      
      setConfig(updatedConfig);
    } catch (error) {
      console.error('Error reloading config:', error);
      setConfig(baseConfig);
    }
  }, [sqlite]);

  useEffect(() => {
    const loadConfigWithRetry = async () => {
      await new Promise(resolve => setTimeout(resolve, 100));
      await reloadConfig();
    };
    
    loadConfigWithRetry();
  }, [reloadConfig]);

  const contextValue: ConfigContextType = {
    config,
    reloadConfig,
  };

  return (
    <ConfigContext.Provider value={contextValue}>
      {children}
    </ConfigContext.Provider>
  );
};

export const useConfig = (): ConfigContextType => {
  const context = useContext(ConfigContext);
  if (!context) {
    throw new Error('useConfig must be used within a ConfigProvider');
  }
  return context;
}; 