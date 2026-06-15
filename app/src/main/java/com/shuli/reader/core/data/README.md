# core/data 职责说明

> 生成时间：2026-06-15
> 目的：明确数据层边界，防止新增代码随手落到错误目录

## 目标结构

```
core/data/
├── preferences/  # DataStore / 用户偏好模型
│   ├── UserPreferences.kt      # 全局用户偏好
│   ├── ReaderPreferences.kt    # 阅读器偏好
│   └── ...
├── database/     # Room database / dao / entity / migration
│   ├── ShuLiDatabase.kt        # Room 数据库定义
│   ├── dao/                    # DAO 接口
│   ├── entity/                 # Entity 定义
│   └── ...
└── repository/   # 跨数据源聚合查询、读写门面
    ├── BookContentRepository.kt
    ├── BookImportRepository.kt
    ├── BookQueryRepository.kt
    ├── FolderRepository.kt
    ├── ReadingProgressRepository.kt
    ├── SearchIndexRepository.kt
    ├── TagRepository.kt
    └── ...
```

## 当前过渡结构（2026-06-15）

目前 `core/data/` 只包含偏好模型：
- `UserPreferences.kt`
- `ReaderPreferences.kt`

以下目录尚未移动到 `core/data/`：
- `core/database/` → 目标：`core/data/database/`
- `core/repository/` → 目标：`core/data/repository/`

## 职责边界

### preferences/
- DataStore 偏好存储
- 用户偏好模型定义
- 偏好序列化/反序列化

### database/
- Room 数据库定义
- DAO 接口
- Entity 定义
- 数据库迁移脚本

### repository/
- 跨数据源聚合查询
- 读写门面
- 业务逻辑封装

## 注意事项

1. 不要在 `core/data/` 中放置业务逻辑
2. Repository 可以依赖 DAO，但不要反向依赖
3. Entity 应该是纯数据类，不要包含业务方法
4. 偏好模型应该是不可变的（data class）
