import developmentConfig from './env/env.development';
import productionConfig from './env/env.production';

const config = __DEV__ ? developmentConfig : productionConfig;

export default config;
