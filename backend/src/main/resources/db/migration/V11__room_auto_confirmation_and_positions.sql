ALTER TABLE recruit_posts ADD COLUMN auto_confirm_at timestamptz;
CREATE INDEX recruit_posts_auto_confirm ON recruit_posts (auto_confirm_at)
    WHERE status = 'RECRUITING';
ALTER TABLE party_members ADD COLUMN position varchar(20);
