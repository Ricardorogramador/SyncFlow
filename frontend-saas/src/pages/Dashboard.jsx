import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, dateFnsLocalizer } from 'react-big-calendar';
import format from 'date-fns/format';
import parse from 'date-fns/parse';
import startOfWeek from 'date-fns/startOfWeek';
import getDay from 'date-fns/getDay';
import es from 'date-fns/locale/es';
import api from '../services/api';

// IMPORTACIÓN DEL LOGO (Asegúrate de que esté en la carpeta src/assets/)
import logo from '../assets/SyncFlow.png';

import 'react-big-calendar/lib/css/react-big-calendar.css';
import '../styles/Dashboard.css';

const locales = { 'es': es };
const localizer = dateFnsLocalizer({
    format, parse, startOfWeek, getDay, locales
});

export default function Dashboard() {
    const navigate = useNavigate();

    // 1. Sidebar ahora inicia cerrado por defecto (false)
    const [isSidebarOpen, setIsSidebarOpen] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(false);

    const [toast, setToast] = useState({ show: false, message: '', type: 'success' });

    const [currentDate, setCurrentDate] = useState(new Date());
    const [currentView, setCurrentView] = useState('month');
    const [events, setEvents] = useState([]);
    const [promptText, setPromptText] = useState('');
    const [isProcessingPrompt, setIsProcessingPrompt] = useState(false);

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isOrgModalOpen, setIsOrgModalOpen] = useState(false);
    const [isViewModalOpen, setIsViewModalOpen] = useState(false);

    // Estados para el modal de Contexto
    const [isTimelineModalOpen, setIsTimelineModalOpen] = useState(false);
    const [timelineContent, setTimelineContent] = useState('');
    const [timelineEntries, setTimelineEntries] = useState([]); // Array para mostrar el historial

    const [selectedEventInfo, setSelectedEventInfo] = useState(null);
    const [selectedEventId, setSelectedEventId] = useState(null);
    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [startTime, setStartTime] = useState('');
    const [endTime, setEndTime] = useState('');
    const [assignee, setAssignee] = useState('');
    const [recurrence, setRecurrence] = useState('Ninguna');
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [orgName, setOrgName] = useState('');
    const [orgCode, setOrgCode] = useState('');

    const showToast = (message, type = 'success') => {
        setToast({ show: true, message, type });
        setTimeout(() => setToast({ show: false, message: '', type: 'success' }), 3000);
    };

    const fetchEvents = async () => {
        try {
            const response = await api.get('/events');
            const baseEvents = (response.data.data || []).map(ev => ({
                id: ev.id,
                baseId: ev.id,
                title: ev.title,
                description: ev.description,
                recurrenceFrequency: ev.recurrenceFrequency,
                start: new Date(ev.startTime),
                end: new Date(ev.endTime),
                hasConflict: ev.hasConflict
            }));

            const expandRecurringEvents = (eventsToExpand) => {
                const expanded = [];
                const limitDate = new Date();
                limitDate.setMonth(limitDate.getMonth() + 6);

                eventsToExpand.forEach(ev => {
                    expanded.push(ev);
                    if (ev.recurrenceFrequency === 'DAILY' || ev.recurrenceFrequency === 'WEEKLY') {
                        let nextStart = new Date(ev.start);
                        let nextEnd = new Date(ev.end);
                        const daysToAdd = ev.recurrenceFrequency === 'DAILY' ? 1 : 7;

                        while (true) {
                            nextStart.setDate(nextStart.getDate() + daysToAdd);
                            nextEnd.setDate(nextEnd.getDate() + daysToAdd);
                            if (nextStart > limitDate) break;

                            expanded.push({
                                ...ev,
                                id: `${ev.id}-rep-${nextStart.getTime()}`,
                                start: new Date(nextStart),
                                end: new Date(nextEnd)
                            });
                        }
                    }
                });
                return expanded;
            };

            setEvents(expandRecurringEvents(baseEvents));
        } catch (err) {
            console.error(err);
            showToast('Error al cargar los eventos', 'error');
        }
    };

    useEffect(() => {
        const token = localStorage.getItem('jwt_token');
        if (!token) return navigate('/login');
        fetchEvents();
        const savedTheme = localStorage.getItem('theme');
        if (savedTheme === 'dark') setIsDarkMode(true);
    }, [navigate]);

    const toggleTheme = () => {
        setIsDarkMode(!isDarkMode);
        localStorage.setItem('theme', !isDarkMode ? 'dark' : 'light');
    };

    const handleLogout = () => {
        localStorage.removeItem('jwt_token');
        navigate('/login');
    };

    const formatDateForInput = (date) => {
        const pad = (n) => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    };

    const getMinDateTime = () => formatDateForInput(new Date());

    const formatDisplayDate = (start, end) => {
        if (!start || !end) return '';
        const optionsDate = { weekday: 'long', day: 'numeric', month: 'long' };
        const optionsTime = { hour: '2-digit', minute: '2-digit' };
        const dateStr = start.toLocaleDateString('es-ES', optionsDate);
        const startStr = start.toLocaleTimeString('es-ES', optionsTime);
        const endStr = end.toLocaleTimeString('es-ES', optionsTime);
        return `${dateStr.charAt(0).toUpperCase() + dateStr.slice(1)} ⋅ ${startStr} - ${endStr}`;
    };

    const handleSelectSlot = ({ start }) => {
        const now = new Date();
        if (start < new Date(now.setHours(0, 0, 0, 0))) {
            showToast('No puedes agendar eventos en fechas pasadas', 'warning');
            return;
        }

        let defaultStart = start;
        if (start < new Date()) defaultStart = new Date();

        setStartTime(formatDateForInput(defaultStart));
        const end = new Date(defaultStart);
        end.setHours(end.getHours() + 1);
        setEndTime(formatDateForInput(end));

        setTitle('');
        setDescription('');
        setAssignee('');
        setRecurrence('Ninguna');
        setSelectedEventId(null);
        setIsModalOpen(true);
    };

    const handleSelectEvent = (event) => {
        setSelectedEventInfo(event);
        setIsViewModalOpen(true);
    };

    const handleEditClick = () => {
        setIsViewModalOpen(false);
        setSelectedEventId(selectedEventInfo.baseId);
        setTitle(selectedEventInfo.title);
        setDescription(selectedEventInfo.description || '');
        setStartTime(formatDateForInput(selectedEventInfo.start));
        setEndTime(formatDateForInput(selectedEventInfo.end));
        setAssignee('');

        let rec = 'Ninguna';
        if (selectedEventInfo.recurrenceFrequency === 'DAILY') rec = 'Diaria';
        if (selectedEventInfo.recurrenceFrequency === 'WEEKLY') rec = 'Semanal';
        setRecurrence(rec);
        setIsModalOpen(true);
    };

    const handlePromptSubmit = async () => {
        if (!promptText.trim()) {
            showToast('Escribe los detalles de la reunión primero', 'warning');
            return;
        }

        setIsProcessingPrompt(true);
        try {
            const response = await api.post('/events/preview', { prompt: promptText });
            const draft = response.data.data;

            setTitle(draft.title || '');
            setDescription(draft.description || '');
            setStartTime(draft.startTime || '');
            setEndTime(draft.endTime || '');
            setAssignee('');
            setRecurrence('Ninguna');
            setSelectedEventId(null);

            setIsModalOpen(true);
            setPromptText('');
            showToast('Borrador generado por IA', 'success');
        } catch (error) {
            console.error(error);
            showToast('No se pudo procesar el texto', 'error');
        } finally {
            setIsProcessingPrompt(false);
        }
    };

    const handleSaveEvent = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        const eventStartDate = new Date(startTime);

        if (!selectedEventId && eventStartDate < new Date()) {
            showToast('La hora de inicio ya ha pasado', 'error');
            setIsSubmitting(false);
            return;
        }

        let recurrencePayload = null;
        if (recurrence === 'Diaria') recurrencePayload = { frequency: 'DAILY', intervalCount: 1 };
        if (recurrence === 'Semanal') recurrencePayload = { frequency: 'WEEKLY', intervalCount: 1 };

        const payload = { title, description, startTime, endTime, recurrence: recurrencePayload };

        try {
            if (selectedEventId) {
                await api.put(`/events/${selectedEventId}`, payload);
                showToast('Evento actualizado correctamente');
            } else {
                const response = await api.post('/events', payload);
                if (response.data.data?.hasConflict) {
                    showToast('Evento agendado, pero existe un solapamiento', 'warning');
                } else {
                    showToast('Evento agendado con éxito');
                }
            }
            setIsModalOpen(false);
            fetchEvents();
        } catch (err) {
            showToast('Hubo un error al guardar el evento', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleDeleteEvent = async () => {
        if (!window.confirm('¿Estás seguro de que deseas eliminar este evento?')) return;
        try {
            const idToDelete = selectedEventInfo ? selectedEventInfo.baseId : selectedEventId;
            await api.delete(`/events/${idToDelete}`);
            showToast('Evento eliminado', 'success');
            setIsViewModalOpen(false);
            setIsModalOpen(false);
            fetchEvents();
        } catch (err) {
            showToast('No se pudo eliminar el evento', 'error');
        }
    };

    // Función para Cargar el Timeline visualmente
    const handleOpenTimeline = async () => {
        setIsViewModalOpen(false);
        setIsTimelineModalOpen(true);
        setTimelineEntries([]);

        try {
            const response = await api.get(`/events/${selectedEventInfo.baseId}/timeline`);
            setTimelineEntries(response.data.data || []);
        } catch (error) {
            console.error("Error al cargar el contexto", error);
            // Si el backend aún no tiene esta ruta, mostramos array vacío silenciosamente
        }
    };

    const handleAddTimelineEntry = async () => {
        if (!timelineContent.trim()) return;
        setIsSubmitting(true);

        try {
            await api.post(`/events/${selectedEventInfo.baseId}/timeline`, {
                content: timelineContent,
                entryType: 'COMMENT'
            });
            showToast('Evidencia agregada al contexto del evento', 'success');
            setTimelineContent('');
            handleOpenTimeline(); // Recargar la lista después de guardar
        } catch (error) {
            console.error('Error al guardar timeline:', error);
            showToast('No se pudo guardar la evidencia', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    const eventPropGetter = (event) => {
        return { className: event.hasConflict ? 'calendar-event-conflict' : 'calendar-event-normal' };
    };

    return (
        <div className={`layout ${isDarkMode ? 'dark-mode' : ''}`}>
            <div className={`toast-container ${toast.show ? 'show' : ''} toast-${toast.type}`}>
                {toast.message}
            </div>

            {/* BARRA LATERAL (SIDEBAR) ACTUALIZADA */}
            <aside className={`sidebar ${isSidebarOpen ? 'open' : 'closed'}`}>
                <div className="sidebar-header" style={{ padding: '20px 15px', display: 'flex', alignItems: 'center', gap: '12px' }}>
                    {/* Implementación de tu logo importado */}
                    <img src={logo} alt="SyncFlow Logo" style={{ width: '35px', height: '35px', borderRadius: '8px', objectFit: 'cover' }} />
                    {isSidebarOpen && <h2 className="brand-name" style={{ margin: 0, fontSize: '1.2rem' }}>SyncFlow Teams</h2>}
                </div>

                <nav className="sidebar-nav">
                    <button onClick={() => setIsOrgModalOpen(true)} className="nav-item active">
                        <span className="icon">🏢</span> {isSidebarOpen && 'Organización'}
                    </button>

                    {/* Opciones de usuario movidas aquí */}
                    <div style={{ margin: '15px 0', borderBottom: '1px solid #334155' }}></div>
                    <button className="nav-item">
                        <span className="icon">⚙️</span> {isSidebarOpen && 'Configuración'}
                    </button>
                    <button className="nav-item">
                        <span className="icon">💳</span> {isSidebarOpen && 'Facturación'}
                    </button>
                </nav>

                <div className="sidebar-footer">
                    <button onClick={handleLogout} className="nav-item text-danger" style={{ width: '100%', textAlign: 'left', background: 'transparent', border: 'none', cursor: 'pointer', color: '#ef4444' }}>
                        <span className="icon">🚪</span> {isSidebarOpen && 'Cerrar Sesión'}
                    </button>
                    {isSidebarOpen && (
                        <div className="sidebar-plan" style={{ marginTop: '15px' }}>
                            <span className="plan-badge">Plan Pro</span>
                            <button className="upgrade-btn">Administrar Plan</button>
                        </div>
                    )}
                </div>
            </aside>

            <main className="main-area">
                {/* HEADER SUPERIOR LIMPIO Y MINIMALISTA */}
                <header className="top-header">
                    <button onClick={() => setIsSidebarOpen(!isSidebarOpen)} className="toggle-btn">☰</button>
                    <div className="header-actions" style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
                        <span style={{ fontSize: '14px', color: isDarkMode ? '#94a3b8' : '#64748b', fontWeight: '500' }}>
                            admin@empresa.com
                        </span>
                        <button onClick={toggleTheme} className="theme-toggle">
                            {isDarkMode ? '☀️' : '🌙'}
                        </button>
                    </div>
                </header>

                <div className="content-container">
                    <section className="prompt-section">
                        <div className="section-header">
                            <h3 className="section-title">Asistente de Agendamiento IA</h3>
                        </div>
                        <div className="prompt-box">
                            <textarea
                                placeholder="Ej: Programa una revisión trimestral con el equipo el viernes a las 10:00 AM..."
                                value={promptText}
                                onChange={(e) => setPromptText(e.target.value)}
                                className="prompt-textarea"
                            />
                            <div className="prompt-actions">
                                <span>Procesamiento de Lenguaje Natural</span>
                                <button
                                    className="primary-btn"
                                    onClick={handlePromptSubmit}
                                    disabled={isProcessingPrompt}
                                >
                                    {isProcessingPrompt ? 'Procesando...' : 'Autocompletar Evento'}
                                </button>
                            </div>
                        </div>
                    </section>

                    <section className="calendar-section">
                        <div className="section-header">
                            <h3 className="section-title">Calendario Corporativo</h3>
                        </div>
                        <div className="calendar-wrapper">
                            <Calendar
                                localizer={localizer}
                                events={events}
                                startAccessor="start"
                                endAccessor="end"
                                culture="es"
                                selectable
                                onSelectSlot={handleSelectSlot}
                                onSelectEvent={handleSelectEvent}
                                eventPropGetter={eventPropGetter}
                                date={currentDate}
                                onNavigate={setCurrentDate}
                                view={currentView}
                                onView={setCurrentView}
                                style={{ height: 600 }}
                                messages={{
                                    next: "Sig", previous: "Ant", today: "Hoy", month: "Mes",
                                    week: "Semana", day: "Día", agenda: "Agenda",
                                    noEventsInRange: "No hay eventos en este rango."
                                }}
                            />
                        </div>
                    </section>
                </div>
            </main>

            {/* Modal para Visualizar Detalles del Evento */}
            {isViewModalOpen && selectedEventInfo && (
                <div className="modal-overlay">
                    <div className="modal-container" style={{ maxWidth: '480px' }}>
                        <div className="modal-header">
                            <h3 className="modal-title">Detalles del Evento</h3>
                            <button onClick={() => setIsViewModalOpen(false)} className="close-btn">✕</button>
                        </div>

                        <div className="view-event-body">
                            <div className="view-event-title-row">
                                <span className="color-indicator" style={{ backgroundColor: selectedEventInfo.hasConflict ? '#ef4444' : '#4f46e5' }}></span>
                                <h2>{selectedEventInfo.title}</h2>
                            </div>

                            <div className="view-event-detail">
                                <div className="view-event-text">
                                    <strong>Horario</strong>
                                    <span>{formatDisplayDate(selectedEventInfo.start, selectedEventInfo.end)}</span>
                                    {selectedEventInfo.recurrenceFrequency && (
                                        <span style={{ marginTop: '0.25rem' }}>Recurrencia: {selectedEventInfo.recurrenceFrequency === 'DAILY' ? 'Todos los días' : 'Cada semana'}</span>
                                    )}
                                </div>
                            </div>

                            {selectedEventInfo.description && (
                                <div className="view-event-detail">
                                    <div className="view-event-text">
                                        <strong>Descripción</strong>
                                        <span>{selectedEventInfo.description}</span>
                                    </div>
                                </div>
                            )}

                            {selectedEventInfo.hasConflict && (
                                <div className="conflict-alert" style={{ marginTop: '0.5rem' }}>
                                    ⚠️ Este evento presenta un conflicto de horario con otra reunión.
                                </div>
                            )}

                            {/* Botón de Contexto Actualizado */}
                            <button
                                type="button"
                                className="primary-btn"
                                style={{ width: '100%', marginTop: '1.5rem', backgroundColor: '#334155' }}
                                onClick={handleOpenTimeline}
                            >
                                📂 Centro de Contexto y Evidencias
                            </button>

                            <div className="modal-actions-spaced" style={{ marginTop: '1.5rem' }}>
                                <button type="button" onClick={handleDeleteEvent} className="delete-btn">
                                    Eliminar Evento
                                </button>
                                <button type="button" onClick={handleEditClick} className="confirm-btn">
                                    Editar Detalles
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Modal para el Timeline/Contexto */}
            {isTimelineModalOpen && selectedEventInfo && (
                <div className="modal-overlay">
                    <div className="modal-container" style={{ maxWidth: '550px' }}>
                        <div className="modal-header">
                            <h3 className="modal-title">Espacio de Trabajo: {selectedEventInfo.title}</h3>
                            <button onClick={() => setIsTimelineModalOpen(false)} className="close-btn">✕</button>
                        </div>
                        <div className="modal-form">

                            {/* ÁREA DE VISUALIZACIÓN DEL CONTEXTO (Historial) */}
                            <div style={{ maxHeight: '250px', overflowY: 'auto', marginBottom: '1.5rem', padding: '15px', backgroundColor: isDarkMode ? '#1e293b' : '#f8fafc', borderRadius: '8px', border: '1px solid', borderColor: isDarkMode ? '#334155' : '#e2e8f0' }}>
                                {timelineEntries.length === 0 ? (
                                    <p style={{ color: '#64748b', fontSize: '14px', textAlign: 'center', margin: 0 }}>Aún no hay minutas ni evidencias registradas.</p>
                                ) : (
                                    timelineEntries.map(entry => (
                                        <div key={entry.id} style={{ backgroundColor: isDarkMode ? '#0f172a' : 'white', padding: '12px', borderRadius: '6px', marginBottom: '10px', border: '1px solid', borderColor: isDarkMode ? '#334155' : '#cbd5e1' }}>
                                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: '#64748b', marginBottom: '8px' }}>
                                                <strong style={{ color: isDarkMode ? '#cbd5e1' : '#334155' }}>{entry.authorName || 'Usuario'}</strong>
                                                <span>{new Date(entry.createdAt).toLocaleString()}</span>
                                            </div>
                                            <p style={{ fontSize: '14px', margin: 0, color: isDarkMode ? '#f1f5f9' : '#334155', whiteSpace: 'pre-wrap' }}>{entry.content}</p>
                                        </div>
                                    ))
                                )}
                            </div>

                            <div className="form-group">
                                <label className="modal-label">Añadir acta, acuerdo o link asíncrono</label>
                                <textarea
                                    className="modal-input"
                                    style={{ minHeight: '100px', resize: 'vertical' }}
                                    placeholder="Escribe notas de la reunión o adjunta links de Drive..."
                                    value={timelineContent}
                                    onChange={(e) => setTimelineContent(e.target.value)}
                                />
                            </div>

                            <div className="modal-actions-spaced" style={{ justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                                <div className="action-buttons-group">
                                    <button type="button" onClick={() => setIsTimelineModalOpen(false)} className="cancel-btn">
                                        Volver
                                    </button>
                                    <button
                                        type="button"
                                        onClick={handleAddTimelineEntry}
                                        className="confirm-btn"
                                        disabled={isSubmitting}
                                    >
                                        {isSubmitting ? 'Guardando...' : 'Publicar en el Contexto'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Modal de Crear/Editar Evento */}
            {isModalOpen && (
                <div className="modal-overlay">
                    <div className="modal-container">
                        <div className="modal-header">
                            <h3 className="modal-title">{selectedEventId ? 'Modificar Evento' : 'Agendar Nueva Reunión'}</h3>
                            <button onClick={() => setIsModalOpen(false)} className="close-btn">✕</button>
                        </div>
                        <form onSubmit={handleSaveEvent} className="modal-form">
                            <div className="form-group">
                                <label className="modal-label">Asunto / Título</label>
                                <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} className="modal-input" required />
                            </div>
                            <div className="form-group">
                                <label className="modal-label">Orden del Día (Opcional)</label>
                                <textarea value={description} onChange={(e) => setDescription(e.target.value)} className="modal-input" style={{ minHeight: '60px', resize: 'vertical' }} placeholder="Temas a tratar..." />
                            </div>
                            <div className="form-row">
                                <div className="form-group">
                                    <label className="modal-label">Inicio</label>
                                    <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} className="modal-input" min={!selectedEventId ? getMinDateTime() : undefined} required />
                                </div>
                                <div className="form-group">
                                    <label className="modal-label">Fin</label>
                                    <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} className="modal-input" min={startTime || getMinDateTime()} required />
                                </div>
                            </div>
                            <div className="form-row">
                                <div className="form-group">
                                    <label className="modal-label">Repetir</label>
                                    <select value={recurrence} onChange={(e) => setRecurrence(e.target.value)} className="modal-input">
                                        <option>Ninguna</option>
                                        <option>Diaria</option>
                                        <option>Semanal</option>
                                    </select>
                                </div>
                                <div className="form-group">
                                    <label className="modal-label">Asignar a Miembro</label>
                                    <input type="text" placeholder="Ej: dev@empresa.com" value={assignee} onChange={(e) => setAssignee(e.target.value)} className="modal-input" />
                                </div>
                            </div>

                            <div className="modal-actions-spaced" style={{ justifyContent: 'flex-end' }}>
                                <div className="action-buttons-group">
                                    <button type="button" onClick={() => setIsModalOpen(false)} className="cancel-btn">Cancelar</button>
                                    <button type="submit" className="confirm-btn" disabled={isSubmitting}>
                                        {isSubmitting ? 'Guardando...' : (selectedEventId ? 'Actualizar Reunión' : 'Programar Reunión')}
                                    </button>
                                </div>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modal de Organización (Se mantiene igual) */}
            {isOrgModalOpen && (
                <div className="modal-overlay">
                    <div className="modal-container org-modal">
                        <div className="modal-header">
                            <h3 className="modal-title">Gestión del Workspace</h3>
                            <button onClick={() => setIsOrgModalOpen(false)} className="close-btn">✕</button>
                        </div>

                        <div className="org-split-layout">
                            <div className="org-half">
                                <div className="org-half-header">
                                    <h4>Crear Nueva Organización</h4>
                                    <p>Inicia un espacio de trabajo para tu equipo desde cero.</p>
                                </div>
                                <div className="org-form-group">
                                    <label className="modal-label">Nombre de la empresa</label>
                                    <input
                                        type="text"
                                        placeholder="Ej: Acme Corp"
                                        value={orgName}
                                        onChange={(e) => setOrgName(e.target.value)}
                                        className="modal-input"
                                    />
                                </div>
                                <button className="primary-btn org-action-btn">Crear y Continuar</button>
                            </div>

                            <div className="org-divider"></div>

                            <div className="org-half">
                                <div className="org-half-header">
                                    <h4>Unirse a un Equipo</h4>
                                    <p>Ingresa el código que te proporcionó el administrador.</p>
                                </div>
                                <div className="org-form-group">
                                    <label className="modal-label">Código de invitación</label>
                                    <input
                                        type="text"
                                        placeholder="Ej: X9A-2B4"
                                        value={orgCode}
                                        onChange={(e) => setOrgCode(e.target.value)}
                                        className="modal-input"
                                    />
                                </div>
                                <button className="secondary-btn org-action-btn">Unirme Ahora</button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}