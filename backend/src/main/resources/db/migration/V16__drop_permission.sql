-- Падстава вокладак больш не захоўваецца на сайце: доказы ўладальнік трымае ў сваёй пошце. Застаюцца толькі назва
-- адкрытай ліцэнзіі і старонка, дзе яна паказаная, — ліцэнзіі тыпу CC BY патрабуюць паказваць іх побач з выявай.
ALTER TABLE cover ADD COLUMN licence text;
ALTER TABLE cover ADD COLUMN licence_url text;

UPDATE cover c SET licence = p.licence, licence_url = p.proof_url
FROM permission p
WHERE p.id = c.permission_id AND p.kind = 'OPEN_LICENCE';

ALTER TABLE cover ADD CHECK ((licence IS NULL) = (licence_url IS NULL));

ALTER TABLE cover DROP COLUMN permission_id;
ALTER TABLE artist_image DROP COLUMN permission_id;
DROP TABLE permission;
