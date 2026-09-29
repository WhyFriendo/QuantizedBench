# Llama.rn App

This app tests LLM inference metrics using the llama.cpp framework via llama.rn.

## Environment Files

Configuration is managed through environment files:
- `src/env/env.development.ts` for development settings.
- `src/env/env.production.ts` for production settings.

These files define API config, enabled models, benchmark and conversation settings.

## Configurable Models

Models are configurable in `src/models.ts`, which lists available Llama models including various quantization levels for Llama 3.1 8B and Llama 3.2 3B families. You can modify this file to include more models.
