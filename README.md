# CodeSync

<p align="center">
  <a href="https://github.com/umair-ali-bhutto/" target="_blank">
    <img src="https://umair-ali-bhutto.github.io/assets/CodePenIcon/logo.png" width="100px" height="100px" alt="logo"><br/>
  </a>
</p>

A simple **Codeshare.io–like** application built with **Spring Boot 2.5.5** and **Java 8**.  
It allows users to share text in real time using a URL-based key — no authentication required.

---

## 🚀 Features

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
- Persistent storage using JPA (MySQL / MSSQL)
- No authentication (MVP)
- WAR packaging (deployable on external servers)

---

## 🛠 Tech Stack

| Layer           | Technology        |
| --------------- | ----------------- |
| Backend         | Spring Boot 2.5.5 |
| Language        | Java 8            |
| Build Tool      | Maven             |
| Persistence     | Spring Data JPA   |
| Database        | ORACLE / MSSQL    |
| Frontend        | HTML + JavaScript |
| Template Engine | Thymeleaf         |
| Packaging       | WAR               |

---

## 📂 Project Structure

```

src/main/java
|
├── com.ag.CodeSync
│   ├── ServletInitializer.java
│   └── CodeSyncApplication.java
├── com.ag.controller
│   ├── CodeSyncController.java
│   └── SharePageController.java
├── com.ag.service
│   └── CodeSyncService.java
├── com.ag.repository
│   └── CodeSyncRepository.java
├── com.ag.entity
    └── CodeSync.java

src/main/resources
├── templates
│   └── share.html
└── application.properties

```

---

## 🗄 Database Schema

### Table: `CODE_SHARE`

| Column     | Type                | Description    |
| ---------- | ------------------- | -------------- |
| id         | BIGINT              | Sequence       |
| share_key  | VARCHAR(100) UNIQUE | URL identifier |
| content    | VARCHAR(MAX)        | Shared text    |
| created_at | DATETIME            | Created time   |
| updated_at | DATETIME            | Last update    |

---

## 🔗 API Endpoints

### Fetch or Create Share

```

GET /api/share/{key}

```

- Returns existing content
- Creates a new share if it does not exist

---

### Update Share Content

```

PUT /api/share/{key}

```

**Request Body**

```

text/plain

```

---

### Delete Share

```

DELETE /api/share/{key}

```

---

## 🌐 Web UI

### Access Shared Editor

```

/share/{key}

```

Example:

[http://localhost:8080/share/umair](http://localhost:8080/share/umair)

All users opening the same URL will see and edit the same content.

---

## 🔄 How Real-Time Sync Works

This MVP uses **polling**:

- Editor auto-saves after typing stops (500ms debounce)
- Browser polls backend every 2 seconds
- If content changes, editor updates automatically

✅ Simple  
✅ Reliable  
❌ Not instant (acceptable for MVP)

---

## 🧠 How This Mimics Codeshare.io

| Codeshare.io    | This Project   |
| --------------- | -------------- |
| URL-based rooms | `/share/{key}` |
| Shared editor   | HTML textarea  |
| Auto-save       | Debounced PUT  |
| Live updates    | Polling        |
| No login        | Public access  |

---

## ▶ Running the Project

### Prerequisites

- Java 8
- Maven
- MySQL or MSSQL
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

Update database configuration in: application.properties

````

Example:
```properties
spring.datasource.url=jdbc:sqlserver://localhost:databaseName=db
spring.datasource.username=secret
spring.datasource.password=secret

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

````

---

## 🔐 Security

- No authentication (by design for MVP)
- Public URLs
- No access restrictions

⚠️ **Do not use as-is for sensitive data**

---

## 🔮 Future Enhancements

- WebSocket-based real-time updates
- Syntax highlighting
- Read-only mode
- Share expiration (TTL)
- Version history
- Authentication & permissions

---

## 📚 Learning Purpose

This project is intentionally kept:

- Simple
- Easy to understand
- Layered properly
- Java 8 compatible

It is ideal for:

- Spring Boot beginners
- Interview preparation
- Internal tools
- MVP prototypes

---

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Authors / Developers

- [![Umair Ali Bhutto](https://img.shields.io/badge/%40author-Umair_Ali_Bhutto-green?style=plastic&logo=github&logoColor=white)](https://github.com/umair-ali-bhutto/)
