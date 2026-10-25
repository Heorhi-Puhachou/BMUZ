-- Водгукі на альбомы: ацэнка ад 1 да 10 і тэкст не карацей за 10 знакаў — ацэнкі без слоў няма. Адзін водгук на
-- альбом ад аднаго карыстальніка. Сыходзіць разам з альбомам і разам з уліковым запісам: тэкст — асабістыя дадзеныя.
-- Ацэнка выканаўцы — сярэдняя ўсіх водгукаў на ягоныя альбомы; асобна яна не захоўваецца.
CREATE TABLE review (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    album_id   bigint NOT NULL REFERENCES album (id) ON DELETE CASCADE,
    user_id    bigint NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    score      smallint NOT NULL CHECK (score BETWEEN 1 AND 10),
    text       text NOT NULL CHECK (char_length(text) BETWEEN 10 AND 2000),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (album_id, user_id)
);

CREATE INDEX review_user ON review (user_id);
