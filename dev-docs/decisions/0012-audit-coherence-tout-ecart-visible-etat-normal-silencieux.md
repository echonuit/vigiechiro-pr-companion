# ADR 0012 — L'audit de cohérence rend tout écart visible, mais un état normal ne crie pas

- **Statut** : Accepté — rétroactif
- **Chantier** : EPIC #1154 (cohérence disque ↔ base ↔ serveur)

## Contexte

Trois représentations d'une nuit coexistent : les **fichiers** sur le disque, la **base** locale, et le **serveur** VigieChiro. Rien ne garantissait qu'elles restent en correspondance - surtout au **renommage** d'un passage (changement d'année ou de numéro → re-préfixage de tous les fichiers). Il fallait pouvoir **auditer** ces correspondances. Mais un audit qui hurle au moindre écart est aussi inutile qu'un audit muet : s'il traite un utilisateur qui a **volontairement** libéré de la place comme s'il avait **corrompu** ses données, on cesse de l'écouter.

## Décision

- **Audit en lecture seule d'abord** : voir les divergences (disque/base, puis base/serveur) avant d'agir.
- **La sévérité d'un constat reflète la *nature* de l'écart, pas seulement sa présence.** Un fait **voulu** est une **INFO** (audio `AUDIO_ARCHIVE` d'un passage archivé, originaux `originals_purged_at`) ; seul un fait **anormal** est une **ERREUR** (`DISQUE_MANQUANT` d'un fichier qui devrait être là). L'audit **distingue archivé (INFO) de corrompu (ERREUR)**.
- **La réciproque est un critère de recette** : sur un workspace sain, l'audit ne doit produire **aucune** ERREUR. Un audit qui crie sur un état normal ne vaut rien.

## Conséquences

- L'utilisateur distingue « tout va bien » de « il y a un vrai problème » : le signal reste **crédible**.
- Les états **voulus** (archivage, purge des bruts) sont des faits attendus, pas des alarmes - c'est la généralisation de « supprimé exprès n'est pas cassé » ([ADR 0005](0005-reactivation-cascade-de-preuves-archive-etat-observe.md)).
- Les tests E2E figent la réciproque : un parcours de restauration se termine sur un audit **sans ERREUR**.

## Alternatives écartées

- **Signaler tout écart au même niveau.** Noie les vrais problèmes sous les faits voulus ; l'audit devient du bruit.
- **Ne rien signaler tant que ça « marche ».** Laisse les vraies divergences (renommage incohérent, fichier réellement manquant) invisibles jusqu'à la perte.
