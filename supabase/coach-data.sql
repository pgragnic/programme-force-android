-- Onglet Coach (15 novembre 2026) : discussion, journal des décisions, bilans, objectifs et préférences du coach,
-- sauvegardés avec le compte pour les retrouver sur un autre téléphone.
-- À coller une fois dans Supabase → SQL Editor → Run.
create table if not exists public.coach_data (
  user_id uuid primary key default auth.uid() references auth.users on delete cascade,
  data jsonb not null,
  updated_at timestamptz not null default now()
);
alter table public.coach_data enable row level security;
drop policy if exists "proprietaire" on public.coach_data;
create policy "proprietaire" on public.coach_data for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
