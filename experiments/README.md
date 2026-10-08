# S02 最小技术原型

原型只验证本地索引的生命周期语义，不是正式业务代码，也不代表真实嵌入质量。要求 Python 3.10+，仅使用标准库。

运行：

```powershell
python experiments/retrieval_prototype.py
```

原型会在工作区临时目录创建自制 TXT、Markdown、结构有效 DOCX 和带交叉引用表的最小 PDF，验证解析、分段、确定性嵌入、单文件原子快照、同库检索、跨库过滤、重启读取、删除文档后不再命中、嵌入维度不匹配拒绝加载，并对 OpenAI 兼容 `/v1/embeddings` 和 Ollama `/api/embed` 发起真实协议请求。输出为 JSON 摘要，不写入用户数据目录；连接失败是如实记录的未验证结果。该脚本不替代 Tika/PDFBox 或真实模型验证。

使用 `python experiments/environment_probe.py` 检查 S02 的 Java/Maven/解析库前置条件；探针只读，不安装依赖。
