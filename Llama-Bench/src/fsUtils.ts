import RNFS, { DownloadFileOptions } from 'react-native-fs';
import { LlmModel } from './types/LlmModel';


export const getLocalPath = (fileName: string) => `${RNFS.DocumentDirectoryPath}/${fileName}`;

export const checkFileExists = async (fileName: string) => {
  const path = getLocalPath(fileName);
  try {
    return await RNFS.exists(path);
  } catch (err) {
    return false;
  }
};

export const listFilesInDocumentDirectory = async (): Promise<string[]> => {
  try {
    const files = await RNFS.readDir(RNFS.DocumentDirectoryPath);
    const fileNames = files
      .filter(item => item.isFile())
      .map(file => file.name);
    return fileNames;
  } catch (error) {
    throw [];
  }
};

type DownloadFileResultStatus = 'success' | 'failed';
export interface DownloadFileResult {
  status: DownloadFileResultStatus;
  path: string;
}
export const downloadFile = async (
  url: string,
  fileName: string,
  progressCallback: (percentage: number) => void,
  jobIdCallback?: (jobId: number) => void
): Promise<DownloadFileResult> => {
  const downloadDest = getLocalPath(fileName);

  const downloadOptions: DownloadFileOptions = {
    fromUrl: url,
    toFile: downloadDest,
    progressInterval: 1000,
    background: true,
    progress: (data) => {
      const percentage = (data.bytesWritten / data.contentLength) * 100;
      progressCallback(percentage);
    },
    begin: () => {
      progressCallback(0);
    },
  };

  try {
    const download = RNFS.downloadFile(downloadOptions);
    if (jobIdCallback) jobIdCallback(download.jobId);
    const downloadResult = await download.promise;
    const status = downloadResult.statusCode === 200 ? 'success' : 'failed';
    return { status, path: downloadDest };
  } catch (err) {
    return { status: 'failed', path: '' };
  }
};

export const stopDownloading = (jobId: number) => RNFS.stopDownload(jobId);

type RemoveFileResultStatus = 'success' | 'failed';
export const removeFile = async (filePath: string): Promise<RemoveFileResultStatus> => {
  try {
    const fileExists: boolean = await RNFS.exists(filePath);
    if (fileExists) {
      await RNFS.unlink(filePath);
    }
    return 'success';
  } catch (error) {
    return 'failed';
  }
};

export const scanForModels = async (models: LlmModel[]) => {
  try {
    const files = await RNFS.readDir(RNFS.DocumentDirectoryPath);
    const availableModels = models.filter((model) =>
      files.some((file) => file.name === model.filename)
    );
    return availableModels;
  } catch (err) {
    return [];
  }
};

export const hasSufficientSpace = async (sizeInGB: number): Promise<boolean> => {
  const buffer = 1.2;
  try {
    const sizeInBytes = sizeInGB * 1024 * 1024 * 1024;
    const freeSpaceInfo = await RNFS.getFSInfo();

    return freeSpaceInfo.freeSpace >= sizeInBytes * buffer;
  } catch (error) {
    console.error('Error checking storage space:', error);
    return false;
  }
};

export const calculateSha256 = async (filePath: string) => {
  return await RNFS.hash(filePath, 'sha256');
};
