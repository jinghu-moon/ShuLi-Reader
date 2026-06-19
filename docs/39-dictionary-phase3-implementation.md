# 39 - 划词查词 Phase 3 实施方案

> 编写时间：2026-06-19
> 前置依赖：Phase 1 + Phase 2 已完成
> 目标：完成剩余所有任务，达到生产可用状态

---

## 1. Phase 1 & 2 完成状态总结

### Phase 1（MVP）✅ 已完成

| 任务 | 状态 | 备注 |
|------|------|------|
| 字符级命中 `selectWordAt()` | ✅ | CJK 前向最大匹配 + 英文词边界 |
| Stardict 解析器 | ✅ | ifo/idx/dict 完整实现 |
| `.dict.dz` 随机访问 | ✅ | DictZipReader 小端序解析 |
| mmap + offset table 索引 | ✅ | StardictIndex 零对象二分查找 |
| Room 表（3 张） | ✅ | dict_meta + dict_history + word_book |
| 词典查询引擎 | ✅ | LRU 缓存 + 分层超时 + 取消支持 |
| 选区菜单「查词」按钮 | ✅ | 分隔线 + 图标 |
| 查词结果 BottomSheet | ✅ | M3 ModalBottomSheet |
| CC-CEDICT 渲染 | ✅ | 繁简分离 + 拼音声调 |
| Stardict HTML 渲染 | ✅ | Jsoup → AnnotatedString |
| 中文前向最大匹配 | ✅ | WordNormalizer |
| 英文词形还原 | ✅ | EnglishStemmer |
| i18n 扩展 | ✅ | zh-CN/zh-TW/en |

### Phase 2（MDX + 管理）✅ 已完成

| 任务 | 状态 | 备注 |
|------|------|------|
| MDX 解析引擎 | ✅ | mdict 模块完整实现 |
| 用户导入词库（SAF） | ✅ | 支持 MDX + Stardict 多选 |
| 词库管理界面 | ✅ | 列表 + 启用/禁用 + 删除 |
| MDX 渲染管线 | ✅ | DictStyleResolver + DictMdxRenderer |
| MDX 词典内跳转 | ✅ | entry:// 链接可点击 |
| 选区扩展把手 | ✅ | 拖动调整选区范围 |
| 生词本界面 | ✅ | 列表 + 搜索 + 删除 |
| Anki TSV 导出 | ✅ | SAF 写文件 |
| 法律声明 | ✅ | AboutSection 声明 |

---

## 2. Phase 3 剩余任务清单

### 2.1 功能增强（高优先级）

| # | 任务 | 复杂度 | 预计工时 |
|---|------|--------|----------|
| 1 | 查词历史记录（持久化 + 列表页 + footer 入口） | 中 | 2h |
| 2 | 词头前缀匹配（联想建议） | 低 | 1h |
| 3 | 多词典 Tab 切换（HorizontalPager + ScrollableTabRow） | 中 | 3h |
| 4 | 词典徽章粘性吸顶（Sticky Header） | 低 | 1h |
| 5 | 防遮挡智能滚动（下半屏选词自动上滚） | 中 | 2h |
| 6 | 词头标题自适应字号（长句动态缩小） | 低 | 0.5h |
| 7 | 上下文释义展示（显示 contextSentence） | 低 | 0.5h |
| 8 | 高频词过滤（停用词表） | 低 | 1h |
| 9 | 未找到空态（前缀建议 + 繁简转换 + 复制该词） | 中 | 2h |
| 10 | footer 三按钮（生词本 + 复制释义 + 历史） | 低 | 1h |
| 11 | 可编辑搜索框（默认只读，点击编辑） | 中 | 2h |
| 12 | TTS 朗读词头 | 低 | 0.5h |

### 2.2 格式扩展（中优先级）

| # | 任务 | 复杂度 | 预计工时 |
|---|------|--------|----------|
| 13 | `.syn` 同义词支持 | 中 | 2h |
| 14 | 模糊匹配（Levenshtein ≤ 1） | 中 | 2h |
| 15 | MDX 索引文件缓存 | 低 | 1h |
| 16 | MDD 资源加载（CSS + 图片） | 中 | 3h |

### 2.3 UI 升级（低优先级）

| # | 任务 | 复杂度 | 预计工时 |
|---|------|--------|----------|
| 17 | 浮动气泡菜单（方向自适应） | 高 | 4h |
| 18 | 嵌套滑动展开（Nested Scrolling） | 中 | 2h |
| 19 | 内置成语词典 | 低 | 1h |

### 2.4 工具链（可选）

| # | 任务 | 复杂度 | 预计工时 |
|---|------|--------|----------|
| 20 | 萌典转换工具 | 中 | 3h |
| 21 | 大体积词库 Wi-Fi 传书导入 | 高 | 4h |

---

## 3. Phase 3 实施步骤

### Step 1: 查词历史记录（2h）

**新增文件：**
- `feature/settings/dictionary/DictHistoryScreen.kt` - 历史列表页
- `feature/settings/dictionary/DictHistoryViewModel.kt` - 历史状态

**修改文件：**
- `feature/reader/dictionary/DictionaryBottomSheet.kt` - footer 添加「历史」按钮
- `feature/settings/SettingsSubScreenNavigation.kt` - 添加历史页路由
- `feature/settings/SettingsScreen.kt` - 词典管理页添加历史入口

**实现要点：**
- 每次查词写入 `dict_history` 表（词头 + 时间戳 + 是否命中 + 来源词典）
- 列表按时间倒序，显示词头 + 查询时间 + 来源词典
- 支持左滑删除、底部「清空全部」按钮
- 点击历史条目可重新触发查词

### Step 2: 多词典 Tab 切换（3h）

**修改文件：**
- `feature/reader/dictionary/DictionaryBottomSheet.kt`

**实现要点：**
- 使用 `HorizontalPager` + `ScrollableTabRow` 联动
- 仅 1 本词典命中时不显示 Tab
- Tab 状态独立保持（滚动位置）
- Tab 样式：Material 3，未选中半透明

### Step 3: 防遮挡智能滚动（2h）

**修改文件：**
- `feature/reader/screen/ReaderScreen.kt`
- `feature/reader/screen/ReaderViewModel.kt`

**实现要点：**
- 触发查词时获取选区 Y 坐标
- 如果选中词在下半屏（y > screenHeight * 0.45），自动上滚 Canvas
- 滚动量 = 选区 Y - 阈值 + 行高 * 2

### Step 4: 未找到空态（2h）

**修改文件：**
- `feature/reader/dictionary/DictionaryBottomSheet.kt`

**实现要点：**
- 虚线圆环图标 + 「未找到释义」
- 前缀匹配建议（最多 3 条）
- 繁简转换建议（使用 OpenCC4J）
- 「复制该词」按钮
- 引导文案：「可在设置 → 词典管理中导入更多词库」

### Step 5: 可编辑搜索框（2h）

**修改文件：**
- `feature/reader/dictionary/DictionaryBottomSheet.kt`

**实现要点：**
- 默认只读大字展示（不获焦、不弹键盘）
- 点击进入编辑态（TextField 获焦 + 弹出软键盘）
- 编辑后按回车或失去焦点触发新查询
- 支持连续查词（踟蹰 → 踯躅 → 踌躇）

### Step 6: 高频词过滤（1h）

**新增文件：**
- `core/dictionary/engine/StopWords.kt` - 停用词表

**修改文件：**
- `feature/reader/screen/ReaderViewModel.kt`

**实现要点：**
- 维护约 200 个高频虚词停用词表
- 命中时仍正常显示查词结果
- 仍写入查词历史
- 不自动加入生词本

### Step 7: 词头标题自适应字号（0.5h）

**修改文件：**
- `feature/reader/dictionary/DictionaryBottomSheet.kt`

**实现要点：**
- ≤4 字：28sp
- ≤6 字：24sp
- ≤10 字：20sp
- \>10 字：17sp
- 取消 maxLines = 1，允许换行（最多 2 行）

### Step 8: 上下文释义展示（0.5h）

**修改文件：**
- `feature/reader/dictionary/DictionaryBottomSheet.kt`

**实现要点：**
- 如果有 contextSentence，在标题下方显示
- 样式：斜体浅灰，带「来自：」前缀

### Step 9: footer 三按钮（1h）

**修改文件：**
- `feature/reader/dictionary/DictionaryBottomSheet.kt`

**实现要点：**
- 「＋ 生词本」主按钮（填充色）
- 「⎘ 复制释义」次按钮（描边）
- 「📋 历史」次按钮（描边）

### Step 10: TTS 朗读词头（0.5h）

**修改文件：**
- `feature/reader/dictionary/DictionaryBottomSheet.kt`

**实现要点：**
- 使用 Android TextToSpeech API
- 词头旁显示喇叭图标
- 点击朗读

### Step 11: `.syn` 同义词支持（2h）

**新增文件：**
- `core/dictionary/engine/SynIndex.kt`

**修改文件：**
- `core/dictionary/engine/StardictParser.kt`
- `core/dictionary/engine/DictLookupEngine.kt`

**实现要点：**
- 解析 .syn 文件（synonym_word + original_word_index）
- 查询时先在 .idx 查找，未命中则在 .syn 查找
- 命中同义词后用 original_word_index 定位到 .idx 词条
- 结果标注「同义词」

### Step 12: 模糊匹配（2h）

**新增文件：**
- `core/dictionary/engine/FuzzyMatcher.kt`

**修改文件：**
- `core/dictionary/engine/DictLookupEngine.kt`

**实现要点：**
- Levenshtein 编辑距离 ≤ 1
- 仅对词头长度 ≤ 6 时启用
- 利用 idx 有序性做范围扫描
- 返回最多 3 个候选，标注「你是不是要找」

### Step 13: 词头前缀匹配（1h）

**修改文件：**
- `core/dictionary/engine/DictLookupEngine.kt`
- `feature/reader/dictionary/DictionaryBottomSheet.kt`

**实现要点：**
- 精确匹配失败时调用 prefixSearch()
- 显示「你是不是要找：XXX？」建议列表
- 最多 3 条建议

### Step 14: 浮动气泡菜单（4h）

**修改文件：**
- `feature/reader/screen/ReaderScreen.kt`
- `feature/reader/screen/ReaderOverlayPanels.kt`

**实现要点：**
- 从固定底栏升级为浮动气泡
- 方向自适应：上方空间不足时改为下方
- 水平边界 clamp：选中词在屏幕最左/右时调整位置
- 带向下小三角（或向上，取决于方向）

### Step 15: 内置成语词典（1h）

**数据准备：**
- 下载 CC 协议的成语词典 Stardict 文件
- 打包到 assets/dictionaries/

**修改文件：**
- `core/dictionary/manager/BuiltinDictInstaller.kt`

---

## 4. 建议实施顺序

```
Step 1:  查词历史记录           ← 用户留存
Step 2:  多词典 Tab 切换        ← 多词典体验
Step 3:  防遮挡智能滚动         ← 阅读体验
Step 4:  未找到空态             ← 兜底体验
Step 5:  可编辑搜索框           ← 连续查词
Step 6:  高频词过滤             ← 生词本质量
Step 7:  词头标题自适应字号     ← UI 细节
Step 8:  上下文释义展示         ← UI 细节
Step 9:  footer 三按钮          ← 功能完整
Step 10: TTS 朗读词头           ← 辅助功能
Step 11: .syn 同义词支持        ← 查询增强
Step 12: 模糊匹配               ← 查询增强
Step 13: 词头前缀匹配           ← 查询增强
Step 14: 浮动气泡菜单           ← UI 升级
Step 15: 内置成语词典           ← 内容增强
```

---

## 5. 验证方式

| 步骤 | 验证 |
|------|------|
| Step 1 | 查词 → 点击历史按钮 → 列表显示 → 点击条目重新查词 |
| Step 2 | 导入 2+ 词典 → 查词 → Tab 显示 → 左右滑动切换 |
| Step 3 | 下半屏选词 → 查词 → Canvas 自动上滚 → 选中词可见 |
| Step 4 | 查不存在的词 → 空态显示 → 前缀建议可用 → 复制按钮可用 |
| Step 5 | 点击搜索框 → 键盘弹出 → 输入新词 → 回车查询 |
| Step 6 | 查高频词（于是、忽然）→ 结果正常显示 → 不自动加入生词本 |
| Step 7 | 查长句（10+ 字）→ 标题字号缩小 → 不截断 |
| Step 8 | 查词带上下文 → 「来自：」显示正确 |
| Step 9 | footer 三按钮 → 生词本/复制/历史功能正常 |
| Step 10 | 点击喇叭图标 → TTS 朗读词头 |
| Step 11 | 查同义词 → .syn 命中 → 结果标注「同义词」 |
| Step 12 | 查相近词（踟躇 vs 踟蹰）→ 模糊匹配建议 |
| Step 13 | 查不存在的词 → 前缀匹配建议显示 |
| Step 14 | 选中词在屏幕顶端 → 菜单在下方弹出 |
| Step 15 | 查成语 → 成语词典释义显示 |

---

## 6. 预计总工时

| 类别 | 工时 |
|------|------|
| 功能增强（Step 1-12） | 18h |
| UI 升级（Step 13-14） | 5h |
| 内容增强（Step 15） | 1h |
| **总计** | **24h** |

建议分 3-4 个 PR 完成：
- PR 1: Step 1-5（核心功能增强）
- PR 2: Step 6-10（UI 细节完善）
- PR 3: Step 11-13（查询增强）
- PR 4: Step 14-15（UI 升级 + 内容增强）
