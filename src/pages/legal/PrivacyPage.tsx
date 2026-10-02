import { Link } from 'react-router-dom';
import { LEGAL } from '../../domain/legal';
import { LegalLayout, LegalSection, LegalTable, LegalToc, Ph, useLegalLang } from './LegalLayout';

/**
 * `/privacy` — 개인정보 처리방침(2026-10-02 소유자 결정 · 한국어 본문 + `?lang=en` 영어판).
 *
 * 항목은 「개인정보 보호법」 제30조 제1항 · 시행령 제31조 제1항을 따른다(근거 조사 — 2026-10-02 · law.go.kr 현행 원문).
 * 적힌 사실은 코드로 확인한 것이다(소셜 scope · 저장하는 칸 · 쿠키 넷 · 브라우저 저장소 키 · 외부 연동 · 음성 연결) — 코드가 바뀌면 이 문서도 같이 바꾼다.
 * **정해지지 않은 것은 대괄호 자리표시**(`domain/legal.ts` 의 값 · 위탁 · 국외 이전 · 로그 · 백업)이고 배포 전에 채운다.
 * 회원 탈퇴는 platform P-48(`DELETE /users/me` — 2026-10-02)이고 "설정 > 회원 탈퇴" 는 내 정보 화면 맨 아래 버튼이다(`MyInfoPage`).
 * 탈퇴해도 남는 것 — 다른 이용자와 함께 확정한 파티 모집 글(작성자 표시만 지운다 · 4항 · 13항). 탈퇴하면 그 브라우저의 그 사람 키를 지운다(`state/userStorage.ts` — 12항).
 */
export function PrivacyPage() {
  const lang = useLegalLang();
  return lang === 'en'
    ? <LegalLayout lang="en" title="Privacy Policy"><PrivacyEn /></LegalLayout>
    : <LegalLayout lang="ko" title="개인정보 처리방침"><PrivacyKo /></LegalLayout>;
}

const Mail = () => <Ph value={LEGAL.contactEmail} />;

const ext = { target: '_blank', rel: 'noopener noreferrer' } as const;

const KO_TOC = [
  { id: 'ko-1', title: '개인정보의 처리 목적' },
  { id: 'ko-2', title: '처리하는 개인정보의 항목' },
  { id: 'ko-3', title: '처리 근거' },
  { id: 'ko-4', title: '처리 및 보유 기간' },
  { id: 'ko-5', title: '파기 절차와 방법' },
  { id: 'ko-6', title: '외부 서비스 연동' },
  { id: 'ko-7', title: '개인정보 처리의 위탁' },
  { id: 'ko-8', title: '개인정보의 국외 이전' },
  { id: 'ko-9', title: '다른 이용자에게 보이는 정보' },
  { id: 'ko-10', title: '음성 · 채팅과 연결 정보' },
  { id: 'ko-11', title: '빠른매치의 자동 배정' },
  { id: 'ko-12', title: '쿠키와 브라우저 저장소' },
  { id: 'ko-13', title: '이용자의 권리와 행사 방법' },
  { id: 'ko-14', title: '만 14세 미만 아동' },
  { id: 'ko-15', title: '안전성 확보 조치' },
  { id: 'ko-16', title: '개인정보 보호책임자' },
  { id: 'ko-17', title: '권익침해 구제방법' },
  { id: 'ko-18', title: '이 방침의 변경' },
];

function PrivacyKo() {
  return <>
    <h1>개인정보 처리방침</h1>
    <p className="legal-meta">시행일 <Ph value={LEGAL.effectiveDate} /> · 운영자 <Ph value={LEGAL.teamName} /> · <Link to="/privacy?lang=en" lang="en">English</Link></p>
    <p className="legal-lead">
      <Ph value={LEGAL.teamName} />(이하 “운영자”)은 QueueMate(이하 “서비스”)를 운영하며 이용자의 개인정보를 「개인정보 보호법」 등 관련 법령에 따라 처리합니다.
      이 방침은 서비스가 어떤 개인정보를 왜, 얼마 동안 처리하는지와 이용자가 권리를 행사하는 방법을 알려 드립니다.
    </p>

    <section className="legal-summary" aria-labelledby="ko-summary">
      <h2 id="ko-summary">한눈에 보기</h2>
      <ul>
        <li>받는 정보 — 소셜 로그인 제공자의 회원 번호와 서비스 닉네임, 그리고 직접 연결한 경우에만 게임 계정과 전적</li>
        <li>받지 않는 정보 — 이메일, 전화번호, 실명, 생년월일, 비밀번호, 결제 정보</li>
        <li>광고 · 분석 쿠키와 외부 추적 도구를 쓰지 않습니다</li>
        <li>탈퇴할 때까지 보관하고, 탈퇴하면 지체 없이 삭제합니다</li>
        <li>파티의 음성 · 채팅은 브라우저끼리 직접 연결되며 서버에 저장되지 않습니다</li>
        <li>만 14세 미만은 가입할 수 없습니다</li>
      </ul>
    </section>

    <LegalToc items={KO_TOC} label="목차" />

    <LegalSection id="ko-1" title="1. 개인정보의 처리 목적">
      <p>운영자는 다음 목적으로만 개인정보를 처리합니다. 목적이 바뀌면 법령에 따라 필요한 조치를 합니다.</p>
      <ul>
        <li><strong>회원 가입과 로그인</strong> — 소셜 로그인으로 회원을 구별하고 로그인 상태를 유지합니다.</li>
        <li><strong>서비스 제공</strong> — 빠른매치(조건이 맞는 팀원 자동 배정), 파티 모집 게시판(방 만들기 · 참가 · 확정), 파티 방의 음성 · 채팅 연결 중개, 게임 계정 연결과 티어 · 전적 표시, 친구 · 차단 · 최근 함께한 사람 기능</li>
        <li><strong>안전한 이용 환경</strong> — 차단 관계 반영, 신고 접수와 처리, 부정 이용 방지</li>
        <li><strong>문의 응대</strong>와 이용자의 권리 행사 처리</li>
      </ul>
    </LegalSection>

    <LegalSection id="ko-2" title="2. 처리하는 개인정보의 항목">
      <LegalTable caption="처리하는 개인정보" head={['구분', '항목', '언제 · 어떻게']} rows={[
        ['회원 가입 · 로그인 (필수)', '소셜 로그인 제공자의 종류(카카오 · 디스코드 · 구글), 제공자가 발급한 회원 번호, 서비스 닉네임, 서비스 회원 번호', '소셜 로그인 뒤 닉네임을 정해 가입할 때'],
        ['게임 계정 연결 (선택)', <ul>
          <li>리그 오브 레전드 — Riot ID(게임 이름#태그), Riot 계정 식별자(PUUID), 랭크 티어(솔로 · 자유), 전적 요약(최근 10경기의 승패와 평균 킬 · 데스 · 어시스트 등, 숙련도 상위 3개 챔피언, 시즌 승패)</li>
          <li>배틀그라운드 — 닉네임, 플랫폼(서버), PUBG 계정 식별자, 랭크 티어, 랭크 전적 요약(판 수 · 평균 피해량 · K/D · 치킨률 등)</li>
          <li>발로란트 — 닉네임, 이용자가 직접 입력한 티어</li>
        </ul>, '이용자가 내 정보에서 게임 계정을 연결할 때. 리그 오브 레전드 · 배틀그라운드의 티어 · 전적은 연결할 때, 그리고 그 뒤 로그인할 때(마지막으로 받은 지 1시간이 지났으면) 게임사 API에서 받아 새로 고칩니다'],
        ['모집 글 · 파티 (이용 시)', '모집 글(제목 · 설명 · 게임 · 모드 · 포지션 · 음성 조건), 방에 들어간 사람과 고른 포지션, 확정된 파티의 파티원과 확정 · 종료 시각', '글을 쓰거나 방에 참가 · 확정할 때'],
        ['빠른매치 (이용 시)', '매칭 조건(게임 · 모드 · 포지션 또는 역할 · 티어 · 음성 · 플레이 목적), 대기 시작 시각, 제안 수락 · 거절', '빠른매치를 시작했을 때'],
        ['소셜 기능 (이용 시)', '친구와 친구 요청, 차단 목록, 최근 함께한 사람, 신고(사유 · 상세 내용 최대 1,000자 · 신고 대상 · 관련 글 번호)', '각 기능을 쓸 때'],
        ['로그인 유지 (자동)', '로그인 유지 토큰(무작위 값과 회원 번호의 연결)', '로그인할 때'],
        ['서비스 기록 (자동)', <>서버 로그(요청 처리 기록과 회원 번호). 서비스 프로그램은 IP 주소를 기록하지 않습니다. 배포 환경의 접속 기록: <Ph value="[배포 때 확정 — 클라우드 · CDN 의 접속 기록(IP 주소 등) 보관 여부와 기간]" /></>, '서비스를 이용할 때'],
      ]} />
      <ul>
        <li>이메일, 전화번호, 실명, 생년월일, 성별, 비밀번호, 결제 정보는 받지 않습니다. 소셜 로그인 제공자의 접근 토큰도 저장하지 않습니다.</li>
        <li>제공자가 알려 주는 닉네임 · 이름은 가입 화면에서 닉네임을 제안하는 데만 쓰고 따로 저장하지 않습니다. 이용자가 그 이름을 그대로 닉네임으로 정하면 서비스 닉네임으로 저장됩니다.</li>
        <li>소셜 로그인 뒤 가입을 마치지 않으면 서버에는 아무것도 저장되지 않습니다. 가입을 기다리는 정보는 10분 동안만 유효한 서명된 쿠키에 담깁니다.</li>
      </ul>
      <h3>소셜 로그인 제공자별로 받는 정보</h3>
      <LegalTable caption="소셜 로그인" head={['제공자', '요청하는 권한', '서비스가 저장하는 것']} rows={[
        ['카카오', <>닉네임(<code>profile_nickname</code>)</>, '카카오 회원 번호'],
        ['디스코드', <><code>identify</code></>, '디스코드 사용자 ID'],
        ['구글', <><code>openid</code> · <code>profile</code></>, <>구글 계정의 고유 식별자(<code>sub</code>)</>],
      ]} />
      <p>
        구글 계정으로 로그인하면 구글에서 고유 식별자(<code>sub</code>)와 이름(<code>name</code>)을 받습니다. <code>sub</code>는 회원을 구별하는 데만 쓰고,
        <code>name</code>은 가입 화면의 닉네임 제안에만 씁니다. 구글 사용자 데이터를 광고에 쓰거나 판매하거나 다른 회사에 넘기지 않으며, 탈퇴하거나 삭제를 요청하면 지웁니다.
      </p>
    </LegalSection>

    <LegalSection id="ko-3" title="3. 처리 근거">
      <p>
        회원 가입 · 로그인과 서비스 제공에 필요한 정보는 이용약관에 따른 서비스 제공(계약의 체결 · 이행)을 위해 처리합니다.
        게임 계정 연결, 모집 글, 빠른매치, 친구 · 신고 같은 선택 기능의 정보는 이용자가 그 기능을 직접 사용할 때 그 기능을 제공하기 위해 처리합니다.
        민감정보와 고유식별정보는 처리하지 않습니다.
      </p>
    </LegalSection>

    <LegalSection id="ko-4" title="4. 처리 및 보유 기간">
      <ul>
        <li><strong>회원 정보와 서비스 이용 중 생긴 정보</strong>(게임 계정 · 전적, 모집 글, 파티 기록, 친구 · 차단 · 신고, 최근 함께한 사람)는 탈퇴할 때까지 보관하고, 탈퇴하면 지체 없이 삭제합니다. 다만 <strong>다른 이용자와 함께 확정한 파티 모집 글</strong>은 다른 파티원의 기록이므로, 탈퇴하면 작성자 표시를 지운 채 제목 · 설명 · 파티원 기록이 남습니다(탈퇴한 사람의 파티원 표시는 지워집니다).</li>
        <li>모집 글은 모집이 끝난 뒤(만료 · 확정)에도 게시판에 남습니다. 글을 “지우기” 하면 모집이 끝난 상태로 바뀌고, 글 자체는 탈퇴할 때 삭제됩니다(다른 이용자와 함께 확정한 글은 위처럼 작성자 표시만 지워집니다). 탈퇴 전에 글을 지워 달라고 하려면 <Mail />로 요청할 수 있습니다.</li>
      </ul>
      <LegalTable caption="짧게만 보관하는 정보" head={['정보', '보관']} rows={[
        ['가입을 기다리는 정보(서명된 쿠키)', '10분'],
        ['로그인 유지 토큰', '7일(쓸 때마다 새로 발급) · 로그아웃하면 바로 삭제'],
        ['방에 있는 사람 · 고른 포지션', '방이 없어질 때까지. 방은 접속 확인이 끊기면 길어야 10분 뒤 자동으로 없어집니다'],
        ['강퇴 · 나가기 기록(같은 방 다시 들어가기 · 자동 합류 제한)', '10분'],
        ['빠른매치 요청 · 제안', '매칭이 진행되는 동안. 취소하면 지워지고, 파티가 확정되면 길어야 10분 안에 자동으로 지워집니다'],
        ['제안 거절 기록(같은 상대와 다시 묶이지 않게)', '10분'],
        ['서버 로그', <Ph value="[배포 때 확정]" />],
      ]} />
      <p>다른 법령에 따라 보존해야 하는 정보가 있으면 그 기간 동안 다른 정보와 분리해 보관합니다. <Ph value="[배포 때 확정 — 해당 법령과 항목. 없으면 이 문장 삭제]" /></p>
    </LegalSection>

    <LegalSection id="ko-5" title="5. 파기 절차와 방법">
      <ul>
        <li>탈퇴하거나 보유 기간이 끝나면 지체 없이 파기합니다.</li>
        <li>데이터베이스의 정보는 복구할 수 없도록 삭제하고, 메모리 저장소(Redis)의 정보는 삭제하거나 보관 기간이 지나면 자동으로 사라지게 합니다.</li>
        <li>백업 — <Ph value="[배포 때 확정 — 백업 보관 여부와 기간]" /></li>
        <li>종이 문서로는 개인정보를 처리하지 않습니다.</li>
      </ul>
    </LegalSection>

    <LegalSection id="ko-6" title="6. 외부 서비스 연동">
      <p>서비스는 기능을 제공하기 위해 다음 외부 서비스와 정보를 주고받습니다. 각 서비스 안에서의 개인정보 처리는 그 회사의 방침을 따릅니다.</p>
      <LegalTable caption="외부 서비스" head={['상대', '주고받는 정보', '목적', '보내는 쪽']} rows={[
        ['카카오 · 디스코드 · 구글', '이용자가 제공자의 로그인 화면에서 직접 로그인하면, 서비스는 회원 번호와 닉네임(이름)을 받습니다', '소셜 로그인', '서버'],
        ['Riot Games (Riot Games API)', '게임 이름#태그, 그 뒤 Riot 계정 식별자(PUUID)', '리그 오브 레전드 계정 확인과 티어 · 전적 조회', '서버'],
        ['KRAFTON (PUBG API)', '닉네임, 플랫폼, PUBG 계정 식별자', '배틀그라운드 계정 확인과 티어 · 전적 조회', '서버'],
        ['Google (STUN 서버 stun.l.google.com)', '이용자의 브라우저가 접속해 자기 공인 IP 주소를 확인합니다. 회원 정보는 보내지 않습니다', '음성 · 채팅 연결', '브라우저'],
        ['Riot Games (Data Dragon · ddragon.leagueoflegends.com)', '챔피언 이미지를 받습니다. 회원 정보는 보내지 않지만 접속 정보(IP 주소 등)가 그 서버에 남을 수 있습니다', '챔피언 이미지 표시', '브라우저'],
      ]} />
      <p>운영자는 이용자의 개인정보를 판매하지 않으며, 위 연동과 법령에 따른 경우 말고는 다른 회사에 제공하지 않습니다.</p>
    </LegalSection>

    <LegalSection id="ko-7" title="7. 개인정보 처리의 위탁">
      <p>운영자는 서비스 운영을 위해 다음과 같이 개인정보 처리 업무를 위탁합니다.</p>
      <p><Ph value="[배포 때 확정 — 클라우드(AWS 서울 리전 예정) 등 수탁자와 위탁 업무]" /></p>
    </LegalSection>

    <LegalSection id="ko-8" title="8. 개인정보의 국외 이전">
      <p><Ph value="[배포 때 확정 — 클라우드 리전(AWS 서울 리전 예정)과 외부 연동(6항)의 국외 이전 해당 여부]" /></p>
      <p className="legal-note">국외 이전이 있으면 이전되는 항목, 이전되는 국가 · 시기 · 방법, 받는 자, 받는 자의 이용 목적과 보유 기간, 이전을 거부하는 방법과 그 효과를 여기에 적습니다.</p>
    </LegalSection>

    <LegalSection id="ko-9" title="9. 다른 이용자에게 보이는 정보">
      <ul>
        <li><strong>파티 모집 게시판</strong> — 로그인한 이용자에게 글의 내용과, 방에 있는 사람의 서비스 닉네임 · 회원 번호 · 게임 닉네임(Riot ID 등) · 티어 · 전적 요약 · 고른 포지션이 보입니다. 확정된 글에는 확정 순간의 파티원이 계속 표시됩니다(탈퇴한 파티원은 빠지고, 작성자가 탈퇴하면 작성자 표시가 없어집니다 — 4항). 어느 한쪽이라도 차단한 이용자에게는 그 방이 보이지 않습니다.</li>
        <li><strong>빠른매치</strong> — 제안을 받거나 파티가 확정되면 같은 파티원끼리 서비스 닉네임 · 게임 프로필 · 고른 포지션을 봅니다.</li>
        <li><strong>방 안</strong> — 같은 방 사람끼리 닉네임 · 게임 프로필 · 음성 연결 상태가 보입니다.</li>
        <li><strong>친구 · 최근 함께한 사람</strong> — 친구와 친구 요청 상대, 함께 파티를 했던 이용자에게 서비스 닉네임과 회원 번호가 보입니다. 회원 번호는 친구 요청을 보낼 때 쓰입니다.</li>
        <li><strong>신고</strong> — 신고한 사실과 내용은 신고 대상에게 알리지 않습니다.</li>
        <li>서비스에는 사람을 검색하거나 둘러보는 기능이 없습니다.</li>
      </ul>
    </LegalSection>

    <LegalSection id="ko-10" title="10. 음성 · 채팅과 연결 정보">
      <ul>
        <li>파티 방의 음성과 채팅은 WebRTC로 이용자의 브라우저끼리 직접 연결되며, 서버는 그 내용을 중계하거나 저장하지 않습니다.</li>
        <li>연결을 맺으려고 브라우저가 만든 연결 정보(IP 주소와 포트 포함)는 서버를 거쳐 같은 방 사람의 브라우저로 전달됩니다. 서버는 이 정보를 전달만 하고 저장하지 않습니다. 그래서 같은 방 사람은 기술적으로 이용자의 IP 주소를 알 수 있습니다.</li>
        <li>음성은 이용자가 마이크를 켰을 때만 보내집니다.</li>
      </ul>
    </LegalSection>

    <LegalSection id="ko-11" title="11. 빠른매치의 자동 배정">
      <p>
        빠른매치는 이용자가 고른 조건으로 팀원을 시스템이 자동으로 묶습니다. 배정에는 게임 · 모드 · 포지션(역할) · 티어 · 음성 · 플레이 목적과 차단 관계, 최근 거절 기록이 쓰입니다.
        제안이 오면 수락하거나 거절할 수 있고, 모두 수락해야 파티가 확정됩니다. 배정 기준을 설명받고 싶으면 <Mail />로 요청할 수 있습니다.
      </p>
    </LegalSection>

    <LegalSection id="ko-12" title="12. 쿠키와 브라우저 저장소">
      <LegalTable caption="쿠키" head={['이름', '용도', '보관']} rows={[
        [<code>qm_access</code>, '로그인 상태 확인', '15분'],
        [<code>qm_refresh</code>, '로그인 유지(다시 발급)', '7일'],
        [<code>qm_social_signup</code>, '소셜 로그인 뒤 가입을 마칠 때까지의 가입 대기 정보', '10분'],
        [<code>qm_oauth_state</code>, '소셜 로그인 요청의 위조 방지', '10분'],
      ]} />
      <p>
        모두 서비스를 쓰는 데 꼭 필요한 쿠키이고, 자바스크립트가 읽을 수 없는(HttpOnly) 쿠키입니다. 광고 · 분석 쿠키와 외부 추적 도구는 쓰지 않습니다.
        브라우저 설정에서 쿠키를 막을 수 있지만, 막으면 로그인할 수 없습니다.
      </p>
      <p>화면을 편하게 쓰도록 이용자의 브라우저 저장소(localStorage · sessionStorage)에 다음 값을 둡니다. 이 값은 이용자의 브라우저에만 저장되며, 브라우저의 사이트 데이터 삭제로 지울 수 있습니다. 회원 탈퇴를 하면 탈퇴한 그 브라우저에 남은 그 사람의 값(아래에서 이름이 <code>…</code>로 끝나는 값)을 지웁니다.</p>
      <ul>
        <li>마지막으로 누른 소셜 로그인 버튼 (<code>qm.lastProvider</code>)</li>
        <li>게임 계정 연결 안내를 지나갔는지 (<code>qm.onboarding.done:…</code>)</li>
        <li>최근 빠른매치 조건 3개와 게임별로 마지막에 고른 빠른매치 조건 (<code>qm.recentConditions</code> · <code>queuemate:introduction:…</code>)</li>
        <li>진행 중인 빠른매치 요청 · 파티의 번호와 조건, 내가 나간 빠른매치 파티(10분) (<code>qm.activeMatch.…</code> · <code>qm.activeParty.…</code> · <code>qm.activePartyInfo.…</code> · <code>qm.leftParties.…</code>)</li>
        <li>예약 화면의 입력 초안 (<code>queuemate:reservation-draft:…</code>)</li>
        <li>한 번만 보여 줄 알림과 이미 연 제안 — 그 탭에서만 (<code>qm.settings.notice</code> · <code>qm.proposalShown.…</code>)</li>
        <li>이전 버전이 남긴 메시지 · 알림 기록(<code>qm:direct-messages:…</code> · <code>qm:notifications:…</code>)이 남아 있을 수 있습니다. 지금 서비스는 이 값을 읽지 않습니다.</li>
      </ul>
    </LegalSection>

    <LegalSection id="ko-13" title="13. 이용자의 권리와 행사 방법">
      <ul>
        <li>이용자는 언제든지 자기 개인정보의 열람 · 정정 · 삭제와 처리 정지를 요구할 수 있습니다.</li>
        <li><strong>서비스 안에서 바로 할 수 있는 것</strong> — 닉네임 변경, 게임 계정 연결 해제, 소셜 계정 연결 · 해제(마지막 하나는 해제할 수 없습니다), 친구 삭제 · 차단 해제, 모집 글 지우기, 로그아웃, <strong>회원 탈퇴(설정 &gt; 회원 탈퇴)</strong>. 탈퇴하면 4항의 정보를 지체 없이 모두 삭제합니다. 다만 다른 이용자와 함께 확정한 파티 모집 글은 다른 파티원의 기록이므로, 작성자 표시를 지운 채 제목 · 설명 · 파티원 기록이 남습니다(탈퇴한 사람의 파티원 표시는 지워집니다).</li>
        <li>빠른매치가 진행 중일 때(파티가 확정된 직후 1분쯤 포함)는 탈퇴할 수 없습니다. 빠른매치를 먼저 취소하거나 잠시 뒤 다시 시도해 주세요. 방에 들어가 있으면 방에서 나간 뒤 탈퇴합니다.</li>
        <li>그 밖의 요청(보관 중인 정보 전체의 열람, 특정 정보의 삭제, 처리 정지 등)은 <Mail />로 보내 주세요. 본인인지 확인한 뒤 지체 없이 처리하고 결과를 알려 드립니다. 법정대리인이나 위임을 받은 사람도 요청할 수 있습니다.</li>
        <li>소셜 로그인 제공자 쪽에서 서비스와의 연결을 끊어도 서비스에 저장된 정보는 지워지지 않습니다. 정보를 지우려면 회원 탈퇴를 하거나 위 이메일로 요청해 주세요.</li>
        <li>법령에 따라 요구가 제한되는 경우에는 그 이유를 알려 드립니다.</li>
      </ul>
    </LegalSection>

    <LegalSection id="ko-14" title="14. 만 14세 미만 아동">
      <p>서비스는 만 14세 미만 아동의 가입을 받지 않습니다. 가입 화면에서 만 14세 이상인지 확인하며, 만 14세 미만이 가입한 사실을 알게 되면 그 계정의 이용을 멈추고 개인정보를 삭제할 수 있습니다.</p>
    </LegalSection>

    <LegalSection id="ko-15" title="15. 안전성 확보 조치">
      <ul>
        <li>비밀번호를 받지 않습니다. 로그인은 소셜 로그인으로만 합니다.</li>
        <li>로그인 토큰은 서명해 위조를 막고(RS256) 15분 뒤 만료됩니다. 로그인 쿠키는 자바스크립트가 읽을 수 없게(HttpOnly) 하고, 다른 사이트에서 시작된 요청에 함부로 실리지 않도록 SameSite 속성을 겁니다. 상태를 바꾸는 요청은 출처(Origin)를 검사합니다.</li>
        <li>서비스 운영 환경의 통신은 HTTPS로 암호화합니다.</li>
        <li>필요한 최소한의 정보만 받고, 쓰지 않는 정보(이메일 · 전화번호 등)는 처음부터 요청하지 않습니다.</li>
        <li>개인정보에 접근할 수 있는 사람은 운영자로 한정합니다. <Ph value="[배포 때 확정 — 접근 권한 관리 · 접속 기록 보관 등 관리적 · 물리적 조치]" /></li>
      </ul>
    </LegalSection>

    <LegalSection id="ko-16" title="16. 개인정보 보호책임자">
      <ul>
        <li>성명 — <Ph value={LEGAL.officerName} /></li>
        <li>소속 — <Ph value={LEGAL.teamName} /></li>
        <li>연락처 — <Mail /></li>
      </ul>
      <p>개인정보 처리와 관련한 문의, 불만 처리, 피해 구제는 위 연락처로 해 주세요. 지체 없이 답변하고 처리하겠습니다.</p>
    </LegalSection>

    <LegalSection id="ko-17" title="17. 권익침해 구제방법">
      <p>개인정보 침해에 대한 피해 구제나 상담이 필요하면 아래 기관에 문의할 수 있습니다.</p>
      <ul>
        <li>개인정보분쟁조정위원회 — (국번 없이) 1833-6972 · <a href="https://www.kopico.go.kr" {...ext}>www.kopico.go.kr</a></li>
        <li>개인정보침해신고센터(한국인터넷진흥원) — (국번 없이) 118 · <a href="https://privacy.kisa.or.kr" {...ext}>privacy.kisa.or.kr</a></li>
        <li>대검찰청 — (국번 없이) 1301 · <a href="https://www.spo.go.kr" {...ext}>www.spo.go.kr</a></li>
        <li>경찰청 — (국번 없이) 182 · <a href="https://ecrm.police.go.kr" {...ext}>ecrm.police.go.kr</a></li>
      </ul>
    </LegalSection>

    <LegalSection id="ko-18" title="18. 이 방침의 변경">
      <p>이 방침은 <Ph value={LEGAL.effectiveDate} />부터 적용됩니다. 내용을 바꾸면 시행일과 바뀐 내용을 이 페이지에 게시하고, 이용자의 권리에 중요한 영향을 주는 변경은 시행 전에 서비스 화면으로 알립니다.</p>
      <ul><li><Ph value={LEGAL.effectiveDate} /> — 제정</li></ul>
    </LegalSection>
  </>;
}

const EN_TOC = [
  { id: 'en-1', title: 'Purposes of processing' },
  { id: 'en-2', title: 'Personal information we process' },
  { id: 'en-3', title: 'Legal basis' },
  { id: 'en-4', title: 'Retention' },
  { id: 'en-5', title: 'Deletion' },
  { id: 'en-6', title: 'External services' },
  { id: 'en-7', title: 'Processors (outsourcing)' },
  { id: 'en-8', title: 'International transfers' },
  { id: 'en-9', title: 'What other users can see' },
  { id: 'en-10', title: 'Voice, chat and connection data' },
  { id: 'en-11', title: 'Automated grouping in Quick Match' },
  { id: 'en-12', title: 'Cookies and browser storage' },
  { id: 'en-13', title: 'Your rights' },
  { id: 'en-14', title: 'Children under 14' },
  { id: 'en-15', title: 'Security measures' },
  { id: 'en-16', title: 'Privacy officer' },
  { id: 'en-17', title: 'Remedies' },
  { id: 'en-18', title: 'Changes to this policy' },
];

function PrivacyEn() {
  return <>
    <h1>Privacy Policy</h1>
    <p className="legal-meta">Effective <Ph value={LEGAL.effectiveDate} /> · Operator <Ph value={LEGAL.teamName} /> · <Link to="/privacy" lang="ko">한국어</Link></p>
    <p className="legal-note">This English version is provided for convenience, including for platform reviewers. If it differs from the Korean version, the Korean version prevails.</p>
    <p className="legal-lead">
      <Ph value={LEGAL.teamName} /> (“we”) operates QueueMate (the “Service”) and processes users’ personal information in accordance with the Personal Information Protection Act of Korea and other applicable laws.
      This policy explains what personal information the Service processes, why and for how long, and how you can exercise your rights.
    </p>

    <section className="legal-summary" aria-labelledby="en-summary">
      <h2 id="en-summary">At a glance</h2>
      <ul>
        <li>We collect your sign-in provider account ID and your QueueMate nickname — and game accounts and stats only if you link them</li>
        <li>We do not collect your email address, phone number, real name, date of birth, password or payment information</li>
        <li>No advertising or analytics cookies, no third-party trackers</li>
        <li>We keep your data until you delete your account, and delete it without delay when you do</li>
        <li>Party voice and text chat connect browser-to-browser and are not stored on our servers</li>
        <li>Children under 14 cannot sign up</li>
      </ul>
    </section>

    <LegalToc items={EN_TOC} label="Contents" />

    <LegalSection id="en-1" title="1. Purposes of processing">
      <p>We process personal information only for the following purposes. If a purpose changes, we take the steps required by law.</p>
      <ul>
        <li><strong>Sign-up and sign-in</strong> — identifying members through social sign-in and keeping them signed in.</li>
        <li><strong>Providing the Service</strong> — Quick Match (automatic grouping of players whose conditions fit), the Party Board (creating, joining and confirming rooms), brokering the voice and chat connection in party rooms, linking game accounts and showing tiers and stats, and friends, blocking and recent players.</li>
        <li><strong>Keeping the Service safe</strong> — applying blocks, receiving and handling reports, and preventing abuse.</li>
        <li><strong>Answering inquiries</strong> and handling requests to exercise your rights.</li>
      </ul>
    </LegalSection>

    <LegalSection id="en-2" title="2. Personal information we process">
      <LegalTable caption="Personal information" head={['Category', 'Items', 'When and how']} rows={[
        ['Sign-up and sign-in (required)', 'Your sign-in provider (Kakao, Discord or Google), the account ID issued by that provider, your QueueMate nickname and your QueueMate member number', 'When you sign up by choosing a nickname after social sign-in'],
        ['Linked game accounts (optional)', <ul>
          <li>League of Legends — Riot ID (game name#tag), Riot account identifier (PUUID), ranked tiers (Solo and Flex), a stats summary (wins/losses and average kills, deaths and assists over the last 10 games, top 3 champions by mastery, season wins/losses)</li>
          <li>PUBG: BATTLEGROUNDS — nickname, platform (shard), PUBG account ID, ranked tier, a ranked stats summary (games, average damage, K/D, win rate, etc.)</li>
          <li>VALORANT — nickname and the tier you enter yourself</li>
        </ul>, 'When you link a game account on your profile. League of Legends and PUBG tiers and stats are fetched from the game publishers’ APIs when you link the account and again when you sign in (if more than one hour has passed since the last fetch)'],
        ['Posts and parties (when used)', 'Recruitment posts (title, description, game, mode, positions, voice requirement), who is in a room and the position they picked, members of confirmed parties and the times they were confirmed and closed', 'When you post, join a room or confirm a party'],
        ['Quick Match (when used)', 'Match conditions (game, mode, position or role, tier, voice, play purpose), the time you started waiting, and whether you accepted or declined a proposal', 'When you start Quick Match'],
        ['Social features (when used)', 'Friends and friend requests, your block list, recent players, and reports (reason, details up to 1,000 characters, the reported user, and the related post number)', 'When you use each feature'],
        ['Staying signed in (automatic)', 'A sign-in refresh token (a random value linked to your member number)', 'When you sign in'],
        ['Service logs (automatic)', <>Server logs (request handling records including your member number). The Service’s own software does not record IP addresses. Access logs of the hosting environment: <Ph value="[To be fixed at deployment — whether cloud/CDN access logs (IP addresses, etc.) are kept, and for how long]" /></>, 'When you use the Service'],
      ]} />
      <ul>
        <li>We do not collect your email address, phone number, real name, date of birth, gender, password or payment information, and we do not store your sign-in provider’s access tokens.</li>
        <li>The nickname or name your provider gives us is used only to suggest a nickname on the sign-up screen and is not stored separately. If you keep it as your nickname, it is stored as your QueueMate nickname.</li>
        <li>If you do not finish signing up after social sign-in, nothing is stored on our servers. The pending sign-up information is kept only in a signed cookie that is valid for 10 minutes.</li>
      </ul>
      <h3>What each sign-in provider gives us</h3>
      <LegalTable caption="Social sign-in" head={['Provider', 'Scopes requested', 'What we store']} rows={[
        ['Kakao', <>Nickname (<code>profile_nickname</code>)</>, 'Kakao account ID'],
        ['Discord', <><code>identify</code></>, 'Discord user ID'],
        ['Google', <><code>openid</code>, <code>profile</code></>, <>Your Google account’s unique identifier (<code>sub</code>)</>],
      ]} />
      <p>
        <strong>Google user data.</strong> When you sign in with Google, we receive your Google account’s unique identifier (<code>sub</code>) and your name (<code>name</code>).
        We store <code>sub</code> only to identify your QueueMate account, and we use <code>name</code> only to suggest a nickname on the sign-up screen; it is not stored.
        We do not use Google user data for advertising, we do not sell it, and we do not transfer it to other companies. We delete it when you delete your account or ask us to.
      </p>
    </LegalSection>

    <LegalSection id="en-3" title="3. Legal basis">
      <p>
        Information needed for sign-up, sign-in and providing the Service is processed to provide the Service under the Terms of Service (entering into and performing a contract).
        Information for optional features — linked game accounts, posts, Quick Match, friends and reports — is processed to provide that feature when you choose to use it.
        We do not process sensitive information or unique identification numbers.
      </p>
    </LegalSection>

    <LegalSection id="en-4" title="4. Retention">
      <ul>
        <li><strong>Your account and the information created while you use the Service</strong> (game accounts and stats, posts, party records, friends, blocks, reports and recent players) are kept until you delete your account and are deleted without delay when you do. However, <strong>a recruitment post confirmed together with other users</strong> is part of the other party members’ records, so when you delete your account it stays with its author removed — its title, description and party member records remain (your own party membership is removed).</li>
        <li>Recruitment posts stay on the board after recruiting ends (expired or confirmed). “Deleting” a post marks it as ended; the post itself is deleted when you delete your account (a post confirmed together with other users only loses its author, as above). To have a post removed before that, email <Mail />.</li>
      </ul>
      <LegalTable caption="Information kept only briefly" head={['Information', 'Kept for']} rows={[
        ['Pending sign-up information (signed cookie)', '10 minutes'],
        ['Sign-in refresh token', '7 days (a new one each time it is used) · deleted immediately when you sign out'],
        ['Who is in a room and the positions picked', 'Until the room ends. A room ends automatically at most 10 minutes after its presence checks stop'],
        ['Kick and leave records (limit re-entering the same room and auto-join)', '10 minutes'],
        ['Quick Match requests and proposals', 'While matching is in progress; removed when you cancel, and deleted automatically within at most 10 minutes after the party is confirmed'],
        ['Declined proposal records (so the same players are not grouped again right away)', '10 minutes'],
        ['Server logs', <Ph value="[To be fixed at deployment]" />],
      ]} />
      <p>If another law requires us to keep certain information, we keep it separately for the period that law requires. <Ph value="[To be fixed at deployment — the law and items; delete this sentence if none]" /></p>
    </LegalSection>

    <LegalSection id="en-5" title="5. Deletion">
      <ul>
        <li>We delete personal information without delay when you delete your account or when its retention period ends.</li>
        <li>Database records are deleted so that they cannot be restored; data in our in-memory store (Redis) is deleted or expires automatically at the end of its retention period.</li>
        <li>Backups — <Ph value="[To be fixed at deployment — whether backups are kept, and for how long]" /></li>
        <li>We do not process personal information on paper.</li>
      </ul>
    </LegalSection>

    <LegalSection id="en-6" title="6. External services">
      <p>To provide its features, the Service exchanges information with the following external services. How each of them processes personal information is governed by its own policy.</p>
      <LegalTable caption="External services" head={['Service', 'Information exchanged', 'Purpose', 'Sent by']} rows={[
        ['Kakao, Discord, Google', 'You sign in directly on the provider’s sign-in page; we receive your account ID and nickname (name)', 'Social sign-in', 'Our server'],
        ['Riot Games (Riot Games API)', 'Game name#tag, then the Riot account identifier (PUUID)', 'Verifying a League of Legends account and fetching tiers and stats', 'Our server'],
        ['KRAFTON (PUBG API)', 'Nickname, platform, PUBG account ID', 'Verifying a PUBG account and fetching tiers and stats', 'Our server'],
        ['Google (STUN server stun.l.google.com)', 'Your browser contacts it to learn its own public IP address. No account information is sent', 'Voice and chat connection', 'Your browser'],
        ['Riot Games (Data Dragon, ddragon.leagueoflegends.com)', 'Champion images are downloaded. No account information is sent, but connection information (such as your IP address) may be logged by that server', 'Showing champion images', 'Your browser'],
      ]} />
      <p>We do not sell users’ personal information, and we do not provide it to other companies except through the integrations above or as required by law.</p>
    </LegalSection>

    <LegalSection id="en-7" title="7. Processors (outsourcing)">
      <p>We entrust the following personal information processing to processors to operate the Service.</p>
      <p><Ph value="[To be fixed at deployment — processors such as cloud hosting (AWS Seoul region planned) and the tasks entrusted]" /></p>
    </LegalSection>

    <LegalSection id="en-8" title="8. International transfers">
      <p><Ph value="[To be fixed at deployment — cloud region (AWS Seoul region planned) and whether the external services in section 6 involve an international transfer]" /></p>
      <p className="legal-note">If personal information is transferred abroad, we will list here the items transferred, the destination country, when and how it is transferred, the recipient, the recipient’s purposes and retention period, and how to refuse the transfer and what happens if you do.</p>
    </LegalSection>

    <LegalSection id="en-9" title="9. What other users can see">
      <ul>
        <li><strong>Party Board</strong> — signed-in users can see a post and, for people in its room, their QueueMate nickname, member number, game nickname (e.g. Riot ID), tier, stats summary and picked position. Confirmed posts keep showing the members at the moment of confirmation (members who delete their account are removed, and if the author deletes their account the post no longer shows an author — see section 4). A room is hidden from users who have a block with anyone in it, in either direction.</li>
        <li><strong>Quick Match</strong> — once a proposal arrives or a party is confirmed, members of that party see each other’s nickname, game profile and picked position.</li>
        <li><strong>Inside a room</strong> — people in the same room see each other’s nickname, game profile and voice connection status.</li>
        <li><strong>Friends and recent players</strong> — friends, people you exchange friend requests with, and people you have partied with can see your nickname and member number. The member number is used to send friend requests.</li>
        <li><strong>Reports</strong> — the reported user is not told that they were reported or what the report says.</li>
        <li>The Service has no feature for searching or browsing people.</li>
      </ul>
    </LegalSection>

    <LegalSection id="en-10" title="10. Voice, chat and connection data">
      <ul>
        <li>Voice and text chat in a party room connect directly between users’ browsers over WebRTC. Our servers neither relay nor store their content.</li>
        <li>To set up the connection, connection information created by your browser (including IP addresses and ports) is passed through our server to the browsers of people in the same room. Our server only passes it on and does not store it. As a result, people in the same room can technically learn your IP address.</li>
        <li>Your voice is sent only while your microphone is on.</li>
      </ul>
    </LegalSection>

    <LegalSection id="en-11" title="11. Automated grouping in Quick Match">
      <p>
        Quick Match automatically groups players based on the conditions they choose. Grouping uses game, mode, position (role), tier, voice, play purpose, blocks and recent declines.
        When a proposal arrives you can accept or decline it, and the party is confirmed only when everyone accepts. To ask for an explanation of how grouping works, email <Mail />.
      </p>
    </LegalSection>

    <LegalSection id="en-12" title="12. Cookies and browser storage">
      <LegalTable caption="Cookies" head={['Name', 'Purpose', 'Kept for']} rows={[
        [<code>qm_access</code>, 'Checking that you are signed in', '15 minutes'],
        [<code>qm_refresh</code>, 'Keeping you signed in (re-issuing)', '7 days'],
        [<code>qm_social_signup</code>, 'Pending sign-up information until you finish signing up after social sign-in', '10 minutes'],
        [<code>qm_oauth_state</code>, 'Preventing forged social sign-in requests', '10 minutes'],
      ]} />
      <p>
        All of them are strictly necessary for the Service and are HttpOnly cookies that scripts cannot read. We use no advertising or analytics cookies and no third-party trackers.
        You can block cookies in your browser settings, but then you will not be able to sign in.
      </p>
      <p>For convenience, the following values are kept in your browser’s storage (localStorage and sessionStorage). They stay in your browser, and you can remove them by clearing the site’s data in your browser. When you delete your account, the values for that account left in the browser you used (those below whose names end in <code>…</code>) are removed.</p>
      <ul>
        <li>The social sign-in button you pressed last (<code>qm.lastProvider</code>)</li>
        <li>Whether you have passed the game account setup screen (<code>qm.onboarding.done:…</code>)</li>
        <li>Your last 3 Quick Match conditions and the last conditions you chose for each game (<code>qm.recentConditions</code>, <code>queuemate:introduction:…</code>)</li>
        <li>The IDs and conditions of your ongoing Quick Match request and party, and Quick Match parties you left (10 minutes) (<code>qm.activeMatch.…</code>, <code>qm.activeParty.…</code>, <code>qm.activePartyInfo.…</code>, <code>qm.leftParties.…</code>)</li>
        <li>A draft of the reservation form (<code>queuemate:reservation-draft:…</code>)</li>
        <li>One-time notices and proposals already opened — in that tab only (<code>qm.settings.notice</code>, <code>qm.proposalShown.…</code>)</li>
        <li>Message and notification records left by an earlier version (<code>qm:direct-messages:…</code>, <code>qm:notifications:…</code>) may remain. The current Service does not read them.</li>
      </ul>
    </LegalSection>

    <LegalSection id="en-13" title="13. Your rights">
      <ul>
        <li>You may at any time ask to access, correct or delete your personal information, or to stop its processing.</li>
        <li><strong>What you can do directly in the Service</strong> — change your nickname, unlink game accounts, link or unlink social accounts (the last one cannot be unlinked), remove friends and unblock users, delete posts, sign out, and <strong>delete your account (Settings &gt; Delete account)</strong>. When you delete your account, all information in section 4 is deleted without delay, except that a recruitment post confirmed together with other users is part of the other party members’ records and stays with its author removed — its title, description and party member records remain (your own party membership is removed).</li>
        <li>You cannot delete your account while a Quick Match is in progress (including about a minute right after a party is confirmed). Cancel the Quick Match first, or try again shortly. If you are in a room, you leave it before your account is deleted.</li>
        <li>For any other request (access to all information we hold, deletion of specific information, stopping processing, etc.), email <Mail />. We will verify your identity, handle the request without delay and tell you the result. A legal representative or a person you authorize may also make a request.</li>
        <li>Disconnecting QueueMate on your sign-in provider’s side does not delete the information stored by the Service. To delete it, delete your account or email us at the address above.</li>
        <li>If the law limits a request, we will tell you why.</li>
      </ul>
    </LegalSection>

    <LegalSection id="en-14" title="14. Children under 14">
      <p>The Service does not accept sign-ups from children under 14. The sign-up screen asks you to confirm that you are 14 or older, and if we learn that a child under 14 has signed up, we may suspend the account and delete its personal information.</p>
    </LegalSection>

    <LegalSection id="en-15" title="15. Security measures">
      <ul>
        <li>We do not collect passwords; sign-in is only through social sign-in.</li>
        <li>Sign-in tokens are signed (RS256) to prevent forgery and expire after 15 minutes. Sign-in cookies are HttpOnly so scripts cannot read them, and carry the SameSite attribute so they are not freely attached to requests started from other sites. Requests that change state are checked for their origin (Origin header).</li>
        <li>Traffic in the Service’s production environment is encrypted with HTTPS.</li>
        <li>We collect only the minimum information needed and never request information we do not use (such as email addresses or phone numbers).</li>
        <li>Access to personal information is limited to the operator. <Ph value="[To be fixed at deployment — administrative and physical measures such as access control and access log retention]" /></li>
      </ul>
    </LegalSection>

    <LegalSection id="en-16" title="16. Privacy officer">
      <ul>
        <li>Name — <Ph value={LEGAL.officerName} /></li>
        <li>Organization — <Ph value={LEGAL.teamName} /></li>
        <li>Contact — <Mail /></li>
      </ul>
      <p>Please send inquiries, complaints and requests for remedies about personal information processing to the contact above. We will respond and act without delay.</p>
    </LegalSection>

    <LegalSection id="en-17" title="17. Remedies">
      <p>If you need a remedy or advice about an infringement of your personal information, you can contact the following Korean agencies.</p>
      <ul>
        <li>Personal Information Dispute Mediation Committee — 1833-6972 · <a href="https://www.kopico.go.kr" {...ext}>www.kopico.go.kr</a></li>
        <li>Personal Information Infringement Report Center (KISA) — 118 · <a href="https://privacy.kisa.or.kr" {...ext}>privacy.kisa.or.kr</a></li>
        <li>Supreme Prosecutors’ Office — 1301 · <a href="https://www.spo.go.kr" {...ext}>www.spo.go.kr</a></li>
        <li>Korean National Police Agency (cyber crime) — 182 · <a href="https://ecrm.police.go.kr" {...ext}>ecrm.police.go.kr</a></li>
      </ul>
    </LegalSection>

    <LegalSection id="en-18" title="18. Changes to this policy">
      <p>This policy applies from <Ph value={LEGAL.effectiveDate} />. If we change it, we will post the effective date and the changes on this page, and notify you in the Service before the change takes effect if it significantly affects your rights.</p>
      <ul><li><Ph value={LEGAL.effectiveDate} /> — first version</li></ul>
    </LegalSection>
  </>;
}
