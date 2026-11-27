-- ============================================================
-- Verrouille la période d'essai côté serveur, pour qu'une désinstallation +
-- réinstallation ne redonne plus un nouvel essai de 7 jours.
-- À exécuter UNE SEULE FOIS dans Supabase > SQL Editor > Run.
-- ============================================================

create table if not exists trials (
    hardware_id text primary key,
    started_at  timestamptz not null default now()
);
alter table trials enable row level security;
revoke all on trials from anon, authenticated;

-- Enregistre (une seule fois par appareil) le début de l'essai, ou renvoie
-- la date déjà enregistrée si cet appareil a déjà été vu, même après
-- désinstallation / réinstallation de l'application.
create or replace function register_trial(p_hardware_id text, p_hint timestamptz default now())
returns json
language plpgsql security definer set search_path = public as $$
declare v_started timestamptz;
begin
    insert into trials (hardware_id, started_at)
    values (p_hardware_id, least(p_hint, now()))
    on conflict (hardware_id) do nothing;

    select started_at into v_started from trials where hardware_id = p_hardware_id;
    return json_build_object('started_at', v_started);
end;
$$;

grant execute on function register_trial(text, timestamptz) to anon, authenticated;

notify pgrst, 'reload schema';
