CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE COLLATION IF NOT EXISTS belarusian (provider = icu, locale = 'be');

CREATE TABLE artist (
    id   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name text COLLATE belarusian NOT NULL
);

CREATE TABLE artist_alias (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artist_id bigint NOT NULL REFERENCES artist (id) ON DELETE CASCADE,
    name      text COLLATE belarusian NOT NULL,
    UNIQUE (artist_id, name)
);

CREATE TABLE album (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artist_id bigint NOT NULL REFERENCES artist (id) ON DELETE CASCADE,
    year      integer NOT NULL,
    title     text COLLATE belarusian NOT NULL,
    UNIQUE (artist_id, year, title)
);

CREATE TABLE track (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    album_id       bigint NOT NULL REFERENCES album (id) ON DELETE CASCADE,
    number         integer NOT NULL CHECK (number > 0),
    title          text COLLATE belarusian NOT NULL,
    length_seconds integer CHECK (length_seconds > 0),
    UNIQUE (album_id, number)
);

CREATE TABLE store (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     text NOT NULL UNIQUE,
    homepage text
);

CREATE TABLE link (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id   bigint NOT NULL REFERENCES store (id),
    album_id   bigint REFERENCES album (id) ON DELETE CASCADE,
    track_id   bigint REFERENCES track (id) ON DELETE CASCADE,
    url        text NOT NULL,
    price_type text NOT NULL CHECK (price_type IN ('FREE', 'NAME_YOUR_PRICE', 'PAID')),
    min_price  numeric(10, 2) CHECK (min_price >= 0),
    currency   text CHECK (char_length(currency) = 3),
    note       text,
    embed_code text,
    CHECK (num_nonnulls(album_id, track_id) = 1)
);

CREATE INDEX artist_name_trgm ON artist USING gin (name gin_trgm_ops);
CREATE INDEX artist_alias_name_trgm ON artist_alias USING gin (name gin_trgm_ops);
CREATE INDEX album_title_trgm ON album USING gin (title gin_trgm_ops);
CREATE INDEX album_artist ON album (artist_id);
CREATE INDEX track_album ON track (album_id);
CREATE INDEX link_album ON link (album_id);
CREATE INDEX link_track ON link (track_id);
