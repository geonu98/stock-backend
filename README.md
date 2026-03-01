📄 Stock Dashboard

JWT 기반 Stateless 인증 구조와 Redis 캐시 전략을 적용한 실시간 주식 포트폴리오 관리 서비스

🔗 Demo

Frontend: https://stock-frontend-five-delta.vercel.app/

Backend: https://github.com/geonu98/stock-backend

Frontend Repo: https://github.com/geonu98/stock-frontend

🛠 Tech Stack

Backend
Spring Boot · Spring Security · JWT · Redis · PostgreSQL

Frontend
React (Vite) · Axios

Infra
Render · Vercel

📌 Key Features
🔐 Authentication

JWT 기반 Stateless 인증 구조

RefreshToken DB 관리 (UserDevice 1:1)

OAuth (Kakao / Google) 로그인 지원

이메일 인증 완료 후에만 JWT 발급

📊 Portfolio Management

매수/매도 거래 내역 기반 포지션 집계

평균 단가 및 실시간 평가 손익 계산

서버에서 집계 로직 수행하여 데이터 정합성 유지

📈 Market & Chart

TwelveData API 기반 시세 조회

7/30/90일 차트 데이터 제공

Redis 캐시 전략 적용으로 외부 API 호출 최소화

⚡ Performance & Caching

Sparkline / Daily Candle Redis 캐싱

MAX_DAYS(90) 단일 캐시 구조로 데이터 중복 제거

서버에서 필요한 구간만 slice하여 반환

외부 API 호출 3회 → 1회로 감소

⚠ Troubleshooting

Preview 배포 환경에서 CORS 차단 문제 → 환경변수 기반 Origin 관리로 해결

AccessToken 만료 시 refresh 동시성 문제 → Axios refreshPromise 적용

로컬/배포 환경 설정 불일치 → Spring profile 분리 및 환경변수 관리

🧠 Architecture Highlights

인증 정책을 “API 검사”가 아닌 “JWT 발급 조건”에서 통제

Refresh 재발급 동시성 제어로 401 루프 방지

외부 API 의존도를 줄이기 위한 캐시 기반 응답 구조 설계

📎 More Details

자세한 설계 과정 및 트러블슈팅은 노션 문서에 정리되어 있습니다.