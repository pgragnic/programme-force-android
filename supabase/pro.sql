-- UpRep : améliorations « pour faire plus pro » (8 novembre 2026).
-- À coller dans SQL Editor → New query → Run (une seule fois ; relançable sans risque).
-- Nécessite d'avoir déjà lancé schema.sql, cle-claude.sql et retours.sql.

-- 1) Programme lisible par tous les utilisateurs (même sans compte) : avant, seul le compte de Philippe recevait
--    les nouvelles versions du programme ; les autres gardaient celui livré avec l'appli.
drop policy if exists "lecture publique" on public.programme;
create policy "lecture publique" on public.programme for select to anon, authenticated using (true);

-- Contrôle plus strict avant de publier une version du programme
create or replace function public.claude_programme(cle text, data jsonb, note text) returns bigint
language plpgsql security definer set search_path = '' as $fn$
declare u uuid := prive.proprio(cle); n bigint; manque text;
begin
  if u is null then raise exception 'Clé invalide'; end if;
  if data->'EX' is null or data->'WEEKS' is null or data->'SESS1' is null or data->'SESS2' is null or data->'WU' is null then
    raise exception 'Programme incomplet'; end if;
  if jsonb_array_length(data->'WEEKS') < 8 then raise exception 'Il faut 8 semaines'; end if;
  select string_agg(distinct e, ', ') into manque from (
    select jsonb_array_elements_text(s.value->'ex') e from jsonb_each(data->'SESS1') s
    union all select jsonb_array_elements_text(s.value->'ex') from jsonb_each(data->'SESS2') s) x
  where not (data->'EX' ? e);
  if manque is not null then raise exception 'Exercices inconnus dans les séances : %', manque; end if;
  select string_agg(distinct w, ', ') into manque from (
    select jsonb_array_elements_text(s.value->'warm') w from jsonb_each(data->'SESS1') s
    union all select jsonb_array_elements_text(s.value->'warm') from jsonb_each(data->'SESS2') s) x
  where not (data->'WU' ? w);
  if manque is not null then raise exception 'Échauffements inconnus : %', manque; end if;
  insert into public.programme (user_id, data, note) values (u, data, note) returning id into n;
  return n;
end $fn$;

-- Retour arrière d'un geste : republie une ancienne version comme la plus récente
create or replace function public.claude_programme_retour(cle text, version bigint) returns bigint
language plpgsql security definer set search_path = '' as $fn$
declare u uuid := prive.proprio(cle); n bigint;
begin
  if u is null then raise exception 'Clé invalide'; end if;
  insert into public.programme (user_id, data, note)
    select u, data, 'Retour à la version ' || version from public.programme where id = version returning id into n;
  if n is null then raise exception 'Version % introuvable', version; end if;
  return n;
end $fn$;

-- 2) Rapports de plantage (envoyés automatiquement par l'appli, sans donnée personnelle)
create table if not exists public.plantages (
  id bigint generated always as identity primary key,
  created_at timestamptz not null default now(),
  appli text not null check (char_length(appli) <= 100),
  android text not null check (char_length(android) <= 100),
  appareil text not null check (char_length(appareil) <= 100),
  quand bigint not null,
  trace text not null check (char_length(trace) <= 12100)
);
alter table public.plantages enable row level security;
drop policy if exists "envoyer" on public.plantages;
create policy "envoyer" on public.plantages for insert to anon, authenticated with check (true);

create or replace function public.claude_plantages(cle text) returns jsonb language plpgsql stable security definer set search_path = '' as $fn$
begin
if prive.proprio(cle) is null then raise exception 'Clé invalide'; end if;
return coalesce((select jsonb_agg(to_jsonb(p) order by p.created_at desc) from (select * from public.plantages order by created_at desc limit 200) p), '[]'::jsonb);
end $fn$;

-- 3) Avis : limite d'envois (contre les abus) et réponses lisibles dans l'appli
alter table public.retours add column if not exists reponse text check (reponse is null or char_length(reponse) <= 2000);
alter table public.retours add column if not exists repondu_le timestamptz;

create or replace function prive.limite_envois() returns trigger language plpgsql security definer set search_path = '' as $fn$
declare n int;
begin
  if tg_table_name = 'retours' then
    if new.user_id is not null then
      select count(*) into n from public.retours where user_id = new.user_id and created_at > now() - interval '1 hour';
      if n >= 5 then raise exception 'Trop de messages envoyés : réessaie dans une heure'; end if;
    end if;
    select count(*) into n from public.retours where user_id is null and created_at > now() - interval '1 hour';
    if new.user_id is null and n >= 30 then raise exception 'Trop de messages envoyés : réessaie plus tard'; end if;
  else
    select count(*) into n from public.plantages where created_at > now() - interval '1 hour';
    if n >= 100 then return null; end if; -- au-delà, on ignore sans erreur
  end if;
  return new;
end $fn$;
drop trigger if exists limite on public.retours;
create trigger limite before insert on public.retours for each row execute function prive.limite_envois();
drop trigger if exists limite on public.plantages;
create trigger limite before insert on public.plantages for each row execute function prive.limite_envois();

-- Chaque compte relit ses propres messages et les réponses
drop policy if exists "relire" on public.retours;
create policy "relire" on public.retours for select to authenticated using (user_id = auth.uid());
-- Les colonnes réservées ne sont pas modifiables à l'envoi
revoke insert on public.retours from anon, authenticated;
grant insert (kind, texte, contact, appli, user_id) on public.retours to anon, authenticated;

create or replace function public.claude_retour_repondre(cle text, n bigint, texte text) returns void language plpgsql security definer set search_path = '' as $fn$
begin
if prive.proprio(cle) is null then raise exception 'Clé invalide'; end if;
update public.retours set reponse = texte, repondu_le = now(), etat = 'repondu' where id = n;
end $fn$;

revoke all on function public.claude_programme_retour(text, bigint), public.claude_plantages(text), public.claude_retour_repondre(text, bigint, text) from public;
grant execute on function public.claude_programme(text, jsonb, text), public.claude_programme_retour(text, bigint), public.claude_plantages(text), public.claude_retour_repondre(text, bigint, text) to anon, authenticated;
revoke all on function prive.limite_envois() from public, anon, authenticated;
