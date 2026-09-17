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

document.addEventListener('click', async (event) => {
    const button = event.target.closest('.load-candidates');
    if (!button) {
        return;
    }

    const rows = document.querySelector(button.dataset.target);
    if (!rows) {
        return;
    }

    button.classList.add('is-loading');
    button.disabled = true;

    try {
        const response = await fetch(button.dataset.url, {headers: {Accept: 'text/html'}});
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const parsed = document.createElement('template');
        parsed.innerHTML = await response.text();
        parsed.content.querySelectorAll('dialog').forEach((dialog) => document.body.append(dialog));
        rows.append(parsed.content);
        formatLocalTimes(rows);

        button.closest('.more-candidates').remove();
    } catch (error) {
        console.error('Не вдалося довантажити кандидатів', error);
        button.classList.remove('is-loading');
        button.disabled = false;
        button.classList.add('is-danger', 'is-light');
        button.querySelector('span:last-child').textContent = button.dataset.error;
    }
});
