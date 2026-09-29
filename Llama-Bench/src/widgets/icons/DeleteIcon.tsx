import Svg, { Path } from 'react-native-svg';
import { SvgIconProps } from '../../types/SvgIconProps';

export const DeleteIcon: React.FC<SvgIconProps> = ({ color = 'rgba(0, 0, 0, 1)', size = 24 }) => (
  <Svg
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill={color}
  >
    <Path d="M5 20a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8h2V6h-4V4a2 2 0 0 0-2-2H9a2 2 0 0 0-2 2v2H3v2h2zM9 4h6v2H9zM8 8h9v12H7V8z" />
    <Path d="M9 10h2v8H9zm4 0h2v8h-2z" />
  </Svg>
);
