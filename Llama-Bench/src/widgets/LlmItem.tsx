import { Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { LlmModel } from '../types/LlmModel';
import { DownloadIcon } from './icons/DownloadIcon';
import { DeleteIcon } from './icons/DeleteIcon';
import { DownloadState } from '../types/DownloadState';
import { QColors } from '../colors';
import { hasSufficientSpace } from '../fsUtils';
import { isInternetConnected } from '../checkInternetConnection';
import { StopIcon } from './icons/StopIcon';

interface LlmItemProps {
  config: LlmModel;
  isDownloaded: boolean;
  onDownload: () => void;
  onDelete: () => void;
  onCancelDownload: () => void;
  downloadState: DownloadState;
}

export const LlmItem: React.FC<LlmItemProps> = ({
  config,
  isDownloaded,
  onDownload,
  onDelete,
  onCancelDownload,
  downloadState,
}) => {
  const { name, memory } = config;

  const handleDownload = async () => {
    const hasSpace = await hasSufficientSpace(memory);
    if (!hasSpace) {
      Alert.alert(
        'Insufficient Space',
        `You do not have sufficient space to download the ${name} model. Please free up some space and try again.`,
      );
      return;
    }

    const isConnected = await isInternetConnected();
    if (!isConnected) {
      Alert.alert(
        'No Internet Connection',
        'The internet connection is required to proceed. Please connect to the internet and try again.',
        [{ text: 'OK' }]
      );
      return;
    }

    Alert.alert(
      'Confirm Download',
      `The model size is ${memory.toFixed(2)} GB. Do you want to download it?`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Download', onPress: () => onDownload() },
      ]
    );
  };

  const handleDelete = () => {
    Alert.alert(
      'Confirm Delete',
      `Do you want to remove the ${name} model from your device?`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', onPress: () => onDelete() },
      ]
    );
  };

  const handleCancelDownload = () => {
    Alert.alert(
      'Confirm Cancel',
      `Do you want to cancel the download of the ${name}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Cancel Download', onPress: () => onCancelDownload() },
      ]
    );
  }

  return (
    <View style={[styles.item, !isDownloaded && styles.notDownloaded]}>
      <View style={styles.info}>
        <Text style={styles.name}>{name}</Text>
        <Text style={styles.size}>{memory.toFixed(2)} GB</Text>
      </View>
      {downloadState.isDownloading ? (
        <View style={styles.downloadingContainer}>
          <View style={styles.progressContainer}>
            <View style={[styles.progressBar, { width: `${downloadState.progress}%` }]} />
            <Text style={styles.progressText}>{downloadState.progress.toFixed(0)}%</Text>
          </View>
          <TouchableOpacity
            onPress={handleCancelDownload}
            style={styles.cancelButton}
          >
            <StopIcon color={QColors.White} size={18} />
          </TouchableOpacity>
        </View>
      ) : (
        <TouchableOpacity
          onPress={isDownloaded ? handleDelete : handleDownload}
          style={styles.actionButton}
        >
          {isDownloaded ? (
            <DeleteIcon color={QColors.White} size={24} />
          ) : (
            <DownloadIcon color={QColors.White} size={24} />
          )}
        </TouchableOpacity>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  item: {
    flexDirection: 'row',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: QColors.Inactive,
    color: QColors.White,
    alignItems: 'center',
  },
  notDownloaded: {
    opacity: 0.6,
  },
  info: {
    flex: 1,
  },
  name: {
    fontSize: 16,
    fontWeight: '600',
    color: QColors.White,
  },
  size: {
    fontSize: 14,
    color: QColors.LightGray,
    marginTop: 4,
  },
  actionButton: {
    padding: 8,
  },
  progressContainer: {
    width: 100,
    height: 20,
    backgroundColor: '#EEEEEE',
    borderRadius: 2,
    position: 'relative',
  },
  progressBar: {
    height: '100%',
    backgroundColor: QColors.LightBlue,
  },
  progressText: {
    fontSize: 12,
    color: QColors.White,
    position: 'absolute',
    left: 0,
    top: '50%',
    transform: [{ translateY: '-50%' }],
    paddingLeft: 4,
  },
  downloadingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  cancelButton: {
    backgroundColor: QColors.Error,
    padding: 8,
    borderRadius: 8,
  },
});
