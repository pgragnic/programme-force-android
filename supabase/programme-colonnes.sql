-- Audit de sécurité du 22 novembre 2026, point 9 : le programme reste lisible par tous, mais plus la colonne user_id
-- (identifiant du compte de Philippe). Seules id, data, note et created_at sont lisibles depuis l'appli.
-- ATTENTION : à ne passer qu'une fois tous les utilisateurs sur une version de l'appli qui lit ces colonnes nommément
-- (Store.pullProg, à partir de la version qui suit la 1.2.3). Les versions antérieures demandent « toutes les colonnes » :
-- elles ne recevraient plus les mises à jour du programme (elles garderaient celui livré avec l'appli, sans plantage).
revoke select on public.programme from anon, authenticated;
grant select (id, data, note, created_at) on public.programme to anon, authenticated;
