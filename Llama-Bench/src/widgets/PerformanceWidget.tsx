import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet, Platform } from 'react-native';
import { QColors } from '../colors';
import { getMetrics } from '../metrics';

interface PerformanceWidgetProps {
  isActive: boolean;
}

export const PerformanceWidget: React.FC<PerformanceWidgetProps> = ({ isActive }) => {
  const [metrics, setMetrics] = useState({
    ram: 'N/A',
    temp: 'N/A',
    usedMemory: 'N/A',
  });

  useEffect(() => {
    let interval: NodeJS.Timeout | null = null;

    const updateMetrics = async () => {
      try {
        const { ramUsage, usedMemory, batteryTemperature } = await getMetrics();

        setMetrics({
          ram: ramUsage?.toFixed(2) + '%',
          temp: batteryTemperature?.toFixed(2) + '°C',
          usedMemory: usedMemory?.toFixed(0) + 'MB',
        });
      } catch (error) {
        console.error('Error fetching metrics:', error);
      }
    };
    if (isActive) {
      interval = setInterval(updateMetrics, 2000);
    }

    return () => {
      if (interval) {
        clearInterval(interval);
      }
    };
  }, [isActive]);

  return (
    <View style={styles.container}>
      <Text style={styles.text}>
        RAM: {metrics.ram} ({metrics.usedMemory}) | TEMP: {metrics.temp}
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: QColors.Dark,
    padding: 4,
    paddingHorizontal: 8,
  },
  text: {
    color: '#fff',
    fontSize: 12,
    fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace' }),
  },
});
