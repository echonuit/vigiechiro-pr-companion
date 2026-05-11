# SAE 2.01 - VigieChiro PR Companion

Énoncé pédagogique de la SAE 2.01 du semestre 2 du BUT Informatique, IUT d'Aix-Marseille. SAE commune aux modules **R2.02 - Développement d'applications avec IHM** et **R2.03 - Qualité de développement**.

## 📖 Lire le brief

👉 **Site publié** : <https://iutinfoaix-s201.github.io/brief/>

Vous pouvez aussi explorer directement les sources Markdown dans [`docs/`](docs/) :

1. [Présentation du projet](docs/Présentation%20du%20projet.md)
2. [Contraintes techniques](docs/Contraintes%20techniques.md)
3. [Objectifs qualités](docs/Objectifs%20qualités.md)
4. [Expression du besoin](docs/Expression%20du%20besoin.md)
5. [Analyse et conception](docs/Analyse%20et%20conception/) (personas, parcours, story mapping, périmètre MVP, planification)
6. [Jalons et livrables](docs/Jalons%20et%20livrables.md)
7. [Calendrier de travail](docs/Calendrier%20de%20travail.md)
8. [Consignes générales](docs/Consignes%20générales.md)

## 📦 Données fournies

- 🟢 [`samples/`](samples/) - sample de 518 Mo versionné dans le dépôt (191 WAV, 473 observations).
- 🔵 `data/` - full dataset 10 Go à télécharger via [Filesender RENATER](https://filesender.renater.fr/?s=download&token=5dc49594-dfa5-4778-8531-00d308f126aa). ⚠️ **Lien valable jusqu'au 10/06/2026** - téléchargez l'archive **dès le démarrage de la SAE** (sprint 0), avant qu'elle n'expire. En cas d'expiration, demandez à l'équipe pédagogique.

## 🛠️ Construire le site localement

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
mkdocs serve  # http://localhost:8000
```

## 🚀 Publication

Tout push sur `main` déclenche le workflow [`deploy-pages.yml`](.github/workflows/deploy-pages.yml) qui rebuild et publie le site sur GitHub Pages.
