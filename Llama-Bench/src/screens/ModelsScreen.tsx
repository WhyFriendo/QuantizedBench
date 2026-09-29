import Svg, { Path, G } from 'react-native-svg';
import { SvgIconProps } from '../types/SvgIconProps';
import { SAV } from '../widgets/SAV';
import { LlmManager } from '../widgets/LlmManager';
import { DeleteLocalFilesWidget } from '../widgets/DeleteLocalFilesWidget';
import { llmModels } from '../models';
import config from '../config';
import { filterModelsByConfig } from '../modelFilter';
import { useState } from 'react';

export const ModelsScreenIcon: React.FC<SvgIconProps> = ({ color, size }) => (
  <Svg width={size} height={size} viewBox="0 0 96.903 96.904">
    <G>
      <Path fill={color} d="M96.123,52.261l-22.984-8.418v-25.62c0-0.494-0.313-0.934-0.781-1.096L48.826,8.508c-0.244-0.086-0.518-0.084-0.762,0
        l-23.488,8.381c-0.237,0.084-0.441,0.238-0.542,0.398c-0.172,0.207-0.267,0.471-0.267,0.738v25.807l-22.958,8.19
        c-0.237,0.085-0.441,0.239-0.542,0.399C0.095,52.628,0,52.892,0,53.16v26.043c0,0.49,0.313,0.932,0.778,1.094l23.502,8.1
        c0.245,0.084,0.518,0.084,0.763,0l23.409-8.062l23.36,8.062c0.244,0.084,0.518,0.084,0.763,0l23.508-8.11
        c0.489-0.15,0.82-0.596,0.82-1.109V53.357C96.903,52.863,96.592,52.423,96.123,52.261z M69.042,42.082l-18.063,6.113V28.822
        l18.063-6.625V42.082z M48.444,12.712l16.801,6.277l-16.781,6.215l-17.73-6.119L48.444,12.712z M24.698,60.339L6.968,54.22
        l17.711-6.373l16.801,6.277L24.698,60.339z M45.276,77.216L27.212,83.33V63.957l18.064-6.625V77.216z M72.229,60.339L54.499,54.22
        l17.711-6.373l16.802,6.277L72.229,60.339z M92.807,77.216L74.743,83.33V63.957l18.063-6.625V77.216z"/>
    </G>
  </Svg>
);


function ModelsScreen() {
  const [models, setModels] = useState(filterModelsByConfig(llmModels, config));

  const onFilesDeleted = () => {
    setModels(filterModelsByConfig(llmModels, config));
  };

  return (
    <SAV>
      <DeleteLocalFilesWidget models={models} onFilesDeleted={onFilesDeleted} />
      <LlmManager models={models} />
    </SAV>
  );
}

export default ModelsScreen;
