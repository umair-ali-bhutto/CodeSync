package com.cs.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SystemCommandService {

	public String execute(String windowsCommand, String linuxCommand, int timeoutSeconds) {
		try {
			List<String> command = new ArrayList<>();
			String os = System.getProperty("os.name").toLowerCase();

			if (os.contains("win")) {
				command.add("powershell");
				command.add("-NoProfile");
				command.add("-Command");
				command.add(windowsCommand);
			} else if (os.contains("nix") || os.contains("nux")) {
				command.add("/bin/bash");
				command.add("-lc");
				command.add(linuxCommand);
			}

			ProcessBuilder builder = new ProcessBuilder(command);
			builder.redirectErrorStream(true);
			Process process = builder.start();
			boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				return "Command timeout";
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StringBuilder output = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
			}
			return output.toString();
		} catch (Exception e) {
			return "ERROR: " + e.getMessage();
		}
	}
}