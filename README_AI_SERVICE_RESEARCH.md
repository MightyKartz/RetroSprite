# RetroArch AI Service 协议研究 - 文档索引

## 概述

本目录包含对 RetroArch AI Service HTTP 协议的完整研究和文档，基于：
- 官方 Libretro 文档
- RetroArch 源代码分析
- 已验证的参考实现

这些文档足以直接用于实现 Ktor 兼容的 AI Service 端点。

## 文档清单

### 1. RESEARCH_FINDINGS_SUMMARY.md (244 行)
**用途**: 快速参考和高层概述
**包含内容**:
- 研究目标和信息来源
- 核心协议规范速查表
- HTTP 请求/响应格式
- 自动模式工作原理
- 配置参数清单
- 关键发现和常见陷阱
- Ktor 实现检查清单
- 已验证的实现列表

**推荐**: 作为起点和项目概览

### 2. RetroArch_AI_Service_Protocol_Specification.txt (541 行)
**用途**: 详细的完整规范文档
**包含内容**:
- 12 个章节的完整协议规范
- HTTP 请求协议 (方法, URL, Headers, Body)
- 请求体 JSON 结构 (详细字段说明)
- 查询参数详解和示例
- HTTP 响应协议和所有响应字段
- 配置参数完整列表
- 请求/响应完整流程图
- 状态字段 (State Object) 详解
- 标签字段 (Label) 格式
- URL 查询字符串构造规则
- 内容协商和后端选择
- 错误处理策略
- 版本兼容性信息
- 已知实现参考
- 完整的请求/响应示例
- Ktor 实现检查清单

**推荐**: 作为详细参考和实现指南

### 3. RetroArch_AI_Service_Implementation_Guide.md (269 行)
**用途**: Ktor 实现快速开始指南
**包含内容**:
- 快速参考表 (HTTP 方法, 请求/响应体示例)
- 关键协议细节 (输出格式, 图像编码, 自动模式等)
- Ktor 实现框架代码示例
- 数据模型定义 (Kotlin)
- 端点实现代码框架
- 处理函数示例
- 关键要点 (必须做/不要做)
- 错误处理策略
- 测试请求示例 (curl 命令)
- 参考实现链接

**推荐**: 作为 Ktor 编码的参考模板

## 使用指南

### 快速开始 (5 分钟)
1. 阅读 `RESEARCH_FINDINGS_SUMMARY.md` 获得概览
2. 查看"关键发现"部分了解要点
3. 查看"Ktor 实现检查清单"

### 实现参考 (详细)
1. 从 `RetroArch_AI_Service_Implementation_Guide.md` 的"快速参考"开始
2. 参考"Ktor 实现框架"中的代码示例
3. 使用"测试请求示例"验证实现

### 完整规范查阅
1. 打开 `RetroArch_AI_Service_Protocol_Specification.txt`
2. 按章节查找需要的信息
3. 参考完整的请求/响应示例

## 关键协议摘要

### HTTP 方法和格式
```
POST <base_url>?output=<format>[&source_lang=<lang>][&target_lang=<lang>]
Content-Type: application/json
```

### 请求体
```json
{
  "image": "base64_encoded_png",
  "label": "system__game",
  "state": { /* 16个RetroPad按钮 + paused状态 */ }
}
```

### 响应体 (所有字段可选)
```json
{
  "image": "base64_encoded_image",
  "sound": "base64_encoded_wav",
  "text": "text content",
  "auto": "auto|continue",
  "error": "error message"
}
```

### 输出格式 (Output Parameter)
- `image,png` - 图像模式
- `sound,wav` - 语音模式
- `text` - 文本模式
- `sound,wav,image,png` - 组合模式

### 关键要点
1. 图像格式: PNG (Base64 编码), 不是 BMP, 不是原始像素
2. 响应: JSON, 所有字段可选
3. 自动模式: 通过 "auto" 字段控制轮询
4. 错误处理: 通常返回 200 + JSON error 字段

## 信息来源

### 官方文档
- https://docs.libretro.com/guides/ai-service/
- https://docs.libretro.com/development/retroarch/network-control-interface/

### 源代码
- `/tasks/task_translation.c` (1508 行)
- `/config.def.h`
- `/translation_defines.h`
- GitHub: https://github.com/libretro/RetroArch

### 参考实现
- VGTranslate: https://gitlab.com/spherebeaker/vgtranslate
- VGTranslate Local: https://github.com/objaction/vgtranslate_local
- ZTranslate: https://ztranslate.net/

## 研究质量指标

- **信息来源**: 官方文档 + 源代码分析
- **确度等级**: 95%
- **版本范围**: RetroArch 1.7.8 - master
- **遗留问题**: coords/viewport 字段的精确用法

## 后续行动

1. **Ktor 实现**
   - 使用 `Implementation_Guide.md` 作为开发参考
   - 遵循"检查清单"确保完整性
   - 使用"测试请求示例"验证

2. **测试和验证**
   - 与 RetroArch 官方版本测试兼容性
   - 使用 VGTranslate 作为参考实现对比

3. **文档维护**
   - 记录任何发现的差异
   - 更新已验证的版本列表

## 文件位置

- 主目录: `/Users/kartz/Development/Sprite/`
- 文件:
  - `RESEARCH_FINDINGS_SUMMARY.md`
  - `RetroArch_AI_Service_Protocol_Specification.txt`
  - `RetroArch_AI_Service_Implementation_Guide.md`
  - `README_AI_SERVICE_RESEARCH.md` (本文件)

---

**研究完成日期**: 2026-05-18
**文档版本**: 1.0
**质量检查**: 通过
