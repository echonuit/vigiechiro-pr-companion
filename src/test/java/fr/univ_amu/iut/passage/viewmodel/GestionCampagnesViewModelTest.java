package fr.univ_amu.iut.passage.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.ServiceCampagne;
import fr.univ_amu.iut.passage.model.dao.CampagneDao;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Tests de [GestionCampagnesViewModel] (#2630) sur une base SQLite jetable, avec le vrai
/// [ServiceCampagne] : ce ViewModel n'a d'intérêt que par ce qu'il fait *au travers* du service, un
/// double masquerait précisément les refus qu'on veut voir traduits.
///
/// [HorlogeFigee] au 20/07/2026 : l'année par défaut est déterministe.
class GestionCampagnesViewModelTest {

    private static final String SUIVI_ENS = "Suivi ENS";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ServiceCampagne service;
    private GestionCampagnesViewModel viewModel;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", dossier.toString());
        Injector injecteur = Guice.createInjector(new CommunModule(), new PersistenceModule());
        source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        service = new ServiceCampagne(
                new CampagneDao(source), new PassageDao(source), new HorlogeFigee(LocalDate.of(2026, 7, 20)));
        viewModel = new GestionCampagnesViewModel(service);
    }

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("créer : la campagne apparaît dans la liste, sélectionnée, et le retour le dit")
    void creer_publie_et_selectionne() {
        viewModel.charger();
        assertThat(viewModel.campagnes()).isEmpty();

        viewModel.creer(SUIVI_ENS, 2026, null);

        assertThat(viewModel.campagnes()).extracting(Campagne::nom).containsExactly(SUIVI_ENS);
        assertThat(viewModel.selectionProperty().get())
                .as("on vient de la créer : c'est sur elle qu'on agira ensuite")
                .isNotNull()
                .extracting(Campagne::nom)
                .isEqualTo(SUIVI_ENS);
        assertThat(viewModel.retourProperty().get().severite()).isEqualTo(Severite.SUCCES);
    }

    @Test
    @DisplayName("créer sans nom : le refus du service devient un message, il ne remonte pas")
    void creer_sans_nom_refuse() {
        viewModel.charger();

        viewModel.creer("   ", 2026, null);

        assertThat(viewModel.campagnes()).as("rien n'a été créé").isEmpty();
        assertThat(viewModel.retourProperty().get().severite()).isEqualTo(Severite.ERREUR);
        assertThat(viewModel.retourProperty().get().texte()).contains("nom");
    }

    @Test
    @DisplayName("modifier : le renommage se relit, et la sélection ne bouge pas")
    void modifier_conserve_la_selection() {
        viewModel.creer(SUIVI_ENS, 2026, null);
        Long idAvant = viewModel.selectionProperty().get().id();

        viewModel.modifier("Suivi ENS Sainte-Baume", 2027, "commanditaire : le Parc");

        assertThat(viewModel.campagnes())
                .extracting(Campagne::nom, Campagne::annee)
                .containsExactly(org.assertj.core.api.Assertions.tuple("Suivi ENS Sainte-Baume", 2027));
        assertThat(viewModel.selectionProperty().get().id())
                .as("renommer ne doit pas déplacer le curseur de l'utilisateur")
                .isEqualTo(idAvant);
    }

    @Test
    @DisplayName("modifier sans sélection : un guidage, pas une erreur")
    void modifier_sans_selection_guide() {
        viewModel.charger();

        viewModel.modifier("Peu importe", 2026, null);

        assertThat(viewModel.retourProperty().get().severite())
                .as("l'utilisateur a quelque chose à faire, rien n'est en panne")
                .isEqualTo(Severite.INFO);
    }

    @Test
    @DisplayName("supprimer : la campagne part, le passage rattaché reste et se retrouve détaché")
    void supprimer_detache_sans_effacer() {
        long idPassage = semerPassage();
        viewModel.creer(SUIVI_ENS, 2026, null);
        Long idCampagne = viewModel.selectionProperty().get().id();
        service.rattacherPassage(idPassage, idCampagne);
        assertThat(viewModel.passagesRattaches(viewModel.selectionProperty().get()))
                .isEqualTo(1);

        viewModel.supprimer();

        assertThat(viewModel.campagnes()).isEmpty();
        assertThat(new PassageDao(source).findById(idPassage))
                .as("le passage n'est pas effacé, seulement détaché")
                .isPresent()
                .get()
                .extracting(passage -> passage.idCampagne())
                .isNull();
        assertThat(viewModel.retourProperty().get().texte()).contains("détaché");
    }

    @Test
    @DisplayName("la phrase du détachement s'accorde, et se tait quand il n'y avait rien à détacher")
    void phrase_detachement_saccorde() {
        // Formulée une seule fois parce qu'elle est dite DEUX fois : par la confirmation avant l'acte,
        // par le retour après. Deux rédactions finiraient par diverger.
        assertThat(GestionCampagnesViewModel.phraseDetachement(0)).isEqualTo("Aucun passage n'y était rattaché.");
        assertThat(GestionCampagnesViewModel.phraseDetachement(1)).contains("Le passage qui y était rattaché");
        assertThat(GestionCampagnesViewModel.phraseDetachement(12)).contains("Les 12 passages");
    }

    @Test
    @DisplayName("l'année par défaut vient de l'horloge du service, pas de la machine")
    void annee_par_defaut_deterministe() {
        assertThat(viewModel.anneeParDefaut())
                .as("horloge figée au 20/07/2026 : une capture doit rendre la même image chaque année")
                .isEqualTo(2026);
    }

    /// Sème un passage minimal et renvoie son id.
    private long semerPassage() {
        return JeuDeDonneesPassage.dans(source)
                .carre("640380")
                .point("A1")
                .nuit(1, 2026, "2026-06-20")
                .statut(StatutWorkflow.DEPOSE)
                .verdict(Verdict.OK)
                .semer()
                .idPassage();
    }
}
