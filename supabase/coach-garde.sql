-- Garde-fous avant l'ouverture à tous (Philippe, 18 novembre 23 h 15 : « Que pourrait-on encore améliorer avant déploiement ? » ; 23 h 18 : « Yes start »).
-- 1. Plafond global du coach : au-delà de 1 500 messages par jour pour toute l'appli, coach_tick renvoie 999
--    (la fonction clever-service répond alors « quota » : « Tu as atteint la limite de messages du jour »). Protège la facture
--    si quelqu'un crée des comptes en masse ; le quota de 40 par compte reste inchangé.
-- 2. coach_data.updated_at mis à jour à chaque modification (il gardait la date de création).
-- Passé par Claude avec deploy-supabase.yml, avec l'accord de Philippe.

create or replace function public.coach_tick() returns int
language plpgsql security definer set search_path = public as $$
declare k int; total int;
begin
  if auth.uid() is null then raise exception 'non connecté'; end if;
  select coalesce(sum(n), 0) into total from coach_usage where day = current_date;
  if total >= 1500 then return 999; end if;
  insert into coach_usage(user_id, day, n) values (auth.uid(), current_date, 1)
  on conflict (user_id, day) do update set n = coach_usage.n + 1
  returning n into k;
  return k;
end $$;
revoke all on function public.coach_tick() from public, anon;
grant execute on function public.coach_tick() to authenticated;

create or replace function public.touch_updated_at() returns trigger
language plpgsql set search_path = public as $$
begin new.updated_at := now(); return new; end $$;
drop trigger if exists coach_data_touch on public.coach_data;
create trigger coach_data_touch before update on public.coach_data for each row execute function public.touch_updated_at();
