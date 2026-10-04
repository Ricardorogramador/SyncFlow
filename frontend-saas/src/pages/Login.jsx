import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../services/api.js';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();
        setError('');
        setIsLoading(true);

        try {
            const response = await api.post('/auth/login', { email, password });
            const token = response.data.data.token;
            localStorage.setItem('jwt_token', token);
            navigate('/dashboard');
        } catch (err) {
            if (err.response && err.response.data) {
                setError(err.response.data.message || 'Credenciales inválidas');
            } else {
                setError('Error de conexión con el servidor');
            }
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div style={styles.page}>
            <div style={styles.card}>
                <div style={styles.header}>
                    <div style={styles.logoMark}></div>
                    <h1 style={styles.brand}>SyncFlow</h1>
                    <p style={styles.subtitle}>Bienvenido de nuevo a tu espacio de trabajo</p>
                </div>

                {error && <div style={styles.errorAlert}>{error}</div>}

                <form onSubmit={handleLogin} style={styles.form}>
                    <div style={styles.inputGroup}>
                        <label style={styles.label}>Correo electrónico</label>
                        <input
                            type="email"
                            placeholder="tu@empresa.com"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            required
                            style={styles.input}
                        />
                    </div>
                    <div style={styles.inputGroup}>
                        <label style={styles.label}>Contraseña</label>
                        <input
                            type="password"
                            placeholder="••••••••"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                            style={styles.input}
                        />
                    </div>
                    <button type="submit" style={styles.primaryButton} disabled={isLoading}>
                        {isLoading ? 'Autenticando...' : 'Iniciar Sesión'}
                    </button>
                </form>
                <div style={styles.footer}>
                    ¿Aún no tienes cuenta? <Link to="/register" style={styles.link}>Crear una cuenta</Link>
                </div>
            </div>
        </div>
    );
}

const styles = {
    page: { display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', backgroundColor: '#f8fafc', fontFamily: 'Inter, system-ui, sans-serif', color: '#0f172a' },
    card: { backgroundColor: '#ffffff', padding: '3rem 2.5rem', borderRadius: '16px', boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.01)', width: '100%', maxWidth: '420px', border: '1px solid #e2e8f0' },
    header: { textAlign: 'center', marginBottom: '2rem' },
    logoMark: { width: '40px', height: '40px', backgroundColor: '#4f46e5', borderRadius: '10px', margin: '0 auto 1rem', backgroundImage: 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)' },
    brand: { fontSize: '1.75rem', fontWeight: '800', letterSpacing: '-0.025em', margin: '0 0 0.5rem 0', color: '#0f172a' },
    subtitle: { fontSize: '0.95rem', color: '#64748b', margin: 0 },
    form: { display: 'flex', flexDirection: 'column', gap: '1.25rem' },
    inputGroup: { display: 'flex', flexDirection: 'column', gap: '0.35rem' },
    label: { fontSize: '0.875rem', fontWeight: '600', color: '#334155' },
    input: { padding: '0.75rem 1rem', borderRadius: '8px', border: '1px solid #cbd5e1', fontSize: '1rem', outline: 'none', transition: 'border-color 0.2s', backgroundColor: '#f8fafc' },
    primaryButton: { padding: '0.875rem', borderRadius: '8px', border: 'none', backgroundColor: '#4f46e5', color: '#ffffff', fontSize: '1rem', fontWeight: '600', cursor: 'pointer', transition: 'background-color 0.2s', boxShadow: '0 4px 6px -1px rgba(79, 70, 229, 0.2)' },
    errorAlert: { backgroundColor: '#fef2f2', borderLeft: '4px solid #ef4444', color: '#991b1b', padding: '1rem', borderRadius: '6px', marginBottom: '1.5rem', fontSize: '0.875rem', fontWeight: '500' },
    footer: { marginTop: '2rem', textAlign: 'center', fontSize: '0.9rem', color: '#64748b' },
    link: { color: '#4f46e5', textDecoration: 'none', fontWeight: '600' }
};