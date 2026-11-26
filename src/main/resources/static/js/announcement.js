// Config is injected by the fragment as window.CODESYNC_ANNOUNCEMENT
(function () {
    'use strict';

    const cfg = window.CODESYNC_ANNOUNCEMENT || {};
    const NOTICE_KEY = cfg.noticeKey || 'codesync-upgrade-notice';
    const REQUIRED_PHRASE = (cfg.requiredPhrase || 'I WILL BACK UP MY DATA').toUpperCase();

    const overlay = document.getElementById('codesync-upgrade-overlay');
    if (!overlay) {
        return;
    }

    const content = document.getElementById('upgrade-content');
    const progress = document.getElementById('upgrade-progress-fill');
    const challengeInput = document.getElementById('upgrade-challenge-input');
    const challengeStatus = document.getElementById('upgrade-challenge-status');
    const agreement = document.getElementById('upgrade-agreement-checkbox');
    const agreementRow = document.getElementById('upgrade-agreement-row');
    const acceptButton = document.getElementById('upgrade-accept-btn');

    let hasReachedBottom = false;

    // Show announcement only once per browser for this version.
    try {
        if (localStorage.getItem(NOTICE_KEY) === 'accepted') {
            return;
        }
    } catch (e) {
        console.warn('CodeSync announcement: localStorage unavailable.');
    }

    overlay.classList.add('visible');
    document.body.style.overflow = 'hidden';

    function isChallengePassed() {
        return challengeInput.value.trim().toUpperCase() === REQUIRED_PHRASE;
    }

    function updateReadingProgress() {
        const maxScroll = content.scrollHeight - content.clientHeight;
        const percentage = maxScroll <= 0
            ? 100
            : Math.min(100, (content.scrollTop / maxScroll) * 100);

        progress.style.width = percentage + '%';

        if (percentage >= 99) {
            hasReachedBottom = true;
            agreement.disabled = false;
            updateAcceptButton();
        }
    }

    content.addEventListener('scroll', updateReadingProgress);
    requestAnimationFrame(updateReadingProgress);
    window.addEventListener('resize', function () {
        requestAnimationFrame(updateReadingProgress);
    });

    function validateChallenge() {
        if (isChallengePassed()) {
            challengeStatus.textContent = '✅ Correct! You understand the backup instruction.';
            challengeStatus.style.color = '#27ae60';
        } else if (challengeInput.value.length > 0) {
            challengeStatus.textContent = '❌ Not quite! Please enter the exact phrase above.';
            challengeStatus.style.color = '#e05252';
        } else {
            challengeStatus.textContent = '🔒 Complete this challenge to continue.';
            challengeStatus.style.color = '#888';
        }
        updateAcceptButton();
    }

    function updateAcceptButton() {
        const passed = isChallengePassed();
        agreementRow.hidden = !passed;

        const canAccept = hasReachedBottom && passed && agreement.checked;
        acceptButton.disabled = !canAccept;
        acceptButton.textContent = canAccept
            ? '✅ I UNDERSTAND — LET ME CONTINUE'
            : '🔒 Please Complete All Steps';
    }

    challengeInput.addEventListener('input', validateChallenge);
    agreement.addEventListener('change', updateAcceptButton);

    acceptButton.addEventListener('click', function () {
        if (!hasReachedBottom || !isChallengePassed() || !agreement.checked) {
            return;
        }

        try {
            localStorage.setItem(NOTICE_KEY, 'accepted');
        } catch (e) {
            alert('Your browser could not save your acknowledgement. ' +
                'Please enable browser storage and try again.');
            return;
        }

        overlay.classList.remove('visible');
        document.body.style.overflow = '';
    });
})();