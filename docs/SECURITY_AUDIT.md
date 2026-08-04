# 安全与隐私审计

## 结论

整理后的发行副本未保留已发现的 API Key、数据库密码、本机绝对路径、私人服务地址、Git 元数据、论文 PDF、数据库导出、Embedding 导出、运行日志或构建产物。

无论旧 Key 是否已经失效，都不应进入公开仓库。失效不等于不可滥用，也不能证明它未被其他系统复用。

## 原工作区发现的位置（已脱敏）

| 类型 | 原位置 | 处理 |
|---|---|---|
| Chat API Key 默认值 | backend/src/main/resources/application.yml | 改为空环境变量 |
| Embedding API Key 默认值 | application.yml、AliyunEmbeddingConfig.java | 改为空环境变量并统一配置 |
| OCR API Key 默认值 | application.yml | 改为空环境变量 |
| Chat 模型 Key 默认值 | DeepSeekModelConfig.java | 删除硬编码 fallback |
| CORE API Key | application.yml | 改为 CORE_API_KEY |
| MinerU JWT Token | application.yml | 删除默认值并改为 MINERU_API_KEY |
| PaddleOCR Token 与个人服务地址 | application.yml、.env.example | Token 改为空环境变量，地址改为本地占位服务 |
| PowerShell Key 赋值命令 | backend/.env.example | 删除整行 |
| PostgreSQL 密码 | application.yml、Dockerfile、ManualSyncTest.java | 删除并改为运行时注入 |
| 私有 IP / 私有模型代理 | Dockerfile、示例环境配置 | 删除或替换为公开 Provider 示例 |
| 本机 F 盘路径 | 后端配置、WebMvcConfig、多个 Vue 文件 | 改为 PAPER_STORAGE_PATH、VITE_PAPER_LOCAL_PATH 或相对路径 |
| 绝对文件路径响应 | LocalFileController.FileInfo | 删除 absolutePath 字段 |

本文只记录文件与用途，不记录任何旧值。

## 已加固项

1. LocalFileController 使用受约束的 paperId，并将候选路径解析到规范化存储根目录。
2. 打开服务器本地文件夹的接口默认关闭，只有显式设置 LOCAL_FILE_OPEN_FOLDER_ENABLED=true 才可使用。
3. CORS 来源从配置读取，不再在多个配置类与 Controller 中同时使用通配符。
4. Dockerfile 不再包含密码、私人地址、个人邮箱或模型代理。
5. 根 .gitignore 排除 .env、PDF、数据、日志、向量、数据库文件、构建目录和证书。
6. 集成测试不再携带真实数据库密码。

## 仍然存在的安全边界

- 当前没有完整的登录、身份认证、角色授权和租户隔离；
- 搜索、任务、论文与本地文件接口不应直接暴露在公网；
- 外部 PDF 与网页内容是不可信输入，应继续防范 Prompt Injection、恶意 PDF、超大文件和解析器漏洞；
- OCR、Chat、Embedding、下载加速服务会接触文档内容，部署前需确认数据合规与供应商策略；
- 模型原始响应和日志可能包含论文文本或敏感数据，生产环境需要脱敏、保留期和访问控制；
- 未执行 Git 历史清理。若旧仓库曾公开或推送密钥，应由仓库所有者轮换密钥并按组织流程处理历史。

## 发布前安全检查

~~~powershell
rg -n --hidden -g '!node_modules/**' -g '!target/**' -g '!dist/**' 'sk-[A-Za-z0-9_-]{20,}|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY'
rg -n -g '!node_modules/**' -g '!target/**' '[A-Za-z]:\\|[A-Za-z]:/'
git status --short
~~~

任何命中都应人工核对；正则扫描不能替代专业秘密扫描工具和代码审查。
