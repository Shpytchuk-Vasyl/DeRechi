/* eslint-disable */
/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import type { DocumentTypeDecoration } from '@graphql-typed-document-node/core';
export type ContactInfoInput = {
  email: string;
  /** E.164 format, for example +380671234567 */
  phone: string;
  socialMedias?: Array<SocialMedia> | null | undefined;
};

export type ItemContactInfoInput = {
  email?: string | null | undefined;
  /** E.164 format, for example +380671234567 */
  phone: string;
  socialMedias?: Array<SocialMedia> | null | undefined;
};

export type ItemFilterInput = {
  categoryId?: string | number | null | undefined;
  dateFrom?: string | null | undefined;
  dateTo?: string | null | undefined;
  near?: NearInput | null | undefined;
  search?: string | null | undefined;
};

export type ItemInput = {
  categoryId: string | number;
  compensation?: MoneyInput | null | undefined;
  contact: ItemContactInfoInput;
  date: string;
  description?: string | null | undefined;
  image?: string | null | undefined;
  place: PlaceInput;
  title: string;
};

export type ItemSort =
  | 'DATE_ASC'
  | 'DATE_DESC'
  | 'TITLE_ASC'
  | 'TITLE_DESC';

export type MoneyInput = {
  /** whole units, 0 or more */
  amount: number;
  /** ISO 4217; defaults to the currency of the place's country */
  currency?: string | null | undefined;
};

/** Only items within the radius of this point */
export type NearInput = {
  /** latitude, -90..90 */
  lat: number;
  /** longitude, -180..180 */
  lon: number;
  /** radius in kilometres, up to 500 */
  radiusKm?: number;
};

export type PlaceInput = {
  /** ISO 3166-1 alpha-2, must be a supported country */
  countryCode: string;
  /** ID Google Places */
  id: string | number;
  /** latitude, -90..90 */
  lat: number;
  /** longitude, -180..180 */
  lon: number;
  name: string;
};

export type SocialMedia =
  | 'MESSENGER'
  | 'SIGNAL'
  | 'TELEGRAM'
  | 'VIBER'
  | 'WHATSAPP';

export type LostItemsQueryVariables = Exact<{
  filter?: ItemFilterInput | null | undefined;
  sort?: ItemSort | null | undefined;
  first?: number | null | undefined;
  after?: string | null | undefined;
}>;


export type LostItemsQuery = { lostItems: { edges: Array<{ cursor: string, node: { id: string, title: string, description: string | null, date: string, image: string | null, compensation: { amount: number, currency: string } | null, category: { id: string, key: string }, place: { id: string, name: string, lat: number | null, lon: number | null, countryCode: string } } }>, pageInfo: { hasNextPage: boolean, endCursor: string | null } } };

export type FoundItemsQueryVariables = Exact<{
  filter?: ItemFilterInput | null | undefined;
  sort?: ItemSort | null | undefined;
  first?: number | null | undefined;
  after?: string | null | undefined;
}>;


export type FoundItemsQuery = { foundItems: { edges: Array<{ cursor: string, node: { id: string, title: string, description: string | null, date: string, image: string, compensation: { amount: number, currency: string } | null, category: { id: string, key: string }, place: { id: string, name: string, lat: number | null, lon: number | null, countryCode: string } } }>, pageInfo: { hasNextPage: boolean, endCursor: string | null } } };

export type LostItemQueryVariables = Exact<{
  id: string | number;
}>;


export type LostItemQuery = { lostItem: { id: string, title: string, description: string | null, date: string, image: string | null, compensation: { amount: number, currency: string } | null, category: { id: string, key: string }, place: { id: string, name: string, lat: number | null, lon: number | null, countryCode: string }, contact: { id: string, phone: string, email: string | null } } | null };

export type FoundItemQueryVariables = Exact<{
  id: string | number;
}>;


export type FoundItemQuery = { foundItem: { id: string, title: string, description: string | null, date: string, image: string, compensation: { amount: number, currency: string } | null, category: { id: string, key: string }, place: { id: string, name: string, lat: number | null, lon: number | null, countryCode: string }, contact: { id: string, phone: string, email: string | null } } | null };

export type CategoriesQueryVariables = Exact<{ [key: string]: never; }>;


export type CategoriesQuery = { categories: Array<{ id: string, key: string }> };

export type CountriesQueryVariables = Exact<{ [key: string]: never; }>;


export type CountriesQuery = { countries: Array<{ code: string, currency: string }> };

export type StatsQueryVariables = Exact<{ [key: string]: never; }>;


export type StatsQuery = { stats: { returnedThisWeek: number, foundToday: number } };

export type CreateLostItemMutationVariables = Exact<{
  input: ItemInput;
}>;


export type CreateLostItemMutation = { createLostItem: { id: string } };

export type CreateFoundItemMutationVariables = Exact<{
  input: ItemInput;
}>;


export type CreateFoundItemMutation = { createFoundItem: { id: string } };

export type ClaimLostItemMutationVariables = Exact<{
  id: string | number;
  contact: ContactInfoInput;
}>;


export type ClaimLostItemMutation = { claimLostItem: { id: string, repeated: boolean, checkoutUrl: string | null, paid: boolean, contactsSent: boolean } };

export type ClaimFoundItemMutationVariables = Exact<{
  id: string | number;
  contact: ContactInfoInput;
}>;


export type ClaimFoundItemMutation = { claimFoundItem: { id: string, repeated: boolean, checkoutUrl: string | null, paid: boolean, contactsSent: boolean } };

export type LostItemClaimQueryVariables = Exact<{
  itemId: string | number;
  id: string | number;
}>;


export type LostItemClaimQuery = { lostItemClaim: { id: string, checkoutUrl: string | null, paid: boolean, contactsSent: boolean } | null };

export type FoundItemClaimQueryVariables = Exact<{
  itemId: string | number;
  id: string | number;
}>;


export type FoundItemClaimQuery = { foundItemClaim: { id: string, checkoutUrl: string | null, paid: boolean, contactsSent: boolean } | null };

export type UnlockLostItemClaimMutationVariables = Exact<{
  itemId: string | number;
  id: string | number;
}>;


export type UnlockLostItemClaimMutation = { unlockLostItemClaim: { id: string, checkoutUrl: string | null, paid: boolean, contactsSent: boolean } };

export type UnlockFoundItemClaimMutationVariables = Exact<{
  itemId: string | number;
  id: string | number;
}>;


export type UnlockFoundItemClaimMutation = { unlockFoundItemClaim: { id: string, checkoutUrl: string | null, paid: boolean, contactsSent: boolean } };

export type ConfirmReturnMutationVariables = Exact<{
  token: string;
}>;


export type ConfirmReturnMutation = { confirmReturn: boolean };

export type PlacesQueryVariables = Exact<{
  name?: string | null | undefined;
  first?: number | null | undefined;
}>;


export type PlacesQuery = { places: { edges: Array<{ node: { id: string, name: string, lat: number | null, lon: number | null, countryCode: string } }> } };

export class TypedDocumentString<TResult, TVariables>
  extends String
  implements DocumentTypeDecoration<TResult, TVariables>
{
  __apiType?: NonNullable<DocumentTypeDecoration<TResult, TVariables>['__apiType']>;
  private value: string;
  public __meta__?: Record<string, any> | undefined;

  constructor(value: string, __meta__?: Record<string, any> | undefined) {
    super(value);
    this.value = value;
    this.__meta__ = __meta__;
  }

  override toString(): string & DocumentTypeDecoration<TResult, TVariables> {
    return this.value;
  }
}

export const LostItemsDocument = new TypedDocumentString(`
    query LostItems($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {
  lostItems(filter: $filter, sort: $sort, first: $first, after: $after) {
    edges {
      cursor
      node {
        id
        title
        description
        date
        compensation {
          amount
          currency
        }
        image
        category {
          id
          key
        }
        place {
          id
          name
          lat
          lon
          countryCode
        }
      }
    }
    pageInfo {
      hasNextPage
      endCursor
    }
  }
}
    `) as unknown as TypedDocumentString<LostItemsQuery, LostItemsQueryVariables>;
export const FoundItemsDocument = new TypedDocumentString(`
    query FoundItems($filter: ItemFilterInput, $sort: ItemSort, $first: Int, $after: String) {
  foundItems(filter: $filter, sort: $sort, first: $first, after: $after) {
    edges {
      cursor
      node {
        id
        title
        description
        date
        compensation {
          amount
          currency
        }
        image
        category {
          id
          key
        }
        place {
          id
          name
          lat
          lon
          countryCode
        }
      }
    }
    pageInfo {
      hasNextPage
      endCursor
    }
  }
}
    `) as unknown as TypedDocumentString<FoundItemsQuery, FoundItemsQueryVariables>;
export const LostItemDocument = new TypedDocumentString(`
    query LostItem($id: ID!) {
  lostItem(id: $id) {
    id
    title
    description
    date
    compensation {
      amount
      currency
    }
    image
    category {
      id
      key
    }
    place {
      id
      name
      lat
      lon
      countryCode
    }
    contact {
      id
      phone
      email
    }
  }
}
    `) as unknown as TypedDocumentString<LostItemQuery, LostItemQueryVariables>;
export const FoundItemDocument = new TypedDocumentString(`
    query FoundItem($id: ID!) {
  foundItem(id: $id) {
    id
    title
    description
    date
    compensation {
      amount
      currency
    }
    image
    category {
      id
      key
    }
    place {
      id
      name
      lat
      lon
      countryCode
    }
    contact {
      id
      phone
      email
    }
  }
}
    `) as unknown as TypedDocumentString<FoundItemQuery, FoundItemQueryVariables>;
export const CategoriesDocument = new TypedDocumentString(`
    query Categories {
  categories {
    id
    key
  }
}
    `) as unknown as TypedDocumentString<CategoriesQuery, CategoriesQueryVariables>;
export const CountriesDocument = new TypedDocumentString(`
    query Countries {
  countries {
    code
    currency
  }
}
    `) as unknown as TypedDocumentString<CountriesQuery, CountriesQueryVariables>;
export const StatsDocument = new TypedDocumentString(`
    query Stats {
  stats {
    returnedThisWeek
    foundToday
  }
}
    `) as unknown as TypedDocumentString<StatsQuery, StatsQueryVariables>;
export const CreateLostItemDocument = new TypedDocumentString(`
    mutation CreateLostItem($input: ItemInput!) {
  createLostItem(input: $input) {
    id
  }
}
    `) as unknown as TypedDocumentString<CreateLostItemMutation, CreateLostItemMutationVariables>;
export const CreateFoundItemDocument = new TypedDocumentString(`
    mutation CreateFoundItem($input: ItemInput!) {
  createFoundItem(input: $input) {
    id
  }
}
    `) as unknown as TypedDocumentString<CreateFoundItemMutation, CreateFoundItemMutationVariables>;
export const ClaimLostItemDocument = new TypedDocumentString(`
    mutation ClaimLostItem($id: ID!, $contact: ContactInfoInput!) {
  claimLostItem(id: $id, contact: $contact) {
    id
    repeated
    checkoutUrl
    paid
    contactsSent
  }
}
    `) as unknown as TypedDocumentString<ClaimLostItemMutation, ClaimLostItemMutationVariables>;
export const ClaimFoundItemDocument = new TypedDocumentString(`
    mutation ClaimFoundItem($id: ID!, $contact: ContactInfoInput!) {
  claimFoundItem(id: $id, contact: $contact) {
    id
    repeated
    checkoutUrl
    paid
    contactsSent
  }
}
    `) as unknown as TypedDocumentString<ClaimFoundItemMutation, ClaimFoundItemMutationVariables>;
export const LostItemClaimDocument = new TypedDocumentString(`
    query LostItemClaim($itemId: ID!, $id: ID!) {
  lostItemClaim(itemId: $itemId, id: $id) {
    id
    checkoutUrl
    paid
    contactsSent
  }
}
    `) as unknown as TypedDocumentString<LostItemClaimQuery, LostItemClaimQueryVariables>;
export const FoundItemClaimDocument = new TypedDocumentString(`
    query FoundItemClaim($itemId: ID!, $id: ID!) {
  foundItemClaim(itemId: $itemId, id: $id) {
    id
    checkoutUrl
    paid
    contactsSent
  }
}
    `) as unknown as TypedDocumentString<FoundItemClaimQuery, FoundItemClaimQueryVariables>;
export const UnlockLostItemClaimDocument = new TypedDocumentString(`
    mutation UnlockLostItemClaim($itemId: ID!, $id: ID!) {
  unlockLostItemClaim(itemId: $itemId, id: $id) {
    id
    checkoutUrl
    paid
    contactsSent
  }
}
    `) as unknown as TypedDocumentString<UnlockLostItemClaimMutation, UnlockLostItemClaimMutationVariables>;
export const UnlockFoundItemClaimDocument = new TypedDocumentString(`
    mutation UnlockFoundItemClaim($itemId: ID!, $id: ID!) {
  unlockFoundItemClaim(itemId: $itemId, id: $id) {
    id
    checkoutUrl
    paid
    contactsSent
  }
}
    `) as unknown as TypedDocumentString<UnlockFoundItemClaimMutation, UnlockFoundItemClaimMutationVariables>;
export const ConfirmReturnDocument = new TypedDocumentString(`
    mutation ConfirmReturn($token: String!) {
  confirmReturn(token: $token)
}
    `) as unknown as TypedDocumentString<ConfirmReturnMutation, ConfirmReturnMutationVariables>;
export const PlacesDocument = new TypedDocumentString(`
    query Places($name: String, $first: Int) {
  places(name: $name, first: $first) {
    edges {
      node {
        id
        name
        lat
        lon
        countryCode
      }
    }
  }
}
    `) as unknown as TypedDocumentString<PlacesQuery, PlacesQueryVariables>;