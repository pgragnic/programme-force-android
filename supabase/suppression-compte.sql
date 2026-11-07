-- Programme Force : suppression de compte par l'utilisateur lui-même (exigence Google Play).
-- À coller dans SQL Editor → New query → Run (une seule fois).
-- Efface le compte connecté ; ses séances, journées, réglages, bilans et programmes partent avec (on delete cascade).
create or replace function public.supprimer_mon_compte() returns void
language plpgsql security definer set search_path = '' as $fn$
begin
  if auth.uid() is null then raise exception 'Non connecté'; end if;
  delete from auth.users where id = auth.uid();
end $fn$;
revoke all on function public.supprimer_mon_compte() from public, anon;
grant execute on function public.supprimer_mon_compte() to authenticated;
