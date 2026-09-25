# Retrouver les journaux et les fiches depuis la CLI : avant et après

Les deux premières lignes comparent le fat-jar du commit d'ouverture `8d2353d5a` à celui de la
clôture, sur des workspaces temporaires. Les avertissements de la JVM, identiques des deux côtés,
sont retirés de cette transcription. Le chemin du workspace est remplacé par `<workspace>`.

| Conséquence visible | Avant | Après | Ce qu'il faut voir |
|---|---|---|---|
| Fiche d'espèce, `lien-espece --code Pippip` | Code 2 ; `Commande inconnue` ; stdout vide | Code 0 ; stdout : `https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/` | L'URL PNA sort seule, sans fenêtre. |
| Participation absente, `lien-participation --passage 42` | Code 2 ; `Commande inconnue` | Code 2 ; `Erreur d'usage : Aucune participation liée au passage 42.` | La commande existe et explique l'absence, sans URL. |
| Base illisible, `lister-sites` | Code 1 ; `Échec : [SQLITE_NOTADB] File opened that is not a database file (file is not a database)` | Même code et même cause, suivis de `Journaux : <workspace>/logs (joignez le fichier vigiechiro-*.log le plus récent à un signalement).` | Le diagnostic nomme l'endroit où retrouver la trace. |

La migration appelée **en processus** ne passe pas par le démarrage du fat-jar. Avant #5528, sa
reproduction dans [#5506](https://github.com/echonuit/vigiechiro-pr-companion/issues/5506) laissait
une ligne `at fr.univ_amu.iut.commun.persistence.SourceDeDonnees.getConnection` sur stderr. Après,
`CliMigrationTest` reproduit la même base illisible après construction de la CLI : code 1, message
`Échec :` sans pile sur stderr, et exception conservée dans le journal. Le test a été vu rouge en
réintroduisant le défaut, puis vert après correction.

Les états d'absence de fiche, de source GBIF ou Wikipédia, et de refus de migration sont gardés par
`CliLiensTest`, `CliVerrouWorkspaceTest` et les scénarios Bats. Ce chantier ne modifie aucun écran
JavaFX ; la différence que l'utilisateur voit est dans le terminal.
