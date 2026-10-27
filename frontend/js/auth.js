// Shared keys used in localStorage
const STORAGE_KEYS = {
    user: 'np_user',
    apiKey: 'np_apiKey',
    pendingEmail: 'np_pendingEmail'
};

// Replace a button's content with a spinner while an async task runs.
function setButtonLoading(button, isLoading, loadingText = 'Please wait…') {
    if (!button) {
        return;
    }

    if (isLoading) {
        if (!button.dataset.originalHtml) {
            button.dataset.originalHtml = button.innerHTML;
        }
        button.disabled = true;
        button.classList.add('is-loading');
        button.setAttribute('aria-busy', 'true');

        const spinner = document.createElement('span');
        spinner.className = 'spinner';
        spinner.setAttribute('aria-hidden', 'true');

        const label = document.createElement('span');
        label.textContent = loadingText;

        button.replaceChildren(spinner, label);
        return;
    }

    button.disabled = false;
    button.classList.remove('is-loading');
    button.removeAttribute('aria-busy');

    if (button.dataset.originalHtml) {
        button.innerHTML = button.dataset.originalHtml;
        delete button.dataset.originalHtml;
    }
}

// Disable (or re-enable) every field in a form while a request is in flight.
function setFormBusy(form, busy) {
    if (!form) {
        return;
    }

    form.querySelectorAll('input, select, textarea, button').forEach((field) => {
        if (busy) {
            if (!field.disabled) {
                field.dataset.wasEnabled = 'true';
                field.disabled = true;
            }
        } else if (field.dataset.wasEnabled === 'true') {
            field.disabled = false;
            delete field.dataset.wasEnabled;
        }
    });
}

// Show a message in one of the existing .alert containers.
function showAlert(element, variant, message) {
    if (!element) {
        return;
    }
    element.className = `alert alert-${variant}`;
    element.textContent = message;
    element.style.display = 'flex';
}

function hideAlert(element) {
    if (!element) {
        return;
    }
    element.textContent = '';
    element.style.display = 'none';
}

// Read a JSON body without throwing when the server returns no/ invalid JSON.
async function readJson(response) {
    try {
        return await response.json();
    } catch {
        return null;
    }
}

// The backend has no /api/auth/me; /api/users is the protected endpoint we can
// safely probe to find out whether the session cookie is still valid.
// Returns 'ok', 'unauthorized' (server reachable, cookie missing/expired) or
// 'unreachable' (CORS/network failure) so pages can explain what went wrong.
async function checkAuth() {
    try {
        const response = await fetch(`${API_BASE_URL}/api/users`, {
            credentials: 'include',
            cache: 'no-store'
        });
        if (response.ok) {
            return 'ok';
        }
        console.warn(
            `[auth] ${API_BASE_URL}/api/users returned ${response.status}. ` +
            `Page origin: ${window.location.origin}. If you just signed in, the ` +
            'ACCESS_TOKEN cookie was not stored or not sent.'
        );
        return 'unauthorized';
    } catch (error) {
        console.error('Auth check failed:', error);
        return 'unreachable';
    }
}

async function requireAuth(redirectUrl = 'index.html') {
    const status = await checkAuth();
    if (status === 'ok') {
        return true;
    }
    const separator = redirectUrl.includes('?') ? '&' : '?';
    window.location.replace(`${redirectUrl}${separator}reason=${status === 'unreachable' ? 'network' : 'auth'}`);
    return false;
}

function setPageLoading(visible) {
    const overlay = document.getElementById('pageLoading');
    if (overlay) {
        overlay.classList.toggle('is-hidden', !visible);
    }
}

// Fetch the CSRF token issued by Spring's CookieCsrfTokenRepository.
async function getCsrfToken() {
    try {
        const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
            credentials: 'include',
            cache: 'no-store'
        });
        if (!response.ok) {
            return null;
        }
        return await response.json();
    } catch (error) {
        console.error('CSRF token request failed:', error);
        return null;
    }
}

const SAFE_HTTP_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];

// Wrapper for endpoints protected by CSRF (/api/keys, logout, password change).
async function authFetch(url, options = {}) {
    const request = {
        credentials: 'include',
        ...options,
        headers: new Headers(options.headers || {})
    };

    const method = (request.method || 'GET').toUpperCase();
    if (!SAFE_HTTP_METHODS.includes(method)) {
        const csrf = await getCsrfToken();
        if (csrf && csrf.token) {
            request.headers.set(csrf.headerName || 'X-XSRF-TOKEN', csrf.token);
        }
    }

    return fetch(url, request);
}

// Logout function
async function logout(button) {
    setButtonLoading(button, true, 'Signing out…');
    try {
        await authFetch(`${API_BASE_URL}/api/auth/logout`, {
            method: 'POST'
        });
    } catch (error) {
        console.error('Logout error:', error);
    } finally {
        localStorage.removeItem(STORAGE_KEYS.user);
        localStorage.removeItem(STORAGE_KEYS.apiKey);
        window.location.href = 'index.html';
    }
}
