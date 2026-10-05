import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { GRAPHQL_URL } from './config.js';

// Spring for GraphQL answers HTTP 200 with an `errors` array when an operation fails, so
// http_req_failed alone would report a run where every request failed as clean.
const graphqlErrors = new Rate('graphql_errors');
const graphqlErrorsByClass = new Counter('graphql_errors_by_class');

const PARAMS = { headers: { 'Content-Type': 'application/json' } };

// Each VU logs the first few failures it sees, enough to tell what broke without flooding.
const MAX_LOGGED = 3;
let logged = 0;

// Sends one operation and returns its `data`, or null when it failed.
// `op` is { name, field, query } from queries.js; `tags` are added to every metric of the request.
export function gql(op, variables, tags = {}) {
    const kind = op.query.trim().startsWith('mutation') ? 'write' : 'read';
    const allTags = { op: op.name, kind, ...tags };
    const res = http.post(GRAPHQL_URL, JSON.stringify({ operationName: op.name, query: op.query, variables }),
        { ...PARAMS, tags: allTags });

    let body = null;
    try {
        body = res.json();
    } catch (e) {
        // A gateway error page or an empty body; reported below.
    }
    const errors = body && Array.isArray(body.errors) ? body.errors : null;
    const failed = res.status !== 200 || !body || errors !== null;

    graphqlErrors.add(failed, allTags);
    for (const error of errors || []) {
        const classification = (error.extensions && error.extensions.classification) || 'UNKNOWN';
        graphqlErrorsByClass.add(1, { ...allTags, classification });
    }
    check(res, {
        'HTTP 200': (r) => r.status === 200,
        'no GraphQL errors': () => body !== null && errors === null,
    }, allTags);

    if (failed && logged < MAX_LOGGED) {
        logged++;
        const detail = errors ? JSON.stringify(errors.map((e) => e.message)) : String(res.body).slice(0, 200);
        console.warn(`${op.name} failed: HTTP ${res.status} ${detail}`);
    }
    return failed ? null : body.data;
}
