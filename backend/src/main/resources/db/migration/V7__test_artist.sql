-- Тэхнічны выканаўца, каб правяраць уліковы запіс выканаўцы на кожнай копіі базы, і на AWS таксама.
-- Бачны ўсім, як сапраўдны.
WITH artist AS (
    INSERT INTO artist (name) VALUES ('Testavy Muzyka') RETURNING id
), album AS (
    INSERT INTO album (year, title) VALUES (2026, 'Testavy albom') RETURNING id
)
INSERT INTO album_credit (album_id, position, artist_id, join_phrase)
SELECT album.id, 0, artist.id, '' FROM album, artist;
