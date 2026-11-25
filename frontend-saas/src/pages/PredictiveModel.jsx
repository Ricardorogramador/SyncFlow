import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import api from '../services/api';
import '../styles/Dashboard.css';

export default function PredictiveModel() {
    const { organizationId } = useParams();

    const [trainingFile, setTrainingFile] = useState(null);
    const [predictionFile, setPredictionFile] = useState(null);
    const [trainingResult, setTrainingResult] = useState(null);
    const [predictionResult, setPredictionResult] = useState(null);
    const [loadingAction, setLoadingAction] = useState('');
    const [error, setError] = useState('');

    const upload = async (mode, file) => {
        if (!file) {
            setError('Selecciona un archivo CSV.');
            return;
        }

        setError('');
        setLoadingAction(mode);

        const formData = new FormData();
        formData.append('file', file);

        try {
            const response = await api.post(
                `/organizations/${organizationId}/predictive-model/${mode}`,
                formData
            );

            if (mode === 'train') {
                setTrainingResult(response.data.data);
            } else {
                setPredictionResult(response.data.data);
            }
        } catch (requestError) {
            setError(
                requestError.response?.data?.message ||
                requestError.response?.data?.detail ||
                'No se pudo procesar el CSV.'
            );
        } finally {
            setLoadingAction('');
        }
    };

    const formatMetrics = (metrics) => {
        if (!metrics) return null;

        return (
            <div className="prediction-metrics-grid">
                {[
                    ['Precisión', metrics.precision],
                    ['Recall', metrics.recall],
                    ['F1', metrics.f1],
                    ['ROC-AUC', metrics.rocAuc]
                ].map(([label, value]) => (
                    <div className="prediction-metric" key={label}>
                        <span>{label}</span>
                        <strong>
                            {value == null
                                ? '—'
                                : `${(Number(value) * 100).toFixed(1)}%`}
                        </strong>
                    </div>
                ))}
            </div>
        );
    };

    const riskLabel = {
        LOW: 'Bajo',
        MEDIUM: 'Medio',
        HIGH: 'Alto'
    };

    return (
        <main className="prediction-page">
            <header className="prediction-page-header">
                <div>
                    <span className="eyebrow">
                        Herramientas del propietario
                    </span>
                    <h1>Predicción de conflictos</h1>
                    <p>
                        Entrena el modelo con asignaciones históricas
                        y analiza nuevas asignaciones.
                    </p>
                </div>

                <Link to="/dashboard" className="secondary-btn">
                    Volver al calendario
                </Link>
            </header>

            {error && (
                <div className="prediction-error" role="alert">
                    {error}
                </div>
            )}

            <section className="prediction-card">
                <span className="eyebrow">Paso 1</span>
                <h2>Entrenar y evaluar el modelo</h2>
                <p>
                    Sube un CSV histórico que incluya la columna
                    <code> has_conflict</code>. El 25 % se reserva
                    para evaluación y se muestran métricas de ambos
                    modelos.
                </p>

                <label className="prediction-file-input">
                    <span>CSV etiquetado</span>
                    <input
                        type="file"
                        accept=".csv,text/csv"
                        onChange={(event) =>
                            setTrainingFile(event.target.files?.[0] || null)
                        }
                    />
                </label>

                {trainingFile && (
                    <p className="prediction-file-name">
                        {trainingFile.name}
                    </p>
                )}

                <button
                    type="button"
                    className="primary-btn"
                    disabled={Boolean(loadingAction)}
                    onClick={() => upload('train', trainingFile)}
                >
                    {loadingAction === 'train'
                        ? 'Entrenando...'
                        : 'Entrenar y evaluar'}
                </button>
            </section>

            {trainingResult && (
                <section className="prediction-card">
                    <span className="eyebrow">Evaluación</span>
                    <h2>
                        Modelo seleccionado:{' '}
                        {trainingResult.selectedModel}
                    </h2>
                    <p>
                        Entrenamiento con {trainingResult.trainingRows}
                        {' '}filas y prueba con {trainingResult.testRows}
                        {' '}filas.
                    </p>

                    <h3>Random Forest</h3>
                    {formatMetrics(
                        trainingResult.metrics?.randomForest
                    )}

                    <h3>Regresión logística</h3>
                    {formatMetrics(
                        trainingResult.metrics?.logisticRegression
                    )}

                    {trainingResult.aiAnalysis && (
                        <div className="prediction-ai-analysis">
                            <h3>Análisis de IA</h3>
                            <p>{trainingResult.aiAnalysis}</p>
                        </div>
                    )}

                    <h3>Riesgo en el conjunto de prueba</h3>
                    <PredictionCounts
                        counts={trainingResult.riskCounts}
                    />

                    <PredictionTable
                        rows={trainingResult.testPredictions}
                        riskLabel={riskLabel}
                        showActual
                    />
                </section>
            )}

            <section className="prediction-card">
                <span className="eyebrow">Paso 2</span>
                <h2>Predecir nuevas asignaciones</h2>
                <p>
                    Sube un CSV con las mismas variables de entrada,
                    pero sin <code>has_conflict</code>. Primero debes
                    entrenar el modelo para esta organización.
                </p>

                <label className="prediction-file-input">
                    <span>CSV sin etiqueta</span>
                    <input
                        type="file"
                        accept=".csv,text/csv"
                        onChange={(event) =>
                            setPredictionFile(
                                event.target.files?.[0] || null
                            )
                        }
                    />
                </label>

                {predictionFile && (
                    <p className="prediction-file-name">
                        {predictionFile.name}
                    </p>
                )}

                <button
                    type="button"
                    className="primary-btn"
                    disabled={Boolean(loadingAction)}
                    onClick={() =>
                        upload('predict', predictionFile)
                    }
                >
                    {loadingAction === 'predict'
                        ? 'Calculando...'
                        : 'Calcular riesgos'}
                </button>
            </section>

            {predictionResult && (
                <section className="prediction-card">
                    <span className="eyebrow">Resultado</span>
                    <h2>
                        {predictionResult.totalRows} asignaciones
                        analizadas
                    </h2>

                    <PredictionCounts
                        counts={predictionResult.riskCounts}
                    />

                    {predictionResult.aiAnalysis && (
                        <div className="prediction-ai-analysis">
                            <h3>Análisis de IA</h3>
                            <p>{predictionResult.aiAnalysis}</p>
                        </div>
                    )}

                    <PredictionTable
                        rows={predictionResult.predictions}
                        riskLabel={riskLabel}
                        showActual={false}
                    />
                </section>
            )}
        </main>
    );
}

function PredictionCounts({ counts = {} }) {
    return (
        <div className="prediction-risk-grid">
            {[
                ['Bajo', counts.LOW || 0, 'low'],
                ['Medio', counts.MEDIUM || 0, 'medium'],
                ['Alto', counts.HIGH || 0, 'high']
            ].map(([label, count, level]) => (
                <div
                    className={`prediction-risk-card ${level}`}
                    key={level}
                >
                    <span>Riesgo {label}</span>
                    <strong>{count}</strong>
                </div>
            ))}
        </div>
    );
}

function PredictionTable({
                             rows = [],
                             riskLabel,
                             showActual
                         }) {
    if (!rows.length) {
        return <p>No hay filas para mostrar.</p>;
    }

    return (
        <div className="prediction-table-wrapper">
            <table className="prediction-table">
                <thead>
                <tr>
                    <th>Fila CSV</th>
                    <th>Probabilidad</th>
                    <th>Riesgo</th>
                    {showActual && <th>Conflicto real</th>}
                </tr>
                </thead>
                <tbody>
                {rows.slice(0, 100).map((row) => (
                    <tr key={row.rowNumber}>
                        <td>{row.rowNumber}</td>
                        <td>
                            {row.riskProbabilityPct.toFixed(1)}%
                        </td>
                        <td>
                            {riskLabel[row.riskLevel] ||
                                row.riskLevel}
                        </td>
                        {showActual && (
                            <td>
                                {row.actualConflict
                                    ? 'Sí'
                                    : 'No'}
                            </td>
                        )}
                    </tr>
                ))}
                </tbody>
            </table>

            {rows.length > 100 && (
                <p>
                    Se muestran las primeras 100 filas; el análisis
                    incluye {rows.length}.
                </p>
            )}
        </div>
    );
}