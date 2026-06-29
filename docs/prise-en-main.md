# Prise en main

Cette page explique comment **installer**, **lancer** VigieChiro, puis **situer les écrans** au
premier démarrage. En quelques minutes, vous êtes prêt à traiter votre première nuit
d'enregistrement.

## Installer l'application

Une fois une version publiée, des installeurs prêts à l'emploi sont disponibles sur la page
[Releases](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/releases) du projet.
Téléchargez celui qui correspond à votre système, puis installez-le comme une application classique :

| Système | Fichier à télécharger |
|---|---|
| Windows | `.msi` (ou `.exe`) |
| macOS | `.dmg` (ou `.pkg`) |
| Linux | `.deb` (Debian, Ubuntu) ou `.rpm` (Fedora) |

L'installeur embarque tout le nécessaire : **aucune installation de Java** n'est requise pour
utiliser l'application.

!!! note "Avertissement de sécurité possible"
    Les installeurs ne sont pas signés. Votre système peut afficher un avertissement à la première
    ouverture (Gatekeeper sur macOS, SmartScreen sur Windows) : autorisez l'application pour
    continuer.

## Lancer depuis les sources

Si vous travaillez à partir du code (par exemple avant la première version publiée), l'application
se lance avec le Maven Wrapper, depuis la racine du projet :

```bash
./mvnw javafx:run
```

Un **JDK 25** est alors nécessaire. Le premier lancement télécharge les dépendances, les suivants
sont immédiats.

## Découvrir l'écran d'accueil

Au lancement, l'application ouvre son **écran d'accueil**. Le bandeau vous invite à
« sélectionner une activité pour traiter une nuit d'enregistrement », et propose quatre points
d'entrée :

![L'écran d'accueil de VigieChiro et ses quatre activités.](assets/captures/apercu-accueil.png)

| Activité | À quoi elle sert |
|---|---|
| **Mes sites** | Gérer vos carrés et points d'écoute. C'est le point de départ : on déclare d'abord *où* l'on capture. |
| **Importer une nuit** | Importer une nuit de Passive Recorder depuis la carte SD. |
| **Sons & validation** | Écouter, valider et exporter votre corpus de sons de référence. |
| **Carte & passages** | La carte de vos sites et le tableau de tous vos passages, avec filtres, tri et export. |

Depuis ces points d'entrée, vous atteignez ensuite les autres écrans : un **site** donne accès à
ses **passages** (les nuits), et un passage ouvre les écrans de **qualification**, de **dépôt
(lot)**, de **validation** des espèces et de **diagnostic**.

La barre du haut affiche un fil d'Ariane qui rappelle où vous vous trouvez et permet de revenir en
arrière.

## Et ensuite ?

- [Parcours métier](parcours/index.md) : le déroulé complet d'une nuit, de la carte SD au dépôt.
- [Référence par écran](ecrans/index.md) : le détail de chaque écran et de ses états.
- [Raccourcis clavier](raccourcis-clavier.md) : piloter l'application sans la souris.
