-- Coach UpRep (Claude) : quota de messages par jour et par compte.
-- À coller une fois dans Supabase → SQL Editor → Run.
create table if not exists public.coach_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null default current_date,
  n int not null default 0,
  primary key (user_id, day)
);
alter table public.coach_usage enable row level security;
-- aucune politique : la table n'est lisible ni modifiable depuis l'appli, seulement par la fonction ci-dessous

create or replace function public.coach_tick() returns int
language plpgsql security definer set search_path = public as $$
declare k int;
begin
  if auth.uid() is null then raise exception 'non connecté'; end if;
  insert into coach_usage(user_id, day, n) values (auth.uid(), current_date, 1)
  on conflict (user_id, day) do update set n = coach_usage.n + 1
  returning n into k;
  return k;
end $$;
revoke all on function public.coach_tick() from public, anon;
grant execute on function public.coach_tick() to authenticated;
