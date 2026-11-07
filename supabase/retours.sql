-- Programme Force : avis et idées d'amélioration envoyés depuis l'appli.
-- À coller dans SQL Editor → New query → Run (une seule fois).
-- Tout le monde peut envoyer un avis (même sans compte) ; personne ne peut lire ceux des autres.
-- Seule la clé Claude permet de les lire, pour en faire la synthèse.
create table if not exists public.retours (
  id bigint generated always as identity primary key,
  created_at timestamptz not null default now(),
  user_id uuid default auth.uid() references auth.users on delete set null,
  kind text not null check (kind in ('idee', 'probleme', 'avis')),
  texte text not null check (char_length(texte) between 3 and 2000),
  contact text check (contact is null or char_length(contact) <= 200),
  appli text check (appli is null or char_length(appli) <= 100),
  etat text not null default 'nouveau'
);
alter table public.retours enable row level security;
drop policy if exists "envoyer" on public.retours;
create policy "envoyer" on public.retours for insert to anon, authenticated
  with check ((user_id is null or user_id = auth.uid()) and etat = 'nouveau');
create or replace function public.claude_retours(cle text) returns jsonb language plpgsql stable security definer set search_path = '' as $fn$
begin
if prive.proprio(cle) is null then raise exception 'Clé invalide'; end if;
return coalesce((select jsonb_agg(to_jsonb(r) - 'user_id' order by r.created_at desc) from public.retours r), '[]'::jsonb);
end $fn$;
create or replace function public.claude_retour_etat(cle text, n bigint, e text) returns void language plpgsql security definer set search_path = '' as $fn$
begin
if prive.proprio(cle) is null then raise exception 'Clé invalide'; end if;
update public.retours set etat = e where id = n;
end $fn$;
revoke all on function public.claude_retours(text), public.claude_retour_etat(text, bigint, text) from public;
grant execute on function public.claude_retours(text), public.claude_retour_etat(text, bigint, text) to anon, authenticated;
