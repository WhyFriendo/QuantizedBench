import Svg, { Path, G } from 'react-native-svg';
import { SvgIconProps } from '../types/SvgIconProps';
import { SAV } from '../widgets/SAV';
import { BenchmarkContainer, BenchmarkContainerRef } from '../widgets/BenchmarkContainer';
import { BenchmarkQueue } from '../widgets/BenchmarkQueue';
import { useEffect, useMemo, useRef, useState, useCallback } from 'react';
import { useSQLiteContext } from '../contexts/SQLiteContext';
import { llmModels } from '../models';
import { LlmModel } from '../types/LlmModel';
import { activateKeepAwake, deactivateKeepAwake } from '@sayem314/react-native-keep-awake';
import { Animated, Easing, Platform, ScrollView, StyleSheet, UIManager, TouchableOpacity, Modal } from 'react-native';
import config from '../config';
import { filterModelsByConfig } from '../modelFilter';
import { SettingsIcon } from '../widgets/icons/SettingsIcon';
import { QColors } from '../colors';
import SettingsScreen from './SettingsScreen';
import { useConfig } from '../contexts/ConfigContext';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

export const BenchmarksScreenIcon: React.FC<SvgIconProps> = ({ color, size }) => (
  <Svg width={size} height={size} viewBox="0 0 90 90">
    <G>
      <Path fill={color} d="m86.0815,25.624l-12.505,0c-0.785,0 -1.421,0.636 -1.421,1.421l0,56.317c0,0.785 0.636,1.421 1.421,1.421l12.505,0c0.785,0 1.421,-0.636 1.421,-1.421l0,-56.317c0.001,-0.785 -0.636,-1.421 -1.421,-1.421zm-1.421,56.316l-9.662,0l0,-53.473l9.662,0l0,53.473zm-68.238,-18.815l-12.504,0c-0.785,0 -1.421,0.636 -1.421,1.421l0,18.816c0,0.785 0.636,1.421 1.421,1.421l12.505,0c0.785,0 1.421,-0.636 1.421,-1.421l0,-18.816c0,-0.785 -0.637,-1.421 -1.422,-1.421zm-1.421,18.815l-9.662,0l0,-15.973l9.662,0l0,15.973zm47.861,-31.385l-12.505,0c-0.785,0 -1.421,0.636 -1.421,1.421l0,31.385c0,0.785 0.636,1.421 1.421,1.421l12.505,0c0.785,0 1.421,-0.636 1.421,-1.421l0,-31.384c0,-0.785 -0.636,-1.422 -1.421,-1.422zm-1.421,31.386l-9.662,0l0,-28.543l9.662,0l0,28.543zm-21.799,-39.914l-12.505,0c-0.785,0 -1.421,0.636 -1.421,1.421l0,39.914c0,0.785 0.636,1.421 1.421,1.421l12.505,0c0.785,0 1.421,-0.636 1.421,-1.421l0,-39.914c0.001,-0.785 -0.636,-1.421 -1.421,-1.421zm-1.421,39.914l-9.662,0l0,-37.071l9.662,0l0,37.071zm-11.617,-54.046c0,1.058 0.244,2.061 0.678,2.954l-12.753,9.93c-1.18,-0.992 -2.7,-1.59 -4.359,-1.59c-3.742,0 -6.786,3.044 -6.786,6.786c0,3.742 3.044,6.786 6.786,6.786s6.786,-3.044 6.786,-6.786c0,-1.058 -0.244,-2.061 -0.678,-2.954l12.753,-9.931c1.18,0.991 2.7,1.59 4.359,1.59c2.761,0 5.141,-1.659 6.199,-4.032l10.235,2.15c0.015,3.729 3.053,6.758 6.785,6.758c3.742,0 6.786,-3.044 6.786,-6.786c0,-1.223 -0.327,-2.37 -0.895,-3.362l13.333,-11.925c1.122,0.82 2.502,1.306 3.995,1.306c3.742,0 6.786,-3.044 6.786,-6.786s-3.044,-6.786 -6.786,-6.786c-3.742,0 -6.786,3.044 -6.786,6.786c0,1.223 0.327,2.37 0.895,3.362l-13.332,11.925c-1.122,-0.82 -2.503,-1.305 -3.995,-1.305c-2.761,0 -5.141,1.659 -6.199,4.032l-10.235,-2.149c-0.015,-3.729 -3.053,-6.758 -6.785,-6.758c-3.743,-0.001 -6.787,3.043 -6.787,6.785zm53.225,-19.835c2.174,0 3.943,1.769 3.943,3.943s-1.769,3.943 -3.943,3.943c-2.174,0 -3.943,-1.769 -3.943,-3.943s1.769,-3.943 3.943,-3.943zm-23.22,20.767c2.174,0 3.943,1.769 3.943,3.943s-1.769,3.943 -3.943,3.943c-2.174,0 -3.943,-1.769 -3.943,-3.943s1.769,-3.943 3.943,-3.943zm-46.439,21.091c-2.174,0 -3.943,-1.769 -3.943,-3.943c0,-2.174 1.769,-3.943 3.943,-3.943s3.943,1.769 3.943,3.943c0,2.174 -1.769,3.943 -3.943,3.943zm23.22,-25.966c2.174,0 3.943,1.769 3.943,3.943c0,2.174 -1.769,3.943 -3.943,3.943s-3.943,-1.769 -3.943,-3.943c0,-2.174 1.769,-3.943 3.943,-3.943z" />
    </G>
  </Svg>
);

function groupByFamily(models: LlmModel[]) {
  const filtered = filterModelsByConfig(models, config);
  const groupedModels = filtered.reduce((acc: Record<string, LlmModel[]>, model: LlmModel) => {
    const key = model.family;
    if (!acc[key]) {
      acc[key] = [];
    }
    acc[key].push(model);
    return acc;
  }, {} as Record<string, LlmModel[]>);
  return Object.entries(groupedModels);
}

function BenchmarksScreen() {
  const allGroupedModels = useMemo(() => groupByFamily([...llmModels]), []);
  const [currentBenchmark, setCurrentBenchmark] = useState<string | null>(null);
  const [expandedFamily, setExpandedFamily] = useState<string | null>(null);
  const animationInProgress = useRef(false);
  const [showSettings, setShowSettings] = useState(false);
  const { config: dynamicConfig, reloadConfig } = useConfig();
  const sqliteContext = useSQLiteContext();

  const [transitionAnim] = useState(new Animated.Value(0));

  const containerRefs = useMemo(() => {
    const refs: Record<string, React.RefObject<BenchmarkContainerRef>> = {};
    allGroupedModels.forEach(([family]) => {
      refs[family] = { current: null };
    });
    return refs;
  }, [allGroupedModels]);

  useEffect(() => {
    if (currentBenchmark) {
      activateKeepAwake();
    } else {
      deactivateKeepAwake();
    }
  }, [currentBenchmark]);

  const checkAndRestorePendingBenchmarks = useCallback(async () => {
    try {
      const benchmarkState = await sqliteContext.getBenchmarkState();
      if (benchmarkState && benchmarkState.isBenchmarking) {
        const family = BenchmarkQueue.extractFamilyFromBenchmarkState(benchmarkState);
        if (family && containerRefs[family]?.current) {
          console.log('Restoring pending benchmarks for family:', family);
          await containerRefs[family].current!.restorePendingBenchmarks();
        }
      }
    } catch (error) {
      console.error('Error checking for pending benchmarks:', error);
    }
  }, [sqliteContext]);

  useEffect(() => {
    checkAndRestorePendingBenchmarks();
  }, [checkAndRestorePendingBenchmarks]);

  const startBenchmarking = (family: string) => {
    setCurrentBenchmark(family);
  };

  const stopBenchmarking = () => {
    setCurrentBenchmark(null);
  };

  const toggleExpand = (family: string) => {
    if (animationInProgress.current) return;
    animationInProgress.current = true;

    if (expandedFamily === family) {
      Animated.timing(transitionAnim, {
        toValue: 0,
        duration: 250,
        useNativeDriver: true,
        easing: Easing.out(Easing.cubic),
      }).start(() => {
        setExpandedFamily(null);
        animationInProgress.current = false;
      });
    } else {
      setExpandedFamily(family);
      Animated.timing(transitionAnim, {
        toValue: 1,
        duration: 250,
        useNativeDriver: true,
        easing: Easing.out(Easing.cubic),
      }).start(() => {
        animationInProgress.current = false;
      });
    }
  };

  const callbacks = {
    startBenchmarking,
    stopBenchmarking,
  };

  const handleSettingsClose = async () => {
    setShowSettings(false);
    await reloadConfig();
  };

  const showSettingsIcon = config.BENCHMARK_SETTINGS.RUNS.length > 1;

  return (
    <SAV>
      <ScrollView
        style={styles.container}
        contentContainerStyle={expandedFamily ? styles.expandedFamilyContent : styles.familyListContent}
        scrollEnabled={!expandedFamily}
      >
        {/* {showSettingsIcon && (
          <TouchableOpacity
            style={styles.settingsButton}
            onPress={() => setShowSettings(true)}
          >
            <SettingsIcon color={QColors.White} size={24} />
          </TouchableOpacity>
        )} */}
        
        {allGroupedModels.map(([family, models]) => {
          const isExpanded = expandedFamily === family;
          const isCurrentlyBenchmarking = currentBenchmark === family;

          const opacity = isExpanded
            ? 1
            : expandedFamily
              ? 0
              : 1;

          return (
            <Animated.View
              key={family + 'bc'}
              style={[
                styles.containerItem,
                {
                  opacity: expandedFamily ? (isExpanded ? 1 : 0) : 1,
                  height: isExpanded ? undefined : 'auto',
                  flex: isExpanded ? 1 : undefined,
                  display: expandedFamily && !isExpanded ? 'none' : 'flex',
                  zIndex: isExpanded ? 10 : 1,
                }
              ]}
            >
              <BenchmarkContainer
                ref={containerRefs[family]}
                family={family}
                models={models}
                benchmarkingPossible={currentBenchmark === null || currentBenchmark === family}
                callbacks={callbacks}
                isExpanded={isExpanded}
                onToggle={() => toggleExpand(family)}
                isCurrentlyBenchmarking={isCurrentlyBenchmarking}
              />
            </Animated.View>
          );
        })}
      </ScrollView>
      
      <Modal
        visible={showSettings}
        animationType="slide"
        presentationStyle="pageSheet"
      >
        <SettingsScreen onClose={handleSettingsClose} />
      </Modal>
    </SAV>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  familyListContent: {
    paddingBottom: 16,
  },
  expandedFamilyContent: {
    flexGrow: 1,
  },
  containerItem: {
    overflow: 'hidden',
  },
  settingsButton: {
    position: 'absolute',
    top: 16,
    right: 16,
    zIndex: 100,
    padding: 12,
    backgroundColor: QColors.Gray,
    borderRadius: 24,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    elevation: 5,
  },
});

export default BenchmarksScreen;
