-- UpRep : copie de toute la base pour la sauvegarde chiffrée de nuit (plan de reprise, claude/plan-reprise.md).
-- Philippe, 22 novembre 2026 : « Peux-tu planifier une copie des donnés de la base cryptée chez toi? »
-- À coller dans SQL Editor → New query → Run (relançable sans risque).
-- NE PAS passer ce script par deploy-supabase.yml : la dernière ligne affiche la clé, et les journaux
-- des Actions d'un dépôt public sont lisibles par tous.
-- Pour changer la clé (perdue ou divulguée) : relancer seulement la dernière ligne ; l'ancienne ne marche plus.

-- 1) Clé de sauvegarde : une seule, empreinte seulement, dans le schéma non exposé
create schema if not exists prive;
revoke all on schema prive from public, anon, authenticated;
create table if not exists prive.cle_sauvegarde (
  id int primary key default 1 check (id = 1),
  empreinte text not null,
  created_at timestamptz not null default now()
);

create or replace function prive.nouvelle_cle_sauvegarde() returns text
language plpgsql security definer set search_path = '' as $fn$
declare k text := replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', '');
begin
  insert into prive.cle_sauvegarde (id, empreinte) values (1, encode(sha256(convert_to(k, 'UTF8')), 'hex'))
  on conflict (id) do update set empreinte = excluded.empreinte, created_at = now();
  return k;
end $fn$;

-- 2) Lecture seule de toutes les tables de l'appli, tous comptes confondus
--    Comptes : identifiant, adresse, fournisseur et dates seulement (pas de mot de passe chiffré ni de jeton).
create or replace function public.sauvegarde_tout(cle text) returns jsonb
language plpgsql stable security definer set search_path = '' as $fn$
begin
  if not exists (select 1 from prive.cle_sauvegarde where empreinte = encode(sha256(convert_to(coalesce(cle, ''), 'UTF8')), 'hex')) then
    raise exception 'Clé invalide';
  end if;
  return jsonb_build_object(
    'format', 1,
    'faite_le', now(),
    'comptes', coalesce((select jsonb_agg(jsonb_build_object('id', id, 'email', email, 'fournisseur', raw_app_meta_data->'provider',
                 'created_at', created_at, 'email_confirmed_at', email_confirmed_at) order by created_at) from auth.users), '[]'::jsonb),
    'sessions',    coalesce((select jsonb_agg(to_jsonb(t)) from public.sessions t), '[]'::jsonb),
    'days',        coalesce((select jsonb_agg(to_jsonb(t)) from public.days t), '[]'::jsonb),
    'state',       coalesce((select jsonb_agg(to_jsonb(t)) from public.state t), '[]'::jsonb),
    'bilans',      coalesce((select jsonb_agg(to_jsonb(t)) from public.bilans t), '[]'::jsonb),
    'coach_data',  coalesce((select jsonb_agg(to_jsonb(t)) from public.coach_data t), '[]'::jsonb),
    'coach_usage', coalesce((select jsonb_agg(to_jsonb(t)) from public.coach_usage t), '[]'::jsonb),
    'retours',     coalesce((select jsonb_agg(to_jsonb(t)) from public.retours t), '[]'::jsonb),
    'plantages',   coalesce((select jsonb_agg(to_jsonb(t)) from public.plantages t), '[]'::jsonb),
    'programme',   coalesce((select jsonb_agg(to_jsonb(t)) from public.programme t), '[]'::jsonb));
end $fn$;

revoke all on function prive.nouvelle_cle_sauvegarde() from public, anon, authenticated;
revoke all on function public.sauvegarde_tout(text) from public;
grant execute on function public.sauvegarde_tout(text) to anon;

-- 3) Crée la clé et l'affiche (une seule fois) : copie-la et donne-la à Claude
select prive.nouvelle_cle_sauvegarde() as cle_sauvegarde;
