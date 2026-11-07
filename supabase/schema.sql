-- Programme Force : tables Supabase. À coller dans SQL Editor → New query → Run.
-- Chaque ligne appartient à un compte ; seul ce compte peut la lire ou la modifier.

create table if not exists public.sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  d bigint not null,
  data jsonb not null,
  created_at timestamptz not null default now()
);
create index if not exists sessions_user_d on public.sessions (user_id, d);

create table if not exists public.days (
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  date text not null,
  data jsonb not null,
  primary key (user_id, date)
);

create table if not exists public.state (
  user_id uuid primary key default auth.uid() references auth.users on delete cascade,
  data jsonb not null
);

create table if not exists public.bilans (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users on delete cascade,
  data jsonb not null,
  created_at timestamptz not null default now()
);

alter table public.sessions enable row level security;
alter table public.days enable row level security;
alter table public.state enable row level security;
alter table public.bilans enable row level security;

do $$ declare t text; begin
  foreach t in array array['sessions','days','state','bilans'] loop
    execute format('drop policy if exists "proprietaire" on public.%I', t);
    execute format('create policy "proprietaire" on public.%I for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid())', t);
  end loop;
end $$;
