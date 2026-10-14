# unifeed 서버 가이드

이 프로젝트를 실제로 띄울 **팀 서버** 사용법입니다.
서버 접속 → 앱 실행 → `https://example.com` 으로 공개하는 데 필요한 것만 담았습니다.

| 항목 | 값 |
|---|---|
| 도메인 | **https://example.com** |
| SSH | `example.com` **포트 47330** · 계정 `ubuntu` |
| 비밀번호 | 멘토에게 따로 받습니다 (저장소에 적지 않습니다) |
| OS | Ubuntu 22.04 · 4 vCPU · 메모리 8GB · 디스크 30GB |
| 권한 | `sudo` 가능 |
| 미리 설치됨 | nginx (80 포트) |

> ⚠️ **한국 IP 에서만** 접속됩니다. 해외 VPN·해외 로밍 중이면 연결이 안 됩니다.
> ⚠️ 비밀번호를 여러 번 틀리면 그 IP 가 **잠시 차단**됩니다. 몇 분 뒤 다시 시도하세요.

---

## 1. 서버 접속

이 폴더의 스크립트를 쓰면 됩니다. 비밀번호를 물어보면 입력하세요.

### macOS

```bash
bash server-guide/connect-mac.sh          # 바로 접속
bash server-guide/connect-mac.sh setup    # 한 번만: 이후 'ssh unifeed' 한 줄로 접속
```

### Windows 10/11

`server-guide\connect-windows.cmd` 를 **더블클릭**하면 접속합니다.

```bat
server-guide\connect-windows.cmd setup    :: 한 번만: 이후 'ssh unifeed' 한 줄로 접속
```

> `ssh` 가 없다고 나오면: 설정 → 앱 → 선택적 기능 → **OpenSSH 클라이언트** 추가.

### 직접 치려면

```bash
ssh -p 47330 ubuntu@example.com
```

파일 올리기(예: 빌드한 jar):

```bash
scp -P 47330 build/libs/osan-unifeed-0.0.1-SNAPSHOT.jar ubuntu@example.com:~/app/
```

---

## 2. HTTPS 는 이미 되어 있습니다 — 서버는 80 포트만

```
브라우저 ──HTTPS──▶ [앞단 LB] ──HTTP(80)──▶ [unifeed 서버 nginx :80] ──▶ [우리 앱 :8080]
         인증서·TLS 는 여기서 처리            여기서부터가 우리 몫
```

- **HTTPS(인증서·443) 는 앞단 LB(로드밸런서)에서 처리**하도록 세팅되어 있습니다. 인증서도 자동으로 갱신됩니다.
- LB 는 요청을 서버의 **80 포트(평문 HTTP)** 로 넘겨줍니다.
- 그래서 우리는 **서버의 nginx 가 80 포트로 받은 요청을 앱(8080)으로 넘기는 리버스 프록시만** 설정하면 됩니다.

하지 **않아도 되는** 것 (하면 오히려 꼬입니다):

- ❌ 서버에 SSL 인증서 설치 / certbot / 443 설정
- ❌ 방화벽에서 8080 같은 포트 열기 — 밖에서 들어오는 웹 요청은 **80 포트 하나**뿐입니다

LB 가 붙여 주는 정보:

| 헤더 | 뜻 |
|---|---|
| `X-Forwarded-Proto: https` | 사용자는 https 로 들어왔다 |
| `X-Forwarded-For` | 실제 사용자 IP |

서버 nginx 는 이미 `X-Forwarded-For` 로 실제 사용자 IP 를 복원하도록 설정되어 있습니다.
그래서 nginx 로그와 앱이 보는 접속 IP 는 **진짜 사용자 IP** 입니다.

지금 https://example.com 에 들어가면 "Welcome to nginx!" 가 보입니다.
아래 3번을 하면 이 자리에 우리 앱이 뜹니다.

---

## 3. 앱 띄우기 (Spring Boot)

### 3-1. Java 25 설치 (서버에서 한 번만)

```bash
sudo apt-get update && sudo apt-get install -y wget gpg
wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb jammy main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update && sudo apt-get install -y temurin-25-jdk
java -version     # openjdk version "25..." 이 나오면 성공
```

### 3-2. 빌드해서 올리기

내 컴퓨터에서 jar 를 만들어 올리는 방법 (권장):

```bash
./gradlew bootJar
ssh -p 47330 ubuntu@example.com 'mkdir -p ~/app'
scp -P 47330 build/libs/osan-unifeed-0.0.1-SNAPSHOT.jar ubuntu@example.com:~/app/app.jar
```

서버에서 직접 빌드해도 됩니다 (`git clone` 후 `./gradlew bootJar`).

### 3-3. DB 연결 — 지금처럼 toydb 그대로

앱은 지금과 똑같이 공유 MySQL(`localhost:3306`, DB `toydb`)에 붙습니다. 서버는 한국에 있어서 **설정을 바꾸지 않아도 그대로 연결**됩니다.

DB 접속 정보를 서버에서 바꾸고 싶을 때는 코드를 고치지 말고 **환경변수**로 덮어씁니다.
스프링은 `spring.datasource.password` 를 `SPRING_DATASOURCE_PASSWORD` 환경변수로도 받고, 환경변수가 `application.properties` 보다 우선합니다.

```bash
sudo mkdir -p /etc/unifeed
sudo cp server-guide/unifeed.env.example /etc/unifeed/unifeed.env   # 저장소를 서버에 clone 했다면 (아니면 nano 로 새로 만들기)
sudo nano /etc/unifeed/unifeed.env                            # 바꿀 값만 채우고, 비워 둔 줄은 지운다
sudo chmod 600 /etc/unifeed/unifeed.env
```

> 비밀번호 같은 값은 저장소가 아니라 이 파일(서버 안)에 두는 게 좋습니다.

### 3-4. 서비스로 등록 (서버가 재부팅돼도 앱이 다시 뜨게)

```bash
sudo cp server-guide/unifeed.service /etc/systemd/system/unifeed.service
sudo systemctl daemon-reload
sudo systemctl enable --now unifeed
sudo systemctl status unifeed          # active (running) 확인
curl -I http://127.0.0.1:8080/         # 앱이 직접 응답하는지
journalctl -u unifeed -f               # 앱 로그 (Ctrl+C 로 나가기)
```

새 버전을 올렸으면 `sudo systemctl restart unifeed` 만 하면 됩니다.

### 3-5. nginx 리버스 프록시 (80 → 8080)

```bash
sudo cp server-guide/nginx-unifeed.conf /etc/nginx/sites-available/unifeed
sudo ln -sf /etc/nginx/sites-available/unifeed /etc/nginx/sites-enabled/unifeed
sudo rm -f /etc/nginx/sites-enabled/default        # "Welcome to nginx!" 기본 페이지 끄기
sudo nginx -t && sudo systemctl reload nginx       # 'syntax is ok' 가 나와야 reload
```

이제 **https://example.com** 에 우리 앱이 뜹니다.

### 3-6. https 로 리다이렉트되게 (중요)

앱은 LB 뒤에 있어서, 따로 알려 주지 않으면 자기가 **http** 로 서비스되는 줄 압니다.
그러면 `redirect:/posts` 같은 리다이렉트가 `http://...` 로 나가 한 번 더 돌아오거나 경고가 납니다.

`unifeed.service` 에 이미 넣어 두었습니다:

```
Environment=SERVER_FORWARD_HEADERS_STRATEGY=native
```

이 설정이 있으면 앱이 `X-Forwarded-Proto: https` 를 믿고 리다이렉트를 https 로 만듭니다.
(코드로 하고 싶으면 `application.properties` 에 `server.forward-headers-strategy=native`)

---

## 4. 프론트엔드만 올리는 경우 (React/Vue 빌드 파일)

정적 파일은 nginx 가 바로 서빙하면 됩니다. `nginx-unifeed.conf` 맨 아래 **정적 사이트 예시**를 참고하세요.

```bash
sudo mkdir -p /var/www/unifeed
sudo cp -r dist/* /var/www/unifeed/
```

---

## 5. 문제가 생기면

| 증상 | 원인 / 확인 |
|---|---|
| 브라우저에 **502 Bad Gateway** | 앱이 안 떠 있거나 포트가 다름 → `systemctl status unifeed`, `curl -I http://127.0.0.1:8080/` |
| 여전히 **Welcome to nginx!** | 기본 사이트가 켜져 있음 → `sudo rm /etc/nginx/sites-enabled/default` 후 reload |
| `nginx -t` 실패 | 메시지에 나온 파일·줄을 고친 뒤 다시 `nginx -t` |
| 앱이 DB 연결 실패 | `journalctl -u unifeed` 의 에러 확인 · `/etc/unifeed/unifeed.env` 에 잘못된 값이 있지 않은지 |
| 파일 업로드가 413 | 업로드는 **50MB** 까지 받습니다 |
| SSH 가 안 됨 | 한국 IP 인지 · 포트 **47330** 인지 · 비밀번호 여러 번 틀려 잠시 막혔는지 |

로그 위치: nginx `/var/log/nginx/access.log` · `/var/log/nginx/error.log` · 앱 `journalctl -u unifeed`

## 이 폴더의 파일

| 파일 | 용도 |
|---|---|
| `connect-mac.sh` | macOS 접속 (`setup` = ssh 설정 등록) |
| `connect-windows.cmd` / `.ps1` | Windows 접속 (더블클릭) |
| `nginx-unifeed.conf` | nginx 리버스 프록시 설정 (80 → 8080) |
| `unifeed.service` | 앱을 systemd 서비스로 등록 |
| `unifeed.env.example` | 서버 환경변수 예시 (DB 접속 정보 덮어쓰기) |
