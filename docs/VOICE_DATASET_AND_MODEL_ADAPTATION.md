# FILTR Voice Ordering: Dataset Design, Acoustic/LM Adaptation, & Fine-Tuning Roadmap

> Permanent architectural reference created by `@data_planner`.

See complete blueprint in [voice_dataset_and_model_adaptation.md](file:///C:/Users/m_hit/.gemini/antigravity/brain/a0ea7bb4-b8dc-417c-928f-c7ed70c45f91/voice_dataset_and_model_adaptation.md).

## Key Pillars:
1. **Audio WAV Standard**: 16kHz Mono 16-bit PCM, normalized to -20 LUFS / -1.0 dBFS Peak.
2. **Collection & Augmentation Pipeline**: Boundary mic + Android dual-mic array; 50k synthetic Hinglish utterances via multi-speaker Neural TTS; pyroomacoustics RIR (RT60 ~0.52s) + in-situ noise stems (steam wand, bean grinder, blender, patron babble) mixed at 5-25 dB SNR.
3. **Model Adaptation**:
   - Rejection of Vosk constrained phrase grammar (which causes OOV graph collapse & hallucination).
   - Selection of **Sherpa-ONNX Zipformer / Whisper-tiny TFLite** as on-device Tier 1 (<300ms latency, 100% offline).
   - Dynamic hotword biasing for zero-shot new menu items.
   - **Gemini 2.5 Flash / 3.5 Flash Lite** as cloud Tier 2 fallback for low-confidence turns / complex disfluencies (<800ms).
4. **20-Sentence Benchmark Dataset**: Complete test suite covering multi-item orders, Hindi numerals, cafe noise stems, modifiers, and self-corrections with full TypeScript/JSON schema.
