import { NativeModules } from 'react-native';
import { getTotalMemory, getBatteryLevel, getModel, getBrand, getSystemName, getSystemVersion } from 'react-native-device-info';
const { TemperatureModule, MemoryModule, BatteryModule } = NativeModules;

export const getMetrics = async () => {
  const [
    totalMemory,
    batteryTemperature,
    batteryLevel,
    usedMemoryMb,
    allTemperatures,
    batteryInfos,
  ] = await Promise.all([
    getTotalMemory(),
    TemperatureModule.getBatteryTemperature(),
    getBatteryLevel(),
    MemoryModule.getMemoryInfo(),
    TemperatureModule.getThermalInfo(),
    BatteryModule.getPreciseBatteryCapacity(),
  ]);

  const totalMemoryMb = totalMemory / 1024.0 / 1024;

  return {
    ramUsage: (usedMemoryMb / totalMemoryMb) * 100,
    usedMemory: usedMemoryMb,
    allTemperatures,
    batteryTemperature,
    batteryLevel,
    batteryInfos,
  };
};

export const getUsedRam = (): Promise<number> => {
  return MemoryModule.getMemoryInfo();
};

export const getPhoneInfo = async () => {
  const deviceModel = getModel();
  const deviceBrand = getBrand();
  const systemName = getSystemName();
  const systemVersion = getSystemVersion();
  const totalMemory = await getTotalMemory();

  return {
    deviceModel,
    deviceBrand,
    systemName,
    systemVersion,
    totalMemory,
  };
};
