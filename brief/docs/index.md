# SAE 2.01 - VigieChiro PR Companion

<div markdown="0" style="margin: 1rem 0 2rem 0;">
  <img src="assets/illustrations/hero-bandeau.webp"
       alt="Nuit de session d'enregistrement acoustique : un Passive Recorder sur son piquet, des chauves-souris en vol émettant des ondes d'écholocation sous une pleine lune en lisière de forêt"
       style="width: 100%; height: auto; display: block; border-radius: 4px;">
</div>

> Énoncé pédagogique de la SAE 2.01 du semestre 2 du BUT Informatique, IUT d'Aix-Marseille.
> SAE commune aux modules **R2.02 - Développement d'applications avec IHM** et **R2.03 - Qualité de développement**.

!!! info "SAE de développement"
    La phase d'analyse et de conception est portée par l'équipe pédagogique. Vous recevez un dossier de spécification opérationnel (section [Analyse et conception](Analyse%20et%20conception/index.md)) et vous concentrez votre énergie sur le développement (R2.02) et la qualité de la production (R2.03).

!!! tip "Présentation du brief en slides"
    Le brief vous a été présenté sous forme de slides : [**VigieChiro PR Companion**](https://iutinfoaix-r202.github.io/cours/presentation-sae-2.01.html) (support R2.02). Une vue d'ensemble rapide du projet, à parcourir avant de plonger dans les sections détaillées ci-dessous.

## Sommaire

1. [Présentation du projet](Présentation%20du%20projet.md)
2. [Contraintes techniques](Contraintes%20techniques.md)
3. [Objectifs qualités](Objectifs%20qualités/index.md)
4. [Expression du besoin](Expression%20du%20besoin.md)
5. [**Analyse et conception**](Analyse%20et%20conception/index.md) (dossier fourni : personas, parcours, story mapping, périmètre MVP, planification)
6. [Jalons et livrables](Jalons%20et%20livrables.md)
7. [Calendrier de travail](Calendrier%20de%20travail.md)
8. [Consignes générales](Consignes%20générales.md)

## Données d'exemple fournies

Un jeu de données réel issu d'une session d'enregistrement nocturne (22-23 avril 2026, PR n° 1925492) vous est fourni en deux variantes complémentaires :

### 🟢 Échantillon versionné dans un dépôt dédié

Un **échantillon représentatif** d'une nuit (audio réduit + observations complètes) est versionné dans le dépôt [`vigiechiro-pr-companion-exemple-nuit`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion-exemple-nuit), disponible immédiatement après `git clone`. Suffit pour démarrer, pour la CI, et pour tester la majorité des stories. Le détail du contenu est décrit dans le `README` de ce dépôt.

### 🔵 Full dataset à télécharger

**~4,2 Go zippés** (~11 Go décompressés) : 1572 WAV bruts + 2109 WAV redécoupés + 4031 observations Tadarida. Indispensable pour valider les stories de **volumétrie** ([O3](Objectifs%20qualités/Objectifs%20qualités/O3.md), [O5](Objectifs%20qualités/Objectifs%20qualités/O5.md)).

!!! danger "À télécharger en priorité - lien valable jusqu'au 15/06/2026"
    Récupérez l'archive (~4,2 Go) **dès le démarrage de la SAE** (sprint 0). Filesender est un service de partage de fichiers volumineux à durée de vie limitée. **Passé le 15/06/2026, le lien expirera.** En cas d'expiration, demandez à l'équipe pédagogique.

    **Depuis votre navigateur** : [page Filesender RENATER](https://filesender.renater.fr/?s=download&token=d18d33a0-3175-45e6-894f-de838850180c).

    **Depuis la ligne de commande**, à la racine de votre clone du brief :

    ```bash
    mkdir -p data
    curl -L -o data/Car640380-2026-Pass2-Z1.zip \
      "https://filesender.renater.fr/download.php?token=d18d33a0-3175-45e6-894f-de838850180c&files_ids=71480536"
    unzip data/Car640380-2026-Pass2-Z1.zip -d data/
    rm data/Car640380-2026-Pass2-Z1.zip   # optionnel, libère ~4,2 Go
    ```

    Le dossier `data/` est listé dans le `.gitignore` : aucun risque de commit accidentel.
