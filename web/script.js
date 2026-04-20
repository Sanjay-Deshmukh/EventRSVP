let currentUser = null;
let cachedEvents = [];
let cachedMyEvents = [];

const els = {};

window.onload = function () {
    captureElements();
    bindEvents();
    bootstrapSession();
};

function captureElements() {
    els.authGuest = document.getElementById('auth-guest');
    els.authForm = document.getElementById('auth-form');
    els.authUser = document.getElementById('auth-user');
    els.usernameInput = document.getElementById('username-input');
    els.passwordInput = document.getElementById('password-input');
    els.usernameDisplay = document.getElementById('username-display');
    els.roleDisplay = document.getElementById('role-display');
    els.userAvatar = document.getElementById('user-avatar');
    els.loginBtn = document.getElementById('login-btn');
    els.registerBtn = document.getElementById('register-btn');
    els.logoutBtn = document.getElementById('logout-btn');
    els.modal = document.getElementById('modal');
    els.heroNextEvent = document.getElementById('hero-next-event');
    els.heroNextTime = document.getElementById('hero-next-time');
    els.heroRecommendationCount = document.getElementById('hero-recommendation-count');
    els.adminNavLink = document.getElementById('admin-nav-link');
    els.adminFooterLink = document.getElementById('admin-footer-link');
    els.adminGate = document.getElementById('admin-gate');
    els.adminDashboard = document.getElementById('admin-dashboard');
}

function bindEvents() {
    if (els.loginBtn) {
        els.loginBtn.addEventListener('click', login);
    }
    if (els.registerBtn) {
        els.registerBtn.addEventListener('click', register);
    }
    if (els.logoutBtn) {
        els.logoutBtn.addEventListener('click', logout);
    }
    if (els.passwordInput) {
        els.passwordInput.addEventListener('keydown', function (evt) {
            if (evt.key === 'Enter') {
                login();
            }
        });
    }

    bindSearch('events-search-btn', 'events-search-input', 'events-search-results');
    bindSearch('planner-search-btn', 'planner-search-input', 'planner-search-results');

    const createEventBtn = document.getElementById('admin-create-event-btn');
    if (createEventBtn) {
        createEventBtn.addEventListener('click', createAdminEvent);
    }
    const cancelEditBtn = document.getElementById('admin-cancel-edit-btn');
    if (cancelEditBtn) {
        cancelEditBtn.addEventListener('click', resetAdminForm);
    }
    const loadAttendeesBtn = document.getElementById('admin-load-attendees-btn');
    if (loadAttendeesBtn) {
        loadAttendeesBtn.addEventListener('click', loadAdminAttendees);
    }

    window.addEventListener('click', function (evt) {
        if (evt.target === els.modal) {
            closeModal();
        }
    });
}

function bindSearch(buttonId, inputId, targetId) {
    const button = document.getElementById(buttonId);
    const input = document.getElementById(inputId);
    if (!button || !input) {
        return;
    }
    button.addEventListener('click', function () {
        searchInto(inputId, targetId);
    });
    input.addEventListener('keydown', function (evt) {
        if (evt.key === 'Enter') {
            searchInto(inputId, targetId);
        }
    });
}

async function bootstrapSession() {
    await fetchCurrentUser();
    loadPublicData();
    refreshPrivateViews();
}

async function fetchCurrentUser() {
    try {
        const response = await fetch('/api/auth/me');
        const data = await response.json();
        currentUser = data.authenticated ? data : null;
    } catch (error) {
        console.error('Error loading session:', error);
        currentUser = null;
    }
    updateAuthUI();
}

function updateAuthUI() {
    if (currentUser) {
        if (els.authForm) {
            els.authForm.style.display = 'none';
        }
        if (els.authUser) {
            els.authUser.style.display = 'flex';
        }
        if (els.usernameDisplay) {
            els.usernameDisplay.textContent = currentUser.username;
        }
        if (els.roleDisplay) {
            els.roleDisplay.textContent = currentUser.isAdmin ? 'Admin' : 'User';
        }
        if (els.userAvatar) {
            els.userAvatar.textContent = currentUser.username.charAt(0).toUpperCase();
        }
    } else {
        if (els.authForm) {
            els.authForm.style.display = 'flex';
        }
        if (els.authUser) {
            els.authUser.style.display = 'none';
        }
    }

    if (els.authGuest) {
        els.authGuest.style.display = currentUser ? 'none' : 'flex';
    }

    const adminDisplay = currentUser && currentUser.isAdmin ? 'inline-flex' : 'none';
    if (els.adminNavLink) {
        els.adminNavLink.style.display = adminDisplay;
    }
    if (els.adminFooterLink) {
        els.adminFooterLink.style.display = currentUser && currentUser.isAdmin ? 'inline' : 'none';
    }
    if (els.adminGate && els.adminDashboard) {
        const isAdminPage = document.body.dataset.page === 'admin';
        els.adminGate.style.display = isAdminPage && (!currentUser || !currentUser.isAdmin) ? 'block' : 'none';
        els.adminDashboard.style.display = isAdminPage && currentUser && currentUser.isAdmin ? 'block' : 'none';
    }
}

async function register() {
    await submitAuth('/api/auth/register');
}

async function login() {
    await submitAuth('/api/auth/login');
}

async function submitAuth(endpoint) {
    const username = els.usernameInput ? els.usernameInput.value.trim() : '';
    const password = els.passwordInput ? els.passwordInput.value : '';
    if (!username || !password) {
        showModal('Credentials required', 'Please enter both a username and a password.', [
            { text: 'Close', action: closeModal, style: 'ghost' }
        ]);
        return;
    }

    try {
        const response = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: username, password: password })
        });
        const data = await response.json();
        if (!response.ok) {
            showModal('Authentication issue', escapeHtml(data.error || 'Unable to authenticate.'), [
                { text: 'Close', action: closeModal, style: 'ghost' }
            ]);
            return;
        }

        currentUser = data;
        if (els.passwordInput) {
            els.passwordInput.value = '';
        }
        updateAuthUI();
        if (document.body.dataset.page === 'login') {
            window.location.href = currentUser.isAdmin ? 'admin.html' : 'index.html';
            return;
        }
        loadPublicData();
        refreshPrivateViews();
    } catch (error) {
        console.error('Authentication error:', error);
        showModal('Request failed', 'The authentication request could not be completed.', [
            { text: 'Close', action: closeModal, style: 'ghost' }
        ]);
    }
}

async function logout() {
    try {
        await fetch('/api/auth/logout', { method: 'POST' });
    } catch (error) {
        console.error('Logout error:', error);
    }
    currentUser = null;
    cachedMyEvents = [];
    if (els.passwordInput) {
        els.passwordInput.value = '';
    }
    updateAuthUI();
    renderGuestStates();
    updateStats();
    updateHeroPreview();
    if (document.body.dataset.page === 'login') {
        return;
    }
}

function loadPublicData() {
    Promise.allSettled([loadEvents(), loadNextEventPreview()]).then(updateHeroPreview);
}

function refreshPrivateViews() {
    renderGuestStates();
    if (!currentUser) {
        return;
    }

    const jobs = [];
    if (document.getElementById('my-events-list')) {
        jobs.push(loadMyEvents());
    }
    if (document.getElementById('recommendations') || els.heroRecommendationCount) {
        jobs.push(loadRecommendations());
    }
    if (document.body.dataset.page === 'admin' && currentUser.isAdmin) {
        jobs.push(loadAdminDashboard());
    }
    Promise.allSettled(jobs).then(function () {
        updateStats();
        updateHeroPreview();
    });
}

function renderGuestStates() {
    setHtmlIfPresent('my-events-list', createEmptyState('Sign in to view your schedule', 'Your RSVPs appear here once you log in.'));
    setHtmlIfPresent('recommendations', createEmptyState('Sign in for recommendations', 'Recommendations are generated from your attendance history.'));
    setHtmlIfPresent('recommendation-memory', createEmptyState('No memory yet', 'Log in to load events you previously attended.'));
    if (document.body.dataset.page === 'admin' && els.adminDashboard) {
        els.adminDashboard.style.display = 'none';
        if (els.adminGate) {
            els.adminGate.style.display = 'block';
        }
    }
}

async function loadEvents() {
    setLoadingIfPresent('events-list', 'Loading events');
    setLoadingIfPresent('landing-events-preview', 'Preparing preview');
    try {
        const response = await fetch('/api/events');
        const events = await response.json();
        cachedEvents = Array.isArray(events) ? events : [];
        updateStats();
        if (document.getElementById('events-list')) {
            renderEventRows('events-list', cachedEvents, {});
        }
        if (document.getElementById('landing-events-preview')) {
            renderPreviewRail('landing-events-preview', cachedEvents.slice(0, 3));
        }
    } catch (error) {
        console.error('Error loading events:', error);
        setHtmlIfPresent('events-list', createAlert('error', 'Error loading events from the server.'));
        setHtmlIfPresent('landing-events-preview', createAlert('error', 'Error loading the homepage preview.'));
    }
}

async function loadNextEventPreview() {
    setLoadingIfPresent('next-event', 'Finding your next event');
    try {
        const response = await fetch('/api/events/next');
        const data = await response.json();
        if (data.error) {
            setHtmlIfPresent('next-event', createEmptyState('No upcoming events', data.error));
            setTextIfPresent('hero-next-event', 'No upcoming events');
            setTextIfPresent('hero-next-time', 'Add events to bring the planner to life.');
            return;
        }
        setTextIfPresent('hero-next-event', data.event.name);
        setTextIfPresent('hero-next-time', data.timeRemaining + ' until it starts');
        if (document.getElementById('next-event')) {
            renderNextFeature('next-event', data.event, data.timeRemaining);
        }
    } catch (error) {
        console.error('Error loading next event:', error);
        setHtmlIfPresent('next-event', createAlert('error', 'Error loading the next upcoming event.'));
    }
}

async function loadMyEvents() {
    try {
        setLoadingIfPresent('my-events-list', 'Loading your RSVPs');
        const response = await fetch('/api/events/my');
        if (response.status === 401) {
            renderGuestStates();
            return;
        }
        const events = await response.json();
        cachedMyEvents = Array.isArray(events) ? events : [];
        updateStats();
        if (!cachedMyEvents.length) {
            setHtmlIfPresent('my-events-list', createEmptyState('No RSVPs yet', 'You have not joined any events yet. Head to Browse Events to get started.'));
            return;
        }
        renderEventRows('my-events-list', cachedMyEvents, { showCancel: true });
    } catch (error) {
        console.error('Error loading my events:', error);
        setHtmlIfPresent('my-events-list', createAlert('error', 'Error loading your RSVPs.'));
    }
}

async function loadRecommendations() {
    try {
        if (!currentUser) {
            renderGuestStates();
            return;
        }
        setLoadingIfPresent('recommendations', 'Preparing recommendations');
        setLoadingIfPresent('recommendation-memory', 'Loading attendance history');
        const response = await fetch('/api/events/recommend');
        if (response.status === 401) {
            renderGuestStates();
            return;
        }
        const data = await response.json();
        if (data.error) {
            setTextIfPresent('hero-recommendation-count', 'Waiting for history');
            setHtmlIfPresent('recommendations', createEmptyState('No recommendations yet', data.error));
            setHtmlIfPresent('recommendation-memory', createEmptyState('Attendance history needed', data.error));
            return;
        }
        const recommendations = Array.isArray(data.recommendations) ? data.recommendations : [];
        setTextIfPresent('hero-recommendation-count', recommendations.length + ' tailored picks');
        renderRecommendationMemory(data.pastEvents || []);
        if (!recommendations.length) {
            setHtmlIfPresent('recommendations', createEmptyState('No close match yet', 'We checked the upcoming schedule but did not find a similar event right now.'));
            return;
        }
        renderEventRows('recommendations', recommendations, {});
    } catch (error) {
        console.error('Error loading recommendations:', error);
        setHtmlIfPresent('recommendations', createAlert('error', 'Error loading recommendations.'));
        setHtmlIfPresent('recommendation-memory', createAlert('error', 'Error loading attendance history.'));
    }
}

async function searchInto(inputId, targetId) {
    const input = document.getElementById(inputId);
    const keyword = input ? input.value.trim() : '';
    if (!keyword) {
        showModal('Search keyword needed', 'Enter a title or location so the site knows what to look for.', [{ text: 'Close', action: closeModal, style: 'ghost' }]);
        return;
    }
    setLoadingIfPresent(targetId, 'Searching events');
    try {
        const response = await fetch('/api/events/search?keyword=' + encodeURIComponent(keyword));
        const events = await response.json();
        if (!Array.isArray(events) || !events.length) {
            setHtmlIfPresent(targetId, createEmptyState('No results found', 'Nothing matched "' + escapeHtml(keyword) + '" this time.'));
            return;
        }
        renderEventRows(targetId, events, {});
    } catch (error) {
        console.error('Error searching events:', error);
        setHtmlIfPresent(targetId, createAlert('error', 'Error searching events.'));
    }
}

async function loadAdminDashboard() {
    if (!currentUser || !currentUser.isAdmin) {
        return;
    }
    try {
        const statsResponse = await fetch('/api/admin/stats');
        if (statsResponse.status === 403 || statsResponse.status === 401) {
            updateAuthUI();
            return;
        }
        const stats = await statsResponse.json();
        setTextIfPresent('admin-stat-events', String(stats.totalEvents || 0));
        setTextIfPresent('admin-stat-open', String(stats.openEvents || 0));
        setTextIfPresent('admin-stat-users', String(stats.totalUsers || 0));
        setTextIfPresent('admin-stat-rsvps', String(stats.totalRsvps || 0));

        const eventsResponse = await fetch('/api/admin/events');
        const events = await eventsResponse.json();
        renderAdminEvents(events);
        if (els.adminGate) {
            els.adminGate.style.display = 'none';
        }
        if (els.adminDashboard) {
            els.adminDashboard.style.display = 'block';
        }
    } catch (error) {
        console.error('Error loading admin dashboard:', error);
        setHtmlIfPresent('admin-events-list', createAlert('error', 'Error loading admin dashboard data.'));
    }
}

async function createAdminEvent() {
    const name = valueOf('admin-event-name');
    const dateTime = valueOf('admin-event-datetime');
    const location = valueOf('admin-event-location');
    const maxAttendees = Number(valueOf('admin-event-capacity'));
    const originalName = valueOf('admin-event-original-name');
    if (!name || !dateTime || !location || !maxAttendees) {
        showModal('Missing event fields', 'Please complete all admin event fields before creating the event.', [{ text: 'Close', action: closeModal, style: 'ghost' }]);
        return;
    }
    try {
        const response = await fetch(originalName ? '/api/admin/events/update' : '/api/admin/events', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ originalName: originalName, name: name, dateTime: dateTime, location: location, maxAttendees: maxAttendees })
        });
        const data = await response.json();
        if (!response.ok) {
            showModal(originalName ? 'Could not update event' : 'Could not create event', escapeHtml(data.error || 'Request failed.'), [{ text: 'Close', action: closeModal, style: 'ghost' }]);
            return;
        }
        resetAdminForm();
        loadPublicData();
        loadAdminDashboard();
    } catch (error) {
        console.error('Error creating event:', error);
    }
}

async function loadAdminAttendees() {
    const eventName = valueOf('admin-attendees-event');
    if (!eventName) {
        setHtmlIfPresent('admin-attendees-results', createEmptyState('Event name required', 'Enter the exact event name to view attendees.'));
        return;
    }
    setLoadingIfPresent('admin-attendees-results', 'Loading attendees');
    try {
        const response = await fetch('/api/admin/attendees?eventName=' + encodeURIComponent(eventName));
        const data = await response.json();
        if (!response.ok) {
            setHtmlIfPresent('admin-attendees-results', createAlert('error', data.error || 'Unable to load attendees.'));
            return;
        }
        if (!data.attendees.length) {
            setHtmlIfPresent('admin-attendees-results', createEmptyState('No attendees yet', 'This event does not have any RSVP entries yet.'));
            return;
        }
        const list = data.attendees.map(function (name) { return '<li>' + escapeHtml(name) + '</li>'; }).join('');
        setHtmlIfPresent('admin-attendees-results', '<div class="memory-list"><strong>' + escapeHtml(data.eventName) + '</strong><ul>' + list + '</ul></div>');
    } catch (error) {
        console.error('Error loading attendees:', error);
        setHtmlIfPresent('admin-attendees-results', createAlert('error', 'Unable to load attendees.'));
    }
}

async function closeAdminEvent(eventName) {
    try {
        const response = await fetch('/api/admin/events/close', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ eventName: eventName })
        });
        const data = await response.json();
        if (!response.ok) {
            showModal('Unable to close event', escapeHtml(data.error || 'Request failed.'), [{ text: 'Close', action: closeModal, style: 'ghost' }]);
            return;
        }
        loadPublicData();
        loadAdminDashboard();
    } catch (error) {
        console.error('Error closing event:', error);
    }
}

async function deleteAdminEvent(eventName) {
    try {
        const response = await fetch('/api/admin/events/delete', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ eventName: eventName })
        });
        const data = await response.json();
        if (!response.ok) {
            showModal('Unable to delete event', escapeHtml(data.error || 'Request failed.'), [{ text: 'Close', action: closeModal, style: 'ghost' }]);
            return;
        }
        resetAdminForm();
        loadPublicData();
        loadAdminDashboard();
    } catch (error) {
        console.error('Error deleting event:', error);
    }
}

function startAdminEdit(event) {
    setValue('admin-event-original-name', event.name);
    setValue('admin-event-name', event.name);
    setValue('admin-event-datetime', toDateTimeLocalValue(event.dateTime));
    setValue('admin-event-location', event.location);
    setValue('admin-event-capacity', String(event.maxAttendees));
    setTextIfPresent('admin-form-title', 'Edit event');
    setTextIfPresent('admin-create-event-btn', 'Save changes');
    const cancelEditBtn = document.getElementById('admin-cancel-edit-btn');
    if (cancelEditBtn) {
        cancelEditBtn.style.display = 'inline-flex';
    }
}

function resetAdminForm() {
    ['admin-event-original-name', 'admin-event-name', 'admin-event-datetime', 'admin-event-location', 'admin-event-capacity'].forEach(clearValue);
    setTextIfPresent('admin-form-title', 'Add a new event');
    setTextIfPresent('admin-create-event-btn', 'Create event');
    const cancelEditBtn = document.getElementById('admin-cancel-edit-btn');
    if (cancelEditBtn) {
        cancelEditBtn.style.display = 'none';
    }
}

function renderAdminEvents(events) {
    const container = document.getElementById('admin-events-list');
    if (!container) {
        return;
    }
    if (!Array.isArray(events) || !events.length) {
        container.innerHTML = createEmptyState('No events available', 'Create your first event from the admin dashboard.');
        return;
    }
    container.innerHTML = events.map(function (event) {
        const base = createEventRow(event, {});
        const eventJson = escapeHtml(JSON.stringify({
            name: event.name,
            dateTime: event.dateTime,
            location: event.location,
            maxAttendees: event.maxAttendees
        }));
        let actions = '<div class="event-actions admin-inline-actions">' +
            '<button class="ghost-btn" onclick="startAdminEdit(JSON.parse(this.dataset.event))" data-event="' + eventJson + '">Edit</button>' +
            '<button class="ghost-btn" onclick="deleteAdminEvent(\'' + escapeJsString(event.name) + '\')">Delete</button>';
        if (!event.closed) {
            actions += '<button class="ghost-btn" onclick="closeAdminEvent(\'' + escapeJsString(event.name) + '\')">Close RSVP</button>';
        }
        actions += '</div>';
        return base.replace('</article>', actions + '</article>');
    }).join('');
}

function renderPreviewRail(containerId, events) {
    const container = document.getElementById(containerId);
    if (!container) { return; }
    if (!events.length) {
        container.innerHTML = createEmptyState('No events available', 'Upcoming events will appear here once the server has data.');
        return;
    }
    container.innerHTML = '<div class="preview-strip">' + events.map(function (event) {
        return '<article class="preview-item"><p class="section-kicker">Preview</p><h3>' + escapeHtml(event.name) + '</h3><p class="preview-meta">' + escapeHtml(event.location) + '</p><p class="preview-meta">' + escapeHtml(formatDateTime(event.dateTime)) + '</p></article>';
    }).join('') + '</div>';
}

function renderEventRows(containerId, events, options) {
    const container = document.getElementById(containerId);
    if (!container) { return; }
    if (!events.length) {
        container.innerHTML = createEmptyState('Nothing to show', 'This section will fill in when matching events are available.');
        return;
    }
    container.innerHTML = events.map(function (event) { return createEventRow(event, options || {}); }).join('');
}

function createEventRow(event, options) {
    const settings = options || {};
    const attendeeCount = Number(event.attendees || 0);
    const maxAttendees = Number(event.maxAttendees || 0);
    const occupancy = maxAttendees > 0 ? Math.min(100, Math.round((attendeeCount / maxAttendees) * 100)) : 0;
    const isAttending = Boolean(event.isAttending) || Boolean(settings.showCancel);
    const canRSVP = currentUser && !event.closed && !isAttending && attendeeCount < maxAttendees;
    const safeEventName = escapeJsString(event.name);
    let statusLine = '<span class="status-pill status-open">Open for RSVP</span>';
    if (event.closed) {
        statusLine = '<span class="status-pill status-closed">Closed</span>';
    } else if (attendeeCount >= maxAttendees) {
        statusLine += '<span class="status-pill status-full">At capacity</span>';
    }
    let actions = '';
    if (canRSVP || isAttending) {
        actions += '<div class="event-actions">';
        if (canRSVP) { actions += '<button class="btn-rsvp" onclick="rsvpEvent(\'' + safeEventName + '\')">RSVP now</button>'; }
        if (isAttending) { actions += '<button class="btn-cancel" onclick="cancelRSVP(\'' + safeEventName + '\')">Cancel RSVP</button>'; }
        actions += '</div>';
    }
    return '<article class="event-row"><div class="event-row-main"><p class="section-kicker">Event</p><h3 class="event-row-title">' + escapeHtml(event.name) + '</h3><div class="badge-line">' + statusLine + '</div><p class="event-description">A shared-database event available from the web app and Telegram bot.</p></div><div class="event-row-data"><div class="data-points">' + createDataPoint('Location', event.location) + createDataPoint('Time', formatDateTime(event.dateTime)) + '</div><div class="capacity-line"><div class="capacity-meta"><span>Attendance</span><span>' + attendeeCount + ' / ' + maxAttendees + ' seats</span></div><div class="capacity-bar"><span style="width:' + occupancy + '%"></span></div></div></div>' + actions + '</article>';
}

function renderNextFeature(containerId, event, timeRemaining) {
    const container = document.getElementById(containerId);
    if (!container) { return; }
    const attendeeCount = Number(event.attendees || 0);
    const maxAttendees = Number(event.maxAttendees || 0);
    const occupancy = maxAttendees > 0 ? Math.min(100, Math.round((attendeeCount / maxAttendees) * 100)) : 0;
    const canRSVP = currentUser && !event.closed && attendeeCount < maxAttendees;
    const safeEventName = escapeJsString(event.name);
    const action = canRSVP ? '<div class="event-actions"><button class="btn-rsvp" onclick="rsvpEvent(\'' + safeEventName + '\')">RSVP now</button></div>' : '';
    container.innerHTML = '<article class="featured-event dark-surface"><p class="section-kicker">Starts soon</p><h3>' + escapeHtml(event.name) + '</h3><p class="event-note">' + escapeHtml(timeRemaining) + ' until it begins.</p><div class="featured-grid">' + createDataPoint('Location', event.location) + createDataPoint('Time', formatDateTime(event.dateTime)) + '</div><div class="capacity-line"><div class="capacity-meta"><span>Attendance</span><span>' + attendeeCount + ' / ' + maxAttendees + ' seats</span></div><div class="capacity-bar"><span style="width:' + occupancy + '%"></span></div></div>' + action + '</article>';
}

function renderRecommendationMemory(pastEvents) {
    const container = document.getElementById('recommendation-memory');
    if (!container) { return; }
    if (!pastEvents.length) {
        container.innerHTML = createEmptyState('No attendance memory yet', 'Attend events to build your personalized recommendation profile.');
        return;
    }
    container.innerHTML = '<div class="memory-list"><strong>Events you previously attended</strong><ul>' + pastEvents.map(function (name) { return '<li>' + escapeHtml(name) + '</li>'; }).join('') + '</ul></div>';
}

async function rsvpEvent(eventName) {
    if (!currentUser) {
        showModal('Log in first', 'Please log in before RSVPing to an event.', [{ text: 'Close', action: closeModal, style: 'ghost' }]);
        return;
    }
    try {
        const response = await fetch('/api/rsvp', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ eventName: eventName }) });
        const data = await response.json();
        if (data.conflict) {
            showModal('Schedule conflict detected', 'You already have another event in this time window.<br><br><strong>Conflicting event:</strong> ' + escapeHtml(data.conflictingEvent.name) + '<br><strong>When:</strong> ' + escapeHtml(formatDateTime(data.conflictingEvent.dateTime)) + '<br><strong>Where:</strong> ' + escapeHtml(data.conflictingEvent.location), [{ text: 'Cancel conflicting RSVP', action: function () { closeModal(); cancelRSVP(data.conflictingEvent.name); } }, { text: 'Keep current plan', action: closeModal, style: 'ghost' }]);
            return;
        }
        if (!response.ok) {
            showModal('Unable to RSVP', escapeHtml(data.error || 'Request failed.'), [{ text: 'Close', action: closeModal, style: 'ghost' }]);
            return;
        }
        loadPublicData();
        refreshPrivateViews();
    } catch (error) {
        console.error('Error RSVPing:', error);
    }
}

async function cancelRSVP(eventName) {
    try {
        const response = await fetch('/api/rsvp/cancel', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ eventName: eventName }) });
        const data = await response.json();
        if (!response.ok) {
            showModal('Unable to cancel', escapeHtml(data.error || 'Request failed.'), [{ text: 'Close', action: closeModal, style: 'ghost' }]);
            return;
        }
        loadPublicData();
        refreshPrivateViews();
    } catch (error) {
        console.error('Error canceling RSVP:', error);
    }
}

function updateStats() {
    setTextIfPresent('stat-total-events', String(cachedEvents.length));
    setTextIfPresent('stat-open-events', String(cachedEvents.filter(function (event) { return !event.closed && Number(event.attendees || 0) < Number(event.maxAttendees || 0); }).length));
    setTextIfPresent('stat-my-events', String(cachedMyEvents.length));
}

function updateHeroPreview() {
    if (!cachedEvents.length) {
        setTextIfPresent('hero-next-event', currentUser ? 'No live event data yet' : 'Syncing event feed');
        setTextIfPresent('hero-next-time', currentUser ? 'Create or load events to populate the website.' : 'Log in to personalize your event flow.');
    }
    if (!currentUser) {
        setTextIfPresent('hero-recommendation-count', 'Adaptive recommendations');
    }
}

function createDataPoint(label, value) { return '<div class="data-point"><span class="data-label">' + escapeHtml(label) + '</span><span class="data-value">' + escapeHtml(value) + '</span></div>'; }
function setLoadingIfPresent(id, label) { const element = document.getElementById(id); if (element) { element.innerHTML = '<div class="loading-state"><div class="loading-pill">' + escapeHtml(label) + '</div></div>'; } }
function setHtmlIfPresent(id, html) { const element = document.getElementById(id); if (element) { element.innerHTML = html; } }
function setTextIfPresent(id, text) { const element = document.getElementById(id); if (element) { element.textContent = text; } }
function valueOf(id) { const element = document.getElementById(id); return element ? element.value.trim() : ''; }
function clearValue(id) { const element = document.getElementById(id); if (element) { element.value = ''; } }
function setValue(id, value) { const element = document.getElementById(id); if (element) { element.value = value; } }
function toDateTimeLocalValue(value) { return value ? String(value).slice(0, 16) : ''; }
function createEmptyState(title, body) { return '<div class="empty-state"><strong>' + escapeHtml(title) + '</strong><span>' + escapeHtml(body) + '</span></div>'; }
function createAlert(kind, text) { return '<div class="alert alert-' + kind + '"><strong>' + (kind === 'error' ? 'Something went wrong' : 'Notice') + '</strong><span>' + escapeHtml(text) + '</span></div>'; }
function showModal(title, message, buttons) { const titleEl = document.getElementById('modal-title'); const messageEl = document.getElementById('modal-message'); const buttonsContainer = document.getElementById('modal-buttons'); if (!titleEl || !messageEl || !buttonsContainer || !els.modal) { return; } titleEl.textContent = title; messageEl.innerHTML = message; buttonsContainer.innerHTML = ''; buttons.forEach(function (btn) { const button = document.createElement('button'); button.textContent = btn.text; button.onclick = btn.action; button.className = btn.style === 'ghost' ? 'ghost-btn' : 'primary-btn'; buttonsContainer.appendChild(button); }); els.modal.style.display = 'block'; }
function closeModal() { if (els.modal) { els.modal.style.display = 'none'; } }
function formatDateTime(dateTimeStr) { const date = new Date(dateTimeStr); return date.toLocaleString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }); }
function escapeHtml(value) { return String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;'); }
function escapeJsString(value) { return String(value).replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/\r/g, ' ').replace(/\n/g, ' '); }
