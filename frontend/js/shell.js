/* Shared application shell: top navigation + account menu.
   Every portal page includes this after js/auth.js and sets
   <body class="portal" data-page="dashboard|api-keys|docs|settings">. */

const NAV_ITEMS = [
    { id: 'dashboard', label: 'Dashboard', href: 'dashboard.html' },
    { id: 'api-keys', label: 'API Keys', href: 'api-keys.html' },
    { id: 'docs', label: 'Documentation', href: 'docs.html' }
];

function currentUser() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEYS.user) || 'null');
    } catch {
        return null;
    }
}

function initialsFrom(name, email) {
    const source = (name || email || '?').trim();
    const parts = source.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return source.slice(0, 2).toUpperCase();
}

function buildShell() {
    const page = document.body.dataset.page || '';
    const user = currentUser();

    const header = document.createElement('header');
    header.className = 'app-header';

    const brand = document.createElement('a');
    brand.className = 'app-brand';
    brand.href = 'dashboard.html';
    brand.innerHTML = `
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect x="2.5" y="4.5" width="19" height="15" rx="3.5" stroke="#818cf8" stroke-width="1.6"/>
            <path d="M6.5 10.5h6M6.5 14h9" stroke="#a5b4fc" stroke-width="1.6" stroke-linecap="round"/>
            <circle cx="17.5" cy="9" r="1.6" fill="#6366f1"/>
        </svg>
        <span>Notification Platform</span>`;
    header.append(brand);

    const navToggle = document.createElement('button');
    navToggle.className = 'nav-toggle';
    navToggle.type = 'button';
    navToggle.setAttribute('aria-label', 'Toggle navigation');
    navToggle.setAttribute('aria-expanded', 'false');
    navToggle.innerHTML = `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>`;
    navToggle.addEventListener('click', () => {
        const expanded = navToggle.getAttribute('aria-expanded') === 'true';
        navToggle.setAttribute('aria-expanded', String(!expanded));
        nav.classList.toggle('is-open', !expanded);
    });

    const nav = document.createElement('nav');
    nav.className = 'app-nav';
    nav.setAttribute('aria-label', 'Primary');
    NAV_ITEMS.forEach((item) => {
        const link = document.createElement('a');
        link.href = item.href;
        link.textContent = item.label;
        if (item.id === page) {
            link.setAttribute('aria-current', 'page');
        }
        nav.append(link);
    });

    const right = document.createElement('div');
    right.className = 'app-header-right';
    right.append(navToggle);

    const account = document.createElement('div');
    account.className = 'account';

    const accountBtn = document.createElement('button');
    accountBtn.type = 'button';
    accountBtn.className = 'account-btn';
    accountBtn.setAttribute('aria-haspopup', 'menu');
    accountBtn.setAttribute('aria-expanded', 'false');
    accountBtn.innerHTML = `<span class="avatar">${initialsFrom(user?.name, user?.email)}</span>
        <span class="account-name">${user?.name ? escapeHtml(user.name) : 'Account'}</span>`;
    account.append(accountBtn);

    const menu = document.createElement('div');
    menu.className = 'account-menu';
    menu.setAttribute('role', 'menu');
    menu.hidden = true;

    const emailRow = document.createElement('div');
    emailRow.className = 'account-email';
    emailRow.textContent = user?.email || 'Signed in';
    menu.append(emailRow);

    const settingsLink = document.createElement('a');
    settingsLink.href = 'settings.html';
    settingsLink.textContent = 'Profile & settings';
    settingsLink.setAttribute('role', 'menuitem');
    menu.append(settingsLink);

    const passwordLink = document.createElement('a');
    passwordLink.href = 'change-password.html';
    passwordLink.textContent = 'Change password';
    passwordLink.setAttribute('role', 'menuitem');
    menu.append(passwordLink);

    const logoutBtn = document.createElement('button');
    logoutBtn.type = 'button';
    logoutBtn.textContent = 'Sign out';
    logoutBtn.setAttribute('role', 'menuitem');
    logoutBtn.addEventListener('click', () => logout(logoutBtn));
    menu.append(logoutBtn);

    account.append(menu);

    const closeMenu = () => {
        menu.hidden = true;
        accountBtn.setAttribute('aria-expanded', 'false');
    };

    accountBtn.addEventListener('click', (event) => {
        event.stopPropagation();
        const open = menu.hidden;
        menu.hidden = !open;
        accountBtn.setAttribute('aria-expanded', String(open));
    });

    document.addEventListener('click', (event) => {
        if (!account.contains(event.target)) {
            closeMenu();
        }
    });

    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
            closeMenu();
        }
    });

    right.append(account);
    header.append(nav, right);

    document.body.prepend(header);
    return header;
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    }[char]));
}

if (document.body) {
    buildShell();
} else {
    document.addEventListener('DOMContentLoaded', buildShell, { once: true });
}
