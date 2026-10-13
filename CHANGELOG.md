# Changelog

형식 버전(`schemaVersion`)은 SDK 버전과 별개입니다. 지금 형식 버전은 1입니다.

## 0.1.0 (미배포)

처음 버전. docs 17 백로그의 M0~M5 대부분을 구현했습니다.

- core: 장면 문서·검증, 3x3 행렬·viewport, 명령·reducer, hit test, 변형·정렬·그룹 계산
- history: 트랜잭션 실행 취소, `KineticaSession`
- serialization: 형식 버전 1 JSON, migration 틀, 파일 저장소, JSON 중첩 상한
- motion: keyframe 평가, easing, 재생 제어, 장면 morph, 전환 진행률
- guides·presets: 안내선 스냅, 모션 프리셋 6종
- render·gestures·editor·player·export: Compose 그리기, 포인터 상태 기계, 편집 화면, 재생기·전환, PNG 내보내기
- java: Java 호스트용 정적 입구
- 샘플: sample-studio, sample-gallery, 소비자 확인 빌드
