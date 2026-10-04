package fr.univ_amu.iut.lot.di;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Reglages;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.ReglagesDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.commun.view.DescripteurReglage;
import fr.univ_amu.iut.lot.model.ModeDepot;
import fr.univ_amu.iut.lot.viewmodel.OngletReglagesDepot;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// **Le dépôt part en WAV par défaut** (#5677, ADR 5677), et le dit partout de la même façon.
///
/// Le défaut se décidait à trois endroits : la lecture d'une valeur absente, le module qui lit le
/// réglage, et l'onglet des réglages qui l'affiche. L'écran et la ligne de commande passent tous deux
/// par le module : la parité tient à ce qu'un seul défaut existe, et ce test le tient.
class ModeDepotParDefautTest {

    @TempDir
    Path dossier;

    private Reglages reglages;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        reglages = new Reglages(new ReglagesDao(source));
    }

    @AfterEach
    void nettoyerPropriete() {
        System.clearProperty("vigiechiro.depot.mode");
    }

    @Test
    @DisplayName("#5677 : sans réglage, le dépôt part en séquences WAV")
    void sans_reglage_le_wav() {
        assertThat(LotModule.modeDepot(reglages)).isEqualTo(ModeDepot.SEQUENCES_WAV);
    }

    @Test
    @DisplayName("#5677 : une valeur absente ou inconnue retombe sur le WAV, sans empêcher de déposer")
    void valeur_inconnue_le_wav() {
        assertThat(ModeDepot.parValeur(null)).isEqualTo(ModeDepot.SEQUENCES_WAV);
        assertThat(ModeDepot.parValeur("tar")).isEqualTo(ModeDepot.SEQUENCES_WAV);
    }

    @Test
    @DisplayName("#5677 : un réglage ZIP déjà enregistré reste ZIP")
    void un_reglage_zip_reste_zip() {
        reglages.ecrireTexte(OngletReglagesDepot.CLE_MODE_DEPOT, ModeDepot.ARCHIVES_ZIP.valeur());

        assertThat(LotModule.modeDepot(reglages)).isEqualTo(ModeDepot.ARCHIVES_ZIP);
    }

    @Test
    @DisplayName("#5677 : l'onglet des réglages affiche le même défaut que celui qui s'applique")
    void l_onglet_affiche_le_defaut_applique() {
        DescripteurReglage.Enumeration mode = (DescripteurReglage.Enumeration) new OngletReglagesDepot()
                .reglages().stream()
                        .filter(DescripteurReglage.Enumeration.class::isInstance)
                        .findFirst()
                        .orElseThrow();

        assertThat(mode.defaut()).isEqualTo(LotModule.modeDepot(reglages).valeur());
        assertThat(mode.options().getFirst().valeur())
                .as("le défaut vient en tête de la liste")
                .isEqualTo(ModeDepot.SEQUENCES_WAV.valeur());
    }
}
