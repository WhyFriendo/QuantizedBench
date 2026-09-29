import React, { useEffect, useMemo, useRef, useState, useCallback, useImperativeHandle, forwardRef } from 'react';

import { Alert, Animated, Dimensions, ScrollView, StyleSheet, Text, TouchableOpacity, View, Easing } from 'react-native';
import { LlmModel } from '../types/LlmModel';
import { QColors } from '../colors';
import { hasSufficientSpace } from '../fsUtils';
import { useLlamaContext } from '../contexts/llamaContext';
import { LlamaContext } from 'llama.rn';
import { getPhoneInfo } from '../metrics';
import { useSQLiteContext } from '../contexts/SQLiteContext';
import { useCloudflare } from '../hooks/useCloudflare';
import { useBenchmarkingContext } from '../contexts/benchmarkingContext';
import { isInternetConnected } from '../checkInternetConnection';
import { useDownloadingContext } from '../contexts/downloadingContext';
import { BenchmarkQueue } from './BenchmarkQueue';
import { ChevronDownIcon, ChevronUpIcon } from './icons/ChevronIcon';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useConfig } from '../contexts/ConfigContext';

const PulsingDot = () => {
  const pulseAnim = useRef(new Animated.Value(0.3)).current;

  useEffect(() => {
    Animated.loop(
      Animated.sequence([
        Animated.timing(pulseAnim, {
          toValue: 1,
          duration: 1000,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
        Animated.timing(pulseAnim, {
          toValue: 0.3,
          duration: 1000,
          easing: Easing.inOut(Easing.ease),
          useNativeDriver: true,
        }),
      ])
    ).start();
  }, [pulseAnim]);

  return (
    <Animated.View style={[
      styles.greenDot,
      { opacity: pulseAnim, transform: [{ scale: pulseAnim }] },
    ]} />
  );
};

const getStatusLabel = (benchmarkingModel: LlmModelBenchmarking) => {
  const { status, isDownloading, downloadingPercent } = benchmarkingModel;

  switch (status) {
    case 'completed':
      return 'Completed';
    case 'waiting':
      return 'In Queue';
    case 'downloading':
      return isDownloading
        ? `Downloading ${downloadingPercent.toFixed(0)}%`
        : 'Preparing';
    case 'post-download':
      return 'Downloaded';
    case 'loading':
      return 'Loading';
    case 'benchmarking':
      return 'Benchmarking';
    case 'saving':
      return 'Saving';
    case 'cleaning':
      return 'Cleaning Up';
    case 'error':
    case 'post-error':
      return 'Error';
    case 'stale':
      return 'Ready';
    case 'waiting-restart':
      return 'Waiting for Restart';
    case 'pre-benchmark':
      return 'Preparing to Benchmark';
    default:
      return status.charAt(0).toUpperCase() + status.slice(1);
  }
};

const getStatusBadgeStyle = (status: string) => {
  switch (status) {
    case 'completed':
      return { backgroundColor: QColors.Success };
    case 'waiting':
      return { backgroundColor: QColors.Warning };
    case 'downloading':
    case 'post-download':
    case 'loading':
    case 'benchmarking':
    case 'saving':
      return { backgroundColor: QColors.Blue };
    case 'cleaning':
      return { backgroundColor: QColors.LightBlue };
    case 'error':
    case 'post-error':
      return { backgroundColor: QColors.Error };
    case 'stale':
      return { backgroundColor: QColors.Gray };
    default:
      return { backgroundColor: QColors.Gray };
  }
};

export interface BenchmarkContainerRef {
  restorePendingBenchmarks: () => Promise<void>;
}

interface BenchmarkContainerProps {
  family: string;
  models: LlmModel[];
  benchmarkingPossible: boolean;
  callbacks: {
    startBenchmarking: (family: string) => void;
    stopBenchmarking: () => void;
  },
  isExpanded: boolean;
  onToggle: () => void;
  isCurrentlyBenchmarking?: boolean;
}

export interface RunMetrics {
  runId?: number,
  tps?: number,
  ttft?: number,
  inferenceTime?: number,
  ram?: number[],
  batteryTempreture?: number[],
  sensorTempreratures?: number[],
  battery?: number[],
  batteryInfos?: any[],
  outputTokens?: number,
  inputTokens?: number,
}

export interface LlmModelBenchmarking {
  model: LlmModel;
  status: 'stale' | 'waiting' | 'downloading' | 'post-download' | 'loading' | 'benchmarking' | 'saving' | 'cleaning' | 'error' | 'post-error' | 'completed' | 'saved-error' | 'waiting-restart' | 'pre-benchmark';
  wasBenchmarked: boolean;
  wasDownloaded: boolean;
  isDownloading: boolean;
  downloadingPercent: number;
  jobId?: number,
  data?: {
    loadTime?: number;
    runs?: RunMetrics[],
  },
}

export const BenchmarkContainer = forwardRef<BenchmarkContainerRef, BenchmarkContainerProps>(({
  family,
  models,
  benchmarkingPossible,
  callbacks,
  isExpanded,
  onToggle,
  isCurrentlyBenchmarking = false,
}, ref) => {
  const { cancelAllDownloads } = useDownloadingContext();
  const cloudflare = useCloudflare();
  const sqliteContext = useSQLiteContext();
  const { context: sharedContext, setContext: setSharedContext } = useLlamaContext();
  const context = useRef<LlamaContext | undefined>(undefined);
  const { isBenchmarking, setIsBenchmarking } = useBenchmarkingContext();
  const [benchmarkingModels, setBenchmarkingModels] = useState<LlmModelBenchmarking[]>([]);
  const [isCanceling, setIsCanceling] = useState(false);
  const [rerunRefresh, setRerunRefresh] = useState<number>(0);
  const { config } = useConfig();
  const insets = useSafeAreaInsets();
  const windowHeight = Dimensions.get('window').height;

  const headerHeight = 60;
  const containerPadding = 16;
  const availableHeight = windowHeight - headerHeight - containerPadding - insets.top - insets.bottom;

  const animation = useRef(new Animated.Value(0)).current;
  const opacityAnimation = useRef(new Animated.Value(0)).current;

  const benchmarkQueue = useMemo<BenchmarkQueue>(() => new BenchmarkQueue(
    sqliteContext,
    cloudflare,
    {
      startBenchmarking: () => {
        setIsBenchmarking(true);
        callbacks.startBenchmarking(family);
      },
      stopBenchmarking: () => {
        setIsBenchmarking(false);
        callbacks.stopBenchmarking();
      },
      setBenchmarkingModels: setBenchmarkingModels,
      getLlamaContext: () => context.current!,
      setLlamaContext: (llamaContext) => {
        context.current = llamaContext;
      },
      releaseLlamaContext: async () => {
        if (!context.current) { return; }

        try { await context.current.stopCompletion(); } catch (err) { }
        await context.current.release();
        context.current = undefined;
      },
      releaseSharedContext: async () => {
        if (!sharedContext) { return; }

        try { await sharedContext.stopCompletion(); } catch (err) { }
        await sharedContext.release();
        setSharedContext(undefined);
      },
      onCancelingStarted: () => {
        setIsCanceling(true);
      },
      onCancelingFinished: () => {
        setIsCanceling(false);
      },
    },
    config
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ), [config]);

  const restorePendingBenchmarks = useCallback(async () => {
    await benchmarkQueue.restorePendingBenchmarks();
  }, [benchmarkQueue]);

  useImperativeHandle(ref, () => ({
    restorePendingBenchmarks,
  }), [restorePendingBenchmarks]);

  useEffect(() => {
    const handler = async () => {
      const { totalMemory } = await getPhoneInfo();
      const totalMemoryGB = totalMemory / 1000 / 1000;
      const runnableModels = models.filter(model => model.memory <= totalMemoryGB);
      const mappedModels: LlmModelBenchmarking[] = runnableModels.map(model => ({
        model,
        status: 'stale',
        wasBenchmarked: false,
        wasDownloaded: true,
        isDownloading: false,
        downloadingPercent: 0,
      }));
      const sortedModels = mappedModels.sort((a, b) => a.model.memory - b.model.memory);
      const savedBenchmarks = await sqliteContext.selectBenchmarks(family);

      const benchmarkModels: LlmModelBenchmarking[] = sortedModels.map(model => {
        const benchmark = savedBenchmarks.find(benchmark => benchmark.model_name === model.model.name);
        if (benchmark) {
          const wasBenchmarked = benchmark.status === 'completed';
          const status = benchmark.status === 'completed' ? 'completed' : 'stale';

          return {
            ...model,
            status,
            wasBenchmarked,
          };
        }
        return model;
      });

      setBenchmarkingModels(benchmarkModels);
    };
    handler();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [models, rerunRefresh]);

  useEffect(() => {
    if (isExpanded) {
      Animated.timing(opacityAnimation, {
        toValue: 1,
        duration: 150,
        useNativeDriver: true,
        easing: Easing.out(Easing.cubic),
      }).start();

      Animated.timing(animation, {
        toValue: availableHeight,
        duration: 250,
        useNativeDriver: false,
        easing: Easing.out(Easing.cubic),
      }).start();
    } else {
      Animated.timing(animation, {
        toValue: 0,
        duration: 200,
        useNativeDriver: false,
        easing: Easing.out(Easing.cubic),
      }).start();

      Animated.timing(opacityAnimation, {
        toValue: 0,
        duration: 100,
        useNativeDriver: true,
        easing: Easing.out(Easing.cubic),
      }).start();
    }
  }, [isExpanded, animation, opacityAnimation, availableHeight]);

  const toggleVisibility = () => {
    onToggle();
  };

  const hasEnoughSpace = async (modelsToCheck: LlmModelBenchmarking[]) => {
    const maxSize = Math.max(...modelsToCheck.map(model => model.model.memory));
    const hasSpace = await hasSufficientSpace(maxSize);
    return { maxSize: maxSize * 1.2, hasSpace };
  };

  const checkBenchmarkPrerequisites = async (modelsToCheck: LlmModelBenchmarking[]): Promise<boolean> => {
    const result = await hasEnoughSpace(modelsToCheck);
    if (!result.hasSpace) {
      Alert.alert('Not enough space', 'Please make sure you have at least ' + result.maxSize.toFixed(2) + 'GB of free space before running the benchmark.');
      return false;
    }

    if (!(await isInternetConnected())) {
      Alert.alert(
        'No Internet Connection',
        'The internet connection is required to proceed. Please connect to the internet and try again.',
        [{ text: 'OK' }]
      );
      return false;
    }

    return true;
  };

  const queueSingleModel = async (model: LlmModelBenchmarking) => {
    if (!(await checkBenchmarkPrerequisites([model]))) {
      return;
    }

    Alert.alert(
      'Information',
      `Do you want to benchmark ${model.model.name}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Start', onPress: () => {
            cancelAllDownloads();
            benchmarkQueue.addToQueue(model);
          },
        },
      ]
    );
  };

  const removeSingleModelFromQueue = (model: LlmModelBenchmarking) => {
    Alert.alert(
      'Remove from Queue',
      `Do you want to remove ${model.model.name} from the benchmark queue?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove', onPress: () => {
            benchmarkQueue.removeFromQueue(model);
          },
        },
      ]
    );
  };

  const resetBenchmarks = async () => {
    Alert.alert(
      'Reset Benchmarks',
      'Are you sure you want to reset all benchmarks for this family?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Reset', onPress: async () => {
            await sqliteContext.deleteBenchmarksByFamilyName(family);
            setRerunRefresh(prev => prev + 1);
          },
        },
      ]
    );
  };

  const toggleBenchmarking = async (restartBenchmarks: boolean) => {
    const benchmarking = !isBenchmarking;

    if (isCanceling) { return; }

    if (benchmarking && restartBenchmarks) {
      Alert.alert(
        'Information',
        'Do you want to reset the benchmarks?',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Reset', onPress: async () => {
              await sqliteContext.deleteBenchmarksByFamilyName(family);
              setRerunRefresh(prev => prev + 1);
            },
          },
        ]
      );
      return;
    }

    if (benchmarking) {
      const modelsToBenchmark = benchmarkingModels.filter(model => model.status === 'stale' || model.status === 'saved-error' || model.status === 'post-error');

      if (!(await checkBenchmarkPrerequisites(modelsToBenchmark))) {
        return;
      }

      Alert.alert(
        'Information',
        'The benchmarking process will start. Please make sure you have a stable internet connection and leave the app open during the process. If you need to do something else, please stop the benchmarking process and resume later.',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Start', onPress: () => {
              cancelAllDownloads();

              benchmarkQueue.addToQueue(modelsToBenchmark);
            },
          },
        ]
      );
    } else {
      Alert.alert(
        'Information',
        'The benchmarking process will be stopped. You can resume it later.',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Stop', onPress: () => {
              benchmarkQueue.cancel();
            },
          },
        ]
      );
    }
  };

  const benchmarkedCount = useMemo<number>(() => benchmarkingModels.filter(model => model.status === 'completed').length, [benchmarkingModels]);
  const isAllBenchmarked = benchmarkedCount === benchmarkingModels.length;

  return (
    <View style={styles.mainContainer}>
      <View style={styles.container}>
        <TouchableOpacity onPress={toggleVisibility} style={styles.headerContainer}>
          <View style={styles.familyInfo}>
            <Text style={styles.familyText}>{family}</Text>
            {isCurrentlyBenchmarking && <PulsingDot />}
            <Text style={styles.statusText}>{benchmarkedCount}/{benchmarkingModels.length}</Text>
          </View>
          <View style={styles.headerRight}>
            <Text style={styles.showText}>{isExpanded ? 'Show less' : 'Show more'}</Text>
            {isExpanded ?
              <ChevronUpIcon color={QColors.LightGray} size={24} /> :
              <ChevronDownIcon color={QColors.LightGray} size={24} />
            }
          </View>
        </TouchableOpacity>

        <Animated.View
          style={[
            styles.collapsibleContentWrapper,
            {
              height: animation,
              overflow: 'hidden',
            },
          ]}
        >
          <Animated.View
            style={[
              styles.collapsibleContent,
              {
                opacity: opacityAnimation,
                pointerEvents: isExpanded ? 'auto' : 'none',
              },
            ]}
          >
            {benchmarkingPossible && (
              <View style={styles.buttonsContainer}>
                <TouchableOpacity
                  onPress={() => toggleBenchmarking(isAllBenchmarked)}
                  style={{ ...styles.benchmarkButton, backgroundColor: QColors.LightBlue }}
                >
                  <Text style={styles.benchmarkButtonText}>
                    {!isBenchmarking ? 'Run Benchmarks' : (isCanceling ? 'Cancelling...' : 'Stop Benchmarking')}
                  </Text>
                </TouchableOpacity>
                <TouchableOpacity
                  onPress={resetBenchmarks}
                  disabled={isBenchmarking || isCanceling}
                  style={{
                    ...styles.benchmarkButton,
                    backgroundColor: (isBenchmarking || isCanceling)
                      ? 'rgba(255, 165, 0, 0.4)'
                      : QColors.Warning,
                  }}
                >
                  <Text style={styles.benchmarkButtonText}>
                    Reset Benchmarks
                  </Text>
                </TouchableOpacity>
              </View>
            )}

            <ScrollView
              style={styles.modelsScrollView}
              contentContainerStyle={styles.modelsScrollViewContent}
              showsVerticalScrollIndicator={true}
              nestedScrollEnabled
            >
              {benchmarkingModels.map((benchmarkingModel, index) => (
                <View key={benchmarkingModel.model.name} style={styles.modelContainer}>
                  <View style={styles.modelContent}>
                    <View style={styles.modelInfoRow}>
                      <Text style={styles.modelName}>{benchmarkingModel.model.name}</Text>
                      <View style={[
                        styles.statusBadge,
                        getStatusBadgeStyle(benchmarkingModel.status),
                      ]}>
                        <Text style={styles.statusText}>
                          {getStatusLabel(benchmarkingModel)}
                        </Text>
                      </View>
                    </View>

                    <View style={styles.modelDetailsRow}>
                      <Text style={styles.modelDetailText}>
                        {benchmarkingModel.model.memory} GB
                      </Text>
                    </View>

                    <View style={styles.actionsContainer}>
                      {benchmarkingModel.status === 'stale' && (
                        <TouchableOpacity
                          style={styles.actionButton}
                          onPress={() => queueSingleModel(benchmarkingModel)}
                        >
                          <Text style={styles.actionButtonText}>Benchmark Model</Text>
                        </TouchableOpacity>
                      )}

                      {benchmarkingModel.status === 'waiting' && (
                        <TouchableOpacity
                          style={[styles.actionButton, styles.cancelButton]}
                          onPress={() => removeSingleModelFromQueue(benchmarkingModel)}
                        >
                          <Text style={styles.actionButtonText}>Remove from Queue</Text>
                        </TouchableOpacity>
                      )}
                    </View>
                  </View>
                </View>
              ))}
            </ScrollView>
          </Animated.View>
        </Animated.View>
      </View>
    </View>
  );
});

BenchmarkContainer.displayName = 'BenchmarkContainer';

const styles = StyleSheet.create({
  mainContainer: {
    padding: 8,
    borderBottomWidth: 1,
    borderBottomColor: QColors.Inactive,
  },
  container: {
    padding: 6,
  },
  collapsibleContainer: {
    overflow: 'hidden',
  },
  headerContainer: {
    justifyContent: 'space-between',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingBottom: 16,
  },
  familyInfo: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  familyText: {
    fontSize: 20,
    fontWeight: 'bold',
    color: QColors.White,
    marginRight: 10,
  },
  greenDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: QColors.Success,
    marginRight: 10,
  },
  headerRight: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  showText: {
    color: QColors.LightGray,
    marginRight: 8,
    fontSize: 14,
  },
  buttonsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: 8,
    marginBottom: 12,
  },
  benchmarkButton: {
    flex: 1,
    paddingVertical: 4,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginVertical: 8,
    marginHorizontal: 4,
    justifyContent: 'center',
    alignSelf: 'stretch',
  },
  benchmarkButtonText: {
    textAlign: 'center',
    flexWrap: 'wrap',
    fontSize: 16,
    color: QColors.White,
    fontWeight: 'bold',
  },
  modelContainer: {
    marginVertical: 4,
    borderRadius: 12,
    backgroundColor: QColors.Dark,
    overflow: 'hidden',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3.84,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.1)',
  },
  modelContent: {
    padding: 12,
  },
  modelInfoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  modelName: {
    fontSize: 16,
    fontWeight: '700',
    color: QColors.LightGray,
    flex: 1,
  },
  modelDetailsRow: {
    marginBottom: 12,
  },
  modelDetailText: {
    fontSize: 14,
    color: QColors.Inactive,
    fontWeight: '400',
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    minWidth: 100,
  },
  statusText: {
    fontSize: 13,
    color: QColors.White,
    fontWeight: '600',
  },
  actionsContainer: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    marginTop: 4,
  },
  actionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: QColors.LightBlue,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
    marginLeft: 8,
  },
  cancelButton: {
    backgroundColor: QColors.Error,
  },
  viewButton: {
    backgroundColor: QColors.Success,
  },
  actionButtonText: {
    color: QColors.White,
    fontSize: 14,
    fontWeight: '500',
    marginLeft: 4,
  },
  collapsibleContentWrapper: {
    overflow: 'hidden',
  },
  collapsibleContent: {
    paddingTop: 8,
    flex: 1,
  },
  modelsScrollView: {
    flex: 1,
  },
  modelsScrollViewContent: {
    paddingBottom: 20,
  },
});
