export function SimplePage({ title }: { title: string }) {
  return (
    <section className="page">
      <div className="page-head">
        <h1>{title}</h1>
        <p>준비 중인 화면입니다.</p>
      </div>
    </section>
  );
}
