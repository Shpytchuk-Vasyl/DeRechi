/* eslint-disable */
import * as types from './graphql';



/**
 * Map of all GraphQL operations in the project.
 *
 * This map has several performance disadvantages:
 * 1. It is not tree-shakeable, so it will include all operations in the project.
 * 2. It is not minifiable, so the string of a GraphQL query will be multiple times inside the bundle.
 * 3. It does not support dead code elimination, so it will add unused operations.
 *
 * Therefore it is highly recommended to use the babel or swc plugin for production.
 * Learn more about it here: https://the-guild.dev/graphql/codegen/plugins/presets/preset-client#reducing-bundle-size
 */
type Documents = {
    "\n  query LostItems($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {\n    lostItems(filter: $filter, sort: $sort, first: $first, after: $after) {\n      edges {\n        cursor\n        node {\n          id\n          title\n          description\n          date\n          compensation {\n            amount\n            currency\n          }\n          image\n          category {\n            id\n            key\n          }\n          place {\n            id\n            name\n            lat\n            lon\n            countryCode\n          }\n        }\n      }\n      pageInfo {\n        hasNextPage\n        endCursor\n      }\n    }\n  }\n": typeof types.LostItemsDocument,
    "\n  query FoundItems($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {\n    foundItems(filter: $filter, sort: $sort, first: $first, after: $after) {\n      edges {\n        cursor\n        node {\n          id\n          title\n          description\n          date\n          compensation {\n            amount\n            currency\n          }\n          image\n          category {\n            id\n            key\n          }\n          place {\n            id\n            name\n            lat\n            lon\n            countryCode\n          }\n        }\n      }\n      pageInfo {\n        hasNextPage\n        endCursor\n      }\n    }\n  }\n": typeof types.FoundItemsDocument,
    "\n  query LostItem($id: ID!) {\n    lostItem(id: $id) {\n      id\n      title\n      description\n      date\n      compensation {\n        amount\n        currency\n      }\n      image\n      category {\n        id\n        key\n      }\n      place {\n        id\n        name\n        lat\n        lon\n        countryCode\n      }\n      contact {\n        id\n        phone\n        email\n      }\n    }\n  }\n": typeof types.LostItemDocument,
    "\n  query FoundItem($id: ID!) {\n    foundItem(id: $id) {\n      id\n      title\n      description\n      date\n      compensation {\n        amount\n        currency\n      }\n      image\n      category {\n        id\n        key\n      }\n      place {\n        id\n        name\n        lat\n        lon\n        countryCode\n      }\n      contact {\n        id\n        phone\n        email\n      }\n    }\n  }\n": typeof types.FoundItemDocument,
    "\n  query Categories {\n    categories {\n      id\n      key\n    }\n  }\n": typeof types.CategoriesDocument,
    "\n  query Countries {\n    countries {\n      code\n      currency\n    }\n  }\n": typeof types.CountriesDocument,
    "\n  query Stats {\n    stats {\n      returnedThisWeek\n      foundToday\n    }\n  }\n": typeof types.StatsDocument,
    "\n  mutation CreateLostItem($input: ItemInput!) {\n    createLostItem(input: $input) {\n      id\n    }\n  }\n": typeof types.CreateLostItemDocument,
    "\n  mutation CreateFoundItem($input: ItemInput!) {\n    createFoundItem(input: $input) {\n      id\n    }\n  }\n": typeof types.CreateFoundItemDocument,
    "\n  mutation ClaimLostItem($id: ID!, $contact: ContactInfoInput!) {\n    claimLostItem(id: $id, contact: $contact) {\n      id\n      repeated\n    }\n  }\n": typeof types.ClaimLostItemDocument,
    "\n  mutation ClaimFoundItem($id: ID!, $contact: ContactInfoInput!) {\n    claimFoundItem(id: $id, contact: $contact) {\n      id\n      repeated\n    }\n  }\n": typeof types.ClaimFoundItemDocument,
    "\n  mutation ConfirmReturn($token: String!) {\n    confirmReturn(token: $token)\n  }\n": typeof types.ConfirmReturnDocument,
    "\n  query Places($name: String, $first: Int) {\n    places(name: $name, first: $first) {\n      edges {\n        node {\n          id\n          name\n          lat\n          lon\n          countryCode\n        }\n      }\n    }\n  }\n": typeof types.PlacesDocument,
};
const documents: Documents = {
    "\n  query LostItems($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {\n    lostItems(filter: $filter, sort: $sort, first: $first, after: $after) {\n      edges {\n        cursor\n        node {\n          id\n          title\n          description\n          date\n          compensation {\n            amount\n            currency\n          }\n          image\n          category {\n            id\n            key\n          }\n          place {\n            id\n            name\n            lat\n            lon\n            countryCode\n          }\n        }\n      }\n      pageInfo {\n        hasNextPage\n        endCursor\n      }\n    }\n  }\n": types.LostItemsDocument,
    "\n  query FoundItems($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {\n    foundItems(filter: $filter, sort: $sort, first: $first, after: $after) {\n      edges {\n        cursor\n        node {\n          id\n          title\n          description\n          date\n          compensation {\n            amount\n            currency\n          }\n          image\n          category {\n            id\n            key\n          }\n          place {\n            id\n            name\n            lat\n            lon\n            countryCode\n          }\n        }\n      }\n      pageInfo {\n        hasNextPage\n        endCursor\n      }\n    }\n  }\n": types.FoundItemsDocument,
    "\n  query LostItem($id: ID!) {\n    lostItem(id: $id) {\n      id\n      title\n      description\n      date\n      compensation {\n        amount\n        currency\n      }\n      image\n      category {\n        id\n        key\n      }\n      place {\n        id\n        name\n        lat\n        lon\n        countryCode\n      }\n      contact {\n        id\n        phone\n        email\n      }\n    }\n  }\n": types.LostItemDocument,
    "\n  query FoundItem($id: ID!) {\n    foundItem(id: $id) {\n      id\n      title\n      description\n      date\n      compensation {\n        amount\n        currency\n      }\n      image\n      category {\n        id\n        key\n      }\n      place {\n        id\n        name\n        lat\n        lon\n        countryCode\n      }\n      contact {\n        id\n        phone\n        email\n      }\n    }\n  }\n": types.FoundItemDocument,
    "\n  query Categories {\n    categories {\n      id\n      key\n    }\n  }\n": types.CategoriesDocument,
    "\n  query Countries {\n    countries {\n      code\n      currency\n    }\n  }\n": types.CountriesDocument,
    "\n  query Stats {\n    stats {\n      returnedThisWeek\n      foundToday\n    }\n  }\n": types.StatsDocument,
    "\n  mutation CreateLostItem($input: ItemInput!) {\n    createLostItem(input: $input) {\n      id\n    }\n  }\n": types.CreateLostItemDocument,
    "\n  mutation CreateFoundItem($input: ItemInput!) {\n    createFoundItem(input: $input) {\n      id\n    }\n  }\n": types.CreateFoundItemDocument,
    "\n  mutation ClaimLostItem($id: ID!, $contact: ContactInfoInput!) {\n    claimLostItem(id: $id, contact: $contact) {\n      id\n      repeated\n    }\n  }\n": types.ClaimLostItemDocument,
    "\n  mutation ClaimFoundItem($id: ID!, $contact: ContactInfoInput!) {\n    claimFoundItem(id: $id, contact: $contact) {\n      id\n      repeated\n    }\n  }\n": types.ClaimFoundItemDocument,
    "\n  mutation ConfirmReturn($token: String!) {\n    confirmReturn(token: $token)\n  }\n": types.ConfirmReturnDocument,
    "\n  query Places($name: String, $first: Int) {\n    places(name: $name, first: $first) {\n      edges {\n        node {\n          id\n          name\n          lat\n          lon\n          countryCode\n        }\n      }\n    }\n  }\n": types.PlacesDocument,
};

/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query LostItems($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {\n    lostItems(filter: $filter, sort: $sort, first: $first, after: $after) {\n      edges {\n        cursor\n        node {\n          id\n          title\n          description\n          date\n          compensation {\n            amount\n            currency\n          }\n          image\n          category {\n            id\n            key\n          }\n          place {\n            id\n            name\n            lat\n            lon\n            countryCode\n          }\n        }\n      }\n      pageInfo {\n        hasNextPage\n        endCursor\n      }\n    }\n  }\n"): typeof import('./graphql').LostItemsDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query FoundItems($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {\n    foundItems(filter: $filter, sort: $sort, first: $first, after: $after) {\n      edges {\n        cursor\n        node {\n          id\n          title\n          description\n          date\n          compensation {\n            amount\n            currency\n          }\n          image\n          category {\n            id\n            key\n          }\n          place {\n            id\n            name\n            lat\n            lon\n            countryCode\n          }\n        }\n      }\n      pageInfo {\n        hasNextPage\n        endCursor\n      }\n    }\n  }\n"): typeof import('./graphql').FoundItemsDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query LostItem($id: ID!) {\n    lostItem(id: $id) {\n      id\n      title\n      description\n      date\n      compensation {\n        amount\n        currency\n      }\n      image\n      category {\n        id\n        key\n      }\n      place {\n        id\n        name\n        lat\n        lon\n        countryCode\n      }\n      contact {\n        id\n        phone\n        email\n      }\n    }\n  }\n"): typeof import('./graphql').LostItemDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query FoundItem($id: ID!) {\n    foundItem(id: $id) {\n      id\n      title\n      description\n      date\n      compensation {\n        amount\n        currency\n      }\n      image\n      category {\n        id\n        key\n      }\n      place {\n        id\n        name\n        lat\n        lon\n        countryCode\n      }\n      contact {\n        id\n        phone\n        email\n      }\n    }\n  }\n"): typeof import('./graphql').FoundItemDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query Categories {\n    categories {\n      id\n      key\n    }\n  }\n"): typeof import('./graphql').CategoriesDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query Countries {\n    countries {\n      code\n      currency\n    }\n  }\n"): typeof import('./graphql').CountriesDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query Stats {\n    stats {\n      returnedThisWeek\n      foundToday\n    }\n  }\n"): typeof import('./graphql').StatsDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  mutation CreateLostItem($input: ItemInput!) {\n    createLostItem(input: $input) {\n      id\n    }\n  }\n"): typeof import('./graphql').CreateLostItemDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  mutation CreateFoundItem($input: ItemInput!) {\n    createFoundItem(input: $input) {\n      id\n    }\n  }\n"): typeof import('./graphql').CreateFoundItemDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  mutation ClaimLostItem($id: ID!, $contact: ContactInfoInput!) {\n    claimLostItem(id: $id, contact: $contact) {\n      id\n      repeated\n    }\n  }\n"): typeof import('./graphql').ClaimLostItemDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  mutation ClaimFoundItem($id: ID!, $contact: ContactInfoInput!) {\n    claimFoundItem(id: $id, contact: $contact) {\n      id\n      repeated\n    }\n  }\n"): typeof import('./graphql').ClaimFoundItemDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  mutation ConfirmReturn($token: String!) {\n    confirmReturn(token: $token)\n  }\n"): typeof import('./graphql').ConfirmReturnDocument;
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query Places($name: String, $first: Int) {\n    places(name: $name, first: $first) {\n      edges {\n        node {\n          id\n          name\n          lat\n          lon\n          countryCode\n        }\n      }\n    }\n  }\n"): typeof import('./graphql').PlacesDocument;


export function graphql(source: string) {
  return (documents as any)[source] ?? {};
}
