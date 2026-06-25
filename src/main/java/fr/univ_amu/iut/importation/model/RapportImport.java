package fr.univ_amu.iut.importation.model;

import java.util.List;

/// **Rapport d'import** (#155) : bilan, fichier par fichier, de ce qu'est devenue la source lors d'un
/// import **résilient** — importés, ignorés (non pertinents) et rejetés (avec raison). Exportable en
/// texte ou CSV pour archivage.
///
/// @param lignes une [LigneRapport] par fichier de la source, dans l'ordre de traitement
public record RapportImport(List<LigneRapport> lignes) {

    public RapportImport {
        lignes = List.copyOf(lignes);
    }

    /// Nombre de fichiers d'un statut donné.
    public long compte(StatutImportFichier statut) {
        return lignes.stream().filter(l -> l.statut() == statut).count();
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
