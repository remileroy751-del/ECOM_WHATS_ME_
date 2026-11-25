-- À exécuter dans Supabase > SQL Editor > Run, pour définir (ou changer) le mot de passe admin.
-- Peut être relancé autant de fois que tu veux avec un nouveau mot de passe.

insert into admin_settings (id, password_hash)
values (1, crypt('Jesuistyros007', gen_salt('bf')))
on conflict (id) do update set password_hash = excluded.password_hash;
