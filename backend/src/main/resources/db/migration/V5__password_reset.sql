-- Аднаўленьне пароля па пошце. Як і пры рэгістрацыі, захоўваецца толькі хэш токена са спасылкі.
ALTER TABLE app_user
    ADD COLUMN reset_token_hash text UNIQUE,
    ADD COLUMN reset_expires_at timestamptz;
