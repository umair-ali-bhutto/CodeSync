
/* ── Sidebar toggle (mobile) ── */
function toggleSidebar() {
    const sidebar = document.getElementById('sidebar');
    const overlay = document.getElementById('overlay');
    const hamburger = document.getElementById('hamburger');
    sidebar.classList.toggle('open');
    overlay.classList.toggle('show');
    hamburger.classList.toggle('open');
}

function closeSidebar() {
    document.getElementById('sidebar').classList.remove('open');
    document.getElementById('overlay').classList.remove('show');
    document.getElementById('hamburger').classList.remove('open');
}

/* ── Category filter ── */
function filterCat(catKey, el) {
    document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
    if (el) el.classList.add('active');

    document.querySelectorAll('.cat-section').forEach(sec => {
        if (catKey === 'all') {
            sec.classList.remove('hidden');
        } else {
            sec.id === 'sec-' + catKey
                ? sec.classList.remove('hidden')
                : sec.classList.add('hidden');
        }
    });

    if (window.innerWidth <= 900) closeSidebar();
}

/* ── Search ── */
const searchBox = document.getElementById('searchBox');
const resultCount = document.getElementById('resultCount');

searchBox.addEventListener('input', function () {
    const q = this.value.toLowerCase().trim();
    let visible = 0;

    document.querySelectorAll('.card').forEach(card => {
        const name = (card.getAttribute('data-name') || '').toLowerCase();
        const show = !q || name.includes(q);
        card.classList.toggle('hidden', !show);
        if (show) visible++;
    });

    /* show/hide empty states per section */
    document.querySelectorAll('.cat-section').forEach(sec => {
        const hasVisible = [...sec.querySelectorAll('.card')].some(c => !c.classList.contains('hidden'));
        let empty = sec.querySelector('.empty-state');
        if (empty) empty.style.display = hasVisible ? 'none' : 'block';
    });

    resultCount.textContent = q ? visible + ' result' + (visible !== 1 ? 's' : '') : '';
});

/* ── Auto-refresh after 60s ── */
setTimeout(() => location.reload(), 60000);