import ChangeLog from '../changelog/connector-arangodb.md';

# ArangoDB

> ArangoDB source connector

## Description

Reads the result of an AQL query from ArangoDB. The query must return an object whose keys match the configured SeaTunnel schema field names.

The connector uses `arangodb-java-driver-shaded` version `7.26.0`.

## Key features

- [x] [batch](../../introduction/concepts/connector-v2-features.md)
- [ ] [stream](../../introduction/concepts/connector-v2-features.md)
- [ ] [exactly-once](../../introduction/concepts/connector-v2-features.md)
- [x] [column projection](../../introduction/concepts/connector-v2-features.md)
- [ ] [parallelism](../../introduction/concepts/connector-v2-features.md)

## Options

| name               | type          | required | default value |
|--------------------|---------------|----------|---------------|
| hosts              | Array\<String> | Yes      | -             |
| username           | String        | No       | -             |
| password           | String        | No       | -             |
| database           | String        | Yes      | -             |
| query              | String        | Yes      | -             |
| query_params       | Map           | No       | {}            |
| batch_size         | Integer       | No       | 1000          |
| use_ssl            | Boolean       | No       | false         |
| verify_host        | Boolean       | No       | true          |
| connect_timeout_ms | Integer       | No       | 30000         |
| schema             | Object        | Yes      | -             |

### hosts [Array\<String>]

One or more ArangoDB endpoints. Each entry accepts `host`, `host:port`, or `[IPv6]:port`. The default port is `8529`.

### username / password [String]

Optional ArangoDB credentials. Omit both options only when the server permits unauthenticated access.

### database [String]

The ArangoDB database in which the AQL query is executed.

### query [String]

The AQL query. Return an object with one property for every schema field. A collection should normally be supplied through a collection bind variable instead of being concatenated into the query.

### query_params [Map]

Static AQL bind variables. For `@@collection`, use the quoted map key `"@collection"`.

### batch_size [Integer]

Maximum number of query results transferred from the server in one cursor round trip. The value must be greater than zero.

### use_ssl / verify_host [Boolean]

Enables TLS and controls hostname verification. Do not disable hostname verification outside an isolated test environment.

### connect_timeout_ms [Integer]

Connection and request timeout in milliseconds. The value must be greater than zero.

### schema [Object]

SeaTunnel schema for the AQL result. Field names are used to look up properties in each returned object.

## Example

```hocon
source {
  ArangoDB {
    hosts = ["localhost:8529"]
    username = "root"
    password = "arangodb-test"
    database = "seatunnel"
    query = "FOR doc IN @@collection SORT doc._key RETURN {id: doc._key, name: doc.name, age: doc.age, active: doc.active, tags: doc.tags}"
    query_params = {
      "@collection" = "seatunnel_docs"
    }
    batch_size = 500

    schema {
      fields {
        id = STRING
        name = STRING
        age = INT
        active = BOOLEAN
        tags = "ARRAY<STRING>"
      }
    }
  }
}
```

## Changelog

<ChangeLog />
