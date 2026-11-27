window.addEventListener('load', function () {
    const loader = document.getElementById('loader-overlay');
    loader.style.opacity = '0';
    setTimeout(() => {
        loader.style.display = 'none';
    }, 400);
});

function refreshData() {
    const loader = document.getElementById('loader-overlay');
    loader.style.display = 'flex';
    loader.style.opacity = '1';
    window.location.reload();
}