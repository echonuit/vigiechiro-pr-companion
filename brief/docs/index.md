# VigieChiro Companion - Brief projet

<div markdown="0" style="margin: 1rem 0 2rem 0;">
  <img src="assets/illustrations/hero-bandeau.webp"
       alt="Nuit de session d'enregistrement acoustique : un Passive Recorder sur son piquet, des chauves-souris en vol émettant des ondes d'écholocation sous une pleine lune en lisière de forêt"
       style="width: 100%; height: auto; display: block; border-radius: 4px;">
</div>

> **Dossier de conception** et documentation produit de *VigieChiro Companion*, le logiciel open
> source qui accompagne les naturalistes utilisant un **Passive Recorder** pour le protocole Vigie-Chiro.
> Né d'une commande réelle de Samuel Busson (CEREMA).

!!! info "Un brief aligné sur le produit"
    Ce dossier décrit l'application **telle qu'elle est construite** : le besoin, le modèle de données,
    les parcours utilisateurs et les écrans réels. C'est un document de conception **vivant**, qui évolue
    avec le produit - pas un énoncé figé. Le code est public :
    [echonuit/vigiechiro-pr-companion](https://github.com/echonuit/vigiechiro-pr-companion)
    (documentation utilisateur et d'architecture séparées).

## Sommaire

1. [Présentation du projet](Présentation%20du%20projet.md)
2. [Stack technique et architecture](Contraintes%20techniques.md)
3. [Objectifs qualités](Objectifs%20qualités/index.md)
4. [Expression du besoin](Expression%20du%20besoin.md)
5. [**Analyse et conception**](Analyse%20et%20conception/index.md) (modèle conceptuel, personas, parcours utilisateurs, maquettes, story mapping)

## Données d'exemple fournies

Un jeu de données réel issu d'une session d'enregistrement nocturne (22-23 avril 2026, PR n° 1925492) accompagne le projet en deux variantes complémentaires :

### 🟢 Échantillon versionné dans un dépôt dédié

Un **échantillon représentatif** d'une nuit (audio réduit + observations complètes) est versionné dans le dépôt [`vigiechiro-pr-companion-exemple-nuit`](https://github.com/echonuit/vigiechiro-pr-companion-exemple-nuit), disponible immédiatement après `git clone`. Suffit pour démarrer, pour la CI, et pour tester la majorité des stories. Le détail du contenu est décrit dans le `README` de ce dépôt.

### 🔵 Full dataset sur Zenodo

**~4,2 Go zippés** (~11 Go décompressés) : 1572 WAV bruts + 2109 WAV redécoupés + 4031 observations Tadarida. Indispensable pour valider les stories de **volumétrie** ([O3](Objectifs%20qualités/Objectifs%20qualités/O3.md), [O5](Objectifs%20qualités/Objectifs%20qualités/O5.md)).

!!! tip "Archive permanente : DOI Zenodo"
    La nuit complète est archivée sur Zenodo, DOI [10.5281/zenodo.20492247](https://doi.org/10.5281/zenodo.20492247) (lien permanent, accès libre) : c'est elle qui permet d'éprouver l'application sur les volumes réels.

    **Depuis votre navigateur** : [fiche Zenodo du jeu de données](https://zenodo.org/records/20492247).

    **Depuis la ligne de commande**, à la racine de votre clone du dépôt :

    ```bash
    mkdir -p data
    curl -L -o data/Car640380-2026-Pass2-Z1.zip \
      "https://zenodo.org/records/20492247/files/Car640380-2026-Pass2-Z1.zip?download=1"
    unzip data/Car640380-2026-Pass2-Z1.zip -d data/
    rm data/Car640380-2026-Pass2-Z1.zip   # optionnel, libère ~4,2 Go
    ```

    Le dossier `data/` est listé dans le `.gitignore` : aucun risque de commit accidentel.
