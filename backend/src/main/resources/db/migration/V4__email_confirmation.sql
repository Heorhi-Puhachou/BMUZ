-- Рэгістрацыя па пошце: уліковы запіс працуе толькі пасьля таго, як уладальнік адкрые спасылку з ліста.
-- Захоўваецца толькі хэш токена, каб уцечка базы не давала гатовых спасылак.
ALTER TABLE app_user
    ADD COLUMN confirmed_at       timestamptz,
    ADD COLUMN confirm_token_hash text UNIQUE,
    ADD COLUMN confirm_expires_at timestamptz;

UPDATE app_user SET confirmed_at = created_at WHERE role = 'ADMIN';
