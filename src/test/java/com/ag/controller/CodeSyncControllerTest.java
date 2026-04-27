package com.ag.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import com.ag.config.JwtAuthenticationEntryPoint;
import com.ag.config.SecurityProtectionConfig;
import com.ag.entity.CodeSync;
import com.ag.service.CodeSyncAuditService;
import com.ag.service.CodeSyncService;

@WebMvcTest(CodeSyncController.class)
//We use this to satisfy Spring without loading the @EnableJpaRepositories from your Main class
@ContextConfiguration(classes = CodeSyncControllerTest.TestConfig.class)
public class CodeSyncControllerTest {

	// 1. Tiny inner class to act as the "Main Application" for this test only
	@org.springframework.boot.test.context.TestConfiguration
	@org.springframework.boot.autoconfigure.SpringBootApplication(exclude = {
			org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
			org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
			org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class })
	static class TestConfig {
	}

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private CodeSyncService service;

	@MockBean
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@MockBean
	private CodeSyncAuditService codeSyncAuditService;

	@MockBean
	private SecurityProtectionConfig securityProtectionConfig;

	// Keep these just in case any security filter still looks for them
	@MockBean
	private javax.sql.DataSource dataSource;
	@MockBean
	private javax.persistence.EntityManagerFactory entityManagerFactory;

	@Test
	@WithMockUser
	void testGetContent_Success() throws Exception {
		String key = "testKey";
		CodeSync mockShare = new CodeSync();
		mockShare.setContent("Hello World");

		when(service.getOrCreate(key)).thenReturn(mockShare);

		mockMvc.perform(get("/api/share/" + key)).andExpect(status().isOk()).andExpect(content().string("Hello World"));
	}

	@Test
	@WithMockUser
	void testSaveOrUpdate_Success() throws Exception {
		String key = "testKey";
		String content = "New Content";

		mockMvc.perform(post("/api/share/" + key).with(csrf()).contentType(MediaType.TEXT_PLAIN).content(content))
				.andExpect(status().isOk());
	}
}