-- Programme Force : programme en base + « clé Claude » à droits limités.
-- À coller dans SQL Editor → New query → Run (une seule fois ; relançable sans risque).
-- Le résultat affiche la clé à donner à Claude. Pour la changer plus tard (clé perdue ou divulguée),
-- relance seulement la dernière ligne : l'ancienne clé ne marche plus.

-- 1) Versions du programme (exercices, semaines, séances, conseils, mouvements)
create table if not exists public.programme (
  id bigint generated always as identity primary key,
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  data jsonb not null,
  note text,
  created_at timestamptz not null default now()
);
alter table public.programme enable row level security;
drop policy if exists "proprietaire" on public.programme;
create policy "proprietaire" on public.programme for select to authenticated using (user_id = auth.uid());

-- 2) Clé Claude : seule son empreinte est stockée, dans un schéma non exposé
create schema if not exists prive;
revoke all on schema prive from public, anon, authenticated;
create table if not exists prive.cle_claude (
  user_id uuid primary key references auth.users on delete cascade,
  empreinte text not null,
  created_at timestamptz not null default now()
);

create or replace function prive.proprio(cle text) returns uuid
language sql stable security definer set search_path = '' as $$
  select user_id from prive.cle_claude where empreinte = encode(sha256(convert_to(cle, 'UTF8')), 'hex')
$$;

create or replace function prive.nouvelle_cle(adresse text) returns text
language plpgsql security definer set search_path = '' as $$
declare k text := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''); u uuid;
begin
  select id into u from auth.users where email = adresse;
  if u is null then raise exception 'Aucun compte avec l''adresse %', adresse; end if;
  insert into prive.cle_claude (user_id, empreinte) values (u, encode(sha256(convert_to(k, 'UTF8')), 'hex'))
  on conflict (user_id) do update set empreinte = excluded.empreinte, created_at = now();
  return k;
end $$;

-- 3) Ce que la clé permet, et rien d'autre
-- Lire séances, journées, réglages, bilans et la version du programme
create or replace function public.claude_lire(cle text) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare u uuid := prive.proprio(cle);
begin
  if u is null then raise exception 'Clé invalide'; end if;
  return jsonb_build_object(
    'sessions', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'd', d, 'data', data) order by d) from public.sessions where user_id = u), '[]'::jsonb),
    'days', coalesce((select jsonb_agg(jsonb_build_object('date', date, 'data', data) order by date) from public.days where user_id = u), '[]'::jsonb),
    'state', (select data from public.state where user_id = u),
    'bilans', coalesce((select jsonb_agg(jsonb_build_object('data', data, 'created_at', created_at) order by created_at) from public.bilans where user_id = u), '[]'::jsonb),
    'programme', (select jsonb_build_object('id', id, 'note', note, 'created_at', created_at) from public.programme where user_id = u order by id desc limit 1));
end $$;

-- Lire le programme complet (dernière version)
create or replace function public.claude_programme_actuel(cle text) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare u uuid := prive.proprio(cle);
begin
  if u is null then raise exception 'Clé invalide'; end if;
  return (select jsonb_build_object('id', id, 'note', note, 'data', data) from public.programme where user_id = u order by id desc limit 1);
end $$;

-- Écrire le bilan de la semaine
create or replace function public.claude_bilan(cle text, data jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare u uuid := prive.proprio(cle);
begin
  if u is null then raise exception 'Clé invalide'; end if;
  insert into public.bilans (user_id, data) values (u, data);
end $$;

-- Publier une nouvelle version du programme (les anciennes sont gardées)
create or replace function public.claude_programme(cle text, data jsonb, note text) returns bigint
language plpgsql security definer set search_path = '' as $$
declare u uuid := prive.proprio(cle); n bigint;
begin
  if u is null then raise exception 'Clé invalide'; end if;
  if data->'EX' is null or data->'WEEKS' is null or data->'SESS1' is null then raise exception 'Programme incomplet'; end if;
  insert into public.programme (user_id, data, note) values (u, data, note) returning id into n;
  return n;
end $$;

revoke all on function prive.proprio(text), prive.nouvelle_cle(text) from public, anon, authenticated;
revoke all on function public.claude_lire(text), public.claude_programme_actuel(text), public.claude_bilan(text, jsonb), public.claude_programme(text, jsonb, text) from public;
grant execute on function public.claude_lire(text), public.claude_programme_actuel(text), public.claude_bilan(text, jsonb), public.claude_programme(text, jsonb, text) to anon, authenticated;

-- 4) Crée la clé et l'affiche (une seule fois) : copie-la et donne-la à Claude
select prive.nouvelle_cle('pgragnic@hotmail.com') as cle_pour_claude;
