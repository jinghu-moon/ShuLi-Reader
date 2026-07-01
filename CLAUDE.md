# CLAUDE.md

## 工具使用约束

### Write 工具
- 对已有文件，优先使用 Edit 工具而非 Write
- 如必须使用 Write 覆写已有文件，必须在同一会话中先 Read 该文件
- 长对话上下文压缩后，早期 Read 可能失效，需重新 Read 再 Write
- 创建新文件时 Write 可直接使用，无需先 Read
