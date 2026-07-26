package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import com.google.inject.Injector;
import fr.univ_amu.iut.commun.di.CommunModule;
import fr.univ_amu.iut.commun.di.PersistenceModule;
import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Verdict;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.Passage;
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

/// Tests de [ServiceCampagne] (CRUD) sur une base SQLite jetable. L'[HorlogeFigee] au 20/07/2026 rend
/// déterministe l'année par défaut à la création.
class ServiceCampagneTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ServiceCampagne service;

    @BeforeEach
    void preparer() {
        System.setProperty("vigiechiro.workspace", dossier.toString());
        Injector injecteur = Guice.createInjector(new CommunModule(), new PersistenceModule());
        source = injecteur.getInstance(SourceDeDonnees.class);
        new MigrationSchema(source).migrer();
        service = new ServiceCampagne(
                new CampagneDao(source), new PassageDao(source), new HorlogeFigee(LocalDate.of(2026, 7, 20)));
    }

    /// Sème un passage minimal et renvoie son id (pour les tests de rattachement).
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

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.workspace");
    }

    @Test
    @DisplayName("créer une campagne retourne un id et l'ajoute à la liste")
    void creer_puis_lister() {
        Campagne creee = service.creerCampagne("Suivi ENS", 2026, "premier suivi");
        assertThat(creee.id()).isNotNull();
        assertThat(service.listerCampagnes())
                .extracting(Campagne::nom, Campagne::annee)
                .containsExactly(tuple("Suivi ENS", 2026));
    }

    @Test
    @DisplayName("sans année, la campagne prend l'année de l'horloge")
    void annee_par_defaut() {
        Campagne creee = service.creerCampagne("Sans année", null, null);
        assertThat(creee.annee()).isEqualTo(2026);
    }

    @Test
    @DisplayName("un nom vide est refusé")
    void nom_vide_refuse() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.creerCampagne("  ", 2026, null));
    }

    @Test
    @DisplayName("renommer met à jour la campagne existante")
    void renommer() {
        Campagne creee = service.creerCampagne("Ancien nom", 2026, null);
        Campagne modifiee = service.modifierCampagne(creee.id(), "Nouveau nom", 2025, "corrigé");
        assertThat(modifiee.nom()).isEqualTo("Nouveau nom");
        assertThat(modifiee.annee()).isEqualTo(2025);
        assertThat(service.listerCampagnes())
                .singleElement()
                .extracting(Campagne::nom)
                .isEqualTo("Nouveau nom");
    }

    @Test
    @DisplayName("modifier une campagne inconnue lève une erreur métier")
    void modifier_inconnue() {
        assertThatExceptionOfType(RegleMetierException.class)
                .isThrownBy(() -> service.modifierCampagne(999L, "x", 2026, null));
    }

    @Test
    @DisplayName("supprimer retire la campagne de la liste")
    void supprimer() {
        Campagne creee = service.creerCampagne("À supprimer", 2026, null);
        service.supprimerCampagne(creee.id());
        assertThat(service.listerCampagnes()).isEmpty();
    }

    @Test
    @DisplayName("supprimer une campagne inconnue lève une erreur métier")
    void supprimer_inconnue() {
        assertThatExceptionOfType(RegleMetierException.class).isThrownBy(() -> service.supprimerCampagne(999L));
    }

    @Test
    @DisplayName("la liste est triée de la plus récente à la plus ancienne")
    void liste_triee_par_annee_decroissante() {
        service.creerCampagne("A", 2024, null);
        service.creerCampagne("B", 2026, null);
        service.creerCampagne("C", 2025, null);
        assertThat(service.listerCampagnes()).extracting(Campagne::annee).containsExactly(2026, 2025, 2024);
    }

    @Test
    @DisplayName("rattacher puis détacher un passage")
    void rattacher_puis_detacher() {
        long idPassage = semerPassage();
        Campagne campagne = service.creerCampagne("Suivi ENS", 2026, null);

        Passage rattache = service.rattacherPassage(idPassage, campagne.id());
        assertThat(rattache.idCampagne()).isEqualTo(campagne.id());
        assertThat(new PassageDao(source).findById(idPassage).orElseThrow().idCampagne())
                .as("rattachement persisté")
                .isEqualTo(campagne.id());

        Passage detache = service.detacherPassage(idPassage);
        assertThat(detache.idCampagne()).isNull();
    }

    @Test
    @DisplayName("supprimer une campagne détache ses passages, sans les effacer")
    void supprimer_campagne_detache_les_passages() {
        long idPassage = semerPassage();
        Campagne campagne = service.creerCampagne("À supprimer", 2026, null);
        service.rattacherPassage(idPassage, campagne.id());

        service.supprimerCampagne(campagne.id());

        Passage apres = new PassageDao(source).findById(idPassage).orElseThrow();
        assertThat(apres.idCampagne())
                .as("détaché par ON DELETE SET NULL, pas effacé")
                .isNull();
    }

    @Test
    @DisplayName("rattacher à une campagne inconnue lève une erreur métier")
    void rattacher_campagne_inconnue() {
        long idPassage = semerPassage();
        assertThatExceptionOfType(RegleMetierException.class)
                .isThrownBy(() -> service.rattacherPassage(idPassage, 999L));
    }

    @Test
    @DisplayName("rattacher un passage inconnu lève une erreur métier")
    void rattacher_passage_inconnu() {
        Campagne campagne = service.creerCampagne("Suivi", 2026, null);
        assertThatExceptionOfType(RegleMetierException.class)
                .isThrownBy(() -> service.rattacherPassage(999L, campagne.id()));
    }
}
