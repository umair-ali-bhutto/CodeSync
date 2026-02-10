combine these feature and give me code i like the two tabs feature i mean same page but switching content to text and file 

Below, I'll provide a detailed plan and modified code to extend your existing CodeSync application (which is a text/code sharing platform similar to Codeshare.io) to support **file sharing functionality** inspired by services like AirDrop or general file-sharing platforms (e.g., "AirforShare" seems to be a reference to a file-sharing feature). This will allow users to:

- **Share multiple files** via a shareable URL (e.g., `/share/{key}`).
- **Open a new tab** for the sharing interface (we'll add a button to trigger this).
- **View files only on that endpoint URL** (files are tied to the share key and not publicly accessible otherwise; access is restricted via the share URL).
- **Store files in the DB and on your PC** (server's filesystem): Files will be saved as binary data in the DB (for quick retrieval) and also persisted to a local directory on your server (e.g., `/uploads/{key}/`) for redundancy and easier management. This ensures files are available even if the DB is down, and you can back them up from your PC.

### Key Assumptions and Design Decisions
- **Current System Recap**: Your app currently shares a single text blob per share key. We'll extend it to support multiple files per share key.
- **File Types**: Supports any file type (e.g., images, docs, code files). Files are uploaded via the UI and stored securely.
- **Security**: Files are only accessible via the share URL (no direct public access). We'll use Spring Security to protect endpoints. Rate limiting and IP blocking from your existing config will apply.
- **Storage**:
  - **DB**: Files are stored as `BLOB` (binary large objects) in a new entity (`CodeSyncFile`). Metadata (e.g., filename, size) is also stored.
  - **Server Filesystem ("on your PC")**: Files are saved to a local directory (e.g., `C:/uploads/codesync/{key}/` on Windows or `/opt/uploads/codesync/{key}/` on Linux). This is configurable via a property. You can back up or manage files from your PC.
- **UI Changes**: The share page (`sharePage.html`) will now include a file upload section (multiple files), a list of uploaded files (with download/view links), and tabs for viewing text or files. A "New Share Tab" button will open a new tab with a fresh share key.
- **Endpoint**: The existing `/share/{key}` will now show both text (legacy) and files. Files are fetched dynamically.
- **Limitations**: Large files (>10MB) may need tuning (e.g., increase DB blob size or use streaming). No file editing in-browser (just upload/view/download).
- **Dependencies**: Add `spring-boot-starter-web` for multipart (already present), and ensure your DB supports BLOBs (e.g., MSSQL does).
- **Testing**: Test file uploads, downloads, and access restrictions. Ensure the local directory exists and is writable.

### Step-by-Step Implementation Plan
1. **Update Database Schema**:
   - Add a new table `CODE_SYNC_FILE` for files.
   - Modify `CODE_SYNC` to optionally link to files (one-to-many).

2. **Add Configuration**:
   - Add a property for the local upload directory (e.g., in `application.properties`).

3. **Modify Entities**:
   - Create `CodeSyncFile` entity.
   - Update `CodeSync` to have a list of files.

4. **Update Service and Repository**:
   - Modify `CodeSyncService` to handle file uploads/saves.
   - Add `CodeSyncFileRepository`.

5. **Update Controllers**:
   - Modify `CodeSyncController` to handle multipart file uploads.
   - Add endpoints for downloading files.

6. **Update UI (HTML/JS)**:
   - Add file upload, display, and "New Share Tab" features.

7. **Security Updates**:
   - Ensure file downloads are protected.

8. **Build and Deploy**:
   - Update POM if needed (no major changes required).
   - Restart and test.

### Modified Code
I'll provide updated versions of the relevant files. Only changed/added parts are shown for brevity—integrate them into your existing code.

1. **Add to `application.properties`** (or `application.yml`):
   ```
   # Local directory for file storage on your PC/server
   codesync.upload.dir=C:/uploads/codesync  # Change to your preferred path, e.g., /opt/uploads/codesync on Linux
   codesync.max.file.size=10MB  # Max file size
   ```

2. **New Entity: `CodeSyncFile.java`** (Add this file in `com.ag.entity`):
   ```java
   @Entity
   @Table(name = "CODE_SYNC_FILE")
   public class CodeSyncFile {
       @Id
       @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CODE_SYNC_FILE_SEQ")
       @SequenceGenerator(name = "CODE_SYNC_FILE_SEQ", sequenceName = "CODE_SYNC_FILE_SEQ", allocationSize = 1)
       private Long id;

       @ManyToOne(fetch = FetchType.LAZY)
       @JoinColumn(name = "SHARE_KEY", referencedColumnName = "SHARE_KEY", nullable = false)
       private CodeSync codeSync;

       @Column(name = "FILE_NAME", nullable = false)
       private String fileName;

       @Lob
       @Column(name = "FILE_DATA", nullable = false)
       private byte[] fileData;  // Binary data for DB storage

       @Column(name = "FILE_SIZE")
       private Long fileSize;

       @Column(name = "UPLOAD_DATE")
       private Timestamp uploadDate;

       @PrePersist
       public void onCreate() {
           uploadDate = new Timestamp(System.currentTimeMillis());
       }

       // Getters and setters
   }
   ```

3. **Update `CodeSync.java`** (Add the relationship):
   ```java
   @Entity
   @Table(name = "CODE_SYNC", uniqueConstraints = { @UniqueConstraint(columnNames = "SHARE_KEY") })
   public class CodeSync {
       // ... existing fields ...

       @OneToMany(mappedBy = "codeSync", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
       private List<CodeSyncFile> files = new ArrayList<>();

       // ... existing methods ...

       // Add getters/setters for files
   }
   ```

4. **New Repository: `CodeSyncFileRepository.java`** (Add in `com.ag.repository`):
   ```java
   public interface CodeSyncFileRepository extends JpaRepository<CodeSyncFile, Long> {
       List<CodeSyncFile> findByCodeSync_ShareKey(String shareKey);
   }
   ```

5. **Update `CodeSyncService.java`** (Add file handling):
   ```java
   @Service
   @Transactional
   public class CodeSyncService {
       // ... existing fields ...
       private final CodeSyncFileRepository fileRepository;

       @Value("${codesync.upload.dir}")
       private String uploadDir;

       public CodeSyncService(CodeSyncRepository repository, CodeSyncFileRepository fileRepository) {
           this.repository = repository;
           this.fileRepository = fileRepository;
       }

       // ... existing methods ...

       public void saveFiles(String shareKey, MultipartFile[] files) throws IOException {
           CodeSync share = getOrCreate(shareKey);
           for (MultipartFile file : files) {
               if (!file.isEmpty()) {
                   // Save to DB
                   CodeSyncFile codeSyncFile = new CodeSyncFile();
                   codeSyncFile.setCodeSync(share);
                   codeSyncFile.setFileName(file.getOriginalFilename());
                   codeSyncFile.setFileData(file.getBytes());
                   codeSyncFile.setFileSize(file.getSize());
                   fileRepository.save(codeSyncFile);

                   // Save to local filesystem
                   Path shareDir = Paths.get(uploadDir, shareKey);
                   Files.createDirectories(shareDir);
                   Path filePath = shareDir.resolve(file.getOriginalFilename());
                   Files.write(filePath, file.getBytes());
               }
           }
       }

       public List<CodeSyncFile> getFiles(String shareKey) {
           return fileRepository.findByCodeSync_ShareKey(shareKey);
       }

       public CodeSyncFile getFile(Long fileId) {
           return fileRepository.findById(fileId).orElseThrow(() -> new ShareNotFoundException("File not found"));
       }
   }
   ```

6. **Update `CodeSyncController.java`** (Add file upload and download endpoints):
   ```java
   @RestController
   @RequestMapping("/api/share")
   public class CodeSyncController {
       // ... existing fields and methods ...

       @PostMapping("/{key}/files")
       public ResponseEntity<Void> uploadFiles(@PathVariable String key, @RequestParam("files") MultipartFile[] files) {
           try {
               service.saveFiles(key, files);
               return ResponseEntity.ok().build();
           } catch (IOException e) {
               return ResponseEntity.status(500).build();
           }
       }

       @GetMapping("/{key}/files")
       public ResponseEntity<List<Map<String, Object>>> getFiles(@PathVariable String key) {
           List<CodeSyncFile> files = service.getFiles(key);
           List<Map<String, Object>> fileList = files.stream().map(f -> {
               Map<String, Object> map = new HashMap<>();
               map.put("id", f.getId());
               map.put("name", f.getFileName());
               map.put("size", f.getFileSize());
               return map;
           }).collect(Collectors.toList());
           return ResponseEntity.ok(fileList);
       }

       @GetMapping("/file/{fileId}")
       public ResponseEntity<byte[]> downloadFile(@PathVariable Long fileId) {
           CodeSyncFile file = service.getFile(fileId);
           return ResponseEntity.ok()
                   .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                   .body(file.getFileData());
       }
   }
   ```

7. **Update `SecurityConfig.java`** (Allow file downloads only via share):
   ```java
   @Override
   protected void configure(HttpSecurity http) throws Exception {
       // ... existing config ...
       .antMatchers("/api/share/**").permitAll()  // Includes new file endpoints
       // ... rest ...
   }
   ```

8. **Update `sharePage.html`** (Add file upload, display, and new tab button):
   - Replace the `<textarea>` with tabs for text and files.
   - Add upload form and file list.

   ```html
   <!-- ... existing head and styles ... -->

   <body>
       <!-- ... existing header ... -->
       <div class="right">
           <!-- ... existing buttons ... -->
           <button class="share-btn" id="new-tab-btn">🆕 New Share Tab</button>
       </div>
       <!-- ... -->

       <main role="main">
           <div class="tabs">
               <button class="tab-btn active" data-tab="text">Text</button>
               <button class="tab-btn" data-tab="files">Files</button>
           </div>
           <div id="text-tab" class="tab-content active">
               <textarea id="editor" ...></textarea>
           </div>
           <div id="files-tab" class="tab-content">
               <input type="file" id="file-input" multiple style="margin-bottom: 10px;">
               <button class="share-btn" id="upload-btn">📤 Upload Files</button>
               <ul id="file-list"></ul>
           </div>
       </main>

       <!-- ... existing footer ... -->

       <script>
           // ... existing scripts ...

           // New Tab Button
           document.getElementById('new-tab-btn').addEventListener('click', () => {
               window.open('/codesync/share/' + Math.random().toString(36).substr(2, 9), '_blank');
           });

           // Tabs
           document.querySelectorAll('.tab-btn').forEach(btn => {
               btn.addEventListener('click', () => {
                   document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                   document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
                   btn.classList.add('active');
                   document.getElementById(btn.dataset.tab + '-tab').classList.add('active');
               });
           });

           // File Upload
           document.getElementById('upload-btn').addEventListener('click', async () => {
               const files = document.getElementById('file-input').files;
               if (files.length === 0) return alert('No files selected');
               const formData = new FormData();
               for (let file of files) formData.append('files', file);
               showLoader();
               try {
                   await fetch(`/codesync/api/share/${shareKey}/files`, { method: 'POST', body: formData });
                   loadFiles();
               } catch (e) { console.error(e); } finally { hideLoader(); }
           });

           // Load and Display Files
           async function loadFiles() {
               const res = await fetch(`/codesync/api/share/${shareKey}/files`);
               const files = await res.json();
               const list = document.getElementById('file-list');
               list.innerHTML = files.map(f => `<li><a href="/codesync/api/share/file/${f.id}" download>${f.name} (${(f.size / 1024).toFixed(2)} KB)</a></li>`).join('');
           }
           loadFiles();
       </script>
   </body>
   </html>
   ```

   - Add CSS for tabs (in `<style>`):
     ```css
     .tabs { display: flex; margin-bottom: 10px; }
     .tab-btn { padding: 10px; cursor: pointer; background: #eee; border: none; }
     .tab-btn.active { background: var(--accent); color: #fff; }
     .tab-content { display: none; }
     .tab-content.active { display: block; }
     #file-list { list-style: none; padding: 0; }
     #file-list li { margin: 5px 0; }
     ```

9. **Update `pom.xml`** (No major changes, but ensure multipart is supported—it's already in `spring-boot-starter-web`).

### How to Test and Deploy
- **Run the App**: Start your Spring Boot app. Access `/share/{key}` to see the updated UI.
- **Upload Files**: Select multiple files, upload, and verify they appear in the list and are downloadable.
- **New Tab**: Click "New Share Tab" to open a fresh share in a new tab.
- **Access Control**: Files are only downloadable via the share URL (e.g., `/api/share/file/{id}` is protected).
- **Storage**: Check your local directory (e.g., `C:/uploads/codesync/{key}/`) for files, and the DB for blobs.
- **Edge Cases**: Test large files, invalid keys, and security (e.g., direct file URL access should be blocked).

If you encounter issues (e.g., DB blob limits), let me know for refinements! This should give you a solid file-sharing extension.


This is a great upgrade. Moving from text-based sharing (Codeshare) to file-based sharing (Airforshare) requires handling **Multipart** data, managing **File I/O**, and updating your **Security** and **Database** schemas.

To achieve this, we need to create a new Entity for files, a Service to handle disk storage, and a new UI page.

### 1. The Database Entity (`FileEntity.java`)

We need to track where the file is stored on your PC and link it to the `shareKey`.

```java
@Entity
@Table(name = "FILE_SYNC")
public class FileSync {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shareKey;
    private String fileName;
    private String filePath; // Path on your PC
    private String contentType;
    private long fileSize;

    @Column(name = "CREATED_AT")
    private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

    // Getters and Setters...
}

```

---

### 2. File Service (`FileShareService.java`)

This service handles saving the file to your **PC's local drive** and saving the metadata to the **DB**.

```java
@Service
public class FileShareService {
    @Autowired
    private FileSyncRepository fileRepo;

    // Define your local storage path here
    private final String UPLOAD_DIR = "C:/CodeSyncUploads/"; 

    public void storeFiles(String key, MultipartFile[] files) throws IOException {
        File directory = new File(UPLOAD_DIR + key);
        if (!directory.exists()) directory.mkdirs();

        for (MultipartFile file : files) {
            String fullPath = UPLOAD_DIR + key + "/" + file.getOriginalFilename();
            file.transferTo(new File(fullPath)); // Save to PC

            FileSync entity = new FileSync();
            entity.setShareKey(key);
            entity.setFileName(file.getOriginalFilename());
            entity.setFilePath(fullPath);
            entity.setContentType(file.getContentType());
            entity.setFileSize(file.getSize());
            fileRepo.save(entity); // Save to DB
        }
    }

    public List<FileSync> getFiles(String key) {
        return fileRepo.findByShareKey(key);
    }
}

```

---

### 3. The New Controller (`FileShareController.java`)

This handles the API calls for uploading and viewing files.

```java
@RestController
@RequestMapping("/api/files")
public class FileShareController {
    
    @Autowired
    private FileShareService fileService;

    @PostMapping("/{key}")
    public ResponseEntity<?> upload(@PathVariable String key, @RequestParam("files") MultipartFile[] files) {
        try {
            fileService.storeFiles(key, files);
            return ResponseEntity.ok("Files uploaded successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/{key}")
    public List<FileSync> listFiles(@PathVariable String key) {
        return fileService.getFiles(key);
    }
}

```

---

### 4. Update Security Configuration

You must permit the new endpoints in your `SecurityConfig.java`.

```java
// Inside your configure(HttpSecurity http) method
.authorizeRequests()
.antMatchers("/api/share/**").permitAll()
.antMatchers("/api/files/**").permitAll() // ADD THIS
.antMatchers("/share/**").permitAll()
.antMatchers("/files/**").permitAll()     // ADD THIS

```

---

### 5. UI Implementation (Frontend)

In your `sharePage.html`, add a button to open the new file sharing tab.

#### Add Button to Header:

```html
<button class="share-btn" id="file-share-btn" style="background: #e67e22;">📁 File Share</button>

```

#### JavaScript for New Tab:

```javascript
document.getElementById('file-share-btn').addEventListener('click', () => {
    // Opens a new tab specifically for files under this share key
    window.open('/files/' + shareKey, '_blank');
});

```

#### The File Share UI (`fileShare.html`):

Create a new Thymeleaf template for the file view. Use a simple input for files:

```html
<div class="container">
    <h2>File Share for: [[${shareKey}]]</h2>
    <input type="file" id="fileInput" multiple>
    <button onclick="uploadFiles()">Upload to PC & DB</button>
    
    <div id="fileList">
        </div>
</div>

<script>
async function uploadFiles() {
    const input = document.getElementById('fileInput');
    const formData = new FormData();
    for (const file of input.files) {
        formData.append("files", file);
    }

    await fetch("/api/files/[[${shareKey}]]", {
        method: "POST",
        body: formData
    });
    location.reload(); // Refresh to see new files
}
</script>

```

---

### Key Improvements Summary:

* **Storage:** Files are stored physically on your PC in `C:/CodeSyncUploads/{key}/`.
* **Database:** Metadata (size, name, path) is stored in the `FILE_SYNC` table.
* **Isolation:** Users can only see files if they know the specific `shareKey` URL.
* **UX:** Clicking "File Share" opens a fresh tab so the user doesn't lose their code editor.

**Would you like me to provide the complete standalone HTML code for the `fileShare.html` page with styling to match your dark theme?**


This is a great upgrade. Moving from text-based sharing (Codeshare) to file-based sharing (Airforshare) requires handling **Multipart** data, managing **File I/O**, and updating your **Security** and **Database** schemas.

To achieve this, we need to create a new Entity for files, a Service to handle disk storage, and a new UI page.

### 1. The Database Entity (`FileEntity.java`)

We need to track where the file is stored on your PC and link it to the `shareKey`.

```java
@Entity
@Table(name = "FILE_SYNC")
public class FileSync {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String shareKey;
    private String fileName;
    private String filePath; // Path on your PC
    private String contentType;
    private long fileSize;

    @Column(name = "CREATED_AT")
    private Timestamp createdAt = new Timestamp(System.currentTimeMillis());

    // Getters and Setters...
}

```

---

### 2. File Service (`FileShareService.java`)

This service handles saving the file to your **PC's local drive** and saving the metadata to the **DB**.

```java
@Service
public class FileShareService {
    @Autowired
    private FileSyncRepository fileRepo;

    // Define your local storage path here
    private final String UPLOAD_DIR = "C:/CodeSyncUploads/"; 

    public void storeFiles(String key, MultipartFile[] files) throws IOException {
        File directory = new File(UPLOAD_DIR + key);
        if (!directory.exists()) directory.mkdirs();

        for (MultipartFile file : files) {
            String fullPath = UPLOAD_DIR + key + "/" + file.getOriginalFilename();
            file.transferTo(new File(fullPath)); // Save to PC

            FileSync entity = new FileSync();
            entity.setShareKey(key);
            entity.setFileName(file.getOriginalFilename());
            entity.setFilePath(fullPath);
            entity.setContentType(file.getContentType());
            entity.setFileSize(file.getSize());
            fileRepo.save(entity); // Save to DB
        }
    }

    public List<FileSync> getFiles(String key) {
        return fileRepo.findByShareKey(key);
    }
}

```

---

### 3. The New Controller (`FileShareController.java`)

This handles the API calls for uploading and viewing files.

```java
@RestController
@RequestMapping("/api/files")
public class FileShareController {
    
    @Autowired
    private FileShareService fileService;

    @PostMapping("/{key}")
    public ResponseEntity<?> upload(@PathVariable String key, @RequestParam("files") MultipartFile[] files) {
        try {
            fileService.storeFiles(key, files);
            return ResponseEntity.ok("Files uploaded successfully");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/{key}")
    public List<FileSync> listFiles(@PathVariable String key) {
        return fileService.getFiles(key);
    }
}

```

---

### 4. Update Security Configuration

You must permit the new endpoints in your `SecurityConfig.java`.

```java
// Inside your configure(HttpSecurity http) method
.authorizeRequests()
.antMatchers("/api/share/**").permitAll()
.antMatchers("/api/files/**").permitAll() // ADD THIS
.antMatchers("/share/**").permitAll()
.antMatchers("/files/**").permitAll()     // ADD THIS

```

---

### 5. UI Implementation (Frontend)

In your `sharePage.html`, add a button to open the new file sharing tab.

#### Add Button to Header:

```html
<button class="share-btn" id="file-share-btn" style="background: #e67e22;">📁 File Share</button>

```

#### JavaScript for New Tab:

```javascript
document.getElementById('file-share-btn').addEventListener('click', () => {
    // Opens a new tab specifically for files under this share key
    window.open('/files/' + shareKey, '_blank');
});

```

#### The File Share UI (`fileShare.html`):

Create a new Thymeleaf template for the file view. Use a simple input for files:

```html
<div class="container">
    <h2>File Share for: [[${shareKey}]]</h2>
    <input type="file" id="fileInput" multiple>
    <button onclick="uploadFiles()">Upload to PC & DB</button>
    
    <div id="fileList">
        </div>
</div>

<script>
async function uploadFiles() {
    const input = document.getElementById('fileInput');
    const formData = new FormData();
    for (const file of input.files) {
        formData.append("files", file);
    }

    await fetch("/api/files/[[${shareKey}]]", {
        method: "POST",
        body: formData
    });
    location.reload(); // Refresh to see new files
}
</script>

```

---

### Key Improvements Summary:

* **Storage:** Files are stored physically on your PC in `C:/CodeSyncUploads/{key}/`.
* **Database:** Metadata (size, name, path) is stored in the `FILE_SYNC` table.
* **Isolation:** Users can only see files if they know the specific `shareKey` URL.
* **UX:** Clicking "File Share" opens a fresh tab so the user doesn't lose their code editor.

**Would you like me to provide the complete standalone HTML code for the `fileShare.html` page with styling to match your dark theme?**