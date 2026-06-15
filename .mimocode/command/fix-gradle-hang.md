---
description: 修复 Gradle 构建挂起问题，包括终止 Java 进程、清理构建目录、重启 Gradle 守护进程
---

# 修复 Gradle 挂起

当 Gradle 构建或测试挂起时，执行以下步骤恢复：

## 执行步骤

### 1. 检查 Java 进程

```bash
tasklist 2>/dev/null | grep -i java
```

### 2. 终止 Java 进程

```bash
taskkill //F //IM java.exe 2>&1 || echo "没有找到 Java 进程"
```

### 3. 停止 Gradle 守护进程

```bash
./gradlew --stop 2>&1
```

### 4. 清理构建目录

```bash
rm -rf app/build 2>&1
```

### 5. 重新运行构建/测试

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -50
```

## 注意事项

- 在 Windows 上使用 `taskkill //F //IM java.exe`
- 清理构建目录可以解决缓存问题
- 如果问题持续，检查 `gradle.properties` 中的内存配置
