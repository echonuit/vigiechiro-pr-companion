package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Garde-fou de **complétude** liant la logique de [ConstructeurLienEspece] au **référentiel réel**
/// (schéma + seed migrés). Il casse si l'on ajoute un chiroptère au seed sans le pourvoir en fiche, ou
/// si la table PNA embarquée référence un code qui n'existe pas (typo, code périmé).
class CompletudeFichesEspecesTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private final ConstructeurLienEspece constructeur = new ConstructeurLienEspece();

    @BeforeEach
    void migrer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
    }

    @Test
    @DisplayName("Tout chiroptère du référentiel donne un lien de fiche (PNA ou GBIF)")
    void tout_chiroptere_donne_un_lien() {
        List<EspeceIdentifiee> chiropteres = taxonsDuGroupe("Chiroptères");
        assertThat(chiropteres).as("le seed doit contenir des chiroptères").isNotEmpty();
        assertThat(chiropteres)
                .allSatisfy(espece -> assertThat(constructeur.lienFiche(espece))
                        .as("aucune fiche pour le chiroptère %s (%s)", espece.codeTadarida(), espece.nomLatin())
                        .isPresent());
    }

    @Test
    @DisplayName("Chaque code de la table PNA correspond à un chiroptère réellement présent dans le seed")
    void chaque_code_pna_existe_dans_le_seed() {
        Set<String> codesChiropteres = new HashSet<>();
        for (EspeceIdentifiee espece : taxonsDuGroupe("Chiroptères")) {
            codesChiropteres.add(espece.codeTadarida());
        }
        assertThat(codesChiropteres)
                .as("codes PNA absents du référentiel (typo ou code périmé)")
                .containsAll(constructeur.codesAvecFichePna());
    }

    private List<EspeceIdentifiee> taxonsDuGroupe(String groupe) {
        String sql = "SELECT t.code, t.latin_name, t.vernacular_name_fr"
                + " FROM taxon t JOIN taxonomic_group g ON g.id = t.group_id WHERE g.name = ?";
        List<EspeceIdentifiee> especes = new ArrayList<>();
        try (Connection connexion = source.getConnection();
                var requete = connexion.prepareStatement(sql)) {
            requete.setString(1, groupe);
            try (ResultSet lignes = requete.executeQuery()) {
                while (lignes.next()) {
                    especes.add(new EspeceIdentifiee(
                            lignes.getString("code"),
                            lignes.getString("latin_name"),
                            lignes.getString("vernacular_name_fr")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lecture des taxons du groupe « " + groupe + " » impossible", e);
        }
        return especes;
    }
}
