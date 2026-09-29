import { ReactNode } from 'react';
import { StyleSheet, ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { QColors } from '../colors';

interface SAVProps {
  children: ReactNode;
  style?: ViewStyle;
}

export const SAV: React.FC<SAVProps> = ({ children, style }) => (
  <SafeAreaView style={[styles.container, style]}>
    {children}
  </SafeAreaView>
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: QColors.Gray,
  },
});
