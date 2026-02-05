# PeerLink - Adaptive File Sharing Backend

<div align="center">

![PeerLink Logo](https://img.shields.io/badge/PeerLink-Adaptive%20Backend-667eea?style=for-the-badge&logo=spring&logoColor=white)

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat-square&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)](LICENSE)

**A high-performance, network-aware backend service designed to optimize file transfers across varying network conditions.**

[Features](#-features) • [Architecture](#-architecture) • [Installation](#-installation) • [API Reference](#-api-reference) 

</div>

---

## 📋 Table of Contents

- [Problem Statement](#-problem-statement)
- [Solution Overview](#-solution-overview)
- [✨ Features](#-features)
- [🏗️ Architecture](#-architecture)
- [File Upload Evolution (v2.0)](#-file-upload-evolution-v20)
- [🛠️ Tech Stack](#-tech-stack)
- [📦 Installation](#-installation)
- [⚙️ Configuration](#-configuration)
- [📚 API Reference](#-api-reference)

---

## ⚠️ Problem Statement

Traditional file-sharing systems suffer from critical limitations:

| Problem | Impact |
|---------|--------|
| **Static Chunking** | Fixed chunk sizes cause timeouts on slow networks |
| **No Adaptation** | Uniform compression wastes bandwidth on pre-compressed files |
| **Manual Sharing** | No device discovery; requires external link copying |
| **Poor Resume Support** | Connection drops restart uploads from 0% |
| **Security Gaps** | Public links lack expiration or revocation controls |

---

## ✅ Solution Overview

**PeerLink** introduces **network-aware intelligence** to file transfers:

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT                                   │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐    │
│  │ File Select │───▶│ Network      │───▶│ Upload with     │    │
│  │             │    │ Measurement  │    │ Custom Headers  │    │
│  └─────────────┘    └──────────────┘    └─────────────────┘    │
│                            │                      │              │
│                   X-Network-Speed         X-Latency-Ms          │
│                   X-Device-Type                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         SERVER                                   │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐    │
│  │ Intelligence│───▶│ Adaptive     │───▶│ Compress &      │    │
│  │ Engine      │    │ Chunking     │    │ Store           │    │
│  └─────────────┘    └──────────────┘    └─────────────────┘    │
│        │                                         │              │
│   ChunkSize: 16KB-8MB                    GZIP Compression       │
│   Compression: 1-9                                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## ✨ Features

### Core Backend Features

| Feature | Description |
|---------|-------------|
| **🔄 Adaptive Chunking** | Dynamically calculates optimal chunk sizes (16KB - 8MB) based on real-time client metrics |
| **📦 Smart Session Sync** | State-aware tracking of received chunks to enable zero-loss resumption |
| **🔐 JWT Security** | Stateless, secure token-based authentication using Spring Security |
| **👥 Peer Discovery** | IP-based neighbor detection for localized file sharing requests |
| **🔗 Public Access** | Revocable, time-bound public download token generation |
| **🧹 Auto-Cleanup** | Scheduled background tasks to prune orphan chunks and expired sessions |

---

## 🏗️ Architecture

### Application Layers

```
┌──────────────────────────────────────────────────────────────┐
│               APPLICATION LAYER (Spring Boot)                 │
│  ┌────────────────┐  ┌─────────────────┐  ┌───────────────┐ │
│  │ AuthController │  │ ChunkedUpload   │  │DownloadCtrl   │ │
│  └────────────────┘  │ Controller v2.0 │  └───────────────┘ │
│  ┌────────────────┐  ├─────────────────┴──┬───────────────┤ │
│  │ Spring Security│  │ IntelligenceSvc    │ FileStorageSvc│ │
│  └────────────────┘  └────────────────────┴───────────────┘ │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│                     DATA LAYER                                │
│  ┌─────────────────────┐    ┌─────────────────────┐         │
│  │   MySQL Database    │    │   Local File System │         │
│  │   (Metadata)        │    │   (Compressed Files)│         │
│  └─────────────────────┘    └─────────────────────┘         │
└──────────────────────────────────────────────────────────────┘
```

---

## 🚀 File Upload Evolution (v2.0)

### v2.0: Smart Chunked & Resume-Aware ✅
The backend implements a multi-stage chunked upload lifecycle:
1.  **Initialization**: Creates a scoped upload session and predicts optimal chunking strategies.
2.  **Streaming Transfer**: Receives individual binary chunks with idempotency checks.
3.  **Atomic Assembly**: Assembles chunks into the final file and updates global metadata.
4.  **Resilience**: Full support for interrupted transfers via the `receivedChunks` status list.

---

## 🛠️ Tech Stack

- **Core**: Java 21 (OpenJDK)
- **Framework**: Spring Boot 3.x
- **Security**: Spring Security & JWT
- **ORM**: Hibernate JPA
- **Database**: MySQL 8.0
- **Compression**: GZIP Adaptive Engine

---

## 📦 Installation

```bash
# Clone the repository
git clone https://github.com/Eshwar863/PeerLink.git

# Navigate to backend
cd PeerLink-backend

# Build and run
mvn clean install
mvn spring-boot:run
```

---

## 📚 API Reference

### Chunked Upload (v2.0)
| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/files/chunked/init` | Initialize a scoped upload session. |
| `POST` | `/files/chunked/upload`| Receive individual file segments. |
| `GET` | `/files/chunked/status/{id}` | Retrieve list of received segments for resumption. |
| `DELETE`| `/files/chunked/cancel/{id}` | Terminate session and purge temporary segments. |

### File & Discovery
- **`/files/download/{id}`**: Authenticated secure download.
- **`/discovery/nearby`**: Local network peer detection.
- **`/fileshare/share/{id}/link`**: Generate shareable public links.

---

## � Known Issues & Fixes

### Issue #1: Resume Bug (Frontend Triggered)
**Problem**: Each "Initialize" button click generates a new `uploadId`, orphaning the previous session and resetting progress to 0%.

**Root Cause**: Frontend calls `/files/chunked/init` on every upload attempt without checking for an existing active session.

**Fix Required**:
```javascript
// Frontend: Check existing session before init
const savedId = localStorage.getItem('currentUploadId');
if (savedId) {
  const status = await axios.get(`/files/chunked/status/${savedId}`);
  if (status.data.status === 'UPLOADING') {
    return savedId; // Resume existing
  }
}
// Start new session only if no active session exists
```

### Issue #2: In-Memory Session Volatility
**Problem**: Server restarts wipe all active upload sessions from the `ConcurrentHashMap`, forcing users to restart from 0%.

**Impact**:
- ❌ Server restart → All sessions lost
- ❌ Single instance only (no shared state for load balancing)

**Planned Fix**: Migration to **Redis** for persistent, cluster-wide session storage (v3.0).

---

## �🔮 Future Enhancements

### 1. Real-Time Adaptation Module
Currently, network metrics are measured once at upload start. The planned enhancement will:
- **Client-Side Splitting**: Split files into multiple chunks on the client-side.
- **Per-Chunk Throughput Measurement**: Measure actual throughput after each chunk transfer.
- **Dynamic Recalculation**: Dynamically recalculate the next chunk size based on observed speed.
- **Network Drop Handling**: Gracefully handle network drops mid-transfer without timeout errors.

```java
public int calculateNextChunkSize(float currentSpeed, int previousSize) {
    if (currentSpeed > previousSpeed * 1.5) {
        return Math.min(previousSize * 2, MAX_CHUNK_SIZE); // Increase
    } else if (currentSpeed < previousSpeed * 0.5) {
        return Math.max(previousSize / 2, MIN_CHUNK_SIZE); // Decrease
    }
    return previousSize; // Keep same
}
```

### 2. Redis Session Persistence (v3.0)
Replace in-memory `ConcurrentHashMap` with **Redis** to allow server restarts without losing upload progress and to support horizontal scaling across multiple nodes.

### 3. WebRTC P2P Transfer (v4.0)
- Direct peer-to-peer file transfer for local users.
- Bypass server for same-network transfers.

---
*Developed for the PeerLink Distributed File Sharing Ecosystem.*
