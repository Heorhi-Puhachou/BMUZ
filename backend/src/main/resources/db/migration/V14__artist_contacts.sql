-- Кантакты, якія выканаўца сам паказвае на сваёй старонцы. Пісаць іх можа толькі ўліковы запіс выканаўцы;
-- адміністратар можа толькі выдаліць. Выглядае адрас правільна, правярае сэрвэр — тут толькі від і даўжыня.
CREATE TABLE artist_contact (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artist_id bigint NOT NULL REFERENCES artist (id) ON DELETE CASCADE,
    position  int    NOT NULL,
    kind      text   NOT NULL CHECK (kind IN ('EMAIL', 'WEBSITE', 'INSTAGRAM', 'FACEBOOK', 'YOUTUBE', 'BANDCAMP',
                                              'SOUNDCLOUD', 'OTHER')),
    value     text   NOT NULL CHECK (length(value) BETWEEN 1 AND 300),
    UNIQUE (artist_id, position)
);
