# 💼 JobA!
### 나에게 맞는 채용공고, 이제 직접 찾지 마세요
### IT 채용공고 자동 수집 · AI 매칭 · 실시간 알림 서비스

<img width="1920" height="1080" alt="표지" src="https://github.com/user-attachments/assets/fa4c9941-82ce-4194-bf24-f2a444191e19" />

---

## 📌 Project Overview
JobA!는 사용자가 채용공고를 직접 찾는 대신,
**IT 채용공고를 자동으로 수집**하고
**AI 모델이 이력서와 매칭해 적합도를 점수화**하며,
**사용자에게 적합한 공고를 실시간으로 알려 주는** 채용 준비 자동화 서비스입니다.

공고 수집부터 이력서 매칭, 알림 발송, 지원 현황 관리까지
채용 준비의 모든 과정을 하나로 연결했습니다.

> 🏷️ TAVE 17th Project

---

## 🎯 Problem & Solution

### ❗ Problem
- **플랫폼 분산** — 취준생 절반 이상이 사람인·잡코리아·원티드 등 2개 이상을 동시에 사용해요
- **수시채용 전환** — 공고를 놓칠 위험이 커졌어요
- **직접 확인** — 공고를 하나씩 읽어 봐야 나와 맞는지 알 수 있어요
- **반복 확인** — 새 공고가 올라왔는지 계속 들어가 봐야 해요
- **근거 부족** — 왜 이 공고가 나에게 맞는지 추천 근거를 알기 어려워요
- **복잡한 조건** — 조건이 다양하고 복잡해 의도에 맞는 공고를 정확히 찾기 번거로워요
- **흩어진 일정** — 채용 관련 일정(지원 현황·면접 등)을 한곳에서 관리하기 어려워요


### ✅ Solution
- **자동 통합 수집** — 사기업·공공기관 IT 공고를 한곳에 자동으로 수집해 제공해요
- **AI 적합도 분석** — 이력서를 분석해 공고별 적합도를 0~100점으로 산출해요
- **AI 요약 제공** — 공고의 핵심 내용을 요약해 빠르게 확인할 수 있어요
- **신규 공고 알림** — 설정한 적합도 기준을 넘는 공고가 올라오면 Slack·이메일로 알려드려요
- **매칭 근거 제** — 적합도 점수의 근거를 함께 제공해 신뢰성을 높여요
- **자연어 검색** — 키워드 한 단어든 긴 문장이든, 의도를 파악해 적합한 공고를 찾아드려요
- **일정 통합 관리** — 스크랩한 공고의 지원 현황·면접 일정을 한곳에서 관리해드려요
---

## ✨ Key Features
- 🔎 **자동 수집 & 실시간 알림** (사기업·공공기관 IT 공고 통합 수집 → 임계값 초과 시 이메일 알림)
- 🤖 **AI 기반 적합도 매칭** (AI 서버의 자체 매칭 모델이 이력서–공고 적합도를 점수화 + 보유·부족 기술·추천 근거 제공)
- 💬 **자연어 공고 검색** (Query Expansion → Hybrid Search → Rerank 3단계 파이프라인으로 의도에 맞는 공고 탐색)
- 📄 **AI 공고 요약** (공고를 요약하고 이력서와 비교 분석해 적합도·추천 이유 표시)
- 🗂️ **입사 지원 현황 트래커** (지원 단계·면접 일정을 자동 정리, 다가오는 일정 요약)
- ⭐ **스크랩 & 마감 임박 알림** (관심 공고 저장, 마감 가까운 공고 자동 취합)
- ⚙️ **온보딩 기반 맞춤 설정** (희망 직무·근무 지역·고용 형태·적합도 기준·알림 채널)

---

## 🚀 Technical Highlights

- 🌙 **매일 새벽 2시 자동 실행되는 4단계 수집·매칭 파이프라인**
  - 스케줄러가 매일 새벽 2시에 **수집 → 매칭 점수 산출**까지 4단계를 자동 실행
  - 각 단계를 **개별 try-catch로 격리**해, 앞 단계가 실패해도 뒷 단계는 정상 실행
  - **Step 1 · 사기업 수집** — 크롤링으로 공고 수집, 신규 INSERT / 변경 UPDATE / 미노출 공고 마감 처리, 직무·고용형태·경력이 비면 **LLM으로 분류**해 저장
  - **Step 2 · 공기업 수집** — 공공데이터 API로 공고 수집, 신규·변경 사항 반영
  - **Step 3 · 임베딩 생성** — 신규 공고를 **AI 서버의 임베딩 모델**로 호출해 벡터 생성
  - **Step 4 · 매칭 점수 산출** — **AI 서버의 매칭 모델**로 활성 이력서–공고 간 적합도 점수를 산출·저장 (사기업·공기업 **독립 실행**)

- 🧭 **이원화된 공고 수집 전략**
  - 사기업: **YAML 설정 기반 크롤러(16종) + Jsoup**, `source_type`(json / embedded_json)별 수집 → `mapRecord` 필드 매핑 → `applyFilter` → 상세 보강 → `source_job_id` 기준 Upsert → **Claude API 기반 LLM 분류**
  - 공기업: **공공데이터 API + 상세 병렬 조회**, 직무기술서(PDF·HWP·ZIP) 다운로드 후 **본문·NCS 소분류 파싱** → `pblntfNo` 기준 Upsert
  - 회사별 순회 시 **에러 격리**로 일부 실패가 전체 수집을 막지 않도록 설계

- 🗣️ **3단계 자연어 검색 파이프라인**
  - 기본은 **형태소 분석기**로 검색어에서 키워드를 추출해 DB 필터 조건으로 활용
  - 여기서 한 단계 더 나아가, 키워드로 매칭되지 않는 자연어 표현까지 이해하도록 3단계로 설계
  - **① Query Expansion** — LLM이 매칭되지 않는 표현을 관련 키워드로 확장 (예: "야근 없는" → "워라밸", "유연근무")
  - **② Hybrid Search** — 키워드 검색 + 벡터 검색을 동시 수행 후 **RRF 알고리즘**으로 병합해, 한쪽만으로 놓칠 공고까지 포괄
  - **③ Rerank** — 한국어 데이터셋으로 학습한 **Cross-Encoder** 모델이 쿼리–공고 관련성을 직접 평가해 최종 순위 결정
  - 각 단계를 **독립적으로 On/Off** 가능하도록 설계하고, 외부 AI 서버 장애 시 **키워드 검색으로 자동 Fallback**

- 🤖 **AI 서버 기반 적합도 매칭**
  - 단순 프롬프트 호출이 아니라, **AI 서버에 임베딩·매칭 모델을 두고** 이력서–공고 적합도를 0~100점으로 점수화
  - 보유·부족 기술을 함께 분석해 **매칭 근거** 제공

- ⚡ **Redis 캐싱**
  - 반복 조회 데이터를 캐싱해 응답 속도 및 부하 개선

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

## 🏗 Architecture
<img width="1640" height="804" alt="image 49" src="https://github.com/user-attachments/assets/64689a0f-8852-4dcf-a6bf-317ded21281b" />

---

## 🗄 ERD
<img width="2701" height="1812" alt="image" src="https://github.com/user-attachments/assets/c6cfeb1e-6067-40ac-b540-0c9a5a18c2d3" />

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
