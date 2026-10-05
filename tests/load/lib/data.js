// Cities and words mirror seed/seed.sql.

import exec from 'k6/execution';

export const CITIES = [
    { name: 'Київ', lat: 50.4501, lon: 30.5234, country: 'UA' },
    { name: 'Харків', lat: 49.9935, lon: 36.2304, country: 'UA' },
    { name: 'Одеса', lat: 46.4825, lon: 30.7233, country: 'UA' },
    { name: 'Дніпро', lat: 48.4647, lon: 35.0462, country: 'UA' },
    { name: 'Львів', lat: 49.8397, lon: 24.0297, country: 'UA' },
    { name: 'Запоріжжя', lat: 47.8388, lon: 35.1396, country: 'UA' },
    { name: 'Вінниця', lat: 49.2331, lon: 28.4682, country: 'UA' },
    { name: 'Полтава', lat: 49.5883, lon: 34.5514, country: 'UA' },
    { name: 'Warszawa', lat: 52.2297, lon: 21.0122, country: 'PL' },
    { name: 'Kraków', lat: 50.0647, lon: 19.9450, country: 'PL' },
    { name: 'Berlin', lat: 52.5200, lon: 13.4050, country: 'DE' },
    { name: 'Paris', lat: 48.8566, lon: 2.3522, country: 'FR' },
];

const NOUNS = [
    ['DOCUMENTS', 'паспорт'], ['DOCUMENTS', 'посвідчення водія'], ['DOCUMENTS', 'студентський квиток'],
    ['WALLET', 'гаманець'], ['WALLET', 'портмоне'], ['WALLET', 'банківська картка'],
    ['ELECTRONICS', 'телефон'], ['ELECTRONICS', 'навушники'], ['ELECTRONICS', 'ноутбук'],
    ['JEWELRY', 'обручка'], ['JEWELRY', 'сережки'], ['JEWELRY', 'браслет'],
    ['ANIMALS', 'собака'], ['ANIMALS', 'кіт'],
    ['KEYS', 'ключі'], ['KEYS', 'ключі від авто'],
    ['BAGS', 'рюкзак'], ['BAGS', 'сумка'],
    ['OTHER', 'парасолька'], ['OTHER', 'окуляри'],
];

const SEARCH_HITS = ['гаманець', 'ключі', 'паспорт', 'телефон', 'собака', 'рюкзак', 'навушники',
    'кіт', 'сумка', 'окуляри', 'метро', 'парку', 'Київ', 'чорний'];
const SEARCH_MISSES = ['велосипед', 'скрипка', 'дрон'];

const SORTS = ['DATE_ASC', 'TITLE_ASC', 'TITLE_DESC'];

export const pick = (list) => list[Math.floor(Math.random() * list.length)];
const chance = (p) => Math.random() < p;

export function randomFilter(categoryIds) {
    const r = Math.random();
    if (r < 0.35) {
        return { label: 'none', filter: null };
    }
    if (r < 0.55) {
        return { label: 'search', filter: { search: chance(0.85) ? pick(SEARCH_HITS) : pick(SEARCH_MISSES) } };
    }
    if (r < 0.70) {
        return { label: 'category', filter: { categoryId: pick(categoryIds) } };
    }
    if (r < 0.85) {
        return { label: 'near', filter: { near: near() } };
    }
    if (r < 0.95) {
        return { label: 'dates', filter: { dateFrom: daysAgo(chance(0.5) ? 7 : 14), dateTo: daysAgo(0) } };
    }
    return {
        label: 'combined',
        filter: { search: pick(SEARCH_HITS), categoryId: pick(categoryIds), near: near() },
    };
}

export function listVariables(filter) {
    const r = Math.random();
    return {
        filter,
        sort: chance(0.7) ? undefined : pick(SORTS),
        first: r < 0.8 ? 20 : r < 0.95 ? 50 : 100,
    };
}

function near() {
    const city = pick(CITIES);
    return { lat: city.lat, lon: city.lon, radiusKm: pick([5, 10, 20, 50, 100]) };
}

export function placePrefixes() {
    const name = pick(CITIES).name;
    return [2, 3, 5].filter((n) => n <= name.length).map((n) => name.slice(0, n));
}

export function itemInput(kind, categoryIdsByKey) {
    const [categoryKey, noun] = pick(NOUNS);
    const city = pick(CITIES);
    const id = uniqueId();
    return {
        title: `${capitalize(noun)} ${pick(['біля метро', 'у парку', 'в автобусі', 'на вокзалі'])}`,
        description: chance(0.8) ? `Навантажувальний тест. Колір: ${pick(['чорний', 'сірий', 'синій'])}.` : null,
        // Never today: the server's clock may be behind ours.
        date: daysAgo(1 + Math.floor(Math.random() * 27)),
        compensation: chance(0.5) ? { amount: pick([200, 500, 1000]) } : null,
        image: kind === 'found' || chance(0.3) ? 'http://localhost:8080/files/load/seed.jpg' : null,
        categoryId: categoryIdsByKey[categoryKey],
        // `load-` marks the row for seed/cleanup.sql.
        place: {
            id: `load-new-${id}`,
            name: `${city.name}, навантажувальний тест ${id}`,
            lat: city.lat + (Math.random() - 0.5) * 0.2,
            lon: city.lon + (Math.random() - 0.5) * 0.3,
            countryCode: city.country,
        },
        contact: contact('author', id),
    };
}

export function claimantContact() {
    return contact('claimant', uniqueId());
}

function contact(role, id) {
    return {
        phone: `+38063${String(Math.floor(Math.random() * 1e7)).padStart(7, '0')}`,
        email: `${role}-${id}@load.test`,
        socialMedias: chance(0.3) ? ['TELEGRAM'] : null,
    };
}

function uniqueId() {
    return `${Date.now().toString(36)}-${exec.vu.idInTest}-${exec.vu.iterationInScenario}`;
}

function daysAgo(days) {
    const date = new Date(Date.now() - days * 24 * 3600 * 1000);
    return date.toISOString().slice(0, 10);
}

function capitalize(text) {
    return text.charAt(0).toUpperCase() + text.slice(1);
}
