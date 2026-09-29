import React, { useCallback, useEffect, useRef } from 'react';
import { NavigationContainer, ParamListBase, RouteProp } from '@react-navigation/native';
import { BottomTabNavigationOptions, BottomTabNavigationProp, createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import InferenceScreen, { InferenceScreenIcon } from './src/screens/InferenceScreen';
import BenchmarksScreen, { BenchmarksScreenIcon } from './src/screens/BenchmarksScreen';
import ModelsScreen, { ModelsScreenIcon } from './src/screens/ModelsScreen';
import InfoScreen, { InfoScreenIcon } from './src/screens/InfoScreen';
import { QColors } from './src/colors';
import { TouchableOpacity } from 'react-native';
import { LlamaProvider } from './src/contexts/llamaContext';
import { SQLiteProvider, useSQLiteContext } from './src/contexts/SQLiteContext';
import { BenchmarkingProvider, useBenchmarkingContext } from './src/contexts/benchmarkingContext';
import { DownloadingProvider } from './src/contexts/downloadingContext';
import { ConfigProvider } from './src/contexts/ConfigContext';


const Tab = createBottomTabNavigator();

const TabIcon: React.FC<{ routeName: string, color: string, size: number }> = ({ routeName, color, size }) => {
  switch (routeName) {
    case 'Chat':
      return <InferenceScreenIcon color={color} size={size} />;
    case 'Benchmarks':
      return <BenchmarksScreenIcon color={color} size={size} />;
    case 'Models':
      return <ModelsScreenIcon color={color} size={size} />;
    case 'Info':
      return <InfoScreenIcon color={color} size={size} />;
  }
};

const isDisabled = (screenName: string) => {
  const disabledScreens = ['Models', 'Chat', 'Info'];
  return disabledScreens.includes(screenName);
};

type ScreenOptions =
BottomTabNavigationOptions | ((props: {
  route: RouteProp<ParamListBase, string>;
  navigation: BottomTabNavigationProp<ParamListBase, string, undefined>;
  theme: ReactNavigation.Theme;
}) => BottomTabNavigationOptions) | undefined;

function TabNavigator() {
  const { isBenchmarking } = useBenchmarkingContext();

  const navigationScreenOptions: ScreenOptions = useCallback(({ route }: any) => ({
    tabBarIcon: ({ focused, color, size }: any) => (
      <TabIcon
        routeName={route.name}
        color={color}
        size={size}
      />
    ),
    tabBarButton: (props: any) => {
      if (isBenchmarking && isDisabled(route.name)) {
        return (
          // @ts-ignore
          <TouchableOpacity
            {...props}
            style={[props.style, { opacity: 0.3 }]}
            activeOpacity={1}
            onPress={() => null}
          >
            {props.children}
          </TouchableOpacity>
        );
      }
      // @ts-ignore
      return <TouchableOpacity {...props}>{props.children}</TouchableOpacity>;
    },
    headerShown: false,
    tabBarActiveTintColor: QColors.LightGray,
    tabBarInactiveTintColor: QColors.Inactive,
    tabBarStyle: {
      borderColor: QColors.Dark,
      backgroundColor: QColors.Dark,
    },
  }), [isBenchmarking]);

  return (
    <Tab.Navigator screenOptions={navigationScreenOptions} initialRouteName="Benchmarks">
      <Tab.Screen name="Info" component={InfoScreen} />
      <Tab.Screen name="Benchmarks" component={BenchmarksScreen} />
      <Tab.Screen name="Models" component={ModelsScreen} />
      <Tab.Screen name="Chat" component={InferenceScreen} />
    </Tab.Navigator>
  );
}

const NavigationHandler: React.FC = () => {
  const sqlite = useSQLiteContext();
  const navigationRef = useRef<any>(null);

  useEffect(() => {
    const checkForPendingBenchmarks = async () => {
      try {
        if (sqlite) {
          const benchmarkState = await sqlite.getBenchmarkState();
          if (benchmarkState && benchmarkState.isBenchmarking && navigationRef.current) {
            // Navigate to Benchmarks tab if there's a pending benchmark
            setTimeout(() => {
              navigationRef.current?.navigate('Benchmarks');
            }, 100);
          }
        }
      } catch (error) {
        console.error('Error checking for pending benchmarks in navigation:', error);
      }
    };

    checkForPendingBenchmarks();
  }, [sqlite]);

  return (
    <NavigationContainer ref={navigationRef}>
      <TabNavigator/>
    </NavigationContainer>
  );
};



function App(): React.JSX.Element {

  return (
    <DownloadingProvider>
      <BenchmarkingProvider>
        <SQLiteProvider>
          <ConfigProvider>
            <LlamaProvider>
              <NavigationHandler/>
            </LlamaProvider>
          </ConfigProvider>
        </SQLiteProvider>
      </BenchmarkingProvider>
    </DownloadingProvider>
  );
}

export default App;
