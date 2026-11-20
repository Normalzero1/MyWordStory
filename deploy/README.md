# GCP 배포

서버를 GCP e2-micro(무료 티어 VM, 고정 IP는 월 약 $4)에 올린다. 구성은 다음과 같다.

- **서버 이미지**: GitHub Actions(`.github/workflows/server-image.yml`)가 `server/`가 바뀔 때마다 Docker 이미지를 만들어 `ghcr.io/normalzero1/mywordstory-server:latest`에 올린다. VM은 이 이미지를 받아서 쓴다.
- **VM 안**: `/opt/mywordstory`에 PostgreSQL, 서버, Caddy(HTTPS 자동 발급)를 docker compose로 띄운다.
- **백업**: 매일 04:17(UTC)에 DB를 덤프해서 Cloud Storage 버킷에 올린다. 버킷은 7일 지난 파일을 지운다.

## 준비물

1. 결제 계정이 연결된 GCP 프로젝트
2. **GitHub 토큰(읽기 전용)**: VM이 비공개 이미지를 받을 때 쓴다.
   - 경로: GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token
   - 권한: `read:packages` 하나만 체크한다.
   - 만든 토큰은 4단계에서 VM 터미널에 붙여 넣는다. 채팅에는 붙여 넣지 않는다.
3. 이 폴더의 `gcp-create.sh`, `vm-setup.sh` 두 파일

## 1. GCP 자원 만들기 (Cloud Shell)

1. https://console.cloud.google.com 에서 위쪽의 프로젝트를 선택하고, 오른쪽 위 **Cloud Shell** 아이콘(`>_`)을 누른다.
2. Cloud Shell 창의 **⋮ → 업로드**로 두 파일을 올린다.
3. 실행한다.
   ```bash
   bash gcp-create.sh word.normalzero.co.kr
   ```
   VM, 고정 IP, 방화벽(80/443), 백업 버킷이 만들어진다. 마지막 줄에 **고정 IP**가 나온다. 이미 있는 자원은 건너뛰므로 다시 실행해도 된다.

## 2. DNS 연결

도메인 관리 화면에서 A 레코드를 하나 추가한다.

| 이름(호스트) | 종류 | 값 |
|---|---|---|
| `word` | A | 1단계에서 나온 고정 IP |

전파에 몇 분에서 몇 시간 걸릴 수 있다.

## 3. 키 넣기와 실행 (VM 안)

Cloud Shell에서 VM에 접속한다. VM이 처음 켜지며 Docker를 설치하는 데 2~3분 걸린다.

```bash
gcloud compute ssh mywordstory --zone=us-west1-b
```

VM 안에서 다음을 실행한다.

```bash
sudo nano /opt/mywordstory/.env          # ANTHROPIC_API_KEY= 뒤에 키를 넣고 저장 (Ctrl+O, Enter, Ctrl+X)
sudo docker login ghcr.io -u <GitHub 아이디> # 비밀번호 칸에 준비물 2의 GitHub 토큰
cd /opt/mywordstory && sudo docker compose up -d
sudo docker compose logs -f server       # "Imported 3140 candidate words"가 보이면 정상 (Ctrl+C로 빠져나옴)
```

`docker: command not found`가 나오면 아직 설치 중이다. 1~2분 뒤 다시 한다. 진행 상황은 `sudo journalctl -u google-startup-scripts -f`로 볼 수 있다.

## 4. 확인과 앱 설정

```bash
curl https://word.normalzero.co.kr/health   # ok
sudo grep API_TOKEN /opt/mywordstory/.env    # 앱에 넣을 토큰
```

앱의 **설정**에서 서버 주소를 `https://word.normalzero.co.kr`로, 토큰을 위 값으로 바꾸고 저장한다.

HTTPS가 안 되면 대개 DNS가 아직 전파되지 않은 것이다. Caddy는 DNS가 맞으면 인증서를 스스로 다시 받는다. 로그는 `sudo docker compose logs caddy`로 본다.

## 5. 예산 알림

GCP 콘솔에서 **결제 → 예산 및 알림 → 예산 만들기**를 누르고 금액을 **$5**로 정한다. 고정 IP 요금이 예상과 다르게 나오면 알 수 있다.

## 운영

| 할 일 | 명령 (VM 안) |
|---|---|
| 새 서버 버전으로 업데이트 | `sudo /opt/mywordstory/update.sh` |
| 서버 상태 | `cd /opt/mywordstory && sudo docker compose ps` |
| 레벨 매기기 진행 상황 | ``curl -s https://word.normalzero.co.kr/study/status -H "Authorization: Bearer $(sudo grep API_TOKEN /opt/mywordstory/.env \| cut -d= -f2)"`` |
| 백업 목록 (Cloud Shell) | `gcloud storage ls gs://<프로젝트ID>-mywordstory-backup` |
| 지금 백업 | `sudo /opt/mywordstory/backup.sh` |

PC에서 띄웠던 개발 서버의 데이터는 옮겨지지 않는다. 직접 추가했던 단어는 앱에서 다시 추가하면 된다.
