# Léa Tran - 24 ans, doctorante en écologie acoustique

> 👩‍🔬 « Mon corpus c'est 800 nuits de capture, 18 sites, 3 ans. Si je perds une seule annotation à cause d'un bug, je m'arrache un cheveu. Et il m'en reste pas tant que ça. »

## Identité

| | |
|---|---|
| **Âge** | 24 ans |
| **Lieu de vie** | Studio à Montpellier, financée par une bourse ANR |
| **Statut** | Doctorante en 2e année au CEFE / CNRS, sujet « Réponse comportementale des chiroptères aux corridors écologiques agricoles » |
| **Formation** | Master Bio-Info + Master EFCE |
| **Matériel** | MacBook Pro perso + station de travail Linux au labo + cluster HPC pour les gros calculs |

## Quotidien VigieChiro

Léa **n'est pas une utilisatrice VigieChiro grand public** : elle utilise les **mêmes formats** (PR + Tadarida + CSV), mais sur un protocole de recherche qui lui est propre, déployé sur **18 sites contrôlés** suivis sur **3 saisons**. Soit, à la fin de sa thèse, **un corpus de l'ordre de 800 nuits de capture**.

Elle traite ses données par lots de plusieurs dizaines de nuits, sur des fenêtres de plusieurs jours, en alternant l'application et ses scripts R. Elle a ses propres pipelines de pré-traitement et veut que l'application **n'écrase rien** de ce qu'elle a fait à la main.

## Compétences techniques

- **Maîtrise** : R, Python, Bash, Git, SQL, Linux serveur, scripts d'analyse audio, traitement du signal.
- **Acceptable** : Java (a fait du dev en M1), JavaFX (jamais utilisé mais saurait lire un FXML).
- **Préfère éviter** : développer ses propres GUI (« je préfère un bon script à une mauvaise interface »).

## Ce qu'elle attend de l'application

- Un **format d'export propre, documenté, reproductible** : si elle relance l'export, elle doit obtenir le même fichier au bit près.
- L'**intégrité totale** des annotations : aucune action de l'application ne doit modifier ou écraser ses validations sans son consentement explicite.
- Pouvoir **annoter une session** avec un commentaire libre suffisamment long (méthodo, anomalie matérielle, hypothèse de travail).
- Pouvoir **comparer deux sessions** ou deux observations côte à côte (par exemple un évènement *Pippip* douteux à côté d'un *Pippip* certain).
- Que la base de données soit **lisible et requêtable** par-dessus l'application (SQLite / H2 ouverts à un client externe pour ses propres analyses R).
- **Pas d'auto-update**, pas de télémétrie, pas d'envoi de rapports d'erreur sans lui demander.

## Ce qui la décourage

- Les outils qui s'imposent comme **autorité unique** sur les données (« propriétaire » du fichier).
- Les changements de format silencieux entre versions.
- Les bases de données opaques où elle ne peut pas faire un `SELECT` sans passer par l'IHM.
- Les modales bloquantes qui interrompent son flux de travail.

## Métriques de succès

- Léa peut **scripter** une partie de son flux (import en CLI, export en CLI) tout en utilisant l'IHM pour la validation manuelle.
- Sa base SQLite reste **interrogeable** depuis R / DBeaver sans que l'application ne s'en plaigne.
- Aucune perte d'annotation sur 6 mois d'utilisation intensive.
