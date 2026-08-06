import ChangeLog from '../changelog/connector-arangodb.md';

# ArangoDB

> ArangoDB Sink 连接器

## 描述

将 SeaTunnel 行转换为 ArangoDB 文档，并按批次执行用户配置的 AQL 数据修改语句。

连接器使用 `arangodb-java-driver-shaded` `7.26.0` 版本。

## 主要功能

- [x] 批量写入
- [ ] [精确一次](../../introduction/concepts/connector-v2-features.md)

该 Sink 提供 at-least-once 语义。为了避免重试产生重复文档，应使用 `UPSERT` 等幂等 AQL。

## 配置选项

| 名称                | 类型            | 是否必须 | 默认值 |
|---------------------|-----------------|----------|--------|
| hosts               | Array\<String>   | 是       | -      |
| username            | String          | 否       | -      |
| password            | String          | 否       | -      |
| database            | String          | 是       | -      |
| query               | String          | 是       | -      |
| query_params        | Map             | 否       | {}     |
| max_batch_size      | Integer         | 否       | 500    |
| batch_data_variable | String          | 否       | batch  |
| use_ssl             | Boolean         | 否       | false  |
| verify_host         | Boolean         | 否       | true   |
| connect_timeout_ms  | Integer         | 否       | 30000  |
| common-options      | config          | 否       | -      |

### hosts [Array\<String>]

一个或多个 ArangoDB 地址。每项支持 `host`、`host:port` 或 `[IPv6]:port`，未指定端口时使用 `8529`。

### username / password [String]

可选的 ArangoDB 认证信息。仅当服务端允许匿名访问时才可同时省略。

### database [String]

执行 AQL 的数据库名。

### query [String]

AQL 数据修改语句。缓冲的输入文档会通过 `batch_data_variable` 指定的数组绑定变量传入。集合名和其他值也应使用绑定变量。

### query_params [Map]

静态 AQL 绑定变量。若 AQL 使用 `@@collection`，配置键应写成带引号的 `"@collection"`。此 Map 不得占用批数据变量名。

### max_batch_size [Integer]

单次 AQL 最多写入的文档数，必须大于 0。检查点和 Writer 关闭时也会刷新未满批次的数据。

### batch_data_variable [String]

批数据 AQL 绑定变量名，不包含前导 `@`。默认配置对应查询中的 `@batch`。

### use_ssl / verify_host [Boolean]

启用 TLS 并控制主机名校验。除隔离测试环境外，不应关闭主机名校验。

### connect_timeout_ms [Integer]

连接与请求超时时间，单位毫秒，必须大于 0。

### common options

Sink 公共参数请参考 [Sink 公共配置](../common-options/sink-common-options.md)。

## 幂等 UPSERT 示例

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

SeaTunnel 字段名会成为文档属性名。`DATE`、`TIME`、`TIMESTAMP` 以 ISO-8601 字符串保存，`BYTES` 以 Base64 字符串保存。

## 变更日志

<ChangeLog />
