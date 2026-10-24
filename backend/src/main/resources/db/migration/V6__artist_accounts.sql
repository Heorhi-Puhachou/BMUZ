-- Уліковыя запісы выканаўцаў. Выканаўца сам праўляе свае дадзеныя; правы даюцца на канкрэтнага выканаўцу, а не
-- роляй: адзін запіс можа праўляць некалькі выканаўцаў, і ў аднаго выканаўцы можа быць некалькі запісаў.
ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK (role IN ('ADMIN', 'ARTIST', 'USER'));

CREATE TABLE artist_member (
    artist_id  bigint NOT NULL REFERENCES artist (id) ON DELETE CASCADE,
    user_id    bigint NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    invited_by bigint REFERENCES app_user (id) ON DELETE SET NULL,
    joined_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (artist_id, user_id)
);

CREATE INDEX artist_member_user ON artist_member (user_id);

-- Запрашэньне адміністратара. Захоўваецца толькі хэш токена са спасылкі.
CREATE TABLE artist_invite (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artist_id  bigint NOT NULL REFERENCES artist (id) ON DELETE CASCADE,
    email      text NOT NULL CHECK (email = lower(email)),
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    invited_by bigint REFERENCES app_user (id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (artist_id, email)
);

-- Кожная зьмена каталёга: хто, калі, што і як было да яе. Зьмены бачныя на сайце адразу, а адміністратар бачыць гэты
-- сьпіс. Спасылкі на выканаўцу й альбом застаюцца пустымі, калі іх выдалілі; subject памятае назву.
CREATE TABLE edit_log (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    bigint REFERENCES app_user (id) ON DELETE SET NULL,
    email      text NOT NULL,
    artist_id  bigint REFERENCES artist (id) ON DELETE SET NULL,
    album_id   bigint REFERENCES album (id) ON DELETE SET NULL,
    action     text NOT NULL,
    subject    text NOT NULL,
    before     jsonb,
    after      jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX edit_log_created ON edit_log (created_at DESC);
