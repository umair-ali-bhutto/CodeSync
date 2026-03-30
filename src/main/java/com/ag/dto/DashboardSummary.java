package com.ag.dto;

public class DashboardSummary {

    private long todayRequests;
    private long yesterdayRequests;
    private long activeClientsToday;
    private long activeClientsYesterday;

    public DashboardSummary(long todayRequests, long yesterdayRequests,
                            long activeClientsToday, long activeClientsYesterday) {
        this.todayRequests = todayRequests;
        this.yesterdayRequests = yesterdayRequests;
        this.activeClientsToday = activeClientsToday;
        this.activeClientsYesterday = activeClientsYesterday;
    }

    public long getTodayRequests() { return todayRequests; }
    public long getYesterdayRequests() { return yesterdayRequests; }
    public long getActiveClientsToday() { return activeClientsToday; }
    public long getActiveClientsYesterday() { return activeClientsYesterday; }
}