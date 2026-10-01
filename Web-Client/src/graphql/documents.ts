import { graphql } from "./generated"

export const LostItemsQuery = graphql(`
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
`)

export const FoundItemsQuery = graphql(`
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
`)

export const LostItemQuery = graphql(`
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
`)

export const FoundItemQuery = graphql(`
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
`)

export const CategoriesQuery = graphql(`
  query Categories {
    categories {
      id
      key
    }
  }
`)

export const CountriesQuery = graphql(`
  query Countries {
    countries {
      code
      currency
    }
  }
`)

export const StatsQuery = graphql(`
  query Stats {
    stats {
      returnedThisWeek
      foundToday
    }
  }
`)

export const CreateLostItemMutation = graphql(`
  mutation CreateLostItem($input: ItemInput!) {
    createLostItem(input: $input) {
      id
    }
  }
`)

export const CreateFoundItemMutation = graphql(`
  mutation CreateFoundItem($input: ItemInput!) {
    createFoundItem(input: $input) {
      id
    }
  }
`)

export const ClaimLostItemMutation = graphql(`
  mutation ClaimLostItem($id: ID!, $contact: ContactInfoInput!) {
    claimLostItem(id: $id, contact: $contact) {
      id
      repeated
    }
  }
`)

export const ClaimFoundItemMutation = graphql(`
  mutation ClaimFoundItem($id: ID!, $contact: ContactInfoInput!) {
    claimFoundItem(id: $id, contact: $contact) {
      id
      repeated
    }
  }
`)

export const ConfirmReturnMutation = graphql(`
  mutation ConfirmReturn($token: String!) {
    confirmReturn(token: $token)
  }
`)

export const PlacesQuery = graphql(`
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
`)
