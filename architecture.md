# 设计说明（全量提交 JSON → XML）

## 核心链路

```text
管理前端（完整 JSON）
         |
         v
PUT /api/policies/{id}/draft
         |
         v
PolicyService（检查 expectedRevision）
         |
         v
PolicyXmlCompiler
  ├── 校验 schemaVersion 和 modules 集合
  ├── Registry：handler.key → 对应 Handler
  ├── JsonNode → 具体 DTO，拒绝未知字段
  ├── Bean Validation（嵌套验证）
  ├── Handler 业务校验（规则 ID 唯一，IPv4 检查）
  ├── StAX 按固定顺序完整输出 XML
  ├── XSD 校验，确保终端协议符合规范
  └── SHA-256 生成内容摘要
         |
         v
CAS 更新 policies.draft_revision
  + 插入 policy_revisions（不可变 JSON + XML）
         |
         v
POST /api/policies/{id}/publish
  ├── CAS 移动 policies.published_revision
  └── 同事务插入 outbox（PENDING）
         |
         v
Outbox worker（实际项目添加）→ MQ/推送/代理同步
                         或
GET /api/policies/{id}/agent-xml → 终端只获取发布版本
```

## 最重要的不变式

- 一次保存必须包含当前协议中的 **所有模块**；模块关闭也用显式 `enabled: false`，缺少模块意味着请求错误。
- **不能读取旧 XML 然后保留未提交节点**：这种行为与全量提交语义冲突，会产生已经删除却仍被下发的旧控制规则。
- 只有当完整 XML 的校验、版本检查和数据库事务全都成功，新草稿才可见。
- 后台发布时不重新编译 XML，而是指向已经校验过、不可变的 revision 快照，保证发布内容与审批的内容完全一致。
- 代理只看到 `publishedRevision`，不应直接读取最新 `draftRevision`。
- XML 头部的 `schemaVersion` 是终端协议版本，不是策略的业务修订号，不能混用。

## 数据库模型

```text
policies
    id PK
    name
    draft_revision                  -- 乐观锁计数
    published_revision NULLABLE    -- 当前发布快照
    updated_at

policy_revisions
    id PK
    policy_id, revision_no UNIQUE
    config_json                    -- 可编辑业务配置快照
    xml_content                    -- 终端可消费的完整快照
    sha256
    created_at

policy_outbox
    id PK
    event_key UNIQUE               -- policyId:revision
    event_type                     -- POLICY_PUBLISHED
    payload                        -- policyId/revision/sha256
    status                         -- PENDING / SENT / FAILED（需实现 worker）
    created_at
```

## 后续技术演进

- `schemaVersion=2`：独立 `PolicyCompilerV2`、XSD v2 和对应模块映射，避免编辑 v1 策略时自动改写未知旧结构。
- 如果模块很多：按模块拆 DTO/Handler，按业务边界拆校验器，提供模块元数据给前端生成表单；注意不可把复杂业务规则简化成任意路径映射。
- 如果策略体积/下发频率很大：XML 可改存对象存储，数据库只保留 URL、sha256、版本与审计；用流式生成、压缩和 CDN
  分发；不要在应用层反复读取大文本拼接。
- 对已有代理 XML 的迁移：先基于真实线上 XML 制作金标测试（golden files），逐字段核对映射和默认值，不能为了改造成全量 JSON
  误删代理仍依赖的标签。
- 若策略必须发布到部分终端或按组织生效：另建 `policy_assignment(policy_id, target_id, desired_revision)` 与
  `agent_ack(agent_id, policy_id, applied_revision, result)`，而不是把目标清单塞进 XML 主体。
