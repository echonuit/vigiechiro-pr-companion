# P15 - Relire l'histoire d'une nuit 🕰

[← Retour au sommaire des parcours](index.md) · **Section B - Chaîne de production**

!!! warning "Cible non livrée"
    Ce parcours décrit un chantier ouvert, pas l'application d'aujourd'hui. Son état se lit sur
    l'issue **#3846**, qui se ferme quand il est livré : c'est la source de vérité, pas cette phrase.

> **Persona principal** : Karim (diagnostiquer après coup) et Samuel (rendre compte de ce qui a été
> fait à la donnée). **Objectifs qualité visés** : imputabilité, intégrité des annotations.

Samuel reprend une nuit de mai qu'il n'a pas touchée depuis deux mois. Le passage est marqué
« Déposé », mais son inventaire est vide. Il ne sait plus s'il a lancé le calcul, s'il l'a lancé et
que rien n'est redescendu, ou s'il a importé les résultats puis supprimé quelque chose.

La fiche du passage lui donne son **état courant**, et rien d'autre. Les dates de dépôt, les
compteurs d'import, les verdicts de qualification et les plans de dépôt sont pourtant tous en base :
ils servent à calculer cet état, puis ne sont plus consultés.

1. Depuis la fiche du passage, Samuel ouvre « **Journal** ».
2. Il lit une **frise antéchronologique** : un évènement par ligne, du plus récent au plus ancien.
   Import, transformation, verdict de qualification, préparation, dépôt, déclenchement du calcul,
   import d'observations, publication de corrections, archivage, réactivation.
3. Chaque ligne porte sa date et un **résumé chiffré** formaté selon le type : « 542 séquences
   transformées », « verdict : exploitable », « 3 archives sur 3 téléversées ».
4. Il filtre sur les évènements de dépôt et voit que le calcul n'a jamais été déclenché. La question
   est réglée en dix secondes, sans reconstituer quoi que ce soit de mémoire.
5. La frise est **consultable seulement** : aucune action ne s'y prend. C'est une lecture de ce qui
   s'est passé, pas un poste de pilotage.

En **ligne de commande**, `vigiechiro statut-passage <id>` expose le même journal.

## Ce que le traitement en lot a rendu nécessaire

Une action groupée sur six nuits rend six lignes de résultat, affichées une fois puis jetées à la
fermeture de la fenêtre. C'est la première fonctionnalité du produit qui **fabrique de l'histoire
sans la conserver**.

Le journal est ce qui la rattrape : le compte rendu d'un lot devient consultable nuit par nuit, après
coup, y compris quand le lot a été interrompu.

## La question ouverte

Ce qui vaut un évènement doit être écrit avant d'écrire la table. Tout geste de l'utilisateur, toute
écriture en base, ou seulement ce qui fait avancer le passage dans son cycle ? Sans critère, la table
se remplit de bruit, ou rate ce qui compte. Les passages déjà en base n'ont par ailleurs pas
d'historique : soit on reconstitue ce qu'on sait dériver des dates stockées en le marquant comme tel,
soit la frise commence au jour de la migration, et l'écran doit le dire.
