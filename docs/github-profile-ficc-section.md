### FICC Wash Trade Surveillance Model

금융권 시장 감시 모델 경험을 바탕으로 구현한 Spring Boot + MySQL 기반 포트폴리오 프로젝트입니다.

- MySQL request queue 기반 비동기 모델 실행 구조 구현
- `appid`, `region`, `model_class_name` 기반 DB-driven model registry 적용
- Stored procedure 기반 trade loading 및 threshold 기반 Wash Trade 탐지
- Production alert history와 Calibration result를 business key 기준으로 비교
- React/Vite frontend, Spring Boot backend, MySQL을 Docker Compose로 한 번에 실행
- GitHub Actions 기반 backend test / frontend build CI 구성
- AWS SDK for Java 기반 optional S3 CSV report export 구현

Repository: https://github.com/KiBeomLee16/Ficc_WashModel
