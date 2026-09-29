import json
import re

with open('mlc-package-config.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

models = data.get('model_list', [])

# Group models by family. Family is determined by a prefix (e.g., Llama, Qwen, Phi, Mistral, gemma).
family_map = {}
for m in models:
    model_id = m['model_id'].lower()
    
    if 'phi' in model_id:
        family = 'phi'
    elif 'qwen' in model_id:
        family = 'qwen'
    elif 'gemma' in model_id:
        family = 'gemma'
    elif 'llama' in model_id:
        family = 'llama'
    elif 'mistral' in model_id:
        family = 'mistral'
    elif 'rwkv' in model_id:
        family = 'rwkv'
    elif 'hermes' in model_id:
        family = 'hermes'
    elif 'yi-' in model_id:
        family = 'yi'
    else:
        # Fallback: take characters before the first dash or number
        match = re.match(r'([a-zA-Z]+)', model_id)
        family = match.group(1) if match else 'other'

    if family not in family_map:
        family_map[family] = []
    family_map[family].append(m)

# Find smallest model for each family
smallest_models = []
commented_models = []

for family, family_models in family_map.items():
    # Sort by estimated_vram_bytes to find smallest
    family_models.sort(key=lambda x: x.get('estimated_vram_bytes', float('inf')))
    smallest_models.append(family_models[0])
    
    # Add the rest to commented_models
    for m in family_models[1:]:
        commented_models.append(m)

# Re-assign data
data['model_list'] = smallest_models
data['commented_model_list'] = commented_models

with open('mlc-package-config.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=4)

print(f"Kept {len(smallest_models)} models:")
for m in smallest_models:
    vram = m.get('estimated_vram_bytes', 0)
    print(f" - {m['model_id']} ({vram / (1024**3):.2f} GB)")
print(f"Moved {len(commented_models)} models to 'commented_model_list'.")
