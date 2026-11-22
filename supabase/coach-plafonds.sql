-- Plafonds du coach par ancienneté de compte (audit de sécurité du 22 novembre 2026, point 5 ;
-- Philippe, 17 h 19 : « Oui pour 2 et 3 » à une variante du plafond global ; 17 h 22 : « Ok » aux chiffres 300 et 3 000).
-- Remplace coach_tick de coach-garde.sql. Avant : 1 500 messages par jour pour toute l'appli, puis coach bloqué pour tous.
-- Après :
--   - 40 messages par jour et par compte (inchangé, contrôlé par la fonction clever-service) ;
--   - comptes de moins de 7 jours : 300 messages par jour à se partager entre eux ; les comptes plus anciens n'y sont pas soumis,
--     donc une création de comptes en masse ne bloque plus les utilisateurs installés ;
--   - plafond de sécurité : 3 000 messages par jour pour toute l'appli (protège la facture).
-- Au-delà, coach_tick renvoie 999 et la fonction répond « quota » (« Tu as atteint la limite de messages du jour »).
-- À passer avec deploy-supabase.yml (sql = coach-plafonds.sql), avec l'accord de Philippe. Relançable sans risque.

create or replace function public.coach_tick() returns int
language plpgsql security definer set search_path = public as $$
declare k int; total int; total_nouveaux int; nouveau boolean;
begin
  if auth.uid() is null then raise exception 'non connecté'; end if;
  -- plafond de sécurité, toute l'appli
  select coalesce(sum(n), 0) into total from coach_usage where day = current_date;
  if total >= 3000 then return 999; end if;
  -- compte de moins de 7 jours : plafond commun aux nouveaux comptes
  select a.created_at > now() - interval '7 days' into nouveau from auth.users a where a.id = auth.uid();
  if coalesce(nouveau, true) then
    select coalesce(sum(u.n), 0) into total_nouveaux
      from coach_usage u join auth.users a on a.id = u.user_id
      where u.day = current_date and a.created_at > now() - interval '7 days';
    if total_nouveaux >= 300 then return 999; end if;
  end if;
  insert into coach_usage(user_id, day, n) values (auth.uid(), current_date, 1)
  on conflict (user_id, day) do update set n = coach_usage.n + 1
  returning n into k;
  return k;
end $$;
revoke all on function public.coach_tick() from public, anon;
grant execute on function public.coach_tick() to authenticated;
