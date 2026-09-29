/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import java.io.File;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

public class ModelListAdapter extends ArrayAdapter<BenchmarkModel> {
    
    public interface OnBenchmarkClickListener {
        void onBenchmarkClick(BenchmarkModel model);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(BenchmarkModel model);
    }
    
    private Context mContext;
    private List<BenchmarkModel> mModels;
    private OnBenchmarkClickListener mBenchmarkClickListener;
    private OnDeleteClickListener mDeleteClickListener;
    
    public ModelListAdapter(@NonNull Context context, @NonNull List<BenchmarkModel> models) {
        super(context, R.layout.model_list_item, models);
        this.mContext = context;
        this.mModels = models;
    }
    
    public void setBenchmarkClickListener(OnBenchmarkClickListener listener) {
        this.mBenchmarkClickListener = listener;
    }

    public void setDeleteClickListener(OnDeleteClickListener listener) {
        this.mDeleteClickListener = listener;
    }
    
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = LayoutInflater.from(mContext).inflate(R.layout.model_list_item, parent, false);
            holder = new ViewHolder();
            holder.modelFilename = convertView.findViewById(R.id.model_filename);
            holder.modelArchitecture = convertView.findViewById(R.id.model_architecture);
            holder.modelStatus = convertView.findViewById(R.id.model_status);
            holder.benchmarkButton = convertView.findViewById(R.id.benchmark_button);
            holder.deleteButton = convertView.findViewById(R.id.delete_model_button);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        
        BenchmarkModel model = mModels.get(position);
        
        // Set model information
        holder.modelFilename.setText(model.getFilename());
        holder.modelArchitecture.setText(model.getArchitecture());
        holder.modelStatus.setText(model.getStatusText());
        holder.modelStatus.setTextColor(model.getStatusColor());
        
        // Configure benchmark button
        holder.benchmarkButton.setEnabled(model.getStatus() == BenchmarkModel.BenchmarkStatus.READY || 
                                         model.getStatus() == BenchmarkModel.BenchmarkStatus.COMPLETED ||
                                         model.getStatus() == BenchmarkModel.BenchmarkStatus.NOT_DOWNLOADED ||
                                         model.getStatus() == BenchmarkModel.BenchmarkStatus.ERROR);
        
        if (model.getStatus() == BenchmarkModel.BenchmarkStatus.LOADING || 
            model.getStatus() == BenchmarkModel.BenchmarkStatus.RUNNING) {
            holder.benchmarkButton.setText("Running...");
            holder.benchmarkButton.setEnabled(false);
        } else if (model.getStatus() == BenchmarkModel.BenchmarkStatus.DOWNLOADING) {
            holder.benchmarkButton.setText("Downloading... " + model.getDownloadProgress() + "%");
            holder.benchmarkButton.setEnabled(false);
        } else if (model.getStatus() == BenchmarkModel.BenchmarkStatus.NOT_DOWNLOADED) {
            holder.benchmarkButton.setText("Download");
        } else if (model.getStatus() == BenchmarkModel.BenchmarkStatus.ERROR) {
            holder.benchmarkButton.setText(R.string.retry_download);
        } else {
            holder.benchmarkButton.setText("Run Benchmark");
        }
        
        // Set click listener for benchmark button
        holder.benchmarkButton.setOnClickListener(v -> {
            if (mBenchmarkClickListener != null) {
                mBenchmarkClickListener.onBenchmarkClick(model);
            }
        });

        File storage = mContext.getExternalFilesDir(null);
        File modelFile = storage == null ? null : new File(new File(storage, "llama"), model.getFilename());
        boolean canDelete = modelFile != null && modelFile.exists()
                && model.getStatus() != BenchmarkModel.BenchmarkStatus.DOWNLOADING
                && model.getStatus() != BenchmarkModel.BenchmarkStatus.LOADING
                && model.getStatus() != BenchmarkModel.BenchmarkStatus.RUNNING;
        holder.deleteButton.setVisibility(canDelete ? View.VISIBLE : View.GONE);
        holder.deleteButton.setOnClickListener(v -> {
            if (mDeleteClickListener != null) {
                mDeleteClickListener.onDeleteClick(model);
            }
        });
        
        return convertView;
    }
    
    static class ViewHolder {
        TextView modelFilename;
        TextView modelArchitecture;
        TextView modelStatus;
        Button benchmarkButton;
        Button deleteButton;
    }
    
    public void updateModel(BenchmarkModel model) {
        notifyDataSetChanged();
    }
}
