# 이슈 관리

- 저장소: `kimsooin77/Sync`
- 이슈와 명세는 GitHub Issues에서 관리합니다.
- 이슈 작업에는 `gh` CLI를 사용합니다.
- 저장소 주소는 `git remote -v`로 확인합니다.
- PRs as a request surface: off

## 작업 규칙

- 생성: `gh issue create --title "제목" --body-file <본문파일>`
- 조회: `gh issue view <번호> --comments`
- 목록: `gh issue list --state open`
- 댓글: `gh issue comment <번호> --body-file <본문파일>`
- 라벨 추가: `gh issue edit <번호> --add-label "라벨"`
- 라벨 제거: `gh issue edit <번호> --remove-label "라벨"`
- 종료: `gh issue close <번호>`

여러 줄 본문은 임시 파일에 작성하고 `--body-file`로 전달합니다.
관련 티켓을 확인할 때는 본문, 댓글, 라벨을 함께 읽습니다.
스킬에서 이슈 트래커에 게시하도록 안내하면 GitHub 이슈를 생성합니다.
외부 게시와 댓글 작성에는 사용자의 요청 및 해당 스킬의 권한 범위를 따릅니다.
