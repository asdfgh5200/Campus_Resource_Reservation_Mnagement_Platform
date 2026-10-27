package com.campus.reservation.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiSkillLoader 单测：验证技能手册从 classpath 加载与按角色拼接。
 */
class AiSkillLoaderTest {

    private AiSkillLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AiSkillLoader();
        loader.load();
    }

    @Test
    void loadsAllSkillFiles() {
        String skills = loader.skillsForRole(AiTool.ROLE_USER);
        assertThat(skills).contains("query_resources").contains("propose_reservation");
    }

    @Test
    void userRoleDoesNotSeeAdminManual() {
        String skills = loader.skillsForRole(AiTool.ROLE_USER);
        assertThat(skills).doesNotContain("admin_set_campus_closure");
    }

    @Test
    void adminRoleGetsAdminManual() {
        String skills = loader.skillsForRole(AiTool.ROLE_ADMIN);
        assertThat(skills).contains("admin_set_campus_closure");
        // 管理员同样能看到通用手册
        assertThat(skills).contains("query_resources");
    }
}
