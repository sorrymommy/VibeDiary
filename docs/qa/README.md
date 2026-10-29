# QA 기록

CLAUDE.md 2번 규칙(수정 후 시니어 개발자 관점의 별도 QA)에 따라 진행한 QA의 분석 내용과 수정 결과를 남깁니다. 새 기록은 위에 추가합니다.

| 날짜 | 대상 | 결과 요약 | 문서 |
|---|---|---|---|
| 2026-10-29 | 녹음 압축 저장(m4a), 10분 넘는 녹음은 글로 옮기지 않음 | 5차까지 진행. Major 7건(녹음 유실 2, 편집 중 분석 완료·회전 시 덮어쓰기 5) 모두 수정. 일부 Minor는 알려진 제약으로 남김. 압축 시 크기 약 1/8(에뮬레이터 확인). | [2026-10-29-audio-compression-and-length-limit.md](2026-10-29-audio-compression-and-length-limit.md) |
| 2026-10-29 | 홈 "오늘의 노선" 요약 카드 (가운데 정렬, 6개부터 스크롤, 정류장 사이에만 선) | 1차: Minor 4, Nit 5 → 8건 수정, 1건(이름 폭 1/5 고정)은 의도대로 유지. 2차: Minor 1 수정, Nit 3 유지. 에뮬레이터에서 1·2·6개 확인. | [2026-10-29-today-route-summary.md](2026-10-29-today-route-summary.md) |
| 2026-10-28 | 음성이 일부만 글로 옮겨지는 문제 | 원인: 녹음을 한꺼번에 넣어 엔진이 첫 구간 뒤 종료. 2배속 분할 입력으로 수정(실기기 실험). QA 3차: Major 2, Minor 6, Nit 2 → 모두 수정, 위험 1건 수용. | [2026-10-28-transcription-cut-off.md](2026-10-28-transcription-cut-off.md) |
| 2026-10-28 | 녹음 일시정지·이어서 녹음, 저장 전 음성 변환 | Critical 1, Major 3, Minor 8, Nit 다수 → 모두 수정 또는 의도된 동작으로 정리. 3차 재검토에서 Critical·Major 없음. | [2026-10-28-recording-pause-and-transcript.md](2026-10-28-recording-pause-and-transcript.md) |
