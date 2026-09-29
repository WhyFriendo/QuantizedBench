# GGUF scoring validation

Validated on 2026-09-20 with `qwen2.5-0.5b-instruct-q4_k_m.gguf`, llama.cpp
`b8940-78433f606`, temperature 0, CPU inference, and the repository's patched
`lm_eval/models/gguf.py`. The reference uses llama.cpp's `/v1/completions`
`logit_bias` to force each continuation token and reads its pre-sampling
log-probability, following the current upstream GGUF adapter.

| Task | Questions | Choices | Scored tokens | Outside top 20 | Changed answers |
| --- | ---: | ---: | ---: | ---: | ---: |
| tinyMMLU | 20 | 80 | 80 | 0 | 0 |
| tinyArc | 100 | 401 | 2,344 | 538 | 35 |

For tinyArc, the patched scorer selected the correct option on 29/100 questions;
exact scoring selected it on 33/100. The task's `gpirt` aggregation changed from
**0.3671** to **0.3909** (+2.38 percentage points). Of the 35 changed answers,
11 changed from wrong to correct, 7 from correct to wrong, and 17 changed
between two wrong options. No context/continuation token-boundary mismatches
were found for this Qwen model.

This is a method validation on one locally available model. The 253 EC2 GGUF
files are not available in this workspace, so their score changes have not
been measured. The validator can be run against any active `llama-server`:

```bash
python scripts/validate_gguf_scoring.py \
  --base-url http://127.0.0.1:8080 \
  --task tinyArc --questions 100 \
  --output reports/scoring_validation_ec2_model.json
```

The JSON files in this directory contain per-question choices, both scores,
top-20 misses, and the selected answers.
