# Backend 테스트가 로컬 MySQL에 의존한 문제

- 발생일: 2026-07-15
- 범위: `UNITHON24/Backend` 전체 Gradle 테스트와 pull request 검증
- 상태: 해결 및 `master` CI 검증 완료
- 영향: Macro 주문 토큰 회귀 테스트는 단독 실행할 수 있었지만, 저장소 전체 테스트를 원격에서
  지속적으로 검증하는 gate가 없었다.

## 기대와 실제

Macro 주문 전달에 installation token을 추가한 뒤 `./gradlew test --no-daemon`이 개발자 장비와
GitHub Actions에서 별도 비밀이나 외부 서비스 없이 반복 실행되기를 기대했다.

최종 감사에서 저장소에 GitHub Actions workflow가 없음을 확인했다. 전체 테스트를 로컬에서
실행하자 `MacroWebhookServiceTest`는 통과했지만 `UnithonApplicationTests.contextLoads()`가
`localhost:3306` MySQL 연결을 시도해 실패했다. test profile로 DB를 격리한 다음에는 토큰
변경 때 추가한 두 번째 생성자 때문에 Spring이 `MacroWebhookService`의 주입 생성자를 고르지
못하는 wiring 오류도 이어서 드러났다.

## 재현과 증거

```sh
./gradlew test --no-daemon
```

- 결과: 2개 테스트 중 context smoke test 1개 실패
- 예외 흐름: `JDBCConnectionException` → MySQL `CommunicationsException` →
  `java.net.ConnectException`
- DB 격리 뒤 예외: `MacroWebhookService`의 `NoSuchMethodException`과 "No default constructor"
- `application.yml`: MySQL, Google STT/TTS와 외부 자격증명을 운영 기본값으로 사용
- 기존 workflow: 없음

따라서 원인은 Spring context smoke test가 운영 인프라 설정을 그대로 상속한 점과, 생성자가
둘인 service에서 운영 주입 생성자를 명시하지 않은 점이었다. 토큰 단위 테스트는 service를
직접 생성하므로 두 문제를 모두 드러내지 못했다.

## 검토한 대안

- GitHub Actions에서 MySQL service container와 가짜 cloud 설정 사용: 실제 DB 엔진과 가장
  가깝지만 context smoke test마다 컨테이너 시간과 실패 지점이 늘어난다.
- Testcontainers로 MySQL 실행: 운영 일치도와 격리는 좋지만 현재 두 개뿐인 테스트에 Docker
  의존성과 초기화 비용을 추가한다.
- context smoke test 제거: 빌드 성공만 확인하고 Spring wiring 회귀를 놓치므로 제외했다.
- H2의 MySQL compatibility mode를 쓰는 명시적 test profile: 빠르고 비밀이 없으며 현재
  entity, schema와 seed 초기화를 함께 검증할 수 있어 선택했다.

## 해결

1. test runtime에 H2를 추가했다.
2. `application-test.yml`에서 in-memory H2를 MySQL mode로 실행하고 STT/TTS를 비활성화했다.
3. context smoke test에 `test` profile을 명시해 운영 MySQL과 cloud credential을 요구하지
   않게 했다.
4. 생성자가 둘인 `MacroWebhookService`의 운영 생성자를 Spring 주입 대상으로 명시했다.
5. Java 21, Gradle wrapper validation, 전체 build·테스트와 실패 report 업로드를 포함한
   Backend CI를 pull request와 `master` push에 추가했다.
6. 외부 action은 현재 release의 전체 commit SHA로 고정하고 `contents: read`만 허용했다.

## 검증

로컬에서 다음 결과를 확인했다.

- `./gradlew clean test --no-daemon`: 2개 테스트, 실패·skip 없이 성공
- `./gradlew test --no-daemon --rerun-tasks`: cache를 쓰지 않은 재실행 성공
- `./gradlew clean build --no-daemon --stacktrace`: packaging을 포함한 전체 build 성공
- workflow와 test profile YAML parse 성공
- PR #3 최종 Backend CI run `29404652823`: Java 21 설정, wrapper 검증과 전체 build 성공
- merge commit `89cc9d7`의 `master` Backend CI run `29404784599`: 동일 gate 성공
- staged 변경과 commit의 로컬 gitleaks 검사: 노출 0건

## 회귀 방지와 남은 위험

모든 pull request와 `master` push는 같은 Gradle suite를 실행한다. 실패한 test report는 7일간
artifact로 남겨 원인을 확인할 수 있다.

H2 compatibility mode는 MySQL의 collation, transaction, index와 SQL dialect를 완전히
재현하지 않는다. 데이터 계층이 커지면 Testcontainers 기반 MySQL integration test를 별도
job으로 추가한다. Google STT/TTS와 실제 자격증명 연동도 이 smoke test의 검증 범위가 아니다.

## 인터뷰에서 설명할 질문

- 단위 테스트는 통과했는데 왜 저장소 전체 gate는 실패했는가?
- H2 test profile과 MySQL service container 사이에서 무엇을 기준으로 선택했는가?
- cloud client를 test profile에서 끄는 것이 어떤 회귀를 잡고 놓치는가?
- 테스트가 늘어날 때 Testcontainers로 전환할 기준은 무엇인가?
