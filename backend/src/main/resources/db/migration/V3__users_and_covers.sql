-- Карыстальнікі сайта. Пакуль ёсьць толькі адміністратар; публічная рэгістрацыя будзе пазьней.
CREATE TABLE app_user (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         text NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash text NOT NULL,
    role          text NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    created_at    timestamptz NOT NULL DEFAULT now()
);

-- Падстава, на якой сайт можа захоўваць і паказваць вокладкі выканаўцы: пісьмовы дазвол або адкрытая ліцэнзія.
-- Без такога запісу вокладка не захоўваецца.
CREATE TABLE permission (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artist_id   bigint NOT NULL REFERENCES artist (id) ON DELETE RESTRICT,
    kind        text NOT NULL CHECK (kind IN ('WRITTEN_PERMISSION', 'OPEN_LICENCE')),
    licence     text,
    proof       text NOT NULL CHECK (length(trim(proof)) > 0),
    proof_url   text,
    granted_on  date NOT NULL,
    scope       text NOT NULL CHECK (length(trim(scope)) > 0),
    created_by  bigint NOT NULL REFERENCES app_user (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (kind <> 'OPEN_LICENCE' OR (licence IS NOT NULL AND proof_url IS NOT NULL))
);

-- Адна вокладка на альбом, у двух памерах. Файлы ляжаць у сховішчы, тут — толькі іх ключы і падстава.
CREATE TABLE cover (
    album_id      bigint PRIMARY KEY REFERENCES album (id) ON DELETE CASCADE,
    large_key     text NOT NULL,
    small_key     text NOT NULL,
    permission_id bigint NOT NULL REFERENCES permission (id) ON DELETE RESTRICT,
    uploaded_by   bigint NOT NULL REFERENCES app_user (id),
    uploaded_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX permission_artist ON permission (artist_id);
CREATE INDEX cover_permission ON cover (permission_id);

ALTER TABLE album ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
