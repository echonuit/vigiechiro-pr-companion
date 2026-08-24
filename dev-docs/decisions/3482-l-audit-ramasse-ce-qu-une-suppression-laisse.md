---
type: adr
title: "L'audit ramasse ce qu'une suppression laisse, et une confirmation dit aussi ce qu'elle ne fait pas"
status: stable
article: A12
chantier: "#3482, relevé au test manuel de la campagne de recette 2 (#3424)"
decided_at: 2026-08-08
verification: certaine
enforced_by:
  - "AuditRetraitOrphelinsViewTest#accepter_retire_et_rend_compte"
verified:
  - by: machine:ci
    at: 2026-08-08
---

# L'audit ramasse ce qu'une suppression laisse, et une confirmation dit aussi ce qu'elle ne fait pas

## Contexte

Supprimer un passage retire sa ligne en base et **laisse son dossier de session sur le disque**. Ce n'est
pas un oubli : `ServicePassage.supprimer` l'écrit noir sur blanc - « les fichiers du workspace (bruts,
transformés) ne sont pas touchés, comme pour `ServiceSites.supprimerSite` : seule la base est nettoyée ».

Et la décision se défend. L'audio est ce que l'application ne sait **pas** reconstituer : un `DELETE` en
base se rattrape par un réimport tant que les WAV sont là, l'inverse jamais. Cette ADR ne revient pas
dessus.

Restaient deux conséquences que personne ne prenait en charge.

**La confirmation annonçait plus qu'elle ne faisait.** La modale demandait « supprimer définitivement ce
passage et **toute sa nuit** (séquences, relevés) ? ». Un utilisateur en conclut que ses fichiers partent.
Ils restent. La CLI, elle, disait déjà la vérité - `supprimer-passage` imprime « Les fichiers restent sur
le disque : `<chemin>` » - et le doc-comment de `SupprimerPassage` affirmait que les deux surfaces étaient
alignées « pour que les deux surfaces ne racontent pas deux histoires ». Elles en racontaient deux.

**Rien ne ramassait derrière.** Le dossier orphelin ne figurait dans aucun inventaire, n'était proposé à
aucun ménage, et pèse **plusieurs Go par nuit**. Sur un disque déjà contraint, c'est l'accumulation
invisible qui finit par bloquer un import - le mur qu'un utilisateur a rencontré en vrai : « espace disque
insuffisant pour décompresser : besoin d'environ 10,1 Go, seulement 9,5 Go disponibles » (#3459).

L'écran d'audit **voyait** pourtant déjà ces dossiers : `BalayageDisque.dossiersOrphelins` produit un
constat `DOSSIER_ORPHELIN` pour chaque dossier de session sans `recording_session` correspondante. Il le
signalait, et s'arrêtait là - conformément à une posture assumée ailleurs dans le produit, où
`lister-sauvegardes` déclare « elle **observe** : elle ne purge rien et ne conseille rien ».

## Décision

**Trois choses, dont une seule est nouvelle.**

1. **La cascade s'arrête toujours à la base.** Supprimer un passage ou un site ne touche pas aux fichiers.
   Inchangé, et pour la même raison qu'avant : l'audio est irremplaçable.

2. **La confirmation dit ce qu'elle ne fait pas.** La modale de suppression porte désormais un constat
   « les fichiers audio restent sur le disque », avec **leur emplacement** et l'indication de l'endroit où
   les retirer. Une confirmation destructive doit délimiter la destruction dans les deux sens : ce qui
   part, et ce qui reste.

3. **L'audit gagne une action, la seule qui écrive.** Un bouton « Retirer N dossier(s) orphelin(s) »
   supprime du disque les dossiers de session qu'aucun passage ne réclame.

### Pourquoi l'audit, et pas ailleurs

Parce que **décider qu'un dossier est orphelin est exactement le calcul de l'audit** : il faut confronter
le disque à la base. Placer l'action dans un menu de maintenance obligerait ce menu à refaire l'audit pour
savoir sur quoi agir. Ici, l'action porte sur des constats **déjà produits et déjà affichés** : ce que
l'utilisateur retire est ce qu'il vient de lire.

### La frontière qui empêche que ce soit une dérive

L'audit reste **observationnel partout ailleurs**, et cette action ne l'ouvre pas à la réparation en
général. Elle est bornée par trois contraintes, toutes tenues par des tests :

- elle n'agit que sur des constats de catégorie `DOSSIER_ORPHELIN`. Le champ `cible` d'un constat porte
  tantôt un dossier, tantôt un **fichier** (`DISQUE_MANQUANT` cite un `.wav`) : élargir la sélection
  effacerait le dossier d'un passage vivant ;
- elle **chiffre la perte avant de la demander** - nombre de dossiers, leurs noms, et la place regagnée.
  « Retirer 3 dossiers » et « retirer 3 dossiers, 42 Go » n'appellent pas la même réponse ;
- elle **rend compte de ce qui s'est produit**, pas de ce qu'on espérait. Un dossier resté en place - cas
  courant sous Windows, où l'explorateur verrouille - fait basculer le bandeau en avertissement et se
  nomme.

Ce dernier point vaut d'être écrit : le helper de suppression récursive qui existait déjà
(`ExtracteurZip.supprimerRecursivement`) est **best-effort et silencieux**, ce qui est juste pour un
dossier temporaire et faux pour des données d'utilisateur. D'où un service distinct, qui mesure avant,
supprime, **vérifie après**, et ne compte pour libéré que ce qui a effectivement disparu.

## Conséquences

- Le jumeau est couvert sans travail supplémentaire. `ServiceSites.supprimerSite` a le même comportement,
  et le balayage ne regarde pas **quelle** suppression a laissé un dossier : il compare la racine du
  workspace à la base. Un dossier laissé par la suppression d'un site apparaît donc au même inventaire.
- L'audit a désormais **une** action qui écrit. Le prochain qui lira « l'audit observe » dans la fiche de
  l'écran doit trouver ici la raison, faute de quoi il conclura à une dérive - ou en ajoutera une seconde
  par imitation, ce que cette ADR n'autorise pas.
- La CLI n'a **pas** d'équivalent pour l'instant. La parité de surface (ADR 2294) n'est pas rompue par une
  action absente d'un côté, elle le serait par deux comportements différents ; la question reste ouverte
  et se posera à la clôture.

## Alternatives écartées

- **Une case « supprimer aussi les fichiers » dans la confirmation.** C'est le pire moment pour demander
  un arbitrage de rangement : juste avant un geste irréversible qu'on vient de faire confirmer. La modale
  est déjà lourde, et la réponse par défaut à une case cochable en situation d'inquiétude n'est pas une
  décision.
- **Supprimer les fichiers avec le passage.** Revient à détruire l'irremplaçable pour économiser un
  ménage. La base se rejoue, l'audio non.
- **Un ménage automatique.** Le produit ne détruit rien qu'on ne lui ait demandé. Un nettoyage qui se
  déclenche seul finirait par emporter la nuit qu'un utilisateur croyait avoir mise de côté.
- **La ligne de commande seulement.** L'utilisateur qui a rencontré le mur du disque plein travaille sous
  Windows, dans l'interface graphique. Une réponse qui suppose un terminal n'en est pas une pour lui.
