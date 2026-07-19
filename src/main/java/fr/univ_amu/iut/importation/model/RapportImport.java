package fr.univ_amu.iut.importation.model;

import java.util.List;

/// **Rapport d'import** (#155, #214) : bilan, fichier par fichier, de ce qu'est devenue la source lors
/// d'un import **résilient** (importés, ignorés non pertinents, rejetés avec raison), complété par la
/// **dimension doublon** (#214/#147) : les passages déjà en base pour la même nuit. Exportable texte/CSV.
///
/// @param lignes une [LigneRapport] par fichier de la source, dans l'ordre de traitement
/// @param doublonsDeNuit passages déjà présents pour cette nuit (même série + date) AVANT cet import ;
///     vide si la nuit est neuve. Non vide = le passage créé est un **doublon de nuit** assumé (#147).
public record RapportImport(List<LigneRapport> lignes, List<PassageExistant> doublonsDeNuit) {

    public RapportImport {
        lignes = List.copyOf(lignes);
        doublonsDeNuit = List.copyOf(doublonsDeNuit);
    }

    /// Variante d'une nuit **neuve** (sans doublon) : pour les appelants/tests qui n'en fournissent pas.
    public RapportImport(List<LigneRapport> lignes) {
        this(lignes, List.of());
    }

    /// Nombre de fichiers d'un statut donné.
    public long compte(StatutImportFichier statut) {
        return lignes.stream().filter(l -> l.statut() == statut).count();
    }

    /// `true` si la nuit importée était **déjà présente** en base (#214/#147) : le passage créé en est un
    /// doublon assumé (l'utilisateur a choisi « importer quand même »).
    public boolean aDoublonDeNuit() {
        return !doublonsDeNuit.isEmpty();
    }

    /// Fichiers **rejetés** (non importés, #155) formatés « nom — raison », pour l'affichage dans M-Import.
    /// Liste vide si aucun rejet.
    public List<String> rejetsFormates() {
        return lignes.stream()
                .filter(ligne -> ligne.statut() == StatutImportFichier.REJETE)
                .map(ligne -> ligne.nomFichier() + " — " + ligne.detail())
                .toList();
    }

    /// Résumé compact « N importés · M ignorés · K rejetés ».
    public String resume() {
        return compte(StatutImportFichier.IMPORTE)
                + " importés · "
                + compte(StatutImportFichier.IGNORE)
                + " ignorés · "
                + compte(StatutImportFichier.REJETE)
                + " rejetés";
    }

    /// `true` si au moins un fichier a été rejeté (utile pour alerter l'utilisateur).
    public boolean aDesRejets() {
        return compte(StatutImportFichier.REJETE) > 0;
    }

    /// Rendu **texte** lisible : une ligne d'en-tête (résumé) puis une ligne par fichier.
    public String versTexte() {
        StringBuilder sb =
                new StringBuilder("Rapport d'import : ").append(resume()).append('\n');
        for (LigneRapport ligne : lignes) {
            sb.append("  [").append(ligne.statut()).append("] ").append(ligne.nomFichier());
            if (!ligne.detail().isEmpty()) {
                sb.append(" — ").append(ligne.detail());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /// Rendu **CSV** (séparateur `;`, en-tête `fichier;statut;detail`) pour archivage / tableur. Les
    /// champs contenant `;`, `"` ou un saut de ligne sont échappés selon RFC 4180.
    public String versCsv() {
        StringBuilder sb = new StringBuilder("fichier;statut;detail\n");
        for (LigneRapport ligne : lignes) {
            sb.append(echapper(ligne.nomFichier()))
                    .append(';')
                    .append(ligne.statut())
                    .append(';')
                    .append(echapper(ligne.detail()))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String echapper(String champ) {
        if (champ.contains(";") || champ.contains("\"") || champ.contains("\n")) {
            return '"' + champ.replace("\"", "\"\"") + '"';
        }
        return champ;
    }
}
