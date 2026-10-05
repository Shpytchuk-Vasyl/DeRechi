// Copied from Web-Client/src/graphql/documents.ts. Unlock* are left out: each call creates a
// real product on Fourthwall.

const LIST_NODE = `
  id
  title
  description
  date
  compensation { amount currency }
  image
  category { id key }
  place { id name lat lon countryCode }
`;

const connection = (field) => `
  query ${field === 'lostItems' ? 'LostItems' : 'FoundItems'}($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {
    ${field}(filter: $filter, sort: $sort, first: $first, after: $after) {
      edges { cursor node { ${LIST_NODE} } }
      pageInfo { hasNextPage endCursor }
    }
  }
`;

const detail = (field) => `
  query ${field === 'lostItem' ? 'LostItem' : 'FoundItem'}($id: ID!) {
    ${field}(id: $id) {
      ${LIST_NODE}
      contact { id phone email }
    }
  }
`;

const CLAIM_FIELDS = 'id repeated checkoutUrl paid contactsSent';

export const QUERIES = {
    lost: {
        list: { name: 'LostItems', field: 'lostItems', query: connection('lostItems') },
        detail: { name: 'LostItem', field: 'lostItem', query: detail('lostItem') },
        create: {
            name: 'CreateLostItem',
            field: 'createLostItem',
            query: 'mutation CreateLostItem($input: ItemInput!) { createLostItem(input: $input) { id } }',
        },
        claim: {
            name: 'ClaimLostItem',
            field: 'claimLostItem',
            query: `mutation ClaimLostItem($id: ID!, $contact: ContactInfoInput!) {
              claimLostItem(id: $id, contact: $contact) { ${CLAIM_FIELDS} } }`,
        },
        claimStatus: {
            name: 'LostItemClaim',
            field: 'lostItemClaim',
            query: `query LostItemClaim($itemId: ID!, $id: ID!) {
              lostItemClaim(itemId: $itemId, id: $id) { id checkoutUrl paid contactsSent } }`,
        },
    },
    found: {
        list: { name: 'FoundItems', field: 'foundItems', query: connection('foundItems') },
        detail: { name: 'FoundItem', field: 'foundItem', query: detail('foundItem') },
        create: {
            name: 'CreateFoundItem',
            field: 'createFoundItem',
            query: 'mutation CreateFoundItem($input: ItemInput!) { createFoundItem(input: $input) { id } }',
        },
        claim: {
            name: 'ClaimFoundItem',
            field: 'claimFoundItem',
            query: `mutation ClaimFoundItem($id: ID!, $contact: ContactInfoInput!) {
              claimFoundItem(id: $id, contact: $contact) { ${CLAIM_FIELDS} } }`,
        },
        claimStatus: {
            name: 'FoundItemClaim',
            field: 'foundItemClaim',
            query: `query FoundItemClaim($itemId: ID!, $id: ID!) {
              foundItemClaim(itemId: $itemId, id: $id) { id checkoutUrl paid contactsSent } }`,
        },
    },
    categories: { name: 'Categories', field: 'categories', query: 'query Categories { categories { id key } }' },
    countries: { name: 'Countries', field: 'countries', query: 'query Countries { countries { code currency } }' },
    places: {
        name: 'Places',
        field: 'places',
        query: `query Places($name: String, $first: Int) {
          places(name: $name, first: $first) { edges { node { id name lat lon countryCode } } } }`,
    },
};
