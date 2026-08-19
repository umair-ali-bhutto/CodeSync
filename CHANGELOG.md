# Change Log

All notable changes to the "CodeSync" project will be documented in this file.

Check [Keep a Changelog](http://keepachangelog.com/) for recommendations on how to structure this file.

---

## [V-2.1.2] 19-AUG-2026

### Added 19-AUG-2026

- added date time format in zip

### Changed 19-AUG-2026

- fixed large zip download issue

## [V-2.1.1] 19-JUL-2026

### Added 19-JUL-2026

- h2 Database support
- direct file deletion functionality
- added highlightjs https://github.com/highlightjs/highlight.js/blob/main/README.md
- added link to changelog
- added BANNER.txt
- some stats logged as well

### Changed 19-JUL-2026

- minor bugs fixes with local ips

## [V-2.1.0] 09-JUN-2026 <--> 14-JUL-2026

### Added 09-JUN-2026

- Added resilience4j for retries

### Changed 09-JUN-2026

- updated exception auth controller for proper appropriate responses
- Fixed error page routing
- some minor bugs fixation

### Added 10-JUN-2026

- Added ip blocked page for user to see that which ip is blocked
- Added Dynamic Ip Blocking And Unblocking Feature
- Added Download all files into zip Feature

### Changed 10-JUN-2026

- Fixed hard coded context roots in html
- Fixed Error page routing

### Added 12-JUN-2026

- Added Database Archiving
- Added Swagger

### Added 17-JUN-2026

- Added Links To Error Reporting And Feedback (created seperate dialog)

### Changed 19-JUN-2026

- made log service controller local only and for allowed ips

### Changed 07-JUL-2026

- changed log file cleanup to 10 days

### Changed 14-JUL-2026

- Added version date from application.properties

## [V-2.0.0] 10-MAY-2026 <--> 18-MAY-2026

### Changed 10-MAY-2026

- Migrated Project to Java 25 Spring boot 4

### Added 10-MAY-2026

- File Sharing Feature
- Added File Expiry

### Modification 11-MAY-2026

- Fixed Bugs In File Transfer
- Fixed File Uploading Restrictions

### Changed 14-MAY-2026

- added response log

### Added 14-MAY-2026

- Logo added on share page

### Changed 15-MAY-2026

- added custom scrollbar on share page

### Changed 18-MAY-2026

- fixed issue of file size hard coded

### Added 18-MAY-2026

- ligth/dark mode state saving feature for perticular keys

### Added 03-JUN-2026

- Winscp support so we can run on windows and still be able to move files to linux server (optional)
- Fixed Minor Issues

---

## [V-1.1.6] 8-APR-2026

### Changed

- Fixed Dependabot Alert For JSON library

---

## [V-1.1.5] 30-MAR-2026

### Changed

- Changed Some Texts on Share Page
- Added method to show user HTTP call status in headers

---

## [V-1.1.4] 30-MAR-2026

### Added

- Added New Endpoint For Dashboard to see various summary
- Added CSV Download for Audit in Dashboard

---

## [V-1.1.3] 13-FEB-2026

### Changed

- Updated Readme.md File

---

## [V-1.1.2] 13-FEB-2026

### Added

- Added ChangeLog File

---

## [V-1.1.1] 13-FEB-2026

### Added

- Added Client IP name fetching from known clients IP Addresses

---

## [V-1.1.0] 13-FEB-2026

### Added

- Added Security to endpoints and removed delete share code

---

## [V-1.0.9] 13-FEB-2026

### Changed

- Updated version in html

---

## [V-1.0.8] 13-FEB-2026

### Fixed

- Security patch

---

## [V-1.0.7] 10-FEB-2026

### Added

- Working on File Sharing Feature

---

## [V-1.0.6] 10-FEB-2026

### Added

- Added copy and clear text button and minor fixes

---

## [V-1.0.5] 04-FEB-2026

### Added

- User agent and browser auditing with logs and DB

---

## [V-1.0.4] 03-FEB-2026

### Fixed

- Body length too large printing log issue fixed

---

## [V-1.0.3] 29-JAN-2026

### Added

- Bucket4j for rate limiting
- Audit log
- Minor bug fixes

---

## [V-1.0.2] 26-JAN-2026

### Added

- Favicon and Open Graph metadata

---

## [V-1.0.1] 23-JAN-2026

### Changed

- Deployment context root updated
- More features added and bug fixes

---

## [V-1.0.0] 22-JAN-2026

### Initial commit

- Project initialized

### Added

- Added code
