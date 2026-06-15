---
name: code-refactoring-execution
description: 执行代码重构计划，包括读取重构方案、设置工作分支、执行子任务、运行测试验证
---

# 代码重构执行技能

用于执行代码重构计划，按照重构方案文档逐步完成代码重构任务。

## 使用场景

- 执行 `docs/23-large-file-split-refactor.md` 等重构计划
- 将大型文件拆分为多个小型、职责单一的文件
- 重构过程中保持功能不变

## 工作流程

### 1. 读取重构计划

```
Read: docs/<重构计划文件>.md
→ 理解重构目标、范围、步骤、依赖关系
```

### 2. 设置工作环境

```
# 创建工作分支（如果需要）
Bash: git checkout -b refactor/<迭代名称>

# 读取目标源文件
Read: <需要重构的源文件>
→ 理解当前代码结构
```

### 3. 执行重构子任务

对每个重构子任务：

```
1. 理解子任务目标
2. Read: 读取相关源文件
3. Edit: 执行代码修改
4. Write: 创建新文件（如果需要）
5. Bash: 编译验证
   ./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

### 4. 运行测试验证

```
# 运行受影响的测试
Bash: ./gradlew :app:testDebugUnitTest --tests "<相关测试类>" 2>&1 | tail -20

# 如果测试失败，分析原因并修复
```

### 5. 提交变更

```
# 检查变更
Bash: git status
Bash: git diff

# 提交（遵循项目约定）
Bash: git add -A
Bash: git commit -m "refactor(scope): 描述重构内容"
```

## 注意事项

- 每次重构后立即编译验证
- 保持功能不变，只改变代码结构
- 遵循项目的 SRP（单一职责原则）
- 目标文件大小约 400 行以内
- 重构过程中允许 breaking changes（pre-release 阶段）

## 示例命令

```bash
# 编译验证
./gradlew :app:compileDebugKotlin 2>&1 | tail -20

# 运行单元测试
./gradlew :app:testDebugUnitTest 2>&1 | tail -50

# 检查文件行数
wc -l app/src/main/java/com/shuli/reader/feature/reader/ReaderViewModel.kt
```
