package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.model.HorlogeFigee;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.NuitRecupereeDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.ServiceDisponibiliteAudio;
import fr.univ_amu.iut.passage.model.ServicePassage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import fr.univ_amu.iut.passage.model.dao.PassageOpportunisteDao;
import fr.univ_amu.iut.passage.model.dao.SequenceDao;
import fr.univ_amu.iut.passage.model.dao.SessionDao;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Supprimer une nuit **récupérée** de Vigie-Chiro (#2581).
///
/// Une nuit rapatriée par la synchro porte le statut « Déposé », et c'est vrai : la participation existe
/// sur la plateforme. Mais la garde de ce statut protège une nuit **que nous avons déposée** - « une
/// donnée officielle transmise à Vigie-Chiro ». Celle-ci, nous ne l'avons pas transmise, nous l'avons
/// reçue ; la supprimer enlève une copie locale, et la participation reste où elle est.
///
/// Le seul recours était « Annuler le dépôt » puis « Supprimer » : on demandait à l'utilisateur
/// d'affirmer quelque chose de faux pour obtenir le droit de nettoyer sa base.
class SuppressionNuitRecupereeTest {

    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private ServicePassage service;
    private PassageDao passageDao;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        passageDao = new PassageDao(source);
        SessionDao sessionDao = new SessionDao(source);
        SequenceDao sequenceDao = new SequenceDao(source);
        service = new ServicePassage(
                passageDao,
                new MoteurWorkflowPassage(),
                new HorlogeFigee(LocalDate.of(2026, 6, 20)),
                sessionDao,
                sequenceDao,
                new ServiceDisponibiliteAudio(sessionDao, sequenceDao, new Workspace(dossier)),
                new PassageOpportunisteDao(source),
                new NuitRecupereeDao(source));
    }

    /// Une nuit **récupérée** : rattachée à une participation, et sans le moindre WAV posé.
    private long semerNuitRecuperee() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .nuit(1, 2026, "2026-04-22")
                .statut(StatutWorkflow.DEPOSE)
                .semerSquelette();
        rattacher(jeu.idPassage());
        return jeu.idPassage();
    }

    /// Une nuit **que nous avons déposée** : importée ici (elle a ses originaux), puis transmise.
    private long semerNuitDeposeeParNous() {
        JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .nuit(2, 2026, "2026-04-23")
                .statut(StatutWorkflow.DEPOSE)
                .semer();
        rattacher(jeu.idPassage());
        return jeu.idPassage();
    }

    private void rattacher(long idPassage) {
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), PARTICIPATION));
    }

    @Test
    @DisplayName("#2581 : une nuit récupérée se supprime, sans passer par « Annuler le dépôt »")
    void nuit_recuperee_se_supprime() {
        long idPassage = semerNuitRecuperee();

        assertThat(service.estNuitRecuperee(idPassage))
                .as("rattachée à une participation, et aucun WAV jamais posé")
                .isTrue();

        service.supprimer(idPassage);

        assertThat(passageDao.findById(idPassage))
                .as("la copie locale est partie ; la participation, elle, reste sur la plateforme")
                .isEmpty();
    }

    @Test
    @DisplayName("#2581 : une nuit que NOUS avons déposée reste protégée, même rattachée")
    void nuit_deposee_par_nous_reste_protegee() {
        long idPassage = semerNuitDeposeeParNous();

        // Le rattachement à lui seul ne suffit donc pas : sans la seconde condition, l'exemption
        // s'étendrait à toutes les nuits déposées, et la garde ne protégerait plus rien.
        assertThat(service.estNuitRecuperee(idPassage))
                .as("elle a ses originaux : c'est nous qui l'avons produite puis déposée")
                .isFalse();

        assertThatThrownBy(() -> service.supprimer(idPassage))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("ne peut pas être supprimé");

        assertThat(passageDao.findById(idPassage)).isPresent();
    }

    @Test
    @DisplayName("#2581 : une nuit sans audio mais NON rattachée n'est pas une nuit récupérée")
    void nuit_sans_audio_non_rattachee_reste_protegee() {
        long idPassage = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .nuit(3, 2026, "2026-04-24")
                .statut(StatutWorkflow.DEPOSE)
                .semerSquelette()
                .idPassage();

        // L'absence d'audio seule ne prouve rien : une nuit purgée localement (#1300) n'a plus ses WAV
        // et n'est pourtant pas venue de la plateforme.
        assertThat(service.estNuitRecuperee(idPassage)).isFalse();
        assertThatThrownBy(() -> service.supprimer(idPassage)).isInstanceOf(RegleMetierException.class);
    }
}
