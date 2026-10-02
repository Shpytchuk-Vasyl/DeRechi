# Add a GraphQL field

Extending the public API in `Client-API`. The schema is the contract: it is written by hand, the Web-Client generates its types from the file, and Spring for GraphQL maps it onto controller methods and records by name.

## Before you start

Read [Notices](../features/notices.md) and [Search and filtering](../features/search-and-filtering.md). Decide whether the field is **derived** (computed from what is stored) or **persisted** (needs a column). The example adds a persisted `color: String` to both item types.

## Steps

1. **Schema first.** `Client-API/src/main/resources/graphql/schema.graphqls`: add the field to `LostItem` and `FoundItem`, and to `ItemInput` if clients may set it. Put a docstring on it; the Web-Client surfaces those. Nullable unless every existing row can have a value.

   ```graphql
   type LostItem {
       # ...
       "Dominant colour, free text"
       color: String
   }
   input ItemInput {
       # ...
       color: String
   }
   ```

2. **Persisted: entity and migration.** Add the property to `Thing` in `DB-Postgres/src/main/java/org/shpytchuk/dbpostgres/core/thing/Thing.java`, generate and review the migration (`./mvnw -pl DB-Postgres compile liquibase:diff`, then rename and fix the file; see [Database migrations](../conventions/database-migrations.md)). Then copy the same property into every module's `Thing`:

   - `Client-API/.../entity/Thing.java`
   - `Admin-API/.../entity/Thing.java`
   - `Worker/.../entity/Thing.java`

   Every service runs with `ddl-auto: none`, so a forgotten copy does not fail at startup; it fails on the first query that touches the column, or worse, silently ignores the data. Keep the copies byte-for-byte identical apart from the package.

3. **Input.** `Client-API/src/main/java/org/shpytchuk/clientapi/input/ItemInput.java` is a record; add the component with Bean Validation (`@Size(max = 30)`). Records bind by constructor, which is why the compiler runs with `-parameters` in the root POM; do not remove that.

4. **DTO and mapper.** Add the component to `dto/ItemDto.java` and map it in `mapper/ItemMapper.toDto`. For a derived field, compute it in the mapper or expose it with a `@SchemaMapping` method on the controller instead of a DTO component.

5. **Service.** `ItemService.buildItemFromInput` copies input to entity; add the line. If the field needs a rule (allowed values, cross-field checks), throw `IllegalArgumentException` with a clear message; `GraphQlExceptionResolver` turns it into `BAD_REQUEST`. Use `NotFoundException` for missing references.

6. **Filter or sort?** A new filter goes into `input/ItemFilterInput.java` and `ThingSpecifications.byFilter` through `SpecificationBuilder`; a new sort option into the `ItemSort` enum and its `toSort`. Both are enums or records, so the schema and the Java side must list the same names.

7. **Controllers.** `LostItemController` and `FoundItemController` only forward to the services with `@QueryMapping`, `@MutationMapping` and `@Argument`; a new top-level query or mutation gets its method there. Argument names must equal the schema argument names.

8. **Manual check.** Start `Client-API` and open GraphiQL at `http://localhost:8082/graphiql` (or through the Gateway at `http://localhost:8080/graphiql`). The schema printer is on, so `http://localhost:8082/graphql/schema` shows what the server actually loaded.

## Tests to add or update

- `LostItemControllerTests` / `FoundItemControllerTests` extend `AbstractGraphQlTests`, which boots the app on a Testcontainers PostGIS database with the real changelog and stubs `RabbitTemplate`. Add a mutation that sets the field and a query that reads it back; `input(title, categoryId)` builds a valid `ItemInput` map to start from.
- `ItemMapperTest` for the DTO mapping, `ItemServiceTests` for any rule.
- `ThingRepositoryTests` if you added a filter.
- For a persisted field, the `DbPostgresApplicationTests` and the `ddl-auto: validate` run of `DB-Postgres` prove the migration matches the entity.

## Migration needed?

Only for a persisted field. Numbered formatted-SQL changeset in `DB-Postgres/changelog/changes/`, reviewed by hand, applied with `liquibase:update`.

## Admin panel

If the field should be editable by staff, the same change continues in `Admin-API`: `ItemForm`, `ItemMapper` (to form, to view), `ItemView`, the `items/form.html` and `items/list.html` templates, and a `field.<name>` key in five bundles. That is a separate checklist but usually the same pull request.

## Web-Client impact

The web team runs `pnpm codegen` (reads our schema file directly, no server needed) and decides where to show or collect the field. Send them the docstring text; it becomes their tooltip.

## Checklist

- [ ] Schema updated with a docstring
- [ ] Entity in `DB-Postgres` plus identical copies in every module, migration reviewed and applied
- [ ] `ItemInput`, `ItemDto`, `ItemMapper`, `ItemService` updated
- [ ] GraphiQL round-trip works
- [ ] Controller, mapper and repository tests extended
- [ ] Admin form and view updated if staff should see it
- [ ] Web-Client team notified
