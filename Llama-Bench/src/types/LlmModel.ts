export interface LlmModel {
  name: string;
  filename: string;
  url: string;
  memory: number;
  family: string;
  sha256: string;
  chat_template: string;
}
