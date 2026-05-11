# SAE 2.01 - VigieChiro PR Companion (édition 2026)

> Énoncé pédagogique de la SAE 2.01 du semestre 2 du BUT Informatique, IUT d'Aix-Marseille.
> SAE commune aux modules **R2.02 - Développement d'applications avec IHM** et **R2.03 - Qualité de développement**.

## Sommaire

1. [Présentation du projet](Présentation%20du%20projet.md)
2. [Contraintes techniques](Contraintes%20techniques.md)
3. [Objectifs qualités](Objectifs%20qualités.md)
4. [Expression du besoin](Expression%20du%20besoin.md)
5. [**Analyse et conception**](Analyse%20et%20conception/) (dossier fourni : personas, parcours, story mapping, périmètre MVP, planification)
6. [Jalons et livrables](Jalons%20et%20livrables.md)
7. [Calendrier de travail](Calendrier%20de%20travail.md)
8. [Consignes générales](Consignes%20générales.md)

> 📌 **SAE de développement** : la phase d'analyse et de conception est portée par l'équipe pédagogique. Vous recevez un dossier de spécification opérationnel ([`Analyse et conception/`](Analyse%20et%20conception/)) et vous concentrez votre énergie sur le développement (R2.02) et la qualité de la production (R2.03).

## Données d'exemple fournies

Un jeu de données réel issu d'une session de capture nocturne (22-23 avril 2026, PR n° 1925492) est mis à votre disposition pour tester votre application au fil du développement :

- `LogPR1925492.txt` - log technique du Passive Recorder
- `PaRecPR1925492_THLog.csv` - log température / humidité
- `wav/` - 1572 enregistrements ultrasons bruts
- `kal/` - 2114 fichiers redécoupés par Tadarida + 2 CSV d'observations classifiées

> 📦 Le jeu de données n'est **pas versionné dans ce dépôt** (volumétrie de plusieurs Go). Récupérez-le depuis l'archive `20260423-selected.zip` publiée dans la dernière [Release GitHub](../../releases) du dépôt (ou sur AmeTICE) et décompressez-le dans un dossier nommé `data/` à la racine de votre clone du brief :
>
> ```bash
> # depuis la racine de votre clone
> mkdir -p data
> unzip ~/Téléchargements/20260423-selected.zip -d data/
> ```
>
> Le dossier `data/` est listé dans le `.gitignore` : aucun risque de commit accidentel.
