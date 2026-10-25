-- Профіль. Імя бачнае ўсім пад камэнтарамі, таму ў двух запісаў яно не можа супадаць — нават рознымі літарамі
-- (вялікімі ці малымі). Правапіс — той, што ўключаецца пры ўваходзе; пусты — пакуль карыстальнік не выбраў.
ALTER TABLE app_user
    ADD COLUMN display_name text CHECK (display_name = btrim(display_name)
        AND char_length(display_name) BETWEEN 1 AND 40),
    ADD COLUMN spelling     text CHECK (spelling IN ('be-tarask', 'be', 'be-Latn'));

CREATE UNIQUE INDEX app_user_display_name ON app_user (lower(display_name));
