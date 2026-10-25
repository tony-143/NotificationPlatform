// Check if user is authenticated
async function checkAuth() {
    try {
        const response = await fetch(`${API_BASE_URL}/api/status`, {
            credentials: 'include'
        });
        return response.ok;
    } catch {
        return false;
    }
}

// Logout function
async function logout() {
    try {
        await fetch(`${API_BASE_URL}/api/auth/logout`, {
            method: 'POST',
            credentials: 'include'
        });
    } catch (error) {
        console.error('Logout error:', error);
    }
    window.location.href = 'index.html';
}

// Get CSRF token if needed
async function getCsrfToken() {
    try {
        const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
            credentials: 'include'
        });
        if (response.ok) {
            const data = await response.json();
            return data;
        }
    } catch (error) {
        console.error('CSRF error:', error);
    }
    return null;
}
