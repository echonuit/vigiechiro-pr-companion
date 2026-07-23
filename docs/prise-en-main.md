# Prise en main

Cette page explique comment **installer**, **lancer** VigieChiro Companion, puis **situer les écrans** au
premier démarrage. En quelques minutes, vous êtes prêt à traiter votre première nuit
d'enregistrement.

## Installer l'application

Les fichiers sont sur la page
[Releases](https://github.com/echonuit/vigiechiro-pr-companion/releases) du projet. Deux
formes vous sont proposées pour chaque système, et le choix n'est pas anodin.

### Installer, ou simplement décompresser

**L'installeur** place l'application dans votre système comme n'importe quel logiciel : elle apparaît
dans le menu Démarrer ou le Launchpad. Il demande en revanche les **droits d'administration**.

| Système | Installeur |
|---|---|
| Windows | `VigieChiroCompanion-…-x64.msi` |
| macOS (Apple Silicon) | `VigieChiroCompanion-…-arm64.dmg` |
| Linux (Debian, Ubuntu) | `vigiechiro_…_amd64-x64.deb` |

**L'archive portable** ne s'installe pas : vous la décompressez où vous voulez, et vous lancez.
Aucun droit particulier n'est requis. C'est la bonne option pour **essayer le produit**, ou pour
travailler sur un **ordinateur que vous n'administrez pas** - un poste de laboratoire, une machine
prêtée.

| Système | Archive portable | Pour lancer |
|---|---|---|
| Windows | `…-windows-x64-portable.zip` | décompressez, puis `VigieChiroCompanion\VigieChiroCompanion.exe` |
| macOS | `…-macos-arm64-portable.zip` | décompressez, puis ouvrez `VigieChiroCompanion.app` |
| Linux | `…-linux-x64-portable.tar.gz` | `tar -xzf …tar.gz` puis `VigieChiroCompanion/bin/VigieChiroCompanion` |

**Sous Linux, une troisième forme** existe : l'**AppImage**, un fichier unique et exécutable. Rien à
décompresser, et elle s'ajoute au menu des applications.

```bash
chmod +x VigieChiroCompanion-2.20.0-linux-x86_64.AppImage
./VigieChiroCompanion-2.20.0-linux-x86_64.AppImage
```

Dans tous les cas, tout le nécessaire est embarqué : **aucune installation de Java** n'est requise.

!!! tip "Et pour mettre à jour ?"
    Avec l'archive portable, remplacez simplement le dossier décompressé par celui de la nouvelle
    version. Vos données (base et journaux) vivent **ailleurs**, dans votre dossier personnel : elles
    ne sont pas touchées.

!!! note "Avertissement de sécurité possible"
    Les installeurs ne sont pas signés. Votre système peut afficher un avertissement à la première
    ouverture (Gatekeeper sur macOS, SmartScreen sur Windows) : autorisez l'application pour
    continuer.

### Vérifier ce que vous avez téléchargé

Faute de signature, c'est **l'empreinte** qui atteste qu'un fichier est bien celui publié et qu'il est
arrivé entier. Chaque artefact est accompagné, sur la page des Releases, d'un petit fichier portant le
même nom suivi de **`.sha256`**.

Téléchargez-le à côté du vôtre, puis, **dans le dossier de téléchargement** :

=== "Linux"

    ```bash
    sha256sum -c VigieChiroCompanion-2.21.1-linux-x64-portable.tar.gz.sha256
    ```

=== "macOS"

    ```bash
    shasum -a 256 -c VigieChiroCompanion-2.21.1-arm64.dmg.sha256
    ```

=== "Windows (PowerShell)"

    ```powershell
    (Get-FileHash .\VigieChiroCompanion-2.21.1-x64.msi -Algorithm SHA256).Hash.ToLower()
    Get-Content .\VigieChiroCompanion-2.21.1-x64.msi.sha256
    ```

Une réponse `OK` (ou, sous Windows, deux empreintes identiques) signifie que le fichier est intact. Un
`FAILED` signifie qu'il **ne correspond pas** : ne l'ouvrez pas, retéléchargez-le.

!!! warning "Ce que l'empreinte prouve, et ce qu'elle ne prouve pas"
    Elle prouve que le fichier est **identique** à celui publié sur la page des Releases : elle
    détecte un téléchargement corrompu ou tronqué. Elle ne remplace pas une **signature** : elle
    n'atteste pas de l'identité de l'auteur, puisqu'elle est publiée au même endroit que les
    fichiers. Sa confiance vaut celle que vous accordez à la page du projet.

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
« sélectionner une activité pour traiter une nuit d'enregistrement », et propose cinq points
d'entrée :

![L'écran d'accueil de VigieChiro Companion et ses cinq activités.](assets/captures/apercu-accueil.png)

| Activité | À quoi elle sert |
|---|---|
| **Mes sites** | Gérer vos carrés et points d'écoute. C'est le point de départ : on déclare d'abord *où* l'on capture. |
| **Importer une nuit** | Importer une nuit de Passive Recorder depuis la carte SD. |
| **Sons & validation** | Écouter, valider et exporter votre corpus de sons de référence. |
| **Carte & passages** | La carte de vos sites et le tableau de tous vos passages, avec filtres, tri et export. |

Depuis ces points d'entrée, vous atteignez ensuite les autres écrans : un **site** donne accès à
ses **passages** (les nuits), et un passage ouvre les écrans de **qualification**, de **dépôt**,
de **validation** des espèces et de **diagnostic**.

La barre du haut affiche un fil d'Ariane qui rappelle où vous vous trouvez et permet de revenir en
arrière.

## Comment l'application vous répond

Quand vous lancez une action - préparer un dépôt, enregistrer un site, corriger un rattachement -
l'application vous dit ce qu'elle a fait, dans un bandeau qui paraît sur l'écran concerné.

![Un bandeau de compte rendu sur l'écran Diagnostic](assets/captures/apercu-diagnostic-retour.png)

La **couleur** dit de quoi il s'agit, sans qu'il faille lire le message en entier :

| Couleur | Ce que ça veut dire |
|---|---|
| **Vert** | l'opération a abouti |
| **Bleu** | il y a quelque chose à savoir, mais rien n'a échoué : une saisie à compléter, une action sans objet, un résultat partiel |
| **Rouge** | l'opération a échoué ou a été refusée |

Le bleu mérite un mot : il ne signale **pas** un problème. « Le traitement est déjà en cours », « aucune
nuit à relever », « dépôt interrompu : 12 fichiers sur 20 sont en ligne » sont des informations utiles,
pas des pannes.

La **croix** à droite referme le bandeau quand vous l'avez lu. Il repart aussi de lui-même dès que vous
lancez l'action suivante : ce que vous voyez concerne toujours votre dernier geste.

Deux exceptions volontaires. Les actions **irréversibles** - supprimer un passage, par exemple -
rendent compte dans une fenêtre qui **bloque** tant que vous ne
l'avez pas lue, parce qu'il serait fâcheux de manquer le résultat d'une destruction. Et ce qui décrit
l'**état** d'une nuit (« Passage déposé le… ») n'est pas un compte rendu : ce libellé-là reste affiché
et ne se ferme pas.

## Et ensuite ?

- [Parcours métier](parcours/index.md) : le déroulé complet d'une nuit, de la carte SD au dépôt.
- [Référence par écran](ecrans/index.md) : le détail de chaque écran et de ses états.
- [Raccourcis clavier](raccourcis-clavier.md) : piloter l'application sans la souris.
