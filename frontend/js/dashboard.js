/* Dashboard: real analytics from /api/dashboard/**, no fabricated data. */

const dashState = {
    range: '7d',
    page: 0,
    size: 10,
    filters: { status: '', channel: '', q: '', from: '', to: '' }
};

const dashErrors = { analytics: null, table: null };

let summaryRequestId = 0;
let listRequestId = 0;
let searchTimer = null;
let lastFocused = null;

const STATUS_COLORS = {
    PENDING: '#60a5fa',
    PROCESSING: '#fbbf24',
    RETRYING: '#fbbf24',
    SENT: '#34d399',
    FAILED: '#f87171',
    CLOSED: '#7d83a8',
    PROVIDER_NOT_CONFIGURED: '#fca5a5'
};

document.addEventListener('DOMContentLoaded', initDashboard);

async function initDashboard() {
    bindDashboardEvents();

    const authenticated = await requireAuth('index.html');
    if (!authenticated) {
        return;
    }

    setPageLoading(false);
    await Promise.all([loadAnalytics(), loadNotifications()]);
}

/* ---------- loaders ---------- */

async function loadAnalytics() {
    const requestId = ++summaryRequestId;
    await Promise.allSettled([loadSummary(requestId), loadSeries(requestId)]);
}

async function loadSummary(requestId) {
    try {
        const data = await api.get(`/api/dashboard/summary?range=${encodeURIComponent(dashState.range)}`);
        if (requestId !== summaryRequestId) {
            return;
        }
        renderMetrics(data);
        renderStatusChart(data);
        renderChannelChart(data);
        dashErrors.analytics = null;
    } catch (error) {
        if (requestId !== summaryRequestId) {
            return;
        }
        handleDashError(error, 'analytics');
    } finally {
        refreshDashAlert();
    }
}

async function loadSeries(requestId) {
    try {
        const data = await api.get(`/api/dashboard/timeseries?range=${encodeURIComponent(dashState.range)}`);
        if (requestId !== summaryRequestId) {
            return;
        }
        renderVolumeChart(data);
        dashErrors.analytics = null;
    } catch (error) {
        if (requestId !== summaryRequestId) {
            return;
        }
        handleDashError(error, 'analytics');
    } finally {
        refreshDashAlert();
    }
}

async function loadNotifications() {
    const requestId = ++listRequestId;
    setTableBusy(true);

    const params = new URLSearchParams();
    params.set('page', String(dashState.page));
    params.set('size', String(dashState.size));
    if (dashState.filters.status) {
        params.set('status', dashState.filters.status);
    }
    if (dashState.filters.channel) {
        params.set('channel', dashState.filters.channel);
    }
    if (dashState.filters.q) {
        params.set('q', dashState.filters.q);
    }
    const from = startOfDayIso(dashState.filters.from);
    const to = endOfDayIso(dashState.filters.to);
    if (from) {
        params.set('from', from);
    }
    if (to) {
        params.set('to', to);
    }

    try {
        const data = await api.get(`/api/dashboard/notifications?${params.toString()}`);
        if (requestId !== listRequestId) {
            return;
        }
        renderNotificationTable(data);
        dashErrors.table = null;
    } catch (error) {
        if (requestId !== listRequestId) {
            return;
        }
        handleDashError(error, 'table');
        renderTableError(error);
    } finally {
        if (requestId === listRequestId) {
            setTableBusy(false);
            refreshDashAlert();
        }
    }
}

/* ---------- rendering: metrics ---------- */

function renderMetrics(summary) {
    const grid = document.getElementById('metricGrid');
    const accepted = summary.accepted;
    const inFlight = summary.processing + summary.retrying;

    const cards = [
        {
            label: 'Total notifications',
            value: formatCount(summary.total),
            sub: `Since ${formatDate(summary.from)}`
        },
        {
            label: 'Provider accepted',
            value: formatCount(accepted),
            sub: 'SENT — delivery receipt not available',
            tip: 'SENT means the email provider (or SMS stub) accepted the message. This backend has no delivery-receipt integration, so it never claims confirmed delivery.'
        },
        {
            label: 'Failed',
            value: formatCount(summary.failed),
            sub:
                summary.providerNotConfigured > 0
                    ? `${formatCount(summary.providerNotConfigured)} provider not configured`
                    : 'No provider configuration errors'
        },
        {
            label: 'Pending',
            value: formatCount(summary.pending),
            sub: 'Waiting to be picked up'
        },
        {
            label: 'Processing / retrying',
            value: formatCount(inFlight),
            sub: `${formatCount(summary.processing)} processing · ${formatCount(summary.retrying)} retrying`
        },
        {
            label: 'Success rate',
            value: summary.successRate === null ? '—' : `${summary.successRate.toFixed(1)}%`,
            sub: 'Accepted ÷ terminal outcomes',
            tip: 'Terminal outcomes are SENT + FAILED + CLOSED + PROVIDER_NOT_CONFIGURED. Pending work is excluded. Shown as — when there are no terminal outcomes yet.'
        }
    ];

    grid.innerHTML = cards
        .map(
            (card) => `<div class="metric">
                <div class="metric-label">
                    <span>${card.label}</span>
                    ${card.tip ? `<span class="hint-ic" title="${escapeHtml(card.tip)}" aria-hidden="true">i</span>` : ''}
                </div>
                <div class="metric-value">${card.value}</div>
                <div class="metric-sub">${escapeHtml(card.sub)}</div>
            </div>`
        )
        .join('');
    grid.setAttribute('aria-busy', 'false');

    const note = document.getElementById('providerNote');
    if (summary.providerNotConfigured > 0) {
        const count = formatCount(summary.providerNotConfigured);
        note.textContent = `${count} notification${summary.providerNotConfigured === 1 ? '' : 's'} could not be delivered because no provider is configured on this server. Add SMTP credentials to local-secrets.properties and restart the backend.`;
        note.hidden = false;
    } else {
        note.hidden = true;
    }
}

/* ---------- rendering: charts ---------- */

function renderVolumeChart(seriesData) {
    const buckets = buildBuckets(seriesData.range, seriesData.points);
    const labels = buckets.map((bucket) => formatBucketLabel(bucket.date, seriesData.bucket));
    const channelKeys = new Set();
    buckets.forEach((bucket) => Object.keys(bucket.channels).forEach((key) => channelKeys.add(key)));

    const ordered = [...channelKeys].sort((a, b) => {
        if (a === 'email') return -1;
        if (b === 'email') return 1;
        return a.localeCompare(b);
    });

    const series = ordered.map((key, index) => ({
        label: channelLabel(key),
        color: key === 'email' ? '#818cf8' : key === 'sms' ? '#22d3ee' : CHART_PALETTE[index % CHART_PALETTE.length],
        values: buckets.map((bucket) => bucket.channels[key] || 0)
    }));

    if (series.length === 0) {
        series.push({
            label: 'Notifications',
            color: '#818cf8',
            values: buckets.map((bucket) => bucket.total)
        });
    }

    renderLineChart(document.getElementById('volumeChart'), {
        labels,
        series,
        ariaLabel: `Notification volume for the last ${seriesData.range}`
    });

    renderLineChart(document.getElementById('failureChart'), {
        labels,
        series: [
            {
                label: 'Failures',
                color: '#f87171',
                dashed: true,
                values: buckets.map((bucket) => bucket.failed)
            }
        ],
        ariaLabel: 'Failed notifications over time'
    });

    const legend = document.getElementById('volumeLegend');
    legend.innerHTML = series
        .map((line) => `<span><i style="background:${line.color}"></i>${escapeHtml(line.label)}</span>`)
        .join('');

    const hint = document.getElementById('volumeHint');
    hint.textContent = seriesData.bucket === 'hour' ? 'Hourly buckets' : 'Daily buckets';
    document.getElementById('volumeChart').setAttribute('aria-busy', 'false');
    document.getElementById('failureChart').setAttribute('aria-busy', 'false');
}

function renderStatusChart(summary) {
    renderDonut(document.getElementById('statusChart'), {
        items: summary.statuses.map((entry) => ({
            label: statusLabel(entry.status),
            value: entry.count,
            color: STATUS_COLORS[entry.status] || '#818cf8'
        })),
        centerValue: formatCount(summary.total),
        centerLabel: 'notifications',
        ariaLabel: 'Notification status distribution'
    });
    document.getElementById('statusChart').setAttribute('aria-busy', 'false');
}

function renderChannelChart(summary) {
    const colors = { email: '#818cf8', sms: '#22d3ee' };
    renderBarList(document.getElementById('channelChart'), {
        items: summary.channels.map((entry) => ({
            label: channelLabel(entry.channel),
            value: entry.count,
            color: colors[entry.channel]
        })),
        emptyText: 'No notifications in this period.'
    });
    document.getElementById('channelChart').setAttribute('aria-busy', 'false');
}

/* ---------- rendering: table ---------- */

function renderNotificationTable(page) {
    const tbody = document.getElementById('notifBody');
    tbody.replaceChildren();

    if (!page.items.length) {
        const hasFilters =
            Boolean(dashState.filters.status) ||
            Boolean(dashState.filters.channel) ||
            Boolean(dashState.filters.q) ||
            Boolean(dashState.filters.from) ||
            Boolean(dashState.filters.to);

        const row = document.createElement('tr');
        row.className = 'empty-row';
        const cell = document.createElement('td');
        cell.colSpan = 7;
        cell.innerHTML = `<div class="empty">
            <svg width="34" height="34" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <rect x="3" y="5" width="18" height="14" rx="2.5" stroke="currentColor" stroke-width="1.5"/>
                <path d="M4 8l8 5 8-5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            <h3>${hasFilters ? 'No matching notifications' : 'No notifications yet'}</h3>
            <p>${
                hasFilters
                    ? 'Try a different status, channel, date range or search term.'
                    : 'Send your first notification from the API using the Documentation playground, then it will appear here.'
            }</p>
            ${hasFilters ? '<button class="btn btn-ghost btn-sm" type="button" id="emptyClearFilters">Clear filters</button>' : '<a class="btn btn-secondary btn-sm" href="docs.html#create-a-notification">Open the API guide</a>'}
        </div>`;
        row.append(cell);
        tbody.append(row);

        const clear = document.getElementById('emptyClearFilters');
        if (clear) {
            clear.addEventListener('click', resetFilters);
        }
    } else {
        page.items.forEach((item) => {
            const row = document.createElement('tr');
            row.dataset.id = String(item.id);
            row.tabIndex = 0;
            row.setAttribute('aria-label', `Open notification ${item.id}`);
            row.innerHTML = `
                <td class="mono strong">#${item.id}</td>
                <td>${escapeHtml(channelLabel(item.channel))}</td>
                <td class="mono">${escapeHtml(item.recipient || '—')}</td>
                <td>${statusBadge(item.status)}</td>
                <td>${formatDateTime(item.createdAt)}</td>
                <td>${item.scheduledAt ? formatDateTime(item.scheduledAt) : '<span class="cell-dim">—</span>'}</td>
                <td>${formatDateTime(item.updatedAt)}</td>`;
            tbody.append(row);
        });
    }

    document.getElementById('pagerInfo').textContent = `${formatCount(page.totalElements)} notification${
        page.totalElements === 1 ? '' : 's'
    } · Page ${page.totalPages === 0 ? 1 : page.page + 1} of ${Math.max(page.totalPages, 1)}`;
    document.getElementById('tableSummary').textContent = `Page size ${page.size}`;

    document.getElementById('prevPage').disabled = page.page <= 0;
    document.getElementById('nextPage').disabled = page.page + 1 >= page.totalPages;
}

function renderTableError(error) {
    const tbody = document.getElementById('notifBody');
    tbody.innerHTML = `<tr class="empty-row"><td colspan="7">
        <div class="empty">
            <h3>Could not load notifications</h3>
            <p>${escapeHtml(error.message)}</p>
            <button class="btn btn-ghost btn-sm" type="button" id="tableRetry">Retry</button>
        </div>
    </td></tr>`;
    document.getElementById('tableRetry').addEventListener('click', loadNotifications);
    document.getElementById('pagerInfo').textContent = 'Unavailable';
    document.getElementById('prevPage').disabled = true;
    document.getElementById('nextPage').disabled = true;
}

function setTableBusy(busy) {
    const tbody = document.getElementById('notifBody');
    tbody.setAttribute('aria-busy', String(busy));
    if (!busy) {
        return;
    }
    tbody.innerHTML = Array.from({ length: 5 })
        .map(() => '<tr><td colspan="7"><div class="skeleton skeleton-row"></div></td></tr>')
        .join('');
    document.getElementById('pagerInfo').textContent = 'Loading…';
    document.getElementById('prevPage').disabled = true;
    document.getElementById('nextPage').disabled = true;
}

/* ---------- detail drawer ---------- */

async function openDetail(notificationId) {
    lastFocused = document.activeElement;
    const drawer = document.getElementById('detailDrawer');
    const backdrop = document.getElementById('drawerBackdrop');

    drawer.hidden = false;
    backdrop.hidden = false;
    document.body.classList.add('no-scroll');
    document.getElementById('detailTitle').textContent = `Notification #${notificationId}`;
    document.getElementById('detailSub').textContent = '';
    document.getElementById('detailBody').innerHTML =
        '<div class="skeleton skeleton-row" style="width:70%"></div>'.repeat(6);
    document.getElementById('detailClose').focus();

    try {
        const detail = await api.get(`/api/dashboard/notifications/${notificationId}`);
        renderDetail(detail);
    } catch (error) {
        if (error.isAuth) {
            window.location.replace('index.html?reason=auth');
            return;
        }
        document.getElementById('detailBody').innerHTML = `<div class="portal-alert error">${escapeHtml(
            error.message
        )}</div>`;
    }
}

function renderDetail(detail) {
    const body = document.getElementById('detailBody');
    document.getElementById('detailSub').textContent = `${channelLabel(detail.channel)} to ${detail.recipient}`;

    const payload = prettyPayload(detail.payload);

    const rows = [
        ['Status', statusBadge(detail.status)],
        ['Channel', escapeHtml(channelLabel(detail.channel))],
        ['Recipient', `<span class="mono">${escapeHtml(detail.recipient || '—')}</span>`],
        ['Created', formatDateTime(detail.createdAt)],
        ['Last updated', formatDateTime(detail.updatedAt)],
        ['Scheduled', detail.scheduledAt ? formatDateTime(detail.scheduledAt) : '—'],
        ['Next attempt', detail.nextAttemptAt ? formatDateTime(detail.nextAttemptAt) : '—'],
        ['Closed', detail.closedAt ? formatDateTime(detail.closedAt) : '—'],
        ['Expires', detail.expiresAt ? formatDateTime(detail.expiresAt) : '—'],
        ['Retries', formatCount(detail.retryCount)]
    ];

    if (detail.statusMessage) {
        rows.push(['Status message', escapeHtml(detail.statusMessage)]);
    }

    body.innerHTML = `
        <dl class="kv">${rows
            .map(([key, value]) => `<dt>${key}</dt><dd>${value}</dd>`)
            .join('')}</dl>

        <h3 class="drawer-section-title">Message</h3>
        <div class="message-box">${escapeHtml(detail.message || '—')}</div>

        <h3 class="drawer-section-title">Payload</h3>
        ${
            payload
                ? `<pre class="payload-box">${escapeHtml(payload)}</pre>`
                : '<p class="muted-note">No payload was supplied with this notification.</p>'
        }

        <h3 class="drawer-section-title">Attempt history</h3>
        <p class="muted-note">
            The backend stores the latest retry count and status timestamp rather than a full
            per-attempt table. The timeline below is derived from those fields.
        </p>
        <ol class="timeline">
            ${detail.timeline
                .map(
                    (event) => `<li>
                        <div class="t-label">${escapeHtml(event.label)}</div>
                        <div class="t-detail">${escapeHtml(event.detail || '')}</div>
                        <div class="t-time">${formatDateTime(event.at)}</div>
                    </li>`
                )
                .join('')}
        </ol>`;
}

function closeDetail() {
    document.getElementById('detailDrawer').hidden = true;
    document.getElementById('drawerBackdrop').hidden = true;
    document.body.classList.remove('no-scroll');
    if (lastFocused && typeof lastFocused.focus === 'function') {
        lastFocused.focus();
    }
    lastFocused = null;
}

function trapFocus(event) {
    const drawer = document.getElementById('detailDrawer');
    if (drawer.hidden || event.key !== 'Tab') {
        return;
    }
    const focusable = drawer.querySelectorAll(
        'a[href], button:not([disabled]), input, select, textarea, [tabindex]:not([tabindex="-1"])'
    );
    if (!focusable.length) {
        return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
    }
}

/* ---------- events ---------- */

function bindDashboardEvents() {
    document.querySelectorAll('.segmented [data-range]').forEach((button) => {
        button.addEventListener('click', () => {
            if (button.dataset.range === dashState.range) {
                return;
            }
            dashState.range = button.dataset.range;
            document.querySelectorAll('.segmented [data-range]').forEach((other) => {
                other.setAttribute('aria-pressed', String(other === button));
            });
            loadAnalytics();
        });
    });

    const search = document.getElementById('filterSearch');
    search.addEventListener('input', () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => {
            dashState.filters.q = search.value.trim();
            dashState.page = 0;
            loadNotifications();
        }, 320);
    });

    document.getElementById('filterStatus').addEventListener('change', (event) => {
        dashState.filters.status = event.target.value;
        dashState.page = 0;
        loadNotifications();
    });

    document.getElementById('filterChannel').addEventListener('change', (event) => {
        dashState.filters.channel = event.target.value;
        dashState.page = 0;
        loadNotifications();
    });

    ['filterFrom', 'filterTo'].forEach((id) => {
        document.getElementById(id).addEventListener('change', (event) => {
            const key = id === 'filterFrom' ? 'from' : 'to';
            dashState.filters[key] = event.target.value;
            if (
                dashState.filters.from &&
                dashState.filters.to &&
                dashState.filters.from > dashState.filters.to
            ) {
                dashErrors.table = 'The “from” date must be on or before the “to” date.';
                refreshDashAlert();
                return;
            }
            dashState.page = 0;
            loadNotifications();
        });
    });

    document.getElementById('resetFilters').addEventListener('click', resetFilters);

    document.getElementById('prevPage').addEventListener('click', () => {
        if (dashState.page > 0) {
            dashState.page -= 1;
            loadNotifications();
        }
    });

    document.getElementById('nextPage').addEventListener('click', () => {
        dashState.page += 1;
        loadNotifications();
    });

    document.getElementById('notifBody').addEventListener('click', (event) => {
        const row = event.target.closest('tr[data-id]');
        if (row) {
            openDetail(row.dataset.id);
        }
    });

    document.getElementById('notifBody').addEventListener('keydown', (event) => {
        const row = event.target.closest('tr[data-id]');
        if (row && (event.key === 'Enter' || event.key === ' ')) {
            event.preventDefault();
            openDetail(row.dataset.id);
        }
    });

    document.getElementById('detailClose').addEventListener('click', closeDetail);
    document.getElementById('drawerBackdrop').addEventListener('click', closeDetail);
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && !document.getElementById('detailDrawer').hidden) {
            closeDetail();
        }
        trapFocus(event);
    });
}

function resetFilters() {
    dashState.filters = { status: '', channel: '', q: '', from: '', to: '' };
    document.getElementById('filterSearch').value = '';
    document.getElementById('filterStatus').value = '';
    document.getElementById('filterChannel').value = '';
    document.getElementById('filterFrom').value = '';
    document.getElementById('filterTo').value = '';
    dashState.page = 0;
    dashErrors.table = null;
    refreshDashAlert();
    loadNotifications();
}

/* ---------- helpers ---------- */

function buildBuckets(range, points) {
    const bucket = range === '24h' ? 'hour' : 'day';
    const count = range === '24h' ? 24 : range === '30d' ? 30 : 7;
    const stepMs = bucket === 'hour' ? 3600000 : 86400000;

    // The API returns date_trunc() buckets in the database's timezone. Match
    // them by exact instant instead of re-deriving keys from the browser clock,
    // which would break for clients in another timezone.
    const byInstant = new Map();
    (points || []).forEach((point) => {
        const time = new Date(point.start).getTime();
        if (Number.isFinite(time)) {
            byInstant.set(time, point);
        }
    });

    let endMs;
    if (byInstant.size > 0) {
        endMs = Math.max(...byInstant.keys());
    } else {
        const now = new Date();
        if (bucket === 'hour') {
            now.setMinutes(0, 0, 0);
        } else {
            now.setHours(0, 0, 0, 0);
        }
        endMs = now.getTime();
    }

    const anchors = [];
    for (let index = count - 1; index >= 0; index -= 1) {
        anchors.push(new Date(endMs - index * stepMs));
    }

    return anchors.map((date) => {
        const point = byInstant.get(date.getTime());
        return {
            date,
            total: point ? point.total : 0,
            channels: point ? point.channels || {} : {},
            failed: point ? point.failed : 0
        };
    });
}

function formatBucketLabel(date, bucket) {
    if (bucket === 'hour') {
        return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    }
    return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function prettyPayload(payload) {
    if (!payload) {
        return null;
    }
    try {
        return JSON.stringify(JSON.parse(payload), null, 2);
    } catch {
        return payload;
    }
}

function handleDashError(error, source) {
    if (error.isAuth) {
        window.location.replace('index.html?reason=auth');
        return;
    }
    dashErrors[source] = error.message;
}

function refreshDashAlert() {
    const alert = document.getElementById('dashAlert');
    const messages = Object.values(dashErrors).filter(Boolean);
    if (messages.length === 0) {
        alert.hidden = true;
        alert.replaceChildren();
        return;
    }

    alert.replaceChildren();
    const text = document.createElement('span');
    text.className = 'grow';
    text.textContent = messages[0];
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.className = 'btn btn-ghost btn-sm';
    retry.textContent = 'Retry';
    retry.addEventListener('click', () => {
        dashErrors.analytics = null;
        dashErrors.table = null;
        refreshDashAlert();
        loadAnalytics();
        loadNotifications();
    });
    alert.append(text, retry);
    alert.hidden = false;
}
