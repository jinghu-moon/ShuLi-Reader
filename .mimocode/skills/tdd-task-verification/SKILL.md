---
name: tdd-task-verification
description: 验证 TDD 任务清单的完成情况，检查代码实现是否符合任务要求，运行测试验证，修复发现的问题
---

# TDD 任务验证技能

用于验证 TDD 任务清单的完成情况，确保所有标记为已完成的任务真正实现且正确。

## 使用场景

- 检查 TDD 任务清单（如 `docs/28-stats-screen-tdd-tasks.md`）的完成状态
- 验证代码实现是否符合任务描述
- 运行相关测试确认功能正确
- 修复发现的实现问题

## 工作流程

### 1. 读取任务清单

```
Read: docs/<任务清单文件>.md
→ 理解所有任务的描述、测试要求、实现要求
```

### 2. 扫描项目结构

```
Bash: find app/src -name "*.kt" | head -50
→ 了解项目源文件结构
```

### 3. 逐一验证任务

对每个标记为 `[x]` 的任务：

```
1. Read: 读取任务描述的测试文件
   → 检查测试代码是否存在且完整

2. Read: 读取任务描述的实现文件
   → 检查实现代码是否存在且符合要求

3. Grep: 搜索关键类/函数名
   → 确认实现已到位

4. Bash: 运行相关测试
   ./gradlew :app:testDebugUnitTest --tests "<测试类名>" 2>&1 | tail -20
   → 确认测试通过
```

### 4. 修复问题

如果发现实现不完整或测试失败：

```
1. 分析失败原因
2. Edit: 修复实现代码
3. Bash: 重新运行测试验证
4. 记录修复内容
```

### 5. 生成报告

输出验证结果：
- 已完成且正确的任务数
- 需要修复的任务数及修复内容
- 未完成的任务数

## 注意事项

- 遵循 TDD 节奏：Red → Green → Refactor
- 不修改测试代码来适配实现（应修复实现）
- 保持测试的独立性和可重复性
- 使用项目约定的测试框架（JUnit 4 + MockK + Coroutines Test）

## 示例命令

```bash
# 运行单个测试类
./gradlew :app:testDebugUnitTest --tests "com.shuli.reader.core.database.entity.ReadingSessionEntityTest"

# 运行某个包的所有测试
./gradlew :app:testDebugUnitTest --tests "com.shuli.reader.feature.stats.*"

# 运行所有测试
./gradlew :app:testDebugUnitTest 2>&1 | tail -50
```
