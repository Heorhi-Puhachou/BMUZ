-- Выява выканаўцы, якую ён загружае сам. Пакуль яе няма, плітка выканаўцы паказвае вокладку ягонага апошняга
-- альбома. Як і вокладка, без падставы яна не захоўваецца.
CREATE TABLE artist_image (
    artist_id     bigint PRIMARY KEY REFERENCES artist (id) ON DELETE CASCADE,
    large_key     text NOT NULL,
    small_key     text NOT NULL,
    permission_id bigint NOT NULL REFERENCES permission (id) ON DELETE RESTRICT,
    uploaded_by   bigint REFERENCES app_user (id) ON DELETE SET NULL,
    uploaded_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX artist_image_permission ON artist_image (permission_id);
