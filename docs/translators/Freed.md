# Freed

## What is Freed?
Freed is a custom translation endpoint. Availability, authentication, and rate limits depend on your service.

## Freed.yaml Configuration Instructions

```yaml

# Freed API endpoint
url: https://your-service.example/translate

# Payload parameters
languageModel: next-gen
usageType: Translate

# Header parameters
acceptLanguage: zh-Hans
appOsVersion: 18.5.0
appDevice: iPhone11,8
appBuild: 3065622
appVersion: 25.37
userAgent: ktor-client

# Network retry
retryCount: 2
retryDelayMs: 500

```
