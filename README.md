# jobai-backend


## 실행 방법

### 1. 환경변수 설정
```bash
cp .env.example .env
```
.env 파일을 열어서 본인 환경에 맞게 값을 채웁니다.

### 2. 컨테이너 실행
```bash
docker compose up --build
```

### 3. 실행 확인

- PostgreSQL: `localhost:${POSTGRES_PORT}` → container `5432`
- Redis: `localhost:${REDIS_PORT}` → container `6379`
- AI Server: `localhost:${FASTAPI_PORT}` → container `8001`

AI Server health check:

```bash
curl http://localhost:${FASTAPI_PORT}/health
```

Expected:

```json
{"status":"ok"}
```

## 주의사항
- 로컬에 PostgreSQL 설치되어 있으면
  .env에서 POSTGRES_PORT=5433으로 변경합니다.
- Spring Boot에서 AI Server를 호출할 때는 Docker 내부 서비스명을 사용합니다.
```bash
FASTAPI_BASE_URL=http://ai-server:8001
```

## 폴더 구조
```
jobai-backend/
├── ai-server/      # FastAPI AI 모델 서버
├── infra/
│   └── postgres/
│       └── init.sql
├── src/            # Spring Boot 서버 코드
├── .env.example
└── docker-compose.yml
```