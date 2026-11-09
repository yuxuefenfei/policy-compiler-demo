# 主机策略管理：全量 JSON 编译为 XML 的 Spring Boot 示例

## 定位

- 前端每次提交完整策略 JSON；后端 **重建**完整 XML，不再读取旧 XML 并做局部合并。
- `PolicyModuleHandler<T>` 负责一个模块的强类型解析后的业务验证与 XML 输出；`PolicyXmlCompiler` 负责处理器发现、路由、Bean
  Validation、XSD 和 SHA-256。
- 每次保存草稿生成不可变 `policy_revisions` 快照（同时保存前端 JSON 和代理 XML），使用条件更新防止并发覆盖。
- 发布仅移动 `publishedRevision` 指针并在同一个数据库事务写入 Outbox；真实终端通信需要另加消费 Outbox 的 worker。

这是一个用于讲解架构的 **最小可运行示例**，使用 H2 临时库。 **未实现登录鉴权、终端鉴权、审批流程、可靠的 Outbox
消费器和生产数据库迁移脚本。请勿原样用于生产。**

## 技术栈

- Java 21
- Spring Boot 3.5.16（用于示例兼容稳定的 Jackson 2 API；新生产项目请优先评估仍处于 OSS 支持期的 Spring Boot 4.x 迁移）
- Spring Web / Bean Validation / Spring Data JPA / H2
- JDK StAX XMLStreamWriter + JAXP XSD + SHA-256

## 运行

安装 JDK 21 和 Maven 3.6.3+ 后，在项目根目录执行：

```bash
mvn test
mvn spring-boot:run
```

默认监听 `http://localhost:8080`。H2 使用内存数据库，重启服务数据会清空。

## API 流程

1. 创建策略壳记录（版本从 0 开始）

```bash
curl -s -X POST http://localhost:8080/api/policies \
  -H 'Content-Type: application/json' \
  -d '{"name":"Windows办公终端基线"}'
# 返回 {"policyId":1,"draftRevision":0}
```

2. 使用 `examples/save-policy.json` 全量保存草稿（含全部四个模块，禁用时也要传 enabled=false）

```bash
curl -s -X PUT http://localhost:8080/api/policies/1/draft \
  -H 'Content-Type: application/json' \
  --data-binary @examples/save-policy.json
# 返回草稿版本 1 和 XML 的 sha256
```

3. 查询草稿，编辑下一版前应使用服务端返回的新版本号填写 `expectedRevision`

```bash
curl -s http://localhost:8080/api/policies/1/draft
```

4. 发布最新草稿（事务内同时写入 Outbox 待发送事件）

```bash
curl -s -X POST http://localhost:8080/api/policies/1/publish \
  -H 'Content-Type: application/json' \
  -d '{"expectedRevision":1}'
```

5. 模拟代理拉取 **已发布**的 XML（真实场景要加 mTLS / 身份授权 / 签名校验）

```bash
curl -i http://localhost:8080/api/policies/1/agent-xml
```

## 模块扩展

1. 在 `PolicyModels` 中定义新模块的不可变 DTO（`@NotNull`、`@Valid`、`@Size` 等）。
2. 新建 `@Component class XxxHandler implements PolicyModuleHandler<XxxConfig>`，实现 `key`、`order`、`configType`、
   `validateBusiness`、`writeXml`。
3. 为新的终端协议版本提供 `policy-v2.xsd` 和对应版本的编译器/注册表，避免在 v1 中直接增加必需模块导致旧版本前端请求失效。
4. 升级前端表单和终端代理的版本能力声明；可先灰度代理再灰度策略发布。

**重要：** 此示例只支持 `schemaVersion=1`，且要求恰好存在 `gateway`、`network`、`fileAudit`、`application` 四个模块。新增模块必须同步规划
XML 协议升级；生产建议按协议版本隔离 handler set、XSD 和验证规则。

## 为什么重新编译而不是改旧 XML

因为本接口是完整配置提交：保持一个确定性转换函数 `compile(JSON, schemaVersion)` 才能确保不留旧模块、旧规则和已删除配置。若未提交某模块，本示例会
**拒绝**请求，而不是默默保留旧值或当成禁用；这样防止大范围误关控制能力。规则列表内部的顺序仍沿用前端提交顺序，网络 ACL
等列表顺序应定义为正式协议的一部分。

## 建议生产部署时补齐

- 前端管理员的 RBAC、审批、双人复核、高危策略发布前的影响评估。
- 终端认证、mTLS、签名、租户隔离、agent 版本适配、回滚及发布范围。
- 使用 Flyway/Liquibase 建表；运行前把 `ddl-auto` 改成 `validate`。
- Outbox worker：事务外发送并重试，收件端按 `policyId + revision` 幂等处理；终端回报回执和失败原因。
- 在网关层设置 JSON 请求体大小与速率限制、全局跨模块策略冲突校验、日志脱敏；示例已经启用 JSON 严格重复键检测。
- XML 禁止不受信任的外部实体和外部 Schema；本示例仅从可信 DTO 写 XML，并禁用 XSD 外部资源加载。
- 当前计算的 SHA-256 用于一致性和下载校验，不代表身份认证；生产可签名或由可信传输绑定身份。
- 策略使用期建议明确 `deny/allow` 的默认动作优先级、路径大小写/规范化和执行主体识别规则。

## 示例验证说明

此项目结构、XML Schema 与请求样例可以静态检查；构建需要安装 Maven 并下载依赖。作者环境若无 Maven，不应声称已运行集成测试。
