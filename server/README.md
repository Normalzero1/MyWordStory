# MyWordStory 서버

Ktor + PostgreSQL. 단어를 받으면 Claude로 카드 내용을 만들어 저장한다. 설계는 [../docs/DESIGN.md](../docs/DESIGN.md).

## 로컬 실행

Docker가 필요하다.

```bash
cd server
cp .env.example .env   # DATABASE_PASSWORD, API_TOKEN, ANTHROPIC_API_KEY 채우기
docker compose up --build
```

```bash
TOKEN=<.env의 API_TOKEN>
curl -s -X POST localhost:8080/words \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"headword": "circle back"}'
curl -s localhost:8080/words -H "Authorization: Bearer $TOKEN"
```

## API

인증: 모든 `/words` 요청에 `Authorization: Bearer <API_TOKEN>`.

| 요청 | 응답 |
|---|---|
| `GET /health` | `ok` (인증 없음) |
| `POST /words` `{"headword": "..."}` | 201 새로 생성 · 200 이미 있던 단어 · 400 빈 값 또는 100자 초과 · 502 생성 실패 |
| `GET /words` | 전체 목록, 최신순 |
| `GET /words/{id}` | 단어 하나 · 404 없음 |

## 테스트

PostgreSQL이 필요하다. 기본값은 `jdbc:postgresql://localhost:5432/mywordstory_test`, 사용자/비밀번호 `postgres`이고, `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD`로 바꿀 수 있다.

```bash
./gradlew test
```
