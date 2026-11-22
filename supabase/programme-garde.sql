-- Audit de sécurité du 22 novembre 2026, point 9 (Philippe : « Travaille sur les points ouverts ») :
-- le programme ne disparaît plus pour tous si le compte qui l'a publié est supprimé.
-- Avant : programme.user_id … on delete cascade → supprimer le compte de Philippe effaçait toutes les versions du programme.
-- Après : la ligne reste, user_id passe à null. Sans effet sur l'appli (lecture publique inchangée) ni sur la clé Claude.
-- À passer une fois avec deploy-supabase.yml (sql = programme-garde.sql), avec l'accord de Philippe. Relançable sans risque.
alter table public.programme alter column user_id drop not null;
alter table public.programme drop constraint if exists programme_user_id_fkey;
alter table public.programme add constraint programme_user_id_fkey
  foreign key (user_id) references auth.users(id) on delete set null;
