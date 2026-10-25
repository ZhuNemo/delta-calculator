# Delta 草稿纸计算器

> 离线优先的 Android 数学草稿纸 —— 把草稿纸、精确分数和科学计算器装进口袋。

![Build APK](https://github.com/ZhuNemo/delta-calculator/actions/workflows/android.yml/badge.svg)

![License](https://img.shields.io/github/license/ZhuNemo/delta-calculator?color=blue)

![Platform](https://img.shields.io/badge/Platform-Android%208%20\(API%2026\)%2B-green)

![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF)

![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.12.01-4285F4)

竖屏是经典计算器，横屏自动切成科学计算器；输入直接用自绘数学键盘，公式由 MathLive 实时排版，结果以**精确分数**给出；内置一元二次方程与二元一次方程组求解。

Kotlin + Jetpack Compose + Material 3，无登录、无广告、**无 INTERNET 权限**，整套应用完全离线可用。

> 本文档含 AI 生成内容，技术细节以代码为准。

---

## 目录

- [核心特性](#核心特性)
- [技术栈](#技术栈)
- [构建与安装](#构建与安装)
  - [环境要求](#环境要求)
  - [本地构建（可选）](#本地构建可选)
  - [GitHub Actions / Release](#github-actions--release)
- [目录结构](#目录结构)
- [关键实现](#关键实现)
  - [精确分数，而不是 Double](#精确分数而不是-double)
  - [LaTeX 输入模型](#latex-输入模型)
  - [两套解析器](#两套解析器)
  - [MathLive 离线渲染](#mathlive-离线渲染)
  - [数据层](#数据层)
- [键盘布局](#键盘布局)
- [已知限制](#已知限制)
- [Roadmap](#roadmap)
- [许可证](#许可证)

---

## 核心特性

| 模块    | 说明                                                  |
| ----- | --------------------------------------------------- |
| 基础计算  | 四则运算、括号、幂、开方、分数、π、e、三角/对数函数                         |
| 精确结果  | 所有有理运算走 `BigInteger` 分数，`0.1+0.2` 得到 `3/10` 而不是浮点尾数 |
| 实时渲染  | MathLive 打包进 APK，输入即排版，完全离线                         |
| 自定义键盘 | 竖屏 4×5 数字键盘 + 快捷条；横屏 7×5 科学键盘；按键样式可换                |
| 一元二次  | 判别式、根式化简、有理根 / 无理根 / 复根，输出求解步骤                      |
| 二元一次  | 克拉默法则，精确分数解，自动判断无解 / 无穷多解                           |
| 历史记录  | Room 本地存储，点击回填，单条删除 / 一键清空                          |
| 主题    | 种子色派生整套配色、浅色/深色/跟随系统、动态取色、圆角、字号                     |

---

## 技术栈

| 类别   | 选型                                                         |
| ---- | ---------------------------------------------------------- |
| 语言   | Kotlin 2.0.21                                              |
| UI   | Jetpack Compose（BOM 2024.12.01）+ Material 3                |
| 架构   | 单 Activity + ViewModel + 手写轻量依赖容器（`DeltaApp.kt`）           |
| 本地存储 | Room 2.6.1（历史记录）+ DataStore 1.1.1（主题与偏好）                   |
| 公式渲染 | MathLive（打包进 APK 的 `assets`，离线）                            |
| 构建   | AGP 8.7.3 / Gradle 8.11.1 / JDK 17 / Node 20（仅拉取 MathLive） |
| 平台   | minSdk 26 · targetSdk 35 · compileSdk 35                   |
| 权限   | 无任何网络权限，无统计 / 广告 SDK                                       |

当前版本：`0.1.0`（versionCode 1）。

---

## 构建与安装

### 环境要求

- **JDK 17**
- **Android SDK**：`compileSdk 35` 对应的 platform 与 build-tools
- **Node 20**：仅用于把 MathLive 拉取到 `assets`（见下）
- 推荐走 GitHub Actions 直接出包，无需本地装齐上述环境

### 本地构建（可选）

```bash
# 1. 把 MathLive 拉到 assets，只需跑一次（已有则自动跳过）
node tools/fetch-mathlive.mjs

# 2. 生成 gradlew（仓库不提交 wrapper jar）
gradle wrapper

# 3. 打包 debug APK
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

> `MathLive` 的 `dist` 不打进仓库（约数 MB），由 `tools/fetch-mathlive.mjs` 在构建前从 npmmirror / npmjs 拉取，保证 APK 里的资源是完整的、离线的。

### GitHub Actions / Release

推到 `main`、发 `v*` tag，或手动触发 `Build APK`：

1. **Actions → `Build APK` → `Run workflow`**
2. 跑完在 Summary 底部下载 artifact `delta-calculator-debug`
3. 打 `v*` tag 会自动创建 Release 并附上 APK

流水线做的事：`checkout → JDK 17 → Node 20 → 拉 MathLive → 跑单元测试 → assembleDebug → 上传 APK`。

---

## 目录结构

```
delta-calculator/
├─ .github/workflows/android.yml      # CI：测试 + 构建 + 发 Release
├─ gradle/libs.versions.toml          # 版本目录
├─ tools/fetch-mathlive.mjs           # 下载 MathLive dist 到 assets
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml          # 刻意不声明 INTERNET
│     ├─ assets/mathlive/
│     │  ├─ index.html                # MathLive 宿主页（透明背景、只读）
│     │  └─ vendor/                   # 构建时生成，不入库
│     ├─ java/com/nemo/deltacalc/
│     │  ├─ DeltaApp.kt               # 手写依赖容器
│     │  ├─ MainActivity.kt           # 单 Activity + 三屏切换
│     │  ├─ core/
│     │  │  ├─ Rational.kt            # 精确有理数 + 根式化简
│     │  │  ├─ Num.kt                 # 精确/近似统一数值类型
│     │  │  ├─ LatexLexer.kt          # LaTeX 子集词法分析
│     │  │  ├─ ExpressionEngine.kt    # 递归下降求值
│     │  │  ├─ Poly.kt                # 方程解析 + 多项式运算
│     │  │  ├─ EquationSolver.kt      # 一元二次 / 二元一次
│     │  │  └─ LatexPretty.kt         # LaTeX → Unicode（历史列表用）
│     │  ├─ data/
│     │  │  ├─ History.kt             # Room Entity/DAO/Database/Repository
│     │  │  └─ Settings.kt            # DataStore 主题与偏好
│     │  ├─ ui/
│     │  │  ├─ CalculatorScreen.kt    # 主界面（横竖屏两套布局）
│     │  │  ├─ HistoryScreen.kt
│     │  │  ├─ SettingsScreen.kt
│     │  │  ├─ components/
│     │  │  │  ├─ MathFormulaView.kt  # WebView + MathLive 桥
│     │  │  │  └─ Keypad.kt           # 键盘布局与按键
│     │  │  └─ theme/Theme.kt         # 种子色 → ColorScheme
│     │  └─ vm/CalculatorViewModel.kt
│     └─ res/                         # 主题、图标、备份规则
└─ app/src/test/java/.../ExactMathTest.kt
```

---

## 关键实现

### 精确分数，而不是 Double

`Rational` 内部是 `BigInteger` 分子/分母，构造时约分、分母恒正。`Num` 是「精确或近似」的联合体：只要参与运算的双方都精确，结果就保持分数；遇到 `π`、`sin` 这类无理量才退化成 `Double`。

所以：

- `1/3 + 1/6` → `1/2`
- `0.1 + 0.2` → `3/10`
- `√16 + √(1/4)` → `9/2`
- `2/3 的平方` → `4/9`

### LaTeX 输入模型

键盘不产生普通字符，而是直接插入 LaTeX 片段（`\frac{}{}`、`\sqrt{}`、`^{}`、`\times`…），公式区渲染的就是真实输入。结构键会插入 `\placeholder{}` 占位：

1. 按 `a/b` → `\frac{\placeholder{}}{\placeholder{}}`
2. 再按 `3` → 填进第一个占位符：`\frac{3}{\placeholder{}}`
3. 按 `4` → `\frac{3}{4}`

退格按「结构」删除：整个 `\frac{…}{…}`、`\sqrt{…}`、`^{…}`、`\placeholder{}` 一次删掉，而不是删一个 `}`。

### 两套解析器

- `ExpressionEngine`：数值求值，支持函数、幂、隐式乘法（`2\pi`、`3(4)`）。
- `Poly.kt`：把方程两边整理成多项式，用于求根。指数上限 32，除法只允许常数，保证可解性。

一元二次走判别式：`Δ` 是完全平方就给两个有理根；不是就用 `factorOutSquares` 把 `√Δ` 化简成 `k√m` 再约分；`Δ < 0` 输出共轭复根。

### MathLive 离线渲染

`WebView` 加载 `file:///android_asset/mathlive/index.html`，页面里 `math-field` 设 `read-only`，虚拟键盘策略设为 `manual`，光标隐藏 —— 输入完全由 Compose 键盘负责。

`MathFormulaView` 里做了三件事：

1. 首次加载前缓存的 latex，等 `onPageFinished` 后统一推送，避免首帧丢公式；
2. `settings.blockNetworkLoads = true`，配合 Manifest 里没有 INTERNET 权限，物理上不可能联网；
3. assets 缺失时降级成 Unicode 文本并提示跑 `fetch-mathlive.mjs`。

### 数据层

- `Room` 存历史：`latex / result / detail / mode / createdAt`，超过 320 条自动裁到 300 条。
- `DataStore` 存主题：模式、种子色、动态取色、圆角、字号、触感、按键样式。
- 主题由单个种子色派生：`theme/Theme.kt` 里用 HSL 推 primary / container / surface / outline 一整套，比只改一个 `primary` 协调得多。

---

## 键盘布局

竖屏（4×5）：

```
AC   (    )    ÷
7    8    9    ×
4    5    6    −
1    2    3    +
0    .    ⌫    =
```

横屏（7×5）：

```
sin  cos  tan  ln   log   √    AC
(    )    ^    π    e    a/b   ⌫
7    8    9    ÷    x    y     ×
4    5    6    −    .    ,     +
1    2    3    0    =（跨 3 格）
```

竖屏顶部还有一条可横向滚动的快捷条：`x`、`y`、`xʸ`、`√`、`π`、`a/b`、`=`，方便在小屏输入方程。

---

## 已知限制

- 三角/对数函数只走 `Double`，只有四则、幂（整数指数）、开方保留精确值。
- 三角函数固定弧度制，暂未提供角度制开关。
- 只支持一元二次与二元一次，三次以上未实现。
- `MathLive` 按 `latest` 拉取，若上游改了 API 需要跟着调 `assets/mathlive/index.html`。
- Release 目前复用 debug 签名，正式分发前请替换 keystore。

## Roadmap

- [ ] 角度制 / 弧度制切换
- [ ] 变量与函数记忆（`Ans`、`M+`）
- [ ] 高次方程与因式分解
- [ ] 历史记录分组与检索
- [ ] 深色/浅色两套独立种子色

## 许可证

[MIT](LICENSE) · Copyright (c) 2026 Nemo
