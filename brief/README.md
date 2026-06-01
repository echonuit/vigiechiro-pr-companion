# SAE 2.01 - VigieChiro PR Companion

Énoncé pédagogique de la SAE 2.01 du semestre 2 du BUT Informatique, IUT d'Aix-Marseille. SAE commune aux modules **R2.02 - Développement d'applications avec IHM** et **R2.03 - Qualité de développement**.

## 📖 Lire le brief

👉 **Brief publié** : <https://iutinfoaix-s201.github.io/brief/>

🦇 **Présentation du brief en slides** (support R2.02, vue d'ensemble rapide) : <https://iutinfoaix-r202.github.io/cours/presentation-sae-2.01.html>

Le brief est conçu pour être lu en ligne. Voici les sections principales :

1. [Présentation du projet](https://iutinfoaix-s201.github.io/brief/Pr%C3%A9sentation%20du%20projet/) — contexte VigieChiro, objectifs, parties prenantes (dont Samuel Busson, client réel)
2. [Contraintes techniques](https://iutinfoaix-s201.github.io/brief/Contraintes%20techniques/) — Java 25 / JavaFX 25 / JDBC SQLite / composants fournis
3. [Objectifs qualités](https://iutinfoaix-s201.github.io/brief/Objectifs%20qualit%C3%A9s/) — 8 objectifs ISO 25010 et 4 scénarios
4. [Expression du besoin](https://iutinfoaix-s201.github.io/brief/Expression%20du%20besoin/) — données fournies, fonctionnalités attendues (MoSCoW)
5. [Analyse et conception](https://iutinfoaix-s201.github.io/brief/Analyse%20et%20conception/) — modèle conceptuel, personas, parcours, story mapping, périmètre MVP, planification, maquettes
6. [Jalons et livrables](https://iutinfoaix-s201.github.io/brief/Jalons%20et%20livrables/) — phases et livrables attendus
7. [Calendrier de travail](https://iutinfoaix-s201.github.io/brief/Calendrier%20de%20travail/) — dates 2026 confirmées
8. [Consignes générales](https://iutinfoaix-s201.github.io/brief/Consignes%20g%C3%A9n%C3%A9rales/) — règles du jeu

## 🚀 Démarrer la SAE

Au démarrage de la SAE, l'équipe pédagogique communiquera un **lien GitHub Classroom** que chaque équipe doit accepter. L'acceptation crée automatiquement un dépôt dans l'organisation **`IUTInfoAix-S201-2026`**. C'est dans ce dépôt — et **nulle part ailleurs** — que vous travaillerez.

Le dépôt n'est pas vide : il embarque un **template d'application JavaFX déjà fonctionnel** avec :

- Une arborescence Maven prête (`pom.xml`, Maven Wrapper, dépendances JavaFX 25 / SQLite JDBC / JUnit 5 / TestFX / AssertJ).
- Une application JavaFX qui démarre (fenêtre principale, FXML, point d'entrée).
- **L'outillage qualité pré-configuré** : Spotless en pre-commit, GitHub Actions CI, `.gitignore`, README de démarrage.
- **Certains composants fonctionnels déjà implémentés** pour vous faire gagner du temps — notamment le composant de vue audio (sonogramme + spectrogramme avec zoom). Voir [Consignes générales](https://iutinfoaix-s201.github.io/brief/Consignes%20g%C3%A9n%C3%A9rales/).

## 📦 Données fournies

- 🟢 **Échantillon d'une nuit de capture** : jeu de données réduit (audio + observations complètes) versionné dans le dépôt [`vigiechiro-pr-companion-exemple-nuit`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion-exemple-nuit). Disponible immédiatement après `git clone` de ce dépôt, suffisant pour développer et tester la chaîne fil rouge.
- 🔵 `data/` — full dataset **~10 Go** à télécharger via [Filesender RENATER](https://filesender.renater.fr/?s=download&token=5dc49594-dfa5-4778-8531-00d308f126aa) (gitignored, à mettre dans le dossier `data/` du projet). ⚠️ **Lien valable jusqu'au 10/06/2026** — téléchargez l'archive **dès le démarrage de la SAE** pour pouvoir valider les objectifs de volumétrie ([O3](https://iutinfoaix-s201.github.io/brief/Objectifs%20qualit%C3%A9s/Objectifs%20qualit%C3%A9s/O3/), [O5](https://iutinfoaix-s201.github.io/brief/Objectifs%20qualit%C3%A9s/Objectifs%20qualit%C3%A9s/O5/)). En cas d'expiration, demandez à l'équipe pédagogique.

<details>
<summary>🔧 Pour les mainteneurs du brief (équipe pédagogique uniquement)</summary>

### Construire le site localement

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
mkdocs serve  # http://localhost:8000
```

### Publication

Tout push sur `main` déclenche le workflow [`deploy-pages.yml`](.github/workflows/deploy-pages.yml) qui rebuild et publie le site sur GitHub Pages.

</details>
