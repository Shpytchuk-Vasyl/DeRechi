# Extending features

Step-by-step checklists for the changes we expect to make more than once. Each page assumes you have read the matching page under [features](../features/README.md), names the exact files to touch, says whether a migration is needed, and lists what the Web-Client team has to do on their side (backend work stops at the GraphQL schema and the message bundles; we do not edit `Web-Client`).

- [Add a language](add-a-language.md): a new UI locale for the admin panel and the web client.
- [Add a country](add-a-country.md): a new country for places and its reward currency.
- [Add an admin permission](add-an-admin-permission.md): a new scope, action or composite role.
- [Add a social network](add-a-social-network.md): a new messenger in contact details and the Notify menu.
- [Add a notification channel](add-a-notification-channel.md): turning on another NotifyHub channel in `Notification`.
- [Add a category](add-a-category.md): a new entry in the category reference data.
- [Add a search language](add-a-search-language.md): a new full-text search language for matching.
- [Add a GraphQL field](add-a-graphql-field.md): extending the public API.
- [Add an event](add-an-event.md): a new RabbitMQ event between services.
- [Add a module](add-a-module.md): a new service in the reactor.

If what you are about to do is not here, write the page as you go; the next person will need it.
