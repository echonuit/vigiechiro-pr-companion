# Lot

L'écran **Lot** (« Préparer le dépôt ») prépare et trace le **dépôt** d'une nuit vérifiée sur la
plateforme Vigie-Chiro. Le dépôt se fait en trois temps.

## Préparer, téléverser, marquer déposé

![L'écran Lot : récapitulatif du lot et étapes du dépôt.](../assets/captures/apercu-lot-preparer.png)

Le **récapitulatif** indique le nombre de séquences, le volume et le **dossier à téléverser**
manuellement sur Vigie-Chiro. Les trois étapes sont :

1. **Préparer le lot** : l'application contrôle la cohérence du passage et le prépare pour le dépôt
   (statut « Prêt à déposer »). Le chemin du **dossier à téléverser** est indiqué dans le
   récapitulatif.
2. **Téléverser** ce dossier **manuellement** sur Vigie-Chiro, depuis votre navigateur.
3. **Marquer déposé** une fois le téléversement terminé.

## Une fois le lot prêt

Quand le lot a été préparé, le bouton **Marquer déposé** devient actif : un clic fait passer le
passage au statut « Déposé » (ce qui déverrouille ensuite la validation Tadarida).

![L'état « Prêt à déposer » : le bouton « Marquer déposé » est actif.](../assets/captures/apercu-lot-deposer.png)

## Alertes de cohérence

Si la nuit n'est pas en état d'être déposée (par exemple séquences d'écoute absentes ou journal du
capteur manquant), l'écran liste des **alertes de cohérence** à corriger **avant** de préparer le
lot. Les boutons restent grisés tant que ces points ne sont pas résolus.

![L'état incohérent : des alertes de cohérence bloquent la préparation du lot.](../assets/captures/apercu-lot-alertes.png)
