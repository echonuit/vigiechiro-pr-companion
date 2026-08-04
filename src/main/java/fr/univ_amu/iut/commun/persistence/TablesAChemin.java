package fr.univ_amu.iut.commun.persistence;

import java.util.List;

/// Les tables que le schéma fait porter un **chemin de fichier**, en un seul endroit (#3133).
///
/// Quand une nuit change de dossier, tout ce que la base retient d'elle doit suivre : sa racine
/// (`recording_session.root_path`), mais aussi chaque original, chaque séquence d'écoute, le journal
/// du capteur, le relevé climatique et le CSV Tadarida. **En absolu**, tous.
///
/// Deux endroits en ont besoin, pour deux raisons différentes :
///
/// - le socle, quand une restauration replace un dossier ailleurs ([ReecritureRacineSession], #2727) ;
/// - la feature passage, quand un rattachement renomme une session (`RattachementDao`).
///
/// Ils énuméraient chacun les six tables. La septième aurait été ajoutée à un endroit sur deux, et le
/// premier oubli aurait reproduit le défaut de #2727 : une base qui **paraît** corrigée et une
/// application qui ne retrouve plus un fichier. Cette liste est de la connaissance de **schéma**, pas
/// de la connaissance métier : sa place est ici, et ce que chacun **fait** de chaque table reste chez
/// lui.
///
/// ⚠️ `recording_session` n'y figure pas : sa colonne s'appelle `root_path` et non `file_path`, et
/// c'est **elle** que les autres suivent. Elle se traite à part, avant les autres.
///
/// `TablesACheminTest` confronte cette liste aux colonnes de chemin déclarées par les migrations :
/// une table de plus dans le schéma fait rougir, y compris si personne n'y a pensé.
public final class TablesAChemin {

    private static final String CLE_SESSION = "session_id";
    private static final String CLE_PASSAGE = "passage_id";

    private static final List<TableAChemin> TOUTES = List.of(
            new TableAChemin("original_recording", CLE_SESSION, true),
            new TableAChemin("listening_sequence", CLE_SESSION, true),
            new TableAChemin("sensor_log", CLE_SESSION, false),
            new TableAChemin("climate_log", CLE_SESSION, false),
            new TableAChemin("identification_results", CLE_PASSAGE, false));

    private TablesAChemin() {}

    /// Toutes les tables à chemin, hors `recording_session`.
    public static List<TableAChemin> toutes() {
        return TOUTES;
    }

    /// Une table portant un `file_path`.
    ///
    /// @param table son nom
    /// @param cle la colonne qui la rattache à sa session, ou au **passage** pour les résultats
    ///     d'identification : c'est la seule dans ce cas, donc celle qu'on oublie
    /// @param avecNomDeFichier `true` si elle porte aussi un `file_name` logique, que le renommage
    ///     d'une session doit suivre là où un simple déplacement le laisse tel quel
    public record TableAChemin(String table, String cle, boolean avecNomDeFichier) {

        /// `true` si cette table se retrouve par le passage et non par la session.
        public boolean surLePassage() {
            return CLE_PASSAGE.equals(cle);
        }
    }
}
