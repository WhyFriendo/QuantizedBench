import config from '../config';

export interface CloudflareAPI {
  saveBenchmark: (userId: string, model: string, family: string, data: string, phoneData: string) => Promise<boolean>,
}

export const useCloudflare = (): CloudflareAPI => {
  const saveBenchmark = async (userId: string, model: string, family: string, data: string, phoneData: string) => {
    console.log('saveBenchmark', userId, model, family, data, phoneData);
    const result = await fetch(`${config.API_URL}/saveBenchmark`, {
      method: 'POST',
      body: JSON.stringify({ userId, model, family, data, phoneData, version: config.VERSION }),
      headers: {
        'X-PROTECT-KEY': config.API_KEY,
      },
    });
    console.log('saveBenchmark result', result.status);
    return result.ok;
  };

  return { saveBenchmark };
}
