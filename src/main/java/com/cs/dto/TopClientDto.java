package com.cs.dto;

public class TopClientDto {
	private String ip;
	private String name;
	private long total;

	public TopClientDto(String ip, String name, long total) {
		this.ip = ip;
		this.name = name;
		this.total = total;
	}

	public String getIp() {
		return ip;
	}

	public String getName() {
		return name;
	}

	public long getTotal() {
		return total;
	}
}