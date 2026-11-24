import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:8080/api',
});

api.interceptors.request.use((config) => {
    const url = config.url || '';

    const isAuthenticationRequest =
        url.startsWith('/auth/login') ||
        url.startsWith('/auth/register');

    if (!isAuthenticationRequest) {
        const token = localStorage.getItem('jwt_token');

        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
    }

    return config;
});

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response?.status === 401) {
            localStorage.removeItem('jwt_token');
        }

        return Promise.reject(error);
    }
);

export default api;