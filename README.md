# lofi

마지막 배포 이후 어떤 메서드가 느려졌는지 바로 확인하세요.

lofi는 Spring Boot 팀을 위한 경량 관찰 라이브러리입니다.  
의존성 하나 추가하고, 배포하면 — 메서드 레벨 latency diff가 자동으로 생성됩니다.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat-square)
![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)

---

## 문제

배포 이후 p99 latency가 튀었습니다.  
원인이 어떤 메서드인지 알려면? Grafana 열고, Jaeger 확인하고, 로그 뒤지고, 커밋 diff 보고 — 매번 머릿속에서 직접 연결해야 합니다.

lofi는 배포 이벤트와 코드를 연결해서, 그 diff를 바로 보여줍니다.

---

## 이렇게 보입니다

```bash
$ lofi diff a3f9c1..d82e04

배포 비교  main@a3f9c1 → main@d82e04
────────────────────────────────────────────────────────
  메서드                              이전      이후    변화
────────────────────────────────────────────────────────
  OrderService.createOrder()         14ms  →  91ms   +77ms  ▲
  PaymentClient.validate()           22ms  →  58ms   +36ms  ▲
  UserService.findById()              3ms  →   3ms      —
  ProductService.getStock()           8ms  →   9ms      —
────────────────────────────────────────────────────────
  성능 저하 2건 감지됨
```

---

## 시작하기

### 1. 의존성 추가

**Gradle**

```groovy
implementation 'io.github.closeup1202:lofi-spring-boot-starter:0.1.0'
```

**Maven**

```xml
<dependency>
  <groupId>io.github.closeup1202</groupId>
  <artifactId>lofi-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

### 2. 커밋 해시 주입

배포 경계는 `GIT_COMMIT_HASH` 환경변수로 감지합니다.

**로컬 개발**

```bash
export GIT_COMMIT_HASH=$(git rev-parse --short HEAD)
./gradlew bootRun
```

**Docker**

```dockerfile
ARG GIT_COMMIT_HASH
ENV GIT_COMMIT_HASH=${GIT_COMMIT_HASH}
```

```bash
docker build \
  --build-arg GIT_COMMIT_HASH=$(git rev-parse --short HEAD) \
  -t my-app .
```

**GitHub Actions**

```yaml
- name: Run application
  env:
    GIT_COMMIT_HASH: ${{ github.sha }}
  run: ./gradlew bootRun
```

**Kubernetes**

```yaml
env:
  - name: GIT_COMMIT_HASH
    value: "a3f9c1"
```

### 3. actuator 엔드포인트 노출

```yaml
management:
  endpoints:
    web:
      exposure:
        include: lofi, lofi-diff
```

### 4. lofi-cli 설치

```bash
npm install -g @closeup1202/lofi-cli
```

---

## 사용법

### diff — 두 배포 간 성능 비교

```bash
lofi diff <base>..<head> --url http://localhost:8080
```

```bash
# 예시
lofi diff a3f9c1..d82e04 --url http://localhost:8080
```

### snapshot — 특정 배포 스냅샷 조회

```bash
lofi snapshot <commitHash> --url http://localhost:8080
```

```bash
# 예시
lofi snapshot a3f9c1 --url http://localhost:8080
```

---

## 동작 방식

lofi는 Spring AOP를 통해 `@Service`, `@Component`, `@Repository` 빈의 메서드 호출을 자동으로 계측합니다.  
배포 경계는 애플리케이션 시작 시점의 `GIT_COMMIT_HASH` 환경변수로 감지됩니다.  
수집된 데이터는 `~/.lofi/metrics.db`에 SQLite 파일로 저장됩니다.  
모든 데이터는 로컬에서 처리됩니다. 대시보드를 선택하지 않으면 외부로 나가는 데이터는 없습니다.

---

## 설정

`application.yml`에서 수집 동작을 조정할 수 있습니다.

```yaml
lofi:
  commit-hash: ${GIT_COMMIT_HASH:unknown}
  buffer:
    flush-threshold: 100   # 메트릭을 모아서 저장할 개수 (기본값: 100)
    flush-delay-ms: 5000   # 주기적으로 저장할 간격 ms (기본값: 5000)
```

---

## 오픈소스 vs 대시보드

| 기능 | 오픈소스 | 대시보드 (출시 예정) |
|------|----------|----------------------|
| 메서드 레벨 latency 수집 | ✓ | ✓ |
| CLI diff 뷰어 | ✓ | ✓ |
| 배포 히스토리 | 로컬 저장 | ✓ |
| 팀 공유 | — | ✓ |
| 성능 저하 알람 | — | ✓ |

---

## 제한사항

- 현재 단일 Pod 환경에서만 동작합니다. 멀티 Pod 지원은 대시보드 플랜에서 제공됩니다.
- Spring Boot 3.x, Java 17 이상에서만 동작합니다.
- `@Service`, `@Component`, `@Repository` 빈만 자동 계측됩니다.

---

lofi는 초기 개발 단계입니다. 피드백 환영합니다 — 이슈를 열거나 연락 주세요.
