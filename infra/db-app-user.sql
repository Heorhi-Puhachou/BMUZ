-- Уласны карыстальнік базы для сайта, bmuz_app, з паролем, які RDS не мяняе.
-- Выконвае адміністратар базы bmuz; пароль бярэцца са зьменнай асяродзьдзя APP_PW.
-- Табліцы, якія ўжо ёсьць, пераходзяць да bmuz_app, каб Flyway мог іх мяняць.
-- Другі запуск спыняецца на CREATE ROLE і нічога не мяняе.
\set ON_ERROR_STOP on
\getenv app_pw APP_PW
BEGIN;
-- Пашырэньне ставіць адміністратар: bmuz_app не можа ствараць яго ў пустой базе.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE ROLE bmuz_app LOGIN PASSWORD :'app_pw';
-- Перадаць свае табліцы bmuz_app адміністратар можа толькі як ягоны ўдзельнік.
GRANT bmuz_app TO bmuz;
REVOKE ALL ON DATABASE bmuz FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE bmuz TO bmuz_app;
GRANT USAGE, CREATE ON SCHEMA public TO bmuz_app;
DO $$
DECLARE r record;
BEGIN
  -- Спачатку табліцы: іхныя пасьлядоўнасьці identity пераходзяць разам з імі.
  FOR r IN SELECT c.oid::regclass AS name FROM pg_class c
           WHERE c.relnamespace = 'public'::regnamespace AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
             AND c.relowner = 'bmuz'::regrole
             AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_class'::regclass
                             AND d.objid = c.oid AND d.deptype = 'e')
  LOOP
    EXECUTE format('ALTER TABLE %s OWNER TO bmuz_app', r.name);
  END LOOP;
  FOR r IN SELECT c.oid::regclass AS name FROM pg_class c
           WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'S'
             AND c.relowner = 'bmuz'::regrole
  LOOP
    EXECUTE format('ALTER SEQUENCE %s OWNER TO bmuz_app', r.name);
  END LOOP;
END $$;
COMMIT;
-- Колькі ў public яшчэ належыць адміністратару: павінна быць 0.
SELECT count(*) AS left_to_master FROM pg_class c
WHERE c.relnamespace = 'public'::regnamespace AND c.relowner = 'bmuz'::regrole
  AND c.relkind IN ('r', 'p', 'v', 'm', 'f', 'S');
