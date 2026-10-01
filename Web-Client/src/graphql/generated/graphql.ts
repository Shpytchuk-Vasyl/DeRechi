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

export type ItemFilterInput = {
  categoryId?: string | number | null | undefined;
  dateFrom?: string | null | undefined;
  dateTo?: string | null | undefined;
  near?: NearInput | null | undefined;
  search?: string | null | undefined;
};

export type ItemInput = {
  categoryId: string | number;
  compensation?: number | null | undefined;
  contact: ContactInfoInput;
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


export type LostItemsQuery = { lostItems: { edges: Array<{ cursor: string, node: { id: string, title: string, description: string | null, date: string, compensation: number | null, image: string | null, category: { id: string, key: string }, place: { id: string, name: string, lat: number | null, lon: number | null } } }>, pageInfo: { hasNextPage: boolean, endCursor: string | null } } };

export type FoundItemsQueryVariables = Exact<{
  filter?: ItemFilterInput | null | undefined;
  sort?: ItemSort | null | undefined;
  first?: number | null | undefined;
  after?: string | null | undefined;
}>;


export type FoundItemsQuery = { foundItems: { edges: Array<{ cursor: string, node: { id: string, title: string, description: string | null, date: string, compensation: number | null, image: string, category: { id: string, key: string }, place: { id: string, name: string, lat: number | null, lon: number | null } } }>, pageInfo: { hasNextPage: boolean, endCursor: string | null } } };

export type LostItemQueryVariables = Exact<{
  id: string | number;
}>;


export type LostItemQuery = { lostItem: { id: string, title: string, description: string | null, date: string, compensation: number | null, image: string | null, category: { id: string, key: string }, place: { id: string, name: string, lat: number | null, lon: number | null }, contact: { id: string, phone: string, email: string } } | null };

export type FoundItemQueryVariables = Exact<{
  id: string | number;
}>;


export type FoundItemQuery = { foundItem: { id: string, title: string, description: string | null, date: string, compensation: number | null, image: string, category: { id: string, key: string }, place: { id: string, name: string, lat: number | null, lon: number | null }, contact: { id: string, phone: string, email: string } } | null };

export type CategoriesQueryVariables = Exact<{ [key: string]: never; }>;


export type CategoriesQuery = { categories: Array<{ id: string, key: string }> };

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

export type PlacesQueryVariables = Exact<{
  name?: string | null | undefined;
  first?: number | null | undefined;
}>;


export type PlacesQuery = { places: { edges: Array<{ node: { id: string, name: string, lat: number | null, lon: number | null } }> } };

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
        compensation
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
        compensation
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
    compensation
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
    compensation
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
export const PlacesDocument = new TypedDocumentString(`
    query Places($name: String, $first: Int) {
  places(name: $name, first: $first) {
    edges {
      node {
        id
        name
        lat
        lon
      }
    }
  }
}
    `) as unknown as TypedDocumentString<PlacesQuery, PlacesQueryVariables>;