-- Альбом або трэк можа мець некалькі выканаўцаў. Крэдыт — іхны ўпарадкаваны сьпіс са злучальным
-- тэкстам паміж імёнамі (« & », «, »), так што імёны разам з ім даюць назву, як яе напісаў выканаўца.

CREATE TABLE album_credit (
    album_id    bigint NOT NULL REFERENCES album (id) ON DELETE CASCADE,
    position    integer NOT NULL CHECK (position >= 0),
    artist_id   bigint NOT NULL REFERENCES artist (id) ON DELETE CASCADE,
    join_phrase text NOT NULL DEFAULT '',
    PRIMARY KEY (album_id, position),
    UNIQUE (album_id, artist_id)
);

CREATE TABLE track_credit (
    track_id    bigint NOT NULL REFERENCES track (id) ON DELETE CASCADE,
    position    integer NOT NULL CHECK (position >= 0),
    artist_id   bigint NOT NULL REFERENCES artist (id) ON DELETE CASCADE,
    join_phrase text NOT NULL DEFAULT '',
    PRIMARY KEY (track_id, position),
    UNIQUE (track_id, artist_id)
);

CREATE INDEX album_credit_artist ON album_credit (artist_id);
CREATE INDEX track_credit_artist ON track_credit (artist_id);

INSERT INTO album_credit (album_id, position, artist_id)
SELECT id, 0, artist_id FROM album;

ALTER TABLE album DROP COLUMN artist_id;

CREATE INDEX album_year_title ON album (year, title);
