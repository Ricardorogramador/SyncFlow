import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, dateFnsLocalizer } from 'react-big-calendar';
import format from 'date-fns/format';
import parse from 'date-fns/parse';
import startOfWeek from 'date-fns/startOfWeek';
import getDay from 'date-fns/getDay';
import es from 'date-fns/locale/es';
import api from '../services/api';
import logo from '../assets/SyncFlow.png';

import 'react-big-calendar/lib/css/react-big-calendar.css';
import '../styles/Dashboard.css';

const locales = { es };
const localizer = dateFnsLocalizer({
    format,
    parse,
    startOfWeek,
    getDay,
    locales
});

export default function Dashboard() {
    const navigate = useNavigate();

    const [isSidebarOpen, setIsSidebarOpen] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [toast, setToast] = useState({
        show: false,
        message: '',
        type: 'success'
    });

    const [currentDate, setCurrentDate] = useState(new Date());
    const [currentView, setCurrentView] = useState('month');
    const [events, setEvents] = useState([]);

    const [promptText, setPromptText] = useState('');
    const [isProcessingPrompt, setIsProcessingPrompt] = useState(false);

    const [isEventModalOpen, setIsEventModalOpen] = useState(false);
    const [isEventDetailsOpen, setIsEventDetailsOpen] = useState(false);
    const [isOrganizationModalOpen, setIsOrganizationModalOpen] =
        useState(false);
    const [isSettingsModalOpen, setIsSettingsModalOpen] =
        useState(false);
    const [isTimelineModalOpen, setIsTimelineModalOpen] =
        useState(false);

    const [selectedEvent, setSelectedEvent] = useState(null);
    const [selectedEventId, setSelectedEventId] = useState(null);

    const [title, setTitle] = useState('');
    const [description, setDescription] = useState('');
    const [startTime, setStartTime] = useState('');
    const [endTime, setEndTime] = useState('');
    const [recurrence, setRecurrence] = useState('Ninguna');
    const [eventScope, setEventScope] = useState('PERSONAL');
    const [assignedUserIds, setAssignedUserIds] = useState([]);

    const [timelineContent, setTimelineContent] = useState('');
    const [timelineEntries, setTimelineEntries] = useState([]);

    const [orgName, setOrgName] = useState('');
    const [orgCode, setOrgCode] = useState('');
    const [organizationName, setOrganizationName] =
        useState('');
    const [inviteMaxUses, setInviteMaxUses] = useState(5);
    const [lastInviteCode, setLastInviteCode] = useState('');

    const [members, setMembers] = useState([]);

    const [activeOrganizationId, setActiveOrganizationId] =
        useState(
            localStorage.getItem(
                'active_organization_id'
            ) || ''
        );

    const showToast = (
        message,
        type = 'success'
    ) => {
        setToast({
            show: true,
            message,
            type
        });

        setTimeout(() => {
            setToast({
                show: false,
                message: '',
                type: 'success'
            });
        }, 3000);
    };

    const formatDateForInput = (date) => {
        const pad = (value) =>
            String(value).padStart(2, '0');

        return (
            `${date.getFullYear()}-` +
            `${pad(date.getMonth() + 1)}-` +
            `${pad(date.getDate())}T` +
            `${pad(date.getHours())}:` +
            `${pad(date.getMinutes())}`
        );
    };

    const getMinDateTime = () =>
        formatDateForInput(new Date());

    const fetchEvents = async () => {
        try {
            const response = await api.get('/events');

            const baseEvents =
                (response.data.data || []).map((event) => ({
                    id: event.id,
                    baseId: event.id,
                    title: event.title,
                    description:
                        event.description || '',
                    recurrenceFrequency:
                    event.recurrenceFrequency,
                    scope:
                        event.scope || 'PERSONAL',
                    organizationId:
                    event.organizationId,
                    start:
                        new Date(event.startTime),
                    end:
                        new Date(event.endTime),
                    hasConflict:
                    event.hasConflict
                }));

            const expandedEvents = [];
            const limitDate = new Date();
            limitDate.setMonth(
                limitDate.getMonth() + 6
            );

            baseEvents.forEach((event) => {
                expandedEvents.push(event);

                if (
                    event.recurrenceFrequency !==
                    'DAILY'
                    &&
                    event.recurrenceFrequency !==
                    'WEEKLY'
                ) {
                    return;
                }

                const daysToAdd =
                    event.recurrenceFrequency ===
                    'DAILY'
                        ? 1
                        : 7;

                let nextStart =
                    new Date(event.start);
                let nextEnd =
                    new Date(event.end);

                while (true) {
                    nextStart.setDate(
                        nextStart.getDate() +
                        daysToAdd
                    );

                    nextEnd.setDate(
                        nextEnd.getDate() +
                        daysToAdd
                    );

                    if (
                        nextStart > limitDate
                    ) {
                        break;
                    }

                    expandedEvents.push({
                        ...event,
                        id:
                            `${event.id}-rep-` +
                            nextStart.getTime(),
                        start:
                            new Date(nextStart),
                        end:
                            new Date(nextEnd)
                    });
                }
            });

            setEvents(expandedEvents);
        } catch (error) {
            console.error(error);
            showToast(
                'Error al cargar los eventos',
                'error'
            );
        }
    };

    const fetchMembers = async () => {
        if (!activeOrganizationId) {
            setMembers([]);
            return;
        }

        try {
            const response =
                await api.get(
                    `/organizations/${activeOrganizationId}/members`
                );

            setMembers(
                response.data.data || []
            );
        } catch (error) {
            console.error(error);
        }
    };

    useEffect(() => {
        const token =
            localStorage.getItem('jwt_token');

        if (!token) {
            navigate('/login');
            return;
        }

        fetchEvents();
        fetchMembers();

        const savedTheme =
            localStorage.getItem('theme');

        setIsDarkMode(
            savedTheme === 'dark'
        );
    }, [navigate]);

    useEffect(() => {
        fetchMembers();
    }, [activeOrganizationId]);

    const toggleTheme = () => {
        const nextTheme =
            isDarkMode ? 'light' : 'dark';

        setIsDarkMode(
            nextTheme === 'dark'
        );

        localStorage.setItem(
            'theme',
            nextTheme
        );
    };

    const handleLogout = () => {
        localStorage.removeItem('jwt_token');
        localStorage.removeItem(
            'active_organization_id'
        );

        navigate('/login');
    };

    const formatDisplayDate = (
        start,
        end
    ) => {
        if (!start || !end) {
            return '';
        }

        const dateOptions = {
            weekday: 'long',
            day: 'numeric',
            month: 'long'
        };

        const timeOptions = {
            hour: '2-digit',
            minute: '2-digit'
        };

        const date =
            start.toLocaleDateString(
                'es-ES',
                dateOptions
            );

        const startTimeText =
            start.toLocaleTimeString(
                'es-ES',
                timeOptions
            );

        const endTimeText =
            end.toLocaleTimeString(
                'es-ES',
                timeOptions
            );

        return (
            `${date.charAt(0).toUpperCase()}` +
            `${date.slice(1)} · ` +
            `${startTimeText} - ${endTimeText}`
        );
    };

    const handleSelectSlot = ({ start }) => {
        const now = new Date();

        if (
            start <
            new Date(
                now.setHours(0, 0, 0, 0)
            )
        ) {
            showToast(
                'No puedes agendar eventos en fechas pasadas',
                'warning'
            );
            return;
        }

        let defaultStart = start;

        if (defaultStart < new Date()) {
            defaultStart = new Date();
        }

        setStartTime(
            formatDateForInput(defaultStart)
        );

        const defaultEnd =
            new Date(defaultStart);

        defaultEnd.setHours(
            defaultEnd.getHours() + 1
        );

        setEndTime(
            formatDateForInput(defaultEnd)
        );

        setTitle('');
        setDescription('');
        setRecurrence('Ninguna');
        setEventScope('PERSONAL');
        setAssignedUserIds([]);
        setSelectedEventId(null);
        setIsEventModalOpen(true);
    };

    const handleSelectEvent = (event) => {
        setSelectedEvent(event);
        setIsEventDetailsOpen(true);
    };

    const handleEditEvent = () => {
        if (!selectedEvent) {
            return;
        }

        setIsEventDetailsOpen(false);
        setSelectedEventId(
            selectedEvent.baseId
        );

        setTitle(selectedEvent.title);
        setDescription(
            selectedEvent.description || ''
        );

        setStartTime(
            formatDateForInput(
                selectedEvent.start
            )
        );

        setEndTime(
            formatDateForInput(
                selectedEvent.end
            )
        );

        setEventScope(
            selectedEvent.scope ||
            'PERSONAL'
        );

        let recurrenceValue =
            'Ninguna';

        if (
            selectedEvent.recurrenceFrequency ===
            'DAILY'
        ) {
            recurrenceValue = 'Diaria';
        }

        if (
            selectedEvent.recurrenceFrequency ===
            'WEEKLY'
        ) {
            recurrenceValue = 'Semanal';
        }

        setRecurrence(
            recurrenceValue
        );

        setAssignedUserIds([]);
        setIsEventModalOpen(true);
    };

    const handlePromptSubmit = async () => {
        if (!promptText.trim()) {
            showToast(
                'Escribe los detalles del evento',
                'warning'
            );
            return;
        }

        setIsProcessingPrompt(true);

        try {
            const response =
                await api.post(
                    '/events/preview',
                    {
                        prompt: promptText
                    }
                );

            const draft =
                response.data.data;

            setTitle(draft.title || '');
            setDescription(
                draft.description || ''
            );
            setStartTime(
                draft.startTime || ''
            );
            setEndTime(
                draft.endTime || ''
            );
            setRecurrence('Ninguna');
            setEventScope('PERSONAL');
            setAssignedUserIds([]);
            setSelectedEventId(null);

            setPromptText('');
            setIsEventModalOpen(true);

            showToast(
                'Borrador generado por IA',
                'success'
            );
        } catch (error) {
            console.error(error);
            showToast(
                'No se pudo procesar el texto',
                'error'
            );
        } finally {
            setIsProcessingPrompt(false);
        }
    };

    const handleSaveEvent = async (event) => {
        event.preventDefault();

        setIsSubmitting(true);

        if (
            !selectedEventId &&
            new Date(startTime) < new Date()
        ) {
            showToast(
                'La hora de inicio ya ha pasado',
                'error'
            );
            setIsSubmitting(false);
            return;
        }

        if (
            eventScope === 'CORPORATE'
            &&
            !activeOrganizationId
        ) {
            showToast(
                'Selecciona o crea una organización',
                'warning'
            );
            setIsSubmitting(false);
            return;
        }

        let recurrencePayload = null;

        if (recurrence === 'Diaria') {
            recurrencePayload = {
                frequency: 'DAILY',
                intervalCount: 1
            };
        }

        if (recurrence === 'Semanal') {
            recurrencePayload = {
                frequency: 'WEEKLY',
                intervalCount: 1
            };
        }

        const payload = {
            title,
            description,
            startTime,
            endTime,
            recurrence:
            recurrencePayload,
            scope:
            eventScope,
            organizationId:
                eventScope === 'CORPORATE'
                    ? activeOrganizationId
                    : null,
            assignedUserIds:
                eventScope === 'CORPORATE'
                    ? assignedUserIds
                    : []
        };

        try {
            if (selectedEventId) {
                await api.put(
                    `/events/${selectedEventId}`,
                    payload
                );

                showToast(
                    'Evento actualizado correctamente'
                );
            } else {
                const response =
                    await api.post(
                        '/events',
                        payload
                    );

                if (
                    response.data.data
                        ?.hasConflict
                ) {
                    showToast(
                        'Evento creado con conflicto de horario',
                        'warning'
                    );
                } else {
                    showToast(
                        'Evento creado correctamente'
                    );
                }
            }

            setIsEventModalOpen(false);
            await fetchEvents();
        } catch (error) {
            console.error(error);

            showToast(
                error.response?.data
                    ?.message ||
                'No se pudo guardar el evento',
                'error'
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleDeleteEvent = async () => {
        if (!selectedEvent) {
            return;
        }

        if (
            !window.confirm(
                '¿Deseas eliminar este evento?'
            )
        ) {
            return;
        }

        try {
            await api.delete(
                `/events/${selectedEvent.baseId}`
            );

            showToast(
                'Evento eliminado'
            );

            setIsEventDetailsOpen(false);
            setSelectedEvent(null);

            await fetchEvents();
        } catch (error) {
            console.error(error);

            showToast(
                error.response?.data
                    ?.message ||
                'No se pudo eliminar el evento',
                'error'
            );
        }
    };

    const handleOpenTimeline = async () => {
        if (!selectedEvent) {
            return;
        }

        setIsEventDetailsOpen(false);
        setIsTimelineModalOpen(true);
        setTimelineEntries([]);

        try {
            const response =
                await api.get(
                    `/events/${selectedEvent.baseId}/timeline`
                );

            setTimelineEntries(
                response.data.data || []
            );
        } catch (error) {
            console.error(error);
        }
    };

    const handleAddTimelineEntry = async () => {
        if (
            !timelineContent.trim()
            ||
            !selectedEvent
        ) {
            return;
        }

        setIsSubmitting(true);

        try {
            await api.post(
                `/events/${selectedEvent.baseId}/timeline`,
                {
                    content:
                    timelineContent,
                    entryType:
                        'COMMENT'
                }
            );

            showToast(
                'Evidencia agregada'
            );

            setTimelineContent('');
            await handleOpenTimeline();
        } catch (error) {
            console.error(error);

            showToast(
                'No se pudo agregar la evidencia',
                'error'
            );
        } finally {
            setIsSubmitting(false);
        }
    };

    const handleCreateOrganization =
        async () => {
            if (!orgName.trim()) {
                showToast(
                    'Escribe el nombre de la organización',
                    'warning'
                );
                return;
            }

            setIsSubmitting(true);

            try {
                const response =
                    await api.post(
                        '/organizations',
                        {
                            name:
                                orgName.trim()
                        }
                    );

                const organization =
                    response.data.data;

                const organizationId =
                    organization.id;

                localStorage.setItem(
                    'active_organization_id',
                    organizationId
                );

                setActiveOrganizationId(
                    organizationId
                );

                setOrganizationName(
                    organization.name
                );

                setOrgName('');
                setIsOrganizationModalOpen(
                    false
                );

                showToast(
                    'Organización creada correctamente'
                );
            } catch (error) {
                console.error(error);

                showToast(
                    error.response?.data
                        ?.message ||
                    'No se pudo crear la organización',
                    'error'
                );
            } finally {
                setIsSubmitting(false);
            }
        };

    const handleJoinOrganization =
        async () => {
            if (!orgCode.trim()) {
                showToast(
                    'Escribe el código de invitación',
                    'warning'
                );
                return;
            }

            setIsSubmitting(true);

            try {
                const response =
                    await api.post(
                        '/organizations/join',
                        {
                            code:
                                orgCode.trim()
                        }
                    );

                const membership =
                    response.data.data;

                localStorage.setItem(
                    'active_organization_id',
                    membership.organizationId
                );

                setActiveOrganizationId(
                    membership.organizationId
                );

                setOrganizationName(
                    membership.organizationName
                );

                setOrgCode('');
                setIsOrganizationModalOpen(
                    false
                );

                showToast(
                    'Te has unido al equipo'
                );
            } catch (error) {
                console.error(error);

                showToast(
                    error.response?.data
                        ?.message ||
                    'No fue posible unirse',
                    'error'
                );
            } finally {
                setIsSubmitting(false);
            }
        };

    const handleUpdateOrganization =
        async () => {
            if (
                !activeOrganizationId
            ) {
                showToast(
                    'No hay una organización activa',
                    'warning'
                );
                return;
            }

            if (
                !organizationName.trim()
            ) {
                showToast(
                    'Escribe el nombre de la organización',
                    'warning'
                );
                return;
            }

            setIsSubmitting(true);

            try {
                await api.patch(
                    `/organizations/${activeOrganizationId}`,
                    {
                        name:
                            organizationName.trim()
                    }
                );

                showToast(
                    'Organización actualizada'
                );
            } catch (error) {
                console.error(error);

                showToast(
                    error.response?.data
                        ?.message ||
                    'No se pudo actualizar',
                    'error'
                );
            } finally {
                setIsSubmitting(false);
            }
        };

    const handleGenerateInvite =
        async () => {
            if (
                !activeOrganizationId
            ) {
                showToast(
                    'No hay una organización activa',
                    'warning'
                );
                return;
            }

            setIsSubmitting(true);

            try {
                const response =
                    await api.post(
                        `/organizations/${activeOrganizationId}/invite-codes`,
                        {
                            maxUses:
                                Number(inviteMaxUses)
                        }
                    );

                setLastInviteCode(
                    response.data.data.code
                );

                showToast(
                    'Código de invitación generado'
                );
            } catch (error) {
                console.error(error);

                showToast(
                    error.response?.data
                        ?.message ||
                    'No se pudo generar el código',
                    'error'
                );
            } finally {
                setIsSubmitting(false);
            }
        };

    const handleUpdateMemberRole =
        async (
            membershipId,
            role
        ) => {
            try {
                await api.patch(
                    `/organizations/${activeOrganizationId}/members/${membershipId}`,
                    { role }
                );

                showToast(
                    'Rol actualizado'
                );

                await fetchMembers();
            } catch (error) {
                console.error(error);

                showToast(
                    error.response?.data
                        ?.message ||
                    'No se pudo actualizar el rol',
                    'error'
                );
            }
        };

    const handleRemoveMember =
        async (membershipId) => {
            if (
                !window.confirm(
                    '¿Deseas eliminar este miembro?'
                )
            ) {
                return;
            }

            try {
                await api.delete(
                    `/organizations/${activeOrganizationId}/members/${membershipId}`
                );

                showToast(
                    'Miembro eliminado'
                );

                await fetchMembers();
            } catch (error) {
                console.error(error);

                showToast(
                    error.response?.data
                        ?.message ||
                    'No se pudo eliminar',
                    'error'
                );
            }
        };

    const toggleAssignedMember =
        (member) => {
            const userId =
                member.user?.id ||
                member.userId;

            if (!userId) {
                return;
            }

            setAssignedUserIds(
                (currentIds) =>
                    currentIds.includes(
                        userId
                    )
                        ? currentIds.filter(
                            (id) =>
                                id !== userId
                        )
                        : [
                            ...currentIds,
                            userId
                        ]
            );
        };

    const todayEvents =
        useMemo(() => {
            const today =
                new Date();

            return events
                .filter((event) => {
                    const eventDate =
                        event.start;

                    return (
                        eventDate
                            .getFullYear() ===
                        today.getFullYear()
                        &&
                        eventDate
                            .getMonth() ===
                        today.getMonth()
                        &&
                        eventDate
                            .getDate() ===
                        today.getDate()
                    );
                })
                .sort(
                    (a, b) =>
                        a.start - b.start
                );
        }, [events]);

    const eventPropGetter =
        (event) => ({
            className:
                event.scope ===
                'CORPORATE'
                    ? event.hasConflict
                        ? 'calendar-event-corporate-conflict'
                        : 'calendar-event-corporate'
                    : event.hasConflict
                        ? 'calendar-event-personal-conflict'
                        : 'calendar-event-personal'
        });

    return (
        <div
            className={`layout ${
                isDarkMode
                    ? 'dark-mode'
                    : ''
            }`}
        >
            {toast.show && (
                <div
                    className={
                        `toast-container show ` +
                        `toast-${toast.type}`
                    }
                >
                    {toast.message}
                </div>
            )}

            <aside
                className={`sidebar ${
                    isSidebarOpen
                        ? 'open'
                        : 'closed'
                }`}
            >
                <div className="sidebar-header">
                    <img
                        src={logo}
                        alt="SyncFlow Logo"
                        className="sidebar-logo"
                    />

                    {isSidebarOpen && (
                        <h2 className="brand-name">
                            SyncFlow Teams
                        </h2>
                    )}
                </div>

                <nav className="sidebar-nav">
                    <button
                        type="button"
                        onClick={() =>
                            setIsOrganizationModalOpen(
                                true
                            )
                        }
                        className="nav-item active"
                    >
                        <span className="icon">
                            🏢
                        </span>

                        {isSidebarOpen &&
                            'Organización'}
                    </button>

                    <div className="sidebar-divider" />

                    <button
                        type="button"
                        onClick={() =>
                            setIsSettingsModalOpen(
                                true
                            )
                        }
                        className="nav-item"
                    >
                        <span className="icon">
                            ⚙️
                        </span>

                        {isSidebarOpen &&
                            'Configuración'}
                    </button>

                    <button
                        type="button"
                        className="nav-item"
                    >
                        <span className="icon">
                            💳
                        </span>

                        {isSidebarOpen &&
                            'Facturación'}
                    </button>
                </nav>

                <div className="sidebar-footer">
                    <button
                        type="button"
                        onClick={handleLogout}
                        className="nav-item text-danger"
                    >
                        <span className="icon">
                            🚪
                        </span>

                        {isSidebarOpen &&
                            'Cerrar Sesión'}
                    </button>
                </div>
            </aside>

            <main className="main-area">
                <header className="top-header">
                    <button
                        type="button"
                        onClick={() =>
                            setIsSidebarOpen(
                                !isSidebarOpen
                            )
                        }
                        className="toggle-btn"
                    >
                        ☰
                    </button>

                    <div className="header-actions">
                        <span className="header-email">
                            Cuenta activa
                        </span>

                        <button
                            type="button"
                            onClick={toggleTheme}
                            className="theme-toggle"
                        >
                            {isDarkMode
                                ? '☀️'
                                : '🌙'}
                        </button>
                    </div>
                </header>

                <div className="content-container">
                    <div className="dashboard-overview">
                        <section className="ai-assistant-card">
                            <div className="overview-card-header">
                                <div>
                                    <span className="eyebrow">
                                        Inteligencia artificial
                                    </span>

                                    <h3>
                                        Asistente de Agendamiento
                                    </h3>
                                </div>
                            </div>

                            <textarea
                                placeholder="Ej: Revisión trimestral con el equipo mañana a las 10:00"
                                value={promptText}
                                onChange={(event) =>
                                    setPromptText(
                                        event.target.value
                                    )
                                }
                                className="ai-prompt-textarea"
                            />

                            <button
                                type="button"
                                onClick={
                                    handlePromptSubmit
                                }
                                disabled={
                                    isProcessingPrompt
                                }
                                className="primary-btn ai-button"
                            >
                                {isProcessingPrompt
                                    ? 'Procesando...'
                                    : 'Completar evento'}
                            </button>
                        </section>

                        <section className="today-agenda-card">
                            <div className="overview-card-header">
                                <div>
                                    <span className="eyebrow">
                                        Tu resumen
                                    </span>

                                    <h3>
                                        Agenda de Hoy
                                    </h3>
                                </div>

                                <span className="agenda-count">
                                    {todayEvents.length}
                                </span>
                            </div>

                            {todayEvents.length ===
                            0 ? (
                                <div className="agenda-empty">
                                    <span>
                                        ✓
                                    </span>

                                    <p>
                                        No tienes eventos
                                        ni asignaciones
                                        para hoy.
                                    </p>
                                </div>
                            ) : (
                                <div className="agenda-list">
                                    {todayEvents.map(
                                        (event) => (
                                            <button
                                                type="button"
                                                key={
                                                    event.id
                                                }
                                                onClick={() =>
                                                    handleSelectEvent(
                                                        event
                                                    )
                                                }
                                                className="agenda-item"
                                            >
                                                <span
                                                    className={
                                                        event.scope ===
                                                        'CORPORATE'
                                                            ? 'agenda-marker corporate'
                                                            : 'agenda-marker personal'
                                                    }
                                                />

                                                <div>
                                                    <strong>
                                                        {
                                                            event.title
                                                        }
                                                    </strong>

                                                    <span>
                                                        {event.start
                                                            .toLocaleTimeString(
                                                                'es-ES',
                                                                {
                                                                    hour:
                                                                        '2-digit',
                                                                    minute:
                                                                        '2-digit'
                                                                }
                                                            )}
                                                        {' · '}
                                                        {event.scope ===
                                                        'CORPORATE'
                                                            ? 'Corporativo'
                                                            : 'Personal'}
                                                    </span>
                                                </div>
                                            </button>
                                        )
                                    )}
                                </div>
                            )}
                        </section>
                    </div>

                    <section className="calendar-section">
                        <div className="section-header">
                            <div>
                                <span className="eyebrow">
                                    Planificación
                                </span>

                                <h3 className="section-title">
                                    Mi calendario
                                </h3>
                            </div>

                            <div className="calendar-legend">
                                <span>
                                    <i className="legend-personal" />
                                    Personal
                                </span>

                                <span>
                                    <i className="legend-corporate" />
                                    Corporativo
                                </span>
                            </div>
                        </div>

                        <div className="calendar-wrapper">
                            <Calendar
                                localizer={
                                    localizer
                                }
                                events={
                                    events
                                }
                                startAccessor="start"
                                endAccessor="end"
                                culture="es"
                                selectable
                                onSelectSlot={
                                    handleSelectSlot
                                }
                                onSelectEvent={
                                    handleSelectEvent
                                }
                                eventPropGetter={
                                    eventPropGetter
                                }
                                date={
                                    currentDate
                                }
                                onNavigate={
                                    setCurrentDate
                                }
                                view={
                                    currentView
                                }
                                onView={
                                    setCurrentView
                                }
                                style={{
                                    height:
                                        560
                                }}
                                messages={{
                                    next: 'Sig',
                                    previous: 'Ant',
                                    today: 'Hoy',
                                    month: 'Mes',
                                    week: 'Semana',
                                    day: 'Día',
                                    agenda: 'Agenda',
                                    noEventsInRange:
                                        'No hay eventos en este rango.'
                                }}
                            />
                        </div>
                    </section>
                </div>
            </main>

            {isEventDetailsOpen &&
                selectedEvent && (
                    <div className="modal-overlay">
                        <div
                            className="modal-container"
                            style={{
                                maxWidth:
                                    '480px'
                            }}
                        >
                            <div className="modal-header">
                                <h3 className="modal-title">
                                    Detalles del evento
                                </h3>

                                <button
                                    type="button"
                                    onClick={() =>
                                        setIsEventDetailsOpen(
                                            false
                                        )
                                    }
                                    className="close-btn"
                                >
                                    ✕
                                </button>
                            </div>

                            <div className="view-event-body">
                                <div className="view-event-title-row">
                                    <span
                                        className="color-indicator"
                                        style={{
                                            backgroundColor:
                                                selectedEvent.scope ===
                                                'CORPORATE'
                                                    ? '#0ea5e9'
                                                    : '#6366f1'
                                        }}
                                    />

                                    <h2>
                                        {
                                            selectedEvent.title
                                        }
                                    </h2>
                                </div>

                                <div className="view-event-detail">
                                    <div className="view-event-text">
                                        <strong>
                                            Horario
                                        </strong>

                                        <span>
                                            {formatDisplayDate(
                                                selectedEvent.start,
                                                selectedEvent.end
                                            )}
                                        </span>

                                        <span>
                                            {selectedEvent.scope ===
                                            'CORPORATE'
                                                ? 'Evento corporativo'
                                                : 'Evento personal'}
                                        </span>

                                        {selectedEvent.recurrenceFrequency && (
                                            <span>
                                                Recurrencia:{' '}
                                                {selectedEvent.recurrenceFrequency ===
                                                'DAILY'
                                                    ? 'Diaria'
                                                    : 'Semanal'}
                                            </span>
                                        )}
                                    </div>
                                </div>

                                {selectedEvent.description && (
                                    <div className="view-event-detail">
                                        <div className="view-event-text">
                                            <strong>
                                                Descripción
                                            </strong>

                                            <span>
                                                {
                                                    selectedEvent.description
                                                }
                                            </span>
                                        </div>
                                    </div>
                                )}

                                {selectedEvent.hasConflict && (
                                    <div className="conflict-alert">
                                        ⚠️ Este evento presenta un conflicto de horario.
                                    </div>
                                )}

                                <button
                                    type="button"
                                    onClick={
                                        handleOpenTimeline
                                    }
                                    className="timeline-open-button"
                                >
                                    📂 Centro de contexto y evidencias
                                </button>

                                <div className="modal-actions-spaced">
                                    <button
                                        type="button"
                                        onClick={
                                            handleDeleteEvent
                                        }
                                        className="delete-btn"
                                    >
                                        Eliminar
                                    </button>

                                    <button
                                        type="button"
                                        onClick={
                                            handleEditEvent
                                        }
                                        className="confirm-btn"
                                    >
                                        Editar
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

            {isEventModalOpen && (
                <div className="modal-overlay">
                    <div className="modal-container">
                        <div className="modal-header">
                            <h3 className="modal-title">
                                {selectedEventId
                                    ? 'Modificar evento'
                                    : 'Nuevo evento'}
                            </h3>

                            <button
                                type="button"
                                onClick={() =>
                                    setIsEventModalOpen(
                                        false
                                    )
                                }
                                className="close-btn"
                            >
                                ✕
                            </button>
                        </div>

                        <form
                            onSubmit={
                                handleSaveEvent
                            }
                            className="modal-form"
                        >
                            <div className="form-group">
                                <label className="modal-label">
                                    Tipo de evento
                                </label>

                                <select
                                    value={
                                        eventScope
                                    }
                                    onChange={(event) =>
                                        setEventScope(
                                            event.target.value
                                        )
                                    }
                                    className="modal-input"
                                >
                                    <option value="PERSONAL">
                                        Personal
                                    </option>

                                    <option value="CORPORATE">
                                        Corporativo
                                    </option>
                                </select>
                            </div>

                            <div className="form-group">
                                <label className="modal-label">
                                    Asunto
                                </label>

                                <input
                                    type="text"
                                    value={
                                        title
                                    }
                                    onChange={(event) =>
                                        setTitle(
                                            event.target.value
                                        )
                                    }
                                    className="modal-input"
                                    required
                                />
                            </div>

                            <div className="form-group">
                                <label className="modal-label">
                                    Descripción
                                </label>

                                <textarea
                                    value={
                                        description
                                    }
                                    onChange={(event) =>
                                        setDescription(
                                            event.target.value
                                        )
                                    }
                                    className="modal-input"
                                    style={{
                                        minHeight:
                                            '70px',
                                        resize:
                                            'vertical'
                                    }}
                                />
                            </div>

                            <div className="form-row">
                                <div className="form-group">
                                    <label className="modal-label">
                                        Inicio
                                    </label>

                                    <input
                                        type="datetime-local"
                                        value={
                                            startTime
                                        }
                                        onChange={(event) =>
                                            setStartTime(
                                                event.target.value
                                            )
                                        }
                                        min={
                                            !selectedEventId
                                                ? getMinDateTime()
                                                : undefined
                                        }
                                        className="modal-input"
                                        required
                                    />
                                </div>

                                <div className="form-group">
                                    <label className="modal-label">
                                        Fin
                                    </label>

                                    <input
                                        type="datetime-local"
                                        value={
                                            endTime
                                        }
                                        onChange={(event) =>
                                            setEndTime(
                                                event.target.value
                                            )
                                        }
                                        min={
                                            startTime ||
                                            getMinDateTime()
                                        }
                                        className="modal-input"
                                        required
                                    />
                                </div>
                            </div>

                            <div className="form-row">
                                <div className="form-group">
                                    <label className="modal-label">
                                        Repetir
                                    </label>

                                    <select
                                        value={
                                            recurrence
                                        }
                                        onChange={(event) =>
                                            setRecurrence(
                                                event.target.value
                                            )
                                        }
                                        className="modal-input"
                                    >
                                        <option>
                                            Ninguna
                                        </option>

                                        <option>
                                            Diaria
                                        </option>

                                        <option>
                                            Semanal
                                        </option>
                                    </select>
                                </div>
                            </div>

                            {eventScope ===
                                'CORPORATE' && (
                                    <div className="form-group">
                                        <label className="modal-label">
                                            Miembros asignados
                                        </label>

                                        {members.length ===
                                        0 ? (
                                            <p className="field-help">
                                                No hay miembros disponibles.
                                            </p>
                                        ) : (
                                            <div className="member-checklist">
                                                {members.map(
                                                    (member) => {
                                                        const memberUser =
                                                            member.user ||
                                                            null;

                                                        const userId =
                                                            memberUser?.id ||
                                                            member.userId;

                                                        const email =
                                                            memberUser
                                                                ?.email ||
                                                            member.userEmail ||
                                                            'Usuario';

                                                        if (!userId) {
                                                            return null;
                                                        }

                                                        return (
                                                            <label
                                                                key={
                                                                    member.id
                                                                }
                                                                className="member-check"
                                                            >
                                                                <input
                                                                    type="checkbox"
                                                                    checked={
                                                                        assignedUserIds.includes(
                                                                            userId
                                                                        )
                                                                    }
                                                                    onChange={() =>
                                                                        toggleAssignedMember(
                                                                            member
                                                                        )
                                                                    }
                                                                />

                                                                <span>
                                                                {email}
                                                            </span>
                                                            </label>
                                                        );
                                                    }
                                                )}
                                            </div>
                                        )}
                                    </div>
                                )}

                            <div className="modal-actions-spaced">
                                <button
                                    type="button"
                                    onClick={() =>
                                        setIsEventModalOpen(
                                            false
                                        )
                                    }
                                    className="cancel-btn"
                                >
                                    Cancelar
                                </button>

                                <button
                                    type="submit"
                                    className="confirm-btn"
                                    disabled={
                                        isSubmitting
                                    }
                                >
                                    {isSubmitting
                                        ? 'Guardando...'
                                        : selectedEventId
                                            ? 'Actualizar evento'
                                            : 'Crear evento'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {isTimelineModalOpen &&
                selectedEvent && (
                    <div className="modal-overlay">
                        <div
                            className="modal-container"
                            style={{
                                maxWidth:
                                    '550px'
                            }}
                        >
                            <div className="modal-header">
                                <h3 className="modal-title">
                                    Contexto:{' '}
                                    {
                                        selectedEvent.title
                                    }
                                </h3>

                                <button
                                    type="button"
                                    onClick={() =>
                                        setIsTimelineModalOpen(
                                            false
                                        )
                                    }
                                    className="close-btn"
                                >
                                    ✕
                                </button>
                            </div>

                            <div className="timeline-list">
                                {timelineEntries.length ===
                                0 ? (
                                    <p className="agenda-empty">
                                        Aún no hay evidencias registradas.
                                    </p>
                                ) : (
                                    timelineEntries.map(
                                        (entry) => (
                                            <div
                                                key={
                                                    entry.id
                                                }
                                                className="timeline-entry"
                                            >
                                                <div>
                                                    <strong>
                                                        {entry.authorName ||
                                                            'Usuario'}
                                                    </strong>

                                                    <span>
                                                        {new Date(
                                                            entry.createdAt
                                                        ).toLocaleString()}
                                                    </span>
                                                </div>

                                                <p>
                                                    {
                                                        entry.content
                                                    }
                                                </p>
                                            </div>
                                        )
                                    )
                                )}
                            </div>

                            <div className="form-group">
                                <label className="modal-label">
                                    Añadir evidencia o acuerdo
                                </label>

                                <textarea
                                    value={
                                        timelineContent
                                    }
                                    onChange={(event) =>
                                        setTimelineContent(
                                            event.target.value
                                        )
                                    }
                                    className="modal-input"
                                    style={{
                                        minHeight:
                                            '100px'
                                    }}
                                />
                            </div>

                            <div className="modal-actions-spaced">
                                <button
                                    type="button"
                                    onClick={() =>
                                        setIsTimelineModalOpen(
                                            false
                                        )
                                    }
                                    className="cancel-btn"
                                >
                                    Volver
                                </button>

                                <button
                                    type="button"
                                    onClick={
                                        handleAddTimelineEntry
                                    }
                                    className="confirm-btn"
                                    disabled={
                                        isSubmitting
                                    }
                                >
                                    {isSubmitting
                                        ? 'Guardando...'
                                        : 'Publicar'}
                                </button>
                            </div>
                        </div>
                    </div>
                )}

            {isOrganizationModalOpen && (
                <div className="modal-overlay">
                    <div className="modal-container org-modal">
                        <div className="modal-header">
                            <h3 className="modal-title">
                                Gestión del Workspace
                            </h3>

                            <button
                                type="button"
                                onClick={() =>
                                    setIsOrganizationModalOpen(
                                        false
                                    )
                                }
                                className="close-btn"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="org-split-layout">
                            <div className="org-half">
                                <div className="org-half-header">
                                    <h4>
                                        Crear organización
                                    </h4>

                                    <p>
                                        Crea un espacio de trabajo para tu equipo.
                                    </p>
                                </div>

                                <div className="org-form-group">
                                    <label className="modal-label">
                                        Nombre de la empresa
                                    </label>

                                    <input
                                        type="text"
                                        value={
                                            orgName
                                        }
                                        onChange={(event) =>
                                            setOrgName(
                                                event.target.value
                                            )
                                        }
                                        placeholder="Ej: Acme Corp"
                                        className="modal-input"
                                    />
                                </div>

                                <button
                                    type="button"
                                    onClick={
                                        handleCreateOrganization
                                    }
                                    disabled={
                                        isSubmitting
                                    }
                                    className="primary-btn org-action-btn"
                                >
                                    {isSubmitting
                                        ? 'Creando...'
                                        : 'Crear organización'}
                                </button>
                            </div>

                            <div className="org-divider" />

                            <div className="org-half">
                                <div className="org-half-header">
                                    <h4>
                                        Unirse a un equipo
                                    </h4>

                                    <p>
                                        Usa el código que te proporcionó el propietario.
                                    </p>
                                </div>

                                <div className="org-form-group">
                                    <label className="modal-label">
                                        Código de invitación
                                    </label>

                                    <input
                                        type="text"
                                        value={
                                            orgCode
                                        }
                                        onChange={(event) =>
                                            setOrgCode(
                                                event.target.value
                                            )
                                        }
                                        placeholder="Ej: ORG-A1B2C3"
                                        className="modal-input"
                                    />
                                </div>

                                <button
                                    type="button"
                                    onClick={
                                        handleJoinOrganization
                                    }
                                    disabled={
                                        isSubmitting
                                    }
                                    className="secondary-btn org-action-btn"
                                >
                                    {isSubmitting
                                        ? 'Uniéndote...'
                                        : 'Unirme'}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {isSettingsModalOpen && (
                <div className="modal-overlay">
                    <div
                        className="modal-container settings-modal"
                    >
                        <div className="modal-header">
                            <h3 className="modal-title">
                                Configuración
                            </h3>

                            <button
                                type="button"
                                onClick={() =>
                                    setIsSettingsModalOpen(
                                        false
                                    )
                                }
                                className="close-btn"
                            >
                                ✕
                            </button>
                        </div>

                        <div className="settings-section">
                            <h4>
                                Organización
                            </h4>

                            {!activeOrganizationId ? (
                                <p className="field-help">
                                    No tienes una organización activa.
                                </p>
                            ) : (
                                <>
                                    <div className="form-group">
                                        <label className="modal-label">
                                            Nombre
                                        </label>

                                        <input
                                            type="text"
                                            value={
                                                organizationName
                                            }
                                            onChange={(event) =>
                                                setOrganizationName(
                                                    event.target.value
                                                )
                                            }
                                            className="modal-input"
                                        />
                                    </div>

                                    <button
                                        type="button"
                                        onClick={
                                            handleUpdateOrganization
                                        }
                                        className="primary-btn"
                                        disabled={
                                            isSubmitting
                                        }
                                    >
                                        Guardar organización
                                    </button>

                                    <div className="settings-divider" />

                                    <h4>
                                        Generar invitación
                                    </h4>

                                    <div className="form-row">
                                        <div className="form-group">
                                            <label className="modal-label">
                                                Máximo de usos
                                            </label>

                                            <input
                                                type="number"
                                                min="1"
                                                value={
                                                    inviteMaxUses
                                                }
                                                onChange={(event) =>
                                                    setInviteMaxUses(
                                                        event.target.value
                                                    )
                                                }
                                                className="modal-input"
                                            />
                                        </div>

                                        <button
                                            type="button"
                                            onClick={
                                                handleGenerateInvite
                                            }
                                            className="primary-btn settings-action"
                                            disabled={
                                                isSubmitting
                                            }
                                        >
                                            Generar código
                                        </button>
                                    </div>

                                    {lastInviteCode && (
                                        <div className="invite-code-box">
                                            <span>
                                                Código generado
                                            </span>

                                            <strong>
                                                {
                                                    lastInviteCode
                                                }
                                            </strong>
                                        </div>
                                    )}

                                    <div className="settings-divider" />

                                    <h4>
                                        Miembros
                                    </h4>

                                    {members.length ===
                                    0 ? (
                                        <p className="field-help">
                                            No hay miembros registrados.
                                        </p>
                                    ) : (
                                        <div className="members-list">
                                            {members.map(
                                                (member) => {
                                                    const memberEmail =
                                                        member.user
                                                            ?.email ||
                                                        member.userEmail ||
                                                        'Usuario';

                                                    const memberRole =
                                                        member.role ||
                                                        'EMPLOYEE';

                                                    return (
                                                        <div
                                                            key={
                                                                member.id
                                                            }
                                                            className="member-row"
                                                        >
                                                            <div>
                                                                <strong>
                                                                    {
                                                                        memberEmail
                                                                    }
                                                                </strong>

                                                                <span>
                                                                    {
                                                                        memberRole
                                                                    }
                                                                </span>
                                                            </div>

                                                            {memberRole !==
                                                            'OWNER' ? (
                                                                <div className="member-actions">
                                                                    <select
                                                                        value={
                                                                            memberRole
                                                                        }
                                                                        onChange={(event) =>
                                                                            handleUpdateMemberRole(
                                                                                member.id,
                                                                                event.target.value
                                                                            )
                                                                        }
                                                                        className="member-role-select"
                                                                    >
                                                                        <option value="ADMIN">
                                                                            Administrador
                                                                        </option>

                                                                        <option value="EMPLOYEE">
                                                                            Empleado
                                                                        </option>
                                                                    </select>

                                                                    <button
                                                                        type="button"
                                                                        onClick={() =>
                                                                            handleRemoveMember(
                                                                                member.id
                                                                            )
                                                                        }
                                                                        className="remove-member-button"
                                                                    >
                                                                        Eliminar
                                                                    </button>
                                                                </div>
                                                            ) : (
                                                                <span className="owner-badge">
                                                                    Propietario
                                                                </span>
                                                            )}
                                                        </div>
                                                    );
                                                }
                                            )}
                                        </div>
                                    )}
                                </>
                            )}
                        </div>

                        <div className="settings-section">
                            <h4>
                                Preferencias
                            </h4>

                            <button
                                type="button"
                                onClick={
                                    toggleTheme
                                }
                                className="secondary-btn"
                            >
                                {isDarkMode
                                    ? 'Cambiar a modo claro'
                                    : 'Cambiar a modo oscuro'}
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}