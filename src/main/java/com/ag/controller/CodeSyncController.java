package com.ag.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ag.config.CodeSyncLogger;
import com.ag.entity.CodeSync;
import com.ag.service.CodeSyncService;

@RestController
@RequestMapping("/api/share")
public class CodeSyncController {

	private final CodeSyncService service;

	public CodeSyncController(CodeSyncService service) {
		this.service = service;
	}

	// Fetch or create
	@GetMapping("/{key}")
	public ResponseEntity<String> get(@PathVariable("key") String key) {
		CodeSyncLogger.logInfo("Called: "+key);
		CodeSync share = service.getOrCreate(key);
		return ResponseEntity.ok(share.getContent());
	}

	// Update content
	@PutMapping("/{key}")
	public ResponseEntity<Void> update(@PathVariable("key") String key, @RequestBody(required = false) String content) {
		if (content == null) {
			content = ""; // treat null as empty
		}
		CodeSyncLogger.logInfo("updateCalled2: "+key);
		CodeSyncLogger.logInfo("updateCalled3: "+content);
		
		service.update(key, content);
		return ResponseEntity.ok().build();
	}

	// Delete
	@DeleteMapping("/{key}")
	public ResponseEntity<Void> delete(@PathVariable("key") String key) {
		CodeSyncLogger.logInfo("delete: "+key);
		
		service.delete(key);
		return ResponseEntity.noContent().build();
	}
}
