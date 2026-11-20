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

인증: `/health`를 뺀 모든 요청에 `Authorization: Bearer <API_TOKEN>`.

| 요청 | 응답 |
|---|---|
| `GET /health` | `ok` (인증 없음) |
| `POST /words` `{"headword": "..."}` | 201 새로 생성 · 200 이미 있던 단어 · 400 빈 값 또는 100자 초과 · 502 생성 실패 |
| `GET /words` | 전체 목록, 최신순 |
| `GET /words/{id}` | 단어 하나 · 404 없음 |
| `POST /words/{id}/status` `{"status": "studied"}` | 상태 변경: `new` · `studied` · `known`(이미 앎) · `excluded`(필요 없음) |
| `GET /study/new?count=30` | 아직 공부하지 않은 카드. 직접 추가한 단어가 먼저 |
| `GET /study/status` | 시작 레벨, 레벨별 후보 수(`none`은 아직 레벨 없음), 준비된 새 단어 수 |
| `GET /level-test` | 레벨(A1~C2)마다 아직 안 본 후보 10개씩 |
| `POST /level-test` `{"startLevel": "B1", "known": [...], "unknown": [...]}` | 204. `known`은 다시 나오지 않고, `unknown`은 먼저 나옴 |
| `GET /candidates/sample?level=B1&count=20` | 레벨이 잘 매겨졌는지 보는 용도 |

## 단어집과 백그라운드 작업

- 처음 시작할 때 `src/main/resources/wordlists/candidates.csv`(NGSL-Spoken, BSL, TSL을 합친 3,140개, CC BY-SA 4.0, [출처](src/main/resources/wordlists/ATTRIBUTION.md))를 DB에 넣는다.
- 10분마다, 그리고 학습 요청 뒤에 백그라운드로 두 가지를 한다.
  1. 레벨이 없는 후보를 100개씩 Claude Haiku에 보내 CEFR 레벨을 매긴다. 처음에는 3,140개를 모두 매기느라 시간이 걸린다.
  2. 수준 테스트가 끝난 뒤라면, 다음에 공부할 단어 카드를 40개까지 미리 만들어 둔다.

## 테스트

PostgreSQL이 필요하다. 기본값은 `jdbc:postgresql://localhost:5432/mywordstory_test`, 사용자/비밀번호 `postgres`이고, `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD`로 바꿀 수 있다.

```bash
./gradlew test
```
