export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
export const GRAPHQL_URL = `${BASE_URL}/graphql`;

export const PROFILE = __ENV.PROFILE || 'smoke';

const RATE = positive('RATE', 20);
const WRITE_SHARE = positive('WRITE_SHARE', 0.05);
const DURATION = __ENV.DURATION;

export const WRITES = (__ENV.WRITES || 'true') === 'true';
const LOCAL_HOSTS = ['localhost', '127.0.0.1', 'host.docker.internal', 'getaway', 'client-api'];
const host = (BASE_URL.match(/^https?:\/\/([^/:]+)/) || [])[1];
if (WRITES && !LOCAL_HOSTS.includes(host) && __ENV.ALLOW_REMOTE_WRITES !== 'true') {
    throw new Error(`Refusing to create notices on ${host}. Run with WRITES=false, `
        + 'or ALLOW_REMOTE_WRITES=true if this environment is meant to take them.');
}

export const THINK = Number(__ENV.THINK || 1);

function positive(name, fallback) {
    const value = Number(__ENV[name] || fallback);
    if (!(value > 0)) {
        throw new Error(`${name} must be a positive number, got ${__ENV[name]}`);
    }
    return value;
}

const writesPerMinute = (rate) => Math.max(1, Math.round(rate * WRITE_SHARE * 60));

function arrival(exec, rate, duration) {
    return {
        executor: 'constant-arrival-rate',
        exec,
        rate,
        timeUnit: exec === 'browse' ? '1s' : '1m',
        duration,
        preAllocatedVUs: Math.ceil(exec === 'browse' ? rate * 2 : rate / 30) + 1,
        maxVUs: Math.ceil(exec === 'browse' ? rate * 10 : rate / 6) + 5,
    };
}

function ramp(exec, multipliers, toRate) {
    const peak = toRate(RATE * multipliers[multipliers.length - 1].x);
    return {
        executor: 'ramping-arrival-rate',
        exec,
        startRate: 0,
        timeUnit: exec === 'browse' ? '1s' : '1m',
        preAllocatedVUs: Math.ceil(exec === 'browse' ? peak : peak / 30) + 1,
        maxVUs: Math.ceil(exec === 'browse' ? peak * 10 : peak / 3) + 5,
        stages: multipliers.map(({ x, duration }) => ({ target: toRate(RATE * x), duration })),
    };
}

const PROFILES = {
    smoke: () => ({
        browse: { executor: 'constant-vus', exec: 'browse', vus: 1, duration: DURATION || '30s' },
        publish: { executor: 'per-vu-iterations', exec: 'publish', vus: 1, iterations: 3, maxDuration: '1m' },
    }),
    load: () => ({
        browse: arrival('browse', RATE, DURATION || '10m'),
        publish: arrival('publish', writesPerMinute(RATE), DURATION || '10m'),
    }),
    stress: () => {
        const steps = [
            { x: 1, duration: '2m' },
            { x: 2, duration: '3m' },
            { x: 4, duration: '3m' },
            { x: 8, duration: '3m' },
            { x: 8, duration: '1m' },
        ];
        return {
            browse: ramp('browse', steps, (r) => Math.max(1, Math.round(r))),
            publish: ramp('publish', steps, writesPerMinute),
        };
    },
    soak: () => ({
        browse: arrival('browse', RATE, DURATION || '1h'),
        publish: arrival('publish', writesPerMinute(RATE), DURATION || '1h'),
    }),
};

export function scenarios() {
    const profile = PROFILES[PROFILE];
    if (!profile) {
        throw new Error(`Unknown PROFILE=${PROFILE}, expected one of ${Object.keys(PROFILES).join(', ')}`);
    }
    const all = profile();
    if (!WRITES) {
        delete all.publish;
    }
    return all;
}

const READ_OPERATIONS = ['LostItems', 'FoundItems', 'LostItem', 'FoundItem', 'Categories', 'Countries',
    'Places', 'LostItemClaim', 'FoundItemClaim'];
const WRITE_OPERATIONS = ['CreateLostItem', 'CreateFoundItem', 'ClaimLostItem', 'ClaimFoundItem'];
const FILTERS = ['none', 'search', 'category', 'near', 'dates', 'combined'];

export function thresholds() {
    const read = ['p(95)<300'];
    const write = ['p(95)<800'];
    const all = {
        http_req_failed: ['rate<0.01'],
        graphql_errors: ['rate<0.01'],
        checks: ['rate>0.99'],
    };
    if (PROFILE === 'smoke') {
        return all;
    }
    Object.assign(all, {
        'http_req_duration{kind:read}': read,
        'http_req_duration{kind:write}': write,
    });
    for (const op of READ_OPERATIONS) {
        all[`http_req_duration{op:${op}}`] = read;
    }
    for (const op of WRITE_OPERATIONS) {
        all[`http_req_duration{op:${op}}`] = write;
    }
    for (const filter of FILTERS) {
        all[`http_req_duration{filter:${filter}}`] = read;
    }
    return all;
}
