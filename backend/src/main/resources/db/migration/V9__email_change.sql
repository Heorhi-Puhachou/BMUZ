-- Зьмена адраса пошты. Новы адрас чакае тут, пакуль спасылка з ліста на яго не пацьвердзіць, што пошта належыць
-- уладальніку запісу; да таго ўваход і лісты ідуць на стары. Захоўваецца толькі хэш токена са спасылкі. Токена няма,
-- калі новы адрас ужо заняты: тады на яго ідзе ліст без спасылкі, а запіс памятае толькі, калі можна прасіць зноў.
ALTER TABLE app_user
    ADD COLUMN new_email               text CHECK (new_email = lower(new_email)),
    ADD COLUMN email_change_token_hash text UNIQUE,
    ADD COLUMN email_change_expires_at timestamptz;
