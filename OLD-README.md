# CodeSync

<p align="center">
  <a href="https://github.com/umair-ali-bhutto/" target="_blank">
    <img src="https://umair-ali-bhutto.github.io/assets/CodePenIcon/logo.png" width="100px" height="100px" alt="logo"><br/>
  </a>
</p>

A simple **Codeshare.io–like** application built with **Spring Boot 2.5.5** and **Java 8**.  
It allows users to share text in real time using a URL-based key — no authentication required, with advanced **security, logging, and auditing** features added.

---

## 🚀 Features

### Core Features

- URL-based shared rooms

```

/share/{customKey}

```

Example:

```

/share/umair
/share/test123

```

- Shared text editor (textarea)
- Auto-create share if it does not exist
- Auto-save with debounce
- Near real-time updates using polling
- Persistent storage using JPA (Oracle / MSSQL / MySQL)
- WAR packaging (deployable on external servers)
- Copy text and Clear text buttons for convenience

### Security Features

- **JWT-based Authentication EntryPoint**  
  Returns 401 Unauthorized HTML page for invalid access attempts.
- **Rate Limiting using Bucket4j**  
  Limits requests per client IP with configurable capacity and refill rate.
- **IP Blocking**  
  Block malicious IPs automatically based on configuration (configurable from `application.properties` file).
- **Client IP Identification**  
  Logs IP, browser, OS, device type, and client type for every request.
- **Audit Logging of Every Request**  
  Stores details in `CodeSyncAudit` table: method, URI, query string, IP, forwarded IPs, content length, body, duration, and client details.
- **Client Name Lookup**  
  Maps known IP addresses to names, logs names instead of IPs for easier audit.
- **Global Exception Handling**  
  All exceptions during filtering, key validation, or request handling are captured and logged.
- **Share Key Validation**  
  Validates share key length (max 100 characters), both strict (exception) and lenient (logs warning).

⚠️ **NOTE: Do not use as-is for sensitive data** ⚠️

### Frontend Features

- Simple HTML/CSS/JS frontend with:
  - Share URL button
  - Copy text button
  - Clear text button
- Real-time polling for updates every 3 seconds
- Responsive textarea for editing

---

## 🌐 Web UI

### Access Shared Editor

```

/share/{key}

```

Example:

[http://172.191.1.223:8081/codesync/share/umair](http://172.191.1.223:8081/codesync/share/umair)

All users opening the same URL will see and edit the same content in real-time.

---

## 🔄 How Real-Time Sync Works

This MVP uses **polling**:

- Editor auto-saves after typing stops (500ms debounce)
- Browser polls backend every 3 seconds
- If content changes, editor updates automatically

✅ Simple  
✅ Reliable  
❌ Not instant (acceptable for MVP)

---

## 🛡 Security / Backend Protection

### Rate Limiting

- Each client IP has a token bucket
- Configurable `capacity`, `refill amount`, `refill interval`
- Excess requests return HTTP `429 Too Many Requests`

### IP Blocking

- Predefined IPs blocked via `security.blocked-ips` property
- Requests from blocked IPs return HTTP `403 Forbidden`

### Authentication EntryPoint

- Unauthenticated access triggers `JwtAuthenticationEntryPoint`
- Responds with styled HTML page explaining proper usage of share URLs

### Audit Logging

Each request logs:

- HTTP method, URI, query string
- Client IP, forwarded IPs, real IP
- Browser info, OS, device type, client type
- Request body and content size
- Duration of request processing

Example log:

```

SECURITY FILTER | GET /share/umair | IP=172.191.1.223 | browserInfo= os=Linux | browser=Chrome | device=Desktop | clientType=Browser | Lang=en-US,en;q=0.9 | Ref=null | Status=200 | Time=12ms | content size: 250 | Body=...

```

### Client IP Name Mapping

- Known client IPs are mapped to names
- Logs show client name instead of raw IP if available
- Dynamically updated when new IPs are added in DB

### Logging Control

- Enable or disable logging globally using `StartUpInit.enableLogs`
- Used to mute verbose security/audit logs in certain deployments

### Share Key Validation

- Validates key length before accessing a share
- Prevents long or malformed keys from causing errors
- Can log warning or throw exception depending on method used

---

## 🛠 Tech Stack

| Layer           | Technology             |
| --------------- | ---------------------- |
| Backend         | Spring Boot 2.5.5      |
| Language        | Java 8                 |
| Build Tool      | Maven                  |
| Persistence     | Spring Data JPA        |
| Database        | ORACLE / MSSQL / MySQL |
| Frontend        | HTML + JavaScript      |
| Template Engine | Thymeleaf              |
| Packaging       | WAR                    |

---

## 🔗 API Endpoints

### Fetch or Create Share

```

GET /share/{key}

```

- Returns existing content
- Creates a new share if it does not exist

### Update Share Content

```

POST /api/share/{key}

```

**Request Body**

```

text/plain

```

- Updates the shared content
- Triggers audit logging

---

## ▶ Running the Project

### Prerequisites

- Java 8
- Maven
- Oracle / MSSQL / MySQL
- Application server (Tomcat / WildFly / GlassFish)

### Build

```

mvn clean package

```

### Deploy

- Deploy generated WAR file to your application server
- Or run using Spring Boot embedded server

---

## ⚙ Configuration

Update database configuration in `application.properties`

```properties

spring.datasource.url=jdbc:sqlserver://localhost;databaseName=yourdb
spring.datasource.username=youruser
spring.datasource.password=yourpassword

# Security
security.blocked-ips=172.191.1.100,172.191.1.101
security.rate.limit.capacity=50
security.rate.limit.refill.seconds=60
security.rate.limit.to.refill=10

```

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## Changelog
See [CHANGELOG](CHANGELOG.md) for the full list of updates and fixes.

---

## 👨‍💻 Authors / Developers

- [![Umair Ali Bhutto](https://img.shields.io/badge/%40author-Umair_Ali_Bhutto-green?style=plastic&logo=github&logoColor=white)](https://github.com/umair-ali-bhutto/)

---
