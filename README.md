# lofi

마지막 배포 이후 어떤 메서드가 느려졌는지 바로 확인하세요.

lofi는 Spring Boot 팀을 위한 경량 관찰 라이브러리입니다.  
의존성 하나 추가하고, 배포하면 — 메서드 레벨 latency diff가 자동으로 생성됩니다.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-compatible-6DB33F?style=flat-square)
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
$ lofi diff HEAD~1..HEAD

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

```xml
<dependency>
  <groupId>io.lofi</groupId>
  <artifactId>lofi-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

### 2. 끝입니다

lofi는 Spring AOP를 통해 빈을 자동으로 계측합니다.  
어노테이션 없음. 별도 설정 없음. 의존성 추가 후 배포하면 바로 동작합니다.

---

## 동작 방식

lofi는 Spring AOP로 메서드 호출을 인터셉트해 배포 단위로 latency를 기록합니다.  
배포 경계는 애플리케이션 시작 시점의 Git 커밋 해시로 자동 감지됩니다.  
모든 데이터는 로컬에서 처리됩니다. 대시보드를 선택하지 않으면 외부로 나가는 데이터는 없습니다.

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

lofi는 초기 개발 단계입니다. 피드백 환영합니다 — 이슈를 열거나 연락 주세요.
