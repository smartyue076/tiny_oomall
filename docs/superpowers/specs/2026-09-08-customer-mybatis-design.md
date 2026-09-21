# Customer Module MyBatis Migration Design

## Goal

Replace Spring Data JPA and Hibernate in the `customer` module with MyBatis XML mappers while preserving the existing HTTP API, MySQL schema, Redis-based session behavior, and business error responses.

## Scope

- Remove the JPA starter and all `jakarta.persistence` annotations from `Customer` and `CustomerAddress`.
- Add the MyBatis Spring Boot starter and configure XML mapper discovery.
- Replace `CustomerRepository` and `AddressRepository` with MyBatis mapper interfaces and XML mapping files.
- Keep Flyway as the owner of the database schema.
- Keep Spring transactions for address changes.

The migration does not change controller paths, request and response DTOs, Redis session storage, or Flyway migration SQL.

## Architecture

`CustomerController` continues to call `CustomerService`. The service continues to own registration, login, address ownership, and default-address rules. It will call MyBatis mapper methods rather than Spring Data repository methods.

Each mapper interface declares Java methods only. Its XML file provides SQL statements with the same mapper namespace. MyBatis maps result columns to JavaBean properties and uses MySQL generated keys to populate ids after inserts.

## Persistence Mapping

`CustomerMapper` provides lookup by mobile, mobile existence checking, and insertion. `AddressMapper` provides owned-address lookup, count, insertion, update, deletion, clearing default flags, paged lookup, and total count.

`Customer` and `CustomerAddress` become regular Java domain objects. They retain constructors, accessors, and domain behavior such as `CustomerAddress.update`, but do not carry persistence annotations.

The address list query uses `ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}`. The service constructs the existing Spring `Page<CustomerAddress>` return value from the list and total count, so its public API remains unchanged.

## Transactions And Errors

`@Transactional` remains on address mutations. MyBatis participates in Spring's transaction manager, so clearing an existing default address and inserting or updating the next default address commit or roll back together.

The service-level mobile existence check remains for a friendly `MOBILE_EXISTS` response. The unique database constraint remains the final concurrency safeguard. Existing exception behavior and controller contracts remain unchanged.

## Configuration And Verification

`application.yaml` removes JPA settings and adds MyBatis mapper XML locations and underscore-to-camel-case mapping. Flyway continues to validate and migrate the schema.

Tests will cover mapper-backed registration and address mutations, especially generated ids, duplicate mobile behavior, page ordering, and default-address transactional behavior. Maven compilation and the customer test suite are the completion checks.
