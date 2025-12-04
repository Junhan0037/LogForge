# LogForge 로컬 도커 환경 가이드

로컬 개발에서 Postgres + Prometheus + Grafana 스택을 실행하는 방법을 정리했다. `docker/docker-compose.yml` 기준으로 동작하며, 호스트에서 애플리케이션(기본 8080 포트)을 실행하면 Prometheus가 Actuator 메트릭을 스크랩하고 Grafana에서 조회할 수 있다.

## 사전 준비
- Docker / Docker Compose가 설치되어 있어야 한다.
- 애플리케이션이 로컬에서 8080 포트로 실행되고 `/actuator/prometheus` 엔드포인트를 노출해야 한다. 포트가 다르면 `docker/prometheus.yml`의 `targets`를 수정한다.

## 실행 방법
```bash
cd docker
docker compose up -d
```

## 주요 서비스 정보
- Postgres: `localhost:5432`, 기본 DB/계정/비밀번호 `logforge` (환경 변수 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`로 변경 가능)
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (기본 계정 `admin` / `admin`, 환경 변수 `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD`로 변경 가능)
- Spring Batch 메타데이터: 컨테이너 최초 기동 시 `docker/initdb/01-batch-schema.sql`이 자동 적용되어 Batch 테이블이 생성된다.

## Prometheus 설정 수정
- 파일: `docker/prometheus.yml`
- 기본 타겟은 `host.docker.internal:8080/actuator/prometheus`이다.
- 애플리케이션을 컨테이너로 띄우는 경우 `targets`를 `app-service-name:8080` 형태로 변경한다.

## Grafana 프로비저닝
- 데이터소스: `docker/grafana-provisioning/datasources/datasource.yml`에서 Prometheus를 기본 데이터소스로 자동 등록한다.
- 대시보드는 필요 시 `docker/grafana-provisioning/dashboards/` 하위에 JSON을 추가하고 `dashboards.yml`을 작성해 프로비저닝할 수 있다.

## 정지 및 정리
```bash
# 컨테이너 중지
docker compose down

# 볼륨까지 모두 삭제(데이터 초기화)
docker compose down -v
```
