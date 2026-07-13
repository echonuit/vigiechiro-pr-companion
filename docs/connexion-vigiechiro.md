# Se connecter à Vigie-Chiro

L'application fonctionne **entièrement hors-ligne**, mais se connecter à votre compte Vigie-Chiro
débloque tout ce qui touche à la plateforme :

- la **synchronisation** de vos sites et points déclarés sur le portail (créés ou reliés
  localement, jamais écrasés) et du **référentiel officiel des taxons** ;
- le **dépôt** d'une nuit préparée, le **lancement de son traitement** par la plateforme et sa
  vérification (écran [Lot](ecrans/lot.md)) ;
- la **récupération des résultats Tadarida** après traitement serveur (écran
  [Validation](ecrans/validation.md)) ;
- les boutons **« Ouvrir sur Vigie-Chiro »** des écrans rattachés (site, passage, validation).

## Obtenir son jeton (méthode du marque-page)

La plateforme n'a pas de « mot de passe d'application » : on récupère le **jeton de session** du
navigateur, valable **14 jours**. La fenêtre **☰ → Se connecter à VigieChiro…** vous guide en trois
étapes, sans rien installer :

1. **Ouvrir la plateforme** : connectez-vous sur le portail Vigie-Chiro (compte GitHub ou Google).
2. **Installer le marque-page** (une seule fois) : le bouton **Copier le marque-page** met dans le
   presse-papiers un petit favori à créer dans votre navigateur (collez-le comme adresse du favori).
3. **Coller le jeton** : sur l'onglet Vigie-Chiro connecté, cliquez le marque-page (il copie votre
   jeton), puis collez-le dans le champ et **Se connecter**.

Une fois connecté, la fenêtre affiche votre **identité** (pseudo) et un **résumé de la
synchronisation** (par exemple « 385 taxons, 3 sites »). Le jeton est conservé localement dans votre
dossier de travail - jamais dans un dépôt git.

## Ce que déclenche la connexion

À chaque connexion réussie, l'application **synchronise** automatiquement :

- le **référentiel des taxons** (fusion prudente : les noms officiels complètent la base locale,
  rien n'est écrasé) ;
- vos **sites et points** : ceux qui existent sur le portail mais pas localement sont créés, les
  autres sont simplement reliés. Le badge d'état des cartes de
  [Mes sites](ecrans/sites.md) (« Enregistré » / « Verrouillé ») reflète ce rattachement.

Cette synchronisation est ensuite **rejouable à la demande**, sans se reconnecter, avec le bouton
**Synchroniser depuis VigieChiro** de l'écran Mes sites.

## Jeton expiré ou absent

Hors connexion (ou jeton expiré), l'application **se dégrade proprement** : les actions liées à la
plateforme sont grisées avec une explication, tout le reste fonctionne normalement. Rouvrez
simplement **☰ → Se connecter à VigieChiro…** et recollez un jeton frais.

Lors de la vérification d'un jeton, la modale distingue désormais les causes d'échec : **« Token
invalide ou expiré »** signifie que la plateforme a réellement refusé le jeton (recollez-en un frais),
tandis que **« VigieChiro est injoignable »** signale un problème de réseau : votre jeton n'est
peut-être pas en cause, vérifiez la connexion et réessayez. De même, l'import des observations, le
suivi du traitement et la synchronisation des sites indiquent la vraie raison d'un échec (plateforme
injoignable, refus du serveur) au lieu d'afficher « aucun résultat ».

!!! tip "En ligne de commande"
    Les commandes VigieChiro (`synchroniser-vigiechiro`, `deposer-vigiechiro`,
    `verifier-depot-vigiechiro`, `importer-vigiechiro`) acceptent un **jeton ponctuel** : option
    `--token`, ou variable d'environnement `VIGIECHIRO_TOKEN` (préférable : elle ne laisse pas le
    jeton dans l'historique du shell). À défaut, la connexion enregistrée dans l'application est
    utilisée.
