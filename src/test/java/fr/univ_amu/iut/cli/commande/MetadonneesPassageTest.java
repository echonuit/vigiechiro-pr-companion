package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.api.ResultatEcriture;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.EnvoiParticipation;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees.BilanRattrapage;
import fr.univ_amu.iut.passage.model.RattrapageMetadonnees.IssuePassage;
import fr.univ_amu.iut.passage.model.ServiceConditionsPassage;
import fr.univ_amu.iut.passage.model.SynchronisationParticipation;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import picocli.CommandLine;

/// `metadonnees-passage` (#1861) : parité CLI des gestes de la modale « Modifier le passage ».
///
/// Les services sont mockés - leur logique est couverte ailleurs (`RattrapageMetadonneesTest`,
/// `ServiceConditionsPassageTest`). On vérifie ici le **câblage**, le **rendu** et surtout les
/// **refus d'invocation** : un rattrapage de saison écrit sur la plateforme, ses garde-fous méritent
/// d'être tenus par des tests.
class MetadonneesPassageTest {

    private final SynchronisationParticipation synchronisation = mock(SynchronisationParticipation.class);
    private final RattrapageMetadonnees rattrapage = mock(RattrapageMetadonnees.class);
    private final ServiceConditionsPassage conditions = mock(ServiceConditionsPassage.class);

    private CommandLine ligne(StringWriter sortie) {
        Provider<Optional<SynchronisationParticipation>> passerelle = () -> Optional.of(synchronisation);
        Provider<Optional<RattrapageMetadonnees>> lot = () -> Optional.of(rattrapage);
        Provider<ServiceConditionsPassage> services = () -> conditions;
        CommandLine ligne = new CommandLine(new MetadonneesPassage(passerelle, lot, services));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    private static EnvoiParticipation envoiReussi() {
        return EnvoiParticipation.sansRealignement(ResultatEcriture.reussie());
    }

    private static Passage passage(String debut, String fin) {
        return new Passage(1L, 1, 2026, "2026-07-04", debut, fin, null, null, null, null, null, null, 1L, "1925492");
    }

    @Test
    @DisplayName("#1861 --passage --recuperer : rapatrie les métadonnées de la nuit")
    void recuperer_une_nuit() {
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--passage", "1", "--recuperer");

        assertThat(code).isZero();
        verify(synchronisation).tirerDepuis(1L);
        assertThat(sortie.toString()).contains("récupérées depuis Vigie-Chiro");
    }

    @Test
    @DisplayName("#1861 --passage --envoyer : signale le réalignement des heures (#1885)")
    void envoyer_dit_le_realignement() {
        when(synchronisation.pousserVers(1L))
                .thenReturn(new EnvoiParticipation(
                        ResultatEcriture.reussie(),
                        Optional.of(new EnvoiParticipation.Realignement("15:00", "15:00", "21:00", "06:00"))));
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--passage", "1", "--envoyer");

        assertThat(code).isZero();
        // Les deux valeurs, pas seulement la nouvelle : l'utilisateur doit pouvoir contester la correction.
        assertThat(sortie.toString()).contains("15:00").contains("21:00").contains("06:00");
    }

    @Test
    @DisplayName("#1861 une écriture refusée sort en 1, elle ne passe pas pour un succès (ADR 0008)")
    void envoi_refuse_sort_en_echec() {
        when(synchronisation.pousserVers(1L))
                .thenReturn(EnvoiParticipation.sansRealignement(ResultatEcriture.echouee("412 Precondition Failed")));
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--passage", "1", "--envoyer");

        assertThat(code).isEqualTo(1);
        assertThat(sortie.toString()).contains("Envoi refusé").contains("412");
    }

    @Test
    @DisplayName("#1861 --enregistreur et les heures sont locales : ni jeton ni réseau")
    void gestes_locaux_sans_reseau() {
        when(conditions.definirHoraires(1L, "21:00", "06:00")).thenReturn(passage("21:00", "06:00"));
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie)
                .execute(
                        "--passage",
                        "1",
                        "--enregistreur",
                        "1925492",
                        "--heure-debut",
                        "21:00",
                        "--heure-fin",
                        "06:00");

        assertThat(code).isZero();
        verify(conditions).definirEnregistreur(1L, "1925492");
        verify(conditions).definirHoraires(1L, "21:00", "06:00");
        verifyNoInteractions(synchronisation);
        assertThat(sortie.toString()).contains("1925492").contains("21:00").contains("06:00");
    }

    @Test
    @DisplayName("#1861 corriger puis publier en une invocation : la saisie précède l'envoi")
    void saisie_avant_envoi() {
        when(conditions.definirHoraires(1L, "21:00", "06:00")).thenReturn(passage("21:00", "06:00"));
        when(synchronisation.pousserVers(1L)).thenReturn(envoiReussi());
        StringWriter sortie = new StringWriter();

        int code =
                ligne(sortie).execute("--passage", "1", "--heure-debut", "21:00", "--heure-fin", "06:00", "--envoyer");

        assertThat(code).isZero();
        InOrder ordre = Mockito.inOrder(conditions, synchronisation);
        ordre.verify(conditions).definirHoraires(1L, "21:00", "06:00");
        ordre.verify(synchronisation).pousserVers(1L);
    }

    @Test
    @DisplayName("#1861 --tout sans --confirmer n'écrit rien : il annonce ce qu'il ferait")
    void tout_sans_confirmer_est_une_annonce() {
        when(rattrapage.passagesLies()).thenReturn(List.of(1L, 2L));
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--tout", "--envoyer");

        assertThat(code).isZero();
        verify(rattrapage, never()).rattraper(any(), anyBoolean(), anyBoolean(), any());
        assertThat(sortie.toString())
                .contains("2 nuit(s)")
                .contains("passage 1")
                .contains("--confirmer");
    }

    @Test
    @DisplayName("#1861 --tout --confirmer : rend compte nuit par nuit, et sort en 1 s'il en reste")
    void tout_confirme_rend_compte_nuit_par_nuit() {
        when(rattrapage.passagesLies()).thenReturn(List.of(1L, 2L));
        when(rattrapage.rattraper(any(), anyBoolean(), anyBoolean(), any())).thenAnswer(invocation -> {
            Consumer<IssuePassage> issue = invocation.getArgument(3);
            issue.accept(new IssuePassage.Traite(
                    1L,
                    false,
                    true,
                    Optional.of(new EnvoiParticipation.Realignement("15:00", "15:00", "21:00", "06:00"))));
            issue.accept(new IssuePassage.Ignore(2L, "Participation introuvable."));
            return new BilanRattrapage(1, 1, 1);
        });
        StringWriter sortie = new StringWriter();

        int code = ligne(sortie).execute("--tout", "--envoyer", "--confirmer");

        // Une nuit non réparée n'est pas un succès : un script de saison ne doit pas conclure au vert.
        assertThat(code).isEqualTo(1);
        assertThat(sortie.toString())
                .contains("[1/2]")
                .contains("[2/2]")
                .contains("ignorée : Participation introuvable.")
                .contains("1 nuit(s) traitée(s), 1 ignorée(s), 1 dont les heures");
    }

    @Test
    @DisplayName("#1861 --tout refuse un n° de série : le poser sur toute une saison inventerait des données")
    void tout_refuse_les_gestes_unitaires() {
        assertThatThrownBy(() -> MetadonneesPassage.verifierInvocation(null, true, false, false, "1925492", null, null))
                .isInstanceOf(ErreurUsage.class)
                .hasMessageContaining("inventerait des données");
        assertThatThrownBy(() -> MetadonneesPassage.verifierInvocation(null, true, true, false, null, "21:00", "06:00"))
                .isInstanceOf(ErreurUsage.class);
    }

    @Test
    @DisplayName("#1861 une seule borne d'heure est refusée : une nuit se délimite par les deux")
    void une_seule_borne_refusee() {
        assertThatThrownBy(() -> MetadonneesPassage.verifierInvocation(1L, false, false, false, null, "21:00", null))
                .isInstanceOf(ErreurUsage.class)
                .hasMessageContaining("vont de pair");
    }

    @Test
    @DisplayName("#1861 sans geste, ou sans cible claire, la commande refuse au lieu de ne rien faire")
    void invocations_sans_objet_refusees() {
        assertThatThrownBy(() -> MetadonneesPassage.verifierInvocation(1L, false, false, false, null, null, null))
                .isInstanceOf(ErreurUsage.class)
                .hasMessageContaining("Aucun geste demandé");
        // Ni cible ni --tout, puis les deux à la fois : deux façons de ne pas dire sur quoi on travaille.
        assertThatThrownBy(() -> MetadonneesPassage.verifierInvocation(null, false, true, false, null, null, null))
                .isInstanceOf(ErreurUsage.class);
        assertThatThrownBy(() -> MetadonneesPassage.verifierInvocation(1L, true, true, false, null, null, null))
                .isInstanceOf(ErreurUsage.class);
    }

    @Test
    @DisplayName("#1861 les invocations légitimes passent la règle")
    void invocations_valides_acceptees() {
        MetadonneesPassage.verifierInvocation(1L, false, true, true, null, null, null);
        MetadonneesPassage.verifierInvocation(1L, false, false, false, "1925492", null, null);
        MetadonneesPassage.verifierInvocation(1L, false, false, true, null, "21:00", "06:00");
        MetadonneesPassage.verifierInvocation(null, true, false, true, null, null, null);
    }

    @Test
    @DisplayName("#1861 une invocation refusée n'écrit rien du tout")
    void invocation_refusee_n_ecrit_rien() {
        int code = ligne(new StringWriter()).execute("--tout", "--enregistreur", "1925492");

        assertThat(code).isNotZero();
        verifyNoInteractions(rattrapage);
        verifyNoInteractions(conditions);
        verifyNoInteractions(synchronisation);
    }

    @Test
    @DisplayName("#1861 hors connexion, les gestes réseau disent pourquoi au lieu d'échouer obscurément")
    void hors_connexion_dit_pourquoi() {
        Provider<Optional<SynchronisationParticipation>> absente = Optional::empty;
        Provider<Optional<RattrapageMetadonnees>> pasDeLot = Optional::empty;
        CommandLine ligne = new CommandLine(new MetadonneesPassage(absente, pasDeLot, () -> conditions));
        ligne.setOut(new PrintWriter(new StringWriter(), true));
        List<Exception> remontees = capturerLesEchecs(ligne);

        ligne.execute("--passage", "1", "--recuperer");

        assertThat(remontees).hasSize(1);
        assertThat(remontees.get(0))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("connexion Vigie-Chiro")
                .hasMessageContaining("VIGIECHIRO_TOKEN");
    }

    @Test
    @DisplayName("#1861 un passage introuvable remonte en échec, il ne devient pas un succès muet")
    void passage_introuvable() {
        Mockito.doThrow(new RegleMetierException("Passage introuvable : 99"))
                .when(synchronisation)
                .tirerDepuis(99L);
        CommandLine ligne = ligne(new StringWriter());
        List<Exception> remontees = capturerLesEchecs(ligne);

        ligne.execute("--passage", "99", "--recuperer");

        assertThat(remontees).hasSize(1);
        assertThat(remontees.get(0)).hasMessageContaining("introuvable");
    }

    /// Retient les exceptions qui sortent de la commande, plutôt que de lire la trace imprimée par le
    /// gestionnaire par défaut de picocli : ce qu'on veut vérifier est **notre** refus, pas son rendu. La
    /// traduction en code de sortie appartient à `Cli`, qui la teste de son côté.
    private static List<Exception> capturerLesEchecs(CommandLine ligne) {
        List<Exception> remontees = new ArrayList<>();
        ligne.setExecutionExceptionHandler((exception, commande, parse) -> {
            remontees.add(exception);
            return 1;
        });
        return remontees;
    }
}
