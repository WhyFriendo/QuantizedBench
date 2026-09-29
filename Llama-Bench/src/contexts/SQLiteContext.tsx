import React, {
  createContext,
  useContext,
  useEffect,
  useState,
  ReactNode,
} from 'react';
import { View, Text, StyleSheet, ActivityIndicator } from 'react-native';
import SQLite, { SQLiteDatabase, ResultSet } from 'react-native-sqlite-storage';
import uuid from 'react-native-uuid';
import { checkFileExists, getLocalPath } from '../fsUtils';

export interface UserData {
  id: string;
}

export interface Benchmark {
  id: number;
  family: string;
  model_name: string;
  data: string;
  status: string;
}

export interface SQLiteContextType {
  insertUserData: (id: string) => Promise<void>;
  selectUserData: () => Promise<UserData[]>;
  insertBenchmark: (
    family: string,
    model_name: string,
    data: string,
    status: string,
  ) => Promise<void>;
  deleteBenchmarkByModelAndFamilyName: (family: string, model_name: string) => Promise<void>;
  deleteBenchmarksByFamilyName: (family: string) => Promise<void>;
  selectBenchmarks: (family: string) => Promise<Benchmark[]>;
  saveSelectedRun: (selectedRun: number) => Promise<void>;
  getSelectedRun: () => Promise<number>;
  saveAutoDeleteModels: (autoDelete: boolean) => Promise<void>;
  getAutoDeleteModels: () => Promise<boolean>;
  saveBenchmarkState: (queueData: string, currentModelData: string, isBenchmarking: boolean, needsRestart: boolean, restartReason: string) => Promise<void>;
  getBenchmarkState: () => Promise<{
    queueData: string;
    currentModelData: string;
    isBenchmarking: boolean;
    needsRestart: boolean;
    restartReason: string;
  } | null>;
  clearBenchmarkState: () => Promise<void>;
}

const SQLiteContext = createContext<SQLiteContextType | null>(null);

const DatabaseLoadingScreen: React.FC<{ error: string | null }> = ({ error }) => {
  if (error) {
    return (
      <View style={styles.container}>
        <Text style={styles.errorTitle}>Database Error</Text>
        <Text style={styles.errorText}>{error}</Text>
        <Text style={styles.errorSubtext}>Please restart the application</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <ActivityIndicator size="large" color="#0A84FF" />
      <Text style={styles.loadingText}>Initializing...</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#1a1a1a',
    padding: 20,
  },
  loadingText: {
    marginTop: 16,
    fontSize: 16,
    color: '#ffffff',
    textAlign: 'center',
  },
  errorTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#FF453A',
    marginBottom: 8,
    textAlign: 'center',
  },
  errorText: {
    fontSize: 16,
    color: '#ffffff',
    textAlign: 'center',
    marginBottom: 8,
  },
  errorSubtext: {
    fontSize: 14,
    color: '#8E8E93',
    textAlign: 'center',
  },
});

export const SQLiteProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [db, setDb] = useState<SQLiteDatabase | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);
  const [initError, setInitError] = useState<string | null>(null);

  useEffect(() => {
    const initDatabase = async () => {
      try {
        console.log('Initializing SQLite database...');
        const fileExists = await checkFileExists('db.sql');
        const database = await SQLite.openDatabase({
          name: getLocalPath('db.sql'),
          location: 'default',
        });

        setDb(database);

        if (!fileExists) {
          console.log('Database file does not exist, initializing...');
          await createTables(database);
          await populateUserData(database);
        } else {
          console.log('Database file exists, checking tables...');
          await createTables(database);
        }
        
        console.log('SQLite database initialized successfully');
        setIsInitialized(true);
      } catch (error) {
        console.error('Error initializing database:', error);
        setInitError(error instanceof Error ? error.message : 'Unknown database error');
      }
    };

    initDatabase();

    return () => {
      db?.close?.();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const createTables = async (database: SQLiteDatabase) => {
    try {
      await executeSql(
        database,
        `
        CREATE TABLE IF NOT EXISTS user_data (
          id TEXT PRIMARY KEY
        );
      `
      );

      await executeSql(
        database,
        `
        CREATE TABLE IF NOT EXISTS benchmark (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          family TEXT,
          model_name TEXT,
          data TEXT,
          status TEXT
        );
      `
      );

      await executeSql(
        database,
        `
        CREATE TABLE IF NOT EXISTS settings (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          key TEXT UNIQUE,
          value TEXT
        );
      `
      );

      await executeSql(
        database,
        `
        CREATE TABLE IF NOT EXISTS benchmark_state (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          queue_data TEXT,
          current_model_data TEXT,
          is_benchmarking BOOLEAN,
          needs_restart BOOLEAN,
          restart_reason TEXT,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
      `
      );

      console.log('Tables created successfully.');
    } catch (error) {
      console.error('Error creating tables:', error);
    }
  };

  const populateUserData = async (database: SQLiteDatabase) => {
    try {
      await executeSql(database, 'DELETE FROM user_data');
      await executeSql(database, 'INSERT INTO user_data (id) VALUES (?)', [uuid.v4()]
      );

      console.log('User data populated successfully.');
    } catch (error) {
      console.error('Error populating user data:', error);
    }
  };

  const executeSql = (
    database: SQLiteDatabase | null,
    sql: string,
    params: any[] = []
  ): Promise<ResultSet> => {
    if (!database) throw new Error('Database is not initialized');
    return new Promise((resolve, reject) => {
      database.transaction((txn) => {
        txn.executeSql(
          sql,
          params,
          (_, result) => resolve(result),
          (_, error) => {
            reject(error);
            return false;
          }
        );
      });
    });
  };

  const insertUserData = async (id: string): Promise<void> => {
    await executeSql(db, 'INSERT INTO user_data (id) VALUES (?)', [id]);
  };

  const selectUserData = async (): Promise<UserData[]> => {
    const result = await executeSql(db, 'SELECT * FROM user_data');
    return result.rows.raw() as UserData[];
  };

  const insertBenchmark = async (
    family: string,
    model_name: string,
    data: string,
    status: string,
  ): Promise<void> => {
    await executeSql(
      db,
      'INSERT INTO benchmark (family, model_name, data, status) VALUES (?, ?, ?, ?)',
      [family, model_name, data, status]
    );
  };

  const selectBenchmarks = async (family: string): Promise<Benchmark[]> => {
    const result = await executeSql(
      db,
      'SELECT * FROM benchmark WHERE family = ?',
      [family]
    );
    return result.rows.raw() as Benchmark[];
  };

  const deleteBenchmarkByModelAndFamilyName = async (family: string, model_name: string): Promise<void> => {
    await executeSql(
      db,
      'DELETE FROM benchmark WHERE family = ? AND model_name = ?',
      [family, model_name]
    );
  };

  const deleteBenchmarksByFamilyName = async (family: string): Promise<void> => {
    await executeSql(
      db,
      'DELETE FROM benchmark WHERE family = ?',
      [family]
    );
  };

  const saveSelectedRun = async (selectedRun: number): Promise<void> => {
    if (!db) {
      console.warn('Database not initialized yet, cannot save selected run');
      return;
    }

    const value = selectedRun.toString();
    await executeSql(
      db,
      'INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)',
      ['selectedRun', value]
    );
  };

  const getSelectedRun = async (): Promise<number> => {
    try {
      if (!db) {
        console.warn('Database not initialized yet, returning default run');
        return 0;
      }

      const result = await executeSql(
        db,
        'SELECT value FROM settings WHERE key = ?',
        ['selectedRun']
      );
      
      if (result.rows.length > 0) {
        const value = result.rows.item(0).value;
        return parseInt(value, 10);
      }
      
      return 0;
    } catch (error) {
      console.error('Error getting selected run:', error);
      return 0;
    }
  };

  const saveAutoDeleteModels = async (autoDelete: boolean): Promise<void> => {
    if (!db) {
      console.warn('Database not initialized yet, cannot save auto delete setting');
      return;
    }

    const value = autoDelete.toString();
    await executeSql(
      db,
      'INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)',
      ['autoDeleteModels', value]
    );
  };

  const getAutoDeleteModels = async (): Promise<boolean> => {
    try {
      if (!db) {
        console.warn('Database not initialized yet, returning default auto delete setting');
        return true;
      }

      const result = await executeSql(
        db,
        'SELECT value FROM settings WHERE key = ?',
        ['autoDeleteModels']
      );
      
      if (result.rows.length > 0) {
        const value = result.rows.item(0).value;
        return value === 'true';
      }
      
      return true;
    } catch (error) {
      console.error('Error getting auto delete setting:', error);
      return true;
    }
  };

  const saveBenchmarkState = async (
    queueData: string,
    currentModelData: string,
    isBenchmarking: boolean,
    needsRestart: boolean,
    restartReason: string
  ): Promise<void> => {
    if (!db) {
      console.warn('Database not initialized yet, cannot save benchmark state');
      return;
    }

    await executeSql(db, 'DELETE FROM benchmark_state');
    
    await executeSql(
      db,
      'INSERT INTO benchmark_state (queue_data, current_model_data, is_benchmarking, needs_restart, restart_reason) VALUES (?, ?, ?, ?, ?)',
      [queueData, currentModelData, isBenchmarking ? 1 : 0, needsRestart ? 1 : 0, restartReason]
    );
  };

  const getBenchmarkState = async (): Promise<{
    queueData: string;
    currentModelData: string;
    isBenchmarking: boolean;
    needsRestart: boolean;
    restartReason: string;
  } | null> => {
    try {
      if (!db) {
        console.warn('Database not initialized yet, returning null benchmark state');
        return null;
      }

      const result = await executeSql(db, 'SELECT * FROM benchmark_state ORDER BY created_at DESC LIMIT 1');
      
      if (result.rows.length > 0) {
        const row = result.rows.item(0);
        return {
          queueData: row.queue_data || '',
          currentModelData: row.current_model_data || '',
          isBenchmarking: Boolean(row.is_benchmarking),
          needsRestart: Boolean(row.needs_restart),
          restartReason: row.restart_reason || '',
        };
      }
      
      return null;
    } catch (error) {
      console.error('Error getting benchmark state:', error);
      return null;
    }
  };

  const clearBenchmarkState = async (): Promise<void> => {
    if (!db) {
      console.warn('Database not initialized yet, cannot clear benchmark state');
      return;
    }

    await executeSql(db, 'DELETE FROM benchmark_state');
  };

  const contextValue: SQLiteContextType = {
    insertUserData,
    selectUserData,
    insertBenchmark,
    selectBenchmarks,
    deleteBenchmarksByFamilyName,
    deleteBenchmarkByModelAndFamilyName,
    saveSelectedRun,
    getSelectedRun,
    saveAutoDeleteModels,
    getAutoDeleteModels,
    saveBenchmarkState,
    getBenchmarkState,
    clearBenchmarkState,
  };

  if (!isInitialized) {
    return (
      <SQLiteContext.Provider value={null}>
        <DatabaseLoadingScreen error={initError} />
      </SQLiteContext.Provider>
    );
  }

  return (
    <SQLiteContext.Provider value={contextValue}>
      {children}
    </SQLiteContext.Provider>
  );
};

export const useSQLiteContext = (): SQLiteContextType => {
  const context = useContext(SQLiteContext);
  if (!context) {
    throw new Error('useSQLiteContext must be used within a SQLiteProvider');
  }
  return context;
};
