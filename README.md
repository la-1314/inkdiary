# InkDiary 墨水日记

一款支持笔画顺序手写动画的 AI 日记伴侣应用，支持中文和英文。

用笔在屏幕上书写，停顿后你的字迹会淡去，一段回复会以正确的笔画顺序一笔一划地浮现，然后慢慢淡去。

没有聊天界面，只有纸上的墨水。

## 功能特性

- **笔画顺序动画** —— 回复内容按正确书写顺序逐笔绘制，中文使用 [Make Me a Hanzi](https://github.com/skishore/makemeahanzi) 数据（笔画顺序经过人工核验），英文使用 Zhang-Suen 骨架细化算法
- **可配置 AI 人设** —— 默认是一位温暖、真诚的知心朋友，可在设置页自定义
- **记忆驱动成长** —— 日记会记住你的对话内容并作为上下文注入，随着交流越多越了解你
- **兼容 OpenAI 接口** —— 支持 OpenAI、OpenRouter、Groq、本地服务器等任何 OpenAI 兼容接口
- **三指长按 5 秒** —— 在日记页用手势进入设置界面
- **首次启动引导** —— 未配置 API Key 时自动进入设置页

## 工作原理

```
笔/触控输入 (MotionEvent, 支持压感)
   │ 笔迹
   ▼
InkCanvasView ── 停顿 2.8 秒 → 提交页面 → PNG
   │
   ▼
OracleClient (OpenAI 兼容 /chat/completions, SSE 流式)
   │  从 PNG 读取手写内容 (视觉大模型)
   │  逐句流式返回回复
   ▼
StrokeAnimator 笔画动画引擎
   ├── 中文字 → Make Me a Hanzi SVG 路径 + medians
   └── 英文字 → 手写字体 → 栅格化 → Zhang-Suen 细化 → 追踪
   │
   ▼ 逐笔动画绘制到画布
InkCanvasView
```

### 中文笔画数据：三层回退策略

| 层级 | 数据来源 | 覆盖范围 | 特点 |
|---|---|---|---|
| 第一层 本地 | `hanzi_common.json`（打包进 APK） | ~4000 常用字 | 离线可用，笔顺权威 |
| 第二层 CDN | jsDelivr `hanzi-writer-data` 按需获取 | 9000+ 字 | 首次联网获取，缓存后离线可用 |
| 第三层 算法 | Zhang-Suen 细化系统字体 | 任意汉字 | 笔顺启发式（非权威），保证总能动画 |

## 编译构建

环境要求：Android Studio Hedgehog+（AGP 8.1）、JDK 17、Android SDK 34。

```sh
git clone https://github.com/la-1314/inkdiary.git
cd inkdiary

# 1. 下载 Dancing Script 字体
mkdir -p app/src/main/assets/fonts
curl -L -o app/src/main/assets/fonts/DancingScript-Regular.ttf \
  "https://github.com/googlefonts/DancingScript/raw/main/fonts/ttf/DancingScript-Regular.ttf"

# 2. 生成 Gradle Wrapper
gradle wrapper --gradle-version 8.2

# 3. 编译
./gradlew assembleDebug
```

### 汉字笔画数据

应用内置约 80 个常用字的演示数据（`hanzi_subset.json`）。获取完整常用字数据：

```sh
bash scripts/download_hanzi_data.sh
```

这会下载 Make Me a Hanzi 的 `graphics.txt` 并生成 `hanzi_common.json`（约 4000 常用字）。

CI 工作流会自动完成这一步。生僻字在运行时从 CDN 按需获取。

## 配置说明

首次启动自动进入设置页。之后可在日记页**三指长按 5 秒**进入设置。

| 字段 | 说明 | 默认值 |
|---|---|---|
| API Key | OpenAI 兼容 API 密钥 | （必填）|
| Base URL | API 端点地址 | `https://api.openai.com/v1` |
| Model | 支持视觉的模型名 | `gpt-4o-mini` |
| Persona | 定义 AI 性格的系统提示词 | 温暖朋友模板 |

内置 OpenAI / OpenRouter / Gemini 一键预设。

## 手势操作

| 操作 | 效果 |
|---|---|
| 书写后抬笔 | 日记吸收墨水并回复 |
| 三指长按 5 秒 | 进入设置界面 |
| 五指同时点按 | 退出日记 |

## 记忆系统

每一轮对话都会存储在本地：
- 你的笔迹（坐标数组）
- 你写的内容转录（从 LLM 回复解析）
- AI 的回复文本

最近 6 轮记忆会注入每次请求的上下文，让日记自然地了解你。

## 技术栈

- **语言**：Kotlin
- **最低 SDK**：24（Android 7.0）
- **目标 SDK**：34
- **网络**：OkHttp 4（SSE 流式）
- **异步**：Kotlin Coroutines
- **UI**：纯 Android Views + Canvas

## 许可证

本仓库所有代码使用 MIT 许可证。

- 中文笔画数据：[Arphic Public License](https://github.com/skishore/makemeahanzi/blob/master/APL/ARPHICPL.TXT)（来自 Make Me a Hanzi）
- Dancing Script 字体：[SIL OFL 1.1](https://scripts.sil.org/OFL)（来自 Google Fonts）

## 致谢

- [riddle](https://github.com/MaximeRivest/riddle) —— 原始灵感来源
- [Make Me a Hanzi](https://github.com/skishore/makemeahanzi) —— 中文笔画数据
- [HanziWriter](https://hanziwriter.org) —— 动画参考实现
- [Tegaki](https://github.com/KurtGokhan/tegaki) —— Zhang-Suen 流水线参考
