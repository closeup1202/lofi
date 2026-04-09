```
lofi/
├── lofi-core/                  ← 공통 도메인 (MethodMetric, DeploySnapshot 등)
├── lofi-collector/             ← Spring AOP 계측, 데이터 수집
├── lofi-actuator/              ← /actuator/lofi 엔드포인트 노출
├── lofi-spring-boot-starter/   ← collector + actuator 자동 설정 묶음
├── lofi-cli/                   ← diff 조회 CLI (TypeScript or Java)
└── pom.xml                     ← parent pom
```

### 각 모듈 역할
- lofi-core — 모든 모듈이 공유하는 도메인 모델만 있어요. 의존성 없는 순수 Java. MethodMetric, DeploySnapshot, DiffResult 같은 클래스들.
- lofi-collector — AOP로 메서드 latency 수집. core에만 의존해요. Spring 의존성 있음.
- lofi-actuator — 수집된 데이터를 HTTP로 노출. collector에 의존해요. Spring Actuator 의존성 있음.
- lofi-spring-boot-starter — collector + actuator를 자동 설정으로 묶는 얇은 레이어. 사용자는 이것만 추가하면 돼요.
- lofi-cli — actuator 엔드포인트 호출해서 diff 출력. 언어는 TypeScript 추천 — gh님 강점이기도 하고, 배포도 npm install -g lofi-cli로 간단해요.

### 의존 관계
```
lofi-core
↑
lofi-collector
↑
lofi-actuator
↑
lofi-spring-boot-starter  (사용자가 추가하는 것)

lofi-cli  (독립적 — actuator HTTP만 호출)
```