"""
Self-Healing Backend System (SHBS) - Predictive ML Model Training
================================================================

Instructions for Interview / Demo:
1. Go to Google Colab (https://colab.research.google.com/)
2. Create a new Notebook and paste this entire script into a cell.
3. Run the cell.
4. It will generate synthetic telemetry data, train a Random Forest model,
   and export it as 'predictive_model.onnx'.
5. Download 'predictive_model.onnx' from Colab and place it in the
   backend/src/main/resources/ directory of your Spring Boot project.
"""

# 1. Install required packages in Colab
import os
os.system("pip install scikit-learn skl2onnx onnxruntime pandas numpy")

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

# 2. Load Real Kaggle Dataset
print("Loading real Kaggle dataset...")
df = pd.read_csv('distributed_system_architecture_stress_dataset.csv')

# Map the columns to match our ONNX input expectations
# The Java app sends: cpu_usage, memory_usage, latency_ms, error_rate
X = df[['cpu_utilization_percent', 'memory_utilization_percent', 'avg_latency_ms', 'error_rate_percent']].copy()

# Rename columns to exactly match what the ONNX exporter expects 
# (though ONNX cares more about array order, it's good practice)
X.columns = ['cpu_usage', 'memory_usage', 'latency_ms', 'error_rate']

# Convert error_rate_percent to a decimal (0.0 - 1.0) to match our Java code
X['error_rate'] = X['error_rate'] / 100.0

# The target variable is 'system_state'. 
# 'healthy' -> 0
# 'degraded' or 'failed' -> 1
y = (df['system_state'] != 'healthy').astype(int)

print(f"Dataset loaded successfully. Shape: {X.shape}")
print(f"Failure samples: {y.sum()} / {len(y)}")

# 3. Train the Model
print("Training Random Forest Classifier...")
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

clf = RandomForestClassifier(n_estimators=50, max_depth=5, random_state=42)
clf.fit(X_train, y_train)

score = clf.score(X_test, y_test)
print(f"Model Accuracy on Test Set: {score * 100:.2f}%")

# 4. Convert to ONNX format
print("Converting model to ONNX format...")
# Define the input types for ONNX (4 float features)
initial_type = [('float_input', FloatTensorType([None, 4]))]
onx = convert_sklearn(clf, initial_types=initial_type)

# 5. Save the model
model_filename = "predictive_model.onnx"
with open(model_filename, "wb") as f:
    f.write(onx.SerializeToString())

print(f"✅ Success! Model saved as '{model_filename}'")
print("Download this file from Colab and place it in your project's src/main/resources/ folder.")
