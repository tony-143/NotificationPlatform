// The API always runs on port 8081 of this machine.
// Use the page's own hostname instead of hard-coding "localhost": browsers treat
// localhost and 127.0.0.1 as *different sites*, so a cookie set for localhost is
// never sent by a page served from 127.0.0.1 (SameSite=Lax), which makes every
// authenticated request look like an expired session.
const API_PORT = 8081;
const API_HOST = window.location.hostname || 'localhost';
const API_BASE_URL = `http://${API_HOST}:${API_PORT}`;

console.info('[config] page origin:', window.location.origin, '| API:', API_BASE_URL);

