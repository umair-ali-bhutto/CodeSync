package com.ag.CodeSync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.ag.config.CodeSyncLogger;

@SpringBootTest
public class CodeSyncTest {

	@Test
	void contextLoads() throws Exception {
		CodeSyncLogger.logInfo("TEST WORKING");
	}

}
