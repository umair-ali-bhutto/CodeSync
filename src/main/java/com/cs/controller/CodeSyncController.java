package com.cs.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cs.config.CodeSyncUtil;
import com.cs.entity.CodeSync;
import com.cs.service.CodeSyncService;

/**
 * REST API for managing CodeSync shares.
 */
@RestController
@RequestMapping("/api/share")
public class CodeSyncController {

	private final CodeSyncService service;

	public CodeSyncController(CodeSyncService service) {
		this.service = service;
	}

	/**
	 * Fetches existing content or creates a new share.
	 *
	 * @param key share key
	 * @return text content
	 */
	@GetMapping("/{key}")
	public ResponseEntity<String> get(@PathVariable String key) {
		CodeSyncUtil.validateKey(key);
		CodeSync share = service.getOrCreate(key);
		return ResponseEntity.ok(share.getContent());
	}

	/**
	 * Saves or updates share content using POST.
	 *
	 * @param key     share key
	 * @param content new content (can be empty)
	 */
	@PostMapping("/{key}")
	public ResponseEntity<Void> saveOrUpdate(@PathVariable String key, @RequestBody(required = false) String content) {
		CodeSyncUtil.validateKey(key);
		if (content == null) {
			content = "";
		}

		service.update(key, content);
		return ResponseEntity.ok().build();
	}

}
