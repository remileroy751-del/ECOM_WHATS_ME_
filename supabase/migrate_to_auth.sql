-- ============================================================
-- Migration : remplace le mot de passe admin personnalisé par l'authentification
-- native de Supabase (onglet Authentication). À exécuter UNE SEULE FOIS dans
-- Supabase > SQL Editor > Run, après avoir créé ton utilisateur dans
-- Authentication > Users (email : tyros.petros@gmail.com, avec un mot de passe).
-- ============================================================

-- ---------- liste des emails administrateurs ----------
create table if not exists admin_users (
    email text primary key
);
alter table admin_users enable row level security;
revoke all on admin_users from anon, authenticated;

insert into admin_users (email) values ('tyros.petros@gmail.com')
on conflict (email) do nothing;

-- Pour ajouter un autre administrateur plus tard (après l'avoir créé dans
-- Authentication > Users), relance juste :
-- insert into admin_users (email) values ('autre.mail@exemple.com') on conflict do nothing;

-- ---------- vérifie si la personne CONNECTÉE (via Supabase Auth) est admin ----------
create or replace function is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from admin_users where email = auth.email());
$$;

-- ---------- on retire les anciennes fonctions protégées par mot de passe ----------
drop function if exists admin_check(text);
drop function if exists admin_import_keys(text, text, text[]);
drop function if exists admin_extend(text, text, int);
drop function if exists admin_stats(text);
drop function if exists admin_find_activation(text, text);
drop table if exists admin_settings;

-- ---------- on les recrée, protégées par la connexion Supabase Auth ----------
create or replace function admin_import_keys(p_type text, p_codes text[])
returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
    if not is_admin() then raise exception 'unauthorized'; end if;
    if p_type not in ('30', '90') then raise exception 'invalid type'; end if;

    with ins as (
        insert into license_keys (code, type)
        select distinct trim(c), p_type from unnest(p_codes) as c
        where trim(c) <> ''
        on conflict (code) do nothing
        returning 1
    )
    select count(*) into n from ins;
    return n;
end;
$$;

create or replace function admin_extend(p_device_id text, p_days int)
returns json
language plpgsql security definer set search_path = public as $$
declare new_expiry timestamptz;
begin
    if not is_admin() then raise exception 'unauthorized'; end if;

    update activations
        set expires_at = greatest(expires_at, now()) + make_interval(days => p_days),
            updated_at = now()
        where device_id = p_device_id
        returning expires_at into new_expiry;

    if new_expiry is null then
        insert into activations (device_id, type, expires_at)
        values (p_device_id, 'manuel', now() + make_interval(days => p_days))
        returning expires_at into new_expiry;
    end if;

    return json_build_object('ok', true, 'expires_at', new_expiry);
end;
$$;

create or replace function admin_stats()
returns json
language plpgsql security definer set search_path = public as $$
declare result json;
begin
    if not is_admin() then raise exception 'unauthorized'; end if;

    select json_build_object(
        'total_installs', (select count(*) from installs),
        'by_country', (
            select coalesce(json_agg(row_to_json(t)), '[]'::json) from (
                select country, country_code, count(*) as n
                from installs where country is not null
                group by country, country_code order by n desc
            ) t
        ),
        'by_language', (
            select coalesce(json_agg(row_to_json(t)), '[]'::json) from (
                select language, count(*) as n from installs where language is not null
                group by language order by n desc
            ) t
        ),
        'keys_30_unused', (select count(*) from license_keys where type='30' and status='unused'),
        'keys_30_used', (select count(*) from license_keys where type='30' and status='used'),
        'keys_90_unused', (select count(*) from license_keys where type='90' and status='unused'),
        'keys_90_used', (select count(*) from license_keys where type='90' and status='used'),
        'active_licenses', (select count(*) from activations where expires_at > now())
    ) into result;
    return result;
end;
$$;

create or replace function admin_find_activation(p_device_id text)
returns json
language plpgsql security definer set search_path = public as $$
declare result json;
begin
    if not is_admin() then raise exception 'unauthorized'; end if;
    select row_to_json(a) into result from activations a where device_id = p_device_id;
    return result;
end;
$$;

grant execute on function admin_import_keys(text, text[]) to authenticated;
grant execute on function admin_extend(text, int) to authenticated;
grant execute on function admin_stats() to authenticated;
grant execute on function admin_find_activation(text) to authenticated;

-- ============================================================
-- Fin de la migration. Important : log_install et claim_license_key (utilisées
-- par l'application mobile) ne changent pas, elles restent accessibles sans
-- connexion — seule l'interface admin passe par un vrai compte désormais.
-- ============================================================
