package com.campus.reservation.service;

import com.campus.reservation.ai.AiSkillLoader;
import com.campus.reservation.ai.AiTool;
import com.campus.reservation.ai.AiToolContext;
import com.campus.reservation.ai.AiToolRegistry;
import com.campus.reservation.common.BizException;
import com.campus.reservation.common.UserContext;
import com.campus.reservation.config.DeepSeekProperties;
import com.campus.reservation.dto.AiChatRequest;
import com.campus.reservation.dto.AiChatReply;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智约机器人：调用 DeepSeek（OpenAI 兼容 Chat Completions + tool/function calling）。
 * 工具的 schema、执行与权限门禁均下沉到 {@link AiToolRegistry} 及其管理的 {@code com.campus.reservation.ai.tools} 工具集；
 * 本类只负责会话编排：拼装历史、多轮工具循环、调用 LLM、收集草案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    /**
     * 提示词 = 头部（身份+当前时间）+ 技能手册（resources/ai/skills/*.md，按角色由 {@link AiSkillLoader} 拼接）
     * + 尾部（预约规则/行为准则/权限约束）。工具清单不再内联在代码里，改由外置 skill 文档维护。
     */
    private static final String PROMPT_HEADER = """
            你是“智约”，校园资源预约平台的 AI 助手。请用中文简洁回答。

            当前时间：今天={today}（{todayWeekday}），现在时刻={nowTime}；一周为周一至周日。
            用户提到“今天/明天/后天/大后天/下周”时，**直接目算为具体日期**后写入工具参数，绝不要反问“你说的是几号”！

            以下是本次会话的技能手册（工具的参数 schema 以系统下发定义为准，手册是使用方法与先后流程）：
            """;

    private static final String PROMPT_TAIL = """
            预约规则：
            1) 同一时段每人只能一个预约；
            2) 实验室/体育馆最多提前 7 天，图书馆最多提前 1 天（即“明天”），教室不需预约；
            3) 开放时间内才能约：图书馆 22:00、体育馆 21:00、实验室 20:00；
            4) 预约开始前 30 分钟至开始后 5 分钟内，用户需在资源附近完成浏览器定位打卡；超时则爽约，1 天内违规 3 次当日禁约。

            【行为准则】：
            • 任何关于具体资源名称/数量/可用时段的描述，**必须先调工具查询**；不要自己编造“一共有10个座位”“6F多媒体室”这类不存在于查询结果里的信息。
            • 当用户已给出“地点类型 + 时间”（如“明天下午3–5点图书馆”），不要反复确认日期、时长、人数，直接按技能手册的标准流程走完查询到草案生成。
            • 回复时不要复述平台规则（“图书馆最多预约到次日”等已知常识），除非用户问到规则本身。
            • 若真的缺少关键信息（如完全不知道地点或时间），一次把要问的都问了，不要分多轮。
            """
            ;

    /** 普通用户会话的权限约束：管理员能力既不下发也不允许装造 */
    private static final String PERMISSION_USER = """
            【权限约束】：
            • 你只能调用面向当前用户权限的工具，可调用范围由系统按登录角色下发；
            • 涉及管理员专属操作（闭馆/恢复、群发通知、审批报修、查看或操作他人预约与数据等）一律拒绝，明确告知“您没有该权限，我无法代为执行”，不要尝试构造此类调用。
            """;

    /** 管理员会话的权限约束：管理员级工具已下发，必须走工具草案而不是直接拒绝 */
    private static final String PERMISSION_ADMIN = """
            【权限约束（管理员会话）】：
            • 当前登录账号是管理员，系统已下发管理员级工具（admin_*）：一键闭馆/恢复、定向闭馆、查看闭馆、撤销闭馆；
            • 管理员要闭馆/恢复时，**不要回答“您没有该权限”**，按 admin.md 手册直接调用对应工具生成草案，由管理员在卡片上点确认后才生效；
            • 草案只是预案，禁止声称“已经闭馆成功”，应说明“已生成草案，请在下方卡片点确认”；
            • 系统未提供工具的管理操作（群发通知、审批报修工单、修改他人预约等）仍应引导管理员前往管理后台对应页面处理。
            """;

    private static final String[] WEEKDAY_CN = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final DeepSeekProperties props;
    private final AiToolRegistry toolRegistry;
    private final AiSkillLoader skillLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public AiChatReply chat(AiChatRequest req) {
        if (!props.ready()) {
            throw new BizException(503, "AI 助手暂未开放，请稍后再试");
        }
        if (req == null || req.getMessages() == null || req.getMessages().isEmpty()) {
            throw BizException.badRequest("请先输入内容再发送");
        }
        UserContext.LoginUser current = UserContext.get();
        Long userId = UserContext.requireUserId();
        int role = current != null && current.getRole() != null ? current.getRole() : 0;
        List<Map<String, Object>> drafts = new ArrayList<>();
        AiToolContext ctx = new AiToolContext(userId, role, drafts);

        ArrayNode messages = new ArrayNode(JsonNodeFactory.instance);
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", buildSystemPrompt(role));
        for (AiChatRequest.Message m : req.getMessages()) {
            if (m.getContent() == null || m.getContent().isBlank()) continue;
            ObjectNode mm = messages.addObject();
            mm.put("role", "user".equals(m.getRole()) ? "user" : "assistant");
            mm.put("content", m.getContent());
        }

        String finalContent = null;
        try {
            for (int loop = 0; loop < props.getMaxToolLoops(); loop++) {
                ObjectNode body = objectMapper.createObjectNode();
                body.put("model", props.getModel());
                body.set("messages", messages);
                body.put("tool_choice", "auto");
                body.set("tools", toolRegistry.schemasForRole(role));

                JsonNode resp = callLlm(body);
                JsonNode choice = resp.path("choices").path(0).path("message");
                String content = choice.path("content").isMissingNode() || choice.path("content").isNull()
                        ? null : choice.path("content").asText();
                JsonNode toolCalls = choice.path("tool_calls");

                if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                    finalContent = content;
                    break;
                }
                // 有 tool_calls：把 assistant 消息（含 tool_calls）塞回历史，然后逐条执行工具
                messages.add(choice.deepCopy());
                for (JsonNode tc : toolCalls) {
                    String id = tc.path("id").asText();
                    String name = tc.path("function").path("name").asText();
                    String argsStr = tc.path("function").path("arguments").asText("{}");
                    Map<String, Object> args = parseArgs(argsStr);
                    String observation = toolRegistry.dispatch(name, args, ctx);
                    ObjectNode toolMsg = messages.addObject();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", id);
                    toolMsg.put("name", name);
                    toolMsg.put("content", observation);
                }
                if (loop == props.getMaxToolLoops() - 1) {
                    finalContent = content == null ? "抱歉，我暂时处理不了这个请求，请稍后再试。" : content;
                }
            }
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            log.error("AI 会话失败", e);
            throw new BizException(502, "AI 服务暂时不可用，请稍后再试");
        }

        AiChatReply reply = new AiChatReply();
        reply.setContent(finalContent == null ? "我已经收到，请查看下方的操作卡片。" : finalContent);
        reply.setPendingActions(drafts);
        AiChatReply.Message echo = new AiChatReply.Message();
        echo.setRole("assistant");
        echo.setContent(reply.getContent());
        reply.setAssistantEcho(echo);
        return reply;
    }

    private String buildSystemPrompt(int role) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        String header = PROMPT_HEADER
                .replace("{today}", today.toString())
                .replace("{todayWeekday}", WEEKDAY_CN[today.getDayOfWeek().getValue() - 1])
                .replace("{nowTime}", String.format("%02d:%02d", now.getHour(), now.getMinute()));
        return header + "\n" + skillLoader.skillsForRole(role) + "\n" + PROMPT_TAIL
                + (role >= AiTool.ROLE_ADMIN ? PERMISSION_ADMIN : PERMISSION_USER);
    }

    private JsonNode callLlm(ObjectNode body) throws Exception {
        String url = props.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            log.error("LLM 调用失败：HTTP {}，响应：{}", resp.statusCode(), truncate(resp.body()));
            throw new BizException(502, "AI 服务暂时不可用，请稍后再试");
        }
        return objectMapper.readTree(resp.body());
    }

    private Map<String, Object> parseArgs(String json) throws Exception {
        if (json == null || json.isBlank()) return Map.of();
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
