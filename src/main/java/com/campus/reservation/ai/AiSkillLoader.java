package com.campus.reservation.ai;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 技能手册加载器：启动时读取 classpath 下 ai/skills/*.md，缓存为按角色组装的系统提示片段。
 * 手册只负责"行为指导"（何时调哪个接口、参数格式、流程顺序）；
 * 可调工具集合与权限边界仍由 {@link AiToolRegistry} 在代码层强制执行。
 */
@Slf4j
@Component
public class AiSkillLoader {

    private static final String SKILL_DIR = "ai/skills/";
    /** 所有角色可见的技能手册 */
    private static final List<String> COMMON_SKILLS = List.of("query.md", "reservation.md");
    /** 管理员会话额外追加的手册 */
    private static final List<String> ADMIN_SKILLS = List.of("admin.md");

    private final Map<String, String> cache = new HashMap<>();

    @PostConstruct
    public void load() {
        for (String file : List.of("query.md", "reservation.md", "admin.md")) {
            ClassPathResource res = new ClassPathResource(SKILL_DIR + file);
            if (!res.exists()) {
                log.warn("AI 技能手册缺失：{}{}", SKILL_DIR, file);
                continue;
            }
            try (InputStream in = res.getInputStream()) {
                cache.put(file, new String(in.readAllBytes(), StandardCharsets.UTF_8).strip());
            } catch (IOException e) {
                log.warn("AI 技能手册读取失败：{}", file, e);
            }
        }
        log.info("AI 技能手册已加载 {} 份：{}", cache.size(), cache.keySet());
    }

    /** 按登录角色返回应注入系统提示词的技能手册拼接文本（缺文件时返回空串，不阻断会话）。 */
    public String skillsForRole(int role) {
        StringBuilder sb = new StringBuilder();
        append(sb, COMMON_SKILLS);
        if (role >= AiTool.ROLE_ADMIN) {
            append(sb, ADMIN_SKILLS);
        }
        return sb.toString();
    }

    private void append(StringBuilder sb, List<String> files) {
        for (String f : files) {
            String content = cache.get(f);
            if (content != null && !content.isBlank()) {
                sb.append(content).append("\n\n");
            }
        }
    }
}
