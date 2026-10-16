# 앱·랜딩 CI/CD

`main`에 병합하면 `.github/workflows/ci.yml`이 검증한 **동일한 빌드**를 앱과 랜딩에 배포한다. 운영 인증을 아래와 같이 한 번 설정해야 첫 배포가 가능하다. 인증이 빠져 있으면 배포 작업은 명시적으로 실패하며 기존 서비스는 유지된다.

## 흐름

1. PR: 프런트 타입 검사·빌드, 랜딩 테스트·빌드, 배포 검증 테스트, 기존 백엔드 CI를 실행한다. `검증 완료`가 필수 병합 검사다.
2. main: 같은 검사를 다시 통과한 빌드를 SHA별 artifact로 보관하고, 앱은 AWS OIDC 역할, 랜딩은 보호된 환경의 Vercel 토큰으로 배포한다.
3. AWS: 실제 도메인·S3 원본을 확인 → 덮어쓸 파일 백업 → `_releases/<SHA>/` 보관 → 정적 파일 업로드 → HTML·release.json 교체 → CloudFront 무효화 완료 대기.
4. Vercel: 팀·프로젝트·도메인 확인 → `--prebuilt --prod --skip-domain`으로 운영 빌드 준비 → 인증된 `vercel curl`로 내용 검사 → 같은 배포를 `promote`.
5. 실제 서비스: 일반 URL에서 `release.json`의 SHA와 **전체 배포 파일**의 SHA-256을 비교한다. 1440px/390px 브라우저에서 앱 홈·로그인, 랜딩 홈, 공통 로고 로딩, 파비콘 링크, 가로 넘침과 JS 오류를 검사한다.
6. 실패: 앱은 백업 파일을 복원하고 다시 무효화한다. 랜딩은 이전 운영 배포로 rollback한다. 검증·복구 결과 JSON과 화면 캡처는 Actions artifact로 30일 보관한다. 복구 오류도 작업 실패로 남는다.

두 사이트는 독립적으로 배포·복구한다. 하나가 실패해도 성공한 사이트를 함께 되돌리지는 않는다. 앱의 복구는 S3의 서비스 파일을 복원하며, 첫 도입 이전에 없었던 새 파일은 남겨 둔다. 기존 해시 자산은 열려 있는 브라우저 탭을 위해 삭제하지 않는다. `_releases/` 보관분은 별도 수명 주기를 정하기 전까지 유지한다.

모든 main 실행은 두 프런트를 배포한다. 연속 병합으로 중간 Actions 실행이 대기열에서 생략돼도 마지막 main이 모든 변경을 포함하게 하기 위함이다. 실행 중인 main 배포는 자동 취소하지 않으며, 이미 최신 main이 아닌 실행은 운영 인증 전에 중단한다. 수동 AWS/Vercel 배포를 동시에 실행하지 않는다.

## GitHub 환경 설정

관리 권한으로 저장소 루트에서 다음 명령을 실행한다. 비밀 값은 다루지 않는다.

```sh
python3 infra/deploy/configure-github.py
```

`production-app`, `production-landing` 환경은 `main` 브랜치만 허용한다. main 병합 후 수동 배포 승인은 요구하지 않는다. PR CI가 한 번 성공한 후 필수 검사·강제 push 금지·PR 병합 규칙을 설정한다.

```sh
python3 infra/deploy/configure-github.py --protect-main
```

## AWS 최초 연결

운영 AWS 계정에서 IAM·CloudFormation 설정 권한으로 [CloudShell](https://console.aws.amazon.com/cloudshell/home)을 연다. 저장소 코드를 검토한 후 실행한다.

```sh
git clone https://github.com/Port-051/QueueMate.git
cd QueueMate
bash infra/deploy/bootstrap-aws.sh
```

main 병합 전 설정하려면 이 PR 브랜치를 checkout한 다음 실행한다. 기본 배포 ID는 `E256CECAKU833U`이며 스크립트는 `app.queue-mate.com` 별칭과 기본 S3 원본을 실제 조회하여 검증한다. 버킷 이름·prefix·리전을 추측하지 않는다. 대상이 바뀌면 배포 ID를 첫 인자로 전달한다.

생성되는 역할 `queuemate-github-frontend-deploy`은 해당 S3 원본의 조회·업로드 및 해당 CloudFront 배포의 조회·무효화만 허용한다. 액세스 키를 발급하지 않는다. 신뢰 정책은 다음 저장소의 immutable OIDC subject에 한정된다.

```text
repo:Port-051@293762925/QueueMate@1350658456:environment:production-app
```

이는 `gh api repos/Port-051/QueueMate/actions/oidc/customization/sub`의 실제 설정이다. 저장소 이전·재생성·OIDC 설정 변경 시 역할 신뢰 정책을 함께 검토한다. 기존 GitHub OIDC provider가 있으면 재사용하며 provider에 `sts.amazonaws.com` audience가 없으면 중단한다.

스크립트가 출력하는 `.deploy/aws-config.json`은 **역할 ARN·버킷 등 비밀이 아닌 설정**만 담는다. GitHub에 로그인된 환경으로 가져와 다음을 실행하거나, 같은 값을 `production-app`의 Variables에 직접 입력한다.

```sh
python3 infra/deploy/configure-github.py --aws-config .deploy/aws-config.json
```

| production-app Variable | 값 |
| --- | --- |
| AWS_ROLE_ARN | 생성된 역할 ARN |
| AWS_REGION | 조회된 S3 리전 |
| AWS_S3_BUCKET | 조회된 운영 버킷 |
| AWS_S3_PREFIX | CloudFront OriginPath, 루트이면 생략 |
| AWS_CLOUDFRONT_DISTRIBUTION_ID | 검증된 배포 ID |

## Vercel 최초 연결

운영 프로젝트는 팀 `port-52`의 `queue-mate-landing`이다. 설정 스크립트가 아래 ID를 GitHub 환경 Variables에 저장한다.

| production-landing Variable | 값 |
| --- | --- |
| VERCEL_ORG_ID | team_TYonFQEoE1HOMC5bYb5xvQXV |
| VERCEL_PROJECT_ID | prj_twOVEOMaFLLQtgu0n4o5l7pmggiu |
| VERCEL_SCOPE | port-52 |

운영 팀에 접근할 수 있는 계정으로 [Vercel 토큰 설정](https://vercel.com/account/settings/tokens)에서 배포에 필요한 접근 범위와 만료 기간으로 토큰을 만들고, [GitHub Environments](https://github.com/Port-051/QueueMate/settings/environments)의 `production-landing`에 **Environment secret `VERCEL_TOKEN`**으로 직접 입력한다. 토큰을 저장소·채팅·로그에 넣지 않는다. 만료 전에 같은 secret을 교체한다.

Vercel Git 자동 배포가 기존 `feat/seo-landing`에 연결되어 있다면 프로젝트의 기존 Git 연결을 해제해 배포 주체를 이 Actions 워크플로로 단일화한다. main의 `landing/vercel.json`도 Git 자동 배포를 비활성화한다. 팀 접근권한이 없으면 이 연결 전환을 완료할 수 없다. Vercel Deployment Protection은 유지한다.

랜딩은 환경 변수 없는 정적 사이트이므로 테스트가 끝난 `dist`를 Build Output API v3 형식으로 패키징한다. clean URL·404·보안/검색 헤더를 함께 보존한다. Vercel CLI와 라우팅 라이브러리 버전은 `scripts/deploy/package-lock.json`에 고정되어 있다.

## 확인과 재시도

[Actions CI](https://github.com/Port-051/QueueMate/actions/workflows/ci.yml)에서 main 실행의 두 운영 배포 작업까지 초록색인지 확인한다. `검증 완료`는 배포 전 검사이며 이것만으로 운영 반영 완료를 뜻하지 않는다.

- 앱: `https://app.queue-mate.com/release.json`
- 랜딩: `https://queue-mate.com/release.json`
- 증거: `app-deployment-<SHA>`, `landing-deployment-<SHA>` artifact의 JSON·PNG

연결 설정 후 Actions의 **Run workflow → main**으로 두 사이트를 배포한다. CLI로는 다음과 같다.

```sh
gh workflow run ci.yml --repo Port-051/QueueMate --ref main
```

검증에 실패하면 실패 로그와 artifact를 확인한다. 수정 PR을 병합하거나 인증 문제를 해결한 뒤 **최신 main**을 다시 실행한다. 서버 재시작이나 DNS 변경은 필요하지 않다. 이 작업은 앱 프런트와 랜딩만 배포하며 백엔드는 기존 CI 검사만 유지한다.

로고 원본은 `frontend/public/brand/queuemate-mark.svg` 하나다. 랜딩 빌드가 이 파일로 로고·SVG/ICO 파비콘·Apple 아이콘을 생성한다. 파생 파일을 수작업으로 수정하지 않는다.
