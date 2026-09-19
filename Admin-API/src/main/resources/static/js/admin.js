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

const formatLocalTimes = (root) => {
    root.querySelectorAll('time.local-datetime').forEach((element) => {
        const moment = new Date(element.dateTime);
        if (Number.isNaN(moment.getTime())) {
            return;
        }
        element.textContent = new Intl.DateTimeFormat(undefined, {
            dateStyle: 'medium',
            timeStyle: 'short',
        }).format(moment);
    });
};

document.addEventListener('DOMContentLoaded', () => formatLocalTimes(document));

/* ---------- htmx ---------- */

document.addEventListener('htmx:load', (event) => formatLocalTimes(event.target));

document.addEventListener('htmx:beforeRequest', (event) => {
    event.detail.elt.classList.add('is-loading');
});

document.addEventListener('htmx:afterRequest', (event) => {
    event.detail.elt.classList.remove('is-loading');
});

const failed = (event) => {
    const element = event.detail.elt;
    const message = element.dataset.error;
    if (!message) {
        return;
    }
    element.classList.add('is-danger', 'is-light');
    element.querySelector('span:last-child').textContent = message;
};

document.addEventListener('htmx:responseError', failed);
document.addEventListener('htmx:sendError', failed);
