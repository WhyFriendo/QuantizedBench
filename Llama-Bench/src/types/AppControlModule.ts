export interface AppControlModule {
  forceCloseApp(): Promise<boolean>;
  restartApp(): Promise<boolean>;
}

declare module 'react-native' {
  interface NativeModulesStatic {
    AppControlModule: AppControlModule;
  }
} 