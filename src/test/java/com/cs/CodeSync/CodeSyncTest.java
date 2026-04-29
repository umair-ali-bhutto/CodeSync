package com.cs.CodeSync;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.cs.config.CodeSyncLogger;

@SpringBootTest
public class CodeSyncTest {

	@Test
	void contextLoads() throws Exception {
		CodeSyncLogger.logInfo("TEST WORKING");
	}

}
