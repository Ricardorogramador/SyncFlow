import io
import os
from pathlib import Path
from uuid import UUID

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, File, HTTPException, UploadFile
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

app = FastAPI(title="SyncFlow Prediction Service")

MODEL_DIR = Path(os.getenv("MODEL_DIR", "/models"))
MAX_FILE_BYTES = 10 * 1024 * 1024

BASE_COLUMNS = {
    "start_time",
    "end_time",
    "assigned_at",
    "is_recurring",
    "group_size",
    "daily_load_before",
    "weekly_load_before",
}
TARGET_COLUMN = "has_conflict"

FEATURE_COLUMNS = [
    "start_hour",
    "weekday",
    "duration_minutes",
    "anticipation_hours",
    "is_recurring",
    "group_size",
    "daily_load_before",
    "weekly_load_before",
]


def model_path(organization_id: UUID) -> Path:
    return MODEL_DIR / f"{organization_id}.joblib"


def parse_boolean_column(series: pd.Series) -> pd.Series:
    normalized = series.astype(str).str.strip().str.lower()
    values = {
        "true": 1,
        "false": 0,
        "1": 1,
        "0": 0,
        "yes": 1,
        "no": 0,
        "si": 1,
        "sí": 1,
    }

    parsed = normalized.map(values)

    if parsed.isna().any():
        raise HTTPException(
            status_code=400,
            detail="is_recurring debe contener true/false, 1/0, yes/no o si/no",
        )

    return parsed.astype(int)


def parse_target_column(series: pd.Series) -> pd.Series:
    normalized = series.astype(str).str.strip().str.lower()
    values = {
        "true": 1,
        "false": 0,
        "1": 1,
        "0": 0,
        "yes": 1,
        "no": 0,
        "si": 1,
        "sí": 1,
    }

    parsed = normalized.map(values)

    if parsed.isna().any():
        raise HTTPException(
            status_code=400,
            detail="has_conflict debe contener true/false, 1/0, yes/no o si/no",
        )

    return parsed.astype(int)


def load_csv(contents: bytes, training: bool) -> pd.DataFrame:
    if len(contents) > MAX_FILE_BYTES:
        raise HTTPException(
            status_code=413,
            detail="El CSV supera el límite de 10 MB",
        )

    try:
        frame = pd.read_csv(io.BytesIO(contents))
    except Exception as exc:
        raise HTTPException(
            status_code=400,
            detail="No se pudo leer el CSV. Comprueba que sea un CSV separado por comas.",
        ) from exc

    required = set(BASE_COLUMNS)

    if training:
        required.add(TARGET_COLUMN)
    elif TARGET_COLUMN in frame.columns:
        raise HTTPException(
            status_code=400,
            detail="Para predecir usa un CSV sin la columna has_conflict",
        )

    missing = required - set(frame.columns)

    if missing:
        raise HTTPException(
            status_code=400,
            detail=f"Faltan columnas requeridas: {', '.join(sorted(missing))}",
        )

    if frame.empty:
        raise HTTPException(
            status_code=400,
            detail="El CSV no contiene filas",
        )

    if frame[list(required)].isna().any().any():
        raise HTTPException(
            status_code=400,
            detail="Hay valores vacíos en columnas requeridas",
        )

    if len(frame) > 100_000:
        raise HTTPException(
            status_code=413,
            detail="El CSV supera el límite de 100.000 filas",
        )

    return frame


def build_features(frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()

    try:
        start = pd.to_datetime(
            result["start_time"],
            errors="raise",
            utc=True,
        )
        end = pd.to_datetime(
            result["end_time"],
            errors="raise",
            utc=True,
        )
        assigned = pd.to_datetime(
            result["assigned_at"],
            errors="raise",
            utc=True,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=400,
            detail="start_time, end_time y assigned_at deben ser fechas ISO válidas",
        ) from exc

    result["start_hour"] = start.dt.hour
    result["weekday"] = start.dt.dayofweek
    result["duration_minutes"] = (
                                         end - start
                                 ).dt.total_seconds() / 60
    result["anticipation_hours"] = (
                                           start - assigned
                                   ).dt.total_seconds() / 3600
    result["is_recurring"] = parse_boolean_column(
        result["is_recurring"]
    )

    numeric_columns = [
        "group_size",
        "daily_load_before",
        "weekly_load_before",
    ]

    for column in numeric_columns:
        result[column] = pd.to_numeric(
            result[column],
            errors="coerce",
        )

    if result[numeric_columns].isna().any().any():
        raise HTTPException(
            status_code=400,
            detail="group_size y las cargas diaria/semanal deben ser numéricas",
        )

    if (result["duration_minutes"] <= 0).any():
        raise HTTPException(
            status_code=400,
            detail="end_time debe ser posterior a start_time",
        )

    if (result["anticipation_hours"] < 0).any():
        raise HTTPException(
            status_code=400,
            detail="assigned_at no puede ser posterior a start_time",
        )

    if (
            (result["group_size"] < 1).any()
            or (result["daily_load_before"] < 0).any()
            or (result["weekly_load_before"] < 0).any()
    ):
        raise HTTPException(
            status_code=400,
            detail="group_size debe ser mayor que cero y las cargas no negativas",
        )

    features = result[FEATURE_COLUMNS].astype(float)

    if not np.isfinite(features.to_numpy()).all():
        raise HTTPException(
            status_code=400,
            detail="El CSV contiene valores numéricos inválidos",
        )

    return features


def risk_level(probability: float) -> str:
    if probability < 0.30:
        return "LOW"
    if probability < 0.60:
        return "MEDIUM"
    return "HIGH"


def summarize_predictions(
        row_numbers: list[int],
        probabilities: np.ndarray,
) -> tuple[list[dict], dict]:
    predictions = []
    counts = {
        "LOW": 0,
        "MEDIUM": 0,
        "HIGH": 0,
    }

    for row_number, probability in zip(
            row_numbers,
            probabilities,
    ):
        level = risk_level(float(probability))
        counts[level] += 1

        predictions.append(
            {
                "rowNumber": int(row_number),
                "riskProbabilityPct": round(
                    float(probability) * 100,
                    1,
                    ),
                "riskLevel": level,
            }
        )

    return predictions, counts


def calculate_metrics(
        model,
        x_test: pd.DataFrame,
        y_test: pd.Series,
) -> dict:
    probabilities = model.predict_proba(x_test)[:, 1]
    predictions = (probabilities >= 0.5).astype(int)

    return {
        "precision": round(
            float(
                precision_score(
                    y_test,
                    predictions,
                    zero_division=0,
                )
            ),
            4,
        ),
        "recall": round(
            float(
                recall_score(
                    y_test,
                    predictions,
                    zero_division=0,
                )
            ),
            4,
        ),
        "f1": round(
            float(
                f1_score(
                    y_test,
                    predictions,
                    zero_division=0,
                )
            ),
            4,
        ),
        "rocAuc": round(
            float(roc_auc_score(y_test, probabilities)),
            4,
        ),
    }


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/models/{organization_id}/train")
async def train_model(
        organization_id: UUID,
        file: UploadFile = File(...),
):
    if not file.filename or not file.filename.lower().endswith(".csv"):
        raise HTTPException(
            status_code=400,
            detail="Sube un archivo con extensión .csv",
        )

    contents = await file.read()
    frame = load_csv(contents, training=True)

    if len(frame) < 40:
        raise HTTPException(
            status_code=400,
            detail="Se requieren al menos 40 filas etiquetadas para entrenar",
        )

    x = build_features(frame)
    y = parse_target_column(frame[TARGET_COLUMN])

    class_counts = y.value_counts()

    if len(class_counts) != 2 or int(class_counts.min()) < 4:
        raise HTTPException(
            status_code=400,
            detail="El CSV debe contener al menos cuatro ejemplos de cada clase",
        )

    row_indices = np.arange(len(frame))

    x_train, x_test, y_train, y_test, _, test_indices = train_test_split(
        x,
        y,
        row_indices,
        test_size=0.25,
        random_state=42,
        stratify=y,
    )

    logistic = make_pipeline(
        StandardScaler(),
        LogisticRegression(
            max_iter=1000,
            class_weight="balanced",
            random_state=42,
        ),
    )

    forest = RandomForestClassifier(
        n_estimators=300,
        min_samples_leaf=2,
        class_weight="balanced_subsample",
        random_state=42,
        n_jobs=-1,
    )

    logistic.fit(x_train, y_train)
    forest.fit(x_train, y_train)

    logistic_metrics = calculate_metrics(
        logistic,
        x_test,
        y_test,
    )
    forest_metrics = calculate_metrics(
        forest,
        x_test,
        y_test,
    )

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "model": forest,
            "feature_columns": FEATURE_COLUMNS,
            "model_name": "Random Forest",
        },
        model_path(organization_id),
    )

    probabilities = forest.predict_proba(x_test)[:, 1]
    test_rows, risk_counts = summarize_predictions(
        (test_indices + 2).tolist(),
        probabilities,
    )

    for prediction, actual in zip(
            test_rows,
            y_test.loc[test_indices].tolist(),
    ):
        prediction["actualConflict"] = bool(actual)

    return {
        "organizationId": str(organization_id),
        "selectedModel": "Random Forest",
        "trainingRows": int(len(x_train)),
        "testRows": int(len(x_test)),
        "metrics": {
            "randomForest": forest_metrics,
            "logisticRegression": logistic_metrics,
        },
        "riskCounts": risk_counts,
        "testPredictions": test_rows,
    }


@app.post("/models/{organization_id}/predict")
async def predict(
        organization_id: UUID,
        file: UploadFile = File(...),
):
    if not file.filename or not file.filename.lower().endswith(".csv"):
        raise HTTPException(
            status_code=400,
            detail="Sube un archivo con extensión .csv",
        )

    path = model_path(organization_id)

    if not path.exists():
        raise HTTPException(
            status_code=409,
            detail="Primero entrena un modelo con un CSV etiquetado",
        )

    contents = await file.read()
    frame = load_csv(contents, training=False)
    features = build_features(frame)

    try:
        saved = joblib.load(path)
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail="No se pudo cargar el modelo de esta organización",
        ) from exc

    if saved.get("feature_columns") != FEATURE_COLUMNS:
        raise HTTPException(
            status_code=500,
            detail="La versión del modelo no coincide con sus variables",
        )

    probabilities = saved["model"].predict_proba(features)[:, 1]
    predictions, risk_counts = summarize_predictions(
        (np.arange(len(frame)) + 2).tolist(),
        probabilities,
    )

    return {
        "organizationId": str(organization_id),
        "model": saved["model_name"],
        "totalRows": int(len(frame)),
        "riskCounts": risk_counts,
        "predictions": predictions,
    }