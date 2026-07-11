# Lot

L'écran **Lot** (« Préparer le dépôt ») prépare et trace le **dépôt** d'une nuit vérifiée sur la
plateforme Vigie-Chiro. Le dépôt suit un **flux ordonné en quatre étapes**, rappelé en haut de l'écran
par un fil d'étapes (l'étape courante est mise en avant) :

**① Préparer · ② Générer les archives · ③ Téléverser · ④ Marquer déposé.**

## ① Vérifier et préparer le lot

![L'écran Lot : récapitulatif du lot et première étape « Vérifier et préparer le lot ».](../assets/captures/apercu-lot-preparer.png)

Le **récapitulatif** indique le nombre de séquences et le volume. Une **checklist de cohérence** montre,
contrôle par contrôle et **même quand tout est satisfait**, ce qui est vérifié : transformation
effectuée, fichiers bien nommés, journal du capteur présent, relevé climatique. Chaque ligne est marquée
**✓** (satisfait), **✗** (à corriger, bloquant) ou **⚠** (avertissement non bloquant, comme un relevé
climatique absent). « Vérifier et préparer le lot » **verrouille** ensuite la liste des séquences qui
partiront. Vos fichiers d'origine ne sont pas modifiés. Le passage passe alors au statut « Prêt à
déposer ».

## ② Générer les archives de dépôt

![L'état « Prêt à déposer » : l'étape « Générer les archives » devient active.](../assets/captures/apercu-lot-deposer.png)

Ce que l'on téléverse sur Vigie-Chiro, ce sont des **archives ZIP** (≤ 700 Mo), découpées depuis les
séquences et écrites dans le sous-dossier `depot/` de la session. La génération peut être **longue**
sur une grosse nuit : elle s'exécute en arrière-plan, avec un indicateur d'activité, et les actions
sont neutralisées le temps de l'écriture (on ne risque pas de téléverser une archive incomplète).

![Génération des archives en cours : indicateur d'activité, actions désactivées.](../assets/captures/apercu-lot-generation.png)

Le tableau de suivi des archives laisse **choisir et réordonner ses colonnes** (clic droit ou menu ☰
« outils ») : voir [Personnaliser les tableaux](../personnaliser-les-tableaux.md).

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

## Checklist de cohérence : ce qui bloque

Si la nuit n'est pas en état d'être déposée (par exemple séquences d'écoute absentes ou journal du
capteur manquant), les contrôles concernés passent en **✗** dans la checklist, avec la raison et la
correction à apporter. Le bouton « Vérifier et préparer le lot » reste grisé tant qu'un contrôle est en
échec. Un **⚠** (relevé climatique absent) n'empêche pas, lui, de préparer le lot.

![L'état incohérent : la checklist montre les contrôles ✓ et ✗ ; la préparation est bloquée.](../assets/captures/apercu-lot-alertes.png)
