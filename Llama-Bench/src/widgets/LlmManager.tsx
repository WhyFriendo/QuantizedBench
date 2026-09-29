import { FlatList, StyleSheet } from 'react-native';
import { LlmModel } from '../types/LlmModel';
import { LlmItem } from './LlmItem';
import { checkFileExists, downloadFile, getLocalPath, removeFile } from '../fsUtils';
import { useEffect, useState } from 'react';
import { DownloadState } from '../types/DownloadState';
import { QColors } from '../colors';
import RNFS from 'react-native-fs';
import { useDownloadingContext } from '../contexts/downloadingContext';

export interface ILlmManager {
  models: LlmModel[];
}
export const LlmManager: React.FC<ILlmManager> = ({ models }) => {
  const { storeJob, removeJob } = useDownloadingContext();
  const [downloadedModels, setDownloadedModels] = useState<Set<string>>(new Set());
  const [downloads, setDownloads] = useState<Record<string, DownloadState>>({});

  useEffect(() => {
    const updateDownloadedModels = async () => {
      const updatedModels = new Set<string>();
      for (const model of models) {
        const exists = await checkFileExists(model.filename);
        if (exists) {
          updatedModels.add(model.name);
        }
      }
      setDownloadedModels(updatedModels);
    };
    updateDownloadedModels();
  }, [models]);

  const handleDownload = async (model: LlmModel) => {
    setDownloads(prev => ({
      ...prev,
      [model.name]: { isDownloading: true, progress: 0 }
    }));

    let downloadJobId: number | null = null;
    const result = await downloadFile(
      model.url,
      model.filename,
      (progress) => {
        setDownloads(prev => ({
          ...prev,
          [model.name]: { ...prev[model.name], progress },
        }));
      },
      (jobId) => {
        downloadJobId = jobId;
        storeJob(jobId);
        setDownloads(prev => ({
          ...prev,
          [model.name]: { ...prev[model.name], jobId },
        }));
      }
    );

    if (downloadJobId !== null) {
      removeJob(downloadJobId);
    }

    if (result.status === 'success') {
      setDownloadedModels(prev => new Set(prev.add(model.name)));
    }

    setDownloads(prev => ({
      ...prev,
      [model.name]: { isDownloading: false, progress: 0 },
    }));
  };

  const handleCancelDownload = async (model: LlmModel) => {
    const download = downloads[model.name];
    if (download?.jobId !== undefined) {
      try {
        removeJob(download.jobId);
        await RNFS.stopDownload(download.jobId);
        const modelFilePath = getLocalPath(model.filename);
        await removeFile(modelFilePath);
      } catch (error) {
        console.error('Error canceling download:', error);
      }
    }

    setDownloads(prev => ({
      ...prev,
      [model.name]: { isDownloading: false, progress: 0 },
    }));
  };

  const handleDelete = async (model: LlmModel) => {
    const result = await removeFile(model.filename);
    if (result === 'success') {
      setDownloadedModels(prev => {
        const next = new Set(prev);
        next.delete(model.name);
        return next;
      });
    }
  };

  return (
    <FlatList
      style={styles.flatlist}
      data={models}
      keyExtractor={(item) => item.name}
      renderItem={({ item }) => (
        <LlmItem
          config={item}
          isDownloaded={downloadedModels.has(item.name)}
          onDownload={() => handleDownload(item)}
          onDelete={() => handleDelete(item)}
          onCancelDownload={() => handleCancelDownload(item)}
          downloadState={downloads[item.name] || { isDownloading: false, progress: 0 }}
        />
      )}
    />
  );
};

const styles = StyleSheet.create({
  flatlist: {
    backgroundColor: QColors.Gray,
  },
});
