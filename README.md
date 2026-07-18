# 💼 JobA!
### 원하는 채용공고, 이제 직접 찾지 마세요
### IT 채용공고 자동 수집 · AI 매칭 · 실시간 알림 서비스

<!-- 📷 여기에 대표 이미지(로고/커버) 삽입 -->

---

## 📌 Project Overview
JobA!는 사용자가 채용공고를 직접 찾는 대신,
**조건에 맞는 공고를 자동으로 수집**하고
**AI가 이력서와 매칭**하여
**적합한 공고를 실시간으로 알려 주는** 채용 준비 자동화 서비스입니다.

공고 수집부터 이력서 매칭, 알림 발송, 지원 현황 관리까지
채용 준비의 모든 과정을 하나로 연결했습니다.

> 🏷️ TAVE 17th Project

---

## 🎯 Problem & Solution

### ❗ Problem
- 취준생 절반 이상이 사람인·잡코리아·원티드 등 **2개 이상의 플랫폼을 동시에** 사용
- **수시채용 전환**으로 공고를 놓칠 위험이 커짐
- 공고를 **하나씩 직접 읽어 봐야** 나와 맞는지 알 수 있음
- 새 공고가 올라왔는지 **계속 들어가 반복 확인**해야 하는 피로
- 왜 이 공고가 나에게 맞는지, **추천 근거를 알기 어려움**

### ✅ Solution
- 사기업·공공기관 공고를 **자동으로 통합 수집**
- 이력서를 AI가 분석해 공고별 **적합도를 0~100점으로 산출**
- **보유 기술 / 부족 기술**을 자동 분석해 **매칭 이유를 함께 제시**
- 설정한 점수 기준을 넘는 공고가 올라오면 **이메일·Slack·Discord로 즉시 알림**
- 키워드가 아닌 **문장(자연어)으로 검색**하면 의도에 맞는 공고 탐색

---

## ✨ Key Features
- 🔎 **자동 수집 & 실시간 알림** (사기업·공공기관 공고 통합 수집 → 조건 충족 시 Slack·Email·Discord 알림)
- 🤖 **AI 기반 맞춤 매칭** (이력서 기반 적합도 점수 + 보유·부족 기술 + 추천 근거 제공)
- 💬 **자연어 공고 검색** (문장으로 입력하면 AI가 검색 의도를 이해해 공고 탐색)
- 📄 **AI 공고 요약** (공고를 요약하고 이력서와 비교 분석해 적합도·추천 이유 표시)
- 🗂️ **입사 지원 현황 트래커** (지원 단계·일정을 자동 정리, 다가오는 일정 요약)
- ⭐ **스크랩 & 마감 임박 알림** (관심 공고 저장, 마감 가까운 공고 자동 취합)
- ⚙️ **온보딩 기반 맞춤 설정** (희망 직무·근무 지역·고용 형태·적합도 기준·알림 채널)

---

## 🚀 Technical Highlights
- 🧭 **이원화된 공고 수집 전략**
  - 사기업: **YAML 설정 기반 크롤러 + Jsoup**으로 사이트별 수집 규칙 유연 관리
  - 공공기관: **공공 데이터 API + Apache PDFBox**로 문서형 공고 파싱
  - 사기업·공기업 데이터 특성에 맞춰 **서로 다른 점수 산식** 적용

- ⏱️ **스케줄링 기반 자동 수집 파이프라인**
  - `DailyJobScheduler` (Quartz) 기반으로 수집 → 정제 → 저장 파이프라인 자동화
  - 주 수집 / 보조 수집 파이프라인 분리로 안정적인 데이터 적재

- 🤖 **AI 매칭 파이프라인**
  - 이력서 파싱 → 공고와 비교 분석 → **적합도 0~100점** 산출
  - 보유·부족 기술 자동 추출 및 **매칭 근거 생성**
  - 사기업 / 공기업 각각의 AI 파이프라인 분리 설계

- 🔔 **임계값 기반 실시간 알림**
  - 사용자가 설정한 점수 기준을 넘는 공고 발생 시 **Slack·Email·Discord** 즉시 발송

- 🗣️ **자연어 검색 파이프라인**
  - 문장 형태 질의를 해석해 의도에 맞는 공고를 검색·랭킹

- ⚡ **Redis 캐싱**
  - 반복 조회 데이터 캐싱으로 응답 속도 및 부하 개선

---

## 📱 Screen Preview

<!-- 📷 여기에 온보딩 화면 이미지 삽입 (기본정보 · 직무설정 · 이력서 · 알림설정) -->

<!-- 📷 여기에 홈 / AI 적합도 추천 화면 이미지 삽입 -->

<!-- 📷 여기에 자연어 검색(Smart Search) 화면 이미지 삽입 -->

<!-- 📷 여기에 공고 상세(Job Details) 화면 이미지 삽입 -->

<!-- 📷 여기에 지원 현황 트래커(Application Tracker) 화면 이미지 삽입 -->

---

## 🛠 Tech Stack

### 🛠 Backend & Framework
<div>
  <img src="https://img.shields.io/badge/Java 17-007396?style=flat-square&logo=java&logoColor=white">
  <img src="https://img.shields.io/badge/Gradle-02303A?style=flat-square&logo=gradle&logoColor=white">
  <img src="https://img.shields.io/badge/Spring Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/Spring Data JPA-6DB33F?style=flat-square&logo=hibernate&logoColor=white">
</div>

### 🔐 Authentication & Security
<div>
  <img src="https://img.shields.io/badge/Spring Security-6DB33F?style=flat-square&logo=springsecurity&logoColor=white">
  <img src="https://img.shields.io/badge/OAuth2-000000?style=flat-square&logo=oauth&logoColor=white">
  <img src="https://img.shields.io/badge/JWT-000000?style=flat-square&logo=jsonwebtokens&logoColor=white">
</div>

### 🗄 Database & Infrastructure
<div>
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white">
  <img src="https://img.shields.io/badge/Amazon ElastiCache-C925D1?style=flat-square&logo=amazonelasticache&logoColor=white">
  <img src="https://img.shields.io/badge/AWS EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white">
</div>

### 🧲 Data Collection & Scheduling
<div>
  <img src="https://img.shields.io/badge/Jsoup-1E9E4A?style=flat-square&logo=java&logoColor=white">
  <img src="https://img.shields.io/badge/Quartz Scheduler-006699?style=flat-square&logo=quartz&logoColor=white">
  <img src="https://img.shields.io/badge/Apache PDFBox-D22128?style=flat-square&logo=apache&logoColor=white">
  <img src="https://img.shields.io/badge/공공데이터 API-003D7C?style=flat-square&logo=data&logoColor=white">
</div>

### 🔔 Notification
<div>
  <img src="https://img.shields.io/badge/Slack-4A154B?style=flat-square&logo=slack&logoColor=white">
  <img src="https://img.shields.io/badge/Discord-5865F2?style=flat-square&logo=discord&logoColor=white">
  <img src="https://img.shields.io/badge/Email-EA4335?style=flat-square&logo=gmail&logoColor=white">
</div>

### 🚀 DevOps
<div>
  <img src="https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white">
  <img src="https://img.shields.io/badge/GitHub Actions-2088FF?style=flat-square&logo=githubactions&logoColor=white">
  <img src="https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white">
</div>

### 🧪 Docs & Test
<div>
  <img src="https://img.shields.io/badge/Swagger-85EA2D?style=flat-square&logo=swagger&logoColor=black">
  <img src="https://img.shields.io/badge/JUnit5-25A162?style=flat-square&logo=junit5&logoColor=white">
</div>

---

## 🔄 Pipeline

### 📥 공고 수집 파이프라인 (DailyJobScheduler)
<!-- 📷 여기에 공고 수집 파이프라인 다이어그램 삽입 -->

### 🔀 공고 수집 파이프라인 2 (보조 수집 · AI 파이프라인)
<!-- 📷 여기에 공기업/사기업 수집 · AI 파이프라인 다이어그램 삽입 -->

### 🔎 검색 파이프라인
<!-- 📷 여기에 자연어 검색 파이프라인 다이어그램 삽입 -->

---

## 🏗 Architecture
<!-- 📷 여기에 JobA! AWS Architecture 이미지 삽입 -->

---

## 🗄 ERD
<!-- 📷 여기에 ERD 이미지 삽입 -->

---

## 📖 API Documentation
프로젝트의 전체 API 명세는 Swagger를 통해 확인할 수 있습니다.

### 🔗 Documentation
* Swagger UI : `[http://{server-host}/swagger-ui/index.html](https://api.jobai.site/swagger-ui/index.html#/)`

## 📂 Directory Structure
```text
📦 src/main/java/com/jobai/backend
 ├── 📁 domain                    # 핵심 비즈니스 로직
 │   ├── 📁 ai                    # AI 서비스 연동 (client / dto / exception)
 │   ├── 📁 application           # 지원서 관리
 │   ├── 📁 auth                  # 인증 (OAuth2)
 │   ├── 📁 bloom                 # Bloom Filter 기반 공고 중복 제거
 │   ├── 📁 crawler               # 채용공고 크롤링 파이프라인
 │   │   ├── 📁 classify              # 공고 분류
 │   │   ├── 📁 engine                # 크롤링 엔진
 │   │   ├── 📁 runner                # 수집 실행
 │   │   ├── 📁 scheduler             # 수집 스케줄러 (DailyJobScheduler)
 │   │   ├── 📁 spec                  # 기업별 크롤링 스펙 로직
 │   │   ├── 📁 summary               # AI 공고 요약
 │   │   └── 📁 export                # 수집 결과 내보내기
 │   ├── 📁 home                  # 홈 화면
 │   ├── 📁 member                # 회원 관리 / 온보딩
 │   ├── 📁 notification          # 알림 (Slack·Email·Discord)
 │   ├── 📁 publicInstitution     # 공기업 채용공고 수집
 │   ├── 📁 scrap                 # 공고 스크랩
 │   ├── 📁 search                # 채용공고 검색 (자연어)
 │   └── 📁 techcard              # 테크 뉴스 카드 (collector / scheduler)
 │
 └── 📁 global                    # 공통 모듈
     ├── 📁 apiPayload            # 공통 응답 / 에러 / 예외 처리 (code / exception / handler)
     ├── 📁 auth                  # 인증·인가 공통 로직
     ├── 📁 config                # 공통 설정 정의
     ├── 📁 llm                   # LLM 연동 공통 모듈
     ├── 📁 storage               # 파일·스토리지(S3) 연동
     └── 📁 util                  # 공통 유틸리티

📦 src/main/resources
 ├── 📄 application.yaml          # 기본 설정
 ├── 📄 application-classify.yml  # 공고 분류 프로필
 ├── 📄 application-collect.yml   # 공고 수집 프로필
 ├── 📄 application-export.yml    # 내보내기 프로필
 ├── 📁 db/migration              # Flyway 마이그레이션 (V1~V9)
 └── 📁 specs                     # 크롤러 기업별 스펙 (17개 기업)

📦 infra                         # 인프라 구성
 ├── 📁 nginx                     # Nginx 설정
 ├── 📁 postgres                  # DB 초기화 스크립트
 └── 📁 terraform                 # AWS IaC (EC2 / ECR / IAM / S3)

📄 .github                        # GitHub Actions CI/CD
📄 docker-compose.yml             # 로컬 개발용
📄 docker-compose.prod.yml        # 운영 배포용
📄 Dockerfile
📄 build.gradle
```

---

## 👨‍👩‍👧‍👦 Developer

## Devloper
| 김민주 | 이원준 | 이정헌 | 
|:------:|:------:|:------:|
| <img src="" alt="김민주" width="150"> | <img src="https://github.com/wonjun-lee-fcwj245.png" alt="이원준" width="150"> | <img src="https://github.com/user-attachments/assets/6de709d7-39f3-43df-b7bb-e636c42463c0" alt="이정헌" width="150"> |
| BE | BE | BE | 
| [GitHub](https://github.com/kimmingju) | [GitHub](https://github.com/wonjun-lee-fcwj245) |  [GitHub](https://github.com/LeeJeongHeon02) | 

---
