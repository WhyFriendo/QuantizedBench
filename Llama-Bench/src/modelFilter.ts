import { LlmModel } from './types/LlmModel';
import { Config } from './types/Config';

export const filterModelsByConfig = (models: LlmModel[], cfg: Config): LlmModel[] => {
  const families = cfg.MODEL_FAMILIES;
  return models.filter((model) => {
    const allowedQuants = families[model.family];
    if (!allowedQuants) return false;
    if (allowedQuants.length === 0) return true;
    return allowedQuants.includes(model.filename);
  });
};


