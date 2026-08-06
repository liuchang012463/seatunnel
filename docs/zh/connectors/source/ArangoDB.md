import ChangeLog from '../changelog/connector-arangodb.md';

# ArangoDB

> ArangoDB Source 连接器

## 描述

执行 ArangoDB AQL 查询并读取查询结果。AQL 必须返回对象，且对象属性名需要与 SeaTunnel schema 字段名一致。

连接器使用 `arangodb-java-driver-shaded` `7.26.0` 版本。

## 主要功能

- [x] [批处理](../../introduction/concepts/connector-v2-features.md)
- [ ] [流处理](../../introduction/concepts/connector-v2-features.md)
- [ ] [精确一次](../../introduction/concepts/connector-v2-features.md)
- [x] [列投影](../../introduction/concepts/connector-v2-features.md)
- [ ] [并行度](../../introduction/concepts/connector-v2-features.md)

## 配置选项

| 名称               | 类型            | 是否必须 | 默认值 |
|--------------------|-----------------|----------|--------|
| hosts              | Array\<String>   | 是       | -      |
| username           | String          | 否       | -      |
| password           | String          | 否       | -      |
| database           | String          | 是       | -      |
| query              | String          | 是       | -      |
| query_params       | Map             | 否       | {}     |
| batch_size         | Integer         | 否       | 1000   |
| use_ssl            | Boolean         | 否       | false  |
| verify_host        | Boolean         | 否       | true   |
| connect_timeout_ms | Integer         | 否       | 30000  |
| schema             | Object          | 是       | -      |

### hosts [Array\<String>]

一个或多个 ArangoDB 地址。每项支持 `host`、`host:port` 或 `[IPv6]:port`，未指定端口时使用 `8529`。

### username / password [String]

可选的 ArangoDB 认证信息。仅当服务端允许匿名访问时才可同时省略。

### database [String]

执行 AQL 的数据库名。

### query [String]

AQL 查询语句。查询结果需要包含 schema 中的全部字段。集合名应通过集合绑定变量传入，不应拼接到查询字符串中。

### query_params [Map]

静态 AQL 绑定变量。若 AQL 使用 `@@collection`，配置键应写成带引号的 `"@collection"`。

### batch_size [Integer]

游标单次网络请求最多传输的记录数，必须大于 0。

### use_ssl / verify_host [Boolean]

启用 TLS 并控制主机名校验。除隔离测试环境外，不应关闭主机名校验。

### connect_timeout_ms [Integer]

连接与请求超时时间，单位毫秒，必须大于 0。

### schema [Object]

AQL 结果的 SeaTunnel schema。连接器按照字段名从返回对象取值并完成类型转换。

## 示例

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

## 变更日志

<ChangeLog />
