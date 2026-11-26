package com.cs.service;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cs.entity.CodeSyncBlockedIp;
import com.cs.entity.CodeSyncClient;
import com.cs.repository.CodeSyncBlockedIpRepository;
import com.cs.repository.CodeSyncClientRepository;

@Service
public class CodeSyncIpManagementService {

	private final CodeSyncBlockedIpRepository blockedIpRepo;
	private final CodeSyncClientRepository clientRepo;
	private final JdbcTemplate jdbc;

	public CodeSyncIpManagementService(CodeSyncBlockedIpRepository blockedIpRepo, CodeSyncClientRepository clientRepo,
			JdbcTemplate jdbc) {
		this.blockedIpRepo = blockedIpRepo;
		this.clientRepo = clientRepo;
		this.jdbc = jdbc;
	}

	// ── blocking state ─────────────────────────────────────────────────────────

	/** Used by the security filter on every request — keep it fast. */
	public boolean isBlocked(String ip) {
		return blockedIpRepo.existsByIp(ip);
	}

	@Transactional
	public void blockIp(String ip, String adminUsername, String reason) {
		if (!blockedIpRepo.existsByIp(ip)) {
			blockedIpRepo.save(new CodeSyncBlockedIp(ip, adminUsername, reason));
		}
	}

	@Transactional
	public void unblockIp(String ip) {
		blockedIpRepo.deleteById(ip);
	}

	public List<CodeSyncBlockedIp> getAllBlockedIps() {
		return blockedIpRepo.findAll();
	}

	// ── known clients (from CODE_SYNC_CLIENTS) ─────────────────────────────────

	/** Returns every row from CODE_SYNC_CLIENTS as a simple DTO list. */
	@SuppressWarnings("unused")
	public List<ClientRow> getKnownClients() {
		return jdbc.query(
				"SELECT ip, name, inserted_on, updated_on, updated_by FROM CODE_SYNC_CLIENTS ORDER BY inserted_on DESC",
				(rs, n) -> new ClientRow(rs.getString("ip"), rs.getString("name"), rs.getString("inserted_on"),
						rs.getString("updated_on"), rs.getString("updated_by")));
	}

	// ── unknown IPs (in audit log but NOT in clients table) ───────────────────

	public List<String> getUnknownIps() {
		return jdbc.queryForList("SELECT DISTINCT csa.client_ip " + "FROM code_sync_audit csa "
				+ "LEFT JOIN code_sync_clients c ON csa.client_ip = c.ip " + "WHERE c.ip IS NULL "
				+ "ORDER BY csa.client_ip", String.class);
	}

	@Transactional
	public void addClient(String ip, String name) {
		ip = ip == null ? "" : ip.trim();
		name = name == null ? "" : name.trim();

		if (!ip.matches("^[0-9a-fA-F:.]{3,45}$")) {
			throw new IllegalArgumentException("Invalid IP address format: " + ip);
		}
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Name is required.");
		}
		if (clientRepo.existsById(ip)) {
			throw new IllegalArgumentException("IP " + ip + " already exists.");
		}

		CodeSyncClient c = new CodeSyncClient();
		c.setIp(ip);
		c.setName(name);
		clientRepo.save(c);
	}

	@Transactional
	public void updateClient(String ip, String name, String updatedByIp) {
		name = name == null ? "" : name.trim();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Name is required.");
		}
		CodeSyncClient c = clientRepo.findById(ip.trim())
				.orElseThrow(() -> new IllegalArgumentException("Client " + ip + " not found."));
		c.setName(name);
		c.setUpdatedOn(new Timestamp(System.currentTimeMillis()));
		c.setUpdatedBy(updatedByIp);
		clientRepo.save(c);
	}

	// ── simple projection ──────────────────────────────────────────────────────

	public record ClientRow(String ip, String name, String insertedOn, String updatedOn, String updatedBy) {
	}
}
