import { sleep } from 'k6';
import { BASE_URL, PROFILE, THINK, WRITES, scenarios, thresholds } from './lib/config.js';
import { gql } from './lib/graphql.js';
import { QUERIES } from './lib/queries.js';
import { claimantContact, itemInput, listVariables, pick, placePrefixes, randomFilter } from './lib/data.js';

export const options = {
    scenarios: scenarios(),
    thresholds: thresholds(),
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    tags: { testid: __ENV.TESTID || PROFILE },
};

export function setup() {
    const data = gql(QUERIES.categories, {});
    if (!data) {
        throw new Error(`Client-API does not answer at ${BASE_URL}/graphql`);
    }
    const categoryIdsByKey = {};
    for (const category of data.categories) {
        categoryIdsByKey[category.key] = category.id;
    }

    const probe = gql(QUERIES.lost.list, { first: 100 });
    if (!probe || !probe.lostItems.pageInfo.hasNextPage) {
        console.warn('Fewer than 100 lost items in the database: the numbers will say little. '
            + 'Seed it first with tests/load/seed/seed.sql.');
    }
    console.log(`profile=${PROFILE} target=${BASE_URL} writes=${WRITES}`);
    return { categoryIdsByKey, categoryIds: Object.values(categoryIdsByKey) };
}

export function browse(data) {
    if (Math.random() < 0.3) {
        gql(QUERIES.categories, {});
        gql(QUERIES.countries, {});
    }

    const kind = Math.random() < 0.5 ? 'lost' : 'found';
    const q = QUERIES[kind];
    const { label, filter } = randomFilter(data.categoryIds);
    const variables = listVariables(filter);

    let page = gql(q.list, variables, { filter: label });
    const firstEdges = page ? page[q.list.field].edges : [];

    for (let n = 0; page && page[q.list.field].pageInfo.hasNextPage && n < 3 && Math.random() < 0.3; n++) {
        think(1, 3);
        const after = page[q.list.field].pageInfo.endCursor;
        page = gql(q.list, { ...variables, after }, { filter: label });
    }

    if (firstEdges.length > 0 && Math.random() < 0.5) {
        think(1, 4);
        gql(q.detail, { id: pick(firstEdges).node.id });
    }

    if (Math.random() < 0.1) {
        for (const name of placePrefixes()) {
            think(0.2, 0.5);
            gql(QUERIES.places, { name, first: 10 });
        }
    }
}

export function publish(data) {
    const kind = Math.random() < 0.5 ? 'lost' : 'found';
    const q = QUERIES[kind];

    const created = gql(q.create, { input: itemInput(kind, data.categoryIdsByKey) });
    if (!created || Math.random() >= 0.5) {
        return;
    }

    think(1, 3);
    const itemId = created[q.create.field].id;
    const claimed = gql(q.claim, { id: itemId, contact: claimantContact() });

    if (claimed && Math.random() < 0.5) {
        think(1, 2);
        gql(q.claimStatus, { itemId, id: claimed[q.claim.field].id });
    }
}

function think(min, max) {
    if (THINK > 0) {
        sleep(THINK * (min + Math.random() * (max - min)));
    }
}
