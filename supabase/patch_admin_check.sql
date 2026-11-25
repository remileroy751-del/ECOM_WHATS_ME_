-- Correctif de sécurité : à exécuter UNE FOIS dans Supabase > SQL Editor > Run
-- si tu as déjà exécuté schema.sql avant cette mise à jour.
-- Retire l'accès public direct à admin_check (vérification du mot de passe),
-- qui permettait de tester des mots de passe en boucle depuis l'extérieur.
-- Les fonctions admin_* continuent de fonctionner normalement : elles
-- l'utilisent en interne, ce qui reste autorisé.

revoke all on function admin_check(text) from anon, authenticated, public;
