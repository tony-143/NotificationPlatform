/* Settings: read the signed-in profile from the server, offer password + sign-out. */

document.addEventListener('DOMContentLoaded', initSettings);

async function initSettings() {
    document.getElementById('apiBase').textContent = API_BASE_URL;
    document.getElementById('signOutBtn').addEventListener('click', (event) => logout(event.currentTarget));

    const authenticated = await requireAuth('index.html');
    if (!authenticated) {
        return;
    }
    setPageLoading(false);
    await loadProfile();
}

async function loadProfile() {
    const body = document.getElementById('profileBody');
    body.setAttribute('aria-busy', 'true');

    try {
        const profile = await api.get('/api/users/me');
        renderProfile(profile);
        document.getElementById('accountId').textContent = `#${profile.id}`;
        document.getElementById('verifiedState').textContent = profile.emailVerified
            ? 'Verified'
            : 'Not verified — API key creation is blocked until the email address is verified.';
    } catch (error) {
        if (error.isAuth) {
            window.location.replace('index.html?reason=auth');
            return;
        }
        const alert = document.getElementById('settingsAlert');
        alert.textContent = error.message;
        alert.hidden = false;
        body.innerHTML = `<div class="empty">
            <h3>Could not load your profile</h3>
            <p>${escapeHtml(error.message)}</p>
            <button class="btn btn-ghost btn-sm" type="button" id="profileRetry">Retry</button>
        </div>`;
        body.querySelector('#profileRetry').addEventListener('click', loadProfile);
    } finally {
        body.setAttribute('aria-busy', 'false');
    }
}

function renderProfile(profile) {
    const body = document.getElementById('profileBody');
    const initials = initialsOf(profile.name, profile.email);

    body.innerHTML = `
        <div class="profile-row">
            <span class="avatar avatar-lg" aria-hidden="true">${escapeHtml(initials)}</span>
            <div style="min-width:0">
                <div class="profile-name">${escapeHtml(profile.name || 'Unnamed account')}</div>
                <div class="profile-email">${escapeHtml(profile.email || '')}</div>
            </div>
            <span class="spacer"></span>
            ${
                profile.emailVerified
                    ? '<span class="badge badge-active">Email verified</span>'
                    : '<span class="badge badge-pending">Email unverified</span>'
            }
        </div>
        <p class="muted-note" style="margin:14px 0 0">
            Profile fields are managed by the authentication service. This build does not expose a
            profile-edit endpoint, so the values above are read-only.
        </p>`;

    // Keep the shell's display name in sync without caching anything sensitive.
    try {
        const stored = JSON.parse(localStorage.getItem(STORAGE_KEYS.user) || 'null');
        if (stored) {
            localStorage.setItem(
                STORAGE_KEYS.user,
                JSON.stringify({ ...stored, name: profile.name, email: profile.email })
            );
        }
    } catch {
        /* ignore malformed storage */
    }
}

function initialsOf(name, email) {
    const source = (name || email || '?').trim();
    const parts = source.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return source.slice(0, 2).toUpperCase();
}
