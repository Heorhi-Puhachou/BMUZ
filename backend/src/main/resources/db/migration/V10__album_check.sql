-- Хто з адміністратараў праглядзеў які альбом і калі. Адзін радок на альбом і адміністратара: няма радка — не
-- праглядзеў, таму новыя альбомы і новыя адміністратары нічога тут не ствараюць. Альбом, зьменены пасьля прагляду
-- (album.updated_at пазьней за checked_at), лічыцца не праглядзеным зноў.
CREATE TABLE album_check (
    album_id   bigint NOT NULL REFERENCES album (id) ON DELETE CASCADE,
    user_id    bigint NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    checked_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (album_id, user_id)
);

CREATE INDEX album_check_user ON album_check (user_id);
