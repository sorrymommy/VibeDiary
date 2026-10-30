# 사진을 찍고 나면 카메라가 다시 열리는 문제

- 날짜: 2026-10-30
- 대상 기능: 홈의 기록 버튼 → "사진 찍기"로 새 기록 화면을 열고 촬영
- 대상 파일: `ui/newentry/NewEntryViewModel.kt` (화면 `ui/newentry/NewEntryScreen.kt`의 `handleStart`, 촬영 런처는 그대로)

## 원인

카메라 앱이 떠 있는 동안 Android가 VibeDiary 프로세스를 정리할 수 있다(카메라는 메모리를 많이 쓴다). 돌아오면 Navigation이 `new/PHOTO` 화면을 복원하면서 `NewEntryViewModel`을 새로 만든다.

- `startHandled`가 일반 필드여서 다시 `false`가 되고, `LaunchedEffect(Unit)` → `handleStart()`가 카메라를 한 번 더 연다.
- `pendingPhoto`도 사라져서 방금 찍은 사진의 결과(`onPhotoTaken`)가 무시된다.

에뮬레이터에서 카메라를 연 채 `run-as com.vibediary.app kill <pid>`로 프로세스를 끝낸 뒤 촬영하여 재현했다. 복귀 직후 카메라 화면(새 `CaptureActivity`)이 다시 열렸다.

## 수정

- `NewEntryViewModel`이 `SavedStateHandle`을 받고, `startHandled`와 `pendingPhoto`(경로 문자열)를 저장 상태에 둔다.
- `onCleared`는 더 이상 `pendingPhoto`를 지우지 않는다(아래 1차 Minor).

## 1차 QA (별도 서브에이전트, 시니어 개발자 관점)

| 등급 | 문제 | 발생 상황 | 처리 |
|---|---|---|---|
| Minor | `onCleared`가 복원될 화면이 받아야 할 사진 파일을 지움 | "활동 유지 안 함" 등으로 카메라가 열린 사이 화면이 파괴되면 `onCleared`가 돈다. 카메라 앱이 출력 파일을 미리 열어 두는 경우 사진이 0바이트로 버려질 수 있음 | 수정: `onCleared`에서 `pendingPhoto` 삭제 제거. 실패·취소 정리는 `onPhotoTaken`이 맡는다. 화면을 진짜로 버린 경우 빈 jpg 하나가 남을 수 있음(수용) |
| Minor (기존) | 복원될 때마다 위치 권한을 다시 요청 | 위치 권한을 거절한 사용자가 사진을 찍고 돌아오면 권한 창이 다시 뜸 | 이번 범위 밖, 수정 안 함 |
| 참고 (기존) | 프로세스가 정리되면 메모·먼저 찍은 사진·녹음 등 나머지 초안이 사라짐 | 메모를 쓰다가 "사진 찍기"를 누르고, 그 사이 프로세스가 정리된 경우. 이전 파일은 고아 파일로 남음 | 이번 범위 밖. 같은 방식으로 저장 상태에 두는 후속 작업 제안 |
| Major (별건) | 프로세스가 정리된 뒤 다시 열면 PIN 잠금을 건너뜀 | 잠금 해제 상태로 나갔다가 프로세스가 정리되면 `KEY_LOCKED=false`가 복원되고 `stoppedAt`이 0이라 재잠금이 안 됨 (`MainActivity`) | 이번 수정과 별개. 후속 작업으로 제안 |
| Nit (기존) | 카메라 앱이 없으면 `takePicture.launch`에서 앱 종료 | 카메라가 없는 기기 | 수정 안 함 |

정상으로 확인된 동작(QA 판단):
- `NavBackStackEntry`의 기본 팩토리가 `(Application, SavedStateHandle)` 생성자를 만들어 준다(lifecycle 2.9.4).
- 복원 순서: ViewModel이 저장 상태를 읽은 뒤 런처가 등록되며 보관된 결과가 전달되므로 `onPhotoTaken` 시점에 `pendingPhoto`가 살아 있다. 그 뒤 `handleStart`는 `startHandled`로 막힌다.
- 화면 회전, 촬영 취소, 화면 안 "사진 찍기" 버튼, 앨범에서 고르기, 메모·음성 시작 모드는 영향 없음.

## 직접 검증한 것

- `gradlew.bat assembleDebug` 빌드 성공(수정 전후 모두).
- 에뮬레이터: 수정 빌드에서 카메라를 연 채 프로세스를 정리하고 촬영 → 새 기록 화면으로 돌아오고 카메라는 다시 열리지 않음.
- 에뮬레이터에서는 프로세스가 아직 시작 중이라 카메라 앱이 사진 파일을 쓰지 못했다("No content provider"). 앱은 빈 파일을 지우고 조용히 넘어갔다.
- 실기기(Galaxy S23): 사용자가 직접 촬영해 보고 "잘 작동한다"고 확인(2026-10-30).

## 아직 확인하지 못한 것

- 프로세스가 정리된 뒤 돌아왔을 때 찍은 사진이 실제로 붙는지(에뮬레이터의 느린 콜드 스타트 때문에 확인 불가). 실기기 확인 때 프로세스가 실제로 정리됐는지는 기록하지 않았다.
- "활동 유지 안 함"을 켠 상태의 실제 동작.
