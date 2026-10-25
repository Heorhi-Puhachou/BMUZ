-- Карыстальнік можа выдаліць свой уліковы запіс. Вокладка і ейная падстава застаюцца: падстава — доказ, што сайт мае
-- права паказваць вокладку. Застаецца толькі сувязь з запісам, які яе стварыў, — яна становіцца пустой.
ALTER TABLE permission ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE permission DROP CONSTRAINT permission_created_by_fkey;
ALTER TABLE permission ADD CONSTRAINT permission_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE SET NULL;

ALTER TABLE cover ALTER COLUMN uploaded_by DROP NOT NULL;
ALTER TABLE cover DROP CONSTRAINT cover_uploaded_by_fkey;
ALTER TABLE cover ADD CONSTRAINT cover_uploaded_by_fkey
    FOREIGN KEY (uploaded_by) REFERENCES app_user (id) ON DELETE SET NULL;
