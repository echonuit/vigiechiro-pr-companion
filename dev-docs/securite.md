# Sécurité & données sensibles

VigieChiro traite des **données naturalistes**. La règle la plus importante n'est pas technique mais
**écologique** : ne jamais exposer la localisation d'espèces protégées. Cette page est la version
développeur de
[**SECURITY.md**](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/SECURITY.md)
(source canonique).

!!! danger "Données sensibles : chiroptères"
    La localisation précise des gîtes et points d'écoute d'**espèces protégées** ne doit **jamais**
    être diffusée publiquement. Concrètement, dans ce dépôt :

    - **aucune donnée terrain réelle** (enregistrements, journaux de capteur, relevés) ;
    - **aucune coordonnée GPS** de site sensible ;
    - **aucune donnée personnelle** d'observateur.

    En cas de doute sur la diffusion d'une donnée ou d'une coordonnée : **on s'abstient et on demande**.

## Le jeu de données d'exemple

Pour tester sans matériel, utilisez le **dataset d'exemple**, **dé-PII** (les fichiers Kaleidoscope
porteurs de métadonnées identifiantes ont été retirés) et publié sur Zenodo
([DOI 10.5281/zenodo.20492247](https://doi.org/10.5281/zenodo.20492247)). La base locale
**`vigiechiro.db`** est un artefact de travail **ignoré par git** : elle ne doit contenir que des
données d'exemple ou de test.

## Secrets et identifiants

- **Aucun secret n'est versionné** : pas de clé d'API, de jeton ni de mot de passe — y compris dans un
  test, une capture ou une base SQLite.
- Les workflows GitHub Actions tournent au **moindre privilège** (`maven.yml` : `permissions: contents:
  read`). N'élargir que là où c'est nécessaire (`contents: write` pour `capture-vues.yml`, qui pousse
  les aperçus).

## Surface applicative

L'application est **locale** : pas de serveur, pas de port ouvert, **aucune authentification réseau**,
aucun identifiant stocké.

- **Pas d'injection SQL** : les DAO utilisent des `PreparedStatement` (requêtes paramétrées), jamais de
  concaténation de chaînes dans le SQL (cf. [Persistance](persistance.md)).
- **Accès natifs cadrés** : sous Java 25 (accès natif strict), seuls les modules concernés sont
  autorisés (`--enable-native-access=javafx.graphics`, `--enable-native-access=org.xerial.sqlitejdbc`).

## Chaîne d'approvisionnement

- **Dependabot** propose mensuellement les mises à jour `maven` et `github-actions` ; les bumps sont
  **revus avant merge** (cf. [CI/CD et release](ci-cd-release.md#dependances)).
- **JavaFX (`org.openjfx:*`) est exclu** de l'automatisation (impact fort : rendu, Headless Platform) :
  mises à jour décidées à la main.
- Le composant **`audio-view`** (Maven Central) fait partie de la chaîne au même titre que toute
  dépendance.

## Signaler une vulnérabilité

Ne **pas** ouvrir d'issue publique. Écrire en privé à
**[sebastien.nedjar@univ-amu.fr](mailto:sebastien.nedjar@univ-amu.fr)** (problème, impact, étapes de
reproduction). Le périmètre supporté est la **branche par défaut** (dernière version).

!!! warning "Ne désactivez pas les garde-fous"
    Ne neutralisez pas PMD, ArchUnit ou la garde d'intégrité pour « faire passer » un build. Et
    vérifiez votre identité git avant de committer (`git config user.email` institutionnel).
