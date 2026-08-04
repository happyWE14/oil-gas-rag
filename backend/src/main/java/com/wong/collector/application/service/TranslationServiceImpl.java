package com.wong.collector.application.service;

import com.wong.collector.application.dto.request.TranslationRequest;
import com.wong.collector.application.dto.request.TranslationRequest.TranslationType;
import com.wong.collector.application.dto.response.TranslationResponse;
import jakarta.annotation.PostConstruct;  // 修改：javax -> jakarta
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TranslationServiceImpl implements TranslationService {

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${translation.cache.duration:2592000}")
    private Long cacheDurationSeconds;

    private static final String CACHE_KEY_PREFIX = "translation:";
    private static final String TRANSLATION_MODEL = "deepseek-chat";

    /**
     * 启动时测试 Redis 连接
     */
    @PostConstruct
    public void testRedisConnection() {
        try {
            String testKey = "translation:system:test";
            String testValue = "connected";
            redisTemplate.opsForValue().set(testKey, testValue, 60, TimeUnit.SECONDS);
            String retrieved = redisTemplate.opsForValue().get(testKey);
            log.info("✅ Redis 连接测试成功: key={}, value={}", testKey, retrieved);
            redisTemplate.delete(testKey);
        } catch (Exception e) {
            log.error("❌ Redis 连接测试失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public TranslationResponse translate(TranslationRequest request) {
        if (!StringUtils.hasText(request.getText())) {
            throw new IllegalArgumentException("翻译文本不能为空");
        }

        String text = request.getText().trim();
        String type = request.getType() != null ? request.getType().name() : "TEXT";
        String cacheKey = generateCacheKey(text, type);

        // 关键日志：查看生成的 key
        log.info("🔑 生成的缓存key: {}", cacheKey);

        String cachedResult = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedResult)) {
            log.info("💾 缓存命中: key={}, value={}", cacheKey, cachedResult);
            return TranslationResponse.builder()
                    .originalText(text)
                    .translatedText(cachedResult)
                    .source("cache")
                    .costTime(0L)
                    .build();
        }

        log.info("🌐 缓存未命中，调用API: type={}, text={}...", type,
                text.substring(0, Math.min(50, text.length())));

        long startTime = System.currentTimeMillis();
        String translatedText;

        try {
            translatedText = callDeepSeekApi(text, request.getType(), request.getContext());
        } catch (Exception e) {
            log.error("❌ 调用DeepSeek API失败: {}", e.getMessage(), e);
            throw new RuntimeException("翻译服务暂时不可用，请稍后重试");
        }

        long costTime = System.currentTimeMillis() - startTime;

        if (StringUtils.hasText(translatedText)) {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    translatedText,
                    cacheDurationSeconds,
                    TimeUnit.SECONDS
            );
            log.info("✅ 已写入缓存: key={}, 过期时间={}秒, 内容={}",
                    cacheKey, cacheDurationSeconds, translatedText);
        } else {
            log.warn("⚠️ 翻译结果为空，未写入缓存");
        }

        return TranslationResponse.builder()
                .originalText(text)
                .translatedText(translatedText)
                .source("api")
                .costTime(costTime)
                .build();
    }

    @Override
    public List<TranslationResponse> translateBatch(List<TranslationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        log.info("开始批量翻译，共 {} 条", requests.size());

        return requests.parallelStream()
                .map(req -> {
                    try {
                        return translate(req);
                    } catch (Exception e) {
                        log.error("批量翻译中单条失败: {}", e.getMessage());
                        return TranslationResponse.builder()
                                .originalText(req.getText())
                                .translatedText("[翻译失败] " + req.getText())
                                .source("error")
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public void clearCache(String text, TranslationType type) {
        String cacheKey = generateCacheKey(text, type.name());
        Boolean deleted = redisTemplate.delete(cacheKey);
        log.info("🗑️ 清除翻译缓存: key={}, 结果: {}", cacheKey, deleted);
    }

    /**
     * 调用 DeepSeek API
     * 关键：使用 OpenAiChatOptions 明确覆盖模型为 deepseek-chat
     */
    private String callDeepSeekApi(String text, TranslationType type, String context) {
        String promptContent = buildPrompt(text, type, context);

        SystemMessage systemMessage = new SystemMessage(
                "你是石油工程学术翻译专家。要求：" +
                        "1)准确翻译专业术语；2)保留化学缩写如HPAM、HPG等；3)直接返回翻译结果，不要解释；4)保持学术严谨性。"
        );

        UserMessage userMessage = new UserMessage(promptContent);

        // ✅ 关键修正：OpenAiChatOptions 的方法名没有 with 前缀
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(TRANSLATION_MODEL)        // 指定 deepseek-chat
                .temperature(0.3)
                .maxTokens(2000)
                .build();

        log.info("🤖 准备调用模型: {}", TRANSLATION_MODEL);

        ChatResponse response = chatClient.prompt()
                .messages(systemMessage, userMessage)
                .options(chatOptions)
                .call()
                .chatResponse();

        if (response == null || response.getResult() == null) {
            throw new RuntimeException("API返回为空");
        }

        AssistantMessage assistantMessage = response.getResult().getOutput();
        String content = assistantMessage.getText();

        log.info("✅ 翻译完成，模型: {}, 结果长度: {}", TRANSLATION_MODEL,
                content != null ? content.length() : 0);

        return content != null ? content.trim() : "";
    }

    private String buildPrompt(String text, TranslationType type, String context) {
        StringBuilder sb = new StringBuilder();

        switch (type) {
            case MATERIAL_NAME:
                sb.append("将以下石油工程材料名称译为中文。\n");
                sb.append("规则：保留英文缩写，格式为\"缩写(中文全名)\"。\n");
                sb.append("示例：\n- HPAM(部分水解聚丙烯酰胺)\n- HPG(羟丙基瓜尔胶)\n- VES(粘弹性表面活性剂)\n\n");
                sb.append("待译材料：").append(text);
                break;

            case PAPER_TITLE:
                sb.append("将以下学术论文标题译为中文。\n");
                sb.append("要求：学术严谨、简洁明了、符合中文论文标题规范，保留专有名词。\n\n");
                sb.append("标题：").append(text);
                break;

            case EVIDENCE_CHUNK:
                sb.append("将以下石油工程学术文本片段译为中文。\n");
                sb.append("要求：1)保留所有数据和单位；2)准确翻译专业术语；3)保持学术语气。\n");
                if (StringUtils.hasText(context)) {
                    sb.append("上下文：").append(context).append("\n");
                }
                sb.append("\n文本内容：\n").append(text);
                break;

            case OBSERVATION:
                sb.append("将以下实验观察记录译为中文。\n");
                sb.append("要求：客观描述，准确传达实验现象和结论。\n\n");
                sb.append("记录：").append(text);
                break;

            case METRIC_DESCRIPTION:
                sb.append("将以下性能指标描述译为中文。\n");
                sb.append("要求：准确翻译技术参数和条件。\n\n");
                sb.append("描述：").append(text);
                break;

            default:
                sb.append("请将以下文本译为中文：\n").append(text);
        }

        return sb.toString();
    }

    private String generateCacheKey(String text, String type) {
        String content = type + ":" + text;
        String md5 = DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
        return CACHE_KEY_PREFIX + md5;
    }
}
