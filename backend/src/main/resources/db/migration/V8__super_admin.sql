-- Адзін суперадміністратар: толькі ён запрашае адміністратараў і выканаўцаў і забірае іхныя правы.
ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check
    CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'ARTIST', 'USER'));
CREATE UNIQUE INDEX app_user_one_super_admin ON app_user ((true)) WHERE role = 'SUPER_ADMIN';

-- Калі адміністратар адзін, ён і становіцца суперадміністратарам.
UPDATE app_user SET role = 'SUPER_ADMIN'
WHERE role = 'ADMIN' AND (SELECT count(*) FROM app_user WHERE role = 'ADMIN') = 1;

-- Запрашэньні цяпер двух відаў: адміністратарам (без выканаўцы) і выканаўцам (з выканаўцам).
ALTER TABLE artist_invite RENAME TO invite;
ALTER TABLE invite ADD COLUMN role text NOT NULL DEFAULT 'ARTIST' CHECK (role IN ('ADMIN', 'ARTIST'));
ALTER TABLE invite ALTER COLUMN role DROP DEFAULT;
ALTER TABLE invite ALTER COLUMN artist_id DROP NOT NULL;
ALTER TABLE invite ADD CONSTRAINT invite_artist_matches_role CHECK ((role = 'ARTIST') = (artist_id IS NOT NULL));
CREATE UNIQUE INDEX invite_admin_email ON invite (email) WHERE role = 'ADMIN';
