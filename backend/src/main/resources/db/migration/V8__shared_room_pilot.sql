CREATE TABLE rooms (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    settings JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CONFIRMED')),
    created_at BIGINT NOT NULL
);
CREATE TABLE room_members (
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile JSONB NOT NULL,
    joined_at BIGINT NOT NULL,
    PRIMARY KEY(room_id,user_id),
    UNIQUE(user_id)
);
CREATE TABLE room_messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    author_id UUID REFERENCES users(id) ON DELETE SET NULL,
    client_message_id UUID NOT NULL,
    body TEXT NOT NULL CHECK (length(body) BETWEEN 1 AND 2000),
    created_at BIGINT NOT NULL,
    UNIQUE(room_id, author_id, client_message_id)
);
CREATE INDEX room_messages_recent ON room_messages(room_id,created_at,id);
