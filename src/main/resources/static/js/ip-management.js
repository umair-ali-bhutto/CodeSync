
// ── state ───────────────────────────────────────────────────────
let _pendingFormId = null;
let _pendingReasonId = null;

// ── helpers ─────────────────────────────────────────────────────
function closeModal(id) {
    document.getElementById(id).classList.remove('active');
    _pendingFormId = null;
    _pendingReasonId = null;
}

// close on backdrop click
document.querySelectorAll('.modal-overlay').forEach(el => {
    el.addEventListener('click', e => { if (e.target === el) closeModal(el.id); });
});

// ── BLOCK ────────────────────────────────────────────────────────
function openBlock(btn) {
    const ip = btn.dataset.ip;
    const formId = btn.dataset.form;
    const reasonId = btn.dataset.reason;

    _pendingFormId = formId;
    _pendingReasonId = reasonId;

    document.getElementById('blockModalIp').textContent = ip;
    document.getElementById('blockReasonInput').value = '';
    document.getElementById('blockModal').classList.add('active');
    document.getElementById('blockReasonInput').focus();
}

document.getElementById('blockModalConfirm').addEventListener('click', () => {
    if (!_pendingFormId) return;
    const reason = document.getElementById('blockReasonInput').value.trim();
    if (_pendingReasonId) {
        document.getElementById(_pendingReasonId).value = reason;
    }
    document.getElementById(_pendingFormId).submit();
    closeModal('blockModal');
});

// submit on Enter inside reason input
document.getElementById('blockReasonInput').addEventListener('keydown', e => {
    if (e.key === 'Enter') document.getElementById('blockModalConfirm').click();
});

// ── UNBLOCK ──────────────────────────────────────────────────────
function openUnblock(btn) {
    const ip = btn.dataset.ip;
    const formId = btn.dataset.form;

    _pendingFormId = formId;

    document.getElementById('unblockModalIp').textContent = ip;
    document.getElementById('unblockModal').classList.add('active');
}

document.getElementById('unblockModalConfirm').addEventListener('click', () => {
    if (!_pendingFormId) return;
    document.getElementById(_pendingFormId).submit();
    closeModal('unblockModal');
});


// ── EDIT CLIENT ──────────────────────────────────────────────────
function openEdit(btn) {
    document.getElementById('editModalIp').textContent = btn.dataset.ip;
    document.getElementById('editIpInput').value = btn.dataset.ip;
    document.getElementById('editNameInput').value = btn.dataset.name || '';
    document.getElementById('editModal').classList.add('active');
    document.getElementById('editNameInput').focus();
}

