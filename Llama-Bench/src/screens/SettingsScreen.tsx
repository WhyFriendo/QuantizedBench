import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView } from 'react-native';
import { SAV } from '../widgets/SAV';
import { useSQLiteContext } from '../contexts/SQLiteContext';
import config from '../env/env.development';
import { QColors } from '../colors';

interface SettingsScreenProps {
  onClose: () => void;
}

const SettingsScreen: React.FC<SettingsScreenProps> = ({ onClose }) => {
  const sqlite = useSQLiteContext();
  const [selectedRun, setSelectedRun] = useState<number>(0);
  const [autoDeleteModels, setAutoDeleteModels] = useState<boolean>(true);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      const run = await sqlite.getSelectedRun();
      const autoDelete = await sqlite.getAutoDeleteModels();
      setSelectedRun(run);
      setAutoDeleteModels(autoDelete);
    } catch (error) {
      console.error('Error loading settings:', error);
    } finally {
      setLoading(false);
    }
  };

  const selectRun = (runIndex: number) => {
    setSelectedRun(runIndex);
  };

  const saveSettings = async () => {
    try {
      await sqlite.saveSelectedRun(selectedRun);
      await sqlite.saveAutoDeleteModels(autoDeleteModels);
      onClose();
    } catch (error) {
      console.error('Error saving settings:', error);
    }
  };

  if (loading) {
    return (
      <SAV>
        <View style={styles.container}>
          <Text style={styles.loadingText}>Loading settings...</Text>
        </View>
      </SAV>
    );
  }

  return (
    <SAV>
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.title}>Benchmark Settings</Text>
          <TouchableOpacity onPress={onClose} style={styles.closeButton}>
            <Text style={styles.closeButtonText}>✕</Text>
          </TouchableOpacity>
        </View>

        <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
          <Text style={styles.sectionTitle}>Select Benchmark Run</Text>
          <Text style={styles.description}>
            Choose which input token configuration to use for benchmarking:
          </Text>

          {config.BENCHMARK_SETTINGS.RUNS.map((run, index) => (
            <TouchableOpacity
              key={index}
              style={[
                styles.runItem,
                selectedRun === index && styles.selectedRunItem
              ]}
              onPress={() => selectRun(index)}
            >
              <View style={styles.runInfo}>
                <Text style={styles.runTitle}>Run {index + 1}</Text>
                <Text style={styles.runDescription}>
                  {run.INPUT_TOKENS} input tokens
                </Text>
              </View>
              <View style={[
                styles.radioButton,
                selectedRun === index && styles.radioButtonSelected
              ]}>
                {selectedRun === index && <View style={styles.radioButtonInner} />}
              </View>
            </TouchableOpacity>
          ))}

          <Text style={[styles.sectionTitle, styles.sectionTitleSpacing]}>Model Management</Text>
          <Text style={styles.description}>
            Control whether downloaded models are automatically deleted after benchmarking:
          </Text>

          <TouchableOpacity
            style={[
              styles.runItem,
              !autoDeleteModels && styles.selectedRunItem
            ]}
            onPress={() => setAutoDeleteModels(!autoDeleteModels)}
          >
            <View style={styles.runInfo}>
              <Text style={styles.runTitle}>Auto-delete Models</Text>
              <Text style={styles.runDescription}>
                {autoDeleteModels ? 'Models will be deleted after benchmarking' : 'Models will be kept after benchmarking'}
              </Text>
            </View>
            <View style={[
              styles.toggleSwitch,
              autoDeleteModels && styles.toggleSwitchActive
            ]}>
              <View style={[
                styles.toggleSwitchThumb,
                autoDeleteModels && styles.toggleSwitchThumbActive
              ]} />
            </View>
          </TouchableOpacity>

          <View style={styles.footer}>
            <TouchableOpacity onPress={saveSettings} style={styles.saveButton}>
              <Text style={styles.saveButtonText}>Save Settings</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
      </View>
    </SAV>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: QColors.Dark,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: QColors.Gray,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: QColors.White,
  },
  closeButton: {
    padding: 8,
  },
  closeButtonText: {
    fontSize: 20,
    color: QColors.White,
  },
  content: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: QColors.White,
    marginBottom: 8,
  },
  description: {
    fontSize: 14,
    color: QColors.LightGray,
    marginBottom: 24,
    lineHeight: 20,
  },
  runItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: QColors.Gray,
    padding: 16,
    marginBottom: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: QColors.Inactive,
  },
  selectedRunItem: {
    borderColor: QColors.Blue,
    backgroundColor: QColors.DarkBlue,
  },
  radioButton: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: QColors.LightGray,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioButtonSelected: {
    borderColor: QColors.Blue,
  },
  radioButtonInner: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: QColors.Blue,
  },
  runInfo: {
    flex: 1,
  },
  runTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: QColors.White,
    marginBottom: 4,
  },
  runDescription: {
    fontSize: 14,
    color: QColors.LightGray,
  },
  footer: {
    marginTop: 32,
    paddingBottom: 20,
  },
  saveButton: {
    backgroundColor: QColors.Blue,
    paddingVertical: 16,
    paddingHorizontal: 32,
    borderRadius: 8,
    alignItems: 'center',
  },
  saveButtonText: {
    color: QColors.White,
    fontSize: 16,
    fontWeight: '600',
  },
  loadingText: {
    fontSize: 16,
    color: QColors.White,
    textAlign: 'center',
    marginTop: 40,
  },
  sectionTitleSpacing: {
    marginTop: 32,
  },
  toggleSwitch: {
    width: 50,
    height: 30,
    borderRadius: 15,
    backgroundColor: QColors.Gray,
    padding: 2,
    justifyContent: 'center',
  },
  toggleSwitchActive: {
    backgroundColor: QColors.Blue,
  },
  toggleSwitchThumb: {
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: QColors.White,
    alignSelf: 'flex-start',
  },
  toggleSwitchThumbActive: {
    alignSelf: 'flex-end',
  },
});

export default SettingsScreen; 