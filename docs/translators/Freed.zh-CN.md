# Freed

## 什么是 Freed？
Freed 是自定义翻译接口，是否需要鉴权以及可用性、限速等取决于你的服务。

## Freed.yaml 配置说明

```yaml

# Freed API 地址
url: https://your-service.example/translate

# 请求参数
languageModel: next-gen
usageType: Translate

# 请求头参数
acceptLanguage: zh-Hans
appOsVersion: 18.5.0
appDevice: iPhone11,8
appBuild: 3065622
appVersion: 25.37
userAgent: ktor-client

# 网络重试
retryCount: 2
retryDelayMs: 500

```
