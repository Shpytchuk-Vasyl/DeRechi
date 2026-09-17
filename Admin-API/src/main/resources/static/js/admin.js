document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.navbar-burger').forEach((burger) => {
        burger.addEventListener('click', () => {
            const menu = document.getElementById(burger.dataset.target);
            if (!menu) {
                return;
            }
            burger.classList.toggle('is-active');
            menu.classList.toggle('is-active');
            burger.setAttribute('aria-expanded', burger.classList.contains('is-active'));
        });
    });
});

document.addEventListener('error', (event) => {
    const image = event.target;
    if (!(image instanceof HTMLImageElement) || !image.classList.contains('thumb-img')) {
        return;
    }
    image.classList.add('is-hidden');
    image.closest('.thumb').querySelector('.thumb-fallback').classList.remove('is-hidden');
}, true);
