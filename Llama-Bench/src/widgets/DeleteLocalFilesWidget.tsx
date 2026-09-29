import { Alert, Button } from 'react-native';
import { calculateSha256, getLocalPath, listFilesInDocumentDirectory, removeFile } from '../fsUtils';
import { LlmModel } from '../types/LlmModel';
import { useState } from 'react';
import { QColors } from '../colors';

const deleteHandlerFiles = async (models: LlmModel[]) => {
  const files = await listFilesInDocumentDirectory();
  const filesToDelete: string[] = [];

  for (const file of files) {
    const model = models.find(model => model.filename === file);
    if (model) {
      const localPath = getLocalPath(file);
      const sha256 = await calculateSha256(localPath);
      if (sha256 !== model.sha256) {
        filesToDelete.push(localPath);
      }
    }
  }

  let successCount = 0;
  for (const file of filesToDelete) {
    const result = await removeFile(file);
    if (result === 'success') {
      successCount++;
    }
  }
  Alert.alert('Files Deleted', `Deleted ${successCount} files.`);
};

export interface DeleteLocalFilesWidgetProps {
  models: LlmModel[];
  onFilesDeleted: () => void;
}
export const DeleteLocalFilesWidget: React.FC<DeleteLocalFilesWidgetProps> = ({ models, onFilesDeleted }) => {
  const [isDeleting, setIsDeleting] = useState(false);

  const handleDeleteFiles = async () => {
    if (isDeleting) return;

    Alert.alert(
      'Confirm Deletion',
      'Are you sure you want to clear invalid files?',
      [
        {
          text: 'Cancel',
          style: 'cancel',
        },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            setIsDeleting(true);
            await deleteHandlerFiles(models);
            onFilesDeleted();
            setIsDeleting(false);
          },
        },
      ]
    );
  };

  return (
    <Button title={isDeleting ? 'Deleting...' : 'Delete Invalid Files'} onPress={handleDeleteFiles} color={QColors.Error} />
  );
};
