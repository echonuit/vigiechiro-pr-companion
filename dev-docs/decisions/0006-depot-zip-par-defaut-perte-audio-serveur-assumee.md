# ADR 0006 — Le dépôt par ZIP est le mode par défaut ; la perte de l'audio serveur est assumée

- **Statut** : Accepté — rétroactif
- **Chantier** : spike #984 (ZIP vs WAV) ; EPIC #1297 (décision produit)
- **Vérification** : humaine — le ZIP comme mode par défaut et la perte assumée de l'audio serveur sont un arbitrage produit, non un invariant du code

## Contexte

VigieChiro accepte deux façons de déposer une nuit : téléverser les **WAV** un à un, ou pousser un **ZIP** de la nuit. Le ZIP est nettement plus **rapide** (un transfert au lieu de centaines). Mais un doute persistait depuis des semaines : le serveur **conserve-t-il** l'audio d'un dépôt ZIP, récupérable plus tard ?

Le spike #984 (2026-07-13) a **tranché**, code de `Scille/vigiechiro-api` lu ligne à ligne **et** sonde live : en dépôt ZIP, le traitement recopie les WAV dans un cache interne (`TASK_PARTICIPATION_DATASTORE_CACHE`) **hors S3 et hors API**. Les documents produits n'ont **aucun `s3_id`** : l'audio n'est **pas** récupérable par le client. En dépôt WAV, chaque fichier a son `s3_id` et reste ré-téléchargeable.

## Décision

Le **ZIP reste le mode de dépôt par défaut** (on garde l'acquis de vitesse). Sa contrepartie - l'audio serveur non récupérable - est **assumée** et **matérialisée** produit : un passage dont l'audio n'est plus là devient un **passage archivé** (consultable, non écoutable), qui **redevient actif** en réimportant les fichiers d'origine (cf. [ADR 0005](0005-reactivation-cascade-de-preuves-archive-etat-observe.md)).

## Conséquences

- Plutôt que de **promettre une restauration qu'on ne sait pas faire**, on rend la perte **navigable et réversible par l'utilisateur** (réimport). Cela clôt l'arbitrage #1244.
- La **carte SD de l'utilisateur** devient l'unique source de récupération de l'audio après un dépôt ZIP - ce qui motive toute la chaîne de réactivation « depuis les bruts » (EPIC #1653).
- La suite de **contrat live** garde une sonde (`probe_zip_vs_wav`) : si elle rougit, c'est que le dépôt par défaut est cassé - pas un signal de « revenir au WAV ».

## Alternatives écartées

- **Repasser au dépôt WAV par défaut** pour garder l'audio serveur. Perd la vitesse du ZIP pour un bénéfice (re-téléchargement) que la récupération locale couvre déjà.
- **Promettre une restauration serveur.** Les WAV existent côté serveur mais hors S3/API : seul un administrateur MNHN les atteint. Promettre l'inatteignable serait mentir.
