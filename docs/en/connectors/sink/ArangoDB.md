import ChangeLog from '../changelog/connector-arangodb.md';

# ArangoDB

> ArangoDB sink connector

## Description

Converts each SeaTunnel row to an ArangoDB document and executes a user-supplied AQL data-modification query in batches.

The connector uses `arangodb-java-driver-shaded` version `7.26.0`.

## Key features

- [x] batch writes
- [ ] [exactly-once](../../introduction/concepts/connector-v2-features.md)

The sink has at-least-once behavior. Use an idempotent AQL query such as `UPSERT` when a retry must not create duplicate documents.

## Options

| name                | type          | required | default value |
|---------------------|---------------|----------|---------------|
| hosts               | Array\<String> | Yes      | -             |
| username            | String        | No       | -             |
| password            | String        | No       | -             |
| database            | String        | Yes      | -             |
| query               | String        | Yes      | -             |
| query_params        | Map           | No       | {}            |
| max_batch_size      | Integer       | No       | 500           |
| batch_data_variable | String        | No       | batch         |
| use_ssl             | Boolean       | No       | false         |
| verify_host         | Boolean       | No       | true          |
| connect_timeout_ms  | Integer       | No       | 30000         |
| common-options      | config        | No       | -             |

### hosts [Array\<String>]

One or more ArangoDB endpoints. Each entry accepts `host`, `host:port`, or `[IPv6]:port`. The default port is `8529`.

### username / password [String]

Optional ArangoDB credentials. Omit both options only when the server permits unauthenticated access.

### database [String]

The ArangoDB database in which the AQL query is executed.

### query [String]

An AQL data-modification query. The buffered input documents are bound as an array using `batch_data_variable`. Use bind variables for collection names and other values.

### query_params [Map]

Static AQL bind variables. For `@@collection`, use the quoted key `"@collection"`. This map must not define the batch variable.

### max_batch_size [Integer]

Maximum number of documents sent by one AQL execution. The value must be greater than zero. Pending documents are also flushed at checkpoints and when the writer closes.

### batch_data_variable [String]

Name of the AQL bind variable containing the batch, without the leading `@`. With the default, the query iterates over `@batch`.

### use_ssl / verify_host [Boolean]

Enables TLS and controls hostname verification. Do not disable hostname verification outside an isolated test environment.

### connect_timeout_ms [Integer]

Connection and request timeout in milliseconds. The value must be greater than zero.

### common options

Sink plugin common parameters, see [Sink Common Options](../common-options/sink-common-options.md).

## Idempotent UPSERT example

```hocon
sink {
  ArangoDB {
    hosts = ["localhost:8529"]
    username = "root"
    password = "arangodb-test"
    database = "seatunnel"
    query = "FOR doc IN @batch UPSERT {_key: doc._key} INSERT doc UPDATE doc IN @@collection"
    query_params = {
      "@collection" = "seatunnel_docs"
    }
    max_batch_size = 500
    batch_data_variable = "batch"
  }
}
```

SeaTunnel field names become document property names. `DATE`, `TIME`, and `TIMESTAMP` values are stored as ISO-8601 strings, and `BYTES` values are stored as Base64 strings.

## Changelog

<ChangeLog />
