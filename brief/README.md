# VigieChiro Companion · Brief projet

Dossier de **conception** de [VigieChiro Companion](https://companion.echonuit.fr/), le logiciel libre qui accompagne les naturalistes utilisant un enregistreur passif pour le protocole **Vigie-Chiro** : besoin, modèle de données, parcours utilisateurs, maquettes et story mapping.

C'est un document **vivant** : il décrit l'application telle qu'elle est réellement construite, et évolue avec elle.

## 📖 Lire le brief

👉 **Publié** : <https://brief.echonuit.fr/>

Il est conçu pour être lu en ligne. Les sections principales :

1. [Présentation du projet](https://brief.echonuit.fr/Pr%C3%A9sentation%20du%20projet/) - contexte Vigie-Chiro, objectifs, parties prenantes (dont Samuel Busson, client réel)
2. [Contraintes techniques](https://brief.echonuit.fr/Contraintes%20techniques/) - Java 25 / JavaFX 26 / JDBC SQLite, composants fournis
3. [Objectifs qualités](https://brief.echonuit.fr/Objectifs%20qualit%C3%A9s/) - 8 objectifs ISO 25010 et 4 scénarios
4. [Expression du besoin](https://brief.echonuit.fr/Expression%20du%20besoin/) - données disponibles, fonctionnalités attendues (MoSCoW)
5. [Analyse et conception](https://brief.echonuit.fr/Analyse%20et%20conception/) - modèle conceptuel, personas, parcours utilisateurs, story mapping, périmètre MVP, maquettes

## 📦 Jeux de données

- 🟢 **Échantillon d'une nuit de capture** : jeu de données réduit (audio + observations complètes) versionné dans le dépôt [`vigiechiro-pr-companion-exemple-nuit`](https://github.com/echonuit/vigiechiro-pr-companion-exemple-nuit). Disponible immédiatement après `git clone`, suffisant pour développer et tester la chaîne fil rouge.
- 🔵 **Jeu complet** (~4,2 Go compressés / ~11 Go décompressés) archivé sur Zenodo, DOI [10.5281/zenodo.20492247](https://doi.org/10.5281/zenodo.20492247), à décompresser dans le dossier `data/` du projet (ignoré par git). C'est lui qui permet de valider les objectifs de volumétrie ([O3](https://brief.echonuit.fr/Objectifs%20qualit%C3%A9s/Objectifs%20qualit%C3%A9s/O3/), [O5](https://brief.echonuit.fr/Objectifs%20qualit%C3%A9s/Objectifs%20qualit%C3%A9s/O5/)).

## 🔗 Les autres sites du projet

| | |
|---|---|
| **Le produit** | [companion.echonuit.fr](https://companion.echonuit.fr/) - documentation utilisateur |
| **Le code** | [companion-dev.echonuit.fr](https://companion-dev.echonuit.fr/) - documentation développeur, et le dépôt [`echonuit/vigiechiro-pr-companion`](https://github.com/echonuit/vigiechiro-pr-companion) |

<details>
<summary>🔧 Pour les mainteneurs du brief</summary>

Les sources du brief vivent dans le dépôt du produit, sous `brief/`, aux côtés de `docs/` (guide utilisateur) et `dev-docs/` (documentation développeur). Une conception qui bouge en même temps que le produit ne demande donc plus deux mises à jour dans deux dépôts. Les 129 commits d'origine ont été conservés : `git log -- brief/` les montre tous.

### Construire le site localement

Depuis la racine du dépôt :

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r docs/requirements.txt
mkdocs serve -f mkdocs-brief.yml  # http://localhost:8000
```

### Publication

Tout push sur `main` touchant `brief/` ou `mkdocs-brief.yml` déclenche [`docs.yml`](../.github/workflows/docs.yml), qui construit les trois sites et pousse celui-ci vers le dépôt `echonuit/brief`, servi sur `brief.echonuit.fr`. Ce dépôt ne contient plus que le site construit, comme `companion` et `companion-dev`.

</details>
