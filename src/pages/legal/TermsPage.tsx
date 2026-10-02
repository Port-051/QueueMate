import { Link } from 'react-router-dom';
import { LEGAL, RIOT_NOTICE_EN, RIOT_NOTICE_KO } from '../../domain/legal';
import { LegalLayout, LegalSection, LegalToc, Ph, useLegalLang } from './LegalLayout';

/**
 * `/terms` — 이용약관(2026-10-02 소유자 결정 · 한국어 본문 + `?lang=en` 영어판).
 * 목차는 흔히 쓰는 서비스 약관의 꼴이다(법정 목록이 아니다). 이용 제한 조항(제11조)은 일반적인 문구이고, **지금 앱에는 운영자가 제재하는 기능이 없다**(신고는 접수만 — platform P-9).
 * 공지 기간(7일 · 불리한 변경 30일) · 서비스 종료 공지(30일)는 흔한 값을 적은 것이라 소유자 · 법률 검토 항목이다.
 */
export function TermsPage() {
  const lang = useLegalLang();
  return lang === 'en'
    ? <LegalLayout lang="en" title="Terms of Service"><TermsEn /></LegalLayout>
    : <LegalLayout lang="ko" title="이용약관"><TermsKo /></LegalLayout>;
}

const Mail = () => <Ph value={LEGAL.contactEmail} />;

const KO_TOC = [
  '목적', '정의', '약관의 게시와 변경', '회원 가입', '계정의 관리', '서비스의 내용', '서비스의 변경과 중단', '게임 계정과 전적 정보',
  '회원의 의무와 금지행위', '게시물', '차단 · 신고와 이용 제한', '회원 탈퇴', '외부 서비스', '책임의 제한', '손해배상', '분쟁 해결과 준거법',
].map((title, index) => ({ id: `tko-${index + 1}`, title: `제${index + 1}조 ${title}` }));

function TermsKo() {
  return <>
    <h1>이용약관</h1>
    <p className="legal-meta">시행일 <Ph value={LEGAL.effectiveDate} /> · 운영자 <Ph value={LEGAL.teamName} /> · <Link to="/terms?lang=en" lang="en">English</Link></p>

    <LegalToc items={KO_TOC} label="목차" />

    <LegalSection id="tko-1" title="제1조 (목적)">
      <p>이 약관은 <Ph value={LEGAL.teamName} />(이하 “운영자”)이 제공하는 QueueMate(이하 “서비스”)의 이용과 관련해 운영자와 회원의 권리 · 의무와 책임, 그 밖에 필요한 사항을 정합니다.</p>
    </LegalSection>

    <LegalSection id="tko-2" title="제2조 (정의)">
      <ol className="legal-list">
        <li>“서비스”란 운영자가 제공하는 게임 팀원 찾기 웹 서비스 QueueMate와 그에 딸린 기능을 말합니다.</li>
        <li>“회원”이란 이 약관에 따라 소셜 계정으로 가입해 서비스를 이용하는 사람을 말합니다.</li>
        <li>“빠른매치”란 회원이 고른 조건(게임 · 모드 · 포지션 · 티어 · 음성 · 플레이 목적 등)에 맞는 회원들을 시스템이 자동으로 묶어 파티를 제안하는 기능을 말합니다.</li>
        <li>“파티 모집 게시판”이란 회원이 모집 글을 올려 방을 만들고 다른 회원이 그 방에 참가하는 기능을 말합니다.</li>
        <li>“방”이란 모집 글이나 빠른매치로 만들어진 파티의 공간으로, 참가자끼리 음성 · 채팅으로 이야기할 수 있는 곳을 말합니다.</li>
        <li>“게시물”이란 회원이 서비스에 올린 모집 글과 그 밖의 정보를 말합니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-3" title="제3조 (약관의 게시와 변경)">
      <ol className="legal-list">
        <li>운영자는 이 약관을 서비스 첫 화면에서 연결되는 페이지에 게시합니다.</li>
        <li>운영자는 관련 법령을 어기지 않는 범위에서 이 약관을 바꿀 수 있습니다.</li>
        <li>약관을 바꾸면 시행일, 바뀐 내용과 그 이유를 시행일 7일 전부터 서비스에 공지합니다. 회원에게 불리한 변경은 시행일 30일 전부터 공지하고 서비스 화면 등으로 따로 알립니다.</li>
        <li>회원은 바뀐 약관에 동의하지 않으면 회원 탈퇴로 이용계약을 끝낼 수 있습니다. 운영자가 공지하면서 “시행일까지 거부 의사를 밝히지 않으면 동의한 것으로 본다”는 뜻을 함께 알렸는데도 회원이 시행일까지 거부 의사를 밝히지 않으면, 바뀐 약관에 동의한 것으로 봅니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-4" title="제4조 (회원 가입)">
      <ol className="legal-list">
        <li>가입은 카카오 · 디스코드 · 구글 계정 가운데 하나로 로그인한 뒤 닉네임을 정하고, 이 약관에 동의하고 <Link to="/privacy">개인정보 처리방침</Link>을 확인하면 완료됩니다.</li>
        <li>만 14세 미만은 가입할 수 없습니다. 가입할 때 만 14세 이상인지 확인합니다.</li>
        <li>운영자는 다른 사람의 소셜 계정을 쓴 경우, 만 14세 미만인 경우, 이 약관에 따라 이용이 제한된 회원이 다시 가입하려는 경우, 그 밖에 법령이나 이 약관을 어긴 경우에는 가입을 받지 않거나 나중에 이용계약을 해지할 수 있습니다.</li>
        <li>닉네임은 다른 회원과 겹칠 수 없고, 다른 사람을 사칭하거나 욕설 · 혐오 표현이 들어간 닉네임은 쓸 수 없습니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-5" title="제5조 (계정의 관리)">
      <ol className="legal-list">
        <li>회원은 자기 소셜 계정을 스스로 관리해야 합니다. 소셜 계정이 도용되어 생긴 손해는 운영자에게 고의나 과실이 없는 한 운영자가 책임지지 않습니다.</li>
        <li>회원은 내 정보에서 다른 소셜 계정을 더 연결하거나 연결을 끊을 수 있습니다. 다만 마지막으로 남은 하나는 끊을 수 없습니다.</li>
        <li>회원은 계정을 다른 사람에게 넘기거나 빌려줄 수 없습니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-6" title="제6조 (서비스의 내용)">
      <ol className="legal-list">
        <li>운영자는 빠른매치, 파티 모집 게시판과 방, 방 안의 음성 · 채팅(브라우저끼리 직접 연결), 게임 계정 연결과 티어 · 전적 표시, 친구 · 차단 · 신고 · 최근 함께한 사람 기능을 제공합니다.</li>
        <li>지원하는 게임은 리그 오브 레전드, 발로란트, 배틀그라운드입니다.</li>
        <li>서비스는 무료입니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-7" title="제7조 (서비스의 변경과 중단)">
      <ol className="legal-list">
        <li>운영자는 서비스의 내용을 바꾸거나 일부를 중단할 수 있으며, 회원에게 중요한 변경은 미리 공지합니다.</li>
        <li>점검, 장애, 통신 · 클라우드 서비스의 문제, 게임사 API나 소셜 로그인 제공자의 중단 · 정책 변경 등으로 서비스가 잠시 멈추거나 일부 기능(예: 전적 표시)을 쓸 수 없을 수 있습니다.</li>
        <li>운영자가 서비스를 끝내려면 30일 전에 공지하고, 끝낼 때 회원의 개인정보를 개인정보 처리방침에 따라 삭제합니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-8" title="제8조 (게임 계정과 전적 정보)">
      <ol className="legal-list">
        <li>회원은 본인의 게임 계정만 연결해야 합니다. 다른 사람의 게임 계정을 연결해서는 안 됩니다.</li>
        <li>리그 오브 레전드와 배틀그라운드의 티어 · 전적은 각 게임사가 제공하는 API에서 받아 보여 주며, 운영자는 그 정확성과 최신성을 보장하지 않습니다. 발로란트 티어는 회원이 직접 입력한 값입니다.</li>
        <li>연결한 게임 계정의 닉네임 · 티어 · 전적은 파티 모집 게시판과 파티에서 다른 회원에게 보입니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-9" title="제9조 (회원의 의무와 금지행위)">
      <p>회원은 다음 행위를 해서는 안 됩니다.</p>
      <ol className="legal-list">
        <li>다른 회원에 대한 욕설 · 비하 · 혐오 표현 · 괴롭힘 · 성희롱</li>
        <li>파티를 확정한 뒤 이유 없이 나타나지 않거나 고의로 게임을 포기하는 행위</li>
        <li>핵 · 대리 게임 · 계정 거래 등 게임사 정책을 어기는 행위를 권하거나 함께하는 행위</li>
        <li>도배, 광고 · 홍보, 다른 서비스로 끌어가는 행위</li>
        <li>다른 사람을 사칭하거나 다른 사람의 소셜 · 게임 계정을 쓰는 행위</li>
        <li>다른 회원의 개인정보(음성 · 채팅 내용 포함)를 동의 없이 녹음 · 수집 · 공개하는 행위</li>
        <li>서비스의 정상적인 운영을 방해하거나, 자동화된 수단으로 서비스에 접속하거나 정보를 모으는 행위</li>
        <li>법령이나 공공질서, 미풍양속에 어긋나는 행위</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-10" title="제10조 (게시물)">
      <ol className="legal-list">
        <li>게시물에 대한 권리와 책임은 작성한 회원에게 있습니다.</li>
        <li>운영자는 서비스를 운영하고 화면에 보여 주는 데 필요한 범위에서 게시물을 사용할 수 있습니다.</li>
        <li>게시물이 법령을 어기거나, 다른 사람의 권리를 침해하거나, 제9조의 금지행위에 해당하면 운영자는 그 게시물을 보이지 않게 하거나 삭제할 수 있습니다.</li>
        <li>자기 권리를 침해당했다고 생각하는 사람은 <Mail />로 게시물의 삭제 등을 요청할 수 있습니다.</li>
        <li>회원이 모집 글을 지우면 모집이 끝난 상태가 되고, 글은 회원 탈퇴 때 삭제됩니다. 다만 다른 회원과 함께 확정한 파티 모집 글은 제12조 제2항에 따라 작성자 표시만 지워진 채 남습니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-11" title="제11조 (차단 · 신고와 이용 제한)">
      <ol className="legal-list">
        <li>회원은 다른 회원을 차단할 수 있습니다. 어느 한쪽이라도 차단하면 두 회원은 빠른매치에서 같은 파티로 묶이지 않고, 상대가 있는 모집 방은 보이지 않습니다.</li>
        <li>회원은 제9조를 어긴 회원을 신고할 수 있습니다. 운영자는 접수된 신고를 확인하며, 신고한 사실은 상대에게 알리지 않습니다.</li>
        <li>운영자는 회원이 이 약관을 어기면 그 정도에 따라 경고, 일정 기간 이용 정지, 이용계약 해지 등으로 이용을 제한할 수 있습니다. 이용을 제한하면 그 사유를 알리며, 회원은 <Mail />로 이의를 제기할 수 있습니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-12" title="제12조 (회원 탈퇴)">
      <ol className="legal-list">
        <li>회원은 언제든지 설정 &gt; 회원 탈퇴에서 탈퇴할 수 있습니다.</li>
        <li>탈퇴하면 회원 정보와 게임 계정 · 전적, 모집 글, 파티 기록, 친구 · 차단 · 신고 기록이 지체 없이 삭제되며 되돌릴 수 없습니다. 다만 다른 회원과 함께 확정한 파티 모집 글은 다른 파티원의 기록이므로, 작성자 표시를 지운 채 제목 · 설명 · 파티원 기록이 남습니다(탈퇴한 회원의 파티원 표시는 지워집니다).</li>
        <li>빠른매치가 진행 중일 때(파티가 확정된 직후 1분쯤 포함)는 탈퇴할 수 없습니다. 방에 들어가 있으면 방에서 나간 뒤 탈퇴합니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-13" title="제13조 (외부 서비스)">
      <ol className="legal-list">
        <li>서비스는 카카오 · 디스코드 · 구글 로그인과 Riot Games · KRAFTON이 제공하는 게임 데이터를 이용합니다. 외부 서비스를 이용할 때에는 그 회사의 약관과 정책이 함께 적용됩니다.</li>
        <li>각 게임의 이름과 상표는 그 권리자의 것입니다.</li>
      </ol>
      <p lang="en">{RIOT_NOTICE_EN}</p>
      <p>{RIOT_NOTICE_KO}</p>
    </LegalSection>

    <LegalSection id="tko-14" title="제14조 (책임의 제한)">
      <ol className="legal-list">
        <li>운영자는 천재지변, 회원의 귀책사유, 외부 서비스(게임사 · 소셜 로그인 제공자 · 통신사 등)의 장애처럼 운영자에게 고의나 과실이 없는 사유로 생긴 손해에 책임지지 않습니다.</li>
        <li>운영자는 회원끼리 또는 회원과 제3자 사이에 서비스를 매개로 생긴 분쟁(게임 안에서의 일 포함)에 개입할 의무가 없으며, 운영자에게 고의나 과실이 없는 한 그로 인한 손해에 책임지지 않습니다.</li>
        <li>운영자는 게임사가 제공하는 티어 · 전적 정보의 정확성에 책임지지 않습니다.</li>
      </ol>
    </LegalSection>

    <LegalSection id="tko-15" title="제15조 (손해배상)">
      <p>운영자나 회원이 이 약관을 어겨 상대에게 손해를 끼치면 그 손해를 배상해야 합니다. 다만 고의나 과실이 없으면 그렇지 않습니다.</p>
    </LegalSection>

    <LegalSection id="tko-16" title="제16조 (분쟁 해결과 준거법)">
      <ol className="legal-list">
        <li>운영자는 회원의 의견이나 불만을 <Mail />로 받아 지체 없이 처리합니다.</li>
        <li>이 약관과 서비스 이용에는 대한민국 법을 적용합니다.</li>
        <li>서비스 이용과 관련한 소송은 민사소송법에 따른 관할 법원에 제기합니다.</li>
      </ol>
    </LegalSection>

    <section className="legal-section" aria-label="부칙">
      <h2>부칙</h2>
      <p>이 약관은 <Ph value={LEGAL.effectiveDate} />부터 시행합니다.</p>
    </section>
  </>;
}

const EN_TOC = [
  'Purpose', 'Definitions', 'Posting and changing these terms', 'Sign-up', 'Account management', 'The Service', 'Changes to and suspension of the Service', 'Game accounts and stats',
  'Member obligations and prohibited conduct', 'Posts', 'Blocking, reports and restrictions', 'Deleting your account', 'External services', 'Limitation of liability', 'Damages', 'Disputes and governing law',
].map((title, index) => ({ id: `ten-${index + 1}`, title: `Article ${index + 1}. ${title}` }));

function TermsEn() {
  return <>
    <h1>Terms of Service</h1>
    <p className="legal-meta">Effective <Ph value={LEGAL.effectiveDate} /> · Operator <Ph value={LEGAL.teamName} /> · <Link to="/terms" lang="ko">한국어</Link></p>
    <p className="legal-note">This English version is provided for convenience, including for platform reviewers. If it differs from the Korean version, the Korean version prevails.</p>

    <LegalToc items={EN_TOC} label="Contents" />

    <LegalSection id="ten-1" title="Article 1 (Purpose)">
      <p>These terms set out the rights, obligations and responsibilities of <Ph value={LEGAL.teamName} /> (the “Operator”) and members, and other necessary matters, regarding the use of QueueMate (the “Service”).</p>
    </LegalSection>

    <LegalSection id="ten-2" title="Article 2 (Definitions)">
      <ol className="legal-list">
        <li>“Service” means QueueMate, the web service for finding game teammates provided by the Operator, and its related features.</li>
        <li>“Member” means a person who has signed up with a social account under these terms and uses the Service.</li>
        <li>“Quick Match” means the feature in which the system automatically groups members whose chosen conditions (game, mode, position, tier, voice, play purpose, etc.) fit, and proposes a party.</li>
        <li>“Party Board” means the feature in which a member posts a recruitment post to open a room and other members join it.</li>
        <li>“Room” means the space of a party created from a post or Quick Match, where participants can talk by voice and text chat.</li>
        <li>“Posts” means recruitment posts and other information members put on the Service.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-3" title="Article 3 (Posting and changing these terms)">
      <ol className="legal-list">
        <li>The Operator posts these terms on a page linked from the Service’s front page.</li>
        <li>The Operator may change these terms to the extent permitted by applicable law.</li>
        <li>When the terms change, the Operator announces the effective date, the changes and the reasons in the Service from 7 days before the effective date. Changes unfavorable to members are announced from 30 days before the effective date and also notified separately, for example on the Service’s screens.</li>
        <li>A member who does not agree to the changed terms may end the agreement by deleting their account. If the Operator announced that members who do not object by the effective date will be deemed to agree, and a member does not object by then, the member is deemed to have agreed to the changed terms.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-4" title="Article 4 (Sign-up)">
      <ol className="legal-list">
        <li>Sign-up is complete when you sign in with a Kakao, Discord or Google account, choose a nickname, agree to these terms and confirm that you have read the <Link to="/privacy?lang=en">Privacy Policy</Link>.</li>
        <li>Children under 14 cannot sign up. You are asked to confirm that you are 14 or older when you sign up.</li>
        <li>The Operator may refuse a sign-up, or later terminate the agreement, if someone else’s social account was used, the person is under 14, a member restricted under these terms tries to sign up again, or the law or these terms are otherwise violated.</li>
        <li>Nicknames must be unique, and nicknames that impersonate others or contain profanity or hate speech may not be used.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-5" title="Article 5 (Account management)">
      <ol className="legal-list">
        <li>Members are responsible for managing their own social accounts. The Operator is not liable for damage caused by misuse of a member’s social account unless the Operator is at fault.</li>
        <li>Members may link more social accounts or unlink them on their profile, but the last remaining one cannot be unlinked.</li>
        <li>Members may not transfer or lend their account to anyone else.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-6" title="Article 6 (The Service)">
      <ol className="legal-list">
        <li>The Operator provides Quick Match, the Party Board and rooms, voice and text chat in rooms (connected directly between browsers), game account linking with tiers and stats, and friends, blocking, reports and recent players.</li>
        <li>Supported games are League of Legends, VALORANT and PUBG: BATTLEGROUNDS.</li>
        <li>The Service is free of charge.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-7" title="Article 7 (Changes to and suspension of the Service)">
      <ol className="legal-list">
        <li>The Operator may change the Service or suspend part of it, and announces changes important to members in advance.</li>
        <li>The Service may stop temporarily, or some features (for example, stats) may be unavailable, due to maintenance, failures, network or cloud problems, or outages or policy changes of game publishers’ APIs or sign-in providers.</li>
        <li>If the Operator ends the Service, it announces this 30 days in advance and deletes members’ personal information under the Privacy Policy when the Service ends.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-8" title="Article 8 (Game accounts and stats)">
      <ol className="legal-list">
        <li>Members may link only their own game accounts, and must not link anyone else’s.</li>
        <li>League of Legends and PUBG tiers and stats are fetched from the APIs provided by each game publisher; the Operator does not guarantee that they are accurate or up to date. VALORANT tiers are entered by members themselves.</li>
        <li>The nickname, tier and stats of a linked game account are visible to other members on the Party Board and in parties.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-9" title="Article 9 (Member obligations and prohibited conduct)">
      <p>Members must not:</p>
      <ol className="legal-list">
        <li>insult, demean, harass or sexually harass other members, or use hate speech against them;</li>
        <li>fail to show up without reason, or deliberately give up a game, after a party is confirmed;</li>
        <li>encourage or take part in conduct that breaks game publishers’ policies, such as cheating, boosting or account trading;</li>
        <li>spam, advertise or promote, or lure members to other services;</li>
        <li>impersonate others or use someone else’s social or game account;</li>
        <li>record, collect or disclose other members’ personal information (including voice and chat content) without consent;</li>
        <li>interfere with the normal operation of the Service, or access it or collect information from it by automated means;</li>
        <li>act against the law, public order or morals.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-10" title="Article 10 (Posts)">
      <ol className="legal-list">
        <li>Rights in and responsibility for a post belong to the member who wrote it.</li>
        <li>The Operator may use posts to the extent needed to operate the Service and display them.</li>
        <li>If a post violates the law, infringes someone’s rights or falls under Article 9, the Operator may hide or delete it.</li>
        <li>Anyone who believes a post infringes their rights may request its removal by emailing <Mail />.</li>
        <li>When a member deletes a recruitment post, it is marked as ended; the post is deleted when the member deletes their account. However, a recruitment post confirmed together with other members stays with only its author removed, under Article 12(2).</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-11" title="Article 11 (Blocking, reports and restrictions)">
      <ol className="legal-list">
        <li>Members may block other members. If either member blocks the other, they are not grouped into the same party in Quick Match, and rooms with the other member in them are hidden.</li>
        <li>Members may report members who violate Article 9. The Operator reviews reports received, and the reported member is not told that they were reported.</li>
        <li>If a member violates these terms, the Operator may restrict their use according to the severity, by a warning, a suspension for a period, termination of the agreement, or similar measures. The Operator notifies the member of the reason, and the member may object by emailing <Mail />.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-12" title="Article 12 (Deleting your account)">
      <ol className="legal-list">
        <li>Members may delete their account at any time in Settings &gt; Delete account.</li>
        <li>When an account is deleted, the member’s information, game accounts and stats, posts, party records, friends, blocks and reports are deleted without delay and cannot be restored. However, a recruitment post confirmed together with other members is part of the other party members’ records, so it stays with its author removed — its title, description and party member records remain (the deleted member’s party membership is removed).</li>
        <li>An account cannot be deleted while a Quick Match is in progress (including about a minute right after a party is confirmed). If the member is in a room, they leave it before the account is deleted.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-13" title="Article 13 (External services)">
      <ol className="legal-list">
        <li>The Service uses Kakao, Discord and Google sign-in and game data provided by Riot Games and KRAFTON. The terms and policies of those companies also apply when you use their services.</li>
        <li>The names and trademarks of each game belong to their respective owners.</li>
      </ol>
      <p>{RIOT_NOTICE_EN}</p>
    </LegalSection>

    <LegalSection id="ten-14" title="Article 14 (Limitation of liability)">
      <ol className="legal-list">
        <li>The Operator is not liable for damage caused by reasons for which it is not at fault, such as natural disasters, causes attributable to members, or failures of external services (game publishers, sign-in providers, network operators, etc.).</li>
        <li>The Operator has no obligation to intervene in disputes arising through the Service between members, or between members and third parties (including what happens in games), and is not liable for resulting damage unless it is at fault.</li>
        <li>The Operator is not responsible for the accuracy of tier and stats information provided by game publishers.</li>
      </ol>
    </LegalSection>

    <LegalSection id="ten-15" title="Article 15 (Damages)">
      <p>If the Operator or a member causes damage to the other by violating these terms, they must compensate for it, unless they are not at fault.</p>
    </LegalSection>

    <LegalSection id="ten-16" title="Article 16 (Disputes and governing law)">
      <ol className="legal-list">
        <li>The Operator receives members’ opinions and complaints at <Mail /> and handles them without delay.</li>
        <li>These terms and the use of the Service are governed by the laws of the Republic of Korea.</li>
        <li>Lawsuits relating to the use of the Service are brought before the court with jurisdiction under the Civil Procedure Act of Korea.</li>
      </ol>
    </LegalSection>

    <section className="legal-section" aria-label="Addendum">
      <h2>Addendum</h2>
      <p>These terms take effect on <Ph value={LEGAL.effectiveDate} />.</p>
    </section>
  </>;
}
