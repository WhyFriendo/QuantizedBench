import { ScrollView, StyleSheet, Text, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { SvgIconProps } from '../types/SvgIconProps';
import { SAV } from '../widgets/SAV';
import { Accordion, AccordionData } from '../widgets/Accordion';
import React from 'react';
import { QColors } from '../colors';
import config from '../config';

export const InfoScreenIcon: React.FC<SvgIconProps> = ({ color, size }) => (
  <Svg width={size} height={size} viewBox="0 0 24 24">
    <Path fill={color} d="M20 3H4a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1V4a1 1 0 0 0-1-1zm-1 16H5V5h14v14z" />
    <Path fill={color} d="M11 7h2v2h-2zm0 4h2v6h-2z" />
  </Svg>
);

interface UnorderedListProps { items: string[]; }
const UnorderedList: React.FC<UnorderedListProps> = ({ items }) => {
  return (
    <View style={styles.listContainer}>
      {items.map((item, index) => (
        <View key={index} style={styles.listItem}>
          <Text style={styles.bullet}>{'\u2022'}</Text>
          <Text style={styles.itemText}>{item}</Text>
        </View>
      ))}
    </View>
  );
};

const accordions: AccordionData[] = [
  {
    title: 'Please read before running benchmarks',
    content: <UnorderedList items={[
      'During benchmarking, please avoid using your phone for other tasks. Ensure the application remains in the foreground (should be visible, not in the background).',
      'You can pause the benchmarking process at any time, but it is recommended to do so only when the model is being downloaded.',
      'Loading the model requires substantial resources, so please close any other apps running in the background beforehand.',
      'Many smartphones have battery-saving modes that may affect the results. Set your phone to "High Performance" mode (or an equivalent setting) for the best accuracy.',
      'As the process can take a significant amount of time, it is advised to keep your phone connected to a charger.',
      'Ensure your internet connection is stable before starting the benchmarks.',
      'Run benchmarks on Wi-Fi to avoid using up your mobile data, as the models sizes are substantial.',
    ]}/>,
  },
  {
    title: 'What data will be collected?',
    content: <UnorderedList items={[
      'Details about your phone, including brand, model, system information, and specifications.',
      'Performance metrics recorded during the benchmarking of the large language models (LLMs).',
      'Hardware metrics such as battery level, temperature, and memory usage.',
    ]}/>,
  },
];

function InfoScreen() {
  return (
    <SAV>
      <View style={styles.container}>
        <ScrollView
          contentInsetAdjustmentBehavior="automatic"
          style={styles.scrollView}>
            <Accordion data={accordions} />
        </ScrollView>
        <View style={styles.versionContainer}>
          <Text style={styles.versionText}>Version {config.VERSION}</Text>
        </View>
      </View>
    </SAV>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  versionContainer: {
    paddingHorizontal: 20,
    paddingVertical: 15,
    alignItems: 'center',
    borderTopWidth: 1,
    borderTopColor: QColors.LightGray + '20',
  },
  versionText: {
    fontSize: 14,
    color: QColors.LightGray,
    fontWeight: '400',
    opacity: 0.7,
  },
  listContainer: {
    marginVertical: 10,
  },
  listItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  bullet: {
    marginRight: 8,
    fontSize: 16,
    lineHeight: 20,
    color: QColors.LightGray,
  },
  itemText: {
    flex: 1,
    fontSize: 16,
    lineHeight: 22,
    color: QColors.LightGray,
  },
});

export default InfoScreen;