






/* Values rendered by Thymeleaf are supplied through CODE_SYNC_CONFIG in the template. */
const shareKey = CODE_SYNC_CONFIG.shareKey;
const apiUrl = CODE_SYNC_CONFIG.apiUrl;
const fileApiUrl = CODE_SYNC_CONFIG.fileApiUrl;
const AUTO_RELOAD_CFG = CODE_SYNC_CONFIG.autoReload;
const MAX_FILE_BYTES = CODE_SYNC_CONFIG.maxFileBytes;
const MAX_FILES_PER_SHARE = CODE_SYNC_CONFIG.maxFilesPerShare;
const MAX_QUEUE_SIZE = CODE_SYNC_CONFIG.maxQueueSize;
const TAB_KEY = 'codesync-tab-' + shareKey;



console.log(
    `%c ✨ SYSTEM INITIALIZED WELCOME BRO %c Codsync Version ${CODE_SYNC_CONFIG.versionDate} --- ${CODE_SYNC_CONFIG.version} `,
    "color: #fff; background: #1e1e2f; padding: 8px 12px; font-size: 14px; font-weight: bold; border-left: 5px solid #7289da; border-radius: 4px;",
    "color: #fff; background: #2f3136; padding: 8px 12px; font-size: 14px; border-radius: 4px;"
);




/* ======================================================
    AUTO RELOAD ON REPEATED ERRORS  (configurable)
   ====================================================== */
const AUTO_RELOAD = {
    enabled: AUTO_RELOAD_CFG.enabled,
    maxConsecutiveErrors: AUTO_RELOAD_CFG.maxConsecutiveErrors,
    countdownSeconds: AUTO_RELOAD_CFG.countdownSeconds,
    maxReloads: AUTO_RELOAD_CFG.maxReloads,
    reloadWindowMs: AUTO_RELOAD_CFG.reloadWindowMs,
    requireReachable: AUTO_RELOAD_CFG.requireReachable,
    countsAsError: s => (s < 200 || s >= 300) && !AUTO_RELOAD_CFG.ignoredStatuses.includes(s)
};


const AUTO_RELOAD_LOG_KEY = 'codesync-autoreload-log-' + shareKey;
const DRAFT_KEY = 'codesync-draft-' + shareKey;

let autoReloadErrors = 0;
let autoReloadPending = false;
let autoReloadTimer = null;
let autoReloadBanner = null;

function trackAutoReload(statusOrError) {
    if (!AUTO_RELOAD.enabled) return;
    const status = (typeof statusOrError === 'number') ? statusOrError : 0;

    if (status >= 200 && status < 300) {          // success → reset
        autoReloadErrors = 0;
        if (autoReloadPending) cancelAutoReload();
        return;
    }
    if (!AUTO_RELOAD.countsAsError(status)) return; // user-type error → ignore

    autoReloadErrors++;
    if (autoReloadErrors >= AUTO_RELOAD.maxConsecutiveErrors && !autoReloadPending) {
        startAutoReload();
    }
}


function reloadAllowedByGuard() {
    const now = Date.now();
    let log = [];
    try { log = JSON.parse(sessionStorage.getItem(AUTO_RELOAD_LOG_KEY) || '[]'); } catch { }
    log = log.filter(t => now - t < AUTO_RELOAD.reloadWindowMs);
    sessionStorage.setItem(AUTO_RELOAD_LOG_KEY, JSON.stringify(log));
    return log.length < AUTO_RELOAD.maxReloads;
}

// RUN THIS BELOW TO CHECK TIMESTAMPS OF RELOAD
// JSON.parse(sessionStorage.getItem(AUTO_RELOAD_LOG_KEY) || '[]')
// .map(t => new Date(t).toLocaleString())

function recordReload() {
    let log = [];
    try { log = JSON.parse(sessionStorage.getItem(AUTO_RELOAD_LOG_KEY) || '[]'); } catch { }
    log.push(Date.now());
    sessionStorage.setItem(AUTO_RELOAD_LOG_KEY, JSON.stringify(log));
}

function showReloadBanner(secondsLeft) {
    if (!autoReloadBanner) {
        autoReloadBanner = document.createElement('div');
        autoReloadBanner.style.cssText =
            'position:fixed;bottom:16px;left:50%;transform:translateX(-50%);z-index:1100;' +
            'background:var(--danger);color:#fff;padding:10px 16px;border-radius:8px;' +
            'box-shadow:0 4px 16px rgba(0,0,0,.35);font-size:.88rem;display:flex;gap:12px;align-items:center;';
        autoReloadBanner.innerHTML =
            '<span id="ar-text"></span><button class="btn secondary" id="ar-cancel">Cancel</button>';
        document.body.appendChild(autoReloadBanner);
        autoReloadBanner.querySelector('#ar-cancel').addEventListener('click', () => {
            cancelAutoReload();
            autoReloadErrors = 0;
        });
    }
    autoReloadBanner.querySelector('#ar-text').textContent =
        `⚠️ Too many errors. Reloading in ${secondsLeft}s…`;
}

function cancelAutoReload() {
    clearInterval(autoReloadTimer);
    autoReloadPending = false;
    if (autoReloadBanner) { autoReloadBanner.remove(); autoReloadBanner = null; }
}

function startAutoReload() {
    if (typeof uploadQueue !== 'undefined' && uploadQueue.some(i => i.status === 'uploading')) return; // don't kill uploads
    if (!reloadAllowedByGuard()) {
        console.warn('Auto-reload skipped: reload limit reached.');
        autoReloadErrors = 0;
        return;
    }
    autoReloadPending = true;
    let left = AUTO_RELOAD.countdownSeconds;
    showReloadBanner(left);
    autoReloadTimer = setInterval(() => {
        left--;
        if (left > 0) { showReloadBanner(left); return; }
        clearInterval(autoReloadTimer);
        doAutoReload();
    }, 1000);
}

async function doAutoReload() {
    if (AUTO_RELOAD.requireReachable) {
        try {
            await fetch(window.location.href, { method: 'HEAD', cache: 'no-store' });
        } catch {
            // server fully unreachable → reloading would show a browser error page
            cancelAutoReload();   // next error will restart the countdown
            return;
        }
    }
    // keep unsaved text
    try {
        if (editor.value !== lastContent) sessionStorage.setItem(DRAFT_KEY, editor.value);
    } catch { }
    recordReload();
    window.location.reload();
}



/* ---- Dark/Light Mode ---- */
const toggle = document.getElementById('mode-toggle');
const darkLogo = document.getElementById('logo-dark');
const lightLogo = document.getElementById('logo-light');

const THEME_KEY = 'codesync-theme-' + shareKey;

/* ---- Highlight.js theme families (light/dark pair per name) ---- */
const HLJS_THEMES = {
    github: { label: 'GitHub', light: 'github.min.css', dark: 'github-dark.min.css' },
    atomOne: { label: 'Atom One', light: 'atom-one-light.min.css', dark: 'atom-one-dark.min.css' },
    vs: { label: 'VS', light: 'vs.min.css', dark: 'vs2015.min.css' },
    stackoverflow: { label: 'Stack Overflow', light: 'stackoverflow-light.min.css', dark: 'stackoverflow-dark.min.css' },
    tokyoNight: { label: 'Tokyo Night', light: 'tokyo-night-light.min.css', dark: 'tokyo-night-dark.min.css' },
    gradient: { label: 'Gradient', light: 'gradient-light.min.css', dark: 'gradient-dark.min.css' },
    panda: { label: 'Panda', light: 'panda-syntax-light.min.css', dark: 'panda-syntax-dark.min.css' },
    solarized: { label: 'Solarized', light: 'base16/solarized-light.min.css', dark: 'base16/solarized-dark.min.css' },

    monokai: { label: 'Monokai', light: 'monokai-sublime.min.css', dark: 'monokai.min.css' },
    nord: { label: 'Nord', light: 'nord.min.css', dark: 'nord.min.css' }, // Nord uses a single mid-tone aesthetic
    gruvbox: { label: 'Gruvbox', light: 'base16/gruvbox-light-hard.min.css', dark: 'base16/gruvbox-dark-hard.min.css' },
    nightOwl: { label: 'Night Owl', light: 'nnfx-light.min.css', dark: 'night-owl.min.css' },
    intellij: { label: 'IntelliJ / JetBrains', light: 'intellij-light.min.css', dark: 'androidstudio.min.css' },
    a11y: { label: 'A11y (Accessible)', light: 'a11y-light.min.css', dark: 'a11y-dark.min.css' },
    kimbie: { label: 'Kimbie', light: 'kimbie-light.min.css', dark: 'kimbie-dark.min.css' },
    paraiso: { label: 'Paraiso', light: 'paraiso-light.min.css', dark: 'paraiso-dark.min.css' }
};
const DEFAULT_HLJS_THEME = 'github'; // <-- configurable default
const HLJS_THEME_KEY = 'codesync-hljs-theme-' + shareKey;
const HLJS_CDN_BASE = 'https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/';

const hljsThemeSelect = document.getElementById('hljs-theme-select');
Object.entries(HLJS_THEMES).forEach(([key, cfg]) => {
    const opt = document.createElement('option');
    opt.value = key;
    opt.textContent = cfg.label;
    hljsThemeSelect.appendChild(opt);
});

/* ---- Highlight.js enable/disable state (declared early — switchTab needs it) ---- */
const HLJS_ENABLED_GLOBAL_KEY = 'codesync-hljs-enabled-global';
const HLJS_ENABLED_SHARE_KEY = 'codesync-hljs-enabled-' + shareKey;

function getSavedHljsEnabled() {
    const shareVal = localStorage.getItem(HLJS_ENABLED_SHARE_KEY);
    if (shareVal !== null) return shareVal === 'true';
    const globalVal = localStorage.getItem(HLJS_ENABLED_GLOBAL_KEY);
    if (globalVal !== null) return globalVal === 'true';
    return false; // default: disabled
}

let hljsEnabled = getSavedHljsEnabled();

let currentHljsTheme = localStorage.getItem(HLJS_THEME_KEY) || DEFAULT_HLJS_THEME;
if (!HLJS_THEMES[currentHljsTheme]) currentHljsTheme = DEFAULT_HLJS_THEME;
hljsThemeSelect.value = currentHljsTheme;

hljsThemeSelect.addEventListener('change', () => {
    currentHljsTheme = hljsThemeSelect.value;
    localStorage.setItem(HLJS_THEME_KEY, currentHljsTheme);
    applyTheme(document.body.classList.contains('dark'));
});

function applyTheme(isDark) {
    document.body.classList.toggle('dark', isDark);
    toggle.classList.toggle('active', isDark);
    darkLogo.style.display = isDark ? 'none' : 'block';
    lightLogo.style.display = isDark ? 'block' : 'none';

    const family = HLJS_THEMES[currentHljsTheme] || HLJS_THEMES[DEFAULT_HLJS_THEME];
    document.getElementById('hljs-theme').href = HLJS_CDN_BASE + (isDark ? family.dark : family.light);
}

//         ### More nice light/dark theme pairings
// | Light | Dark |
// |---|---|
// | `github.min.css` | `github-dark.min.css` |
// | `atom-one-light.min.css` | `atom-one-dark.min.css` |
// | `vs.min.css` | `vs2015.min.css` |
// | `xcode.min.css` | `dracula.min.css` |
// | `stackoverflow-light.min.css` | `stackoverflow-dark.min.css` |
// | `tokyo-night-light.min.css` | `tokyo-night-dark.min.css` |
// | `nord.min.css` (works ok both ways, muted) | `nord.min.css` |
// | `gradient-light.min.css` | `gradient-dark.min.css` |
// | `panda-syntax-light.min.css` | `panda-syntax-dark.min.css` |
// | `base16/solarized-light.min.css` | `base16/solarized-dark.min.css` |


// Load saved preference on page open
const savedTheme = localStorage.getItem(THEME_KEY);
applyTheme(savedTheme === 'dark');

toggle.addEventListener('click', () => {
    const isDark = !document.body.classList.contains('dark');
    applyTheme(isDark);
    localStorage.setItem(THEME_KEY, isDark ? 'dark' : 'light');
});

/* ---- Loader ---- */
const loader = document.getElementById('loader');
function showLoader() { loader.classList.add('active'); }
function hideLoader() { loader.classList.remove('active'); }

/* ---- Toast ---- */
const TOAST_MAX = 5; // max toasts visible at once

function showToast(message, type = 'success', icon = '✅', duration = 2500) {
    const container = document.getElementById('toast-container');

    // Limit stack size: dismiss the oldest if too many
    const existing = container.querySelectorAll('.toast:not(.hide)');
    if (existing.length >= TOAST_MAX) existing[0].dispatchEvent(new Event('dismiss'));

    const toast = document.createElement('div');
    toast.className = 'toast ' + type;
    toast.style.setProperty('--toast-duration', duration + 'ms');
    toast.setAttribute('role', 'status');
    toast.innerHTML = `
        <div class="toast-icon">${icon}</div>
        <div class="toast-body">${escHtml(message)}</div>
        <span class="toast-close">✕</span>
        <div class="toast-progress"><div class="toast-progress-bar"></div></div>
    `;
    container.appendChild(toast);

    let remaining = duration;
    let startedAt = Date.now();
    let timer = null;
    let dismissed = false;

    function dismiss() {
        if (dismissed) return;
        dismissed = true;
        clearTimeout(timer);
        toast.classList.add('hide');
        setTimeout(() => toast.remove(), 350);
    }

    function startTimer() {
        startedAt = Date.now();
        timer = setTimeout(dismiss, remaining);
    }

    function pauseTimer() {
        clearTimeout(timer);
        remaining -= Date.now() - startedAt;
    }

    toast.addEventListener('mouseenter', pauseTimer);
    toast.addEventListener('mouseleave', startTimer);
    toast.addEventListener('click', dismiss);        // click to dismiss
    toast.addEventListener('dismiss', dismiss);      // programmatic dismiss

    startTimer();
}

/* ---- Info dialog (replaces alert) ---- */

let infoResolver = null;

function showInfoDialog(icon, title, message) {
    return new Promise(resolve => {
        infoResolver = resolve;
        document.getElementById('info-icon').textContent = icon;
        document.getElementById('info-title').textContent = title;
        document.getElementById('info-message').textContent = message;
        document.getElementById('info-overlay').classList.add('visible');
    });
}

function closeInfoDialog() {
    document.getElementById('info-overlay').classList.remove('visible');
    if (infoResolver) { infoResolver(); infoResolver = null; }
}

document.getElementById('info-close').addEventListener('click', closeInfoDialog);
document.getElementById('info-ok-btn').addEventListener('click', closeInfoDialog);
document.getElementById('info-overlay').addEventListener('click', e => {
    if (e.target.id === 'info-overlay') closeInfoDialog();
});



/* ---- Confirm dialog (replaces confirm) ---- */
let confirmResolver = null;

function showConfirmDialog({
    icon = '❓',
    title = 'Are you sure?',
    message = '',
    confirmText = 'Yes',
    cancelText = 'Cancel',
    danger = true
} = {}) {
    return new Promise(resolve => {
        confirmResolver = resolve;
        document.getElementById('confirm-icon').textContent = icon;
        document.getElementById('confirm-title').textContent = title;
        document.getElementById('confirm-message').textContent = message;
        const okBtn = document.getElementById('confirm-ok-btn');
        okBtn.textContent = confirmText;
        okBtn.className = 'btn' + (danger ? ' danger' : '');
        document.getElementById('confirm-cancel-btn').textContent = cancelText;
        document.getElementById('confirm-overlay').classList.add('visible');
    });
}

function closeConfirmDialog(result) {
    document.getElementById('confirm-overlay').classList.remove('visible');
    if (confirmResolver) {
        confirmResolver(result);
        confirmResolver = null;
    }
}

document.getElementById('confirm-ok-btn').addEventListener('click', () => closeConfirmDialog(true));
document.getElementById('confirm-cancel-btn').addEventListener('click', () => closeConfirmDialog(false));
document.getElementById('confirm-close').addEventListener('click', () => closeConfirmDialog(false));
document.getElementById('confirm-overlay').addEventListener('click', e => {
    if (e.target.id === 'confirm-overlay') closeConfirmDialog(false);
});


/* ---- Status Display ---- */
const statusDisplay = document.getElementById("status-display");
const statusConfig = {
    200: { text: "✅ Success", color: "var(--success)" },
    400: { text: "⚠️ Bad Request", color: "var(--warn)" },
    401: { text: "⚠️ Unauthorized", color: "var(--warn)" },
    403: { text: "🚫 Forbidden", color: "var(--danger)" },
    404: { text: "❌ Not Found", color: "var(--danger)" },
    409: { text: "🚫 File Limit Reached", color: "var(--warn)" },
    413: { text: "⚠️ File Too Large", color: "var(--warn)" },
    429: { text: "❌ Too Many Requests", color: "var(--danger)" },
    500: { text: "💥 Server Error", color: "darkred" },
    507: { text: "💾 Server Storage Full", color: "var(--danger)" },
    0: { text: "❌ Network Error", color: "var(--danger)" }
};
let statusTimer = null;
function showStatus(statusOrError, context = "") {
    console.log('statusOrError: ', statusOrError);

    if (statusTimer) clearTimeout(statusTimer);
    const cfg = (typeof statusOrError === "number")
        ? (statusConfig[statusOrError] || { text: `HTTP ${statusOrError}`, color: "#4a90e2" })
        : statusConfig[0];
    const prefix = { load: "📥 ", poll: "🔄 ", save: "💾 ", clear: "🧹 ", upload: "📤 ", delete: "🗑️ " }[context] || "";
    statusDisplay.textContent = prefix + cfg.text;
    statusDisplay.style.color = cfg.color;
    statusTimer = setTimeout(() => { statusDisplay.textContent = ""; }, 1200);

    // Track system status based on API response
    const isSuccess = (typeof statusOrError === "number") && (statusOrError >= 200 && statusOrError < 300);
    updateSystemStatus(isSuccess);
    trackAutoReload(statusOrError);
}

/* ======================================================
   TAB SWITCHING
   ====================================================== */
let currentTab = 'code';

function switchTab(tab) {
    currentTab = tab;
    try { localStorage.setItem(TAB_KEY, tab); } catch { }
    document.getElementById('panel-code').classList.toggle('active', tab === 'code');
    document.getElementById('panel-files').classList.toggle('active', tab === 'files');
    document.getElementById('tab-code').classList.toggle('active', tab === 'code');
    document.getElementById('tab-files').classList.toggle('active', tab === 'files');

    // Toggle header buttons
    const codeButtons = ['clear-text-btn', 'share-btn', 'copy-text-btn'];
    const fileButtons = ['dl-all-btn', 'refresh-files-btn', 'del-all-btn'];
    codeButtons.forEach(id => document.getElementById(id).style.display = (tab === 'code' ? '' : 'none'));
    fileButtons.forEach(id => document.getElementById(id).style.display = (tab === 'files' ? '' : 'none'));
    document.getElementById('hljs-theme-select').style.display = (tab === 'code' && hljsEnabled) ? '' : 'none';

    if (tab === 'files') loadFileList();
}
// init: restore last used tab for this share key (default: code)
let savedTab = 'code';
try { savedTab = localStorage.getItem(TAB_KEY) || 'code'; } catch { }
if (savedTab !== 'code' && savedTab !== 'files') savedTab = 'code';
switchTab(savedTab);

/* ======================================================
   CODE TAB — existing logic (unchanged)
   ====================================================== */
const editor = document.getElementById("editor");
let debounceTimer, lastContent = "", userTyping = false;

/* ---- Highlight overlay engine (no virtualization) ---- */
const hlLayer = document.getElementById("highlight-layer");
const hlCode = document.getElementById("highlight-code");

function highlightSync(text) {
    if (!hljsEnabled) return; // NEW — skip entirely when the user has it off
    // trailing newline fix so line counts stay aligned with the textarea
    hlCode.textContent = text.endsWith('\n') ? text + ' ' : text;
    hlCode.className = 'hljs';
    delete hlCode.dataset.highlighted;
    hljs.highlightElement(hlCode);
    hlLayer.scrollTop = editor.scrollTop;
    hlLayer.scrollLeft = editor.scrollLeft;
}

// keep overlay scroll in sync while typing/scrolling
editor.addEventListener('scroll', () => {
    hlLayer.scrollTop = editor.scrollTop;
    hlLayer.scrollLeft = editor.scrollLeft;
});

// keep overlay size in sync if the textarea is resized (it has resize:vertical)
const editorResizeObs = new ResizeObserver(() => {
    hlLayer.style.height = editor.offsetHeight + 'px';
    hlLayer.style.width = editor.offsetWidth + 'px';
});
editorResizeObs.observe(editor);

/* ---- Highlight.js enable/disable toggle + warning dialog ---- */
const editorWrap = document.getElementById('editor-wrap');
const hljsEnableToggle = document.getElementById('hljs-enable-toggle');
const hljsWarningOverlay = document.getElementById('hljs-warning-overlay');

function setHljsEnabled(enabled) {
    hljsEnabled = enabled;
    hljsEnableToggle.classList.toggle('active', enabled);
    editorWrap.classList.toggle('hljs-disabled', !enabled);
    hljsThemeSelect.style.display = (enabled && currentTab === 'code') ? '' : 'none';
    if (enabled) highlightSync(editor.value);
}

setHljsEnabled(hljsEnabled); // apply saved/default state now that the editor exists

hljsEnableToggle.addEventListener('click', () => {
    if (hljsEnabled) {
        // turning OFF — clear any stored preference (share-specific AND global)
        // so old values don't linger and silently re-enable highlighting later
        localStorage.removeItem(HLJS_ENABLED_SHARE_KEY);
        localStorage.removeItem(HLJS_ENABLED_GLOBAL_KEY);
        setHljsEnabled(false);
    } else {
        // turning ON — must pick a scope first, toggle stays off until then
        hljsWarningOverlay.classList.add('visible');
    }
});

document.getElementById('hljs-scope-share').addEventListener('click', () => {
    localStorage.setItem(HLJS_ENABLED_SHARE_KEY, 'true');
    hljsWarningOverlay.classList.remove('visible');
    setHljsEnabled(true);
});

document.getElementById('hljs-scope-global').addEventListener('click', () => {
    localStorage.setItem(HLJS_ENABLED_GLOBAL_KEY, 'true');
    localStorage.removeItem(HLJS_ENABLED_SHARE_KEY); // let this share follow the new global default
    hljsWarningOverlay.classList.remove('visible');
    setHljsEnabled(true);
});

function closeHljsWarning() {
    hljsWarningOverlay.classList.remove('visible'); // no scope chosen → stays disabled
}
document.getElementById('hljs-warning-close').addEventListener('click', closeHljsWarning);
document.getElementById('hljs-warning-cancel').addEventListener('click', closeHljsWarning);
hljsWarningOverlay.addEventListener('click', (e) => {
    if (e.target === hljsWarningOverlay) closeHljsWarning();
});


showLoader();
fetch(apiUrl)
    .then(r => { showStatus(r.status, "load"); return r.text(); })
    // .then(t => { editor.value = t; lastContent = t; highlightSync(t); })
    .then(t => {
        const draft = sessionStorage.getItem(DRAFT_KEY);
        if (draft !== null) {
            sessionStorage.removeItem(DRAFT_KEY);
            if (draft !== t) {                       // restore unsaved text and re-save it
                editor.value = draft; lastContent = t;
                highlightSync(draft);
                editor.dispatchEvent(new Event('input'));
                return;
            }
        }
        editor.value = t; lastContent = t; highlightSync(t);
    })

    .catch(() => showStatus(0, "load"))
    .finally(hideLoader);

editor.addEventListener("input", function () {
    userTyping = true;
    highlightSync(editor.value);
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
        showLoader();
        fetch(apiUrl, { method: "POST", headers: { "Content-Type": "text/plain" }, body: editor.value })
            .then(r => showStatus(r.status, "save"))
            .then(() => { lastContent = editor.value; })
            .catch(() => showStatus(0, "save"))
            .finally(() => { userTyping = false; hideLoader(); });
    }, 1000);
});

/* Polling */
let pollInProgress = false, pollTimer = null;
function poll() {
    if (currentTab !== 'code') { pollTimer = setTimeout(poll, 3000); return; }
    if (userTyping || pollInProgress) { pollTimer = setTimeout(poll, 3000); return; }
    pollInProgress = true; showLoader();
    fetch(apiUrl)
        .then(r => { showStatus(r.status, "poll"); if (!r.ok) throw new Error(r.status); return r.text(); })
        .then(t => { if (t !== lastContent) { editor.value = t; lastContent = t; highlightSync(t); } })
        .catch(() => showStatus(0, "poll"))
        .finally(() => { pollInProgress = false; hideLoader(); pollTimer = setTimeout(poll, 3000); });
}
poll();

/* Share URL */
const shareBtn = document.getElementById("share-btn");
const shareUrl = window.location.origin + CODE_SYNC_CONFIG.contextPath + "/share/" + shareKey;
function copyTextFallback(text) {
    const t = document.createElement("textarea");
    t.value = text; t.style.cssText = "position:fixed;opacity:0;";
    document.body.appendChild(t); t.select(); document.execCommand("copy"); document.body.removeChild(t);
}


shareBtn.addEventListener("click", async () => {
    try {
        if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(shareUrl);
        else copyTextFallback(shareUrl);
        shareBtn.textContent = "✅ Copied!";
        showToast("Share URL copied to clipboard", "success", "🔗");
        setTimeout(() => shareBtn.textContent = "🔗 Share URL", 1500);
    } catch {
        showToast("Copy failed. Share URL: " + shareUrl, "error", "❌");
    }
});


/* Copy Text */
document.getElementById("copy-text-btn").addEventListener("click", async () => {
    if (!editor.value) {
        showInfoDialog("📭", "Nothing to Copy", "The editor is empty. Write something first, then try again.");
        return;
    }
    try {
        if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(editor.value);
        else copyTextFallback(editor.value);
        document.getElementById("copy-text-btn").textContent = "✅ Copied!";
        showToast("Text copied to clipboard", "success", "📋");
        setTimeout(() => document.getElementById("copy-text-btn").textContent = "📋 Copy Text", 1500);
    } catch {
        showToast("Copy failed", "error", "❌");
    }
});

/* Clear */
document.getElementById("clear-text-btn").addEventListener("click", async () => {
    if (!editor.value.trim()) {
        showInfoDialog("🧹", "Nothing to Clear", "The editor is already empty.");
        return;
    }
    const ok = await showConfirmDialog({
        icon: '🧹',
        title: 'Clear All Text?',
        message: 'This will erase everything in the editor for everyone using this link.',
        confirmText: '🧹 Yes, Clear'
    });
    if (!ok) return;

    clearTimeout(pollTimer); pollInProgress = true; showLoader();
    let success = false;
    try {
        const r = await fetch(apiUrl, { method: "POST", headers: { "Content-Type": "text/plain" }, body: "" });
        showStatus(r.status, "clear");
        success = r.ok;
    } catch { showStatus(0, "clear"); }

    if (success) {
        editor.value = ""; lastContent = ""; highlightSync("");
        document.getElementById("clear-text-btn").textContent = "✅ Cleared!";
        setTimeout(() => document.getElementById("clear-text-btn").textContent = "🧹 Clear", 1500);
        showToast("Text cleared successfully", "success", "🧹");
    } else {
        showToast("Failed to clear text", "error", "❌");
    }
    hideLoader(); pollInProgress = false; poll();
});

/* ======================================================
   FILES TAB
   ====================================================== */
let uploadQueue = []; // [{file, id, status}]

let currentFiles = []; // cache of the last loaded file list (used for names and counts)

function shortName(name, max = 100) {
    name = String(name || 'file');
    return name.length > max ? name.slice(0, max - 1) + '…' : name;
}
function countFiles(n) { return n + ' file' + (n === 1 ? '' : 's'); }

/* ---- Utility: file icon by extension ---- */
function fileIcon(name) {
    const ext = (name.split('.').pop() || '').toLowerCase();
    const map = {
        pdf: '📄', jpg: '🖼️', jpeg: '🖼️', png: '🖼️', gif: '🖼️', svg: '🖼️', webp: '🖼️',
        mp4: '🎬', mov: '🎬', avi: '🎬', mkv: '🎬', webm: '🎬',
        mp3: '🎵', wav: '🎵', ogg: '🎵', flac: '🎵',
        zip: '🗜️', rar: '🗜️', '7z': '🗜️', tar: '🗜️', gz: '🗜️',
        js: '📜', ts: '📜', jsx: '📜', tsx: '📜',
        html: '🌐', css: '🎨',
        py: '🐍', java: '☕', class: '☕', cpp: '⚙️', c: '⚙️', cs: '⚙️',
        txt: '📝', md: '📝', csv: '📊', json: '📋', xml: '📋', yaml: '📋', yml: '📋',
        xlsx: '📊', xls: '📊', docx: '📄', doc: '📄', pptx: '📊',
        sh: '🖥️', bat: '🖥️', exe: '💾', dmg: '💾',
        sql: '🗄️'
    };
    return map[ext] || '📎';
}

/* ---- Utility: human-readable size ---- */
function humanSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

/* ---- Utility: unique id ---- */
let _uid = 0;
function uid() { return ++_uid; }

/* ---- Drop Zone ---- */
const dropZone = document.getElementById('drop-zone');
const fileInput = document.getElementById('file-input');

dropZone.addEventListener('dragover', e => { e.preventDefault(); dropZone.classList.add('dragover'); });
dropZone.addEventListener('dragleave', () => dropZone.classList.remove('dragover'));
dropZone.addEventListener('drop', e => {
    e.preventDefault(); dropZone.classList.remove('dragover');
    addFilesToQueue([...e.dataTransfer.files]);
});
fileInput.addEventListener('change', () => {
    addFilesToQueue([...fileInput.files]);
    fileInput.value = '';
});

function addFilesToQueue(files) {
    const remaining = MAX_QUEUE_SIZE - uploadQueue.length;
    if (remaining <= 0) {
        showInfoDialog('📦', 'Queue Is Full', `Maximum ${MAX_QUEUE_SIZE} files at a time. Upload or remove some files first.`);
        return;
    }
    const allowed = files.slice(0, remaining);
    const skipped = files.length - allowed.length;
    if (skipped > 0) showInfoDialog('⚠️', 'Some Files Not Added', `Only ${remaining} slot(s) left in queue. ${skipped} file(s) were not added.`);

    allowed.forEach(file => {
        const id = uid();
        uploadQueue.push({ file, id, status: 'pending' });
        renderQueueItem({ file, id, status: 'pending' });
    });
    if (uploadQueue.length > 0) document.getElementById('upload-queue').classList.add('visible');
}


function renderQueueItem(item) {
    const list = document.getElementById('queue-list');
    const li = document.createElement('li');
    li.className = 'queue-item';
    li.id = 'qi-' + item.id;
    const oversized = item.file.size > MAX_FILE_BYTES;
    const warn = oversized ? '<span class="size-warn">Too large</span>' : '';
    li.innerHTML = `
        <span class="queue-file-icon">${fileIcon(item.file.name)}</span>
        <div class="queue-file-info">
            <div class="queue-file-name">${escHtml(item.file.name)}${warn}</div>
            <div class="queue-file-size">${humanSize(item.file.size)}</div>
            <div class="progress-bar-wrap" id="prog-wrap-${item.id}" style="display:none">
                <div class="progress-bar-fill" id="prog-${item.id}"></div>
            </div>
        </div>
        <span class="queue-file-status" id="qs-${item.id}">${oversized ? '<span style="color:var(--warn)">⚠️ Skip</span>' : '⏳ Ready'}</span>
        <button class="queue-remove-btn" onclick="removeFromQueue(${item.id})" title="Remove">✕</button>
    `;
    list.appendChild(li);
}

function removeFromQueue(id) {
    const item = uploadQueue.find(i => i.id === id);
    uploadQueue = uploadQueue.filter(i => i.id !== id);
    const el = document.getElementById('qi-' + id);
    if (el) el.remove();
    if (uploadQueue.length === 0) document.getElementById('upload-queue').classList.remove('visible');
    if (item) showToast(`Removed ${shortName(item.file.name)} from queue`, 'info', '✖');
}

document.getElementById('clear-queue-btn').addEventListener('click', () => {
    const count = uploadQueue.length;
    uploadQueue = [];
    document.getElementById('queue-list').innerHTML = '';
    document.getElementById('upload-queue').classList.remove('visible');
    if (count) showToast(`Queue cleared (${count} file${count > 1 ? 's' : ''})`, 'info', '🧹');
});

/* ---- Upload All ---- */

document.getElementById('upload-all-btn').addEventListener('click', async () => {
    const pending = uploadQueue.filter(i => i.status === 'pending' && i.file.size <= MAX_FILE_BYTES);
    if (pending.length === 0) {
        await showInfoDialog('📭', 'Nothing to Upload', 'There are no valid files in the queue to upload.');
        return;
    }

    // Check current active file count before uploading
    let activeCount = 0;
    try {
        const r = await fetch(fileApiUrl + '/count');
        if (r.ok) activeCount = await r.json();
    } catch { /* proceed, server will enforce */ }

    const slotsAvailable = MAX_FILES_PER_SHARE - activeCount;
    if (slotsAvailable <= 0) {
        await showInfoDialog('🚫', 'File Limit Reached', `This share has reached the limit of ${MAX_FILES_PER_SHARE} active files. Delete some files to upload more.`);
        return;
    }

    const toUpload = pending.slice(0, slotsAvailable);
    const skipped = pending.length - toUpload.length;
    if (skipped > 0) await showInfoDialog('⚠️', 'Some Files Will Be Skipped', `Only ${slotsAvailable} slot(s) available. ${skipped} file(s) will be skipped.`);

    let okCount = 0, failCount = 0;
    const okNames = [], failNames = [];

    for (const item of toUpload) {
        item.status = 'uploading';
        const statusEl = document.getElementById('qs-' + item.id);
        const progWrap = document.getElementById('prog-wrap-' + item.id);
        const prog = document.getElementById('prog-' + item.id);
        if (statusEl) statusEl.innerHTML = '<span class="uploading">⬆️ Uploading…</span>';
        if (progWrap) progWrap.style.display = 'block';

        try {
            await uploadFileWithProgress(item.file, prog);
            item.status = 'done';
            okCount++;
            okNames.push(item.file.name);
            if (statusEl) statusEl.innerHTML = '<span class="done">✅ Done</span>';
            showStatus(200, 'upload');
        } catch (err) {
            item.status = 'error';
            failCount++;
            failNames.push(item.file.name);
            const is413 = err.message === '413';
            const is409 = err.message === '409';
            const is503 = err.message === '503';
            const is507 = err.message === '507';
            const msg = is413 ? '⚠️ Too Large' : is409 ? '🚫 Limit Reached' : is503 ? '⚠️ Service Unavailable' : is507 ? '🗃⚠️ Server Storage Full Contact Admin' : '❌ Failed';
            if (statusEl) statusEl.innerHTML = `<span class="error">${msg}</span>`;
            showStatus(is413 ? 413 : is409 ? 409 : 0, 'upload');
        }
    }

    // Mark remaining pending as skipped in UI
    pending.slice(slotsAvailable).forEach(item => {
        const statusEl = document.getElementById('qs-' + item.id);
        if (statusEl) statusEl.innerHTML = '<span class="error">⏭️ Skipped</span>';
        item.status = 'skipped';
    });

    if (okCount > 0 && failCount === 0) {
        const label = okCount === 1 ? shortName(okNames[0]) : countFiles(okCount);
        showToast(`${label} uploaded successfully`, 'success', '⬆️');
    } else if (okCount > 0) {
        showToast(`${countFiles(okCount)} uploaded, ${failCount} failed: ${failNames.slice(0, 2).map(n => shortName(n, 50)).join(', ')}${failNames.length > 2 ? '…' : ''}`, 'warn', '⚠️');
    } else if (failCount > 0) {
        const label = failCount === 1 ? shortName(failNames[0]) : countFiles(failCount);
        showToast(`Upload failed: ${label}`, 'error', '❌');
    }


    setTimeout(() => {
        uploadQueue.filter(i => i.status === 'done').forEach(i => {
            const el = document.getElementById('qi-' + i.id);
            if (el) el.remove();
        });
        uploadQueue = uploadQueue.filter(i => i.status !== 'done');
        if (uploadQueue.length === 0) document.getElementById('upload-queue').classList.remove('visible');
        loadFileList();
    }, 1200);
});








function uploadFileWithProgress(file, progEl) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        const fd = new FormData();
        fd.append('file', file);
        xhr.open('POST', fileApiUrl + '/upload');
        xhr.upload.addEventListener('progress', e => {
            if (e.lengthComputable && progEl) {
                progEl.style.width = Math.round((e.loaded / e.total) * 100) + '%';
            }
        });
        xhr.addEventListener('load', () => {
            if (xhr.status >= 200 && xhr.status < 300) resolve();
            else reject(new Error(String(xhr.status)));
        });
        xhr.addEventListener('error', () => reject(new Error('0')));
        xhr.send(fd);
    });
}

/* ---- Load File List ---- */
async function loadFileList() {
    try {
        showLoader();
        const r = await fetch(fileApiUrl + '/list');
        if (!r.ok) { showStatus(r.status, 'load'); return false; }
        const text = await r.text();
        const files = text.trim() ? JSON.parse(text) : [];
        renderFileList(files);
        return true;
    } catch (e) {
        console.error(e);
        showStatus(0, 'load');
        return false;
    } finally {
        hideLoader();
    }
}


function renderFileList(files) {
    currentFiles = files;
    const list = document.getElementById('file-list');
    const badge = document.getElementById('file-count-badge');
    list.innerHTML = '';
    badge.textContent = files.length ? `${files.length} file${files.length > 1 ? 's' : ''}` : '';

    if (!files.length) {
        list.innerHTML = `
                <li class="files-empty">
                    <span class="files-empty-icon">📭</span>
                    No files shared yet. Upload some above!
                </li>`;
        document.getElementById('dl-all-btn').style.display = 'none';
        document.getElementById('del-all-btn').style.display = 'none';
        return;
    }

    document.getElementById('dl-all-btn').style.display = '';
    document.getElementById('del-all-btn').style.display = '';

    files.forEach(f => {
        const li = document.createElement('li');
        const now = Date.now();
        const expiresAt = f.expiresAt ? new Date(f.expiresAt) : null;
        const isExpiring = expiresAt && (expiresAt - now) < 24 * 60 * 60 * 1000; // < 24h left
        const expiryStr = expiresAt ? formatExpiry(expiresAt, now) : '—';
        const expiryHtml = expiresAt
            ? `&nbsp;•&nbsp; ⏳ <span data-expires-at="${expiresAt.toISOString()}" style="color:${!isExpiring ? '#888' : 'var(--danger)'};font-weight:${!isExpiring ? 400 : 700}">${expiryStr}</span>`
            : '';
        li.className = 'file-item';

        const uploader = (f.uploaderName) ? f.uploaderName : (f.uploaderIp || '—');
        const dlCount = f.downloadCount ?? 0;
        const lastDl = f.lastDownloadedByName || f.lastDownloadedByIp || 'Unknown';
        const dlHtml = (f.downloaderCount > 0)
            ? `&nbsp;•&nbsp; 👥 <a href="#" class="downloaders-link" onclick="openDownloaders('${f.fileId}');return false;">${f.downloaderCount} downloader${f.downloaderCount !== 1 ? 's' : ''}</a>
               &nbsp;•&nbsp; 🕒 Last Downloader: ${escHtml(lastDl)} (${formatDate(f.lastDownloadedAt)})`
            : '';

        li.innerHTML = `
                <span class="file-icon">${fileIcon(f.originalName)}</span>
                <div class="file-meta">
                    <div class="file-name" title="${escHtml(f.originalName)}">${escHtml(f.originalName)}</div>
                    <div class="file-info">
                        ${humanSize(f.fileSize)} &nbsp;•&nbsp; 
                        ${formatDate(f.uploadedAt)} &nbsp;•&nbsp; 
                        ⬇️ ${dlCount} download${dlCount !== 1 ? 's' : ''}
                        ${dlHtml}
                        &nbsp;•&nbsp; 👤 Uploader: ${escHtml(uploader)}
                        ${expiryHtml}
                    </div>

                </div>
                <div class="file-actions">
                    <button class="file-dl-btn" onclick="downloadFile('${f.fileId}','${escAttr(f.originalName)}')">⬇️ Download</button>
                    <button class="file-del-btn" onclick="deleteFile('${f.fileId}')" title="Delete">🗑️</button>
                </div>
                `;
        list.appendChild(li);
    });

    if (files.some(f => f.expiresAt)) startExpiryTicker();
}

let expiryTicker = null;
function startExpiryTicker() {
    if (expiryTicker) clearInterval(expiryTicker);
    expiryTicker = setInterval(() => {
        let anyExpired = false;
        document.querySelectorAll('[data-expires-at]').forEach(el => {
            const expiresAt = new Date(el.dataset.expiresAt);
            const diff = expiresAt - Date.now();
            el.textContent = formatExpiry(expiresAt, Date.now());
            el.style.color = diff < 60000 ? 'var(--danger)' : '';
            el.style.fontWeight = diff < 60000 ? '700' : '400';
            if (diff <= 0) anyExpired = true;
        });

        // Refresh list when something expires so it disappears cleanly
        // if (anyExpired) {
        // clearInterval(expiryTicker);
        // expiryTicker = null;
        // loadFileList();
        // }
    }, 1000);
}

function formatDate(isoStr) {
    if (!isoStr) return '';
    try {
        const d = new Date(isoStr);
        return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
            + ' ' + d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch { return isoStr; }
}

function formatExpiry(expiresAt, now) {
    const diffMs = expiresAt - now;
    if (diffMs <= 0) return 'Expired';

    const diffSecs = Math.floor(diffMs / 1000);
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffSecs < 60) return `Expires in ${diffSecs}s`;
    if (diffMins < 60) return `Expires in ${diffMins}m ${diffSecs % 60}s`;
    if (diffHours < 24) return `Expires in ${diffHours}h ${diffMins % 60}m ${diffSecs % 60}s`;
    return `Expires in ${diffDays}d ${diffHours % 24}h ${diffMins % 60}m ${diffSecs % 60}s`;
}

/* ---- Download single file ---- */
function downloadFile(fileId, originalName) {
    const a = document.createElement('a');
    a.href = fileApiUrl + '/download/' + fileId;
    a.download = originalName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    showToast(`Downloading ${shortName(originalName)}`, 'info', '⬇️');
}




/* ---- Download All ---- */
document.getElementById('dl-all-btn').addEventListener('click', async () => {
    const fileCount = currentFiles.length;
    const overlay = document.getElementById('dl-overlay');
    const fillEl = document.getElementById('dl-progress-fill');
    const statusEl = document.getElementById('dl-status');

    overlay.classList.add('visible');
    fillEl.style.width = '0%';
    statusEl.textContent = '🗜️ Requesting ZIP archive...';

    try {
        // Fetch the ZIP file from the new endpoint
        const response = await fetch(fileApiUrl + '/download-all');

        if (!response.ok) {
            if (response.status === 404) throw new Error('No files found to download.');
            if (response.status === 410) throw new Error('All files have expired.');
            throw new Error(`Server returned ${response.status}`);
        }

        // --- 🌟 EXTRACT FILENAME FROM BACKEND HEADERS 🌟 ---
        const disposition = response.headers.get('Content-Disposition');
        let filename = `codesync_${shareKey}.zip`; // Fallback just in case

        if (disposition) {
            // This regex matches filename="something.zip" or filename=something.zip
            const matches = /filename="?([^";]+)"?/.exec(disposition);
            if (matches && matches[1]) {
                filename = matches[1];
            }
        }
        // ---------------------------------------------------

        const contentLength = response.headers.get('X-Total-Uncompressed-Size');
        const total = parseInt(contentLength, 10);
        let loaded = 0;

        const reader = response.body.getReader();
        const chunks = [];

        statusEl.textContent = '⬇️ Downloading ZIP...';

        // Read the stream chunk by chunk to track progress
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            chunks.push(value);
            loaded += value.length;

            // Update progress bar
            if (total && !isNaN(total)) {
                // const percent = Math.round((loaded / total) * 100);
                const percent = Math.min(100, Math.round((loaded / total) * 100));
                fillEl.style.width = percent + '%';
                statusEl.textContent = `⬇️ Downloading ZIP... ${percent}%`;
            } else {
                // If content-length is unknown, simulate progress up to 90%
                let currentWidth = parseFloat(fillEl.style.width) || 0;
                if (currentWidth < 90) {
                    currentWidth += Math.random() * 5;
                }
                fillEl.style.width = currentWidth + '%';
                statusEl.textContent = `⬇️ Downloading ZIP... ${humanSize(loaded)}`;
            }
        }

        // Create a Blob from the downloaded chunks and trigger the save dialog
        const blob = new Blob(chunks, { type: 'application/zip' });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;

        // --- 🌟 USE THE EXTRACTED FILENAME HERE 🌟 ---
        a.download = filename;
        // ---------------------------------------------

        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);

        fillEl.style.width = '100%';
        statusEl.textContent = '✅ ZIP file downloaded!';
        showToast(`${countFiles(fileCount)} downloaded (${humanSize(loaded)}) as ${shortName(filename, 40)}`, 'success', '📦');
        setTimeout(() => overlay.classList.remove('visible'), 1500);

    } catch (error) {
        console.error('Download all failed:', error);
        statusEl.textContent = '❌ ' + (error.message || 'Failed to download ZIP.');
        showToast(error.message || 'Failed to download ZIP', 'error', '❌');
        fillEl.style.width = '0%';
        setTimeout(() => overlay.classList.remove('visible'), 2000);
    }
});


/* ---- Refresh Files ---- */
document.getElementById('refresh-files-btn').addEventListener('click', async () => {
    const ok = await loadFileList();
    if (ok) showToast(`File list refreshed (${countFiles(currentFiles.length)})`, 'info', '🔄');
    else showToast('Failed to refresh files', 'error', '❌');
});

/* ---- Delete all files ---- */

document.getElementById('del-all-btn').addEventListener('click', async () => {
    const count = currentFiles.length;

    const ok = await showConfirmDialog({
        icon: '🗑️',
        title: 'Delete All Files?',
        message: `All ${countFiles(count)} in this share will be permanently deleted. This cannot be undone.`,
        confirmText: `🗑️ Delete ${count > 1 ? 'All ' + count : 'All'}`
    });
    if (!ok) return;

    showLoader();
    try {
        const r = await fetch(fileApiUrl + '/delete-all', { method: 'DELETE' });
        showStatus(r.status, 'delete');
        if (r.ok) showToast(`${countFiles(count)} deleted`, 'success', '🗑️');
        else showToast('Failed to delete files', 'error', '❌');
    } catch (e) {
        console.error(e);
        showStatus(0, 'delete');
        showToast('Failed to delete files', 'error', '❌');
    } finally {
        hideLoader();
        loadFileList();
    }
});

/* ---- Delete file ---- */
async function deleteFile(fileId) {
    const f = currentFiles.find(x => String(x.fileId) === String(fileId));
    const name = f ? f.originalName : 'File';

    const ok = await showConfirmDialog({
        icon: '🗑️',
        title: 'Delete This File?',
        message: `"${shortName(name)}" will be permanently deleted.`,
        confirmText: '🗑️ Delete'
    });
    if (!ok) return;

    showLoader();
    try {
        const r = await fetch(fileApiUrl + '/delete/' + fileId, { method: 'DELETE' });
        showStatus(r.status, 'delete');
        if (r.ok) showToast(`Deleted ${shortName(name)}`, 'success', '🗑️');
        else showToast(`Failed to delete ${shortName(name)}`, 'error', '❌');
        loadFileList();
    } catch {
        showStatus(0, 'delete');
        showToast(`Failed to delete ${shortName(name)}`, 'error', '❌');
    } finally { hideLoader(); }
}


/* ---- Feedback / Report Issue Modal ---- */
const feedbackOverlay = document.getElementById('feedback-overlay');
const feedbackBtn = document.getElementById('feedback-btn');
const feedbackClose = document.getElementById('feedback-close');

feedbackBtn.addEventListener('click', () => {
    feedbackOverlay.classList.add('visible');
});

feedbackClose.addEventListener('click', () => {
    feedbackOverlay.classList.remove('visible');
});

// Close on backdrop click
feedbackOverlay.addEventListener('click', (e) => {
    if (e.target === feedbackOverlay) feedbackOverlay.classList.remove('visible');
});

// Close on Escape key
document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;

    if (feedbackOverlay.classList.contains('visible')) {
        feedbackOverlay.classList.remove('visible');
    }

    if (downloadersOverlay.classList.contains('visible')) {
        closeDownloaders();
    }

    if (document.getElementById('info-overlay').classList.contains('visible')) {
        closeInfoDialog();
    }

    if (document.getElementById('confirm-overlay').classList.contains('visible')) {
        closeConfirmDialog(false);
    }
});





/* ---- Downloaders Modal ---- */
const downloadersOverlay = document.getElementById('downloaders-overlay');

async function openDownloaders(fileId) {
    const list = document.getElementById('downloaders-list');
    const sub = document.getElementById('downloaders-sub');
    list.innerHTML = '';
    sub.textContent = 'Loading…';
    downloadersOverlay.classList.add('visible');
    try {
        const r = await fetch(fileApiUrl + '/downloaders/' + fileId);
        if (!r.ok) { sub.textContent = 'Failed to load (HTTP ' + r.status + ')'; return; }
        const data = await r.json();
        sub.textContent = data.length + ' unique downloader' + (data.length !== 1 ? 's' : '');
        data.forEach(d => {
            const li = document.createElement('li');
            const primary = d.name && d.name.trim() ? d.name : (d.ip || 'Unknown');
            const secondary = (d.name && d.name.trim() && d.ip) ? ` <span class="downloader-ip">${escHtml(d.ip)}</span>` : '';
            li.innerHTML = `👤 ${escHtml(primary)}${secondary}`;
            list.appendChild(li);
        });
    } catch (e) {
        console.error(e);
        sub.textContent = 'Failed to load downloaders.';
    }
}

function closeDownloaders() { downloadersOverlay.classList.remove('visible'); }
document.getElementById('downloaders-close').addEventListener('click', closeDownloaders);
downloadersOverlay.addEventListener('click', e => { if (e.target === downloadersOverlay) closeDownloaders(); });


/* ---- Helpers ---- */
function escHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
function escAttr(str) {
    return String(str).replace(/'/g, "\\'");
}

/* ---- Init file list on load ---- */
// (loaded on tab switch, not on page load to avoid extra requests)





let systemStatus = 'UNKNOWN';
let lastApiErrorTime = 0;
const SYSTEM_OFFLINE_TIMEOUT = 5000;

function updateSystemStatus(isSuccess) {

    const now = Date.now();

    if (isSuccess) {

        if (systemStatus !== 'ONLINE') {

            systemStatus = 'ONLINE';
            logSystemStatus('ONLINE');
        }

        return;
    }

    if (systemStatus === 'ONLINE') {

        lastApiErrorTime = now;

        systemStatus = 'CHECKING';
        logSystemStatus('CHECKING');

        return;
    }

    if (
        systemStatus === 'CHECKING' &&
        (now - lastApiErrorTime) >= SYSTEM_OFFLINE_TIMEOUT
    ) {

        systemStatus = 'OFFLINE';
        logSystemStatus('OFFLINE');
    }
}

function logSystemStatus(status) {

    const time = new Date().toLocaleTimeString();

    if (status === 'ONLINE') {

        console.log(
            "%c🚀 SYSTEM ONLINE\n%c --> Operator: " + shareKey + "\t 🕒 " + time,
            "color: #00ff00; font-size: 40px; font-weight: bold; font-family: monospace; text-shadow: 3px 3px 0 #003300;",
            "color: #888; font-size: 16px; font-family: monospace; font-style: italic;"
        );

    } else if (status === 'CHECKING') {

        console.log(
            "%c⚠️ SYSTEM DEGRADED\n%cConnection issues detected, monitoring...\t 🕒 " + time,
            "color: #ffaa00; font-size: 40px; font-weight: bold; font-family: monospace; text-shadow: 3px 3px 0 #553300;",
            "color: #888; font-size: 16px; font-family: monospace; font-style: italic;"
        );

    } else if (status === 'OFFLINE') {

        console.log(
            "%c💀 SYSTEM OFFLINE\n%cCannot reach server, check your connection!\t 🕒 " + time,
            "color: #ff0000; font-size: 40px; font-weight: bold; font-family: monospace; text-shadow: 3px 3px 0 #330000;",
            "color: #888; font-size: 16px; font-family: monospace; font-style: italic;"
        );
    }
}




