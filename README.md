
# 📚 Mogakcong Discord Bot (개인 개선 버전)

> 이 프로젝트는 아래 원본 레포지토리를 기반으로 개발되었습니다.  
> Original Repository: [https://github.com/dradnats1012/study-bot](https://github.com/dradnats1012/study-bot)  

개인 환경에 맞게 기능을 정리하고, 최신 라이브러리 대응 및 템플릿 개편 작업을 진행한 버전입니다.

---

## 🔧 주요 변경사항

### ✔ 1. **JDA 버전 업그레이드**

* **JDA 5.0.0 → 6.1.2**
* 새로운 이벤트 구조 및 변경된 타입 시스템에 맞게 전체 코드 리팩토링

### ✔ 2. **Team 관련 클래스 제거**

* 기존 Guild Team / TeamManager 관련 기능 미사용으로 판단해 **전체 제거**
* Listener / Command 구조 간소화

### ✔ 3. **기록 템플릿 개편**

* 일간/주간/월간 기록 메시지의 포맷 단순화

---

## 🚀 실행 방법

```bash
./gradlew bootJar
java -jar build/libs/studybot-*.jar
```

환경 변수 또는 `application.yml`의 Discord Token 값 설정은 필수입니다.

---

## 📌 목적

* 최신 JDA 환경에 맞춘 유지보수
* 팀 기능 제거로 코드 단순화
* 기록·통계 메시지 개선으로 스터디 운영 편의성 강화

---

## 📄 라이선스

원본 프로젝트의 라이선스를 그대로 따릅니다.
수정된 내용은 개인 학습 및 운영을 위해 변경되었습니다.

---
