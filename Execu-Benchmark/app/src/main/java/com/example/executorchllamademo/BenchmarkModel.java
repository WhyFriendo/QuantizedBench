/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

public class BenchmarkModel {
    private String filename;
    private String architecture;
    private String tokenizerPath;
    private String modelUrl;
    private String tokenizerUrl;
    private BenchmarkStatus status;
    private int downloadProgress;

    public enum BenchmarkStatus {
        NOT_DOWNLOADED,
        DOWNLOADING,
        READY,
        LOADING,
        RUNNING,
        COMPLETED,
        ERROR
    }

    public BenchmarkModel(String filename, String architecture, String tokenizerPath, String modelUrl,
            String tokenizerUrl) {
        this.filename = filename;
        this.architecture = architecture;
        this.tokenizerPath = tokenizerPath;
        this.modelUrl = modelUrl;
        this.tokenizerUrl = tokenizerUrl;
        this.status = BenchmarkStatus.NOT_DOWNLOADED;
        this.downloadProgress = 0;
    }

    public String getFilename() {
        return filename;
    }

    public String getArchitecture() {
        return architecture;
    }

    public String getTokenizerPath() {
        return tokenizerPath;
    }

    public String getModelUrl() {
        return modelUrl;
    }

    public String getTokenizerUrl() {
        return tokenizerUrl;
    }

    public BenchmarkStatus getStatus() {
        return status;
    }

    public void setStatus(BenchmarkStatus status) {
        this.status = status;
    }

    public int getDownloadProgress() {
        return downloadProgress;
    }

    public void setDownloadProgress(int progress) {
        this.downloadProgress = progress;
    }

    public String getStatusText() {
        switch (status) {
            case NOT_DOWNLOADED:
                return "Not Downloaded";
            case DOWNLOADING:
                return "Downloading... " + downloadProgress + "%";
            case READY:
                return "Ready";
            case LOADING:
                return "Loading...";
            case RUNNING:
                return "Running benchmark...";
            case COMPLETED:
                return "Completed";
            case ERROR:
                return "Error";
            default:
                return "Unknown";
        }
    }

    public int getStatusColor() {
        switch (status) {
            case NOT_DOWNLOADED:
                return 0xFFFFA500; // Orange
            case DOWNLOADING:
                return 0xFF0066CC; // Blue
            case READY:
                return 0xFF007700; // Green
            case LOADING:
            case RUNNING:
                return 0xFF0066CC; // Blue
            case COMPLETED:
                return 0xFF006600; // Dark green
            case ERROR:
                return 0xFFCC0000; // Red
            default:
                return 0xFF666666; // Gray
        }
    }

    public static BenchmarkModel[] getDefaultModels() {
        return new BenchmarkModel[] {
                new BenchmarkModel(
                        "gemma3_270m_8da4w.pte",
                        "Gemma 3 270M ARCHITECTURE (8da4w)",
                        "gemma3_270m_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-270M-it-ExecuTorch/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-270M-it-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "gemma3_270m_8da8w.pte",
                        "Gemma 3 270M ARCHITECTURE (8da8w)",
                        "gemma3_270m_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-270M-it-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-270M-it-ExecuTorch-8da8w/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "gemma3_270m_fp32.pte",
                        "Gemma 3 270M ARCHITECTURE (FP32)",
                        "gemma3_270m_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-270M-it-ExecuTorch-baseline-fp32/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-270M-it-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "gemma3_1b_8da4w.pte",
                        "Gemma 3 1B ARCHITECTURE (8da4w)",
                        "gemma3_1b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-1B-it-ExecuTorch/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-1B-it-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "gemma3_1b_8da8w.pte",
                        "Gemma 3 1B ARCHITECTURE (8da8w)",
                        "gemma3_1b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-1B-it-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-1B-it-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "gemma3_1b_fp32.pte",
                        "Gemma 3 1B ARCHITECTURE (FP32)",
                        "gemma3_1b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-1B-it-ExecuTorch-baseline-fp32/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-1B-it-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "gemma3_4b_8da4w.pte",
                        "Gemma 3 4B ARCHITECTURE (8da4w)",
                        "gemma3_4b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-4B-it-ExecuTorch-8da4w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-4B-it-ExecuTorch-8da4w/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "gemma3_4b_8da8w.pte",
                        "Gemma 3 4B ARCHITECTURE (8da8w)",
                        "gemma3_4b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-4B-it-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-4B-it-ExecuTorch-8da8w/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "gemma3_4b_bf16.pte",
                        "Gemma 3 4B ARCHITECTURE (BF16)",
                        "gemma3_4b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Gemma-3-4B-it-ExecuTorch-baseline-bf16/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Gemma-3-4B-it-ExecuTorch-baseline-bf16/resolve/main/tokenizer.json"),

                new BenchmarkModel(
                        "llama3_2_1b_instruct_8da4w.pte",
                        "LLAMA 3.2 1B ARCHITECTURE (8da4w)",
                        "llama3_2_1b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-1B-Instruct-ExecuTorch/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-1B-Instruct-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "llama3_2_1b_instruct_8da8w.pte",
                        "LLAMA 3.2 1B ARCHITECTURE (8da8w)",
                        "llama3_2_1b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-1B-Instruct-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-1B-Instruct-ExecuTorch-8da8w/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "llama3_2_1b_instruct_4w.pte",
                        "LLAMA 3.2 1B ARCHITECTURE (4w)",
                        "llama3_2_1b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-1B-Instruct-ExecuTorch-4w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-1B-Instruct-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "llama3_2_1b_instruct_fp32.pte",
                        "LLAMA 3.2 1B ARCHITECTURE (FP32)",
                        "llama3_2_1b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-1B-Instruct-ExecuTorch-baseline-fp32/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-1B-Instruct-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "llama3_2_3b_instruct_8da4w.pte",
                        "LLAMA 3.2 3B ARCHITECTURE (8da4w)",
                        "llama3_2_3b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-3B-Instruct-ExecuTorch/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-3B-Instruct-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "llama3_2_3b_instruct_8da8w.pte",
                        "LLAMA 3.2 3B ARCHITECTURE (8da8w)",
                        "llama3_2_3b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-3B-Instruct-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-3B-Instruct-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "llama3_2_3b_instruct_bf16.pte",
                        "LLAMA 3.2 3B ARCHITECTURE (BF16)",
                        "llama3_2_3b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-3B-Instruct-ExecuTorch-baseline-bf16/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.2-3B-Instruct-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "llama3_1_8b_instruct_8da4w.pte",
                        "LLAMA 3.1 8B ARCHITECTURE (8da4w)",
                        "llama3_1_8b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.1-8B-Instruct-ExecuTorch-8da4w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.1-8B-Instruct-ExecuTorch-8da4w/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "llama3_1_8b_instruct_8da8w.pte",
                        "LLAMA 3.1 8B ARCHITECTURE (8da8w)",
                        "llama3_1_8b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Llama-3.1-8B-Instruct-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Llama-3.1-8B-Instruct-ExecuTorch-8da8w/resolve/main/tokenizer.json"),

                new BenchmarkModel(
                        "phi4_model_8da4w.pte",
                        "Phi-4 ARCHITECTURE (8da4w)",
                        "phi4_mini_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Phi-4-mini-instruct-ExecuTorch/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Phi-4-mini-instruct-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "phi4_model_8da8w.pte",
                        "Phi-4 ARCHITECTURE (8da8w)",
                        "phi4_mini_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Phi-4-mini-instruct-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Phi-4-mini-instruct-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "phi4_model_bf16.pte",
                        "Phi-4 ARCHITECTURE (BF16)",
                        "phi4_mini_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Phi-4-mini-instruct-ExecuTorch-baseline-bf16/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Phi-4-mini-instruct-ExecuTorch/resolve/main/tokenizer.json"),

                new BenchmarkModel(
                        "qwen3_0_6b_8da4w.pte",
                        "Qwen3 0.6B ARCHITECTURE (8da4w)",
                        "qwen3_0_6b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-0.6B-ExecuTorch/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-0.6B-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_0_6b_8da8w.pte",
                        "Qwen3 0.6B ARCHITECTURE (8da8w)",
                        "qwen3_0_6b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-0.6B-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-0.6B-ExecuTorch-8da8w/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_0_6b_fp32.pte",
                        "Qwen3 0.6B ARCHITECTURE (FP32)",
                        "qwen3_0_6b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-0.6B-ExecuTorch-baseline-fp32/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-0.6B-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_1_7b_8da4w.pte",
                        "Qwen3 1.7B ARCHITECTURE (8da4w)",
                        "qwen3_1_7b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-1.7B-ExecuTorch/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-1.7B-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_1_7b_8da8w.pte",
                        "Qwen3 1.7B ARCHITECTURE (8da8w)",
                        "qwen3_1_7b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-1.7B-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-1.7B-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_1_7b_fp32.pte",
                        "Qwen3 1.7B ARCHITECTURE (FP32)",
                        "qwen3_1_7b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-1.7B-ExecuTorch-baseline-fp32/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-1.7B-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_4b_8da4w.pte",
                        "Qwen3 4B ARCHITECTURE (8da4w)",
                        "qwen3_4b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-4B-ExecuTorch/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-4B-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_4b_8da8w.pte",
                        "Qwen3 4B ARCHITECTURE (8da8w)",
                        "qwen3_4b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-4B-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-4B-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_4b_bf16.pte",
                        "Qwen3 4B ARCHITECTURE (BF16)",
                        "qwen3_4b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-4B-ExecuTorch-baseline-bf16/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-4B-ExecuTorch/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_8b_8da4w.pte",
                        "Qwen3 8B ARCHITECTURE (8da4w)",
                        "qwen3_8b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-8B-ExecuTorch-8da4w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-8B-ExecuTorch-8da4w/resolve/main/tokenizer.json"),
                new BenchmarkModel(
                        "qwen3_8b_8da8w.pte",
                        "Qwen3 8B ARCHITECTURE (8da8w)",
                        "qwen3_8b_tokenizer.json",
                        "https://huggingface.co/WhyFriendo/Qwen3-8B-ExecuTorch-8da8w/resolve/main/model.pte",
                        "https://huggingface.co/WhyFriendo/Qwen3-8B-ExecuTorch-8da8w/resolve/main/tokenizer.json"), };
    }
}
