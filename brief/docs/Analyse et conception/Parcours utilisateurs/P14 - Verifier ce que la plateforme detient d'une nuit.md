# P14 - Vérifier ce que la plateforme détient d'une nuit 🛰

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

!!! warning "Cible non livrée"
    Ce parcours décrit un chantier ouvert, pas l'application d'aujourd'hui. Son état se lit sur
    l'issue **#3845**, qui se ferme quand il est livré : c'est la source de vérité, pas cette phrase.

> **Persona principal** : Karim (une nuit d'un lot a échoué, il veut savoir laquelle et pourquoi) et
> Marie (« est-ce que ma nuit est bien arrivée ? »). **Objectifs qualité visés** : imputabilité,
> tolérance aux erreurs.

Karim a lancé un téléversement sur six nuits d'un geste (parcours
[P5](P5%20-%20Naviguer%20dans%20plusieurs%20sites%20et%20passages.md)). Le compte rendu du lot annonce
cinq nuits faites et une en échec. Il ouvre la fiche de celle qui a échoué, et il n'y trouve que son
statut local : « Dépôt en cours ». Rien ne lui dit ce que la plateforme, elle, détient.

Aujourd'hui il lui reste deux chemins, et aucun n'est à la maille de sa question :
l'[audit de cohérence](../Maquettes/M-Audit.md), qui balaie toute l'installation et rend une liste de
constats triés par sévérité, ou le portail Vigie-Chiro dans un navigateur.

1. Depuis la fiche du passage, Karim ouvre « **Relever l'état sur la plateforme** ». L'application
   interroge Vigie-Chiro pour cette participation seulement.
2. Elle rend un **état comparé** : ce que le serveur détient, ce que la base locale affirme, et la
   liste des écarts, chacun nommé. Rien n'est écrit à cette étape.
3. Chaque écart porte sa **catégorie** : réparable ici, à faire sur le portail, ou constat sans
   réparation possible. Un bouton qui échouerait à coup sûr n'est pas proposé.
4. Karim déclenche les réparations qu'il veut, **une par une et sous confirmation** : rapatrier des
   résultats disponibles, corriger un indicateur local que le serveur dément.
5. Un jeton expiré donne un message qui le nomme, et **jamais** un nouveau téléversement : c'est le
   piège classique de ce genre d'écran, où l'absence de réponse serveur se lit comme une absence de
   données.

En **ligne de commande**, `vigiechiro audit-coherence --passage <id>` rend le même relevé, avec
`--json` pour un script.

## Le vocabulaire qui fait la différence

Un fichier absent du disque mais présent sur le serveur n'est **pas** un fichier manquant : c'est un
fichier purgé localement, état normal après un dépôt réussi. Le relevé dit « sur serveur seul ».

Sans cette distinction, l'écran alarmerait sur toutes les nuits archivées, c'est-à-dire sur les nuits
dont tout s'est bien passé. Un outil de diagnostic qui crie au loup sur le cas nominal cesse d'être lu.

## Frontière avec l'audit de cohérence

Les deux répondent à des questions voisines à deux échelles. L'[audit](../Maquettes/M-Audit.md) part
de l'installation et cherche les anomalies ; ce parcours part d'**une nuit** et demande son état.
Faire cohabiter deux jeux de contrôles qui divergeraient serait pire que de n'en avoir qu'un : la
décision d'architecture attendue au chantier tranche entre réutiliser les mêmes contrôles ou écrire
la différence de périmètre.
