# SAE 2.01 - VigieChiro PR Companion

> Énoncé pédagogique de la SAE 2.01 du semestre 2 du BUT Informatique, IUT d'Aix-Marseille.
> SAE commune aux modules **R2.02 - Développement d'applications avec IHM** et **R2.03 - Qualité de développement**.

!!! info "SAE de développement"
    La phase d'analyse et de conception est portée par l'équipe pédagogique. Vous recevez un dossier de spécification opérationnel (section [Analyse et conception](Analyse%20et%20conception/README.md)) et vous concentrez votre énergie sur le développement (R2.02) et la qualité de la production (R2.03).

## Sommaire

1. [Présentation du projet](Présentation%20du%20projet.md)
2. [Contraintes techniques](Contraintes%20techniques.md)
3. [Objectifs qualités](Objectifs%20qualités.md)
4. [Expression du besoin](Expression%20du%20besoin.md)
5. [**Analyse et conception**](Analyse%20et%20conception/README.md) (dossier fourni : personas, parcours, story mapping, périmètre MVP, planification)
6. [Jalons et livrables](Jalons%20et%20livrables.md)
7. [Calendrier de travail](Calendrier%20de%20travail.md)
8. [Consignes générales](Consignes%20générales.md)

## Données d'exemple fournies

Un jeu de données réel issu d'une session de capture nocturne (22-23 avril 2026, PR n° 1925492) vous est fourni en deux variantes complémentaires :

### 🟢 Sample versionné dans le dépôt

**~518 Mo** disponibles immédiatement après `git clone`. Suffit pour démarrer, pour la CI, et pour tester la majorité des stories. Contient le LogPR + THLog complets, **191 WAV** redécoupés couvrant tous les taxa principaux, et les **2 CSV d'observations** filtrés en cohérence (473 obs sur 4031). Détails : [`samples/`](https://github.com/IUTInfoAix-S201/brief/tree/main/samples) sur GitHub.

### 🔵 Full dataset à télécharger

**~10 Go zippés** (~11 Go décompressés) : 1572 WAV bruts + 2114 WAV redécoupés + 4031 observations Tadarida. Indispensable pour valider les stories de **volumétrie** ([O3](Objectifs%20qualités/Objectifs%20qualités/O3.md), [O5](Objectifs%20qualités/Objectifs%20qualités/O5.md)).

!!! danger "À télécharger en priorité - lien valable jusqu'au 10/06/2026"
    Récupérez l'archive (~10 Go) **dès le démarrage de la SAE** (sprint 0). Filesender est un service de partage de fichiers volumineux à durée de vie limitée. **Passé le 10/06/2026, le lien expirera.** En cas d'expiration, demandez à l'équipe pédagogique.

    **Depuis votre navigateur** : [page Filesender RENATER](https://filesender.renater.fr/?s=download&token=5dc49594-dfa5-4778-8531-00d308f126aa).

    **Depuis la ligne de commande**, à la racine de votre clone du brief :

    ```bash
    mkdir -p data
    curl -L -o data/20260423-selected.zip \
      "https://filesender.renater.fr/download.php?token=5dc49594-dfa5-4778-8531-00d308f126aa&files_ids=71231318"
    unzip data/20260423-selected.zip -d data/
    rm data/20260423-selected.zip   # optionnel, libère ~10 Go
    ```

    Le dossier `data/` est listé dans le `.gitignore` : aucun risque de commit accidentel.
