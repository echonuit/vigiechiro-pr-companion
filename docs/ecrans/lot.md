# Lot

L'écran **Lot** (« Préparer le dépôt ») prépare et trace le **dépôt** d'une nuit vérifiée sur la
plateforme Vigie-Chiro. Le dépôt suit un **flux ordonné en quatre étapes**, rappelé en haut de l'écran
par un fil d'étapes (l'étape courante est mise en avant) :

**① Préparer · ② Générer les archives · ③ Téléverser · ④ Marquer déposé.**

## ① Vérifier et préparer le lot

![L'écran Lot : récapitulatif du lot et première étape « Vérifier et préparer le lot ».](../assets/captures/apercu-lot-preparer.png)

Le **récapitulatif** indique le nombre de séquences et le volume. « Vérifier et préparer le lot »
contrôle d'abord que la nuit est **complète et conforme** (transformation effectuée, fichiers bien
nommés, journal du capteur présent, relevé climatique), puis **verrouille** la liste des séquences qui
partiront. Vos fichiers d'origine ne sont pas modifiés. Le passage passe alors au statut « Prêt à
déposer ».

## ② Générer les archives de dépôt

![L'état « Prêt à déposer » : l'étape « Générer les archives » devient active.](../assets/captures/apercu-lot-deposer.png)

Ce que l'on téléverse sur Vigie-Chiro, ce sont des **archives ZIP** (≤ 700 Mo), découpées depuis les
séquences et écrites dans le sous-dossier `depot/` de la session. La génération peut être **longue**
sur une grosse nuit : elle s'exécute en arrière-plan, avec un indicateur d'activité, et les actions
sont neutralisées le temps de l'écriture (on ne risque pas de téléverser une archive incomplète).

![Génération des archives en cours : indicateur d'activité, actions désactivées.](../assets/captures/apercu-lot-generation.png)

## ③ Téléverser ces archives sur Vigie-Chiro

![Archives générées : la liste des ZIP s'affiche et « Ouvrir le dossier » s'active.](../assets/captures/apercu-lot-archives.png)

Une fois les archives produites, la liste des `.zip` s'affiche et le bouton **« Ouvrir le dossier »**
s'active : il ouvre directement le sous-dossier `depot/` dans le gestionnaire de fichiers, pour vous
amener au bon endroit. Le **téléversement est manuel** (hors application) : vous déposez ces archives
sur Vigie-Chiro depuis votre navigateur.

## ④ Marquer le passage déposé

![L'état « Déposé » : toutes les étapes sont franchies.](../assets/captures/apercu-lot-depose.png)

Une fois le téléversement terminé, « Marquer déposé » fait passer le passage au statut « Déposé » (ce
qui déverrouille ensuite la validation Tadarida) et trace la date du dépôt.

## Alertes de cohérence

Si la nuit n'est pas en état d'être déposée (par exemple séquences d'écoute absentes ou journal du
capteur manquant), l'écran liste des **alertes de cohérence** à corriger **avant** de préparer le
lot. Les boutons restent grisés tant que ces points ne sont pas résolus.

![L'état incohérent : des alertes de cohérence bloquent la préparation du lot.](../assets/captures/apercu-lot-alertes.png)
