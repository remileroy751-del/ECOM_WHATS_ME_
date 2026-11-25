-- ============================================================
-- Ecom Whats Me — structure Supabase
-- À copier-coller intégralement dans Supabase > SQL Editor > Run
-- Ne s'exécute qu'une seule fois (sur une base neuve).
-- ============================================================

create extension if not exists pgcrypto;

-- ---------- tables ----------

create table if not exists license_keys (
    id            uuid primary key default gen_random_uuid(),
    code          text not null unique,
    type          text not null check (type in ('30', '90')),
    status        text not null default 'unused' check (status in ('unused', 'used')),
    used_by_device text,
    used_at       timestamptz,
    created_at    timestamptz not null default now()
);

create table if not exists activations (
    id            uuid primary key default gen_random_uuid(),
    device_id     text not null unique,
    key_code      text references license_keys(code),
    type          text,
    activated_at  timestamptz not null default now(),
    expires_at    timestamptz not null,
    updated_at    timestamptz not null default now()
);

create table if not exists installs (
    id            bigserial primary key,
    device_id     text not null,
    country       text,
    country_code  text,
    language      text,
    app_version   text,
    created_at    timestamptz not null default now()
);

create table if not exists admin_settings (
    id            int primary key default 1,
    password_hash text not null,
    constraint single_row check (id = 1)
);

-- ---------- mot de passe admin ----------
-- Remplace 'change-me-now' par ton mot de passe, PUIS exécute cette ligne UNE SEULE FOIS.
-- Tu pourras le changer plus tard en relançant cette même commande avec un nouveau mot de passe.
insert into admin_settings (id, password_hash)
values (1, crypt('change-me-now', gen_salt('bf')))
on conflict (id) do update set password_hash = excluded.password_hash;

-- ---------- sécurité : personne ne touche les tables directement ----------
alter table license_keys enable row level security;
alter table activations enable row level security;
alter table installs enable row level security;
alter table admin_settings enable row level security;
-- aucune politique créée => aucun accès direct, ni lecture ni écriture, pour anon/authenticated.
-- Tout passe obligatoirement par les fonctions RPC ci-dessous (SECURITY DEFINER).

revoke all on license_keys, activations, installs, admin_settings from anon, authenticated;

-- ============================================================
-- Fonctions appelées par l'APPLICATION (clé publique uniquement)
-- ============================================================

-- Enregistre une installation (pays / langue) pour les statistiques.
create or replace function log_install(
    p_device_id text, p_country text, p_country_code text, p_language text, p_app_version text
) returns void
language plpgsql security definer set search_path = public as $$
begin
    insert into installs (device_id, country, country_code, language, app_version)
    values (p_device_id, p_country, p_country_code, p_language, p_app_version);
end;
$$;

-- Active une clé pour un appareil. Retourne {"ok":true,"days":30,"type":"30"} ou {"ok":false,"error":"..."}
create or replace function claim_license_key(p_device_id text, p_code text)
returns json
language plpgsql security definer set search_path = public as $$
declare
    k record;
    cur_expiry timestamptz;
    new_expiry timestamptz;
    add_days int;
begin
    select * into k from license_keys where code = p_code for update;

    if k is null then
        return json_build_object('ok', false, 'error', 'key_err_unknown');
    end if;
    if k.status = 'used' then
        return json_build_object('ok', false, 'error', 'key_err_used');
    end if;

    add_days := case k.type when '30' then 30 when '90' then 90 else 0 end;

    select expires_at into cur_expiry from activations where device_id = p_device_id;
    if cur_expiry is not null and cur_expiry > now() then
        new_expiry := cur_expiry + make_interval(days => add_days);
    else
        new_expiry := now() + make_interval(days => add_days);
    end if;

    update license_keys set status = 'used', used_by_device = p_device_id, used_at = now()
        where code = p_code;

    insert into activations (device_id, key_code, type, expires_at)
        values (p_device_id, p_code, k.type, new_expiry)
    on conflict (device_id) do update
        set key_code = excluded.key_code, type = excluded.type,
            expires_at = excluded.expires_at, updated_at = now();

    return json_build_object(
        'ok', true, 'type', k.type,
        'days', ceil(extract(epoch from (new_expiry - now())) / 86400)
    );
end;
$$;

grant execute on function log_install(text, text, text, text, text) to anon, authenticated;
grant execute on function claim_license_key(text, text) to anon, authenticated;

-- ============================================================
-- Fonctions ADMIN (protégées par mot de passe, utilisées par admin/index.html)
-- ============================================================

create or replace function admin_check(p_password text) returns boolean
language plpgsql security definer set search_path = public as $$
declare ok boolean;
begin
    select (password_hash = crypt(p_password, password_hash)) into ok from admin_settings where id = 1;
    return coalesce(ok, false);
end;
$$;

-- Importe une liste de clés (une par ligne, déjà séparées côté appli) pour un type donné.
-- Ignore les doublons. Retourne le nombre de clés réellement ajoutées.
create or replace function admin_import_keys(p_password text, p_type text, p_codes text[])
returns int
language plpgsql security definer set search_path = public as $$
declare n int;
begin
    if not admin_check(p_password) then
        raise exception 'unauthorized';
    end if;
    if p_type not in ('30', '90') then
        raise exception 'invalid type';
    end if;

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

-- Prolonge la licence active d'un appareil (ex : +30 jours).
create or replace function admin_extend(p_password text, p_device_id text, p_days int)
returns json
language plpgsql security definer set search_path = public as $$
declare new_expiry timestamptz;
begin
    if not admin_check(p_password) then
        raise exception 'unauthorized';
    end if;

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

-- Statistiques globales pour le tableau de bord admin.
create or replace function admin_stats(p_password text)
returns json
language plpgsql security definer set search_path = public as $$
declare result json;
begin
    if not admin_check(p_password) then
        raise exception 'unauthorized';
    end if;

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

-- Recherche une activation par identifiant d'appareil (pour le bouton "+30 jours").
create or replace function admin_find_activation(p_password text, p_device_id text)
returns json
language plpgsql security definer set search_path = public as $$
declare result json;
begin
    if not admin_check(p_password) then
        raise exception 'unauthorized';
    end if;
    select row_to_json(a) into result from activations a where device_id = p_device_id;
    return result;
end;
$$;

-- admin_check n'est PAS ouverte directement à anon/authenticated : elle ne doit servir
-- qu'en interne aux fonctions admin_* ci-dessous (SECURITY DEFINER, exécutées avec les
-- droits du propriétaire). L'ouvrir publiquement permettrait de tester des mots de passe
-- en boucle depuis l'extérieur sans jamais toucher à license_keys ni activations.
revoke all on function admin_check(text) from anon, authenticated, public;

grant execute on function admin_import_keys(text, text, text[]) to anon, authenticated;
grant execute on function admin_extend(text, text, int) to anon, authenticated;
grant execute on function admin_stats(text) to anon, authenticated;
grant execute on function admin_find_activation(text, text) to anon, authenticated;

-- ============================================================
-- Fin. Les clés "30 jours" font 8 caractères alphanumériques affichés avec un
-- espace au milieu par l'appli (ex : jY8k 4Asx), et les clés "90 jours" font
-- 12 caractères sans espace (ex : Hg78kB32S4xP). Stocke-les SANS espace dans
-- la base pour les clés de 30 jours si tu préfères, ou AVEC l'espace exact
-- affiché au client — l'appli envoie la clé telle que l'utilisateur l'a tapée
-- (espace compris pour le format à 9 caractères). Reste cohérent entre ce que
-- tu génères/vends et ce que tu importes ici.
-- ============================================================
